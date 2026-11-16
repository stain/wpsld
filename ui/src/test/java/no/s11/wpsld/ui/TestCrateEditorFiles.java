/*
 * Copyright 2026 The University of Manchester, UK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package no.s11.wpsld.ui;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;
import org.testfx.util.WaitForAsyncUtils;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.FormFields;

/**
 * GUI tests of the crate window's files: opening, saving, asking to save,
 * and the file changed on disk meanwhile.
 */
public class TestCrateEditorFiles extends CrateEditorTest {

	@Test
	public void saveWarnsAboutRequirements() {
		entities();
		clickOn("#save");
		assertTrue(lookup(".dialog-pane").tryQuery().isPresent());
		assertTrue(lookup("The crate does not yet meet the RO-Crate requirements").tryQuery().isPresent());
		clickOn("Cancel");
		assertTrue(lookup(".dialog-pane").tryQuery().isEmpty());
	}

	@Test
	public void newCrateAsksToSaveChanges() {
		entities();
		// Nothing changed yet: a new crate at once (with its shortcut, as
		// moving the mouse over the menu bar may open another menu)
		push(KeyCode.SHORTCUT, KeyCode.N);
		assertTrue(lookup(".dialog-pane").tryQuery().isEmpty());
		addEntity("person");
		Entity root = entities().getItems().get(0);

		push(KeyCode.SHORTCUT, KeyCode.N);
		assertTrue(lookup("Do you want to save the changes to Untitled?").tryQuery().isPresent());
		clickOn("Cancel");
		assertEquals(2, entities().getItems().size());

		push(KeyCode.SHORTCUT, KeyCode.N);
		clickOn("Don't Save");
		assertEquals(1, entities().getItems().size());
		assertNotSame(root, entities().getItems().get(0));
		assertEquals(entities().getItems().get(0), entities().getSelectionModel().getSelectedItem());
	}

