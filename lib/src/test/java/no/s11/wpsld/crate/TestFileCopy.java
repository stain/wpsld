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
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import no.s11.wpsld.crate.FileCopy.Conflict;

public class TestFileCopy {
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private static List<String> names(Path folder) throws Exception {
		try (var files = Files.list(folder)) {
			return files.map(f -> f.getFileName().toString()).sorted().toList();
		}
	}

	/** Copied with their dates; one not found told, the others still copied */
	@Test
	public void copy() throws Exception {
		Path from = temp.newFolder("from").toPath();
		Path to = temp.newFolder("to").toPath();
		Path a = Files.writeString(from.resolve("a.csv"), "x".repeat(100_000));
		Files.setLastModifiedTime(a, FileTime.from(Instant.parse("2025-06-02T10:15:30Z")));
		Path b = Files.writeString(from.resolve("b.txt"), "b");
		List<long[]> progress = new ArrayList<>();
		FileCopy.Result result = FileCopy.copy(List.of(a, from.resolve("gone.txt"), b), to, Conflict.SKIP,
				(file, done, total) -> progress.add(new long[] { done, total }), () -> false);
		assertEquals(List.of("a.csv", "b.txt"), names(to));
		assertEquals(Files.readString(a), Files.readString(to.resolve("a.csv")));
		assertEquals(Files.getLastModifiedTime(a), Files.getLastModifiedTime(to.resolve("a.csv")));
		assertEquals(2, result.copied().size());
		assertEquals(1, result.failed().size());
		assertTrue(result.failed().get(0).why(), result.failed().get(0).why().startsWith("Not found"));
		assertEquals(100_001, progress.get(progress.size() - 1)[0]);
		assertEquals(100_001, progress.get(0)[1]);
	}

	@Test
	public void conflicts() throws Exception {
		Path from = temp.newFolder("from").toPath();
		Path to = temp.newFolder("to").toPath();
		Path a = Files.writeString(from.resolve("a.csv"), "new");
		Files.writeString(to.resolve("a.csv"), "old");
		assertEquals(List.of(a), FileCopy.conflicts(List.of(a), to));
		assertEquals(List.of(a), FileCopy.copy(List.of(a), to, Conflict.SKIP, (f, d, t) -> {
		}, () -> false).skipped());
		assertEquals("old", Files.readString(to.resolve("a.csv")));
		FileCopy.copy(List.of(a), to, Conflict.KEEP_BOTH, (f, d, t) -> {
		}, () -> false);
		assertEquals("new", Files.readString(to.resolve("a (2).csv")));
		FileCopy.copy(List.of(a), to, Conflict.REPLACE, (f, d, t) -> {
		}, () -> false);
		assertEquals("new", Files.readString(to.resolve("a.csv")));
		assertEquals(List.of("a (2).csv", "a.csv"), names(to));
	}

	/** Cancelled while copying: no part of the file left */
	@Test
	public void cancel() throws Exception {
		Path from = temp.newFolder("from").toPath();
		Path to = temp.newFolder("to").toPath();
		Path big = Files.write(from.resolve("big.bin"), new byte[1 << 20]);
		int[] blocks = { 0 };
		FileCopy.Result result = FileCopy.copy(List.of(big), to, Conflict.SKIP, (f, d, t) -> blocks[0]++,
				() -> blocks[0] >= 3);
		assertTrue(result.cancelled());
		assertEquals(List.of(), names(to));
	}
}
