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
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import no.s11.wpsld.Settings;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * View > Theme: the look of every window, kept in the settings.
 */
public class TestCrateEditorTheme extends ApplicationTest {
	private Stage stage;
	private Path settingsFile;

	@Override
	public void start(Stage stage) throws Exception {
		this.stage = stage;
		Path temp = Files.createTempDirectory("wpsld-theme");
		settingsFile = temp.resolve("config/settings.json");
		CrateEditor app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm,
				new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
						Optional.of(temp.resolve("profiles")), List.of()),
				Optional.of(settingsFile));
		app.init();
		app.start(stage);
	}

	private RadioMenuItem themeItem(String id) {
		MenuBar bar = lookup("#menuBar").queryAs(MenuBar.class);
		Menu view = bar.getMenus().stream().filter(m -> "menuView".equals(m.getId())).findFirst().orElseThrow();
		Menu theme = (Menu) view.getItems().stream().filter(i -> "menuTheme".equals(i.getId())).findFirst()
				.orElseThrow();
		MenuItem item = theme.getItems().stream().filter(i -> ("menuTheme-" + id).equals(i.getId())).findFirst()
				.orElseThrow();
		return (RadioMenuItem) item;
	}

	private static boolean dark(Scene scene) {
		return scene.getStylesheets().stream().anyMatch(s -> s.endsWith("/dark.css"))
				&& scene.getRoot().getStyleClass().contains("theme-dark");
	}

	@Test
	public void darkTheme() throws Exception {
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#addEntity").tryQuery().isPresent());
		assertTrue(themeItem("light").isSelected());
		assertFalse(dark(stage.getScene()));
		assertTrue(stage.getScene().getStylesheets().stream().anyMatch(s -> s.endsWith("/wpsld.css")));

		// As clicking it
		interact(() -> themeItem("dark").setSelected(true));
		assertTrue(themeItem("dark").isSelected());
		assertTrue(dark(stage.getScene()));
		assertEquals("dark", Settings.load(settingsFile).theme());

		// Dialogs opened after are dark too
		push(KeyCode.SHORTCUT, KeyCode.COMMA);
		DialogPane preferences = lookup(".dialog-pane").queryAs(DialogPane.class);
		assertTrue(dark(preferences.getScene()));
		clickOn("Cancel");
		// Cancelling the preferences keeps the theme
		assertEquals("dark", Settings.load(settingsFile).theme());

		interact(() -> themeItem("light").setSelected(true));
		assertFalse(dark(stage.getScene()));
		assertFalse(stage.getScene().getRoot().getStyleClass().stream().anyMatch(c -> c.startsWith("theme-")
				&& !c.equals("theme-light")));
		assertEquals("light", Settings.load(settingsFile).theme());
	}

	@Test
	public void themeNamesKnown() {
		assertEquals(Theme.DARK, Theme.named("dark"));
		assertEquals(Theme.SYSTEM, Theme.named("system"));
		// From a newer version's settings
		assertEquals(Theme.LIGHT, Theme.named("high-contrast"));
	}
}
