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
package no.s11.wpsld.crate;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestCrateFolder {
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	/** Its files and folders, not the crate's own, nor hidden ones */
	@Test
	public void list() throws Exception {
		Path folder = temp.getRoot().toPath();
		Files.createDirectories(folder.resolve("data/raw"));
		Files.writeString(folder.resolve("data/my counts.csv"), "a,b\n");
		Files.writeString(folder.resolve(Crate.RO_CRATE_METADATA_JSON), "{}");
		Files.writeString(folder.resolve("ro-crate-preview.html"), "");
		Files.createDirectories(folder.resolve(".git"));
		Files.writeString(folder.resolve(".git/config"), "");
		Files.writeString(folder.resolve("data/.hidden"), "");
		// Only at the top are these the crate's own
		Files.writeString(folder.resolve("data/ro-crate-preview.html"), "");
		assertEquals(List.of("data/", "data/my%20counts.csv", "data/raw/", "data/ro-crate-preview.html"),
				CrateFolder.list(folder, 100).stream().sorted().toList());
		assertEquals(2, CrateFolder.list(folder, 2).size());
	}

	@Test
	public void describe() throws Exception {
		Path file = temp.newFile("counts.csv").toPath();
		Files.writeString(file, "a,b\n");
		Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2025-06-02T10:15:30.123Z")));
		Entity entity = new Entity("counts.csv", "File");
		CrateFolder.describe(entity, file);
		assertEquals("4", entity.get("contentSize").orElseThrow().asText());
		assertEquals("2025-06-02T10:15:30Z", entity.get("dateModified").orElseThrow().asText());
	}
}
