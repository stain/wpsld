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

	/** The crate's files in the folder and not, and the folder's not in the crate */
	@Test
	public void match() throws Exception {
		Path folder = temp.getRoot().toPath();
		Files.createDirectories(folder.resolve("data"));
		Files.writeString(folder.resolve("data/my counts.csv"), "");
		Files.writeString(folder.resolve("other.txt"), "");
		Crate crate = new Crate();
		for (String id : List.of("data/", "data/my%20counts.csv", "gone.csv", "https://example.com/x", "page.html#t")) {
			Entity part = crate.add("File");
			crate.rename(part, id);
		}
		crate.getRoot().setAll("hasPart", List.of(ref("data/"), ref("data/my%20counts.csv"), ref("gone.csv"),
				ref("https://example.com/x"), ref("page.html#t")));
		CrateFolder.Match match = CrateFolder.match(crate, folder, 100);
		assertEquals(List.of("data/", "data/my%20counts.csv"), match.present());
		assertEquals(List.of("gone.csv"), match.absent());
		assertEquals(List.of("other.txt"), match.undescribed());
		assertFalse(match.elsewhere());
		assertTrue(CrateFolder.match(crate, folder.resolve("elsewhere"), 100).elsewhere());
	}

	/** What is wrong with a crate attached to a folder, to tell before saving */
	@Test
	public void problems() throws Exception {
		Path folder = temp.getRoot().toPath();
		Files.createDirectories(folder.resolve("data"));
		Files.writeString(folder.resolve("page.html"), "");
		Crate crate = new Crate();
		for (String[] e : new String[][] { { "data", "Dataset" }, { "../other/x.csv", "File" },
				{ "gone.csv", "File" }, { "page.html#t2", "Table" }, { "lost.html#t1", "Table" },
				{ "https://example.com/y.csv", "File" }, { "#alice", "Person" } }) {
			Entity entity = crate.add(e[1]);
			crate.rename(entity, e[0]);
		}
		assertEquals(List.of(
				"1 entity is outside the crate's folder, which an attached crate can't have: ../other/x.csv",
				"1 Dataset's @id should end with / as a folder's: data",
				"2 files or folders are not in the crate's folder: gone.csv, lost.html#t1"),
				CrateFolder.problems(crate, folder));
		crate.rename(crate.getRoot(), "root/");
		assertEquals("The root data entity's @id must be ./ or an absolute URI, not root/",
				CrateFolder.problems(crate, folder).get(0));
		crate.rename(crate.getRoot(), "https://doi.org/10.5281/zenodo.1");
		assertEquals(3, CrateFolder.problems(crate, folder).size());
		assertFalse(CrateFolder.exists(folder, "../" + folder.getFileName() + "/page.html"));
	}

	/**
	 * Where names are found ignoring upper and lower case (Windows, macOS),
	 * an @id with other case than the file's is a problem; elsewhere it is
	 * not found at all
	 */
	@Test
	public void otherCase() throws Exception {
		Path folder = temp.getRoot().toPath();
		Files.createDirectories(folder.resolve("Data"));
		Files.writeString(folder.resolve("Data/Counts.csv"), "");
		assertEquals(java.util.Optional.empty(), CrateFolder.problem(folder, "Data/Counts.csv"));
		String problem = CrateFolder.problem(folder, "data/counts.csv").orElseThrow();
		if (Files.exists(folder.resolve("data/counts.csv"))) {
			assertTrue(problem, problem.startsWith("Named Data/Counts.csv in the crate's folder"));
		} else {
			assertEquals("Not found in the crate's folder: data/counts.csv", problem);
		}
	}

	/** Size and date brought up to date; the same date written otherwise kept */
	@Test
	public void update() throws Exception {
		Path file = temp.newFile("counts.csv").toPath();
		Files.writeString(file, "a,b\n");
		Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2025-06-02T10:15:30Z")));
		Entity entity = new Entity("counts.csv", "File");
		assertEquals(List.of("contentSize 4", "dateModified 2025-06-02T10:15:30Z"), CrateFolder.update(entity, file));
		assertEquals(List.of(), CrateFolder.update(entity, file));
		entity.set("dateModified", java.util.Optional.of(com.fasterxml.jackson.databind.node.TextNode.valueOf("2025-06-02")));
		entity.set("contentSize", java.util.Optional.of(com.fasterxml.jackson.databind.node.TextNode.valueOf("3")));
		assertEquals(List.of("contentSize 3 → 4"), CrateFolder.update(entity, file));
		entity.set("dateModified", java.util.Optional.of(com.fasterxml.jackson.databind.node.TextNode.valueOf("2025-06-02T12:15:30+02:00")));
		assertEquals(List.of(), CrateFolder.update(entity, file));
		Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2025-07-01T00:00:00Z")));
		assertEquals(List.of("dateModified 2025-06-02T12:15:30+02:00 → 2025-07-01T00:00:00Z"),
				CrateFolder.update(entity, file));
		assertEquals(List.of(), CrateFolder.update(entity, temp.getRoot().toPath().resolve("gone.csv")));
	}

	private static com.fasterxml.jackson.databind.JsonNode ref(String id) {
		return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("@id", id);
	}
}
