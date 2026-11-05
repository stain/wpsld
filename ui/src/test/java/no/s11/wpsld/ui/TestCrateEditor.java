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

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
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

	@Override
	public void start(Stage stage) throws Exception {
		CrateEditor app = new CrateEditor();
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

	/** Visible labels of the form's property rows */
	private List<String> visibleProperties() {
		return lookup(n -> n.getId() != null && n.getId().startsWith("label-")).queryAllAs(Label.class).stream()
				.filter(Label::isVisible).filter(l -> l.getParent() != null && l.getParent().isVisible())
				.map(Label::getText).toList();
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
		// name and description fixed under @id, then the other required ones
		assertEquals(List.of("name *", "description *", "datePublished *", "license *"), labels.subList(0, 4));
		assertTrue(lookup("Required by RO-Crate Machine Actionable Profile 0.1.0 (2)").tryQuery().isPresent());
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

	/**
	 * The form's groups as the tabs of an OS/2-style notebook, with pages.
	 */
	@Test
	public void notebookView() {
		entities();
		clickOn("#viewNotebook");
		assertTrue(lookup("#notebook").tryQuery().isPresent());
		assertEquals(List.of("Required", "Dataset", "CreativeWork", "Thing"),
				lookup(".notebook-tab").queryAllAs(Label.class).stream().map(Label::getText).toList());
		assertTrue(lookup("#propertySearch").tryQuery().isEmpty() || !lookup("#propertySearch").query().isVisible());
		// CreativeWork's properties on pages
		clickOn(lookup(".notebook-tab").queryAllAs(Label.class).stream().filter(l -> l.getText().equals("CreativeWork"))
				.findFirst().orElseThrow());
		assertTrue(lookup(".notebook-status").queryAs(Label.class).getText().matches("Page 1 of \\d+"));
		// Back to the list
		clickOn("#viewList");
		assertTrue(lookup("#notebook").tryQuery().isEmpty());
		assertTrue(visibleProperties().contains("name *"));
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
		TextField name = lookup("#value-name-0").queryAs(TextField.class);
		clickOn(name).write("My crate");
		assertEquals("My crate", lookup("#previewName").queryAs(Label.class).getText());
		clickOn("#propertySearch").eraseText(4).write("license");
		clickOn(lookup("#value-license-0").queryAs(ComboBox.class).getEditor())
				.write("http://spdx.org/licenses/CC0-1.0");
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
		clickOn("#value-name-0").write("My crate");
		assertFalse(lookup("#requiredType-Dataset").tryQuery().isPresent());
		clickOn("#addType");
		clickOn("#typeSearch").write("person").type(KeyCode.ENTER);
		clickOn("#removeType-Dataset");
		Entity root = entities().getItems().get(0);
		assertEquals(List.of("Person"), root.getTypes());
		assertTrue(lookup("#requiredType-Dataset").tryQuery().isPresent());
		// name is fixed under @id, a property of any type
		assertFalse(lookup("#otherProperties").tryQuery().isPresent());
		clickOn("#addRequiredType-Dataset");
		assertEquals(List.of("Person", "Dataset"), root.getTypes());
		assertFalse(lookup("#requiredType-Dataset").tryQuery().isPresent());
	}
}
