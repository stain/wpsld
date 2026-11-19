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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.After;
import org.junit.Test;
import org.testfx.util.WaitForAsyncUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Open URL: a crate from the web is attached or detached by where it came from
 * after redirects, and its relative @ids lead there, though they stay as written.
 * (Apart from TestCrateEditor, whose JVM would otherwise run out of memory:
 * every test's window stays in memory, see ui/build.gradle.)
 */
public class TestCrateEditorWebCrate extends UiTest {
	private CrateEditor app;
	private HttpServer server;
	/** Whether /README.md answers 200, as the first file of the example crate, to be found beside the document */
	private volatile boolean readmeThere;
	/** Held until released, by /slow/ro-crate-metadata.json */
	private final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

	@Override
	public void start(Stage stage) throws Exception {
		Path temp = Files.createTempDirectory("wpsld-web");
		app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm,
				new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
						Optional.of(temp.resolve("profiles")), List.of()),
				Optional.of(temp.resolve("settings.json")));
		app.init();
		app.start(stage);
	}

	@After
	public void stopServer() {
		release.countDown();
		if (server != null) {
			server.stop(0);
		}
	}

	/** The example crate at /crate1/ro-crate-metadata.json and, by another name, at /x-ro-crate-metadata.json */
	private URI serve() throws IOException {
		byte[] crate;
		try (var in = getClass().getResourceAsStream("/example-crate/ro-crate-metadata.json")) {
			crate = in.readAllBytes();
		}
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			if (path.equals("/doi/x")) {
				exchange.getResponseHeaders().add("Location", "/crate1/ro-crate-metadata.json");
				exchange.sendResponseHeaders(302, -1);
			} else if (path.equals("/crate1/ro-crate-metadata.json") || path.equals("/x-ro-crate-metadata.json")
					|| path.equals("/slow/ro-crate-metadata.json")) {
				if (path.startsWith("/slow")) {
					try {
						release.await();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
				exchange.getResponseHeaders().add("Content-Type", "application/ld+json");
				exchange.sendResponseHeaders(200, crate.length);
				exchange.getResponseBody().write(crate);
			} else if (path.equals("/README.md") && readmeThere) {
				exchange.sendResponseHeaders(200, -1);
			} else {
				exchange.sendResponseHeaders(404, -1);
			}
			exchange.close();
		});
		server.start();
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
	}

	private Stage window() throws Exception {
		waitForFx(30, () -> lookup("#entities").tryQuery().isPresent() && lookup("#save").tryQuery().isPresent());
		return (Stage) lookup("#entities").queryListView().getScene().getWindow();
	}

	private ListView<Entity> entities() {
		return onFx(() -> lookup("#entities").queryListView());
	}

	private void select(String id) {
		Entity entity = entities().getItems().stream().filter(e -> e.getId().equals(id)).findFirst().orElseThrow();
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(entity)));
	}

	/** A crate named ro-crate-metadata.json is attached to its folder, found by a redirect from a DOI's address */
	@Test
	public void attachedCrateAfterRedirect() throws Exception {
		URI site = serve();
		Stage stage = window();
		List<String> opened = new ArrayList<>();
		interact(() -> app.browse = opened::add);
		interact(() -> app.openUrl(stage, site.resolve("/doi/x")));
		waitForFx(30, () -> app.notice().isPresent());
		String notice = app.notice().orElseThrow();
		assertTrue(notice, notice.contains("attached crate"));
		assertTrue(notice, notice.contains("lead to " + site.resolve("/crate1/")));
		assertTrue(stage.getTitle(), stage.getTitle().contains("crate1/ro-crate-metadata.json"));

		select("README.md");
		assertFalse(lookup("#openId").query().isDisabled());
		clickOn("#openId");
		assertEquals(List.of(site.resolve("/crate1/README.md").toString()), opened);
		// The @id is not changed, nor is anything outside the crate's folder to open
		assertTrue(entities().getItems().stream().anyMatch(e -> e.getId().equals("README.md")));
		select("#alice");
		assertTrue(lookup("#openId").query().isDisabled());
	}

	/** Another name for the metadata file means a crate without a folder: detached, still resolved against the document */
	@Test
	public void detachedCrate() throws Exception {
		URI site = serve();
		Stage stage = window();
		interact(() -> app.openUrl(stage, site.resolve("/x-ro-crate-metadata.json")));
		waitForFx(30, () -> app.notice().isPresent());
		String notice = app.notice().orElseThrow();
		assertTrue(notice, notice.contains("detached crate"));
		// Its files have relative @ids, but there is no telling where: not linked
		assertTrue(notice, notice.contains("neither fully detached nor attached"));
		assertTrue(notice, notice.contains("There is no telling where"));
		// What its root could be: the folder beside it, the address having no query
		assertTrue(notice, notice.contains("Its RO-Crate Root could be " + site.resolve("/") + " (the folder beside the metadata document)"));
		select("README.md");
		assertTrue(lookup("#openId").query().isDisabled());
	}

	/**
	 * A semi-detached crate's first file is looked for beside where the root
	 * could be, and if it is there, that is the base: nothing is guessed, and
	 * nothing is chosen for the user otherwise.
	 */
	@Test
	public void findsTheFilesBesideTheDocument() throws Exception {
		readmeThere = true;
		URI site = serve();
		Stage stage = window();
		List<String> opened = new ArrayList<>();
		interact(() -> app.browse = opened::add);
		interact(() -> app.openUrl(stage, site.resolve("/x-ro-crate-metadata.json")));
		waitForFx(30, () -> app.notice().filter(n -> n.contains("Found the files beside")).isPresent());
		String notice = app.notice().orElseThrow();
		assertTrue(notice, notice.contains("Found the files beside " + site.resolve("/")));
		assertTrue(notice, notice.contains("README.md is there"));
		select("README.md");
		assertFalse(lookup("#openId").query().isDisabled());
		clickOn("#openId");
		assertEquals(List.of(site.resolve("/README.md").toString()), opened);
		// Still as written
		assertTrue(entities().getItems().stream().anyMatch(e -> e.getId().equals("README.md")));
	}

	@Test
	public void webAddresses() {
		assertEquals(Optional.of(URI.create("https://doi.org/10.5281/zenodo.1234")),
				CrateEditor.webAddress("doi:10.5281/zenodo.1234"));
		assertEquals(Optional.of(URI.create("https://doi.org/10.5281/zenodo.1234")),
				CrateEditor.webAddress("10.5281/zenodo.1234"));
		assertEquals(Optional.of(URI.create("https://doi.org/10.5281/zenodo.1234")),
				CrateEditor.webAddress("https://doi.org/10.5281/zenodo.1234"));
		assertEquals(Optional.of(URI.create("http://localhost:8000/example-crate/")),
				CrateEditor.webAddress("http://localhost:8000/example-crate/"));
		assertEquals(Optional.empty(), CrateEditor.webAddress("file:///etc/passwd"));
		assertEquals(Optional.empty(), CrateEditor.webAddress("not an address"));
		assertEquals(Optional.empty(), CrateEditor.webAddress("ftp://example.com/x"));
	}

	/** Opens the crate, and asks to save it as a file, answering the warning with a button; the saved file's @ids */
	private List<String> saveAs(String button, String name) throws Exception {
		URI site = serve();
		Stage stage = window();
		interact(() -> app.openUrl(stage, site.resolve("/crate1/ro-crate-metadata.json")));
		waitForFx(30, () -> app.notice().isPresent());
		Path file = Files.createTempDirectory("saved").resolve(name);
		var saved = WaitForAsyncUtils.asyncFx(() -> app.saveAsChecked(stage, file));
		waitForFx(30, () -> lookup(".dialog-pane").tryQuery().isPresent());
		clickOn(lookup(".dialog-pane .button").match((Button b) -> b.getText().equals(button)).queryButton());
		WaitForAsyncUtils.waitForFxEvents();
		boolean done = saved.get();
		assertEquals(!button.equals("Cancel"), done);
		assertEquals(done, Files.exists(file));
		if (!done) {
			return List.of();
		}
		JsonNode graph = new ObjectMapper().readTree(file.toFile()).get("@graph");
		List<String> ids = new ArrayList<>();
		graph.forEach(e -> ids.add(e.get("@id").asText()));
		return ids;
	}

	/** Saved elsewhere, relative @ids would name other files: Make Absolute keeps them leading to the web copies */
	@Test
	public void saveAsMakeAbsolute() throws Exception {
		List<String> ids = saveAs("Make Absolute", "ro-crate-metadata.json");
		String site = "http://127.0.0.1:" + server.getAddress().getPort() + "/crate1/";
		assertTrue(ids.toString(), ids.contains(site + "README.md"));
		assertTrue(ids.toString(), ids.contains(site + "data/counts.csv"));
		assertFalse(ids.toString(), ids.contains("README.md"));
		// An attached crate's root is its folder, and local ids are local
		assertTrue(ids.toString(), ids.contains("./"));
		assertTrue(ids.toString(), ids.contains("#alice"));
	}

	@Test
	public void saveAsKeepRelative() throws Exception {
		List<String> ids = saveAs("Keep Relative", "copy-ro-crate-metadata.json");
		assertTrue(ids.toString(), ids.contains("README.md"));
		assertTrue(ids.toString(), ids.contains("data/counts.csv"));
	}

	@Test
	public void saveAsCancelled() throws Exception {
		assertEquals(List.of(), saveAs("Cancel", "ro-crate-metadata.json"));
	}

	/**
	 * While a crate is fetched a window says so, modal, with Cancel: the crate
	 * being edited stays as it is, and cancelled, it is not replaced when the
	 * server answers after all.
	 */
	@Test
	public void cancelWhileFetching() throws Exception {
		URI site = serve();
		Stage stage = window();
		String title = stage.getTitle();
		interact(() -> app.openUrl(stage, site.resolve("/slow/ro-crate-metadata.json")));
		waitForFx(30, () -> lookup("#openUrlProgress").tryQuery().isPresent());
		assertTrue(app.notice().isEmpty());
		assertEquals(title, stage.getTitle());
		clickOn(lookup(".dialog-pane .button").match((Button b) -> b.getText().equals("Cancel")).queryButton());
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("#openUrlProgress").tryQuery().isEmpty());
		// The answer comes, too late
		release.countDown();
		Thread.sleep(500);
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(app.notice().isEmpty());
		assertEquals(title, stage.getTitle());
	}
}
