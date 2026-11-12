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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.lookup.GeoNames;
import no.s11.wpsld.lookup.ROR;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Organisations found in ROR (here a stand-in for it).
 */
public class TestCrateEditorOrganizations extends ApplicationTest {
	static final ROR.Organization OSLO = new ROR.Organization("01xtthb56", "University of Oslo",
			List.of(new ROR.Name("University of Oslo", Optional.of("en"), List.of("ror_display", "label")),
					new ROR.Name("Universitetet i Oslo", Optional.of("no"), List.of("label")),
					new ROR.Name("UiO", Optional.empty(), List.of("acronym"))),
			List.of("education", "funder"), "active", Optional.of("https://www.uio.no/"),
			Optional.of(new GeoNames.Place(3143244, "Oslo", Optional.of("Oslo"), Optional.of("Norway"),
					Optional.empty(), 59.91273, 10.74609)));
	private final List<String> searches = new ArrayList<>();

	@Override
	public void start(Stage stage) throws Exception {
		Path temp = Files.createTempDirectory("wpsld-organizations");
		CrateEditor app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm,
				new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
						Optional.of(temp.resolve("profiles")), List.of()),
				Optional.of(temp.resolve("settings.json")));
		app.setOrganizationSearch(query -> {
			searches.add(query);
			if (query.equals("offline")) {
				throw new java.net.ConnectException("api.ror.org unreachable");
			}
			return query.toLowerCase().contains("oslo") ? List.of(OSLO) : List.of();
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
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#organizationResults").tryQuery().isPresent()
				&& !lookup("#organizationResults").queryListView().getItems().isEmpty());
	}

	private void openFromNewMenu(String field) throws Exception {
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText(field));
		MenuButton create = lookup("#new-" + field).queryAs(MenuButton.class);
		// Shown and waited for, so not inside interact()
		Platform.runLater(() -> create.getItems().stream().filter(i -> ("newRor-" + field).equals(i.getId()))
				.findFirst().orElseThrow().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#organizationQuery").tryQuery().isPresent());
	}

	/**
	 * New ▾ Organization from ROR… on the crate's Publisher, written by its
	 * Norwegian name, in Oslo
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void publisherFromRor() throws Exception {
		Entity root = entities().getItems().get(0);
		openFromNewMenu("publisher");
		interact(() -> lookup("#organizationQuery").queryAs(TextField.class).setText("Oslo"));
		interact(() -> lookup("#organizationSearch").queryButton().fire());
		waitForResults();
		assertEquals(List.of("Oslo"), searches);
		assertEquals("1 organisation found; choose it and OK",
				lookup("#organizationStatus").queryAs(Label.class).getText());
		ComboBox<ROR.Name> name = lookup("#organizationName").queryAs(ComboBox.class);
		assertEquals("University of Oslo", name.getValue().value());
		interact(() -> name.getSelectionModel().select(1));
		interact(() -> lookup("#organizationOk").queryButton().fire());

		Entity uio = entity("https://ror.org/01xtthb56").orElseThrow();
		assertEquals(List.of("Organization"), uio.getTypes());
		assertEquals("Universitetet i Oslo", uio.getLabel());
		assertEquals("https://www.uio.no/", uio.get("url").orElseThrow().asText());
		assertEquals(uio.getId(), root.get("publisher").orElseThrow().path("@id").asText());
		Entity city = entity("http://sws.geonames.org/3143244/").orElseThrow();
		assertEquals("Oslo", city.getLabel());
		assertEquals(city.getId(), uio.get("location").orElseThrow().path("@id").asText());
	}

	/**
	 * An organisation added by hand, then found in ROR by its name: given
	 * its ROR identifier as @id, still the crate's publisher.
	 */
	@Test
	public void findInRor() throws Exception {
		Entity root = entities().getItems().get(0);
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("publisher"));
		interact(() -> lookup("#new-publisher").queryAs(MenuButton.class).getItems().stream()
				.filter(i -> i.getText().equals("Organization")).findFirst().orElseThrow().fire());
		Entity organization = entities().getItems().stream().filter(e -> e.getTypes().contains("Organization"))
				.findFirst().orElseThrow();
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(organization)));
		assertTrue(lookup("#rorNone").tryQuery().isPresent());
		interact(() -> lookup("#header-name").queryAs(TextField.class).setText("University of Oslo"));
		Platform.runLater(() -> lookup("#findOrganization").queryButton().fire());
		// Searched for its name at once
		waitForResults();
		assertEquals(List.of("University of Oslo"), searches);
		interact(() -> lookup("#organizationOk").queryButton().fire());

		assertEquals("https://ror.org/01xtthb56", organization.getId());
		assertEquals("https://ror.org/01xtthb56", root.get("publisher").orElseThrow().path("@id").asText());
		assertEquals("University of Oslo", organization.getLabel());
		assertEquals("01xtthb56", lookup("#rorLink").queryAs(Hyperlink.class).getText());
	}

	/** Offline, the search fails, saying the identifier can still be typed */
	@Test
	public void unreachable() throws Exception {
		entities();
		openFromNewMenu("publisher");
		interact(() -> lookup("#organizationQuery").queryAs(TextField.class).setText("offline"));
		interact(() -> lookup("#organizationSearch").queryButton().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
				() -> lookup("#organizationStatus").queryAs(Label.class).getText().startsWith("Can't search ROR"));
		assertTrue(lookup("#organizationStatus").queryAs(Label.class).getText()
				.endsWith("You can still type the ROR identifier as the organisation's @id."));
		assertTrue(lookup("#organizationOk").queryButton().isDisabled());
		type(KeyCode.ESCAPE);
	}
}
