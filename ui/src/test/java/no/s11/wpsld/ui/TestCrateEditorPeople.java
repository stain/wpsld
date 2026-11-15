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
import org.testfx.util.WaitForAsyncUtils;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.lookup.ORCID;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * People found in ORCID (here a stand-in for it, with ORCID's fictional
 * researcher).
 */
public class TestCrateEditorPeople extends UiTest {
	private static final ORCID.Person CARBERRY = new ORCID.Person("0000-0002-1825-0097", Optional.of("Josiah"),
			Optional.of("Carberry"), Optional.empty(), List.of("Josiah Stinkney Carberry"),
			List.of("Brown University", "Wesleyan University"));
	private final List<String> searches = new ArrayList<>();
	private CrateEditor app;

	@Override
	public void start(Stage stage) throws Exception {
		Path temp = Files.createTempDirectory("wpsld-people");
		app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm,
				new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
						Optional.of(temp.resolve("profiles")), List.of()),
				Optional.of(temp.resolve("settings.json")));
		app.setPersonSearch(query -> {
			searches.add(query);
			if (query.equals("offline")) {
				throw new java.net.ConnectException("pub.orcid.org unreachable");
			}
			return query.toLowerCase().contains("carberry") ? List.of(CARBERRY) : List.of();
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

	private void waitForResults() throws Exception {
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#personResults").tryQuery().isPresent()
				&& !lookup("#personResults").queryListView().getItems().isEmpty());
	}

	/** New ▾ Person from ORCID… on the crate's Author */
	@Test
	public void authorFromOrcid() throws Exception {
		Entity root = entities().getItems().get(0);
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("author"));
		MenuButton create = lookup("#new-author").queryAs(MenuButton.class);
		// Shown and waited for, so not inside interact()
		Platform.runLater(() -> create.getItems().stream().filter(i -> "newOrcid-author".equals(i.getId()))
				.findFirst().orElseThrow().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#personQuery").tryQuery().isPresent());
		interact(() -> lookup("#personQuery").queryAs(TextField.class).setText("Josiah Carberry"));
		interact(() -> lookup("#personSearch").queryButton().fire());
		waitForResults();
		assertEquals(List.of("Josiah Carberry"), searches);
		assertEquals("1 person found; choose them and OK", lookup("#personStatus").queryAs(Label.class).getText());
		interact(() -> lookup("#personOk").queryButton().fire());

		Entity josiah = entity("https://orcid.org/0000-0002-1825-0097").orElseThrow();
		assertEquals(List.of("Person"), josiah.getTypes());
		assertEquals("Josiah Carberry", josiah.getLabel());
		assertEquals("Carberry", josiah.get("familyName").orElseThrow().asText());
		assertEquals(josiah.getId(), root.get("author").orElseThrow().path("@id").asText());
	}

	/**
	 * A person added by hand, then found in ORCID by their name: given their
	 * ORCID iD as @id, still the crate's author.
	 */
	@Test
	public void findInOrcid() throws Exception {
		Entity root = entities().getItems().get(0);
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("author"));
		interact(() -> lookup("#new-author").queryAs(MenuButton.class).getItems().stream()
				.filter(i -> i.getText().equals("Person")).findFirst().orElseThrow().fire());
		Entity person = entity("#person-1").orElseThrow();
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(person)));
		assertTrue(lookup("#orcidNone").tryQuery().isPresent());
		interact(() -> lookup("#header-name").queryAs(TextField.class).setText("Josiah Carberry"));
		Platform.runLater(() -> lookup("#findPerson").queryButton().fire());
		// Searched for their name at once
		waitForResults();
		assertEquals(List.of("Josiah Carberry"), searches);
		interact(() -> lookup("#personOk").queryButton().fire());

		assertEquals("https://orcid.org/0000-0002-1825-0097", person.getId());
		assertEquals("https://orcid.org/0000-0002-1825-0097", root.get("author").orElseThrow().path("@id").asText());
		assertEquals("Josiah", person.get("givenName").orElseThrow().asText());
		assertEquals("0000-0002-1825-0097", lookup("#orcidLink").queryAs(javafx.scene.control.Hyperlink.class).getText());
	}

	/** Offline, the search fails, saying the iD can still be typed */
	@Test
	public void unreachable() throws Exception {
		entities();
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("author"));
		MenuButton create = lookup("#new-author").queryAs(MenuButton.class);
		Platform.runLater(() -> create.getItems().stream().filter(i -> "newOrcid-author".equals(i.getId()))
				.findFirst().orElseThrow().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#personQuery").tryQuery().isPresent());
		interact(() -> lookup("#personQuery").queryAs(TextField.class).setText("offline"));
		searches.clear();
		interact(() -> lookup("#personSearch").queryButton().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
				() -> lookup("#personStatus").queryAs(Label.class).getText().startsWith("Can't search ORCID"));
		assertTrue(lookup("#personStatus").queryAs(Label.class).getText()
				.endsWith("You can still type the ORCID iD as the person's @id."));
		assertTrue(lookup("#personOk").queryButton().isDisabled());
		type(KeyCode.ESCAPE);
	}

	/**
	 * An ORCID iD typed in another form as a person's @id is the same as one
	 * imported: renaming offers to merge them
	 */
	@Test
	public void renameToOrcidInAnotherFormMerges() throws Exception {
		entities();
		java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("people");
		java.nio.file.Files.writeString(dir.resolve("ro-crate-metadata.json"), """
				{"@context": "https://w3id.org/ro/crate/1.3/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
				  {"@id": "./", "@type": "Dataset", "name": "People",
				   "author": [{"@id": "https://orcid.org/0000-0002-1825-0097"}, {"@id": "#person3"}]},
				  {"@id": "https://orcid.org/0000-0002-1825-0097", "@type": "Person", "name": "Josiah Carberry"},
				  {"@id": "#person3", "@type": "Person", "name": "J. Carberry"}]}""");
		javafx.stage.Stage stage = (javafx.stage.Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir.resolve("ro-crate-metadata.json"))).get());
		WaitForAsyncUtils.waitForFxEvents();
		Entity typed = entity("#person3").orElseThrow();
		Entity imported = entity("https://orcid.org/0000-0002-1825-0097").orElseThrow();
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(typed)));
		javafx.scene.control.TextField field = lookup("#entityId").queryAs(javafx.scene.control.TextField.class);
		clickOn(field);
		interact(field::selectAll);
		write("0000-0002-1825-0097").type(javafx.scene.input.KeyCode.ENTER);
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(lookup("Merge entities?").tryQuery().isPresent());
		clickOn("Merge");
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals(Optional.of(imported), entity("https://orcid.org/0000-0002-1825-0097"));
		assertTrue(entity("#person3").isEmpty());
	}

	@Test
	public void canonicalIds() {
		assertEquals("https://orcid.org/0000-0002-1825-0097", CrateEditor.canonicalId("0000-0002-1825-0097"));
		assertEquals("https://orcid.org/0000-0002-1825-0097",
				CrateEditor.canonicalId("http://orcid.org/0000-0002-1825-0097/"));
		assertEquals("https://ror.org/027m9bs27", CrateEditor.canonicalId("ror.org/027m9bs27"));
		assertEquals("027m9bs27", CrateEditor.canonicalId("027m9bs27"));
		assertEquals("https://doi.org/10.5281/zenodo.3541888", CrateEditor.canonicalId("doi:10.5281/zenodo.3541888"));
		assertEquals("#person-3", CrateEditor.canonicalId("#person-3"));
		assertEquals("data/counts.csv", CrateEditor.canonicalId("data/counts.csv"));
	}

	private javafx.scene.control.MenuItem menuItem(String id) {
		javafx.scene.control.MenuBar bar = lookup("#menuBar").queryAs(javafx.scene.control.MenuBar.class);
		return bar.getMenus().stream().flatMap(m -> m.getItems().stream()).filter(i -> id.equals(i.getId()))
				.findFirst().orElseThrow();
	}

	/**
	 * Edit > Merge Entities…: two selected, of any types, merged into the
	 * one whose @id is chosen (an ORCID iD before a local one); not more
	 * than two of different types, as likely selected by accident
	 */
	@Test
	public void mergeSelected() throws Exception {
		entities();
		java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("merge");
		java.nio.file.Files.writeString(dir.resolve("ro-crate-metadata.json"), """
				{"@context": "https://w3id.org/ro/crate/1.3/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
				  {"@id": "./", "@type": "Dataset", "name": "People",
				   "author": [{"@id": "#j"}, {"@id": "https://orcid.org/0000-0002-1825-0097"}],
				   "publisher": {"@id": "#uni"}},
				  {"@id": "#j", "@type": "Person", "name": "J. Carberry", "email": "j@example.org"},
				  {"@id": "https://orcid.org/0000-0002-1825-0097", "@type": "Person", "name": "Josiah Carberry"},
				  {"@id": "#uni", "@type": "Organization", "name": "Brown University"}]}""");
		javafx.stage.Stage stage = (javafx.stage.Stage) entities().getScene().getWindow();
		assertTrue(WaitForAsyncUtils.asyncFx(() -> app.open(stage, dir.resolve("ro-crate-metadata.json"))).get());
		WaitForAsyncUtils.waitForFxEvents();
		java.util.function.Consumer<List<String>> select = ids -> interact(() -> {
			entities().getSelectionModel().clearSelection();
			ids.forEach(id -> entities().getSelectionModel().select(entity(id).orElseThrow()));
		});
		// Three of different types: not offered
		select.accept(List.of("#j", "https://orcid.org/0000-0002-1825-0097", "#uni"));
		assertTrue(menuItem("menuMerge").isDisable());
		// Two
		select.accept(List.of("#j", "https://orcid.org/0000-0002-1825-0097"));
		assertFalse(menuItem("menuMerge").isDisable());
		// Not in interact(), which would wait for the dialog's answer
		javafx.application.Platform.runLater(menuItem("menuMerge")::fire);
		WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS,
				() -> lookup("#mergeKeep").tryQuery().isPresent());
		assertEquals("https://orcid.org/0000-0002-1825-0097",
				((Entity) lookup("#mergeKeep").queryAs(javafx.scene.control.ChoiceBox.class).getValue()).getId());
		clickOn("Merge");
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(entity("#j").isEmpty());
		Entity merged = entity("https://orcid.org/0000-0002-1825-0097").orElseThrow();
		assertEquals("j@example.org", merged.get("email").orElseThrow().asText());
		assertEquals(List.of("https://orcid.org/0000-0002-1825-0097"), entities().getItems().get(0).getAll("author")
				.stream().map(v -> v.path("@id").asText()).toList());
	}
}
