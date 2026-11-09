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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.SchemaOrgFormFields;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * GUI tests of the entity form, run headless (see ui/build.gradle).
 */
public class TestEntityEditor extends ApplicationTest {
	private static final SchemaFromJsonLd SCHEMA = new SchemaFromJsonLd();
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private final Crate crate = new Crate();
	private EntityEditor editor;

	@Override
	public void start(Stage stage) {
		editor = new EntityEditor(SCHEMA, new SchemaOrgFormFields(SCHEMA), crate::getEntities, crate::getRoot, () -> {
		}, crate::add, crate::rename);
		stage.setScene(new Scene(editor, 900, 700));
		stage.show();
		editor.edit(crate.getRoot());
	}

	private void edit(Entity entity) {
		interact(() -> editor.edit(entity));
	}

	/** Properties of the form's visible rows */
	private List<String> visibleProperties() {
		return lookup(n -> n.getId() != null && n.getId().startsWith("label-")).queryAllAs(Label.class).stream()
				.filter(Node::isVisible).filter(l -> l.getParent() != null && l.getParent().isVisible())
				// The property, not its human readable label; * if required
				.map(l -> l.getId().substring("label-".length()) + (l.getText().endsWith(" *") ? " *" : ""))
				.toList();
	}

	private TextField value(String property, int row) {
		Node control = lookup("#value-" + property + "-" + row).query();
		return control instanceof ComboBox<?> combo ? combo.getEditor() : (TextField) control;
	}

	private void chooseType(String search) {
		clickOn("#addType");
		clickOn("#typeSearch").write(search).type(KeyCode.ENTER);
	}

	private static JsonNode ref(String id) {
		return JSON.objectNode().put("@id", id);
	}

	@Test
	public void propertySearchFilters() {
		assertTrue(visibleProperties().contains("distribution"));
		// isBasedOn is from CreativeWork, folded and not built before searching
		assertFalse(visibleProperties().contains("isBasedOn"));

		clickOn("#propertySearch").write("isBa");
		List<String> found = visibleProperties();
		assertTrue(found.contains("isBasedOn"));
		assertFalse(found.contains("distribution"));
		assertFalse(found.contains("isPartOf"));

		// Backspace shows more again
		eraseText(2);
		assertTrue(visibleProperties().contains("isPartOf"));

		// Cleared, as before the search
		eraseText(2);
		assertTrue(visibleProperties().contains("distribution"));
		assertFalse(visibleProperties().contains("isBasedOn"));
	}

	@Test
	public void valuesSurviveTypeChanges() {
		clickOn(value("issn", 0)).write("1234-5678");
		// Removing the last type falls back to Thing, which has no issn
		clickOn("#removeType-Dataset");
		assertEquals(List.of("Thing"), crate.getRoot().getTypes());
		assertNotNull(lookup("#otherProperties").query());
		assertEquals("1234-5678", crate.getRoot().get("issn").orElseThrow().asText());
		// A lone Thing can't be removed, it would come back
		assertTrue(lookup("#removeType-Thing").queryAs(Button.class).isDisabled());

		// Suggested types are those having issn, e.g. Dataset
		clickOn("#suggestType-Dataset");
		assertEquals(List.of("Dataset"), crate.getRoot().getTypes());
		assertFalse(lookup("#otherProperties").query().isVisible());
		assertEquals("1234-5678", value("issn", 0).getText());
	}

	@Test
	public void subclassReplacesType() {
		clickOn("#removeType-Dataset");
		chooseType("person");
		assertEquals(List.of("Person"), crate.getRoot().getTypes());
	}

	@Test
	public void unrelatedTypesKept() {
		chooseType("softwaresourcecode");
		assertEquals(List.of("Dataset", "SoftwareSourceCode"), crate.getRoot().getTypes());
		assertNotNull(lookup("#type-SoftwareSourceCode").query());
	}

	@Test
	public void superclassReplace() {
		chooseType("creativework");
		clickOn("Replace");
		assertEquals(List.of("CreativeWork"), crate.getRoot().getTypes());
	}

	@Test
	public void superclassAddAnyway() {
		chooseType("creativework");
		clickOn("Add anyway");
		assertEquals(List.of("Dataset", "CreativeWork"), crate.getRoot().getTypes());
	}

	@Test
	public void superclassCancel() {
		chooseType("creativework");
		clickOn("Cancel");
		assertEquals(List.of("Dataset"), crate.getRoot().getTypes());
	}

	@Test
	public void severalValues() {
		clickOn(value("issn", 0)).write("1111-1111");
		clickOn("#add-issn");
		clickOn(value("issn", 1)).write("2222-2222");
		assertEquals(List.of(JSON.textNode("1111-1111"), JSON.textNode("2222-2222")),
				crate.getRoot().getAll("issn"));
		assertTrue(crate.getRoot().get("issn").orElseThrow().isArray());
	}

	@Test
	public void newLinkedEntity() {
		clickOn("#new-distribution");
		clickOn("DataDownload");
		Entity download = crate.get("#datadownload-1").orElseThrow();
		assertEquals(List.of("DataDownload"), download.getTypes());
		assertEquals(List.of(ref(download.getId())), crate.getRoot().getAll("distribution"));
		assertEquals("#datadownload-1", value("distribution", 0).getText());
	}

