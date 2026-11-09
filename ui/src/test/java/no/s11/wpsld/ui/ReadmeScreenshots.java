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

import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.stage.Stage;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
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
		app = new CrateEditor(SchemaFromJsonLd::new, CrateTerms::prewarm,
				new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
						Optional.of(temp.resolve("profiles")), List.of()),
				Optional.of(temp.resolve("settings.json")));
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
		interact(() -> entities.getItems().stream().filter(e -> e.getId().equals(id)).findFirst()
				.ifPresent(entities.getSelectionModel()::select));
		WaitForAsyncUtils.waitForFxEvents();
	}

	/** The window as a PNG, written without AWT (javafx.swing isn't used) */
	private void png(Path file) throws IOException {
		WaitForAsyncUtils.waitForFxEvents();
		Image[] image = new Image[1];
		interact(() -> image[0] = stage.getScene().snapshot(null));
		int width = (int) image[0].getWidth();
		int height = (int) image[0].getHeight();
		PixelReader pixels = image[0].getPixelReader();
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