	@Test
	public void openCrate() throws Exception {
		entities();
		ComboBox<FormFields> profiles = lookup("#profile").queryComboBox();
		// schema.org chosen, but the crate conforms to the core profile
		interact(() -> profiles.getSelectionModel().selectLast());
		Path dir = Files.createTempDirectory("crate");
		Files.writeString(dir.resolve("ro-crate-metadata.json"), """
				{"@context": "https://w3id.org/ro/crate/1.2/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.2"}},
				  {"@id": "./", "@type": "Dataset", "name": "Old crate",
				   "conformsTo": {"@id": "https://language-research-technology.github.io/ro-crate-masp/profiles/ro-crate/profile-crate/"},
				   "author": {"@id": "#alice"}},
				  {"@id": "#alice", "@type": "Person", "name": "Alice"}
				 ]}""");
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir)).get());
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(List.of("./", "#alice"), entities().getItems().stream().map(Entity::getId).toList());
		assertEquals(entities().getItems().get(0), entities().getSelectionModel().getSelectedItem());
		assertEquals("RO-Crate Machine Actionable Profile 0.1.0", profiles.getValue().getName());
		assertEquals(title(dir.getFileName() + java.io.File.separator + "ro-crate-metadata.json", false),
				stage.getTitle());
		// Written with its own version, and nothing to discard yet
		assertTrue(json().contains("https://w3id.org/ro/crate/1.2/context"));
		push(KeyCode.SHORTCUT, KeyCode.N);
		assertTrue(lookup(".dialog-pane").tryQuery().isEmpty());
		assertEquals(1, entities().getItems().size());

		// Not a crate: an error, and the crate stays
		Files.writeString(dir.resolve("ro-crate-metadata.json"), "[]");
		assertFalse(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir)).get());
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("Can't open " + dir).tryQuery().isPresent());
		clickOn("OK");
		assertEquals(1, entities().getItems().size());
	}

	/**
	 * File > Save writes to the file opened, without asking where; the
	 * title shows unsaved changes until then.
	 */
	@Test
	public void saveToOpenedFile() throws Exception {
		Path file = openNewCrate();
		Stage stage = (Stage) entities().getScene().getWindow();
		String name = file.getParent().getFileName() + java.io.File.separator + "ro-crate-metadata.json";
		assertEquals(title(name, false), stage.getTitle());
		addEntity("person");
		assertEquals(title(name, true), stage.getTitle());

		push(KeyCode.SHORTCUT, KeyCode.S);
		WaitForAsyncUtils.waitForFxEvents();
		// The person has no name, which the core profile doesn't require
		if (lookup("Save Anyway").tryQuery().isPresent()) {
			clickOn("Save Anyway");
		}
		assertTrue(Files.readString(file).contains("Person"));
		assertEquals(title(name, false), stage.getTitle());
		// Saved: nothing to ask about
		push(KeyCode.SHORTCUT, KeyCode.N);
		assertTrue(lookup(".dialog-pane").tryQuery().isEmpty());
	}

	/**
	 * Closing (here File > New) with unsaved changes: Save saves them first.
	 */
	@Test
	public void saveWhenAsked() throws Exception {
		Path file = openNewCrate();
		addEntity("person");
		push(KeyCode.SHORTCUT, KeyCode.N);
		// The dialog's, not the toolbar's Save
		clickOn(lookup(".dialog-pane .button").match((Button b) -> b.getText().equals("Save")).queryButton());
		WaitForAsyncUtils.waitForFxEvents();
		if (lookup("Save Anyway").tryQuery().isPresent()) {
			clickOn("Save Anyway");
		}
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(Files.readString(file).contains("Person"));
		assertEquals(1, entities().getItems().size());
		Stage stage = (Stage) entities().getScene().getWindow();
		assertEquals(title(CrateFile.UNTITLED, false), stage.getTitle());
	}

	/**
	 * Changed on disk with no unsaved changes: reloaded, and Undo brings the
	 * crate as it was back.
	 */
	@Test
	public void reloadChangedFile() throws Exception {
		Path file = openNewCrate();
		changeOnDisk(file);
		checkDisk();
		assertTrue(lookup(".dialog-pane").tryQuery().isEmpty());
		assertTrue(json().contains("Changed elsewhere"));
		Stage stage = (Stage) entities().getScene().getWindow();
		assertFalse(stage.getTitle().startsWith("*") || stage.getTitle().endsWith("Edited"));
		push(KeyCode.SHORTCUT, KeyCode.Z);
		assertTrue(json().contains("A crate"));
	}

	/**
	 * Written again with the same content, with a new time (as git may): not
	 * a change, so unsaved changes stay and nothing is asked.
	 */
	@Test
	public void touchedFileNotChanged() throws Exception {
		Path file = openNewCrate();
		addEntity("person");
		Files.writeString(file, Files.readString(file));
		Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(java.time.Instant.now().plusSeconds(10)));
		checkDisk();
		assertTrue(lookup(".dialog-pane").tryQuery().isEmpty());
		assertEquals(2, entities().getItems().size());
	}

	/**
	 * Changed on disk with unsaved changes: asked once whether to keep them;
	 * saving then asks again, and Cancel leaves the other changes alone, Save
	 * Anyway overwrites them.
	 */
	@Test
	public void keepChangesOverChangedFile() throws Exception {
		Path file = openNewCrate();
		addEntity("person");
		changeOnDisk(file);
		checkDisk();
		assertTrue(lookup("“" + file.getParent().getFileName() + java.io.File.separator
				+ "ro-crate-metadata.json” has been changed by another application.").tryQuery().isPresent());
		clickDialogButton("Keep My Changes");
		assertEquals(2, entities().getItems().size());
		// Not asked again for the same change
		checkDisk();
		assertTrue(lookup(".dialog-pane").tryQuery().isEmpty());

		push(KeyCode.SHORTCUT, KeyCode.S);
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup(".dialog-pane .label").match((Label l) -> l.getText().contains("since you opened or saved it"))
				.tryQuery().isPresent());
		clickDialogButton("Cancel");
		assertTrue(Files.readString(file).contains("Changed elsewhere"));

		push(KeyCode.SHORTCUT, KeyCode.S);
		WaitForAsyncUtils.waitForFxEvents();
		clickDialogButton("Save Anyway");
		// Then perhaps the RO-Crate requirements
		if (lookup(".dialog-pane .button").match((Button b) -> b.getText().equals("Save Anyway")).tryQuery()
				.isPresent()) {
			clickDialogButton("Save Anyway");
		}
		assertTrue(Files.readString(file).contains("Person"));
		assertFalse(Files.readString(file).contains("Changed elsewhere"));
	}

	/**
	 * Saved, changed elsewhere (e.g. on the command line), then edited: coming
	 * back reloads the file, so saving keeps both the other change and the edit.
	 */
	@Test
	public void saveAfterChangeElsewhereKeepsIt() throws Exception {
		Path file = openNewCrate();
		addEntity("person");
		push(KeyCode.SHORTCUT, KeyCode.S);
		WaitForAsyncUtils.waitForFxEvents();
		if (lookup(".dialog-pane .button").match((Button b) -> b.getText().equals("Save Anyway")).tryQuery()
				.isPresent()) {
			clickDialogButton("Save Anyway");
		}
		assertTrue(Files.readString(file).contains("Person"));
		changeOnDisk(file);
		// Back to the window
		checkDisk();
		assertTrue(json().contains("Changed elsewhere"));
		interact(() -> entities().getSelectionModel().clearAndSelect(0));
		clickOn(lookup("#value-description-0").queryAs(TextField.class)).write(" More");
		clickOn("#entities");
		push(KeyCode.SHORTCUT, KeyCode.S);
		WaitForAsyncUtils.waitForFxEvents();
		if (lookup(".dialog-pane .button").match((Button b) -> b.getText().equals("Save Anyway")).tryQuery()
				.isPresent()) {
			clickDialogButton("Save Anyway");
		}
		String saved = Files.readString(file);
		assertTrue(saved, saved.contains("Changed elsewhere"));
		assertTrue(saved, saved.contains("For testing More"));
	}

	/** A file with \r\n line ends is saved with them, on any platform; others with \n */
	@Test
	public void lineEndsKept() throws Exception {
		Path file = openNewCrate();
		Stage stage = (Stage) entities().getScene().getWindow();
		// Opened again with \r\n line ends
		Files.writeString(file, Files.readString(file).replace("\n", "\r\n"));
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, file.getParent())).get());
		WaitForAsyncUtils.waitForFxEvents();
		addEntity("person");
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.saveTo(stage, file)).get());
		String saved = Files.readString(file);
		assertTrue(saved.contains("Person"));
		assertTrue(saved.contains("\r\n"));
		assertFalse(saved.replace("\r\n", "").contains("\n"));
		// A new crate: \n
		push(KeyCode.SHORTCUT, KeyCode.N);
		Path other = file.resolveSibling("other-ro-crate-metadata.json");
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.saveTo(stage, other)).get());
		assertFalse(Files.readString(other).contains("\r"));
	}

	/** Saving over a changed file: Reload discards the unsaved changes instead */
	@Test
	public void reloadInsteadOfSaving() throws Exception {
		Path file = openNewCrate();
		addEntity("person");
		changeOnDisk(file);
		push(KeyCode.SHORTCUT, KeyCode.S);
		WaitForAsyncUtils.waitForFxEvents();
		clickDialogButton("Reload");
		assertEquals(1, entities().getItems().size());
		assertTrue(json().contains("Changed elsewhere"));
		assertTrue(Files.readString(file).contains("Changed elsewhere"));
	}
}
