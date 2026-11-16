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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.crate.CrateChecks;
import no.s11.wpsld.crate.CrateReader;

/**
 * GUI tests of the crate window's help, and the Getting started
 * tutorial's steps as a user would take them.
 */
public class TestCrateEditorGettingStarted extends CrateEditorTest {

	@Test
	public void helpMenuAndProfileContext() {
		entities();
		interact(menuItem("menuManual")::fire);
		assertTrue(TestHelpDialog.text(lookup("#helpContent").queryAs(javafx.scene.text.TextFlow.class))
				.startsWith("Help contents"));
		clickOn("Close");
		if (!CrateFile.onMac()) {
			push(KeyCode.F1);
			assertTrue(lookup("#helpContent").tryQuery().isPresent());
			clickOn("Close");
		}
		clickOn("#profileHelp");
		assertTrue(TestHelpDialog.text(lookup("#helpContent").queryAs(javafx.scene.text.TextFlow.class))
				.startsWith("Profiles"));
		clickOn("Close");
	}

	@Test
	public void f1OpensHelpForTheFocusedControl() {
		entities();
		interact(() -> lookup("#entities").query().requestFocus());
		push(KeyCode.F1);
		assertEquals("Entities and identifiers", lookup("#helpTopics").queryListView()
				.getSelectionModel().getSelectedItem().toString());
		clickOn("Close");

		interact(() -> lookup("#entityId").query().requestFocus());
		push(KeyCode.F1);
		assertEquals("Entities and identifiers", lookup("#helpTopics").queryListView()
				.getSelectionModel().getSelectedItem().toString());
		clickOn("Close");

		interact(() -> lookup("#propertySearch").query().requestFocus());
		push(KeyCode.F1);
		assertEquals("Properties and links", lookup("#helpTopics").queryListView()
				.getSelectionModel().getSelectedItem().toString());
		clickOn("Close");

		interact(() -> lookup("#addType").query().requestFocus());
		push(KeyCode.F1);
		assertEquals("Types", lookup("#helpTopics").queryListView().getSelectionModel().getSelectedItem().toString());
		clickOn("Close");

		interact(() -> lookup("#profile").query().requestFocus());
		push(KeyCode.F1);
		assertEquals("Profiles", lookup("#helpTopics").queryListView().getSelectionModel().getSelectedItem().toString());
		clickOn("Close");

		interact(() -> lookup("#save").query().requestFocus());
		push(KeyCode.F1);
		assertEquals("Opening and saving crates", lookup("#helpTopics").queryListView()
				.getSelectionModel().getSelectedItem().toString());
		clickOn("Close");

		interact(() -> lookup("#views").query().requestFocus());
		push(KeyCode.F1);
		assertEquals("Views and styles", lookup("#helpTopics").queryListView()
				.getSelectionModel().getSelectedItem().toString());
		clickOn("Close");

		clickOn("#addType");
		push(KeyCode.F1);
		assertEquals("Types", lookup("#helpTopics").queryListView().getSelectionModel().getSelectedItem().toString());
		clickOn("Close");
		clickOn("Cancel");
	}

	@Test
	public void gettingStartedCreatesLinkedDescriptions() throws Exception {
		ListView<Entity> list = entities();
		Entity root = list.getItems().get(0);
		interact(() -> {
			lookup("#header-name").queryAs(TextField.class).setText("Example dataset");
			lookup("#header-description").queryAs(javafx.scene.control.TextArea.class)
					.setText("A sample file described with wpsld.");
			MenuButton parts = lookup("#headerNew-hasPart").queryAs(MenuButton.class);
			parts.getItems().stream().filter(item -> item.getText().equals("File")).findFirst().orElseThrow().fire();
		});
		Entity file = list.getItems().stream().filter(e -> e.getTypes().contains("File")).findFirst().orElseThrow();
		interact(() -> list.getSelectionModel().clearAndSelect(list.getItems().indexOf(file)));
		renameTo("data.csv");
		interact(() -> lookup("#header-name").queryAs(TextField.class).setText("Sample data"));
		interact(() -> list.getSelectionModel().clearAndSelect(0));
		assertEquals("data.csv", root.getAll("hasPart").get(0).path("@id").asText());

		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("author"));
		interact(() -> {
			MenuButton author = lookup("#new-author").queryAs(MenuButton.class);
			author.getItems().stream().filter(item -> item.getText().equals("Person")).findFirst().orElseThrow().fire();
		});
		Entity person = list.getItems().stream().filter(e -> e.getTypes().contains("Person")).findFirst().orElseThrow();
		interact(() -> list.getSelectionModel().clearAndSelect(list.getItems().indexOf(person)));
		interact(() -> lookup("#header-name").queryAs(TextField.class).setText("Example author"));

