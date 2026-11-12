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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import com.fasterxml.jackson.databind.node.ArrayNode;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.EntityListing.TypeCount;
import no.s11.wpsld.form.EntityTree;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * The entity list: the root first, then by type, name and @id; and only
 * those of a type chosen.
 */
public class TestCrateEditorEntityList extends ApplicationTest {
	private CrateEditor app;
	private Path temp;

	/** A crate with files in folders, one not a part, a web resource, and a person */
	private static final String CRATE = """
			{"@context": "https://w3id.org/ro/crate/1.3/context", "@graph": [
			 {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
			  "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
			 {"@id": "./", "@type": "Dataset", "name": "Survey",
			  "hasPart": [{"@id": "data/"}, {"@id": "raw/2025/a.txt"}, {"@id": "https://example.com/z.csv"}],
			  "mentions": [{"@id": "docs/cited.pdf"}], "author": {"@id": "#alice"}},
			 {"@id": "data/", "@type": "Dataset", "name": "Data", "hasPart": {"@id": "data/counts.csv"}},
			 {"@id": "data/counts.csv", "@type": "File", "name": "Bird counts"},
			 {"@id": "raw/2025/a.txt", "@type": "File"},
			 {"@id": "docs/cited.pdf", "@type": "File"},
			 {"@id": "https://example.com/z.csv", "@type": "File"},
			 {"@id": "#alice", "@type": "Person", "name": "Alice"}
			]}
			""";

