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
package no.s11.wpsld;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestSettings {
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	@Test
	public void savedAndLoaded() throws Exception {
		Path file = temp.getRoot().toPath().resolve("config/wpsld/settings.json");
		assertEquals(Settings.DEFAULT, Settings.load(file));
		assertTrue(Settings.DEFAULT.isRegistryEnabled("profiles.ro-crate.org"));
		Settings settings = new Settings(Set.of("profiles.ro-crate.org"));
		settings.save(file);
		assertEquals(settings, Settings.load(file));
		assertFalse(Settings.load(file).isRegistryEnabled("profiles.ro-crate.org"));
		assertTrue(Settings.load(file).isRegistryEnabled("RO-Crate MASP examples"));
	}

	@Test
	public void styleAndColours() throws Exception {
		Path file = temp.getRoot().toPath().resolve("settings.json");
		assertEquals("classic", Settings.DEFAULT.style());
		assertEquals("light", Settings.DEFAULT.colours());
		Settings settings = new Settings(Set.of("profiles.ro-crate.org")).withStyle("win11").withColours("dark");
		settings.save(file);
		assertEquals(settings, Settings.load(file));
		// Changing the registries keeps the look
		assertEquals(new Settings(Set.of(), "win11", "dark"), settings.withDisabledRegistries(Set.of()));
		// Settings from before styles
		Files.writeString(file, "{\"disabledRegistries\": []}");
		assertEquals(Settings.DEFAULT, Settings.load(file));
	}

	@Test
	public void geonamesUsername() throws Exception {
		Path file = temp.getRoot().toPath().resolve("settings.json");
		assertEquals("", Settings.DEFAULT.geonamesUsername());
		Settings settings = Settings.DEFAULT.withStyle("warp").withGeonamesUsername(" someone ");
		assertEquals("someone", settings.geonamesUsername());
		settings.save(file);
		assertEquals(settings, Settings.load(file));
		// Kept when the look changes
		assertEquals("someone", settings.withColours("dark").geonamesUsername());
	}

	@Test
	public void brokenFileIgnored() throws Exception {
		Path file = temp.newFile("settings.json").toPath();
		Files.writeString(file, "{ not json");
		assertEquals(Settings.DEFAULT, Settings.load(file));
	}
}
