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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import no.s11.wpsld.Settings;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.crate.GeoNames;
import no.s11.wpsld.crate.Places;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Places: found in GeoNames (here a stand-in for it), and shown and moved on
 * a map (without tiles in the tests, see ui/build.gradle).
 */
public class TestCrateEditorPlaces extends ApplicationTest {
	private static final GeoNames.Place CATALINA = new GeoNames.Place(8152662, "Catalina Park",
			Optional.of("New South Wales"), Optional.of("Australia"), Optional.of("park"), -33.7152, 150.30119);
	private Stage stage;
	private Path settingsFile;
	private CrateEditor app;
	private final List<String> searches = new ArrayList<>();

	@Override
	public void start(Stage stage) throws Exception {
		this.stage = stage;
		Path temp = Files.createTempDirectory("wpsld-places");
		settingsFile = temp.resolve("config/settings.json");
		Settings.DEFAULT.withGeonamesUsername("tester").save(settingsFile);
		app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm,
				new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
						Optional.of(temp.resolve("profiles")), List.of()),
				Optional.of(settingsFile));
		app.setPlaceSearch((query, username) -> {
			searches.add(query + " as " + username);
			return query.contains("Catalina") ? List.of(CATALINA) : List.of();
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

	private void select(Entity entity) {
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(entity)));
	}

	private void waitFor(String query) throws Exception {
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup(query).tryQuery().isPresent());
	}

	/**
	 * New ▾ Place from GeoNames… on the crate's Content location: search,
	 * choose, and the place is in the crate with its GeoNames identifier,
	 * name and a Geometry where GeoNames has it.
	 */
	@Test
	public void newPlaceFromGeoNames() throws Exception {
		entities();
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("contentLocation"));
		MenuButton create = lookup("#new-contentLocation").queryAs(MenuButton.class);
		// Shown and waited for, so not inside interact()
		Platform.runLater(() -> create.getItems().stream()
				.filter(i -> "newGeoNames-contentLocation".equals(i.getId())).findFirst().orElseThrow().fire());
		waitFor("#placeQuery");
		interact(() -> lookup("#placeQuery").queryAs(TextField.class).setText("Catalina"));
		interact(() -> lookup("#placeSearch").queryButton().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
				() -> !lookup("#placeResults").queryListView().getItems().isEmpty());
		assertEquals(List.of("Catalina as tester"), searches);
		assertEquals("1 places found; choose one and OK", lookup("#placeStatus").queryAs(Label.class).getText());
		clickOn("#placeOk");

		Entity place = entity("http://sws.geonames.org/8152662/").orElseThrow();
		assertEquals(List.of("Place"), place.getTypes());
		assertEquals("Catalina Park", place.getLabel());
		assertEquals("https://www.geonames.org/8152662/", place.get("url").orElseThrow().asText());
		Entity root = entities().getItems().get(0);
		assertEquals(place.getId(), root.get("contentLocation").orElseThrow().path("@id").asText());
		Entity geometry = entity(place.get("geo").orElseThrow().path("@id").asText()).orElseThrow();
		assertEquals(List.of("Geometry"), geometry.getTypes());
		assertEquals(Places.wkt(-33.7152, 150.30119), geometry.get("asWKT").orElseThrow().asText());
	}

	/**
	 * A place's form has its map: clicking it puts the place there, as a new
	 * Geometry; Find place… gives it its GeoNames identifier.
	 */
	@Test
	public void placeOnMap() throws Exception {
		entities();
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("contentLocation"));
		interact(() -> lookup("#new-contentLocation").queryAs(MenuButton.class).getItems().stream()
				.filter(i -> i.getText().equals("Place")).findFirst().orElseThrow().fire());
		Entity place = entity("#place-1").orElseThrow();
		select(place);
		Label coordinates = lookup("#locationCoordinates").queryAs(Label.class);
		assertTrue(coordinates.getText(), coordinates.getText().startsWith("Not set"));
		// The map starts on the whole world, centred on 0, 0
		clickOn("#mapView");
		Entity geometry = entity(place.get("geo").orElseThrow().path("@id").asText()).orElseThrow();
		Places.Area area = Places.of(geometry, this::entity).orElseThrow();
		assertEquals(0, area.latitude(), 1);
		assertEquals(0, area.longitude(), 1);
		assertTrue(lookup("#locationCoordinates").queryAs(Label.class).getText().startsWith("Latitude"));
		assertTrue(lookup("#mapMarker").query().isVisible());

		// Named, then looked up by that name
		interact(() -> lookup("#header-name").queryAs(TextField.class).setText("Catalina Park"));
		Platform.runLater(() -> lookup("#findPlace").queryButton().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
				() -> lookup("#placeResults").tryQuery().isPresent()
						&& !lookup("#placeResults").queryListView().getItems().isEmpty());
		assertEquals(List.of("Catalina Park as tester"), searches);
		clickOn("#placeOk");
		assertEquals("http://sws.geonames.org/8152662/", place.getId());
		// The same geometry, moved
		assertEquals(Places.wkt(-33.7152, 150.30119), geometry.get("asWKT").orElseThrow().asText());
		assertEquals(geometry.getId(), place.get("geo").orElseThrow().path("@id").asText());
	}

	/**
	 * Edit > Preferences > Places keeps the GeoNames username.
	 */
	@Test
	public void geonamesUsernameInPreferences() throws Exception {
		entities();
		Platform.runLater(() -> app.showPreferences(stage));
		waitFor("#keptProfiles");
		// The dialog's tabs, not those of the window's preview
		Tab places = lookup(".tab-pane").queryAllAs(TabPane.class).stream().flatMap(t -> t.getTabs().stream())
				.filter(t -> "placesTab".equals(t.getId())).findFirst().orElseThrow();
		interact(() -> places.getTabPane().getSelectionModel().select(places));
		TextField username = lookup("#geonamesUsername").queryAs(TextField.class);
		assertEquals("tester", username.getText());
		interact(() -> username.setText("someone"));
		clickOn("OK");
		assertEquals("someone", Settings.load(settingsFile).geonamesUsername());
	}
}
