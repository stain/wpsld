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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.testfx.util.WaitForAsyncUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Crate &gt; Detach…: the relative @ids, of files and of other entities, are
 * made absolute against a base asked for, as RO-Crate requires of a detached
 * crate, and the base may be kept as the @base of the context.
 */
public class TestCrateEditorDetach extends UiTest {
	private CrateEditor app;

	private static final String CRATE = """
			{"@context": "https://w3id.org/ro/crate/1.3/context", "@graph": [
			 {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
			  "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
			 {"@id": "./", "@type": "Dataset", "name": "Survey", "description": "d", "datePublished": "2026-01-01",
			  "license": {"@id": "https://creativecommons.org/licenses/by/4.0/"}, "author": {"@id": "#alice"},
			  "hasPart": [{"@id": "data/"}, {"@id": "README.md"}, {"@id": "https://example.com/z.csv"}]},
			 {"@id": "data/", "@type": "Dataset", "name": "Data", "hasPart": {"@id": "data/counts.csv"}},
			 {"@id": "data/counts.csv", "@type": "File", "name": "Counts"},
			 {"@id": "README.md", "@type": "File", "name": "Read me"},
			 {"@id": "https://example.com/z.csv", "@type": "File", "name": "On the web"},
			 {"@id": "https://creativecommons.org/licenses/by/4.0/", "@type": "CreativeWork", "name": "CC BY"},
			 {"@id": "#alice", "@type": "Person", "name": "Alice"}
			]}""";

