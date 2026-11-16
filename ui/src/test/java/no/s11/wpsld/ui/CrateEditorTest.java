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
import java.util.concurrent.TimeUnit;

import org.testfx.util.WaitForAsyncUtils;

import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import no.s11.wpsld.crate.Entity;

/**
 * The crate window, as its GUI tests start it, run headless (see
 * ui/build.gradle), and what they share. The tests are in several classes,
 * TestCrateEditor and TestCrateEditor*, as each class runs in a JVM of its
 * own and the classes run in parallel: one class of them all took longest.
 */
public abstract class CrateEditorTest extends UiTest {

	protected CrateEditor app;

	@Override
	public void start(Stage stage) throws Exception {
		app = new CrateEditor();
		app.init();
		app.start(stage);
	}

	protected ListView<Entity> entities() {
		try {
			// The schema loads in the background; the toolbar's buttons appear
			// once it is laid out
			waitForFx(30, () -> lookup("#entities").tryQuery().isPresent()
					&& lookup("#save").tryQuery().isPresent());
		} catch (Exception e) {
			throw new AssertionError("Editor did not appear", e);
		}
		return onFx(() -> lookup("#entities").queryListView());
	}

	protected void addEntity(String type) {
		clickOn("#addEntity");
		clickOn("#typeSearch").write(type).type(KeyCode.ENTER);
	}

	protected void renameTo(String id) {
		TextField field = lookup("#entityId").queryAs(TextField.class);
		clickOn(field);
		interact(field::selectAll);
		write(id).type(KeyCode.ENTER);
	}

	/** Leave the field being edited, by clicking the entity list's first entity, the root */
	protected void leaveField() {
		clickOn(lookup("#entities .list-cell").nth(0).queryAs(Node.class));
	}

	/** The window's title for this file name, as on this operating system */
	protected static String title(String name, boolean modified) {
		return CrateFile.onMac() ? name + (modified ? " — Edited" : "") : (modified ? "*" : "") + name + " – wpsld";
	}

	/** A crate in a new folder, opened */
	protected Path openNewCrate() throws Exception {
		entities();
		Path dir = Files.createTempDirectory("crate");
		Path file = dir.resolve("ro-crate-metadata.json");
		Files.writeString(file, """
				{"@context": "https://w3id.org/ro/crate/1.3/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
				  {"@id": "./", "@type": "Dataset", "name": "A crate", "description": "For testing",
				   "datePublished": "2026-11-09", "license": {"@id": "http://spdx.org/licenses/CC0-1.0"}}
				 ]}""");
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir)).get());
		WaitForAsyncUtils.waitForFxEvents();
		return file;
	}

	/** As another application (e.g. git) would: the crate with another name, and a later time */
	protected static void changeOnDisk(Path file) throws Exception {
		Files.writeString(file, Files.readString(file).replace("\"A crate\"", "\"Changed elsewhere\""));
		Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(java.time.Instant.now().plusSeconds(10)));
	}

	/** Check the file on disk, as when coming back to the window, without waiting for a dialog it shows */
	protected void checkDisk() {
		WaitForAsyncUtils.asyncFx(app::checkDisk);
		WaitForAsyncUtils.waitForFxEvents();
	}

	/** A dialog's button, not one of the window's with the same text */
	protected void clickDialogButton(String text) {
		clickOn(lookup(".dialog-pane .button").match((Button b) -> b.getText().equals(text)).queryButton());
		WaitForAsyncUtils.waitForFxEvents();
	}

	/** The crate's JSON-LD, as View > Crate JSON-LD shows it (closed again) */
	protected String json() {
		interact(menuItem("menuCrateJson")::fire);
		TextInputControl text = lookup("#crateJson").queryTextInputControl();
		String json = text.getText();
		interact(() -> text.getScene().getWindow().hide());
		return json;
	}

	protected MenuItem menuItem(String id) {
		MenuBar bar = lookup("#menuBar").queryAs(MenuBar.class);
		return bar.getMenus().stream().flatMap(m -> m.getItems().stream()).filter(i -> id.equals(i.getId()))
				.findFirst().orElseThrow();
	}

	/** Properties of the form's visible rows */
	protected List<String> visibleProperties() {
		return lookup(n -> n.getId() != null && n.getId().startsWith("label-")).queryAllAs(Label.class).stream()
				.filter(Label::isVisible).filter(l -> l.getParent() != null && l.getParent().isVisible())
				// The property, not its human readable label; * if required
				.map(l -> l.getId().substring("label-".length()) + (l.getText().endsWith(" *") ? " *" : ""))
				.toList();
	}

	protected Hyperlink previewLink(String containerId, String text) {
		return from(lookup(containerId).queryAs(Node.class)).lookup(".hyperlink").queryAllAs(Hyperlink.class).stream()
				.filter(l -> l.getText().equals(text)).findFirst().orElseThrow(() -> new AssertionError(text));
	}

	/** Wait for the preview to show this name, as it is made again once typing pauses */
	protected void waitForPreview(String name) {
		try {
			waitForFx(5, () -> lookup("#previewName").tryQuery()
					.map(n -> ((Label) n).getText().equals(name)).orElse(false));
		} catch (java.util.concurrent.TimeoutException e) {
			throw new AssertionError("Preview not showing " + name, e);
		}
	}
}
