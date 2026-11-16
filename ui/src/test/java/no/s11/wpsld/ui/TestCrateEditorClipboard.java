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
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.testfx.util.WaitForAsyncUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.scene.Node;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Clipboard;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.DialogPane;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import no.s11.wpsld.crate.Entity;

/**
 * GUI tests of copying and pasting entities in the crate window.
 */
public class TestCrateEditorClipboard extends CrateEditorTest {

	/**
	 * Edit > Copy in the entity list copies the selected entity as JSON-LD;
	 * Paste adds it, with a new @id as the crate has the copied one, and
	 * mentions it.
	 */
	@Test
	public void copyAndPasteEntity() throws Exception {
		entities();
		addEntity("person");
		Entity root = entities().getItems().get(0);
		Entity alice = entities().getItems().get(1);
		interact(() -> alice.set("name", Optional.of(JsonNodeFactory.instance.textNode("Alice"))));
		clickOn("#entities");
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(alice)));
		MenuItem copy = menuItem("menuCopy");
		assertFalse(copy.isDisable());
		interact(copy::fire);
		String copied = WaitForAsyncUtils.asyncFx(() -> Clipboard.getSystemClipboard().getString()).get();
		assertEquals(alice.toJson(), new ObjectMapper().readTree(copied));
		// And as a JSON-LD document with the crate's context and its base
		JsonNode document = new ObjectMapper().readTree((String) WaitForAsyncUtils
				.asyncFx(() -> Clipboard.getSystemClipboard().getContent(EntityClipboard.LD_JSON)).get());
		assertEquals("https://w3id.org/ro/crate/1.3/context", document.get("@context").get(0).asText());
		String base = document.get("@context").get(1).get("@base").asText();
		assertTrue(base, base.matches("arcp://uuid,[0-9a-f-]{36}/"));
		assertEquals(alice.toJson(), document.get("@graph").get(0));
		assertEquals(1, document.get("@graph").size());
		// Also as wpsld's own, which its text fields don't take as text
		JsonNode wpsld = new ObjectMapper().readTree((String) WaitForAsyncUtils
				.asyncFx(() -> Clipboard.getSystemClipboard().getContent(EntityClipboard.ENTITY)).get());
		assertEquals(1, wpsld.get("version").asInt());
		assertEquals(base, wpsld.get("crate").asText());
		assertEquals(document, wpsld.get("document"));

		interact(menuItem("menuPaste")::fire);
		assertEquals(3, entities().getItems().size());
		// Selected, wherever its name sorts it
		Entity pasted = entities().getSelectionModel().getSelectedItem();
		// Added as #thing-1, so a new @id for the copy, from its type
		assertEquals("#thing-1", alice.getId());
		assertEquals("#person-1", pasted.getId());
		assertEquals("Alice", pasted.getLabel());
		assertTrue(root.getAll("mentions").stream().anyMatch(v -> v.get("@id").asText().equals("#person-1")));

		// In the form, a reference field pastes its @id, a text field nothing
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(root)));
		clickOn("#propertySearch").write("author");
		Node author = lookup("#value-author-0").query();
		clickOn(author);
		push(KeyCode.SHORTCUT, KeyCode.V);
		assertEquals("#thing-1", ((ComboBox<?>) author).getEditor().getText());
		assertEquals(List.of(JsonNodeFactory.instance.objectNode().put("@id", "#thing-1")), root.getAll("author"));
		TextField name = lookup("#header-name").queryAs(TextField.class);
		clickOn(name).write("Crate");
		push(KeyCode.SHORTCUT, KeyCode.V);
		assertEquals("Crate", name.getText());

		// JSON-LD copied as text in a text editor pastes as an entity too
		interact(() -> {
			ClipboardContent text = new ClipboardContent();
			text.putString("{\"@id\": \"https://orcid.org/0000-0001-9842-9718\", \"@type\": \"Person\"}");
			Clipboard.getSystemClipboard().setContent(text);
		});
		clickOn("#entities");
		interact(menuItem("menuPaste")::fire);
		assertEquals("https://orcid.org/0000-0001-9842-9718", entities().getSelectionModel().getSelectedItem().getId());

		// A JSON-LD document of one entity, from another tool, or as text
		String bob = "{\"@context\": \"https://w3id.org/ro/crate/1.2/context\", "
				+ "\"@graph\": [{\"@id\": \"#bob\", \"@type\": \"Person\", \"name\": \"Bob\"}]}";
		interact(() -> {
			ClipboardContent ldJson = new ClipboardContent();
			ldJson.put(EntityClipboard.LD_JSON, bob);
			Clipboard.getSystemClipboard().setContent(ldJson);
		});
		interact(menuItem("menuPaste")::fire);
		assertEquals("Bob", entities().getSelectionModel().getSelectedItem().getLabel());
		interact(() -> {
			ClipboardContent text = new ClipboardContent();
			text.putString(bob.replace("#bob", "#bob-2"));
			Clipboard.getSystemClipboard().setContent(text);
		});
		interact(menuItem("menuPaste")::fire);
		assertEquals("#bob-2", entities().getSelectionModel().getSelectedItem().getId());

		// Not an entity: says so, and adds nothing
		interact(() -> {
			ClipboardContent text = new ClipboardContent();
			text.putString("Alice");
			Clipboard.getSystemClipboard().setContent(text);
		});
		WaitForAsyncUtils.asyncFx(menuItem("menuPaste")::fire);
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("No entity to paste").tryQuery().isPresent());
		clickOn("OK");
		assertEquals(6, entities().getItems().size());
	}

	/**
	 * Pasting many entities shows the busy pointer first, as it takes a
	 * while on the JavaFX thread, then pastes them
	 */
	@Test
	public void pasteManyShowsBusy() throws Exception {
		StringBuilder graph = new StringBuilder();
		for (int i = 0; i <= CrateEditor.QUICK; i++) {
			graph.append(i == 0 ? "" : ", ").append("{\"@id\": \"#pv-").append(i)
					.append("\", \"@type\": \"PropertyValue\", \"name\": \"v").append(i).append("\"}");
		}
		String document = "{\"@context\": \"https://w3id.org/ro/crate/1.3/context\", \"@graph\": [" + graph + "]}";
		interact(() -> {
			ClipboardContent ldJson = new ClipboardContent();
			ldJson.put(EntityClipboard.LD_JSON, document);
			Clipboard.getSystemClipboard().setContent(ldJson);
		});
		int before = entities().getItems().size();
		clickOn("#entities");
		javafx.scene.Scene scene = entities().getScene();
		javafx.scene.Cursor[] busy = new javafx.scene.Cursor[1];
		interact(() -> {
			menuItem("menuPaste").fire();
			busy[0] = scene.getCursor();
		});
		assertEquals(javafx.scene.Cursor.WAIT, busy[0]);
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> entities().getItems().size() > before);
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(before + CrateEditor.QUICK + 1, entities().getItems().size());
		assertNotEquals(javafx.scene.Cursor.WAIT, scene.getCursor());
	}

	/**
	 * Several entities copied, with the one they refer to: pasted into the
	 * same crate, references to the one not copied are right; from another
	 * crate, they may not be, which is reported.
	 */
	@Test
	public void copyAndPasteSeveralEntities() throws Exception {
		entities();
		addEntity("person");
		addEntity("person");
		addEntity("organization");
		// Listed by type: the organisation first
		Entity org = entities().getItems().get(1);
		Entity alice = entities().getItems().get(2);
		Entity bob = entities().getItems().get(3);
		assertEquals(List.of("Organization"), org.getTypes());
		interact(() -> {
			alice.set("name", Optional.of(JsonNodeFactory.instance.textNode("Alice")));
			bob.set("name", Optional.of(JsonNodeFactory.instance.textNode("Bob")));
			alice.set("affiliation", Optional.of(JsonNodeFactory.instance.objectNode().put("@id", org.getId())));
			bob.set("affiliation", Optional.of(JsonNodeFactory.instance.objectNode().put("@id", org.getId())));
			bob.set("knows", Optional.of(JsonNodeFactory.instance.objectNode().put("@id", alice.getId())));
		});
		clickOn("#entities");
		// Selected the other way round: copied in the crate's order
		interact(() -> {
			entities().getSelectionModel().clearSelection();
			entities().getSelectionModel().selectIndices(3, 2);
		});
		interact(menuItem("menuCopy")::fire);
		JsonNode wpsld = new ObjectMapper().readTree((String) WaitForAsyncUtils
				.asyncFx(() -> Clipboard.getSystemClipboard().getContent(EntityClipboard.ENTITY)).get());
		assertEquals(List.of(alice.getId(), bob.getId()),
				wpsld.get("document").get("@graph").valueStream().map(e -> e.get("@id").asText()).toList());
		assertEquals(org.toJson(), wpsld.get("referenced").get(0));
		assertEquals(1, wpsld.get("referenced").size());
		// As text, objects as in a @graph
		String text = WaitForAsyncUtils.asyncFx(() -> Clipboard.getSystemClipboard().getString()).get();
		assertEquals(2, new ObjectMapper().readTree("[" + text + "]").size());

		interact(menuItem("menuPaste")::fire);
		assertEquals(6, entities().getItems().size());
		// Selected, in the order copied, wherever their names sort them
		Entity alice2 = entities().getSelectionModel().getSelectedItems().get(0);
		Entity bob2 = entities().getSelectionModel().getSelectedItems().get(1);
		assertNotSame(alice, alice2);
		assertEquals("Alice", alice2.getLabel());
		// Bob's copy knows Alice's copy; both with the same affiliation
		assertEquals(alice2.getId(), bob2.get("knows").orElseThrow().get("@id").asText());
		assertEquals(org.getId(), bob2.get("affiliation").orElseThrow().get("@id").asText());
		// Same crate: nothing to report
		assertFalse(lookup(".alert").tryQuery().isPresent());

		// As if from another crate, whose organization differs from this one
		interact(() -> {
			ClipboardContent content = new ClipboardContent();
			((ObjectNode) wpsld).put("crate", "arcp://uuid,00000000-0000-4000-8000-000000000000/");
			((ObjectNode) wpsld.get("referenced").get(0)).put("name", "Another organization");
			content.put(EntityClipboard.ENTITY, wpsld.toString());
			Clipboard.getSystemClipboard().setContent(content);
		});
		interact(menuItem("menuPaste")::fire);
		assertEquals(8, entities().getItems().size());
		assertTrue(lookup(org.getId()).tryQuery().isPresent()
				|| lookup(n -> n instanceof javafx.scene.control.Label l && l.getText() != null
						&& l.getText().contains(org.getId())).tryQuery().isPresent());
		clickOn("OK");
	}

	/**
	 * Pasting entities the crate has already says so, in one dialog for the
	 * paste: those the same left out, and those merged or kept.
	 */
	@Test
	public void pasteReportsWhatIsAlreadyThere() throws Exception {
		entities();
		addEntity("person");
		renameTo("https://orcid.org/0000-0002-1825-0097");
		addEntity("person");
		Entity orcid = entities().getItems().stream()
				.filter(e -> e.getId().equals("https://orcid.org/0000-0002-1825-0097")).findFirst().orElseThrow();
		Entity local = entities().getItems().stream().filter(e -> e.getId().startsWith("#")).findFirst()
				.orElseThrow();
		clickOn("#entities");
		interact(() -> {
			entities().getSelectionModel().clearSelection();
			entities().getSelectionModel().selectIndices(entities().getItems().indexOf(orcid),
					entities().getItems().indexOf(local));
		});
		interact(menuItem("menuCopy")::fire);
		interact(menuItem("menuPaste")::fire);
		// The local one copied, the ORCID one the same
		assertEquals(4, entities().getItems().size());
		DialogPane report = lookup(".dialog-pane").queryAs(DialogPane.class);
		assertEquals("Pasted 1 of 2 entities: the others are already in this crate", report.getHeaderText());
		assertTrue(report.getContentText(), report.getContentText()
				.startsWith("Already in this crate, the same, so not added again: https://orcid.org/"));
		clickOn("OK");

		// Only the ORCID one, changed: merged
		JsonNode copied = new ObjectMapper().readTree((String) WaitForAsyncUtils
				.asyncFx(() -> Clipboard.getSystemClipboard().getContent(EntityClipboard.ENTITY)).get());
		ArrayNode graph = (ArrayNode) copied.get("document").get("@graph");
		graph.remove(1);
		((ObjectNode) graph.get(0)).put("name", "Josiah Carberry");
		interact(() -> {
			ClipboardContent content = new ClipboardContent();
			content.put(EntityClipboard.ENTITY, copied.toString());
			Clipboard.getSystemClipboard().setContent(content);
		});
		clickOn("#entities");
		// Not waiting for Paste, which waits for the Merge dialog
		WaitForAsyncUtils.asyncFx(menuItem("menuPaste")::fire);
		WaitForAsyncUtils.waitForFxEvents();
		clickOn("Merge");
		WaitForAsyncUtils.waitForFxEvents();
		report = lookup(".dialog-pane").queryAs(DialogPane.class);
		assertEquals("Nothing new pasted: it is already in this crate", report.getHeaderText());
		assertEquals("Merged into those in this crate with the same @id: Josiah Carberry"
				+ " (https://orcid.org/0000-0002-1825-0097).", report.getContentText());
		clickOn("OK");
		assertEquals("Josiah Carberry", orcid.getLabel());
		assertEquals(4, entities().getItems().size());
	}
}
