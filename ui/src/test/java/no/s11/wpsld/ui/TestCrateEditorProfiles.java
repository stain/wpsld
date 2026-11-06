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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import no.s11.wpsld.Settings;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.FormFields;
import no.s11.wpsld.masp.Profile;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.masp.ProfileRegistry;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Loading profiles from a URL or file, kept for next time.
 */
public class TestCrateEditorProfiles extends ApplicationTest {
	private static final String TEST_PROFILE = "https://example.com/test-profile/";

	private Path temp;
	private Path store;
	private Path settingsFile;
	private CrateEditor app;

	@Override
	public void start(Stage stage) throws Exception {
		temp = Files.createTempDirectory("wpsld-profiles");
		store = temp.resolve("profiles");
		settingsFile = temp.resolve("config/settings.json");
		app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm, cache(), Optional.of(settingsFile));
		app.init();
		app.start(stage);
	}

	private ProfileCache cache() {
		return new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
				Optional.of(store), List.of(registry));
	}

	/** A registry listing one profile, in a file */
	private final ProfileRegistry registry = new ProfileRegistry() {
		@Override
		public String name() {
			return "Test registry";
		}

		@Override
		public List<Entry> entries() {
			return List.of(new Entry(List.of(TEST_PROFILE), "Registered Profile", Optional.of("0.1.0"), Optional.empty(),
					testProfile("Registered Profile").toUri(), name()));
		}
	};

	/**
	 * A copy of the bundled core profile under another name and identifier.
	 */
	private Path testProfile(String name) {
		try (InputStream in = ProfileCache.class.getResourceAsStream("/masp-profiles/ro-crate.json")) {
			String json = new String(in.readAllBytes(), StandardCharsets.UTF_8)
					.replace("\"RO-Crate Machine Actionable Profile\"", "\"" + name + "\"")
					.replace("https://language-research-technology.github.io/ro-crate-masp/profiles/ro-crate/profile-crate/",
							TEST_PROFILE);
			Path file = temp.resolve(name.replace(' ', '-') + ".json");
			Files.writeString(file, json);
			return file;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private ComboBox<FormFields> profiles() {
		try {
			WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#loadProfile").tryQuery().isPresent());
		} catch (Exception e) {
			throw new AssertionError("Editor did not appear", e);
		}
		return lookup("#profile").queryComboBox();
	}

	private List<String> names(ComboBox<FormFields> profiles) {
		return profiles.getItems().stream().map(FormFields::getName).toList();
	}

	private Profile load(URI location) throws Exception {
		Profile profile = WaitForAsyncUtils.asyncFx(() -> app.loadProfile(location)).get(10, TimeUnit.SECONDS)
				.get(10, TimeUnit.SECONDS);
		WaitForAsyncUtils.waitForFxEvents();
		return profile;
	}

	@Test
	public void loadedProfileChosenAndKept() throws Exception {
		ComboBox<FormFields> profiles = profiles();
		assertEquals(List.of("RO-Crate Machine Actionable Profile 0.1.0", "schema.org (no profile)"), names(profiles));

		assertFalse(load(testProfile("Test Profile").toUri()).hasErrors());
		assertEquals(List.of("RO-Crate Machine Actionable Profile 0.1.0", "Test Profile 0.1.0", "schema.org (no profile)"),
				names(profiles));
		assertEquals("Test Profile 0.1.0", profiles.getValue().getName());
		assertTrue(Files.isRegularFile(store.resolve(ProfileCache.STORE_INDEX)));

		// A newer copy replaces it
		assertFalse(load(testProfile("Test Profile Renamed").toUri()).hasErrors());
		assertEquals(List.of("RO-Crate Machine Actionable Profile 0.1.0", "Test Profile Renamed 0.1.0",
				"schema.org (no profile)"), names(profiles));

		// Next time
		assertEquals(List.of("RO-Crate Machine Actionable Profile 0.1.0", "Test Profile Renamed 0.1.0"),
				cache().available().stream().map(p -> p.name().orElseThrow() + " " + p.version().orElseThrow()).toList());
	}

	@Test
	public void failureShown() throws Exception {
		ComboBox<FormFields> profiles = profiles();
		Path broken = Files.writeString(temp.resolve("broken.json"), "{ not json");
		assertTrue(load(broken.toUri()).hasErrors());
		assertEquals("Can't load the profile", lookup("#profileStatus").queryAs(Label.class).getText());
		assertEquals(2, profiles.getItems().size());
		assertEquals("RO-Crate Machine Actionable Profile 0.1.0", profiles.getValue().getName());
		assertFalse(lookup("#loadProfile").query().isDisabled());
		assertFalse(Files.exists(store));
	}

	/**
	 * Through the dialog, with a file path as typed.
	 */
	@Test
	public void loadFromDialog() throws Exception {
		ComboBox<FormFields> profiles = profiles();
		Path file = testProfile("Dialog Profile");
		clickOn("#loadProfile");
		clickOn("#profileLocation").write(file.toString());
		clickOn("Load");
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> profiles.getItems().size() == 3);
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals("Dialog Profile 0.1.0", profiles.getValue().getName());
	}

	/**
	 * Chosen from those the registries list.
	 */
	@Test
	public void loadFromRegistry() throws Exception {
		ComboBox<FormFields> profiles = profiles();
		clickOn("#loadProfile");
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
				() -> !lookup("#registryProfiles").queryListView().getItems().isEmpty());
		doubleClickOn("Registered Profile 0.1.0 \u2014 Test registry");
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> profiles.getItems().size() == 3);
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals("Registered Profile 0.1.0", profiles.getValue().getName());
	}

	/**
	 * Edit > Preferences: remove a kept profile, turn off a registry.
	 */
	@Test
	public void preferences() throws Exception {
		ComboBox<FormFields> profiles = profiles();
		assertFalse(load(testProfile("Test Profile").toUri()).hasErrors());
		assertEquals("Test Profile 0.1.0", profiles.getValue().getName());

		// Edit > Preferences…, with its shortcut, as moving the mouse over the
		// menu bar may open another menu
		push(KeyCode.SHORTCUT, KeyCode.COMMA);
		clickOn("Kept profiles");
		ListView<ProfileCache.Kept> kept = lookup("#keptProfiles").queryListView();
		assertEquals(1, kept.getItems().size());
		clickOn(lookup("#keptProfiles .list-cell").nth(0).queryAs(javafx.scene.Node.class));
		clickOn("#removeKept");
		assertEquals(List.of(), kept.getItems());
		// No longer offered, and the profile chosen before is back
		assertEquals(List.of("RO-Crate Machine Actionable Profile 0.1.0", "schema.org (no profile)"), names(profiles));
		assertEquals("RO-Crate Machine Actionable Profile 0.1.0", profiles.getValue().getName());

		clickOn("Profile registries");
		CheckBox registry = lookup("#registry0").queryAs(CheckBox.class);
		assertEquals("Test registry", registry.getText());
		assertTrue(registry.isSelected());
		clickOn(registry);
		clickOn("OK");
		assertEquals(Set.of("Test registry"), Settings.load(settingsFile).disabledRegistries());
		assertEquals(List.of(), cache().kept());
	}

	/**
	 * A profile chosen is written in the root's conformsTo, so undone as a
	 * change, and the profile follows the crate.
	 */
	@Test
	public void profileInConformsTo() throws Exception {
		ComboBox<FormFields> profiles = profiles();
		assertFalse(load(testProfile("Test Profile").toUri()).hasErrors());
		ListView<Entity> entities = lookup("#entities").queryListView();
		Entity root = entities.getItems().get(0);
		assertEquals(List.of(TEST_PROFILE), root.getAll("conformsTo").stream().map(v -> v.path("@id").asText()).toList());

		// schema.org: not a profile to declare
		interact(() -> profiles.getSelectionModel().selectLast());
		assertEquals(List.of(), entities.getItems().get(0).getAll("conformsTo"));

		clickOn(lookup("#entities .list-cell").nth(0).queryAs(javafx.scene.Node.class));
		push(KeyCode.SHORTCUT, KeyCode.Z);
		assertEquals("Test Profile 0.1.0", profiles.getValue().getName());
		push(KeyCode.SHORTCUT, KeyCode.Z);
		assertEquals(List.of(), entities.getItems().get(0).getAll("conformsTo"));
		assertEquals("RO-Crate Machine Actionable Profile 0.1.0", profiles.getValue().getName());
		push(KeyCode.SHORTCUT, KeyCode.SHIFT, KeyCode.Z);
		assertEquals("Test Profile 0.1.0", profiles.getValue().getName());
	}

	@Test
	public void profileLocations() {
		assertEquals(URI.create("https://w3id.org/ldac/profile"),
				CrateEditor.profileLocation("https://w3id.org/ldac/profile").orElseThrow());
		assertEquals("file", CrateEditor.profileLocation("/tmp/profile.json").orElseThrow().getScheme());
		assertEquals(Optional.empty(), CrateEditor.profileLocation("https://exa mple.com/"));
	}
}
