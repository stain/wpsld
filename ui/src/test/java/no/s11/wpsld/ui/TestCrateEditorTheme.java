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
import javafx.scene.control.Label;
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
 * View > Style and Colours: the look of every window, kept in the settings.
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

	/** View > Style or View > Colours: the item of this choice */
	private RadioMenuItem item(String menu, String id) {
		MenuBar bar = lookup("#menuBar").queryAs(MenuBar.class);
		Menu view = bar.getMenus().stream().filter(m -> "menuView".equals(m.getId())).findFirst().orElseThrow();
		Menu submenu = (Menu) view.getItems().stream().filter(i -> ("menu" + menu).equals(i.getId())).findFirst()
				.orElseThrow();
		MenuItem item = submenu.getItems().stream().filter(i -> ("menu" + menu + "-" + id).equals(i.getId()))
				.findFirst().orElseThrow();
		return (RadioMenuItem) item;
	}

	/** As clicking the item */
	private void choose(String menu, String id) {
		interact(() -> item(menu, id).setSelected(true));
	}

	private static boolean has(Scene scene, String stylesheet) {
		return scene.getStylesheets().stream().anyMatch(s -> s.endsWith("/" + stylesheet));
	}

	private static boolean dark(Scene scene) {
		return scene.getRoot().getStyleClass().contains("colours-dark");
	}

	private void editorShown() throws Exception {
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#addEntity").tryQuery().isPresent());
	}

	@Test
	public void darkColours() throws Exception {
		editorShown();
		Scene scene = stage.getScene();
		assertTrue(item("Colours", "light").isSelected());
		assertTrue(item("Style", "classic").isSelected());
		assertFalse(dark(scene));
		assertTrue(has(scene, "wpsld.css"));
		assertFalse(has(scene, "dark.css"));

		choose("Colours", "dark");
		assertTrue(dark(scene));
		assertTrue(has(scene, "dark.css"));
		assertEquals("dark", Settings.load(settingsFile).colours());

		// Dialogs opened after are dark too
		push(KeyCode.SHORTCUT, KeyCode.COMMA);
		DialogPane preferences = lookup(".dialog-pane").queryAs(DialogPane.class);
		assertTrue(dark(preferences.getScene()));
		assertTrue(has(preferences.getScene(), "dark.css"));
		clickOn("Cancel");
		// Cancelling the preferences keeps the colours
		assertEquals("dark", Settings.load(settingsFile).colours());

		choose("Colours", "light");
		assertFalse(dark(scene));
		assertFalse(has(scene, "dark.css"));
		assertEquals("light", Settings.load(settingsFile).colours());
	}

	/**
	 * Windows 11, with its own dark colours on top.
	 */
	@Test
	public void windows11Style() throws Exception {
		editorShown();
		Scene scene = stage.getScene();
		choose("Style", "win11");
		assertTrue(has(scene, "win11.css"));
		assertTrue(scene.getRoot().getStyleClass().contains("style-win11"));
		assertEquals("win11", Settings.load(settingsFile).style());

		choose("Colours", "dark");
		assertEquals(List.of("wpsld.css", "win11.css", "win11-dark.css"), scene.getStylesheets().stream()
				.map(s -> s.substring(s.lastIndexOf('/') + 1)).toList());

		choose("Style", "classic");
		assertEquals(List.of("wpsld.css", "dark.css"), scene.getStylesheets().stream()
				.map(s -> s.substring(s.lastIndexOf('/') + 1)).toList());
		assertFalse(scene.getRoot().getStyleClass().contains("style-win11"));
	}

	/**
	 * The OS/2 Warp style: the form's groups as the tabs of a notebook, with
	 * pages.
	 */
	@Test
	public void warpStyleNotebook() throws Exception {
		editorShown();
		choose("Style", "warp");
		assertTrue(lookup("#notebook").tryQuery().isPresent());
		assertEquals(List.of("Required", "Dataset", "CreativeWork", "Thing"),
				lookup(".notebook-tab").queryAllAs(Label.class).stream().map(Label::getText).toList());
		assertTrue(lookup("#propertySearch").tryQuery().isEmpty() || !lookup("#propertySearch").query().isVisible());
		// CreativeWork's properties on pages
		clickOn(lookup(".notebook-tab").queryAllAs(Label.class).stream().filter(l -> l.getText().equals("CreativeWork"))
				.findFirst().orElseThrow());
		assertTrue(lookup(".notebook-status").queryAs(Label.class).getText().matches("Page 1 of \\d+"));
		// Back to a list
		choose("Style", "win11");
		assertTrue(lookup("#notebook").tryQuery().isEmpty());
		assertTrue(lookup("#label-name").tryQuery().isPresent());
	}

	@Test
	public void choicesNamed() {
		assertEquals(Theme.Style.WIN11, Theme.named(Theme.Style.class, "win11"));
		assertEquals(Theme.Colours.SYSTEM, Theme.named(Theme.Colours.class, "system"));
		// From a newer version's settings
		assertEquals(Theme.Style.CLASSIC, Theme.named(Theme.Style.class, "aqua"));
		assertEquals(Theme.Colours.LIGHT, Theme.named(Theme.Colours.class, "high-contrast"));
	}
}
