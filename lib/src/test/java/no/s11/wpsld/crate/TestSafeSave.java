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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TestSafeSave {
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private static byte[] bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

	/** A new file, and over one, leaving no temporary file */
	@Test
	public void write() throws Exception {
		Path folder = temp.getRoot().toPath();
		Path file = folder.resolve("ro-crate-metadata.json");
		SafeSave.Method method = SafeSave.write(file, bytes("one"));
		assertEquals("one", Files.readString(file));
		SafeSave.write(file, bytes("two"));
		assertEquals("two", Files.readString(file));
		try (var files = Files.list(folder)) {
			assertEquals(1, files.count());
		}
		// Linked where POSIX, as on Linux and macOS
		if (Files.getFileStore(folder).supportsFileAttributeView("posix")) {
			assertEquals(SafeSave.Method.LINKED, method);
		}
	}

	/** Its permissions kept, as the new file takes its place */
	@Test
	public void permissions() throws Exception {
		Path file = temp.getRoot().toPath().resolve("ro-crate-metadata.json");
		Assume.assumeTrue(Files.getFileStore(temp.getRoot().toPath()).supportsFileAttributeView("posix"));
		Files.writeString(file, "one");
		Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
		SafeSave.write(file, bytes("two"));
		assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
	}

	/** A symbolic link stays one: the file it links to is saved */
	@Test
	public void symbolicLink() throws Exception {
		Path folder = temp.getRoot().toPath();
		Path real = Files.createDirectories(folder.resolve("real")).resolve("ro-crate-metadata.json");
		Files.writeString(real, "one");
		Path link = folder.resolve("ro-crate-metadata.jsonld");
		try {
			Files.createSymbolicLink(link, real);
		} catch (UnsupportedOperationException | java.io.IOException e) {
			Assume.assumeNoException(e);
		}
		SafeSave.write(link, bytes("two"));
		assertTrue(Files.isSymbolicLink(link));
		assertEquals("two", Files.readString(real));
	}
}
