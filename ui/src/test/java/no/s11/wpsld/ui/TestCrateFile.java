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

import java.nio.file.Path;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * The file being edited: its name, unsaved changes, and the window title on
 * each operating system.
 */
public class TestCrateFile {
	private static final JsonNode SAVED = JsonNodeFactory.instance.objectNode().put("name", "saved");
	private static final JsonNode CHANGED = JsonNodeFactory.instance.objectNode().put("name", "changed");

	@Test
	public void untitled() {
		CrateFile file = new CrateFile(SAVED);
		assertEquals("Untitled", file.name());
		assertFalse(file.isModified(SAVED));
		assertEquals("Untitled – wpsld", file.title(SAVED, false));
		assertEquals("*Untitled – wpsld", file.title(CHANGED, false));
		assertEquals("Untitled", file.title(SAVED, true));
		assertEquals("Untitled — Edited", file.title(CHANGED, true));
	}

	@Test
	public void savedWithFolder() {
		CrateFile file = new CrateFile(CHANGED);
		Path path = Path.of("crates", "my-crate", "ro-crate-metadata.json");
		file.reset(Optional.of(path), SAVED);
		assertEquals(Optional.of(path), file.path());
		// The folder, as most crates' files have the same name
		assertEquals(Path.of("my-crate", "ro-crate-metadata.json").toString(), file.name());
		assertTrue(file.isModified(CHANGED));
		assertEquals("*" + file.name() + " – wpsld", file.title(CHANGED, false));
		assertEquals(file.name() + " — Edited", file.title(CHANGED, true));
		// Back to new
		file.reset(Optional.empty(), CHANGED);
		assertEquals("Untitled", file.name());
	}

	@Test
	public void noFolder() {
		CrateFile file = new CrateFile(SAVED);
		file.reset(Optional.of(Path.of("ro-crate-metadata.json")), SAVED);
		assertEquals("ro-crate-metadata.json", file.name());
	}
}
