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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.lookup.DOI;
import no.s11.wpsld.lookup.DOI.Author;
import no.s11.wpsld.lookup.DOI.Work;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Works found by DOI or title (here a stand-in for Crossref and DataCite,
 * which looks a DOI up, in any of its forms, and searches titles).
 */
public class TestCrateEditorWorks extends ApplicationTest {
	private static final Work SPEC = new Work("10.5281/zenodo.3541888", "ScholarlyArticle",
			"RO-Crate Metadata Specification 1.0",
			List.of(new Author("Peter Sefton", Optional.of("Peter"), Optional.of("Sefton"),
					Optional.of("0000-0002-3545-944X"))),
			Optional.of("2019-11-15"), Optional.empty(), List.of(), Optional.empty(), Optional.empty(),
			Optional.empty(), Optional.of("Zenodo"), Optional.of("This document specifies a method."), "DataCite");
	private static final Work GRAPHS = new Work("10.1109/tcyb.2014.2386282", "ScholarlyArticle",
			"Topic Model for Graph Mining",
			List.of(new Author("Junyu Xuan", Optional.of("Junyu"), Optional.of("Xuan"), Optional.empty())),
			Optional.of("2015-12"), Optional.of("IEEE Transactions on Cybernetics"), List.of("2168-2267"),
			Optional.of("45"), Optional.of("12"), Optional.of("2792-2803"), Optional.empty(), Optional.empty(),
			"Crossref");
	private final List<String> searches = new ArrayList<>();

	@Override
	public void start(Stage stage) throws Exception {
		Path temp = Files.createTempDirectory("wpsld-works");
		CrateEditor app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm,
				new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
						Optional.of(temp.resolve("profiles")), List.of()),
				Optional.of(temp.resolve("settings.json")));
		app.setWorkSearch(query -> {
			searches.add(query);
			return DOI.doiOf(query).isPresent() ? List.of(SPEC)
					: query.toLowerCase().contains("graph") ? List.of(GRAPHS) : List.of();
		});
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

	private Optional<Entity> entity(String id) {
		return entities().getItems().stream().filter(e -> e.getId().equals(id)).findFirst();
	}

	/** New ▾ Work by DOI… on the crate's Citation, searching for the query */
	private void cite(String query) throws Exception {
		interact(() -> entities().getSelectionModel().clearAndSelect(0));
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("citation"));
		MenuButton create = lookup("#new-citation").queryAs(MenuButton.class);
		// Shown and waited for, so not inside interact()
		Platform.runLater(() -> create.getItems().stream().filter(i -> "newDoi-citation".equals(i.getId()))
				.findFirst().orElseThrow().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#workQuery").tryQuery().isPresent());
		interact(() -> lookup("#workQuery").queryAs(TextField.class).setText(query));
		interact(() -> lookup("#workSearch").queryButton().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#workResults").tryQuery().isPresent()
				&& !lookup("#workResults").queryListView().getItems().isEmpty());
		interact(() -> lookup("#workOk").queryButton().fire());
	}

	/** A bare DOI, as people copy them from papers */
	@Test
	public void citationByBareDoi() throws Exception {
		Entity root = entities().getItems().get(0);
		cite("10.5281/zenodo.3541888");
		assertEquals(List.of("10.5281/zenodo.3541888"), searches);
		Entity spec = entity("https://doi.org/10.5281/zenodo.3541888").orElseThrow();
		assertEquals(List.of("ScholarlyArticle"), spec.getTypes());
		assertEquals("RO-Crate Metadata Specification 1.0", spec.getLabel());
		assertEquals("This document specifies a method.", spec.get("abstract").orElseThrow().asText());
		assertEquals(spec.getId(), root.get("citation").orElseThrow().path("@id").asText());
		Entity sefton = entity("https://orcid.org/0000-0002-3545-944X").orElseThrow();
		assertEquals(sefton.getId(), spec.get("author").orElseThrow().path("@id").asText());
	}

	/** A DOI's address, and then a title */
	@Test
	public void citationByAddressAndTitle() throws Exception {
		Entity root = entities().getItems().get(0);
		cite("https://doi.org/10.5281/zenodo.3541888");
		cite("topic model for graph mining");
		assertEquals(List.of("https://doi.org/10.5281/zenodo.3541888", "topic model for graph mining"), searches);
		assertEquals(List.of("https://doi.org/10.5281/zenodo.3541888", "https://doi.org/10.1109/tcyb.2014.2386282"),
				root.getAll("citation").stream().map(v -> v.path("@id").asText()).toList());
		Entity journal = entity("https://portal.issn.org/resource/ISSN/2168-2267").orElseThrow();
		assertEquals("IEEE Transactions on Cybernetics", journal.getLabel());
	}

	/**
	 * A work known only by its DOI, e.g. from another crate: its form fills
	 * it in from the DOI.
	 */
	@Test
	public void fillInFromDoi() throws Exception {
		entities();
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("citation"));
		interact(() -> lookup("#new-citation").queryAs(MenuButton.class).getItems().stream()
				.filter(i -> i.getText().equals("CreativeWork")).findFirst().orElseThrow().fire());
		Entity work = entity("#creativework-1").orElseThrow();
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(work)));
		interact(() -> {
			TextField id = lookup("#entityId").queryAs(TextField.class);
			id.setText("https://doi.org/10.5281/zenodo.3541888");
			id.getOnAction().handle(new ActionEvent());
		});
		Platform.runLater(() -> lookup("#fillFromDoi").queryButton().fire());
		// Looked up at once, by the DOI alone
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#workResults").tryQuery().isPresent()
				&& !lookup("#workResults").queryListView().getItems().isEmpty());
		assertEquals(List.of("10.5281/zenodo.3541888"), searches);
		interact(() -> lookup("#workOk").queryButton().fire());
		assertEquals("RO-Crate Metadata Specification 1.0", work.getLabel());
		assertEquals(List.of("CreativeWork", "ScholarlyArticle"), work.getTypes());
		assertEquals("2019-11-15", work.get("datePublished").orElseThrow().asText());
	}

	/**
	 * A DOI typed or pasted into Citation, as RO-Crate's example has it: the
	 * link is the DOI's address, and the work is looked up and described.
	 */
	@Test
	public void doiTypedIntoCitation() throws Exception {
		Entity root = entities().getItems().get(0);
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("citation"));
		TextField editor = lookup("#value-citation-0").queryAs(javafx.scene.control.ComboBox.class).getEditor();
		interact(() -> editor.setText("doi:10.5281/zenodo.3541888"));
		// Enter; the finder it opens waits for an answer, so not inside interact()
		Platform.runLater(() -> editor.fireEvent(new ActionEvent()));
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#workResults").tryQuery().isPresent()
				&& !lookup("#workResults").queryListView().getItems().isEmpty());
		assertEquals(List.of("10.5281/zenodo.3541888"), searches);
		assertEquals("https://doi.org/10.5281/zenodo.3541888", root.get("citation").orElseThrow().path("@id").asText());
		interact(() -> lookup("#workOk").queryButton().fire());
		Entity spec = entity("https://doi.org/10.5281/zenodo.3541888").orElseThrow();
		assertEquals("RO-Crate Metadata Specification 1.0", spec.getLabel());
		// Confirmed again: no second look-up
		Platform.runLater(() -> editor.fireEvent(new ActionEvent()));
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(1, searches.size());
	}
}
