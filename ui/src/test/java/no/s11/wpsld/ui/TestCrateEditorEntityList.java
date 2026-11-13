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
		// Once the editor is there
		entities();
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

	/** View > Entities by Part: the hasPart hierarchy; the selection kept between the trees */
	@Test
	public void byPart() throws Exception {
		EntityTreeView tree = openByFolder();
		choose("menuListByPart");
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(List.of("Survey", "  data", "  https://example.com/z.csv", "  raw/2025/a.txt", "File",
				"  docs/cited.pdf", "Person", "  Alice"), rows(tree));
		interact(() -> tree.getTreeItem(1).setExpanded(true));
		assertEquals("    counts.csv", rows(tree).get(2));
		interact(() -> tree.getSelectionModel().clearAndSelect(4));
		assertEquals("raw/2025/a.txt", lookup("#entityId").queryAs(TextField.class).getText());
		choose("menuListByFolder");
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals("raw/2025/a.txt", tree.selectedEntities().get(0).getId());
		assertEquals("      a.txt", rows(tree).get(tree.getSelectionModel().getSelectedIndex()));
		choose("menuListByPart");
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals("  raw/2025/a.txt", rows(tree).get(tree.getSelectionModel().getSelectedIndex()));
		choose("menuListByType");
		assertEquals("raw/2025/a.txt", entities().getSelectionModel().getSelectedItem().getId());
	}

	/**
	 * File > Open Folder without a metadata file: a new crate of the folder,
	 * its files greyed out to add; saved in it without asking
	 */
	@Test
	public void openFolder() throws Exception {
		Path folder = Files.createDirectories(temp.resolve("Bird survey"));
		Files.createDirectories(folder.resolve("data"));
		Files.writeString(folder.resolve("data/counts.csv"), "species,count\n");
		Files.writeString(folder.resolve("my notes.txt"), "notes");
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.openFolder(stage, folder)).get());
		choose("menuListByFolder");
		EntityTreeView tree = lookup("#entityTree").queryAs(EntityTreeView.class);
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(List.of("Bird survey", "  data ~", "  my notes.txt ~"), rows(tree));
		assertTrue(stage.getTitle(),
				stage.getTitle().contains("Bird survey" + java.io.File.separator + "ro-crate-metadata.json"));

		// The file and the folder, the folder first
		interact(() -> tree.getTreeItem(1).setExpanded(true));
		interact(() -> {
			tree.getSelectionModel().clearSelection();
			tree.getSelectionModel().selectIndices(1, 2);
		});
		interact(() -> menuItem("menuAddDataset").fire());
		interact(() -> tree.getSelectionModel().clearAndSelect(rows(tree).indexOf("    counts.csv ~")));
		interact(() -> menuItem("menuAddPart").fire());
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(List.of("Bird survey", "  data", "    counts.csv", "  my notes.txt ~"), rows(tree).subList(0, 4));
		assertEquals("data/counts.csv", lookup("#entityId").queryAs(TextField.class).getText());
		interact(() -> tree.getSelectionModel().clearAndSelect(3));
		interact(() -> menuItem("menuAddPart").fire());
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals("my%20notes.txt", lookup("#entityId").queryAs(TextField.class).getText());

		push(KeyCode.SHORTCUT, KeyCode.S);
		WaitForAsyncUtils.waitForFxEvents();
		// No licence nor description yet
		if (lookup("Save Anyway").tryQuery().isPresent()) {
			clickOn("Save Anyway");
		}
		String saved = Files.readString(folder.resolve("ro-crate-metadata.json"));
		assertTrue(saved, saved.contains("\"contentSize\" : \"14\""));
		assertTrue(saved, saved.contains("\"text/csv\""));
		assertTrue(saved, saved.contains("\"@id\" : \"my%20notes.txt\""));
	}

	/**
	 * A metadata file whose files are not beside it (e.g. downloaded) is
	 * detached, as is one of another name: its folder's files are not listed
	 */
	@Test
	public void detached() throws Exception {
		Path folder = Files.createDirectories(temp.resolve("Downloads"));
		Files.writeString(folder.resolve("unrelated.pdf"), "");
		Path metadata = folder.resolve("ro-crate-metadata.json");
		Files.writeString(metadata, CRATE);
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, metadata)).get());
		WaitForAsyncUtils.waitForFxEvents();
		// The warning saying so, beside the window
		interact(() -> List.copyOf(javafx.stage.Window.getWindows()).stream().filter(w -> w != stage)
				.forEach(w -> ((Stage) w).close()));
		choose("menuListByFolder");
		EntityTreeView tree = lookup("#entityTree").queryAs(EntityTreeView.class);
		assertFalse(rows(tree).toString(), rows(tree).contains("  unrelated.pdf ~"));
		// Attached after all, its folder's files are listed
		interact(() -> ((javafx.scene.control.CheckMenuItem) menuItem("menuAttached")).setSelected(true));
		interact(() -> menuItem("menuAttached").fire());
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(rows(tree).toString(), rows(tree).contains("  unrelated.pdf ~"));
	}

	/**
	 * Save As ro-crate-metadata.json into a folder without the crate's files
	 * asks first, telling which match
	 */
	@Test
	public void saveAsIntoOtherFolder() throws Exception {
		Path from = Files.createDirectories(temp.resolve("survey"));
		Files.createDirectories(from.resolve("data"));
		Files.writeString(from.resolve("data/counts.csv"), "");
		Files.writeString(from.resolve("ro-crate-metadata.json"), CRATE);
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, from.resolve("ro-crate-metadata.json"))).get());
		Path to = Files.createDirectories(temp.resolve("elsewhere"));
		Files.writeString(to.resolve("other.txt"), "");
		Path target = to.resolve("ro-crate-metadata.json");
		no.s11.wpsld.crate.Crate named = new no.s11.wpsld.crate.Crate();
		named.getRoot().set("name", Optional.of(com.fasterxml.jackson.databind.node.TextNode.valueOf("Bird survey, 2025!")));
		assertEquals("bird-survey-2025-ro-crate-metadata.json", CrateEditor.detachedName(named));

		var saving = WaitForAsyncUtils.asyncFx(() -> app.saveAsChecked(stage, target));
		WaitForAsyncUtils.waitForFxEvents();
		String text = lookup(".dialog-pane .content").queryAs(javafx.scene.control.Label.class).getText();
		assertTrue(text, text.contains("0 of the crate's 3 files are in " + to + "; 3 are not, e.g. data/"));
		assertTrue(text, text.contains("The folder has 1 file the crate doesn't describe, e.g. other.txt."));
		assertTrue(text, text.contains("such as survey-ro-crate-metadata.json"));
		clickOn("Cancel");
		assertFalse(saving.get());
		assertFalse(Files.exists(target));

		saving = WaitForAsyncUtils.asyncFx(() -> app.saveAsChecked(stage, target));
		WaitForAsyncUtils.waitForFxEvents();
		clickOn("Save Here");
		assertTrue(saving.get());
		assertTrue(Files.exists(target));
	}

	/**
	 * In an attached crate, entities whose files are not in its folder are
	 * marked; Crate > Check Web Availability marks web parts not available
	 */
	@Test
	public void problems() throws Exception {
		com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer
				.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/ok.csv", exchange -> {
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
		String web = "http://127.0.0.1:" + server.getAddress().getPort();
		try {
			Path folder = Files.createDirectories(temp.resolve("survey"));
			Files.writeString(folder.resolve("counts.csv"), "");
			Files.writeString(folder.resolve("ro-crate-metadata.json"), """
					{"@context": "https://w3id.org/ro/crate/1.3/context", "@graph": [
					 {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"}},
					 {"@id": "./", "@type": "Dataset", "name": "Survey", "hasPart": [{"@id": "counts.csv"},
					  {"@id": "gone.csv"}, {"@id": "WEB/ok.csv"}, {"@id": "WEB/gone.csv"}]},
					 {"@id": "counts.csv", "@type": "File"},
					 {"@id": "gone.csv", "@type": "File"},
					 {"@id": "WEB/ok.csv", "@type": "File"},
					 {"@id": "WEB/gone.csv", "@type": "File"}
					]}""".replace("WEB", web));
			Stage stage = (Stage) entities().getScene().getWindow();
			assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, folder.resolve("ro-crate-metadata.json"))).get());
			EntityTreeView tree = openTree();
			assertEquals(List.of("  gone.csv"), problemRows(tree));

			var task = WaitForAsyncUtils.asyncFx(() -> app.checkWeb(stage).orElseThrow()).get();
			assertEquals(2, task.get(10, TimeUnit.SECONDS).size());
			WaitForAsyncUtils.waitForFxEvents();
			assertTrue(lookup("1 of 2 web resources are not available").tryQuery().isPresent());
			assertEquals(List.of("  gone.csv", "  " + web + "/gone.csv"), problemRows(tree));

			// Detached, its files are not looked for
			interact(() -> ((javafx.scene.control.CheckMenuItem) menuItem("menuAttached")).setSelected(false));
			interact(() -> menuItem("menuAttached").fire());
			WaitForAsyncUtils.waitForFxEvents();
			assertEquals(List.of("  " + web + "/gone.csv"), problemRows(tree));
		} finally {
			server.stop(0);
		}
	}

	private EntityTreeView openTree() {
		choose("menuListByFolder");
		WaitForAsyncUtils.waitForFxEvents();
		return lookup("#entityTree").queryAs(EntityTreeView.class);
	}

	/** The tree's rows shown as having a problem */
	private List<String> problemRows(EntityTreeView tree) {
		return lookup("#entityTree .tree-cell").queryAllAs(javafx.scene.control.TreeCell.class).stream()
				.filter(cell -> cell.getStyleClass().contains("problem") && cell.getTreeItem() != null)
				.sorted(java.util.Comparator.comparing(cell -> tree.getRow(cell.getTreeItem())))
				.map(cell -> rows(tree).get(tree.getRow(cell.getTreeItem()))).toList();
	}

	/**
	 * A crate of RO-Crate 1.1 or later saved as ro-crate-metadata.jsonld, RO-Crate
	 * 1.0's name, is saved as ro-crate-metadata.json instead, if chosen; a
	 * symbolic link of that name is left as it is
	 */
	@Test
	public void legacyName() throws Exception {
		Path folder = Files.createDirectories(temp.resolve("legacy"));
		Path legacy = folder.resolve("ro-crate-metadata.jsonld");
		Files.writeString(legacy, CRATE);
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, legacy)).get());
		interact(() -> List.copyOf(javafx.stage.Window.getWindows()).stream().filter(w -> w != stage)
				.forEach(w -> ((Stage) w).close()));
		var saving = WaitForAsyncUtils.asyncFx(() -> app.saveTo(stage, legacy));
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("Save as ro-crate-metadata.json?").tryQuery().isPresent());
		clickOn("Save as .json");
		assertTrue(saving.get());
		assertFalse(Files.exists(legacy));
		Path json = folder.resolve("ro-crate-metadata.json");
		assertTrue(Files.readString(json).contains("Survey"));
		assertTrue(stage.getTitle(), stage.getTitle().contains("ro-crate-metadata.json"));

		// As some web sites need: a link to it can stay
		try {
			Files.createSymbolicLink(legacy, json.getFileName());
		} catch (UnsupportedOperationException | java.io.IOException e) {
			return;
		}
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.saveTo(stage, json)).get());
		assertTrue(Files.isSymbolicLink(legacy));
	}

	/** Saving an attached crate with an HTML preview asks once to move it aside */
	@Test
	public void stalePreview() throws Exception {
		Path folder = Files.createDirectories(temp.resolve("previewed"));
		Files.createDirectories(folder.resolve("data"));
		Files.writeString(folder.resolve("data/counts.csv"), "");
		Files.writeString(folder.resolve("ro-crate-metadata.json"), CRATE);
		Path preview = folder.resolve("ro-crate-preview.html");
		Files.writeString(preview, "<html></html>");
		Files.setLastModifiedTime(preview, java.nio.file.attribute.FileTime.from(java.time.Instant.parse("2024-12-24T12:00:00Z")));
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, folder.resolve("ro-crate-metadata.json"))).get());
		var saving = WaitForAsyncUtils.asyncFx(() -> app.saveTo(stage, folder.resolve("ro-crate-metadata.json")));
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("The crate's HTML preview is out of date").tryQuery().isPresent());
		clickOn("Move Aside");
		assertTrue(saving.get());
		assertFalse(Files.exists(preview));
		assertTrue(Files.exists(folder.resolve("ro-crate-preview_files/old-ro-crate-preview-2024-12-24.html")));
		// Not asked again for this crate
		Files.writeString(preview, "<html></html>");
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.saveTo(stage, folder.resolve("ro-crate-metadata.json"))).get());
		assertTrue(Files.exists(preview));
	}

	/**
	 * View > Show Files Not in Crate, as each view has it: on by folder, off
	 * by type and part; Open Folder lists by folder
	 */
	@Test
	public void showFiles() throws Exception {
		Path folder = Files.createDirectories(temp.resolve("Bird survey"));
		Files.writeString(folder.resolve("my notes.txt"), "notes");
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.openFolder(stage, folder)).get());
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(((RadioMenuItem) menuItem("menuListByFolder")).isSelected());
		EntityTreeView tree = lookup("#entityTree").queryAs(EntityTreeView.class);
		assertEquals(List.of("Bird survey", "  my notes.txt ~"), rows(tree));

		choose("menuListByType");
		assertEquals(List.of("Bird survey"), labels());
		javafx.scene.control.CheckMenuItem files = (javafx.scene.control.CheckMenuItem) menuItem("menuShowFiles");
		interact(() -> {
			files.setSelected(true);
			files.fire();
		});
		assertEquals(List.of("Bird survey", "my%20notes.txt"), labels());
		// Selected, it isn't edited, but can be added
		interact(() -> entities().getSelectionModel().clearAndSelect(1));
		assertEquals("./", lookup("#entityId").queryAs(TextField.class).getText());
		interact(() -> menuItem("menuAddPart").fire());
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals("my%20notes.txt", lookup("#entityId").queryAs(TextField.class).getText());
		assertEquals(List.of("Bird survey", "my%20notes.txt"), labels());
		assertEquals(List.of("./", "my%20notes.txt"), entities().getItems().stream().map(Entity::getId).toList());

		Files.writeString(folder.resolve("more.txt"), "");
		choose("menuListByPart");
		assertFalse(files.isSelected());
		interact(() -> {
			files.setSelected(true);
			files.fire();
		});
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(rows(tree).toString(), rows(tree).containsAll(List.of("(Not in crate)", "  more.txt ~")));
		// Each view as it was
		choose("menuListByType");
		assertTrue(files.isSelected());
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
