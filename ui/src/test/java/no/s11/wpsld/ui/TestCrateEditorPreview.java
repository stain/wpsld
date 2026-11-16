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

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * The Preview tab's links and descriptions.
 * (Apart from TestCrateEditor, whose JVM would otherwise run out of memory:
 * every test's window stays in memory, see ui/build.gradle.)
 */
public class TestCrateEditorPreview extends UiTest {
	private CrateEditor app;

	@Override
	public void start(Stage stage) throws Exception {
		Path temp = Files.createTempDirectory("wpsld-preview");
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

	/**
	 * Compact IRIs open as expanded with the crate's @context, in the preview
	 * and beside the @id; text values are links when the property takes URLs
	 * (to the web or a file beside the crate), not when it takes only text,
	 * and for unknown properties when they are web addresses.
	 */
	@Test
	public void previewWebLinks() throws Exception {
		entities();
		Path dir = Files.createTempDirectory("crate");
		Files.writeString(dir.resolve("data.csv"), "a,b\n");
		Files.writeString(dir.resolve("ro-crate-metadata.json"), """
				{"@context": ["https://w3id.org/ro/crate/1.3/context", {"s": "http://schema.org/"}],
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
				  {"@id": "./", "@type": "Dataset", "name": "A crate", "url": "data.csv",
				   "alternateName": "https://example.com/name", "sameAs": "https://example.com/same",
				   "s:unknownTerm": "https://example.com/unknown",
				   "mentions": [{"@id": "s:ActiveActionStatus"}, {"@id": "s:CompletedActionStatus"}]},
				  {"@id": "s:ActiveActionStatus", "@type": "Thing"}]}""");
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir)).get());
		WaitForAsyncUtils.waitForFxEvents();
		List<String> opened = new java.util.ArrayList<>();
		interact(() -> app.browse = opened::add);
		Entity root = entities().getItems().stream().filter(e -> e.getId().equals("./")).findFirst().orElseThrow();
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(root)));
		java.util.Set<String> links = from(lookup("#previewProperties").queryAs(Node.class)).lookup(".hyperlink")
				.queryAllAs(Hyperlink.class).stream().map(Hyperlink::getText)
				.collect(java.util.stream.Collectors.toSet());
		assertTrue(links.toString(), links.containsAll(java.util.Set.of("data.csv", "https://example.com/same",
				"https://example.com/unknown", "s:CompletedActionStatus")));
		assertFalse(links.toString(), links.contains("https://example.com/name"));
		Hyperlink completed = previewLink("#previewProperties", "s:CompletedActionStatus");
		interact(completed::fire);
		assertEquals(List.of("http://schema.org/CompletedActionStatus"), opened);

		Entity status = entities().getItems().stream().filter(e -> e.getId().equals("s:ActiveActionStatus"))
				.findFirst().orElseThrow();
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(status)));
		Hyperlink id = lookup("#previewId").queryAs(Hyperlink.class);
		assertEquals("s:ActiveActionStatus", id.getText());
		assertFalse(lookup("#openId").query().isDisabled());
		clickOn("#openId");
		assertEquals("http://schema.org/ActiveActionStatus", opened.get(1));
	}

	/** A description written in Markdown is formatted in the preview; others are plain text */
	@Test
	public void previewMarkdownDescription() throws Exception {
		entities();
		Path dir = Files.createTempDirectory("crate");
		Files.writeString(dir.resolve("ro-crate-metadata.json"), """
				{"@context": "https://w3id.org/ro/crate/1.3/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
				  {"@id": "./", "@type": "Dataset", "name": "A crate",
				   "description": "# Workshop\\n\\nUse **DuckDB**, see [the site](https://duckdb.org/).\\n\\n| a | b |\\n|---|---|\\n| 1 | 2 |",
				   "mentions": {"@id": "#p"}},
				  {"@id": "#p", "@type": "Person", "name": "P", "description": "Line one\\nline two"}]}""");
		Stage stage = (Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir)).get());
		WaitForAsyncUtils.waitForFxEvents();
		List<String> opened = new java.util.ArrayList<>();
		interact(() -> app.browse = opened::add);
		interact(() -> entities().getSelectionModel().clearAndSelect(0));
		javafx.scene.text.TextFlow flow = lookup("#previewDescription").queryAs(javafx.scene.text.TextFlow.class);
		assertTrue(flow.getChildren().stream().anyMatch(n -> n instanceof javafx.scene.text.Text t
				&& t.getText().equals("DuckDB") && t.getStyle().contains("bold")));
		assertTrue(flow.getAccessibleText(), flow.getAccessibleText().contains("Workshop"));
		Hyperlink site = flow.getChildren().stream().filter(Hyperlink.class::isInstance).map(Hyperlink.class::cast)
				.findFirst().orElseThrow();
		assertEquals("the site", site.getText());
		interact(site::fire);
		assertEquals(List.of("https://duckdb.org/"), opened);
		Entity person = entities().getItems().stream().filter(e -> e.getId().equals("#p")).findFirst().orElseThrow();
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(person)));
		assertEquals("Line one\nline two", lookup("#previewDescription").queryAs(Label.class).getText());
	}
}
