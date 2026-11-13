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
package no.s11.wpsld.readers.image;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import org.junit.Test;

import no.s11.wpsld.lookup.EmbeddedMetadata.Found;
import no.s11.wpsld.lookup.FileMetadata;
import no.s11.wpsld.lookup.MetadataReader;

public class TestImageMetadata {
	private static Map<String, String> byProperty(List<Found> found) {
		Map<String, String> values = new LinkedHashMap<>();
		found.forEach(f -> values.putIfAbsent(f.property(), f.value()));
		return values;
	}

	/** As FileMetadata reads it: with this reader, if it reads the file */
	private static List<Found> read(Path file) {
		FileMetadata.Reading reading = new FileMetadata(List.of(new ImageMetadata()), FileMetadata.LIMIT).read(file);
		assertEquals(List.of(), reading.problems());
		return reading.found();
	}

	/** Found as a service, as lib's FileMetadata finds it */
	@Test
	public void service() {
		assertTrue(ServiceLoader.load(MetadataReader.class).stream()
				.anyMatch(p -> p.type().equals(ImageMetadata.class)));
		assertTrue(FileMetadata.installed().readers().stream().anyMatch(ImageMetadata.class::isInstance));
	}

	private static Path resource(String name) throws Exception {
		Path file = Files.createTempDirectory("images").resolve(name);
		try (var in = TestImageMetadata.class.getResourceAsStream("/no/s11/wpsld/lookup/" + name)) {
			Files.copy(in, file);
		}
		return file;
	}

	/** A photo's EXIF, GPS, IPTC and XMP; not its camera */
	@Test
	public void jpeg() throws Exception {
		List<Found> found = read(resource("photo.jpg"));
		Map<String, String> values = byProperty(found);
		assertEquals("Bus stop on Oxford Road", values.get("description"));
		assertEquals("Josiah Carberry", values.get("author"));
		assertEquals("© 2026 Josiah Carberry", values.get("copyrightNotice"));
		// When taken, with its offset; when changed, without one
		assertEquals("2026-04-20T15:36:54+01:00", values.get("dateCreated"));
		assertEquals("2026-04-21T09:00:00", values.get("dateModified"));
		assertEquals("53.466800, -2.233900", values.get("contentLocation"));
		assertEquals("bus, Manchester", values.get("keywords"));
		assertEquals("https://creativecommons.org/licenses/by/4.0/", values.get("license"));
		// The same author from EXIF and XMP: once
		assertEquals(1, found.stream().filter(f -> f.property().equals("author")).count());
		assertEquals("8 px", values.get("width"));
		assertEquals("6 px", values.get("height"));
		// The camera, and where: only on purpose
		assertTrue(values.get("exifData"), values.get("exifData").startsWith("Canon EOS R6"));
		assertTrue(found.stream().filter(f -> f.property().equals("exifData")).allMatch(Found::onPurpose));
		assertTrue(found.stream().filter(f -> f.property().equals("contentLocation")).allMatch(Found::onPurpose));
		assertFalse(found.stream().filter(f -> f.property().equals("author")).anyMatch(Found::onPurpose));
	}

	@Test
	public void png() throws Exception {
		Map<String, String> values = byProperty(read(resource("picture.png")));
		assertEquals("Workshop diagram", values.get("name"));
		assertEquals("Ada Lovelace", values.get("author"));
		assertEquals("diagram, workshop", values.get("keywords"));
		assertEquals("Ada Lovelace", values.get("copyrightNotice"));
		assertEquals("2026-04-20T15:36:54+01:00", values.get("dateCreated"));
		assertEquals("https://creativecommons.org/licenses/by-sa/4.0/", values.get("license"));
	}

	@Test
	public void exifDates() {
		assertEquals(Optional.of("2026-04-20T15:36:54"), ImageMetadata.exifDate("2026:04:20 15:36:54", null));
		assertEquals(Optional.of("2026-04-20T15:36:54-05:00"),
				ImageMetadata.exifDate("2026:04:20 15:36:54", "-05:00"));
		// Unknown, as cameras write it
		assertEquals(Optional.empty(), ImageMetadata.exifDate("0000:00:00 00:00:00", null));
	}
}