		assertEquals(person.getId(), root.getAll("author").get(0).path("@id").asText());
		interact(() -> list.getSelectionModel().clearAndSelect(0));
		clickOn("#save");
		String warning = lookup(".dialog-pane").queryAs(DialogPane.class).getContentText();
		assertTrue(warning.contains("datePublished"));
		assertTrue(warning.contains("license"));
		clickOn("Cancel");

		interact(() -> {
			lookup("#propertySearch").queryAs(TextField.class).clear();
			lookup("#value-datePublished-0").queryAs(DatePicker.class).getEditor().setText("2025-01-31");
			MenuButton license = lookup("#new-license").queryAs(MenuButton.class);
			license.getItems().stream().filter(item -> item.getText().equals("CreativeWork"))
					.findFirst().orElseThrow().fire();
		});
		Entity license = list.getItems().stream().filter(e -> e.getTypes().contains("CreativeWork"))
				.findFirst().orElseThrow();
		interact(() -> list.getSelectionModel().clearAndSelect(list.getItems().indexOf(license)));
		interact(() -> lookup("#header-name").queryAs(TextField.class)
				.setText("Creative Commons Attribution 4.0 International"));
		renameTo("http://spdx.org/licenses/CC-BY-4.0");
		assertEquals("2025-01-31", root.get("datePublished").orElseThrow().asText());
		assertEquals("http://spdx.org/licenses/CC-BY-4.0", root.getAll("license").get(0).path("@id").asText());

		Path folder = Files.createTempDirectory("wpsld-help-");
		Path data = Files.writeString(folder.resolve("data.csv"), "value\n1\n");
		Path metadata = folder.resolve("ro-crate-metadata.json");
		try {
			Stage stage = (Stage) list.getScene().getWindow();
			assertTrue(WaitForAsyncUtils.asyncFx(() -> app.saveTo(stage, metadata)).get());
			JsonNode graph = new ObjectMapper().readTree(metadata.toFile()).path("@graph");
			assertTrue(graph.toString().contains("\"data.csv\""));
			var saved = new CrateReader().read(metadata).crate().orElseThrow();
			assertTrue(CrateChecks.check(saved).isEmpty());
			assertEquals("http://spdx.org/licenses/CC-BY-4.0",
					saved.getRoot().getAll("license").get(0).path("@id").asText());
			Entity savedLicense = saved.get("http://spdx.org/licenses/CC-BY-4.0").orElseThrow();
			assertEquals(List.of("CreativeWork"), savedLicense.getTypes());
			assertEquals("Creative Commons Attribution 4.0 International",
					savedLicense.get("name").orElseThrow().asText());
			assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, metadata)).get());
			assertTrue(list.getItems().stream().anyMatch(e -> e.getId().equals("data.csv")));
		} finally {
			Files.deleteIfExists(metadata);
			Files.deleteIfExists(data);
			// Written with it, as an attached crate's
			Files.deleteIfExists(folder.resolve("ro-crate-preview.html"));
			Files.deleteIfExists(folder);
		}
	}

	@Test
	public void gettingStartedAddsPhotographAndWikidataPerson() {
		ListView<Entity> list = entities();
		interact(() -> {
			MenuButton parts = lookup("#headerNew-hasPart").queryAs(MenuButton.class);
			parts.getItems().stream().filter(item -> item.getText().equals("File")).findFirst().orElseThrow().fire();
		});
		Entity file = list.getItems().stream().filter(e -> e.getTypes().contains("File")).findFirst().orElseThrow();
		interact(() -> list.getSelectionModel().clearAndSelect(list.getItems().indexOf(file)));
		renameTo("photo.jpg");
		clickOn("#addType");
		clickOn("#typeSearch").write("Photograph").type(KeyCode.ENTER);
		assertEquals(List.of("File", "Photograph"), file.getTypes());
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("about"));
		interact(() -> {
			MenuButton about = lookup("#new-about").queryAs(MenuButton.class);
			about.getItems().stream().filter(item -> item.getText().equals("Thing")).findFirst().orElseThrow().fire();
		});
		Entity person = list.getItems().stream().filter(e -> e.getTypes().contains("Thing")).findFirst().orElseThrow();
		interact(() -> list.getSelectionModel().clearAndSelect(list.getItems().indexOf(person)));
		clickOn("#addType");
		clickOn("#typeSearch").write("Person").type(KeyCode.ENTER);
		assertEquals(List.of("Person"), person.getTypes());
		interact(() -> lookup("#header-name").queryAs(TextField.class).setText("Margaret Hamilton"));
		renameTo("https://www.wikidata.org/entity/Q11628");
		assertEquals("https://www.wikidata.org/entity/Q11628", file.getAll("about").get(0).path("@id").asText());
		assertEquals("Margaret Hamilton", person.get("name").orElseThrow().asText());
		assertEquals("photo.jpg", list.getItems().get(0).getAll("hasPart").get(0).path("@id").asText());
	}
}
