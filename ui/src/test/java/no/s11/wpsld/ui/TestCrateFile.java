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
import java.nio.file.attribute.FileTime;
import java.time.Instant;
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

	/**
	 * Changes on disk: a new time and size, new content, the same content
	 * written again (as git may), and a deleted file.
	 */
	@Test
	public void disk() throws Exception {
		Path file = Files.createTempDirectory("crate").resolve("ro-crate-metadata.json");
		Files.writeString(file, "{\"a\": 1}");
		CrateFile crateFile = new CrateFile(SAVED);
		assertEquals(CrateFile.Disk.SAME, crateFile.disk());
		crateFile.reset(Optional.of(file), SAVED);
		assertEquals(CrateFile.Disk.SAME, crateFile.disk());

		// Touched, as git checking out the same content: not a change
		Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(10)));
		assertEquals(CrateFile.Disk.SAME, crateFile.disk());
		Files.writeString(file, "{\"a\": 1}");
		assertEquals(CrateFile.Disk.SAME, crateFile.disk());

		// The same size, other content and time
		Files.writeString(file, "{\"a\": 2}");
		Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(20)));
		assertEquals(CrateFile.Disk.CHANGED, crateFile.disk());
		assertFalse(crateFile.keptChangesOverDisk());
		crateFile.keepChangesOverDisk();
		assertTrue(crateFile.keptChangesOverDisk());
		// Changed again: asked again
		Files.writeString(file, "{\"a\": 3}");
		assertFalse(crateFile.keptChangesOverDisk());
		// Saved (or reloaded): the file as it is now
		crateFile.reset(Optional.of(file), SAVED);
		assertEquals(CrateFile.Disk.SAME, crateFile.disk());

		Files.delete(file);
		assertEquals(CrateFile.Disk.MISSING, crateFile.disk());
	}

	/** Line ends as the file had them: \r\n only if every line has them, as in Vim */
	@Test
	public void lineEnds() throws Exception {
		assertEquals("\n", CrateFile.lineEnd("{\n}\n".getBytes()));
		assertEquals("\r\n", CrateFile.lineEnd("{\r\n}\r\n".getBytes()));
		assertEquals("\n", CrateFile.lineEnd("{\r\n}\n".getBytes()));
		assertEquals("\n", CrateFile.lineEnd("{}".getBytes()));
		Path file = Files.createTempDirectory("crate").resolve("ro-crate-metadata.json");
		Files.writeString(file, "{\r\n}\r\n");
		CrateFile crateFile = new CrateFile(SAVED);
		assertEquals("\n", crateFile.lineEnd());
		crateFile.reset(Optional.of(file), SAVED);
		assertEquals("\r\n", crateFile.lineEnd());
		// Save As elsewhere keeps them; a new crate has \n
		crateFile.reset(Optional.empty(), SAVED);
		assertEquals("\n", crateFile.lineEnd());
	}
}
