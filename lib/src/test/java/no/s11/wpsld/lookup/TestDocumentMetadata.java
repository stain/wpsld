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

import no.s11.wpsld.lookup.DocumentMetadata.Found;
import no.s11.wpsld.lookup.DocumentMetadata.Kind;

public class TestDocumentMetadata {
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
		List<Found> found = DocumentMetadata.read(zip("workshop.docx", parts));
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
		Map<String, String> values = byProperty(DocumentMetadata.read(zip("workshop.odt", parts)));
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
		Map<String, String> values = byProperty(DocumentMetadata.read(zip("notes.sxw", parts)));
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
		assertEquals(List.of(), DocumentMetadata.read(zip("evil.docx", parts)));
		// Not a ZIP, or not a document
		assertEquals(List.of(), DocumentMetadata.read(secret));
		assertEquals(List.of(), DocumentMetadata.read(zip("other.zip", Map.of("a.txt", "a"))));
	}

	@Test
	public void dates() {
		assertEquals(Optional.of("2026-04-20T15:36:54Z"), DocumentMetadata.date("2026-04-20T15:36:54Z"));
		assertEquals(Optional.of("2026-04-20T15:36:54+01:00"), DocumentMetadata.date("2026-04-20T15:36:54.5+01:00"));
		assertEquals(Optional.of("2026-04-20"), DocumentMetadata.date("2026-04-20"));
		// Office's unknown date
		assertEquals(Optional.empty(), DocumentMetadata.date("1601-01-01T00:00:00Z"));
		assertEquals(Optional.empty(), DocumentMetadata.date("yesterday"));
	}
}
