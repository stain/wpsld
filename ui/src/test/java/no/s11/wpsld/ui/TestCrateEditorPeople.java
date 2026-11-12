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
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.crate.Orcid;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * People found in ORCID (here a stand-in for it, with ORCID's fictional
 * researcher).
 */
public class TestCrateEditorPeople extends ApplicationTest {
	private static final Orcid.Person CARBERRY = new Orcid.Person("0000-0002-1825-0097", Optional.of("Josiah"),
			Optional.of("Carberry"), Optional.empty(), List.of("Josiah Stinkney Carberry"),
			List.of("Brown University", "Wesleyan University"));
	private final List<String> searches = new ArrayList<>();

	@Override
	public void start(Stage stage) throws Exception {
		Path temp = Files.createTempDirectory("wpsld-people");
		CrateEditor app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm,
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
}
