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

import com.sun.net.httpserver.HttpServer;

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
			} else if (path.equals("/crate1/ro-crate-metadata.json") || path.equals("/x-ro-crate-metadata.json")) {
				exchange.getResponseHeaders().add("Content-Type", "application/ld+json");
				exchange.sendResponseHeaders(200, crate.length);
				exchange.getResponseBody().write(crate);
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
		assertTrue(app.notice().orElseThrow(), app.notice().orElseThrow().contains("detached crate"));
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
}
