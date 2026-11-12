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

import com.fasterxml.jackson.databind.node.TextNode;

import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.lookup.SPDXLicenses;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Licences chosen from the bundled SPDX License List.
 */
public class TestCrateEditorLicenses extends ApplicationTest {
	@Override
	public void start(Stage stage) throws Exception {
		Path temp = Files.createTempDirectory("wpsld-licenses");
		CrateEditor app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm,
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

	private Optional<Entity> entity(String id) {
		return entities().getItems().stream().filter(e -> e.getId().equals(id)).findFirst();
	}

	/** New ▾ Licence from SPDX… on the License field, choosing the first match of the query */
	private void chooseLicence(String query) throws Exception {
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("license"));
		MenuButton create = lookup("#new-license").queryAs(MenuButton.class);
		// Shown and waited for, so not inside interact()
		Platform.runLater(() -> create.getItems().stream().filter(i -> "newSpdx-license".equals(i.getId()))
				.findFirst().orElseThrow().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#licenseQuery").tryQuery().isPresent());
		interact(() -> lookup("#licenseQuery").queryAs(TextField.class).setText(query));
		interact(() -> lookup("#licenseOk").queryButton().fire());
	}

	@Test
	public void licenceFromSpdx() throws Exception {
		Entity root = entities().getItems().get(0);
		chooseLicence("cc-by-4.0");

		Entity licence = entity("http://spdx.org/licenses/CC-BY-4.0").orElseThrow();
		assertEquals(List.of("CreativeWork"), licence.getTypes());
		assertEquals("Creative Commons Attribution 4.0 International", licence.getLabel());
		assertEquals(licence.getId(), root.get("license").orElseThrow().path("@id").asText());
		Entity identifier = entity("#spdx-CC-BY-4.0").orElseThrow();
		assertEquals(List.of("PropertyValue"), identifier.getTypes());
		assertEquals("CC-BY-4.0", identifier.get("value").orElseThrow().asText());
		assertEquals(SPDXLicenses.PROPERTY_ID, identifier.get("propertyID").orElseThrow().asText());
		assertEquals(identifier.getId(), licence.get("identifier").orElseThrow().path("@id").asText());

		// A file with the same licence links the same entities
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("hasPart"));
		interact(() -> lookup("#headerNew-hasPart").queryAs(MenuButton.class).getItems().stream()
				.filter(i -> i.getText().equals("File")).findFirst().orElseThrow().fire());
		Entity file = entities().getItems().stream().filter(e -> e.getTypes().contains("File")).findFirst()
				.orElseThrow();
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(file)));
		int before = entities().getItems().size();
		chooseLicence("CC-BY-4.0");
		assertEquals(licence.getId(), file.get("license").orElseThrow().path("@id").asText());
		assertEquals(before, entities().getItems().size());
	}

	/**
	 * Deprecated identifiers are hidden unless asked for; the list filters
	 * while typing.
	 */
	@Test
	public void deprecatedHidden() throws Exception {
		entities();
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("license"));
		MenuButton create = lookup("#new-license").queryAs(MenuButton.class);
		Platform.runLater(() -> create.getItems().stream().filter(i -> "newSpdx-license".equals(i.getId()))
				.findFirst().orElseThrow().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#licenseQuery").tryQuery().isPresent());
		ListView<SPDXLicenses.License> results = lookup("#licenseResults").queryListView();
		interact(() -> lookup("#licenseQuery").queryAs(TextField.class).setText("gpl-2.0"));
		assertEquals("GPL-2.0-only", results.getItems().get(0).id());
		assertTrue(results.getItems().stream().noneMatch(SPDXLicenses.License::deprecated));
		interact(() -> lookup("#licenseDeprecated").queryAs(CheckBox.class).setSelected(true));
		assertEquals("GPL-2.0", results.getItems().get(0).id());
		interact(() -> lookup("#licenseQuery").queryAs(TextField.class).setText("no such licence"));
		assertTrue(results.getItems().isEmpty());
		assertTrue(lookup("#licenseOk").queryButton().isDisabled());
	}

	/**
	 * Crate > Repair Crate turns a licence copied from the browser into the
	 * SPDX licence, described.
	 */
	@Test
	public void repairCopiedAddress() throws Exception {
		Entity root = entities().getItems().get(0);
		interact(() -> root.set("license",
				Optional.of(TextNode.valueOf("https://spdx.org/licenses/Apache-2.0.html"))));
		MenuBar bar = lookup("#menuBar").queryAs(MenuBar.class);
		MenuItem repair = bar.getMenus().stream().flatMap(m -> m.getItems().stream())
				.filter(i -> "menuRepair".equals(i.getId())).findFirst().orElseThrow();
		WaitForAsyncUtils.asyncFx(repair::fire);
		WaitForAsyncUtils.waitForFxEvents();
		String report = lookup(".dialog-pane").queryAs(DialogPane.class).getContentText();
		assertTrue(report, report.contains("Licences of the SPDX License List"));
		assertTrue(report, report.contains("https://spdx.org/licenses/Apache-2.0.html"));
		clickOn("OK");
		assertEquals("http://spdx.org/licenses/Apache-2.0", root.get("license").orElseThrow().path("@id").asText());
		Entity apache = entity("http://spdx.org/licenses/Apache-2.0").orElseThrow();
		assertEquals("Apache License 2.0", apache.getLabel());
		assertTrue(entity("#spdx-Apache-2.0").isPresent());
	}
}