	@Override
	public void start(Stage stage) throws Exception {
		Path temp = Files.createTempDirectory("wpsld-detach");
		app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm,
				new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
						Optional.of(temp.resolve("profiles")), List.of()),
				Optional.of(temp.resolve("settings.json")));
		app.init();
		app.start(stage);
	}

	private Stage window() throws Exception {
		waitForFx(30, () -> lookup("#entities").tryQuery().isPresent() && lookup("#save").tryQuery().isPresent());
		return (Stage) lookup("#entities").queryListView().getScene().getWindow();
	}

	private MenuItem menuItem(String id) {
		return lookup("#menuBar").queryAs(MenuBar.class).getMenus().stream().flatMap(m -> m.getItems().stream())
				.filter(i -> id.equals(i.getId())).findFirst().orElseThrow();
	}

	/** A crate with its files beside it, opened (so attached), and the saved file of it, as JSON */
	private Path openCrate(Stage stage) throws Exception {
		Path folder = Files.createDirectories(Files.createTempDirectory("detach").resolve("survey"));
		Files.createDirectories(folder.resolve("data"));
		Files.writeString(folder.resolve("data/counts.csv"), "a\n");
		Files.writeString(folder.resolve("README.md"), "x\n");
		Files.writeString(folder.resolve("ro-crate-metadata.json"), CRATE);
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, folder.resolve("ro-crate-metadata.json"))).get());
		WaitForAsyncUtils.waitForFxEvents();
		return folder;
	}

	private JsonNode savedAs(Stage stage, Path file) throws Exception {
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.saveAsChecked(stage, file)).get());
		return new ObjectMapper().readTree(file.toFile());
	}

	private static List<String> ids(JsonNode saved) {
		List<String> ids = new ArrayList<>();
		saved.get("@graph").forEach(e -> ids.add(e.get("@id").asText()));
		return ids;
	}

	/** Made absolute against a web address, the root too, and undone in one step */
	@Test
	public void detachAgainstAWebAddress() throws Exception {
		Stage stage = window();
		Path folder = openCrate(stage);
		// Something to make absolute, so it is offered
		interact(() -> lookup("#menuBar").queryAs(MenuBar.class).getMenus().stream()
				.filter(m -> "menuCrate".equals(m.getId())).findFirst().orElseThrow().getOnShowing().handle(null));
		assertFalse(menuItem("menuDetach").isDisable());
		assertEquals("D_etach…", menuItem("menuDetach").getText());

		interact(() -> app.detach(new Detachment(Optional.of(URI.create("https://example.org/survey/")), false)));
		assertTrue(app.notice().orElseThrow(), app.notice().orElseThrow().startsWith("Detached: 4 identifiers made absolute against https://example.org/survey/"));
		JsonNode saved = savedAs(stage, folder.resolve("survey-ro-crate-metadata.json"));
		List<String> ids = ids(saved);
		// Files and folders, the root as the base is a web address, but not what is local or already absolute
		for (String id : List.of("https://example.org/survey/", "https://example.org/survey/data/",
				"https://example.org/survey/data/counts.csv", "https://example.org/survey/README.md", "#alice",
				"https://example.com/z.csv", "ro-crate-metadata.json")) {
			assertTrue(id + " in " + ids, ids.contains(id) || id.equals("ro-crate-metadata.json") && ids.contains(id));
		}
		assertFalse(ids.toString(), ids.contains("README.md"));
		assertFalse(ids.toString(), ids.contains("./"));
		// The descriptor is about the root, which is now absolute
		assertEquals("https://example.org/survey/", saved.get("@graph").get(0).get("about").get("@id").asText());
		// No @base unless asked for: the identifiers are absolute
		assertTrue(saved.get("@context").toString(), saved.get("@context").isTextual());

		// One undo step puts it all back
		interact(app::undo);
		WaitForAsyncUtils.waitForFxEvents();
		List<String> back = ids(savedAs(stage, folder.resolve("back-ro-crate-metadata.json")));
		assertTrue(back.toString(), back.containsAll(List.of("./", "data/", "README.md", "data/counts.csv")));
	}

	/** An anonymous arcp base: absolute ids, the root left as ./, and the base kept as @base to relativize by */
	@Test
	public void detachAgainstAnAnonymousBase() throws Exception {
		Stage stage = window();
		Path folder = openCrate(stage);
		URI base = DetachDialog.newArcpBase();
		assertTrue(base.toString(), base.toString().matches("arcp://uuid,[0-9a-f-]{36}/"));
		interact(() -> app.detach(new Detachment(Optional.of(base), true)));
		JsonNode saved = savedAs(stage, folder.resolve("anon-ro-crate-metadata.json"));
		List<String> ids = ids(saved);
		assertTrue(ids.toString(), ids.contains(base + "data/counts.csv"));
		assertTrue(ids.toString(), ids.contains(base + "README.md"));
		// An arcp address is not where the crate is online: the root may be ./
		assertTrue(ids.toString(), ids.contains("./"));
		JsonNode context = saved.get("@context");
		assertTrue(context.toString(), context.isArray() && context.size() == 2);
		assertEquals(base.toString(), context.get(1).get("@base").asText());
		// Detached: there is no folder to look in, and it is read back with the base
		assertEquals(Optional.of(base.toString()), new CrateReader().read(folder.resolve("anon-ro-crate-metadata.json"))
				.context().getBase());
	}

	/** The dialog: filled in, a new anonymous base, a warning about file: */
	@Test
	public void detachDialog() throws Exception {
		Stage stage = window();
		Crate crate = new CrateReader().read(new ByteArrayInputStream(CRATE.getBytes(StandardCharsets.UTF_8)), "test")
				.crate().orElseThrow();
		DetachDialog dialog = onFx(() -> new DetachDialog(stage, crate.relativeEntities(),
				Optional.of(URI.create("https://example.org/known"))));
		interact(dialog::show);
		// Known before: filled in, as a folder
		assertEquals("https://example.org/known", lookup("#detachBase").queryAs(TextField.class).getText());
		assertEquals(Optional.of(URI.create("https://example.org/known/")), dialog.detachment().orElseThrow().base());
		assertTrue(lookup("#detachIntro").queryAs(Label.class).getText().contains("3 identifiers are relative"));
		assertEquals("data/ would be https://example.org/known/data/", lookup("#detachExample").queryAs(Label.class).getText());
		assertEquals("", lookup("#detachWarning").queryAs(Label.class).getText());
		assertFalse(lookup("#detachRecordBase").queryAs(CheckBox.class).isSelected());

		// A new anonymous base, kept as @base
		clickOn("#detachAnonymous");
		String text = lookup("#detachBase").queryAs(TextField.class).getText();
		assertTrue(text, text.matches("arcp://uuid,[0-9a-f-]{36}/"));
		assertTrue(lookup("#detachRecordBase").queryAs(CheckBox.class).isSelected());
		assertEquals(text + "data/", lookup("#detachExample").queryAs(Label.class).getText().replaceFirst("^data/ would be ", ""));
		assertTrue(dialog.detachment().orElseThrow().recordBase());

		// file: only means something here
		interact(() -> lookup("#detachBase").queryAs(TextField.class).setText("file:///home/me/survey"));
		assertTrue(lookup("#detachWarning").queryAs(Label.class).getText(),
				lookup("#detachWarning").queryAs(Label.class).getText().startsWith("A file: address only means something"));
		assertTrue(DetachDialog.isFile(dialog.detachment().orElseThrow().base().orElseThrow()));
		// Not an address at all: nothing to detach with
		interact(() -> lookup("#detachBase").queryAs(TextField.class).setText("not an address"));
		assertEquals(Optional.empty(), dialog.detachment());
		assertTrue(lookup("#detachOk").query().isDisable());
		assertTrue(lookup("#detachWarning").queryAs(Label.class).getText().startsWith("Not an https:"));
		interact(dialog::close);
	}

	private static final String WITH_ADDRESSES = """
			{"@context": "https://w3id.org/ro/crate/1.3/context", "@graph": [
			 {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
			  "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
			 {"@id": "./", "@type": "Dataset", "name": "Survey", "description": "d", "datePublished": "2026-01-01",
			  "license": {"@id": "SITE/licence"},
			  "hasPart": [{"@id": "README.md"}, {"@id": "missing.csv"}, {"@id": "other.csv"}]},
			 {"@id": "README.md", "@type": "File", "name": "Read me", "contentUrl": "SITE/files/readme-download"},
			 {"@id": "missing.csv", "@type": "File", "name": "Missing"},
			 {"@id": "other.csv", "@type": "File", "name": "Two", "url": ["SITE/files/one", "SITE/files/two"]},
			 {"@id": "SITE/licence", "@type": "CreativeWork", "name": "A licence"}
			]}""";

	/** The dialog's two options: use a file's own contentUrl, and check the addresses afterwards (not for a base that is no place) */
	@Test
	public void detachDialogOptions() throws Exception {
		Stage stage = window();
		Crate crate = new CrateReader().read(new ByteArrayInputStream(WITH_ADDRESSES.replace("SITE", "https://x.example")
				.getBytes(StandardCharsets.UTF_8)), "test").crate().orElseThrow();
		DetachDialog dialog = onFx(() -> new DetachDialog(stage, crate.relativeEntities(),
				Optional.of(URI.create("https://example.org/survey/"))));
		interact(dialog::show);
		// Three files, one that says where it is (the one with two urls does not say which)
		CheckBox use = lookup("#detachUseContentUrl").queryAs(CheckBox.class);
		assertTrue(use.getText(), use.getText().endsWith("(1 of 3)"));
		assertFalse(use.isDisabled());
		assertFalse(use.isSelected());
		// A web address: checked afterwards, by default
		CheckBox check = lookup("#detachCheck").queryAs(CheckBox.class);
		assertTrue(check.isSelected());
		assertTrue(dialog.detachment().orElseThrow().checkAvailability());
		assertFalse(dialog.detachment().orElseThrow().useDownloadAddresses());
		interact(use::fire);
		assertTrue(dialog.detachment().orElseThrow().useDownloadAddresses());
		assertEquals("README.md would be https://x.example/files/readme-download",
				lookup("#detachExample").queryAs(Label.class).getText());
		// An anonymous base is nowhere the files can be asked for
		clickOn("#detachAnonymous");
		assertTrue(lookup("#detachCheck").queryAs(CheckBox.class).isDisabled());
		assertFalse(lookup("#detachCheck").queryAs(CheckBox.class).isSelected());
		assertFalse(dialog.detachment().orElseThrow().checkAvailability());
		// Back to a web address: chosen for them again, unless they chose
		interact(() -> lookup("#detachBase").queryAs(TextField.class).setText("https://example.org/again/"));
		assertTrue(lookup("#detachCheck").queryAs(CheckBox.class).isSelected());
		interact(() -> lookup("#detachCheck").queryAs(CheckBox.class).fire());
		interact(() -> lookup("#detachBase").queryAs(TextField.class).setText("https://example.org/third/"));
		assertFalse(lookup("#detachCheck").queryAs(CheckBox.class).isSelected());
		interact(dialog::close);
	}

	/** The files' own addresses as their @ids, then every web address asked about (contentUrl and url too) */
	@Test
	public void detachUsingContentUrlThenCheck() throws Exception {
		com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
				new java.net.InetSocketAddress("127.0.0.1", 0), 0);
		java.util.Set<String> there = java.util.Set.of("/survey/", "/licence", "/files/readme-download", "/files/one");
		server.createContext("/", exchange -> {
			exchange.sendResponseHeaders(there.contains(exchange.getRequestURI().getPath()) ? 200 : 404, -1);
			exchange.close();
		});
		server.start();
		try {
			URI site = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
			Stage stage = window();
			Path folder = Files.createDirectories(Files.createTempDirectory("detach").resolve("survey"));
			Files.writeString(folder.resolve("README.md"), "x\n");
			Files.writeString(folder.resolve("missing.csv"), "x\n");
			Files.writeString(folder.resolve("other.csv"), "x\n");
			Files.writeString(folder.resolve("ro-crate-metadata.json"), WITH_ADDRESSES.replace("SITE", site.toString()));
			assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, folder.resolve("ro-crate-metadata.json"))).get());
			WaitForAsyncUtils.waitForFxEvents();
			interact(() -> app.detach(stage, new Detachment(Optional.of(site.resolve("/survey/")), false, true, true)));
			// README.md is its own contentUrl; missing.csv and other.csv (two urls: no telling) are at the base.
			// Asked about: the root (the base), the licence and readme-download (there), missing.csv and other.csv
			// (not), and both urls of other.csv, of which one is there
			waitForFx(60, () -> lookup(".dialog-pane").tryQuery().isPresent());
			javafx.scene.control.DialogPane pane = lookup(".dialog-pane").queryAs(javafx.scene.control.DialogPane.class);
			assertTrue(pane.getHeaderText(), pane.getHeaderText().contains("web resources are not available"));
			String content = pane.getContentText();
			assertTrue(content, content.contains(site + "/survey/missing.csv"));
			assertTrue(content, content.contains(site + "/survey/other.csv"));
			assertTrue(content, content.contains(site + "/files/two"));
			assertFalse(content, content.contains("readme-download"));
			assertFalse(content, content.contains(site + "/files/one"));
		} finally {
			server.stop(0);
		}
	}

	@Test
	public void bases() {
		assertEquals(Optional.of(URI.create("https://example.org/a/")), DetachDialog.baseOf("https://example.org/a"));
		assertEquals(Optional.of(URI.create("https://example.org/a/?x=1")), DetachDialog.baseOf("https://example.org/a/?x=1"));
		assertEquals(Optional.of(URI.create("arcp://uuid,b7749d0b-0e47-5fc4-999d-f154abe68065/")),
				DetachDialog.baseOf("arcp://uuid,b7749d0b-0e47-5fc4-999d-f154abe68065"));
		assertEquals(Optional.empty(), DetachDialog.baseOf("ftp://example.org/a/"));
		assertEquals(Optional.empty(), DetachDialog.baseOf("data/files"));
		assertEquals(Optional.empty(), DetachDialog.baseOf(""));
	}
}
