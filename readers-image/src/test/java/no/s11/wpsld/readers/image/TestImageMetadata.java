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

import com.drew.lang.Rational;

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

	/**
	 * A test file: this module's own (made with ffmpeg, libheif, libavif,
	 * ImageMagick and exiftool from photo.jpg), else lib's
	 */
	static Path resource(String name) throws Exception {
		Path file = Files.createTempDirectory("images").resolve(name);
		var own = TestImageMetadata.class.getResourceAsStream(name);
		try (var in = own != null ? own : TestImageMetadata.class.getResourceAsStream("/no/s11/wpsld/lookup/" + name)) {
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

	/** HEIC and AVIF, with the photo's EXIF, GPS and XMP */
	@Test
	public void heicAndAvif() throws Exception {
		for (String name : List.of("photo.heic", "photo.avif")) {
			List<Found> found = read(resource(name));
			Map<String, String> values = byProperty(found);
			assertEquals(name, "Bus stop on Oxford Road", values.get("description"));
			assertEquals(name, "Josiah Carberry", values.get("author"));
			assertEquals(name, "2026-04-20T15:36:54+01:00", values.get("dateCreated"));
			assertEquals(name, "53.466800, -2.233900", values.get("contentLocation"));
			assertEquals(name, "https://creativecommons.org/licenses/by/4.0/", values.get("license"));
			assertTrue(name, values.get("exifData").startsWith("Canon EOS R6"));
		}
		// Not HEIF's own size, of the coded image before it is cropped and
		// turned (64 px, of an 8 × 6 photo): only EXIF's, which these lack
		assertNull(byProperty(read(resource("photo.heic"))).get("width"));
		assertNull(byProperty(read(resource("photo.avif"))).get("width"));
	}

	/** A GIF's XMP and size */
	@Test
	public void gif() throws Exception {
		Map<String, String> values = byProperty(read(resource("picture.gif")));
		assertEquals("Orange square", values.get("name"));
		assertEquals("Josiah Carberry", values.get("author"));
		assertEquals("8 px", values.get("width"));
		assertEquals("6 px", values.get("height"));
	}

	/** An MP4's title, when (UTC), how long, and its size */
	@Test
	public void mp4() throws Exception {
		Map<String, String> values = byProperty(read(resource("clip.mp4")));
		assertEquals("Bus stop", values.get("name"));
		assertEquals("2026-04-20T14:36:54Z", values.get("dateCreated"));
		assertEquals("PT2.5S", values.get("duration"));
		assertEquals("16 px", values.get("width"));
		assertEquals("12 px", values.get("height"));
	}

	/**
	 * A phone's MOV: Apple's title, author, when (with its offset, before
	 * the movie header's UTC) and where, and the phone, only on purpose
	 */
	@Test
	public void mov() throws Exception {
		List<Found> found = read(resource("clip.mov"));
		Map<String, String> values = byProperty(found);
		assertEquals("Bus stop", values.get("name"));
		assertEquals("Josiah Carberry", values.get("author"));
		assertEquals("2026-04-20T15:36:54+01:00", values.get("dateCreated"));
		assertTrue(found.contains(new Found("dateCreated", "2026-04-20T14:36:54Z",
				no.s11.wpsld.lookup.EmbeddedMetadata.Kind.DATE, "QuickTime creation time")));
		assertEquals("53.466800, -2.233900", values.get("contentLocation"));
		assertEquals("PT2.5S", values.get("duration"));
		assertEquals("16 px", values.get("width"));
		assertEquals("Apple iPhone 15", values.get("exifData"));
		assertTrue(found.stream().filter(f -> f.property().equals("exifData")).allMatch(Found::onPurpose));
	}

	/** By metadata-extractor's detection: raw formats too; not others */
	@Test
	public void reads() throws Exception {
		ImageMetadata reader = new ImageMetadata();
		for (String name : List.of("photo.heic", "photo.avif", "picture.gif", "clip.mp4", "clip.mov", "photo.jpg",
				"picture.png")) {
			assertTrue(name, reader.reads(Path.of(name), Files.readAllBytes(resource(name))));
		}
		byte[] cr2 = { 'I', 'I', 42, 0, 16, 0, 0, 0, 'C', 'R', 2, 0 };
		byte[] orf = { 'I', 'I', 'R', 'O', 8, 0, 0, 0 };
		byte[] rw2 = { 'I', 'I', 'U', 0, 24, 0, 0, 0 };
		byte[] raf = "FUJIFILMCCD-RAW 0201FF383501".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		for (byte[] head : List.of(cr2, orf, rw2, raf)) {
			assertTrue(ImageMetadata.type(head).toString(), reader.reads(Path.of("raw"), head));
		}
		for (String other : List.of("%PDF-1.7\n", "PK\3\4", "Hello", "")) {
			assertFalse(other, reader.reads(Path.of("other"), other.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
		}
	}

	@Test
	public void durations() {
		assertEquals(Optional.of("PT2.5S"), ImageMetadata.duration(new Rational(2500, 1000)));
		assertEquals(Optional.of("PT1H2M5S"), ImageMetadata.duration(new Rational(3725, 1)));
		assertEquals(Optional.of("PT1M"), ImageMetadata.duration(new Rational(60, 1)));
		assertEquals(Optional.empty(), ImageMetadata.duration(new Rational(0, 1000)));
		assertEquals(Optional.empty(), ImageMetadata.duration(new Rational(5, 0)));
		assertEquals(Optional.empty(), ImageMetadata.duration(null));
	}

	@Test
	public void iso6709() {
		assertEquals(Optional.of("53.466800, -2.233900"), ImageMetadata.iso6709("+53.4668-002.2339+040.000/"));
		assertEquals(Optional.of("53.466800, -2.233900"), ImageMetadata.iso6709("+53.4668-002.2339/"));
		// Degrees and minutes; degrees, minutes and seconds
		assertEquals(Optional.of("53.500000, -2.250000"), ImageMetadata.iso6709("+5330.0-00215.0/"));
		assertEquals(Optional.of("53.466800, -2.233900"), ImageMetadata.iso6709("+532800.48-0021402.04/"));
		for (String bad : List.of("", "53.4668,-2.2339", "+91.0+000.0/", "+00.0000+000.0000/", "+5-2/")) {
			assertEquals(bad, Optional.empty(), ImageMetadata.iso6709(bad));
		}
		assertEquals(Optional.empty(), ImageMetadata.iso6709(null));
	}

	@Test
	public void quickTimeDates() {
		assertEquals(Optional.of("2026-04-20T15:36:54+01:00"), ImageMetadata.quickTimeDate("2026-04-20T15:36:54+0100"));
		assertEquals(Optional.of("2026-04-20T15:36:54-05:00"), ImageMetadata.quickTimeDate("2026-04-20T15:36:54-05:00"));
		assertEquals(Optional.empty(), ImageMetadata.quickTimeDate("yesterday"));
	}

	/**
	 * A movie whose sample description claims more entries than fit in it
	 * is broken, rather than metadata-extractor running out of memory on it
	 * (as found by the damaged-input tests)
	 */
	@Test
	public void brokenMovie() throws Exception {
		Path file = resource("clip.mov");
		byte[] bytes = Files.readAllBytes(file);
		int stsd = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).indexOf("stsd");
		assertTrue(stsd > 0);
		// The count, after the type, version and flags
		java.nio.ByteBuffer.wrap(bytes).putInt(stsd + 8, Integer.MAX_VALUE);
		Files.write(file, bytes);
		FileMetadata.Reading reading = new FileMetadata(List.of(new ImageMetadata()), FileMetadata.LIMIT).read(file);
		assertEquals(1, reading.problems().size());
		assertTrue(reading.problems().get(0), reading.problems().get(0).contains("Broken movie: its stsd box has "
				+ Integer.MAX_VALUE + " entries"));
	}
}
