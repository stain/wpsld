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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import no.s11.wpsld.test.Damage;
import no.s11.wpsld.test.Damaged;

/** PDFBox on damaged PDFs (see {@link Damage}) */
@Category(Damaged.class)
public class TestDamagedPdfs {
	@Test
	public void pdf() throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (PDDocument pdf = new PDDocument()) {
			pdf.addPage(new PDPage());
			pdf.getDocumentInformation().setTitle("Report");
			pdf.getDocumentInformation().setAuthor("Ada Lovelace");
			PDMetadata xmp = new PDMetadata(pdf);
			xmp.importXMPMetadata("""
					<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
					<rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/">
					<dc:title><rdf:Alt><rdf:li xml:lang="x-default">Report</rdf:li></rdf:Alt></dc:title>
					</rdf:Description></rdf:RDF></x:xmpmeta>""".getBytes(StandardCharsets.UTF_8));
			pdf.getDocumentCatalog().setMetadata(xmp);
			pdf.save(bytes);
		}
		Damage.readAll(List.of(new PdfMetadata()), "report.pdf", bytes.toByteArray(), 1);
	}
}
