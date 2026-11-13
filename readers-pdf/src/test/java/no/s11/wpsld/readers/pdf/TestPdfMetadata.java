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
package no.s11.wpsld.readers.pdf;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.junit.Test;

import no.s11.wpsld.lookup.EmbeddedMetadata.Found;
import no.s11.wpsld.lookup.EmbeddedMetadata.Kind;

public class TestPdfMetadata {
	/** A PDF's document information and XMP, each value once */
	@Test
	public void pdf() throws Exception {
		Path file = Files.createTempDirectory("pdf").resolve("report.pdf");
		try (PDDocument pdf = new PDDocument()) {
			pdf.addPage(new PDPage());
			PDDocumentInformation info = pdf.getDocumentInformation();
			info.setTitle("Report");
			info.setAuthor("Ada Lovelace; Charles Babbage");
			info.setSubject("Engines");
			info.setKeywords("engines, history");
			Calendar created = Calendar.getInstance(TimeZone.getTimeZone("GMT+01:00"));
			created.clear();
			created.set(2026, Calendar.APRIL, 20, 15, 36, 54);
			info.setCreationDate(created);
			PDMetadata xmp = new PDMetadata(pdf);
			xmp.importXMPMetadata("""
					<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
					<rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/"
					 xmlns:cc="http://creativecommons.org/ns#">
					<dc:title><rdf:Alt><rdf:li xml:lang="x-default">Report</rdf:li></rdf:Alt></dc:title>
					<cc:license rdf:resource="https://creativecommons.org/licenses/by/4.0/"/>
					</rdf:Description></rdf:RDF></x:xmpmeta>""".getBytes(StandardCharsets.UTF_8));
			pdf.getDocumentCatalog().setMetadata(xmp);
			pdf.save(file.toFile());
		}
		PdfMetadata reader = new PdfMetadata();
		assertTrue(reader.reads(file, Files.readAllBytes(file)));
		List<Found> found = reader.read(file);
		assertEquals(List.of("name Report", "description Engines", "author Ada Lovelace", "author Charles Babbage",
				"keywords engines, history", "dateCreated 2026-04-20T15:36:54+01:00",
				"license https://creativecommons.org/licenses/by/4.0/"),
				// The XMP's title, the same as the document information's: once
				found.stream().map(f -> f.property() + " " + f.value()).toList());
		assertEquals(Kind.PERSON, found.get(2).kind());
		assertEquals("PDF document information Author", found.get(2).source());
		// Not a PDF
		assertFalse(reader.reads(file, "not".getBytes(StandardCharsets.UTF_8)));
	}
}
