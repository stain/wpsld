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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import no.s11.wpsld.test.Damage;
import no.s11.wpsld.test.Damaged;

/** The built-in readers on damaged files (see {@link Damage}) */
@Category(Damaged.class)
public class TestDamagedFiles {
	private static final String XMP = """
			<?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
			<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
			<rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/"
			 xmlns:pdf="http://ns.adobe.com/pdf/1.3/" pdf:Producer="LibreOffice">
			<dc:title><rdf:Alt><rdf:li xml:lang="x-default">Report</rdf:li></rdf:Alt></dc:title>
			<dc:creator><rdf:Seq><rdf:li>Ada Lovelace</rdf:li></rdf:Seq></dc:creator>
			</rdf:Description></rdf:RDF></x:xmpmeta>
			<?xpacket end="w"?>""";

	@Test
	public void office() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry(EmbeddedMetadata.OFFICE_CORE));
			zip.write("""
					<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
					 xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/">
					<dc:title>Week 8 workshop</dc:title><dc:creator>Josiah Carberry</dc:creator>
					<dcterms:created>2026-04-20T15:36:54Z</dcterms:created></cp:coreProperties>"""
					.getBytes(StandardCharsets.UTF_8));
			zip.putNextEntry(new ZipEntry(EmbeddedMetadata.OPEN_DOCUMENT_META));
			zip.write("""
					<office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
					 xmlns:dc="http://purl.org/dc/elements/1.1/"><office:meta><dc:title>Notes</dc:title>
					</office:meta></office:document-meta>""".getBytes(StandardCharsets.UTF_8));
		}
		Damage.readAll(EmbeddedMetadata.builtIn(), "workshop.docx", bytes.toByteArray(), 1);
	}

	@Test
	public void svg() throws Exception {
		Damage.readAll(EmbeddedMetadata.builtIn(), "drawing.svg", """
				<?xml version="1.0" encoding="UTF-8"?>
				<svg xmlns="http://www.w3.org/2000/svg" xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
				 xmlns:cc="http://creativecommons.org/ns#" xmlns:dc="http://purl.org/dc/elements/1.1/"
				 width="210mm" height="297mm"><title>Route map</title>
				 <metadata><rdf:RDF><cc:Work rdf:about="">
				  <dc:creator><cc:Agent><dc:title>Ada Lovelace</dc:title></cc:Agent></dc:creator>
				  <cc:license rdf:resource="http://creativecommons.org/licenses/by/4.0/"/>
				 </cc:Work></rdf:RDF></metadata><rect width="10" height="10"/></svg>"""
				.getBytes(StandardCharsets.UTF_8), 2);
	}

	@Test
	public void pdfAndEps() throws Exception {
		Damage.readAll(EmbeddedMetadata.builtIn(), "report.pdf",
				("%PDF-1.7\n5 0 obj\n<</Type/Metadata/Subtype/XML>>\nstream\n" + XMP + "\nendstream\nendobj\n%%EOF\n")
						.getBytes(StandardCharsets.UTF_8), 3);
		Damage.readAll(EmbeddedMetadata.builtIn(), "figure.eps",
				("%!PS-Adobe-3.0 EPSF-3.0\n%%BeginMetadata\n" + XMP).getBytes(StandardCharsets.UTF_8), 4);
	}
}
