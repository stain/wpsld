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
import java.util.Optional;

import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
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
		editor = new EntityEditor(SCHEMA, new SchemaOrgFormFields(SCHEMA), crate::getEntities, () -> {
		}, crate::add, crate::rename);
		stage.setScene(new Scene(editor, 900, 700));
		stage.show();
		editor.edit(crate.getRoot());
	}

	private void edit(Entity entity) {
		interact(() -> editor.edit(entity));
	}

	/** Visible labels of the form's property rows */
	private List<String> visibleProperties() {
		return lookup(n -> n.getId() != null && n.getId().startsWith("label-")).queryAllAs(Label.class).stream()
				.filter(Node::isVisible).filter(l -> l.getParent() != null && l.getParent().isVisible())
				.map(Label::getText).toList();
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
		// hasPart is from CreativeWork, folded and not built before searching
		assertFalse(visibleProperties().contains("hasPart"));

		clickOn("#propertySearch").write("hasP");
		List<String> found = visibleProperties();
		assertTrue(found.contains("hasPart"));
		assertFalse(found.contains("distribution"));
		assertFalse(found.contains("isPartOf"));

		// Backspace shows more again
		eraseText(2);
		assertTrue(visibleProperties().contains("character"));

		// Cleared, as before the search
		eraseText(2);
		assertTrue(visibleProperties().contains("distribution"));
		assertFalse(visibleProperties().contains("hasPart"));
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
		assertTrue(lookup("#otherProperties").tryQuery().isEmpty());
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
}