	/**
	 * The root's parts and mentions are under @id, its parts files, folders
	 * or other creative works.
	 */
	@Test
	public void rootLinksInHeader() {
		assertTrue(lookup("#headerField-hasPart").tryQuery().isPresent());
		assertTrue(lookup("#headerField-mentions").tryQuery().isPresent());
		clickOn("#headerNew-hasPart");
		MenuButton create = lookup("#headerNew-hasPart").queryAs(MenuButton.class);
		assertEquals(List.of("File", "Dataset", "CreativeWork"),
				create.getItems().stream().map(MenuItem::getText).toList());
		// Not clickOn("Dataset"), which finds the root's type
		interact(() -> create.getItems().get(1).fire());
		Entity folder = crate.get("#dataset-1").orElseThrow();
		assertEquals(List.of(ref(folder.getId())), crate.getRoot().getAll("hasPart"));
		assertEquals("#dataset-1", lookup("#headerValue-hasPart-0").queryAs(ComboBox.class).getEditor().getText());

		// The group's field shows the same values once opened
		clickOn("#propertySearch").write("hasPart");
		assertEquals("#dataset-1", value("hasPart", 0).getText());

		// Other Datasets have their parts in their group only
		edit(folder);
		assertTrue(lookup("#headerField-hasPart").tryQuery().isEmpty());
	}

	@Test
	public void referenceSuggestionsFitRange() {
		crate.add("DataDownload");
		crate.add("Person");
		edit(crate.getRoot());
		ComboBox<?> distribution = lookup("#value-distribution-0").queryAs(ComboBox.class);
		assertEquals(List.of("#datadownload-1"), List.copyOf(distribution.getItems()));
		// Typing filters by @id and name
		clickOn(distribution.getEditor()).write("zzz");
		assertTrue(distribution.getItems().isEmpty());
	}

	@Test
	public void renameUpdatesReferences() {
		Entity download = crate.add("DataDownload");
		crate.getRoot().setAll("distribution", List.of(ref(download.getId())));
		edit(download);
		TextField id = lookup("#entityId").queryAs(TextField.class);
		doubleClickOn(id);
		interact(id::selectAll);
		write("#dl").type(KeyCode.ENTER);
		assertEquals("#dl", download.getId());
		assertEquals(List.of(ref("#dl")), crate.getRoot().getAll("distribution"));
		assertEquals(Optional.of(download), crate.get("#dl"));
	}

	/**
	 * schema:MediaObject is shown and written as RO-Crate's File, and found
	 * by either name.
	 */
	@Test
	public void fileForMediaObject() {
		Entity entity = crate.add("Thing");
		edit(entity);
		chooseType("file");
		assertEquals(List.of("File"), entity.getTypes());
		assertNotNull(lookup("#type-File").query());
		assertEquals("File", entity.toJson().get("@type").asText());
		// Its own properties, grouped as File
		assertTrue(visibleProperties().contains("encodingFormat"));
	}

	@Test
	public void mediaObjectSearchGivesFile() {
		Entity entity = crate.add("Thing");
		edit(entity);
		chooseType("mediaobject");
		assertEquals(List.of("File"), entity.getTypes());
	}

	private DatePicker datePicker(String property) {
		return lookup("#value-" + property + "-0").queryAs(DatePicker.class);
	}

	/**
	 * Typed ISO 8601 dates are kept, also after leaving the field.
	 */
	@Test
	public void isoDateTyped() {
		clickOn("#propertySearch").write("datePublished");
		DatePicker picker = datePicker("datePublished");
		clickOn(picker.getEditor()).write("2026-11-05");
		// Leave the field, which commits the date picker
		clickOn("#propertySearch");
		assertEquals("2026-11-05", crate.getRoot().get("datePublished").orElseThrow().asText());
		assertEquals("2026-11-05", picker.getEditor().getText());
		assertEquals(LocalDate.of(2026, 11, 5), picker.getValue());
	}

	/**
	 * Dates chosen in the calendar are ISO 8601, not the locale's format.
	 */
	@Test
	public void isoDateChosen() {
		clickOn("#propertySearch").write("datePublished");
		DatePicker picker = datePicker("datePublished");
		interact(() -> picker.setValue(LocalDate.of(2026, 11, 12)));
		assertEquals("2026-11-12", picker.getEditor().getText());
		assertEquals("2026-11-12", crate.getRoot().get("datePublished").orElseThrow().asText());
	}

	@Test
	public void invalidDateNotStored() {
		clickOn("#propertySearch").write("datePublished");
		DatePicker picker = datePicker("datePublished");
		clickOn(picker.getEditor()).write("11/12/2026");
		assertFalse(crate.getRoot().get("datePublished").isPresent());
		assertTrue(picker.getEditor().getPseudoClassStates().stream()
				.anyMatch(p -> p.getPseudoClassName().equals("invalid")));
		assertNotNull(picker.getTooltip());
	}

