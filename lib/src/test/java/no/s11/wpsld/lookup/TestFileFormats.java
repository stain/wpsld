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
package no.s11.wpsld.lookup;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

public class TestFileFormats {
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private Path write(String name, byte[] content) throws Exception {
		return Files.write(temp.getRoot().toPath().resolve(name), content);
	}

	private static byte[] hdf5At(int offset) {
		byte[] bytes = new byte[offset + 64];
		byte[] signature = { (byte) 0x89, 'H', 'D', 'F', '\r', '\n', 0x1a, '\n' };
		System.arraycopy(signature, 0, bytes, offset, signature.length);
		return bytes;
	}

	/** By content, whatever the name says */
	@Test
	public void byContent() throws Exception {
		FileFormats formats = FileFormats.bundled();
		FileFormats.Detected pdf = formats.detect(write("scan.bin", "%PDF-1.4\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1)));
		assertEquals("application/pdf", pdf.mediaType());
		assertEquals("Portable Document Format", pdf.name().orElseThrow());
		// PRONOM has PDF's versions, not PDF as such
		assertTrue(pdf.pronom().isEmpty());
		assertEquals(FileFormats.HDF5, formats.detect(write("run1.h5", hdf5At(0))).mediaType());
		// After a user block, as some instruments write
		assertEquals(FileFormats.HDF5, formats.detect(write("run2.dat", hdf5At(512))).mediaType());
	}

	/** An instrument's .dat: text or not; never a guess from the extension */
	@Test
	public void unknown() throws Exception {
		FileFormats formats = FileFormats.bundled();
		byte[] noise = new byte[4096];
		new Random(42).nextBytes(noise);
		FileFormats.Detected bin = formats.detect(write("signal.dat", noise));
		assertEquals(FileFormats.OCTET_STREAM, bin.mediaType());
		assertTrue(bin.pronom().isEmpty());
		assertEquals("text/plain", formats.detect(write("log.dat", "t=0 v=1.2\nt=1 v=1.3\n".getBytes())).mediaType());
	}

	/** Linked to PRONOM where only one format fits, and described in the crate */
	@Test
	public void addTo() throws Exception {
		Crate crate = new Crate();
		Entity csv = crate.add("File");
		crate.rename(csv, "counts.csv");
		Path file = write("counts.csv", "species,count\nrobin,3\n".getBytes());
		FileFormats.Detected detected = FileFormats.bundled().addTo(crate, csv, file).orElseThrow();
		assertEquals("text/csv", detected.mediaType());
		assertEquals("x-fmt/18", detected.pronom().orElseThrow().puid());
		assertEquals("text/csv", csv.getAll("encodingFormat").get(0).asText());
		String iri = csv.getAll("encodingFormat").get(1).get("@id").asText();
		assertTrue(crate.get(iri).orElseThrow().getTypes().containsAll(PRONOMFormats.TYPES));
		// Not again, once it has one
		assertTrue(FileFormats.bundled().addTo(crate, csv, file).isEmpty());
		assertEquals(2, csv.getAll("encodingFormat").size());
	}
}
