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

import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.FormFields;

/**
 * GUI tests of the crate window, run headless (see ui/build.gradle).
 */
public class TestCrateEditor extends ApplicationTest {

	private CrateEditor app;

	@Override
	public void start(Stage stage) throws Exception {
		app = new CrateEditor();
		app.init();
		app.start(stage);
	}

	private ListView<Entity> entities() {
		try {
			// The schema loads in the background; the toolbar's buttons appear
			// once it is laid out
			WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#entities").tryQuery().isPresent()
					&& lookup("#save").tryQuery().isPresent());
		} catch (Exception e) {
			throw new AssertionError("Editor did not appear", e);
		}
		return lookup("#entities").queryListView();
	}

	private void addEntity(String type) {
		clickOn("#addEntity");
		clickOn("#typeSearch").write(type).type(KeyCode.ENTER);
	}

	private void renameTo(String id) {
		TextField field = lookup("#entityId").queryAs(TextField.class);
		clickOn(field);
		interact(field::selectAll);
		write(id).type(KeyCode.ENTER);
	}

	@Test
	public void addEntityChoosesType() {
		assertEquals(1, entities().getItems().size());
		addEntity("person");
		assertEquals(2, entities().getItems().size());
		assertEquals(List.of("Person"), entities().getItems().get(1).getTypes());
	}

	@Test
	public void addTypeOutsideSchemaOrg() {
		entities();
		// From the RO-Crate context: Bioschemas and PCDM
		addEntity("computationalworkflow");
		assertEquals(List.of("ComputationalWorkflow"), entities().getItems().get(1).getTypes());
		addEntity("repositorycollection");
		assertEquals(List.of("RepositoryCollection"), entities().getItems().get(2).getTypes());
	}

	@Test
	public void idClashMergesOnce() {
		entities();
		addEntity("datadownload");
		addEntity("person");
		Entity download = entities().getItems().get(1);
		Entity person = entities().getItems().get(2);
		// The person is shown; give it the download's @id
		renameTo(download.getId());
		// Asked once to merge, even as the dialog takes the focus
		clickOn("OK");
		assertTrue(lookup(".dialog-pane").tryQuery().isEmpty());
		assertEquals(List.of(download), entities().getItems().subList(1, entities().getItems().size()));
		assertEquals(List.of("DataDownload", "Person"), download.getTypes());
		assertFalse(entities().getItems().contains(person));
	}

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
	public void newCrateAsksToDiscardChanges() {
		entities();
		// Nothing changed yet: a new crate at once (with its shortcut, as
		// moving the mouse over the menu bar may open another menu)
		push(KeyCode.SHORTCUT, KeyCode.N);
		assertTrue(lookup(".dialog-pane").tryQuery().isEmpty());
		addEntity("person");
		Entity root = entities().getItems().get(0);

		push(KeyCode.SHORTCUT, KeyCode.N);
		assertTrue(lookup("Discard the changes to this crate?").tryQuery().isPresent());
		clickOn("Cancel");
		assertEquals(2, entities().getItems().size());

		push(KeyCode.SHORTCUT, KeyCode.N);
		clickOn("Discard");
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
		assertEquals("wpsld – " + dir.resolve("ro-crate-metadata.json"), stage.getTitle());
		// Written with its own version, and nothing to discard yet
		assertTrue(lookup("#preview").queryTextInputControl().getText().contains("https://w3id.org/ro/crate/1.2/context"));
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

	/** Leave the field being edited, by clicking the entity list's first entity, the root */
	private void leaveField() {
		clickOn(lookup("#entities .list-cell").nth(0).queryAs(Node.class));
	}

	/**
	 * Undo points after leaving a field: Ctrl+Z inside a field is the field's
	 * own undo of typing.
	 */
	@Test
	public void undoTyping() {
		entities();
		clickOn("#header-name").write("My crate");
		leaveField();
		Entity root = entities().getItems().get(0);
		assertEquals("My crate", root.get("name").orElseThrow().asText());

		push(KeyCode.SHORTCUT, KeyCode.Z);
		root = entities().getItems().get(0);
		assertTrue(root.get("name").isEmpty());
		assertEquals("", lookup("#header-name").queryAs(TextField.class).getText());
		// Back in the field changed, where Ctrl+Z goes on with the crate's undo
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals("header-name", entities().getScene().getFocusOwner().getId());
		// Nothing more to undo
		push(KeyCode.SHORTCUT, KeyCode.Z);
		assertTrue(entities().getItems().get(0).get("name").isEmpty());

		push(KeyCode.SHORTCUT, KeyCode.SHIFT, KeyCode.Z);
		assertEquals("My crate", entities().getItems().get(0).get("name").orElseThrow().asText());
		assertEquals("My crate", lookup("#header-name").queryAs(TextField.class).getText());
		// Leaving a field without changes is no new point: redo is still there after undo
		push(KeyCode.SHORTCUT, KeyCode.Z);
		leaveField();
		push(KeyCode.SHORTCUT, KeyCode.Y);
		assertEquals("My crate", entities().getItems().get(0).get("name").orElseThrow().asText());
	}

	/**
	 * Adding an entity (with its type), and merging by @id, each undone in
	 * one step; New starts a new history.
	 */
	@Test
	public void undoAddAndMerge() {
		entities();
		addEntity("datadownload");
		addEntity("person");
		Entity download = entities().getItems().get(1);
		renameTo(download.getId());
		clickOn("OK");
		assertEquals(2, entities().getItems().size());

		leaveField();
		push(KeyCode.SHORTCUT, KeyCode.Z);
		assertEquals(List.of(List.of("DataDownload"), List.of("Person")), entities().getItems().subList(1, 3)
				.stream().map(Entity::getTypes).toList());
		// The entity that changed is shown
		assertEquals(entities().getItems().get(1).getId(), entities().getSelectionModel().getSelectedItem().getId());
		push(KeyCode.SHORTCUT, KeyCode.Z);
		assertEquals(2, entities().getItems().size());
		push(KeyCode.SHORTCUT, KeyCode.Z);
		assertEquals(1, entities().getItems().size());

		push(KeyCode.SHORTCUT, KeyCode.SHIFT, KeyCode.Z);
		push(KeyCode.SHORTCUT, KeyCode.SHIFT, KeyCode.Z);
		push(KeyCode.SHORTCUT, KeyCode.SHIFT, KeyCode.Z);
		assertEquals(2, entities().getItems().size());
		assertEquals(List.of("DataDownload", "Person"), entities().getItems().get(1).getTypes());

		push(KeyCode.SHORTCUT, KeyCode.N);
		clickOn("Discard");
		push(KeyCode.SHORTCUT, KeyCode.Z);
		assertEquals(1, entities().getItems().size());
	}

	/** Properties of the form's visible rows */
	private List<String> visibleProperties() {
		return lookup(n -> n.getId() != null && n.getId().startsWith("label-")).queryAllAs(Label.class).stream()
				.filter(Label::isVisible).filter(l -> l.getParent() != null && l.getParent().isVisible())
				// The property, not its human readable label; * if required
				.map(l -> l.getId().substring("label-".length()) + (l.getText().endsWith(" *") ? " *" : ""))
				.toList();
	}

	/**
	 * The core RO-Crate profile is chosen by default: the root's required
	 * properties come first, marked.
	 */
	@Test
	public void coreProfileByDefault() {
		entities();
		ComboBox<FormFields> profile = lookup("#profile").queryComboBox();
		assertEquals("RO-Crate Machine Actionable Profile 0.1.0", profile.getValue().getName());
		List<String> labels = visibleProperties();
		// name and description under @id, then the required ones
		assertEquals(List.of("name *", "description *", "datePublished *", "description *", "license *", "name *"),
				labels.subList(0, 6));
		assertTrue(lookup("Required by RO-Crate Machine Actionable Profile 0.1.0 (4)").tryQuery().isPresent());
	}

	@Test
	public void schemaOrgWithoutProfile() {
		entities();
		ComboBox<FormFields> profile = lookup("#profile").queryComboBox();
		interact(() -> profile.getSelectionModel().select(profile.getItems().size() - 1));
		assertEquals("schema.org (no profile)", profile.getValue().getName());
		assertFalse(visibleProperties().contains("name *"));
		assertTrue(visibleProperties().contains("distribution"));
	}

	/**
	 * Only the root has the root's required properties, not a folder Dataset.
	 */
	@Test
	public void rootRulesForRootOnly() {
		entities();
		addEntity("dataset");
		assertEquals(List.of("Dataset"), entities().getItems().get(1).getTypes());
		assertTrue(visibleProperties().stream().noneMatch(l -> l.endsWith(" *")));
	}

	private Hyperlink previewLink(String containerId, String text) {
		return from(lookup(containerId).queryAs(Node.class)).lookup(".hyperlink").queryAllAs(Hyperlink.class).stream()
				.filter(l -> l.getText().equals(text)).findFirst().orElseThrow(() -> new AssertionError(text));
	}

	/**
	 * The preview shows the selected entity, with links that open the
	 * entities it refers to, and those referring to it.
	 */
	@Test
	public void previewLinks() {
		entities();
		assertTrue(lookup("#previewTab").tryQuery().isPresent());
		assertEquals("./", lookup("#previewName").queryAs(Label.class).getText());
		// Link the root to a new DataDownload
		clickOn("#propertySearch").write("distribution");
		clickOn("#new-distribution");
		clickOn("DataDownload");
		Entity download = entities().getItems().get(1);
		clickOn(previewLink("#previewProperties", download.getId()));
		// Opened for editing, and previewed
		assertSame(download, entities().getSelectionModel().getSelectedItem());
		assertEquals(download.getId(), lookup("#previewName").queryAs(Label.class).getText());
		assertEquals(download.getId(), lookup("#entityId").queryAs(TextField.class).getText());
		// Back via the entity referring to it
		clickOn(previewLink("#previewReferencedBy", "./ (distribution)"));
		assertSame(entities().getItems().get(0), entities().getSelectionModel().getSelectedItem());
		// And the link to the crate's root from any other entity
		interact(() -> entities().getSelectionModel().select(download));
		clickOn("#previewRoot");
		assertSame(entities().getItems().get(0), entities().getSelectionModel().getSelectedItem());
	}

	@Test
	public void previewUpdatesWhileEditing() {
		entities();
		clickOn("#propertySearch").write("name");
		TextField name = lookup("#header-name").queryAs(TextField.class);
		clickOn(name).write("My crate");
		assertEquals("My crate", lookup("#previewName").queryAs(Label.class).getText());
		clickOn("#propertySearch").eraseText(4).write("license");
		clickOn(lookup("#value-license-0").queryAs(ComboBox.class).getEditor())
				.write("http://spdx.org/licenses/CC0-1.0");
		// Human readable labels of the properties
		assertTrue(from(lookup("#previewProperties").queryAs(Node.class)).lookup(".label").queryAllAs(Label.class)
				.stream().anyMatch(l -> l.getText().equals("License")));
		// A web address, not an entity of the crate, opens in the browser
		Hyperlink license = previewLink("#previewProperties", "http://spdx.org/licenses/CC0-1.0");
		assertEquals("Open in the browser", license.getTooltip().getText());
	}

	/**
	 * The profile requires the root to be a Dataset; a subtype would do.
	 */
	@Test
	public void requiredTypeNotice() {
		entities();
		clickOn("#header-name").write("My crate");
		assertFalse(lookup("#requiredType-Dataset").tryQuery().isPresent());
		clickOn("#addType");
		clickOn("#typeSearch").write("person").type(KeyCode.ENTER);
		clickOn("#removeType-Dataset");
		Entity root = entities().getItems().get(0);
		assertEquals(List.of("Person"), root.getTypes());
		assertTrue(lookup("#requiredType-Dataset").tryQuery().isPresent());
		// name is fixed under @id, a property of any type
		assertFalse(lookup("#otherProperties").tryQuery().map(Node::isVisible).orElse(false));
		clickOn("#addRequiredType-Dataset");
		assertEquals(List.of("Person", "Dataset"), root.getTypes());
		assertFalse(lookup("#requiredType-Dataset").tryQuery().isPresent());
	}

	/**
	 * Typing updates the entity's label in the list without rebuilding the
	 * list's cells, which made the list flicker.
	 */
	@Test
	public void listUpdatesInPlace() {
		entities();
		ListCell<?> cell = from(entities()).lookup(".list-cell").queryAs(ListCell.class);
		clickOn("#header-name").write("My crate");
		ListCell<?> after = from(entities()).lookup(".list-cell").queryAs(ListCell.class);
		assertSame(cell, after);
		assertTrue(after.getText().startsWith("My crate\n"));
	}
}
