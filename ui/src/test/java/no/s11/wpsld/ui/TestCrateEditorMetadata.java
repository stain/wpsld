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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.testfx.util.WaitForAsyncUtils;

import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Metadata from the File… and Crate > Update File Details with what files say about themselves.
 * (Apart from TestCrateEditor, whose JVM would otherwise run out of memory:
 * every test's window stays in memory, see ui/build.gradle.)
 */
public class TestCrateEditorMetadata extends UiTest {
	private CrateEditor app;

	@Override
	public void start(Stage stage) throws Exception {
		Path temp = Files.createTempDirectory("wpsld-metadata");
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

	/** The entity of an @id, as listed */
	private Optional<Entity> listed(String id) {
		return entities().getItems().stream().filter(e -> e.getId().equals(id)).findFirst();
	}

	/** A dialog's button, not one of the window's with the same text */
	private void clickDialogButton(String text) {
		clickOn(lookup(".dialog-pane .button").match((Button b) -> b.getText().equals(text)).queryButton());
		WaitForAsyncUtils.waitForFxEvents();
	}

	private Hyperlink previewLink(String containerId, String text) {
		return from(lookup(containerId).queryAs(Node.class)).lookup(".hyperlink").queryAllAs(Hyperlink.class).stream()
				.filter(l -> l.getText().equals(text)).findFirst().orElseThrow(() -> new AssertionError(text));
	}

	/** Metadata from the File… of the file selected, once read in the background */
	private void fromFile(Stage stage) throws Exception {
		Task<?> task = WaitForAsyncUtils.asyncFx(() -> app.metadataFromFile(stage)).get().orElseThrow();
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, task::isDone);
		WaitForAsyncUtils.waitForFxEvents();
	}

	/**
	 * A Word file's own metadata, chosen in a dialog: its title replaces the
	 * name, its creator is a Person linked by name, its licence the SPDX
	 * licence; asked again, only what is new.
	 */
	@Test
	public void metadataFromFile() throws Exception {
		entities();
		Path dir = Files.createTempDirectory("crate");
		try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(
				Files.newOutputStream(dir.resolve("workshop.docx")))) {
			zip.putNextEntry(new java.util.zip.ZipEntry("docProps/core.xml"));
			zip.write("""
					<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
					 xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/">
					<dc:title>Week 8 workshop</dc:title><dc:creator>Josiah Carberry</dc:creator>
					<dcterms:created>2026-04-20T15:36:54Z</dcterms:created></cp:coreProperties>"""
					.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			zip.putNextEntry(new java.util.zip.ZipEntry("docProps/custom.xml"));
			zip.write("""
					<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/custom-properties"
					 xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
					<property name="License"><vt:lpwstr>https://creativecommons.org/licenses/by/4.0/</vt:lpwstr></property>
					</Properties>""".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		Files.writeString(dir.resolve("ro-crate-metadata.json"), """
				{"@context": "https://w3id.org/ro/crate/1.3/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
				  {"@id": "./", "@type": "Dataset", "name": "A crate", "hasPart": {"@id": "workshop.docx"}},
				  {"@id": "workshop.docx", "@type": "File", "name": "Draft"}]}""");
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir)).get());
		WaitForAsyncUtils.waitForFxEvents();
		Entity file = entities().getItems().stream().filter(e -> e.getId().equals("workshop.docx")).findFirst()
				.orElseThrow();
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(file)));
		fromFile(stage);
		// The name it has: not chosen to start with
		List<javafx.scene.control.CheckBox> boxes = lookup("#fileMetadata").lookup(".check-box")
				.queryAllAs(javafx.scene.control.CheckBox.class).stream().toList();
		assertEquals(4, boxes.size());
		javafx.scene.control.CheckBox name = boxes.stream().filter(b -> b.getText().startsWith("Name"))
				.findFirst().orElseThrow();
		assertFalse(name.isSelected());
		assertEquals(3, boxes.stream().filter(javafx.scene.control.CheckBox::isSelected).count());
		clickOn(name);
		clickDialogButton("OK");
		assertEquals("Week 8 workshop", file.getLabel());
		assertEquals("2026-04-20T15:36:54Z", file.get("dateCreated").orElseThrow().asText());
		Entity author = listed(file.get("author").orElseThrow().get("@id").asText()).orElseThrow();
		assertEquals(List.of("Person"), author.getTypes());
		assertEquals("Josiah Carberry", author.getLabel());
		assertEquals("http://spdx.org/licenses/CC-BY-4.0", file.get("license").orElseThrow().get("@id").asText());
		assertTrue(listed("http://spdx.org/licenses/CC-BY-4.0").isPresent());
		// One undo step
		push(KeyCode.SHORTCUT, KeyCode.Z);
		assertEquals("Draft", listed("workshop.docx").orElseThrow().getLabel());
		push(KeyCode.SHORTCUT, KeyCode.SHIFT, KeyCode.Z);
		// Again: all there already
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().stream()
				.filter(e -> e.getId().equals("workshop.docx")).findFirst().map(entities().getItems()::indexOf)
				.orElseThrow()));
		fromFile(stage);
		assertTrue(lookup("Nothing new in “Week 8 workshop”").tryQuery().isPresent());
		clickDialogButton("OK");
	}

	/**
	 * A photo's metadata: where it was taken offered, but only given on
	 * purpose, as a Place with its point; its licence the SPDX licence.
	 */
	@Test
	public void metadataFromPhoto() throws Exception {
		entities();
		Path dir = Files.createTempDirectory("crate");
		try (var in = getClass().getResourceAsStream("/no/s11/wpsld/lookup/photo.jpg")) {
			Files.copy(in, dir.resolve("photo.jpg"));
		}
		Files.writeString(dir.resolve("ro-crate-metadata.json"), """
				{"@context": "https://w3id.org/ro/crate/1.3/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
				  {"@id": "./", "@type": "Dataset", "name": "A crate", "hasPart": {"@id": "photo.jpg"}},
				  {"@id": "photo.jpg", "@type": ["File", "ImageObject"], "name": "Bus stop"}]}""");
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir)).get());
		WaitForAsyncUtils.waitForFxEvents();
		Entity photo = listed("photo.jpg").orElseThrow();
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(photo)));
		fromFile(stage);
		javafx.scene.control.CheckBox where = lookup("#fileMetadata").lookup(".check-box")
				.queryAllAs(javafx.scene.control.CheckBox.class).stream()
				.filter(b -> b.getText().contains("53.466800, -2.233900")).findFirst().orElseThrow();
		assertFalse(where.isSelected());
		clickOn(where);
		clickDialogButton("OK");
		Entity place = listed(photo.get("contentLocation").orElseThrow().get("@id").asText()).orElseThrow();
		assertEquals(List.of("Place"), place.getTypes());
		Entity geometry = listed(place.get("geo").orElseThrow().get("@id").asText()).orElseThrow();
		assertTrue(geometry.get("asWKT").orElseThrow().asText(),
				geometry.get("asWKT").orElseThrow().asText().endsWith("POINT (-2.2339 53.4668)"));
		assertEquals("http://spdx.org/licenses/CC-BY-4.0", photo.get("license").orElseThrow().get("@id").asText());
		assertEquals("2026-04-20T15:36:54+01:00", photo.get("dateCreated").orElseThrow().asText());
		assertEquals("© 2026 Josiah Carberry", photo.get("copyrightNotice").orElseThrow().asText());
	}

	/**
	 * Crate > Update File Details, with what files say about themselves:
	 * given where the entity has none, not where a photo was taken nor the
	 * camera, nor over its own name.
	 */
	@Test
	public void updateFilesFromTheirMetadata() throws Exception {
		entities();
		Path dir = Files.createTempDirectory("crate");
		try (var in = getClass().getResourceAsStream("/no/s11/wpsld/lookup/photo.jpg")) {
			Files.copy(in, dir.resolve("photo.jpg"));
		}
		Files.writeString(dir.resolve("ro-crate-metadata.json"), """
				{"@context": "https://w3id.org/ro/crate/1.3/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
				  {"@id": "./", "@type": "Dataset", "name": "A crate", "hasPart": {"@id": "photo.jpg"}},
				  {"@id": "photo.jpg", "@type": "File", "name": "Bus stop"}]}""");
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir)).get());
		WaitForAsyncUtils.waitForFxEvents();
		Task<?> task = WaitForAsyncUtils.asyncFx(() -> app.updateFilesFromThem(stage, false, false)).get();
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, task::isDone);
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("1 file updated").tryQuery().isPresent());
		clickDialogButton("OK");
		Entity photo = listed("photo.jpg").orElseThrow();
		assertEquals("Bus stop", photo.getLabel());
		assertEquals("Bus stop on Oxford Road", photo.get("description").orElseThrow().asText());
		assertEquals("8 px", photo.get("width").orElseThrow().asText());
		assertEquals("http://spdx.org/licenses/CC-BY-4.0", photo.get("license").orElseThrow().get("@id").asText());
		assertEquals("Josiah Carberry",
				listed(photo.get("author").orElseThrow().get("@id").asText()).orElseThrow().getLabel());
		assertTrue(photo.get("contentLocation").isEmpty());
		assertTrue(photo.get("exifData").isEmpty());
		// Again: nothing new
		Task<?> again = WaitForAsyncUtils.asyncFx(() -> app.updateFilesFromThem(stage, false, false)).get();
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, again::isDone);
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("Nothing to update").tryQuery().isPresent());
		clickDialogButton("OK");
	}
}
