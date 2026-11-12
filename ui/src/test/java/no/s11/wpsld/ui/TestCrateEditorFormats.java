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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * File formats chosen from the bundled PRONOM formats.
 */
public class TestCrateEditorFormats extends ApplicationTest {
	@Override
	public void start(Stage stage) throws Exception {
		Path temp = Files.createTempDirectory("wpsld-formats");
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

	/** A new file of the crate, with this @id, shown in the form */
	private Entity newFile(String id) {
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText(""));
		interact(() -> lookup("#headerNew-hasPart").queryAs(MenuButton.class).getItems().stream()
				.filter(i -> i.getText().equals("File")).findFirst().orElseThrow().fire());
		Entity file = entities().getItems().get(entities().getItems().size() - 1);
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(file)));
		interact(() -> {
			TextField field = lookup("#entityId").queryAs(TextField.class);
			field.setText(id);
			field.getOnAction().handle(new ActionEvent());
		});
		return entity(id).orElseThrow();
	}

	/** New ▾ Format from PRONOM… on Encoding format; the query it starts with */
	private String openFinder() throws Exception {
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("encodingFormat"));
		MenuButton create = lookup("#new-encodingFormat").queryAs(MenuButton.class);
		// Shown and waited for, so not inside interact()
		Platform.runLater(() -> create.getItems().stream().filter(i -> "newPronom-encodingFormat".equals(i.getId()))
				.findFirst().orElseThrow().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#formatQuery").tryQuery().isPresent());
		return lookup("#formatQuery").queryAs(TextField.class).getText();
	}

	@Test
	public void formatFromExtension() throws Exception {
		entities();
		Entity file = newFile("data.csv");
		assertEquals(".csv", openFinder());
		interact(() -> lookup("#formatOk").queryButton().fire());
		String csv = "https://www.nationalarchives.gov.uk/PRONOM/x-fmt/18";
		assertEquals(List.of(TextNode.valueOf("text/csv"), reference(csv)), file.getAll("encodingFormat"));
		Entity format = entity(csv).orElseThrow();
		assertEquals(List.of("WebPage", "Standard"), format.getTypes());
		assertEquals("Comma Separated Values", format.getLabel());
	}

	/**
	 * A PDF's media type doesn't say which version: the finder starts from
	 * it, and the version chosen is linked beside it.
	 */
	@Test
	public void versionOfMediaType() throws Exception {
		entities();
		Entity file = newFile("paper.pdf");
		interact(() -> file.set("encodingFormat", Optional.of(TextNode.valueOf("application/pdf"))));
		interact(() -> entities().getSelectionModel().clearAndSelect(entities().getItems().indexOf(file)));
		assertEquals("application/pdf", openFinder());
		assertTrue(lookup("#formatResults").queryListView().getItems().size() > 10);
		interact(() -> lookup("#formatQuery").queryAs(TextField.class).setText("pdf 1.7 portable"));
		interact(() -> lookup("#formatOk").queryButton().fire());
		List<JsonNode> formats = file.getAll("encodingFormat");
		assertEquals(TextNode.valueOf("application/pdf"), formats.get(0));
		assertEquals(2, formats.size());
		assertTrue(formats.get(1).path("@id").asText().startsWith("https://www.nationalarchives.gov.uk/PRONOM/"));
		assertTrue(entity(formats.get(1).path("@id").asText()).orElseThrow().getLabel().contains("1.7"));
	}

	private static JsonNode reference(String id) {
		return JsonNodeFactory.instance.objectNode().put("@id", id);
	}
}
