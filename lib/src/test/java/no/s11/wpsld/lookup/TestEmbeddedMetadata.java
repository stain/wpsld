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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

import no.s11.wpsld.lookup.EmbeddedMetadata.Found;
import no.s11.wpsld.lookup.EmbeddedMetadata.Kind;

public class TestEmbeddedMetadata {
	/** A ZIP of these parts, as Word or LibreOffice would write it */
	private static Path zip(String name, Map<String, String> parts) throws IOException {
		Path file = Files.createTempDirectory("documents").resolve(name);
		try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
			for (Map.Entry<String, String> part : parts.entrySet()) {
				zip.putNextEntry(new ZipEntry(part.getKey()));
				zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return file;
	}

	private static Map<String, String> byProperty(List<Found> found) {
		Map<String, String> values = new LinkedHashMap<>();
		found.forEach(f -> values.putIfAbsent(f.property(), f.value()));
		return values;
	}

	@Test
	public void word() throws Exception {
		Map<String, String> parts = new LinkedHashMap<>();
		parts.put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>");
		parts.put("docProps/core.xml", """
				<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
				<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
				 xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/"
				 xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
				<dc:title>Week 8 workshop</dc:title><dc:subject>Traffic counts</dc:subject>
				<dc:creator>Josiah Carberry</dc:creator><cp:keywords>DuckDB, SQL; air quality</cp:keywords>
				<dc:description></dc:description><cp:lastModifiedBy>Someone Else</cp:lastModifiedBy>
				<cp:revision>3</cp:revision><dc:language>en-GB</dc:language>
				<dcterms:created xsi:type="dcterms:W3CDTF">2026-04-20T15:36:54Z</dcterms:created>
				<dcterms:modified xsi:type="dcterms:W3CDTF">2026-11-10T14:05:32Z</dcterms:modified>
				</cp:coreProperties>""");
		parts.put("docProps/custom.xml", """
				<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
				<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/custom-properties"
				 xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
				<property fmtid="{D5CDD505-2E9C-101B-9397-08002B2CF9AE}" pid="2" name="Department"><vt:lpwstr>CS</vt:lpwstr></property>
				<property fmtid="{D5CDD505-2E9C-101B-9397-08002B2CF9AE}" pid="3" name="License"><vt:lpwstr>https://creativecommons.org/licenses/by/4.0/</vt:lpwstr></property>
				</Properties>""");
		List<Found> found = EmbeddedMetadata.read(zip("workshop.docx", parts));
		Map<String, String> values = byProperty(found);
		assertEquals("Week 8 workshop", values.get("name"));
		// No description, so the subject
		assertEquals("Traffic counts", values.get("description"));
		assertEquals("Josiah Carberry", values.get("author"));
		assertEquals("DuckDB, SQL; air quality", values.get("keywords"));
		assertEquals("en-GB", values.get("inLanguage"));
		assertEquals("2026-04-20T15:36:54Z", values.get("dateCreated"));
		assertEquals("2026-11-10T14:05:32Z", values.get("dateModified"));
		assertEquals("https://creativecommons.org/licenses/by/4.0/", values.get("license"));
		assertFalse(values.containsValue("Someone Else"));
		Found author = found.stream().filter(f -> f.property().equals("author")).findFirst().orElseThrow();
		assertEquals(Kind.PERSON, author.kind());
		assertEquals("docProps/core.xml dc:creator", author.source());
	}

	@Test
	public void libreOffice() throws Exception {
		Map<String, String> parts = new LinkedHashMap<>();
		parts.put("mimetype", "application/vnd.oasis.opendocument.text");
		parts.put("meta.xml", """
				<?xml version="1.0" encoding="UTF-8"?>
				<office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
				 xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0"
				 office:version="1.3"><office:meta>
				<meta:creation-date>2026-04-20T15:36:54.123456789</meta:creation-date>
				<dc:date>2026-11-10T14:05:32.5</dc:date>
				<meta:generator>LibreOffice/24.8</meta:generator>
				<dc:title>Week 8 workshop</dc:title><dc:description>Getting started
				with DuckDB</dc:description><dc:subject>Traffic</dc:subject>
				<meta:keyword>DuckDB</meta:keyword><meta:keyword>SQL</meta:keyword>
				<meta:initial-creator>Josiah Carberry</meta:initial-creator><dc:creator>Someone Else</dc:creator>
				<dc:language>en-GB</dc:language>
				<meta:document-statistic meta:page-count="3" meta:word-count="812"/>
				<meta:user-defined meta:name="Licence">CC-BY-4.0</meta:user-defined>
				<meta:user-defined meta:name="Project">COMP63301</meta:user-defined>
				</office:meta></office:document-meta>""");
		Map<String, String> values = byProperty(EmbeddedMetadata.read(zip("workshop.odt", parts)));
		assertEquals("Week 8 workshop", values.get("name"));
		assertEquals("Getting started\n\t\t\t\twith DuckDB".replaceAll("\\s+", " "),
				values.get("description").replaceAll("\\s+", " "));
		assertEquals("Josiah Carberry", values.get("author"));
		assertEquals("DuckDB, SQL", values.get("keywords"));
		// No time zone: none made up
		assertEquals("2026-04-20T15:36:54", values.get("dateCreated"));
		assertEquals("2026-11-10T14:05:32", values.get("dateModified"));
		assertEquals("CC-BY-4.0", values.get("license"));
		assertFalse(values.containsValue("Someone Else"));
	}

	/** OpenOffice 1 (.sxw): the same meta.xml, in its own namespaces */
	@Test
	public void openOffice1() throws Exception {
		Map<String, String> parts = new LinkedHashMap<>();
		parts.put("meta.xml", """
				<?xml version="1.0" encoding="UTF-8"?>
				<office:document-meta xmlns:office="http://openoffice.org/2000/office"
				 xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:meta="http://openoffice.org/2000/meta"
				 office:version="1.0"><office:meta>
				<dc:title>Old notes</dc:title><meta:initial-creator>Ada</meta:initial-creator>
				<meta:creation-date>2004-03-01T10:00:00</meta:creation-date>
				<meta:keywords><meta:keyword>old</meta:keyword><meta:keyword>notes</meta:keyword></meta:keywords>
				</office:meta></office:document-meta>""");
		Map<String, String> values = byProperty(EmbeddedMetadata.read(zip("notes.sxw", parts)));
		assertEquals(Map.of("name", "Old notes", "author", "Ada", "dateCreated", "2004-03-01T10:00:00",
				"keywords", "old, notes"), values);
	}

	/** Untrusted: no DTDs (nor so external entities), nothing from other files */
	@Test
	public void untrusted() throws Exception {
		Path secret = Files.createTempFile("secret", ".txt");
		Files.writeString(secret, "secret");
		Map<String, String> parts = new LinkedHashMap<>();
		parts.put("docProps/core.xml", "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"" + secret.toUri()
				+ "\">]><cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\""
				+ " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>&e;</dc:title></cp:coreProperties>");
		assertEquals(List.of(), EmbeddedMetadata.read(zip("evil.docx", parts)));
		// Not a ZIP, or not a document
		assertEquals(List.of(), EmbeddedMetadata.read(secret));
		assertEquals(List.of(), EmbeddedMetadata.read(zip("other.zip", Map.of("a.txt", "a"))));
	}

	@Test
	public void dates() {
		assertEquals(Optional.of("2026-04-20T15:36:54Z"), EmbeddedMetadata.date("2026-04-20T15:36:54Z"));
		assertEquals(Optional.of("2026-04-20T15:36:54+01:00"), EmbeddedMetadata.date("2026-04-20T15:36:54.5+01:00"));
		assertEquals(Optional.of("2026-04-20"), EmbeddedMetadata.date("2026-04-20"));
		// Office's unknown date
		assertEquals(Optional.empty(), EmbeddedMetadata.date("1601-01-01T00:00:00Z"));
		assertEquals(Optional.empty(), EmbeddedMetadata.date("yesterday"));
	}

	private static Path resource(String name) throws Exception {
		Path file = Files.createTempDirectory("images").resolve(name);
		try (var in = TestEmbeddedMetadata.class.getResourceAsStream("/no/s11/wpsld/lookup/" + name)) {
			Files.copy(in, file);
		}
		return file;
	}

	/** A photo's EXIF, GPS, IPTC and XMP; not its camera */
	@Test
	public void jpeg() throws Exception {
		List<Found> found = EmbeddedMetadata.read(resource("photo.jpg"));
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
		assertFalse(found.stream().anyMatch(f -> f.value().contains("Canon")));
	}

	@Test
	public void png() throws Exception {
		Map<String, String> values = byProperty(EmbeddedMetadata.read(resource("picture.png")));
		assertEquals("Workshop diagram", values.get("name"));
		assertEquals("Ada Lovelace", values.get("author"));
		assertEquals("diagram, workshop", values.get("keywords"));
		assertEquals("Ada Lovelace", values.get("copyrightNotice"));
		assertEquals("2026-04-20T15:36:54+01:00", values.get("dateCreated"));
		assertEquals("https://creativecommons.org/licenses/by-sa/4.0/", values.get("license"));
	}

	/** As Inkscape writes it, with SVG 1.1's DOCTYPE, which isn't fetched */
	@Test
	public void svg() throws Exception {
		Path file = Files.createTempDirectory("svg").resolve("drawing.svg");
		Files.writeString(file, """
				<?xml version="1.0" encoding="UTF-8" standalone="no"?>
				<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
				<svg xmlns="http://www.w3.org/2000/svg" xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
				 xmlns:cc="http://creativecommons.org/ns#" xmlns:dc="http://purl.org/dc/elements/1.1/"
				 width="210mm" height="297mm">
				 <title>Route map</title><desc>The bus routes</desc>
				 <metadata><rdf:RDF><cc:Work rdf:about="">
				  <dc:format>image/svg+xml</dc:format>
				  <dc:type rdf:resource="http://purl.org/dc/dcmitype/StillImage"/>
				  <dc:creator><cc:Agent><dc:title>Ada Lovelace</dc:title></cc:Agent></dc:creator>
				  <dc:date>2026-04-20</dc:date>
				  <dc:rights><cc:Agent><dc:title>The University</dc:title></cc:Agent></dc:rights>
				  <dc:subject><rdf:Bag><rdf:li>buses</rdf:li><rdf:li>map</rdf:li></rdf:Bag></dc:subject>
				  <cc:license rdf:resource="http://creativecommons.org/licenses/by/4.0/"/>
				 </cc:Work></rdf:RDF></metadata>
				 <rect width="10" height="10"/>
				</svg>""");
		Map<String, String> values = byProperty(EmbeddedMetadata.read(file));
		assertEquals(Map.of("name", "Route map", "description", "The bus routes", "author", "Ada Lovelace",
				"dateCreated", "2026-04-20", "copyrightNotice", "The University", "keywords", "buses, map",
				"license", "http://creativecommons.org/licenses/by/4.0/"), values);
	}

	/** XMP as PDFs have it, for ui to read with PDFBox */
	@Test
	public void xmp() throws Exception {
		List<Found> found = EmbeddedMetadata.xmp(new java.io.ByteArrayInputStream("""
				<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
				<rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/"
				 xmlns:xmp="http://ns.adobe.com/xap/1.0/" xmlns:xmpRights="http://ns.adobe.com/xap/1.0/rights/"
				 xmp:CreateDate="2026-04-20T15:36:54+01:00" xmp:ModifyDate="2026-11-10T14:05:32Z">
				<dc:title><rdf:Alt><rdf:li xml:lang="x-default">Report</rdf:li><rdf:li xml:lang="nb">Rapport</rdf:li></rdf:Alt></dc:title>
				<dc:creator><rdf:Seq><rdf:li>Ada Lovelace</rdf:li><rdf:li>Charles Babbage</rdf:li></rdf:Seq></dc:creator>
				<xmpRights:WebStatement>https://creativecommons.org/licenses/by/4.0/</xmpRights:WebStatement>
				</rdf:Description></rdf:RDF></x:xmpmeta>""".getBytes(StandardCharsets.UTF_8)), "PDF XMP ");
		assertEquals(List.of("name Report", "author Ada Lovelace", "author Charles Babbage",
				"license https://creativecommons.org/licenses/by/4.0/", "dateCreated 2026-04-20T15:36:54+01:00",
				"dateModified 2026-11-10T14:05:32Z"),
				found.stream().map(f -> f.property() + " " + f.value()).sorted(java.util.Comparator
						.comparing((String v) -> List.of("name", "author", "license", "dateCreated", "dateModified")
								.indexOf(v.substring(0, v.indexOf(' '))))).toList());
		assertEquals("PDF XMP xmp:CreateDate", found.stream().filter(f -> f.property().equals("dateCreated"))
				.findFirst().orElseThrow().source());
	}

	@Test
	public void exifDates() {
		assertEquals(Optional.of("2026-04-20T15:36:54"), EmbeddedMetadata.exifDate("2026:04:20 15:36:54", null));
		assertEquals(Optional.of("2026-04-20T15:36:54-05:00"),
				EmbeddedMetadata.exifDate("2026:04:20 15:36:54", "-05:00"));
		// Unknown, as cameras write it
		assertEquals(Optional.empty(), EmbeddedMetadata.exifDate("0000:00:00 00:00:00", null));
	}
}
