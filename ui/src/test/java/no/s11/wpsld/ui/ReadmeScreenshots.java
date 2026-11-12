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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

import org.junit.Assume;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.application.Platform;
import no.s11.wpsld.Settings;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Dois;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.crate.GeoNames;
import no.s11.wpsld.crate.Orcid;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * The screenshots in README.md, of the example crate in
 * src/test/resources/example-crate in several styles. Only taken when asked
 * for, into the folder named by the environment variable SCREENSHOTS:
 *
 * <pre>
 * SCREENSHOTS=$PWD/docs/screenshots ./gradlew :ui:test --tests '*ReadmeScreenshots' --rerun
 * </pre>
 *
 * They are taken headless (see ui/build.gradle), so with JavaFX's own text
 * rendering rather than the desktop's.
 */
public class ReadmeScreenshots extends ApplicationTest {
	private Stage stage;
	private CrateEditor app;

	@Override
	public void start(Stage stage) throws Exception {
		this.stage = stage;
		Path temp = Files.createTempDirectory("wpsld-screenshots");
		String help = System.getenv("HELP_SCREENSHOTS");
		if (help != null && !help.isBlank()) {
			// The help's maps with OpenStreetMap's tiles (the tests have none),
			// and a GeoNames username, as the user would have set
			System.setProperty("wpsld.mapTiles", "osm");
			Settings.DEFAULT.withGeonamesUsername("example").save(temp.resolve("settings.json"));
		}
		app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm,
				new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
						Optional.of(temp.resolve("profiles")), List.of()),
				Optional.of(temp.resolve("settings.json")));
		// GeoNames' Catalina Park, as in the RO-Crate specification's example,
		// without asking GeoNames
		app.setPlaceSearch((query, username) -> List.of(new GeoNames.Place(8152662, "Catalina Park",
				Optional.of("New South Wales"), Optional.of("Australia"), Optional.of("park"), -33.7152, 150.301195)));
		// ORCID's fictional researcher, as ORCID has him, without asking ORCID
		app.setPersonSearch(query -> List.of(new Orcid.Person("0000-0002-1825-0097", Optional.of("Josiah"),
				Optional.of("Carberry"), Optional.empty(),
				List.of("Josiah Stinkney Carberry", "J. Carberry", "J. S. Carberry"),
				List.of("Brown University", "Wesleyan University"))));
		// The RO-Crate 1.0 specification as DataCite has it, without asking it
		app.setWorkSearch(query -> List.of(new Dois.Work("10.5281/zenodo.3541888", "ScholarlyArticle",
				"RO-Crate Metadata Specification 1.0",
				List.of(new Dois.Author("Peter Sefton", Optional.of("Peter"), Optional.of("Sefton"),
						Optional.of("0000-0002-3545-944X")),
						new Dois.Author("Eoghan Ó Carragáin", Optional.of("Eoghan Ó"), Optional.of("Carragáin"),
								Optional.of("0000-0001-8131-2150")),
						new Dois.Author("Stian Soiland-Reyes", Optional.of("Stian"), Optional.of("Soiland-Reyes"),
								Optional.of("0000-0001-9842-9718"))),
				Optional.of("2019-11-15"), Optional.empty(), List.of(), Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.of("Zenodo"),
				Optional.of("This document specifies a method, known as RO-Crate (Research Object Crate), of "
						+ "organizing file-based data with associated metadata, using linked data principles, in "
						+ "both human and machine readable formats."),
				"DataCite")));
		app.init();
		app.start(stage);
	}

	@Test
	public void screenshots() throws Exception {
		String folder = System.getenv("SCREENSHOTS");
		Assume.assumeTrue("Set SCREENSHOTS to the folder for them", folder != null && !folder.isBlank());
		Path out = Files.createDirectories(Path.of(folder));
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#addEntity").tryQuery().isPresent());
		interact(() -> {
			stage.setWidth(1200);
			stage.setHeight(720);
		});
		Path crate = copyExample();
		WaitForAsyncUtils.asyncFx(() -> app.open(stage, crate)).get();
		WaitForAsyncUtils.waitForFxEvents();

		look("classic", "light");
		select("#alice");
		png(out.resolve("classic-person.png"));

		look("win11", "light");
		select("./");
		png(out.resolve("windows11-dataset.png"));

		look("macos", "dark");
		select("data/counts.csv");
		png(out.resolve("macos-dark-file.png"));

		look("warp", "light");
		select("#spring-survey");
		png(out.resolve("warp-event.png"));
	}

	/** Screenshots for the help walkthrough, with the same example values. */
	@Test
	public void helpScreenshots() throws Exception {
		String folder = System.getenv("HELP_SCREENSHOTS");
		Assume.assumeTrue("Set HELP_SCREENSHOTS to the folder for them", folder != null && !folder.isBlank());
		Path out = Files.createDirectories(Path.of(folder));
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#addEntity").tryQuery().isPresent());
		interact(() -> {
			stage.setWidth(1400);
			stage.setHeight(900);
		});
		look("classic", "light");
		interact(() -> {
			lookup("#header-name").queryAs(TextField.class).setText("Example dataset");
			lookup("#header-description").queryAs(TextArea.class).setText("A sample file described with wpsld.");
		});
		png(out.resolve("editor-overview.png"));

		interact(() -> {
			MenuButton parts = lookup("#headerNew-hasPart").queryAs(MenuButton.class);
			parts.getItems().stream().filter(i -> i.getText().equals("File")).findFirst().orElseThrow().fire();
		});
		select("#file-1");
		interact(() -> {
			TextField id = lookup("#entityId").queryAs(TextField.class);
			id.setText("data.csv");
			id.getOnAction().handle(new javafx.event.ActionEvent());
			lookup("#header-name").queryAs(TextField.class).setText("Sample data");
		});
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("encoding format"));
		// Text or a link to a format's description, so a drop-down
		interact(() -> lookup("#value-encodingFormat-0").queryAs(ComboBox.class).getEditor().setText("text/csv"));
		png(out.resolve("file-form.png"), lookup(n -> n instanceof EntityEditor).query());

		select("./");
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("author"));
		interact(() -> {
			MenuButton author = lookup("#new-author").queryAs(MenuButton.class);
			author.getItems().stream().filter(i -> i.getText().equals("Person")).findFirst().orElseThrow().fire();
		});
		select("#person-1");
		interact(() -> lookup("#header-name").queryAs(TextField.class).setText("Example author"));
		select("./");
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("author"));
		org.junit.Assert.assertEquals("./", lookup("#entityId").queryAs(TextField.class).getText());
		org.junit.Assert.assertTrue(lookup("#value-author-0").queryAs(ComboBox.class).getEditor().getText()
				.startsWith("#person-"));
		png(out.resolve("author-link.png"), lookup(n -> n instanceof EntityEditor).query());

		clickOn("#save");
		png(out.resolve("save-warning.png"), lookup(".dialog-pane").query());
		clickOn("Cancel");
		interact(() -> {
			lookup("#propertySearch").queryAs(TextField.class).clear();
			lookup("#value-datePublished-0").queryAs(DatePicker.class).getEditor().setText("2025-01-31");
		});
		// New > Licence from SPDX..., which waits for an answer, so not inside interact()
		MenuButton license = lookup("#new-license").queryAs(MenuButton.class);
		Platform.runLater(() -> license.getItems().stream().filter(i -> "newSpdx-license".equals(i.getId()))
				.findFirst().orElseThrow().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#licenseQuery").tryQuery().isPresent());
		interact(() -> lookup("#licenseQuery").queryAs(TextField.class).setText("cc-by-4.0"));
		interact(() -> lookup("#licenseOk").queryButton().fire());
		select("http://spdx.org/licenses/CC-BY-4.0");
		png(out.resolve("license-entity.png"), lookup(n -> n instanceof EntityEditor).query());
		select("./");
		png(out.resolve("dataset-form.png"), lookup(n -> n instanceof EntityEditor).query());

		interact(() -> {
			MenuButton parts = lookup("#headerNew-hasPart").queryAs(MenuButton.class);
			parts.getItems().stream().filter(i -> i.getText().equals("File")).findFirst().orElseThrow().fire();
		});
		select("#file-1");
		interact(() -> {
			TextField id = lookup("#entityId").queryAs(TextField.class);
			id.setText("photo.jpg");
			id.getOnAction().handle(new javafx.event.ActionEvent());
			lookup("#header-name").queryAs(TextField.class).setText("Photograph of Margaret Hamilton");
		});
		clickOn("#addType");
		clickOn("#typeSearch").write("Photograph").type(KeyCode.ENTER);
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("about"));
		interact(() -> {
			MenuButton about = lookup("#new-about").queryAs(MenuButton.class);
			about.getItems().stream().filter(i -> i.getText().equals("Thing")).findFirst().orElseThrow().fire();
		});
		select("#thing-1");
		clickOn("#addType");
		clickOn("#typeSearch").write("Person").type(KeyCode.ENTER);
		interact(() -> {
			lookup("#header-name").queryAs(TextField.class).setText("Margaret Hamilton");
			TextField id = lookup("#entityId").queryAs(TextField.class);
			id.setText("https://www.wikidata.org/entity/Q11628");
			id.getOnAction().handle(new javafx.event.ActionEvent());
		});
		png(out.resolve("wikidata-person.png"), lookup(n -> n instanceof EntityEditor).query());
		select("photo.jpg");
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("about"));
		org.junit.Assert.assertEquals("https://www.wikidata.org/entity/Q11628",
				lookup("#value-about-0").queryAs(ComboBox.class).getEditor().getText());
		png(out.resolve("photograph-about.png"), lookup(n -> n instanceof EntityEditor).query());
	}

	/** The example crate, copied to a new folder with its files */
	private Path copyExample() throws Exception {
		Path source = Path.of(getClass().getResource("/example-crate/ro-crate-metadata.json").toURI()).getParent();
		Path target = Files.createTempDirectory("example-crate");
		try (Stream<Path> files = Files.walk(source)) {
			for (Path file : files.toList()) {
				Path copy = target.resolve(source.relativize(file).toString());
				if (Files.isDirectory(file)) {
					Files.createDirectories(copy);
				} else {
					Files.copy(file, copy);
				}
			}
		}
		return target;
	}

	/**
	 * Screenshots of places for the help: finding one in GeoNames, and its
	 * map. They need the internet, for OpenStreetMap's map tiles.
	 */
	@Test
	public void placeScreenshots() throws Exception {
		String folder = System.getenv("HELP_SCREENSHOTS");
		Assume.assumeTrue("Set HELP_SCREENSHOTS to the folder for them", folder != null && !folder.isBlank());
		Path out = Files.createDirectories(Path.of(folder));
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#addEntity").tryQuery().isPresent());
		interact(() -> {
			stage.setWidth(1400);
			stage.setHeight(900);
		});
		look("classic", "light");
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("contentLocation"));
		MenuButton create = lookup("#new-contentLocation").queryAs(MenuButton.class);
		// The dialog waits for an answer, so not inside interact()
		Platform.runLater(() -> create.getItems().stream()
				.filter(i -> "newGeoNames-contentLocation".equals(i.getId())).findFirst().orElseThrow().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#placeQuery").tryQuery().isPresent());
		interact(() -> lookup("#placeQuery").queryAs(TextField.class).setText("Catalina Park"));
		interact(() -> lookup("#placeSearch").queryButton().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
				() -> !lookup("#placeResults").queryListView().getItems().isEmpty());
		waitForTiles();
		png(out.resolve("place-finder.png"), lookup(".dialog-pane").match(n -> n.lookup("#placeQuery") != null).query());
		interact(() -> lookup("#placeOk").queryButton().fire());
		select("http://sws.geonames.org/8152662/");
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText(""));
		waitForTiles();
		png(out.resolve("place-location.png"), lookup(n -> n instanceof EntityEditor).query());
	}

	/**
	 * A screenshot of choosing a licence from the SPDX License List, for the
	 * help.
	 */
	@Test
	public void licenceScreenshots() throws Exception {
		String folder = System.getenv("HELP_SCREENSHOTS");
		Assume.assumeTrue("Set HELP_SCREENSHOTS to the folder for them", folder != null && !folder.isBlank());
		Path out = Files.createDirectories(Path.of(folder));
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#addEntity").tryQuery().isPresent());
		look("classic", "light");
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("license"));
		MenuButton create = lookup("#new-license").queryAs(MenuButton.class);
		// The dialog waits for an answer, so not inside interact()
		Platform.runLater(() -> create.getItems().stream().filter(i -> "newSpdx-license".equals(i.getId()))
				.findFirst().orElseThrow().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#licenseQuery").tryQuery().isPresent());
		interact(() -> lookup("#licenseQuery").queryAs(TextField.class).setText("creative commons attribution 4.0"));
		png(out.resolve("licence-finder.png"),
				lookup(".dialog-pane").match(n -> n.lookup("#licenseQuery") != null).query());
		interact(() -> lookup("#licenseOk").queryButton().fire());
	}

	/**
	 * A screenshot of choosing a file format from PRONOM, for the help: a
	 * PDF, whose versions are listed.
	 */
	@Test
	public void formatScreenshots() throws Exception {
		String folder = System.getenv("HELP_SCREENSHOTS");
		Assume.assumeTrue("Set HELP_SCREENSHOTS to the folder for them", folder != null && !folder.isBlank());
		Path out = Files.createDirectories(Path.of(folder));
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#addEntity").tryQuery().isPresent());
		look("classic", "light");
		interact(() -> lookup("#headerNew-hasPart").queryAs(MenuButton.class).getItems().stream()
				.filter(i -> i.getText().equals("File")).findFirst().orElseThrow().fire());
		select("#file-1");
		interact(() -> {
			TextField id = lookup("#entityId").queryAs(TextField.class);
			id.setText("paper.pdf");
			id.getOnAction().handle(new javafx.event.ActionEvent());
		});
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("encodingFormat"));
		MenuButton create = lookup("#new-encodingFormat").queryAs(MenuButton.class);
		// The dialog waits for an answer, so not inside interact()
		Platform.runLater(() -> create.getItems().stream()
				.filter(i -> "newPronom-encodingFormat".equals(i.getId())).findFirst().orElseThrow().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#formatQuery").tryQuery().isPresent());
		interact(() -> lookup("#formatQuery").queryAs(TextField.class).setText("pdf 1."));
		png(out.resolve("format-finder.png"),
				lookup(".dialog-pane").match(n -> n.lookup("#formatQuery") != null).query());
		interact(() -> lookup("#formatOk").queryButton().fire());
	}

	/**
	 * A screenshot of finding a person in ORCID, for the help.
	 */
	@Test
	public void personScreenshots() throws Exception {
		String folder = System.getenv("HELP_SCREENSHOTS");
		Assume.assumeTrue("Set HELP_SCREENSHOTS to the folder for them", folder != null && !folder.isBlank());
		Path out = Files.createDirectories(Path.of(folder));
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#addEntity").tryQuery().isPresent());
		look("classic", "light");
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("author"));
		MenuButton create = lookup("#new-author").queryAs(MenuButton.class);
		// The dialog waits for an answer, so not inside interact()
		Platform.runLater(() -> create.getItems().stream().filter(i -> "newOrcid-author".equals(i.getId()))
				.findFirst().orElseThrow().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#personQuery").tryQuery().isPresent());
		interact(() -> lookup("#personQuery").queryAs(TextField.class).setText("Josiah Carberry"));
		interact(() -> lookup("#personSearch").queryButton().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
				() -> !lookup("#personResults").queryListView().getItems().isEmpty());
		png(out.resolve("person-finder.png"),
				lookup(".dialog-pane").match(n -> n.lookup("#personQuery") != null).query());
		interact(() -> lookup("#personOk").queryButton().fire());
		select("https://orcid.org/0000-0002-1825-0097");
		png(out.resolve("person-orcid.png"), lookup(n -> n instanceof EntityEditor).query());
	}

	/**
	 * Screenshots of citing a work by its DOI, for the help.
	 */
	@Test
	public void workScreenshots() throws Exception {
		String folder = System.getenv("HELP_SCREENSHOTS");
		Assume.assumeTrue("Set HELP_SCREENSHOTS to the folder for them", folder != null && !folder.isBlank());
		Path out = Files.createDirectories(Path.of(folder));
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#addEntity").tryQuery().isPresent());
		look("classic", "light");
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("citation"));
		MenuButton create = lookup("#new-citation").queryAs(MenuButton.class);
		// The dialog waits for an answer, so not inside interact()
		Platform.runLater(() -> create.getItems().stream().filter(i -> "newDoi-citation".equals(i.getId()))
				.findFirst().orElseThrow().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> lookup("#workQuery").tryQuery().isPresent());
		interact(() -> lookup("#workQuery").queryAs(TextField.class).setText("10.5281/zenodo.3541888"));
		interact(() -> lookup("#workSearch").queryButton().fire());
		WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
				() -> !lookup("#workResults").queryListView().getItems().isEmpty());
		png(out.resolve("work-finder.png"),
				lookup(".dialog-pane").match(n -> n.lookup("#workQuery") != null).query());
		interact(() -> lookup("#workOk").queryButton().fire());
		select("https://doi.org/10.5281/zenodo.3541888");
		interact(() -> lookup("#propertySearch").queryAs(TextField.class).setText("author"));
		png(out.resolve("work-form.png"), lookup(n -> n instanceof EntityEditor).query());
	}

	/** Time for the map's tiles to come from OpenStreetMap */
	private void waitForTiles() throws InterruptedException {
		Thread.sleep(6000);
		WaitForAsyncUtils.waitForFxEvents();
	}

	/** View > Style and Colours, as chosen from the menu */
	private void look(String style, String colours) {
		choose("Style", style);
		choose("Colours", colours);
	}

	private void choose(String menu, String id) {
		MenuBar bar = lookup("#menuBar").queryAs(MenuBar.class);
		for (Menu top : bar.getMenus()) {
			for (MenuItem item : top.getItems()) {
				if (item instanceof Menu sub && ("menu" + menu).equals(sub.getId())) {
					sub.getItems().stream().filter(i -> ("menu" + menu + "-" + id).equals(i.getId())).findFirst()
							.ifPresent(i -> interact(() -> ((RadioMenuItem) i).setSelected(true)));
				}
			}
		}
		WaitForAsyncUtils.waitForFxEvents();
	}

	private void select(String id) {
		ListView<Entity> entities = lookup("#entities").queryListView();
		interact(() -> {
			Entity entity = entities.getItems().stream().filter(e -> e.getId().equals(id)).findFirst().orElseThrow();
			entities.getSelectionModel().clearAndSelect(entities.getItems().indexOf(entity));
		});
		WaitForAsyncUtils.waitForFxEvents();
	}

	/** The window as a PNG, written without AWT (javafx.swing isn't used) */
	private void png(Path file) throws IOException {
		WaitForAsyncUtils.waitForFxEvents();
		Image[] image = new Image[1];
		interact(() -> image[0] = stage.getScene().snapshot(null));
		png(file, image[0]);
	}

	private void png(Path file, Node node) throws IOException {
		WaitForAsyncUtils.waitForFxEvents();
		Image[] image = new Image[1];
		interact(() -> image[0] = node.snapshot(null, null));
		png(file, image[0]);
	}

	private void png(Path file, Image image) throws IOException {
		int width = (int) image.getWidth();
		int height = (int) image.getHeight();
		PixelReader pixels = image.getPixelReader();
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		for (int y = 0; y < height; y++) {
			// Each row with the Sub filter, which compresses screenshots well
			raw.write(1);
			int left = 0;
			for (int x = 0; x < width; x++) {
				int argb = pixels.getArgb(x, y);
				for (int shift : new int[] { 16, 8, 0 }) {
					raw.write(((argb >> shift) - (left >> shift)) & 0xff);
				}
				left = argb;
			}
		}
		ByteArrayOutputStream header = new ByteArrayOutputStream();
		DataOutputStream ihdr = new DataOutputStream(header);
		ihdr.writeInt(width);
		ihdr.writeInt(height);
		// 8 bits per channel, RGB
		ihdr.write(new byte[] { 8, 2, 0, 0, 0 });
		ByteArrayOutputStream deflated = new ByteArrayOutputStream();
		try (DeflaterOutputStream deflater = new DeflaterOutputStream(deflated,
				new java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION))) {
			raw.writeTo(deflater);
		}
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(png);
		out.write(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' });
		chunk(out, "IHDR", header.toByteArray());
		chunk(out, "IDAT", deflated.toByteArray());
		chunk(out, "IEND", new byte[0]);
		try {
			Files.write(file, png.toByteArray());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void chunk(DataOutputStream out, String type, byte[] data) throws IOException {
		byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		out.writeInt(data.length);
		out.write(name);
		out.write(data);
		CRC32 crc = new CRC32();
		crc.update(name);
		crc.update(data);
		out.writeInt((int) crc.getValue());
	}
}
