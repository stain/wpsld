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

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.testfx.framework.junit.ApplicationTest;

import javafx.scene.image.Image;
import javafx.stage.Stage;
import no.s11.wpsld.test.Online;

/**
 * OpenStreetMap still gives wpsld its tiles, as wpsld names itself (see
 * its tile usage policy). Not part of the build: <code>./gradlew
 * onlineTest</code>, one tile.
 */
@Category(Online.class)
public class TestMapTilesOnline extends ApplicationTest {
	@Override
	public void start(Stage stage) {
		// Only the JavaFX toolkit, to make images
	}

	@Test
	public void tile() throws Exception {
		Optional<Image> tile = MapTiles.openStreetMap(CrateEditor.USER_AGENT).tile(0, 0, 0).get(30, TimeUnit.SECONDS);
		assertTrue("No tile from tile.openstreetmap.org", tile.isPresent());
		assertFalse(tile.get().isError());
		assertEquals(256, tile.get().getWidth(), 0);
	}
}
