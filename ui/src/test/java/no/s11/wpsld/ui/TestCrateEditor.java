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
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.testfx.util.WaitForAsyncUtils;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.scene.Node;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Clipboard;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.DialogPane;
import javafx.scene.control.MenuButton;
import javafx.scene.control.SplitPane;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.FormFields;

/**
 * GUI tests of the crate window: adding, linking and repairing entities,
 * undo, the JSON-LD, menus, the sidebar, the preview and profiles. The
 * others are in TestCrateEditor* (see CrateEditorTest).
 */
public class TestCrateEditor extends CrateEditorTest {

	@Test
	public void addEntityChoosesType() {
		assertEquals(1, entities().getItems().size());
		addEntity("person");
		assertEquals(2, entities().getItems().size());
		assertEquals(List.of("Person"), entities().getItems().get(1).getTypes());
		// Not left dangling: the root mentions it
		Entity root = entities().getItems().get(0);
		assertEquals(List.of(entities().getItems().get(1).getId()),
				root.getAll("mentions").stream().map(v -> v.get("@id").asText()).toList());
	}

	@Test
	public void removingTheOnlyLinkMentions() {
		entities();
		addEntity("person");
		addEntity("organization");
		Entity root = entities().getItems().get(0);
		Entity person = entities().getItems().get(1);
		Entity org = entities().getItems().get(2);
		// The organization linked from the person, no longer from the root
		interact(() -> {
			person.set("affiliation", Optional.of(JsonNodeFactory.instance.objectNode().put("@id", org.getId())));
			root.setAll("mentions", List.of(JsonNodeFactory.instance.objectNode().put("@id", person.getId())));
		});
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(person)));
		clickOn("#removeEntity");
		assertTrue(root.getAll("mentions").stream().map(v -> v.get("@id").asText()).toList()
				.contains(org.getId()));
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
		clickOn("Merge");
		assertTrue(lookup(".dialog-pane").tryQuery().isEmpty());
		assertEquals(List.of(download), entities().getItems().subList(1, entities().getItems().size()));
		assertEquals(List.of("DataDownload", "Person"), download.getTypes());
		assertFalse(entities().getItems().contains(person));
	}

	/**
	 * Repairing adds a Thing for each reference to an entity not in the
	 * crate, and mentions what is not linked otherwise, and only that.
	 */
	@Test
	public void repairCrate() {
		entities();
		addEntity("person");
		addEntity("person");
		Entity root = entities().getItems().get(0);
		Entity alice = entities().getItems().get(1);
		Entity bob = entities().getItems().get(2);
		// Alice is now the crate's author, with a mistyped affiliation; Bob is
		// still only mentioned
		interact(() -> {
			root.set("author", Optional.of(JsonNodeFactory.instance.objectNode().put("@id", alice.getId())));
			alice.set("affiliation", Optional.of(JsonNodeFactory.instance.objectNode().put("@id", "#uni")));
		});
		// Saving leaves the mentions as they are
		clickOn("#save");
		clickOn("Cancel");
		assertEquals(List.of(alice.getId(), bob.getId()),
				root.getAll("mentions").stream().map(v -> v.get("@id").asText()).toList());

		MenuItem repair = menuItem("menuRepair");
		WaitForAsyncUtils.asyncFx(repair::fire);
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup(".dialog-pane").queryAs(javafx.scene.control.DialogPane.class).getContentText()
				.contains("#uni"));
		clickOn("OK");
		assertEquals(List.of(bob.getId()),
				root.getAll("mentions").stream().map(v -> v.get("@id").asText()).toList());
		Entity uni = entities().getItems().get(3);
		assertEquals("#uni", uni.getId());
		assertEquals(List.of("Thing"), uni.getTypes());

		// Nothing more to repair
		WaitForAsyncUtils.asyncFx(repair::fire);
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("Nothing to repair.").tryQuery().isPresent());
		clickOn("OK");
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
		// Redo as on Windows and Linux; on macOS Command-Y isn't Redo
		if (CrateFile.onMac()) {
			push(KeyCode.SHORTCUT, KeyCode.SHIFT, KeyCode.Z);
		} else {
			push(KeyCode.SHORTCUT, KeyCode.Y);
		}
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
		clickOn("Merge");
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
		clickOn("Don't Save");
		push(KeyCode.SHORTCUT, KeyCode.Z);
		assertEquals(1, entities().getItems().size());
	}

	/**
	 * A text field pastes once, also with Edit > Paste's shortcut; and the
	 * menu's text commands are only enabled in a text field, except Copy and
	 * Paste of entities in the entity list.
	 */
	@Test
	public void textEditing() {
		entities();
		MenuItem paste = menuItem("menuPaste");
		clickOn("#entities");
		assertTrue(menuItem("menuCut").isDisable());
		assertTrue(menuItem("menuSelectAll").isDisable());
		clickOn("#preview");
		assertTrue(paste.isDisable());
		TextField name = lookup("#value-name-0").queryAs(TextField.class);
		clickOn(name).write("abc");
		assertFalse(paste.isDisable());
		push(KeyCode.SHORTCUT, KeyCode.A);
		push(KeyCode.SHORTCUT, KeyCode.C);
		push(KeyCode.END);
		push(KeyCode.SHORTCUT, KeyCode.V);
		assertEquals("abcabc", name.getText());
		// From the menu
		interact(paste::fire);
		assertEquals("abcabcabc", name.getText());
	}

	/**
	 * View > No Sidebar hides the entities; choosing a view of them shows
	 * them again, as they were, or in another view
	 */
	@Test
	public void sidebar() {
		entities();
		SplitPane split = lookup(".split-pane").queryAs(SplitPane.class);
		assertEquals(3, split.getItems().size());
		interact(() -> ((RadioMenuItem) menuItem("menuNoSidebar")).setSelected(true));
		assertEquals(2, split.getItems().size());
		assertTrue(lookup("#entities").tryQuery().isEmpty());
		interact(() -> ((RadioMenuItem) menuItem("menuListByType")).setSelected(true));
		assertEquals(3, split.getItems().size());
		assertTrue(lookup("#entities").tryQuery().isPresent());
		// Hidden, then shown by folder
		interact(() -> ((RadioMenuItem) menuItem("menuNoSidebar")).setSelected(true));
		interact(() -> ((RadioMenuItem) menuItem("menuListByFolder")).setSelected(true));
		assertEquals(3, split.getItems().size());
		assertTrue(lookup("#entityTree").tryQuery().isPresent());
	}

	/**
	 * The entity's JSON-LD edited in its tab: applied as one undo step, its
	 * @id renamed with the references to it; refused saying why, changing
	 * nothing; reverted; and pasted in, applied on leaving it
	 */
	@Test
	public void jsonEdited() throws Exception {
		entities();
		addEntity("person");
		Entity person = entities().getSelectionModel().getSelectedItem();
		Entity root = entities().getItems().get(0);
		clickOn("#jsonTab");
		TextInputControl json = lookup("#preview").queryTextInputControl();
		Button apply = lookup("#jsonApply").queryButton();
		assertTrue(apply.isDisable());
		String before = json.getText();

		// Not right: says why, and changes nothing
		interact(() -> json.setText("{\"@id\": \"#alice\""));
		assertFalse(apply.isDisable());
		interact(apply::fire);
		assertTrue(lookup("#jsonError").queryLabeled().getText(), lookup("#jsonError").queryLabeled().getText().startsWith("Not JSON"));
		assertEquals(List.of("Person"), person.getTypes());
		interact(lookup("#jsonRevert").queryButton()::fire);
		assertEquals(before, json.getText());
		assertEquals("", lookup("#jsonError").queryLabeled().getText());
		assertTrue(apply.isDisable());

		interact(() -> json.setText("{\"@id\": \"#alice\", \"@type\": [\"Person\", \"Author\"], "
				+ "\"name\": \"Alice\", \"email\": \"alice@example.org\"}"));
		interact(apply::fire);
		assertEquals("#alice", person.getId());
		assertEquals(List.of("Person", "Author"), person.getTypes());
		assertEquals("Alice", person.getLabel());
		assertEquals("Alice", lookup("#header-name").queryAs(TextField.class).getText());
		// The root's mention of it follows
		assertTrue(root.getAll("mentions").stream().anyMatch(v -> v.path("@id").asText().equals("#alice")));
		// Shown again as the crate writes it
		assertTrue(json.getText(), json.getText().contains("\"email\" : \"alice@example.org\""));
		assertTrue(apply.isDisable());
		Stage stage = (Stage) entities().getScene().getWindow();
		MenuItem undo = menuItem("menuUndo");
		assertEquals("_Undo Edit JSON-LD", WaitForAsyncUtils.asyncFx(() -> {
			app.nameUndo(stage, undo, menuItem("menuRedo"));
			return undo.getText();
		}).get());

		// Pasted in, applied on leaving it
		interact(() -> {
			ClipboardContent text = new ClipboardContent();
			text.putString("{\"@id\": \"#alice\", \"@type\": \"Person\", \"name\": \"Alice Smith\"}");
			Clipboard.getSystemClipboard().setContent(text);
		});
		clickOn(json);
		push(KeyCode.SHORTCUT, KeyCode.A);
		push(KeyCode.SHORTCUT, KeyCode.V);
		clickOn("#entities");
		assertEquals("Alice Smith", person.getLabel());
		assertEquals(List.of("Person"), person.getTypes());
		assertTrue(person.get("email").isEmpty());

		interact(undo::fire);
		Entity undone = entities().getItems().stream().filter(e -> e.getId().equals("#alice")).findFirst().orElseThrow();
		assertEquals("Alice", undone.getLabel());
	}

	/**
	 * Entities written in the JSON-LD edited, as ROHub writes a dataset's
	 * parts: added as entities of their own, said in a notice, and undone
	 * with the edit
	 */
	@Test
	public void jsonNestedFlattened() throws Exception {
		entities();
		interact(() -> entities().getSelectionModel().clearAndSelect(0));
		clickOn("#jsonTab");
		TextInputControl json = lookup("#preview").queryTextInputControl();
		int before = entities().getItems().size();
		interact(() -> json.setText("""
				{"@id": "./", "@type": "Dataset", "name": "Survey",
				 "hasPart": {"@id": "https://w3id.org/ro-id/x/folders/a", "@type": "Dataset", "name": "Folder A"},
				 "variableMeasured": {"@type": "PropertyValue", "name": "depth"}}"""));
		interact(lookup("#jsonApply").queryButton()::fire);
		assertEquals("", lookup("#jsonError").queryLabeled().getText());
		assertEquals(before + 2, entities().getItems().size());
		Entity folder = entities().getItems().stream().filter(e -> e.getId().equals("https://w3id.org/ro-id/x/folders/a"))
				.findFirst().orElseThrow();
		assertEquals("Folder A", folder.getLabel());
		assertTrue(entities().getItems().stream().anyMatch(e -> e.getTypes().equals(List.of("PropertyValue"))
				&& e.getLabel().equals("depth") && e.getId().startsWith("#propertyvalue-")));
		String notice = lookup("#noticeText").queryLabeled().getText();
		assertTrue(notice, notice.contains("added 2 entities written in it"));
		// The root shown again, as the crate writes it: references only
		assertTrue(json.getText(), json.getText().contains("\"@id\" : \"https://w3id.org/ro-id/x/folders/a\""));
		assertFalse(json.getText(), json.getText().contains("Folder A"));

		clickOn("#entities");
		interact(menuItem("menuUndo")::fire);
		assertEquals(before, entities().getItems().size());
	}

	/**
	 * The JSON-LD tab shows the entity selected, as it changes; View > Crate
	 * JSON-LD the whole crate's, made again with Refresh
	 */
	@Test
	public void jsonOfEntityAndCrate() throws Exception {
		entities();
		addEntity("person");
		Entity person = entities().getSelectionModel().getSelectedItem();
		clickOn("#jsonTab");
		TextInputControl tab = lookup("#preview").queryTextInputControl();
		assertTrue(tab.getText(), tab.getText().contains("\"@id\" : \"" + person.getId() + "\""));
		assertFalse(tab.getText(), tab.getText().contains("ro-crate-metadata.json"));
		clickOn(lookup("#header-name").queryAs(TextField.class)).write("Alice");
		waitForFx(5, () -> tab.getText().contains("Alice"));
		interact(() -> entities().getSelectionModel().clearAndSelect(0));
		assertTrue(tab.getText(), tab.getText().contains("\"@id\" : \"./\""));
		assertFalse(tab.getText(), tab.getText().contains("Alice"));

		interact(menuItem("menuCrateJson")::fire);
		TextInputControl crate = lookup("#crateJson").queryTextInputControl();
		assertTrue(crate.getText().contains("ro-crate-metadata.json"));
		assertTrue(crate.getText().contains("Alice"));
		interact(() -> person.set("name", Optional.of(JsonNodeFactory.instance.textNode("Bob"))));
		assertFalse(crate.getText().contains("Bob"));
		interact(lookup("#crateJsonRefresh").queryButton()::fire);
		assertTrue(crate.getText().contains("Bob"));
		interact(() -> crate.getScene().getWindow().hide());
	}

	/** Alt and a menu's letter opens it, as on Windows and Linux */
	@Test
	public void menuMnemonics() {
		entities();
		MenuBar bar = lookup("#menuBar").queryAs(MenuBar.class);
		assertEquals(List.of("_File", "_Edit", "_View", "_Insert", "_Crate", "_Help"), bar.getMenus().stream().map(Menu::getText).toList());
		assertTrue(bar.getMenus().stream().allMatch(Menu::isMnemonicParsing));
		// Each menu's items have their own letters
		for (Menu menu : bar.getMenus()) {
			List<Character> letters = menu.getItems().stream().map(MenuItem::getText).filter(t -> t != null)
					.map(t -> t.replace("__", "")).filter(t -> t.contains("_"))
					.map(t -> Character.toLowerCase(t.charAt(t.indexOf('_') + 1))).toList();
			assertEquals(menu.getText() + " " + letters, letters.size(), new java.util.HashSet<>(letters).size());
		}
		// macOS has no such keys
		if (!CrateFile.onMac()) {
			push(KeyCode.ALT, KeyCode.F);
			WaitForAsyncUtils.waitForFxEvents();
			assertTrue(bar.getMenus().get(0).isShowing());
			push(KeyCode.ESCAPE);
		}
	}

	/** Edit > Undo and Redo say what they would undo and redo */
	@Test
	public void namedUndo() {
		entities();
		Stage stage = (Stage) entities().getScene().getWindow();
		MenuItem undo = menuItem("menuUndo");
		MenuItem redo = menuItem("menuRedo");
		Runnable name = () -> interact(() -> app.nameUndo(stage, undo, redo));
		name.run();
		assertEquals("_Undo", undo.getText());
		addEntity("person");
		name.run();
		assertEquals("_Undo Add Person", undo.getText());
		assertEquals("_Redo", redo.getText());
		push(KeyCode.SHORTCUT, KeyCode.Z);
		name.run();
		assertEquals("_Redo Add Person", redo.getText());
		push(KeyCode.SHORTCUT, KeyCode.SHIFT, KeyCode.Z);
		// Typing in a field (the root's name): undone first
		interact(() -> entities().getSelectionModel().clearAndSelect(0));
		clickOn(lookup("#value-name-0").queryAs(TextField.class)).write("Bob");
		name.run();
		assertEquals("_Undo Typing", undo.getText());
		// Left: an edit of the name
		clickOn("#entities");
		name.run();
		assertEquals("_Undo Edit “Name”", undo.getText());
	}

	/**
	 * The preview's @id, on its own line: opens where it leads, a web address
	 * or a file beside the crate; #local, blank node and urn:uuid ids don't.
	 */
	@Test
	public void previewIdentifier() throws Exception {
		entities();
		Path dir = Files.createTempDirectory("crate");
		Files.writeString(dir.resolve("data.csv"), "a,b\n");
		List<String> ids = List.of("https://example.com/x", "data.csv", "missing.csv", "#local", "_:b0",
				"urn:uuid:3f2504e0-4f89-11d3-9a0c-0305e82c3301");
		Files.writeString(dir.resolve("ro-crate-metadata.json"), """
				{"@context": "https://w3id.org/ro/crate/1.3/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
				  {"@id": "./", "@type": "Dataset", "name": "A crate"},
				  """ + ids.stream().map(id -> "{\"@id\": \"" + id + "\", \"@type\": \"Thing\"}")
				.collect(java.util.stream.Collectors.joining(",")) + "]}");
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir)).get());
		WaitForAsyncUtils.waitForFxEvents();
		Map<String, Boolean> clickable = new java.util.LinkedHashMap<>();
		for (Entity entity : List.copyOf(entities().getItems())) {
			interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(entity)));
			clickable.put(entity.getId(), lookup("#previewId").query() instanceof Hyperlink);
		}
		assertEquals(Map.of("./", true, "https://example.com/x", true, "data.csv", true, "missing.csv", false,
				"#local", false, "_:b0", false, "urn:uuid:3f2504e0-4f89-11d3-9a0c-0305e82c3301", false), clickable);
	}

	/**
	 * A place, as RO-Crate 1.3 describes it: its geo a Geometry with
	 * Well-Known Text, shown on a map from the preview, and new geometries
	 * made from its geo field.
	 */
	@Test
	public void places() throws Exception {
		entities();
		Path dir = Files.createTempDirectory("crate");
		Files.writeString(dir.resolve("ro-crate-metadata.json"), """
				{"@context": "https://w3id.org/ro/crate/1.3/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
				  {"@id": "./", "@type": "Dataset", "name": "A crate", "contentLocation": {"@id": "#catalina"}},
				  {"@id": "#catalina", "@type": "Place", "name": "Catalina Park", "geo": {"@id": "_:g1"}},
				  {"@id": "_:g1", "@type": "Geometry",
				   "asWKT": "<http://www.opengis.net/def/crs/OGC/1.3/CRS84> POINT (150.301195 -33.7152)"}
				 ]}""");
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir)).get());
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("#previewMap").tryQuery().isEmpty());
		Entity place = entities().getItems().stream().filter(e -> e.getId().equals("#catalina")).findFirst()
				.orElseThrow();
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(place)));
		Hyperlink map = lookup("#previewMap").queryAs(Hyperlink.class);
		assertEquals("Show on OpenStreetMap", map.getText());
		assertEquals("Latitude -33.715200, longitude 150.301195", map.getTooltip().getText());
		// asWKT is a field of the Geometry, as schema.org's properties are
		Entity geometry = entities().getItems().stream().filter(e -> e.getId().equals("_:g1")).findFirst()
				.orElseThrow();
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(geometry)));
		assertTrue(visibleProperties().contains("asWKT"));
		// A place's geo may be a new Geometry
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(place)));
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("geo"));
		interact(() -> {
			MenuButton geo = lookup("#new-geo").queryAs(MenuButton.class);
			geo.getItems().stream().filter(item -> item.getText().equals("Geometry")).findFirst().orElseThrow().fire();
		});
		assertTrue(entities().getItems().stream().anyMatch(e -> e.getId().equals("#geometry-1")
				&& e.getTypes().equals(List.of("Geometry"))));
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
		// name, description, parts and mentions under @id, then the required ones
		assertEquals(List.of("name *", "description *", "hasPart", "mentions", "datePublished *", "description *",
				"license *", "name *"), labels.subList(0, 8));
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
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(download)));
		clickOn("#previewRoot");
		assertSame(entities().getItems().get(0), entities().getSelectionModel().getSelectedItem());
	}

	@Test
	public void previewUpdatesWhileEditing() {
		entities();
		clickOn("#propertySearch").write("name");
		TextField name = lookup("#header-name").queryAs(TextField.class);
		clickOn(name).write("My crate");
		// Once typing pauses
		waitForPreview("My crate");
		clickOn("#propertySearch").eraseText(4).write("license");
		clickOn(lookup("#value-license-0").queryAs(ComboBox.class).getEditor())
				.write("http://spdx.org/licenses/CC0-1.0");
		try {
			waitForFx(5, () -> lookup("#previewProperties").tryQuery().isPresent()
					&& from(lookup("#previewProperties").queryAs(Node.class)).lookup(".hyperlink")
							.queryAllAs(Hyperlink.class).stream()
							.anyMatch(l -> l.getText().equals("http://spdx.org/licenses/CC0-1.0")));
		} catch (java.util.concurrent.TimeoutException e) {
			throw new AssertionError("Preview not updated", e);
		}
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
	public void listUpdatesInPlace() throws Exception {
		ListView<Entity> list = entities();
		boolean[] itemChanged = { false };
		ListCell<?> cell = WaitForAsyncUtils.asyncFx(() -> {
			list.getScene().getRoot().applyCss();
			list.getScene().getRoot().layout();
			VirtualFlow<?> flow = (VirtualFlow<?>) list.lookup(".virtual-flow");
			assertNotNull(flow);
			ListCell<?> rootCell = (ListCell<?>) flow.getVisibleCell(0);
			assertNotNull(rootCell);
			assertSame(list.getItems().get(0), rootCell.getItem());
			assertFalse(rootCell.isEmpty());
			// A list refresh can reuse the cell but briefly clear its item.
			rootCell.itemProperty().addListener((obs, old, current) -> itemChanged[0] = true);
			return rootCell;
		}).get();
		clickOn("#header-name").write("My crate");
		WaitForAsyncUtils.asyncFx(() -> {
			list.getScene().getRoot().layout();
			VirtualFlow<?> flow = (VirtualFlow<?>) list.lookup(".virtual-flow");
			assertNotNull(flow);
			ListCell<?> after = (ListCell<?>) flow.getVisibleCell(0);
			assertSame(cell, after);
			assertSame(list.getItems().get(0), after.getItem());
			assertFalse("Root cell's item changed while typing", itemChanged[0]);
			assertTrue(after.getText().startsWith("My crate\n"));
		}).get();
	}
}
