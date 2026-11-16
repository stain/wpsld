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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.testfx.util.WaitForAsyncUtils;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.lookup.FileMetadata;
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
		Task<?> task = WaitForAsyncUtils.asyncFx(() -> app.updateFilesFromThem(stage)).get();
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, task::isDone);
		WaitForAsyncUtils.waitForFxEvents();
		// Read, but given only on a click
		assertTrue(listed("photo.jpg").orElseThrow().get("description").isEmpty());
		String notice = app.notice().orElseThrow();
		assertTrue(notice, notice.startsWith("Read 1 file. It has its own description"));
		clickOn("#noticeAction");
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("1 file updated").tryQuery().isPresent());
		clickDialogButton("OK");
		assertEquals("_Undo Metadata from Files", undoName(stage));
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
		Task<?> again = WaitForAsyncUtils.asyncFx(() -> app.updateFilesFromThem(stage)).get();
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, again::isDone);
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(Optional.of("Read 1 file. Nothing their entities lack."), app.notice());
	}

	/** An empty crate in a folder of its own, with photos in it not yet described, opened */
	private Path crateWithPhotos(Stage stage, String... photos) throws Exception {
		Path dir = Files.createTempDirectory("crate");
		for (String photo : photos) {
			try (var in = getClass().getResourceAsStream("/no/s11/wpsld/lookup/photo.jpg")) {
				Files.copy(in, dir.resolve(photo));
			}
		}
		Files.writeString(dir.resolve("ro-crate-metadata.json"), """
				{"@context": "https://w3id.org/ro/crate/1.3/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
				  {"@id": "./", "@type": "Dataset", "name": "A crate"}]}""");
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir)).get());
		WaitForAsyncUtils.waitForFxEvents();
		return dir;
	}

	/**
	 * A file inserted is read in the background, and a notice offers what
	 * it says, to review in the dialog
	 */
	@Test
	public void fileAddedOffered() throws Exception {
		entities();
		Stage stage = (Stage) entities().getScene().getWindow();
		Path dir = crateWithPhotos(stage, "photo.jpg");
		interact(() -> app.insertFiles(stage, List.of(dir.resolve("photo.jpg"))));
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> app.notice().isPresent());
		WaitForAsyncUtils.waitForFxEvents();
		String notice = app.notice().orElseThrow();
		assertTrue(notice, notice.startsWith("“photo.jpg” has its own description, author"));
		assertTrue(notice, notice.contains("licence"));
		// Not where it was taken, nor the camera
		assertFalse(notice, notice.contains("contentLocation") || notice.contains("exifData"));
		clickOn("#noticeAction");
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#fileMetadata").tryQuery().isPresent());
		clickDialogButton("OK");
		Entity photo = listed("photo.jpg").orElseThrow();
		assertEquals("Bus stop on Oxford Road", photo.get("description").orElseThrow().asText());
		assertTrue(app.notice().isEmpty());
	}

	/** Several files added that say something: filled in where their entities have nothing, at once */
	@Test
	public void filesAddedFilledIn() throws Exception {
		entities();
		Stage stage = (Stage) entities().getScene().getWindow();
		Path dir = crateWithPhotos(stage, "one.jpg", "two.jpg");
		interact(() -> app.insertFiles(stage, List.of(dir.resolve("one.jpg"), dir.resolve("two.jpg"))));
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> app.notice().isPresent());
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(app.notice().orElseThrow(), app.notice().orElseThrow().startsWith("2 of the files added have"));
		// Typed meanwhile: kept, and an undo step of its own
		Entity one = listed("one.jpg").orElseThrow();
		interact(() -> one.set("description", Optional.of(com.fasterxml.jackson.databind.node.TextNode.valueOf("Mine"))));
		clickOn("#noticeAction");
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("2 files updated").tryQuery().isPresent());
		clickDialogButton("OK");
		assertEquals("Mine", listed("one.jpg").orElseThrow().get("description").orElseThrow().asText());
		Entity two = listed("two.jpg").orElseThrow();
		assertEquals("Bus stop on Oxford Road", two.get("description").orElseThrow().asText());
		assertTrue(two.get("contentLocation").isEmpty());
		assertEquals("Josiah Carberry",
				listed(listed("one.jpg").orElseThrow().get("author").orElseThrow().get("@id").asText()).orElseThrow()
						.getLabel());
		// Undone at once, as one step, leaving what was typed
		assertEquals("_Undo Metadata from Files", undoName(stage));
		interact(menuItem("menuUndo")::fire);
		assertTrue(listed("two.jpg").orElseThrow().get("description").isEmpty());
		assertTrue(listed("one.jpg").orElseThrow().get("author").isEmpty());
		assertEquals("Mine", listed("one.jpg").orElseThrow().get("description").orElseThrow().asText());
	}

	/** More files than are read at once: offered, not read, and read with progress if asked */
	@Test
	public void manyFilesAddedOffered() throws Exception {
		entities();
		Stage stage = (Stage) entities().getScene().getWindow();
		String[] photos = java.util.stream.IntStream.rangeClosed(1, CrateEditor.READ_AT_ONCE + 1)
				.mapToObj(i -> "photo" + i + ".jpg").toArray(String[]::new);
		Path dir = crateWithPhotos(stage, photos);
		interact(() -> app.insertFiles(stage, java.util.Arrays.stream(photos).map(dir::resolve).toList()));
		assertEquals(Optional.of("Added " + photos.length + " files. Files can say who made them, when and under which"
				+ " licence."), app.notice());
		clickOn("#noticeAction");
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
				() -> app.notice().filter(n -> n.startsWith("Read " + photos.length + " files. All")).isPresent());
		clickOn("#noticeAction");
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup(photos.length + " files updated").tryQuery().isPresent());
		clickDialogButton("OK");
		assertEquals("Bus stop on Oxford Road", listed("photo1.jpg").orElseThrow().get("description").orElseThrow()
				.asText());
	}

	/** A file added that says nothing its entity lacks: no notice */
	@Test
	public void fileAddedSayingNothing() throws Exception {
		entities();
		Stage stage = (Stage) entities().getScene().getWindow();
		Path dir = crateWithPhotos(stage);
		Files.writeString(dir.resolve("notes.txt"), "Nothing to say");
		interact(() -> app.insertFiles(stage, List.of(dir.resolve("notes.txt"))));
		Entity notes = listed("notes.txt").orElseThrow();
		Task<?> task = WaitForAsyncUtils.asyncFx(() -> app.offerWhatFilesSay(stage, List.of(notes))).get()
				.orElseThrow();
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, task::isDone);
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(Optional.empty(), app.notice());
	}

	private MenuItem menuItem(String id) {
		MenuBar bar = lookup("#menuBar").queryAs(MenuBar.class);
		return bar.getMenus().stream().flatMap(m -> m.getItems().stream()).filter(i -> id.equals(i.getId()))
				.findFirst().orElseThrow();
	}

	/** The Undo menu item's text, as the Edit menu names it when shown */
	private String undoName(Stage stage) throws Exception {
		MenuItem undo = menuItem("menuUndo");
		return WaitForAsyncUtils.asyncFx(() -> {
			app.nameUndo(stage, undo, menuItem("menuRedo"));
			return undo.getText();
		}).get();
	}

	/** A job reading files that runs until cancelled, as one taking long */
	private static Task<Map<String, FileMetadata.Reading>> slowJob() {
		return new Task<>() {
			@Override
			protected Map<String, FileMetadata.Reading> call() throws InterruptedException {
				updateMessage("0 of 1000 files read");
				new java.util.concurrent.CountDownLatch(1).await();
				return Map.of();
			}
		};
	}

	/**
	 * Opening another crate while reading files the user asked to: asked
	 * whether to stop; if not, the crate stays; if so, it is cancelled
	 */
	@Test
	public void switchingCrateStopsReading() throws Exception {
		entities();
		Stage stage = (Stage) entities().getScene().getWindow();
		crateWithPhotos(stage, "photo.jpg");
		Task<Map<String, FileMetadata.Reading>> job = WaitForAsyncUtils
				.asyncFx(() -> app.inBackground(stage, "Reading slowly", slowJob())).get();
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(job::isRunning).get());
		Platform.runLater(() -> app.newCrate(stage));
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
				() -> lookup("Still working in the background").tryQuery().isPresent());
		assertTrue(lookup(".dialog-pane .content").queryLabeled().getText().contains("Reading slowly (0 of 1000 files read)"));
		clickDialogButton("Cancel");
		assertTrue(WaitForAsyncUtils.asyncFx(job::isRunning).get());
		Platform.runLater(() -> app.newCrate(stage));
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
				() -> lookup("Still working in the background").tryQuery().isPresent());
		clickDialogButton("Stop");
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(job::isCancelled).get());
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(listed("photo.jpg").isEmpty());
	}

	/**
	 * Files added are read quietly; opening another crate meanwhile stops
	 * that without asking, and nothing read is offered for, nor given to,
	 * the new crate
	 */
	@Test
	public void switchingCrateDropsQuietReading() throws Exception {
		entities();
		Stage stage = (Stage) entities().getScene().getWindow();
		Path dir = crateWithPhotos(stage, "photo.jpg");
		Path other = crateWithPhotos(stage, "photo.jpg");
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir)).get());
		interact(() -> {
			app.insertFiles(stage, List.of(dir.resolve("photo.jpg")));
			// Not asked: files added are read without asking
			assertTrue(app.open(stage, other));
		});
		Thread.sleep(1000);
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(Optional.empty(), app.notice());
		assertTrue(listed("photo.jpg").isEmpty());
	}

	/**
	 * A job in the background shows at the foot of the window; Show… brings
	 * up the jobs' window, Run in Background hides it, and Cancel there
	 * cancels it, which ends the strip
	 */
	@Test
	public void jobsStrip() throws Exception {
		entities();
		Stage stage = (Stage) entities().getScene().getWindow();
		assertFalse(lookup("#jobsStrip").query().isVisible());
		Task<Map<String, FileMetadata.Reading>> job = WaitForAsyncUtils
				.asyncFx(() -> app.inBackground(stage, "Reading slowly", slowJob())).get();
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#jobsStrip").query().isVisible()
				&& lookup("#jobsLatest").queryLabeled().getText().equals("Reading slowly: 0 of 1000 files read"));
		// Shown by itself after a moment, as the user asked for it
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#jobsHide").tryQuery().isPresent());
		clickOn("#jobsHide");
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("#jobsHide").tryQuery().isEmpty());
		assertTrue(WaitForAsyncUtils.asyncFx(job::isRunning).get());
		clickOn("#jobsShow");
		WaitForAsyncUtils.waitForFxEvents();
		clickOn("#jobCancel");
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.asyncFx(job::isCancelled).get());
		WaitForAsyncUtils.waitForFxEvents();
		assertFalse(lookup("#jobsStrip").query().isVisible());
		assertTrue(lookup("#jobsHide").tryQuery().isEmpty());
	}
}