	@Test
	public void isoDates() {
		assertEquals(java.util.Optional.of("2026-11-05"), EntityEditor.isoDate(" 2026-11-05 "));
		assertTrue(EntityEditor.isoDate("11/12/2026").isEmpty());
		assertTrue(EntityEditor.isoDate("2026-13-01").isEmpty());
		assertTrue(EntityEditor.isoDate("").isEmpty());
	}

	/**
	 * Labels are human readable names, with the property as tooltip, and a
	 * [?] button shows its description.
	 */
	@Test
	public void labelsAndDescriptions() {
		clickOn("#propertySearch").write("datePublished");
		Label label = lookup("#label-datePublished").queryAs(Label.class);
		assertEquals("Date published", label.getText());
		assertEquals("datePublished", label.getTooltip().getText());
		Node description = lookup("#description-datePublished").query();
		assertFalse(description.isVisible());
		clickOn("#help-datePublished");
		assertTrue(description.isVisible());
		// As plain text, without schema.org's [[CreativeWork]] markup
		String text = from(description).lookup(".label").queryAs(Label.class).getText();
		assertTrue(text, text.startsWith("Date of first publication"));
		assertFalse(text, text.contains("[["));
		Hyperlink definition = lookup("#definition-datePublished").queryAs(Hyperlink.class);
		assertEquals("schema.org/datePublished", definition.getText());
	}

	/**
	 * The search finds properties by their term and by their readable name,
	 * ignoring case.
	 */
	@Test
	public void searchTermOrName() {
		for (String query : List.of("datepublished", "Date published")) {
			clickOn("#propertySearch").eraseText(((TextField) lookup("#propertySearch").query()).getText().length())
				.write(query);
			// "Structured data date published" too; the fields under @id stay on top
			assertEquals(query, List.of("datePublished", "sdDatePublished"),
					visibleProperties().stream().map(p -> p.replace(" *", ""))
						.filter(p -> !EntityEditor.HEADER.contains(p) && !Crate.ROOT_LINKS.contains(p)).sorted().toList());
		}
	}

	/**
	 * name and description under @id edit their first value, as text only,
	 * and stay in step with their fields under Thing.
	 */
	@Test
	public void nameUnderIdAndThing() {
		clickOn("#header-name").write("Rain");
		assertEquals(List.of("Rain"), crate.getRoot().getAll("name").stream().map(JsonNode::asText).toList());
		// Only text: no buttons to add values or entities
		assertTrue(lookup("#header-name").queryAs(Node.class).getParent() instanceof GridPane);
		clickOn("#propertySearch").write("name");
		TextField inThing = lookup("#value-name-0").queryAs(TextField.class);
		assertEquals("Rain", inThing.getText());
		clickOn(inThing).write("fall");
		assertEquals("Rainfall", lookup("#header-name").queryAs(TextField.class).getText());
		// A second name, only under Thing; the first stays under @id
		clickOn("#add-name");
		clickOn("#value-name-1").write("Precipitation");
		assertEquals(List.of("Rainfall", "Precipitation"),
				crate.getRoot().getAll("name").stream().map(JsonNode::asText).toList());
		clickOn("#header-name").type(KeyCode.END).write("s");
		assertEquals(List.of("Rainfalls", "Precipitation"),
				crate.getRoot().getAll("name").stream().map(JsonNode::asText).toList());
		assertEquals("Rainfalls", lookup("#value-name-0").queryAs(TextField.class).getText());
	}

	/** Tab leaves the description at the top, rather than typing a tab */
	@Test
	public void tabLeavesDescription() {
		clickOn("#header-description").write("Rain").type(KeyCode.TAB);
		assertEquals(List.of("Rain"), crate.getRoot().getAll("description").stream().map(JsonNode::asText).toList());
		assertFalse(lookup("#header-description").queryAs(Node.class).isFocused());
	}

	/**
	 * Retired properties are hidden, unless in use or searched for by their
	 * full term or name; then they are editable, with a note.
	 */
	@Test
	public void retiredProperties() {
		Node others = lookup("#otherProperties").query();
		assertFalse(others.isVisible());
		clickOn("#propertySearch").write("fileForm");
		assertFalse(visibleProperties().contains("fileFormat"));
		write("at");
		assertTrue(others.isVisible());
		assertEquals(List.of("fileFormat"), visibleProperties().stream()
				.filter(p -> !EntityEditor.HEADER.contains(p) && !Crate.ROOT_LINKS.contains(p)).toList());
		assertEquals("\u2691 Retired by schema.org: use Encoding format instead",
				lookup("#retired-fileFormat").queryAs(Label.class).getText());
		clickOn(value("fileFormat", 0)).write("text/csv");
		assertEquals("text/csv", crate.getRoot().get("fileFormat").orElseThrow().asText());

		// In use, it stays after the search, and after showing the form again
		clickOn("#propertySearch").eraseText("fileFormat".length());
		assertTrue(others.isVisible());
		assertTrue(visibleProperties().contains("fileFormat"));
		chooseType("SoftwareSourceCode");
		assertTrue(lookup("#otherProperties").query().isVisible());
		assertEquals("text/csv", value("fileFormat", 0).getText());
	}
}