	@Override
	public void start(Stage stage) throws Exception {
		temp = Files.createTempDirectory("wpsld-entity-list");
		app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm,
				new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
						Optional.of(temp.resolve("profiles")), List.of()),
				Optional.of(temp.resolve("settings.json")));
		app.init();
		app.start(stage);
	}

	private ListView<Entity> entities() {
		try {
			WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#entities").tryQuery().isPresent()
					&& lookup("#save").tryQuery().isPresent());
		} catch (Exception e) {
			throw new AssertionError("Editor did not appear", e);
		}
		return lookup("#entities").queryListView();
	}

	@SuppressWarnings("unchecked")
	private ComboBox<TypeCount> entityType() {
		return lookup("#entityType").queryAs(ComboBox.class);
	}

	private void addNamed(String type, String name) {
		clickOn("#addEntity");
		clickOn("#typeSearch").write(type).type(KeyCode.ENTER);
		clickOn("#header-name").write(name);
	}

	/** Open the crate above, listed by folder */
	private EntityTreeView openByFolder() throws Exception {
		return openByFolder(CRATE);
	}

	private EntityTreeView openByFolder(String json) throws Exception {
		Path metadata = temp.resolve("ro-crate-metadata.json");
		Files.writeString(metadata, json);
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, metadata)).get());
		choose("menuListByFolder");
		return lookup("#entityTree").queryAs(EntityTreeView.class);
	}

	/** As choosing the radio item in its menu */
	private void choose(String id) {
		interact(() -> ((RadioMenuItem) menuItem(id)).setSelected(true));
	}

	private MenuItem menuItem(String id) {
		return lookup("#menuBar").queryAs(MenuBar.class).getMenus().stream().flatMap(m -> m.getItems().stream())
				.filter(i -> id.equals(i.getId())).findFirst().orElseThrow();
	}

	/** The tree's rows as shown, indented, "~" if greyed out */
	private static List<String> rows(EntityTreeView tree) {
		List<String> rows = new ArrayList<>();
		for (int i = 0; i < tree.getExpandedItemCount(); i++) {
			TreeItem<EntityTree.Node> item = tree.getTreeItem(i);
			EntityTree.Node node = item.getValue();
			boolean grey = (node.kind() == EntityTree.Kind.FILE || node.kind() == EntityTree.Kind.FOLDER)
					&& !node.data();
			rows.add("  ".repeat(tree.getTreeItemLevel(item) - 1) + node.name() + (grey ? " ~" : ""));
		}
		return rows;
	}

	private List<String> labels() {
		return entities().getItems().stream().map(Entity::getLabel).toList();
	}

	private void showType(String type) {
		interact(() -> entityType().setValue(
				entityType().getItems().stream().filter(t -> t.type().equals(type)).findFirst().orElseThrow()));
	}

	/**
	 * Sorted by type then name once a name is typed and the field left, not
	 * while typing; the root stays first
	 */
	@Test
	public void sorted() throws Exception {
		Entity root = entities().getItems().get(0);
		addNamed("Person", "alice");
		addNamed("Person", "Zoë");
		// Not yet sorted, as still typing: listed as when it had no name
		Entity zoe = entities().getSelectionModel().getSelectedItem();
		assertEquals("Zoë", zoe.getLabel());
		assertEquals(zoe, entities().getItems().get(1));
		addNamed("Organization", "University");
		assertEquals(List.of(root.getLabel(), "University", "alice", "Zoë"), labels());
		// Still the one edited, though its place changed
		assertEquals("University", entities().getSelectionModel().getSelectedItem().getLabel());
		assertEquals("University", lookup("#header-name").queryAs(TextField.class).getText());
	}

	/**
	 * Only entities of a type and its subtypes; all again when one not of it
	 * is added, or the one edited is no longer of it
	 */
	@Test
	public void ofType() throws Exception {
		Entity root = entities().getItems().get(0);
		addNamed("Person", "Alice");
		addNamed("Person", "Bob");
		addNamed("File", "Data");
		clickOn("#entities");
		assertEquals(List.of("All types (4)", "CreativeWork (2)", "Dataset (1)", "File (1)", "Person (2)"),
				entityType().getItems().stream().map(entityType().getConverter()::toString).toList());

		showType("Person");
		assertEquals(List.of("Alice", "Bob"), labels());
		showType("CreativeWork");
		assertEquals(List.of(root.getLabel(), "Data"), labels());

		showType("Person");
		interact(() -> entities().getSelectionModel().clearAndSelect(1));
		assertEquals("Bob", lookup("#header-name").queryAs(TextField.class).getText());
		// Bob becomes an Organization: no longer a Person, so all are listed
		clickOn("#addType");
		clickOn("#typeSearch").write("Organization").type(KeyCode.ENTER);
		clickOn("#removeType-Person");
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals("", entityType().getValue().type());
		assertEquals("Bob", entities().getSelectionModel().getSelectedItem().getLabel());

		showType("Person");
		assertEquals(List.of("Alice"), labels());
		addNamed("Place", "Oslo");
		assertEquals("", entityType().getValue().type());
		assertEquals("Oslo", entities().getSelectionModel().getSelectedItem().getLabel());
	}

	/**
	 * When the entity edited is not of the type chosen, none is selected,
	 * though others selected are; it is selected again when listed again
	 */
	/** Of the type chosen, the first listed can be removed: it isn't the root */
	@Test
	public void removeFirstOfType() throws Exception {
		addNamed("Person", "Alice");
		addNamed("Person", "Bob");
		showType("Person");
		clickOn(lookup("#entities .list-cell").nth(0).queryAs(javafx.scene.Node.class));
		assertEquals("Alice", entities().getSelectionModel().getSelectedItem().getLabel());
		assertFalse(lookup("#removeEntity").query().isDisabled());
		clickOn("#removeEntity");
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(List.of("Bob"), labels());
	}

	@Test
	public void removeFirstOfTypeSelectedBefore() throws Exception {
		addNamed("Person", "Alice");
		addNamed("Person", "Bob");
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(
				entities().getItems().stream().filter(e -> e.getLabel().equals("Alice")).findFirst().orElseThrow())));
		showType("Person");
		assertEquals("Alice", entities().getSelectionModel().getSelectedItem().getLabel());
		assertFalse(lookup("#removeEntity").query().isDisabled());
	}

	/** Del in the list, or Edit > Remove, removes those selected, never the root */
	@Test
	public void removeByKey() throws Exception {
		Entity root = entities().getItems().get(0);
		addNamed("Person", "Alice");
		addNamed("Person", "Bob");
		clickOn("#entities");
		interact(() -> entities().getSelectionModel().clearAndSelect(labels().indexOf("Alice")));
		type(KeyCode.DELETE);
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(List.of(root.getLabel(), "Bob"), labels());
		// Not while typing in a field
		interact(() -> entities().getSelectionModel().clearAndSelect(labels().indexOf("Bob")));
		clickOn("#header-name");
		assertTrue(menuItem("menuRemove").isDisable());
		type(KeyCode.END, KeyCode.BACK_SPACE, KeyCode.LEFT, KeyCode.DELETE);
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(2, labels().size());
		assertEquals("B", lookup("#header-name").queryAs(TextField.class).getText());
		// The root, with another, leaves the root
		clickOn("#entities");
		interact(() -> entities().getSelectionModel().selectAll());
		assertFalse(menuItem("menuRemove").isDisable());
		interact(() -> menuItem("menuRemove").fire());
		assertEquals(List.of(root.getLabel()), labels());
		assertTrue(menuItem("menuRemove").isDisable());
	}

	/** → beside a reference shows the entity it links to; ↗ opens a web @id */
	@Test
	public void openFromForm() throws Exception {
		addNamed("Organization", "Example University");
		String university = lookup("#entityId").queryAs(TextField.class).getText();
		assertTrue(lookup("#openId").query().isDisabled());
		addNamed("Person", "Alice");
		assertTrue(lookup("#open-affiliation-0").query().isDisabled());
		interact(() -> lookup("#value-affiliation-0").queryAs(ComboBox.class).getEditor().setText(university));
		assertFalse(lookup("#open-affiliation-0").query().isDisabled());
		clickOn("#open-affiliation-0");
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals("Example University", lookup("#header-name").queryAs(TextField.class).getText());
		assertEquals("Example University", entities().getSelectionModel().getSelectedItem().getLabel());
		// A web address as @id can be opened
		TextField id = lookup("#entityId").queryAs(TextField.class);
		interact(() -> id.setText("https://ror.org/01xtthb56"));
		assertFalse(lookup("#openId").query().isDisabled());
	}

	/**
	 * An entity of several types is in the folder of each; selected in
	 * one, it is selected in each, also after listing by type and back, and
	 * copied once
	 */
	@Test
	public void severalPlaces() throws Exception {
		EntityTreeView tree = openByFolder("""
				{"@context": "https://w3id.org/ro/crate/1.2/context", "@graph": [
				{"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"}},
				{"@id": "./", "@type": "Dataset", "name": "Survey", "hasPart": [{"@id": "report.html"}]},
				{"@id": "report.html", "@type": "File"},
				{"@id": "report.html#table2", "@type": "Table", "name": "Counts"},
				{"@id": "#uio", "@type": ["Organization", "Place"], "name": "UiO"}
				]}""");
		assertEquals(List.of("Survey", "  report.html", "Organization", "  UiO", "Place", "  UiO", "Table", "  Counts"),
				rows(tree));
		interact(() -> tree.getSelectionModel().clearAndSelect(3));
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(List.of(3, 5), selectedRows(tree));
		assertEquals(3, tree.getFocusModel().getFocusedIndex());
		assertEquals("UiO", lookup("#header-name").queryAs(TextField.class).getText());
		assertEquals(1, tree.selectedEntities().size());
		// The part of a file: its place under the file is selected once shown
		interact(() -> tree.getSelectionModel().clearAndSelect(7));
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(List.of(7), selectedRows(tree));
		interact(() -> tree.getTreeItem(1).setExpanded(true));
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals("    #table2 ~", rows(tree).get(2));
		assertEquals(List.of(2, 8), selectedRows(tree));
		choose("menuListByType");
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals("Counts", entities().getSelectionModel().getSelectedItem().getLabel());
		choose("menuListByFolder");
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(List.of(2, 8), selectedRows(tree));
	}

	private static List<Integer> selectedRows(EntityTreeView tree) {
		return tree.getSelectionModel().getSelectedIndices().stream().sorted().toList();
	}

	/**
	 * Crate > Add Dataset for Folder and Add to Has Part bring greyed out
	 * folders and files into the crate's hasPart
	 */
	@Test
	public void addParts() throws Exception {
		EntityTreeView tree = openByFolder();
		javafx.scene.control.Menu crateMenu = lookup("#menuBar").queryAs(MenuBar.class).getMenus().stream()
				.filter(m -> "menuCrate".equals(m.getId())).findFirst().orElseThrow();
		Runnable opened = () -> interact(() -> crateMenu.getOnShowing().handle(null));
		interact(() -> {
			tree.getSelectionModel().clearSelection();
			tree.getSelectionModel().selectIndices(2, 3);
		});
		opened.run();
		assertFalse(menuItem("menuAddDataset").isDisable());
		assertTrue(menuItem("menuAddPart").isDisable());
		interact(() -> menuItem("menuAddDataset").fire());
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(List.of("Survey", "  data", "  docs", "  raw", "External resources", "  https://example.com/z.csv",
				"Person", "  Alice"), rows(tree));
		assertEquals(List.of("docs/", "raw/"), tree.selectedEntities().stream().map(Entity::getId).toList());

		// The cited file, mentioned by the root, a part of docs/ now
		interact(() -> tree.getTreeItem(2).setExpanded(true));
		interact(() -> tree.getSelectionModel().clearAndSelect(3));
		assertEquals("    cited.pdf ~", rows(tree).get(3));
		opened.run();
		assertTrue(menuItem("menuAddDataset").isDisable());
		assertFalse(menuItem("menuAddPart").isDisable());
		interact(() -> menuItem("menuAddPart").fire());
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals("    cited.pdf", rows(tree).get(3));
		assertEquals("docs/cited.pdf", tree.selectedEntities().get(0).getId());
		// No longer mentioned by the root, as it is linked as a part
		interact(() -> tree.getSelectionModel().clearAndSelect(0));
		assertFalse(lookup("#preview").queryAs(TextArea.class).getText().contains("\"mentions\""));
	}

	@Test
	public void editedNotListed() throws Exception {
		addNamed("Person", "Alice");
		addNamed("File", "Data");
		clickOn("#entities");
		Entity alice = entities().getItems().stream().filter(e -> e.getLabel().equals("Alice")).findFirst()
				.orElseThrow();
		Entity data = entities().getItems().stream().filter(e -> e.getLabel().equals("Data")).findFirst()
				.orElseThrow();
		interact(() -> {
			entities().getSelectionModel().clearSelection();
			entities().getSelectionModel().select(alice);
			entities().getSelectionModel().select(data);
		});
		assertEquals("Data", lookup("#header-name").queryAs(TextField.class).getText());

		showType("Person");
		assertEquals(List.of("Alice"), labels());
		assertTrue(entities().getSelectionModel().getSelectedItems().isEmpty());
		// Still editing Data; nothing to copy or remove
		assertEquals("Data", lookup("#header-name").queryAs(TextField.class).getText());
		assertTrue(lookup("#removeEntity").queryButton().isDisabled());

		showType("");
		assertEquals(List.of(data), entities().getSelectionModel().getSelectedItems());
		assertEquals("Data", lookup("#header-name").queryAs(TextField.class).getText());

		// The root and Data are both CreativeWorks: both stay selected
		Entity root = entities().getItems().get(0);
		interact(() -> {
			entities().getSelectionModel().clearSelection();
			entities().getSelectionModel().select(root);
			entities().getSelectionModel().select(data);
		});
		showType("CreativeWork");
		assertEquals(List.of(root, data), entities().getSelectionModel().getSelectedItems());
		assertEquals(data, entities().getSelectionModel().getSelectedItem());
	}

	/**
	 * View > Entities by Folder: the tree, top folders open; the entity
	 * edited selected; folders opened to show one selected
	 */
	@Test
	public void byFolder() throws Exception {
		EntityTreeView tree = openByFolder();
		assertFalse(lookup("#entities").tryQuery().isPresent());
		assertEquals(List.of("Survey", "  data", "  docs ~", "  raw ~", "External resources", "  https://example.com/z.csv", "Person", "  Alice"),
				rows(tree));
		// The root, still edited, selected
		assertEquals("Survey", tree.getSelectionModel().getSelectedItem().getValue().name());

		interact(() -> tree.getTreeItem(1).setExpanded(true));
		interact(() -> tree.getSelectionModel().clearAndSelect(2));
		assertEquals("data/counts.csv", lookup("#entityId").queryAs(TextField.class).getText());
		assertEquals(List.of("Survey", "  data", "    counts.csv", "  docs ~", "  raw ~", "External resources", "  https://example.com/z.csv", "Person", "  Alice"), rows(tree));

		// Folders without an entity: selected, but the form stays
		interact(() -> tree.getSelectionModel().clearAndSelect(4));
		assertEquals("data/counts.csv", lookup("#entityId").queryAs(TextField.class).getText());
		assertTrue(lookup("#removeEntity").queryButton().isDisabled());

		// Back to the list: the file still selected
		choose("menuListByType");
		assertEquals("Bird counts", entities().getSelectionModel().getSelectedItem().getLabel());
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(
				entities().getItems().stream().filter(e -> e.getId().equals("raw/2025/a.txt")).findFirst()
						.orElseThrow())));
		choose("menuListByFolder");
		assertEquals("raw/2025/a.txt", tree.selectedEntities().get(0).getId());
		assertEquals(List.of("Survey", "  data", "    counts.csv", "  docs ~", "  raw ~", "    2025 ~",
				"      a.txt", "External resources", "  https://example.com/z.csv", "Person", "  Alice"), rows(tree));
	}

	/**
	 * A file named in the form keeps its place, shown with its name; the
	 * contextual file not in hasPart greyed out until it is a part
	 */
	@Test
	public void byFolderChanges() throws Exception {
		EntityTreeView tree = openByFolder();
		interact(() -> tree.getTreeItem(2).setExpanded(true));
		assertEquals("    cited.pdf ~", rows(tree).get(3));
		interact(() -> tree.getSelectionModel().clearAndSelect(3));
		clickOn("#header-name").write("Cited paper");
		// Shown with its name as typed
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("cited.pdf — Cited paper").tryQuery().isPresent());
		// Made a part of the crate: no longer greyed out, and still selected
		Entity root = tree.getTreeItem(0).getValue().entity().orElseThrow();
		interact(() -> ((ArrayNode) root.get("hasPart").orElseThrow()).addObject().put("@id", "docs/cited.pdf"));
		// Leaving the field sorts and lists again
		interact(tree::requestFocus);
		assertEquals("    cited.pdf", rows(tree).get(3));
		assertEquals("docs/cited.pdf", tree.selectedEntities().get(0).getId());
	}
}
