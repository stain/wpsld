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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;

import no.s11.wpsld.lookup.EmbeddedMetadata;
import no.s11.wpsld.lookup.EmbeddedMetadata.Found;
import no.s11.wpsld.lookup.EmbeddedMetadata.Kind;
import no.s11.wpsld.lookup.MetadataReader;

/**
 * A PDF's own metadata, as {@link EmbeddedMetadata} reads other files':
 * its document information (title, subject, author, keywords, when created
 * and changed) and its XMP, also if compressed. With PDFBox, in a module
 * of its own as it is a library of its own and needs java.desktop. Not a
 * PDF that needs a password to open.
 */
public final class PdfMetadata implements MetadataReader {
	private static final byte[] PDF = { '%', 'P', 'D', 'F', '-' };

	@Override
	public String name() {
		return "PDF (PDFBox)";
	}

	@Override
	public boolean reads(Path file, byte[] head) {
		return head.length >= PDF.length && Arrays.equals(head, 0, PDF.length, PDF, 0, PDF.length);
	}

	/** What a PDF says about itself; what was read, if it is broken or needs a password */
	@Override
	public List<Found> read(Path file) throws IOException {
		List<Found> found = new ArrayList<>();
		try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
			PDDocumentInformation info = pdf.getDocumentInformation();
			String source = "PDF document information ";
			text(info.getTitle()).ifPresent(t -> found.add(new Found("name", t, Kind.TEXT, source + "Title")));
			text(info.getSubject())
					.ifPresent(t -> found.add(new Found("description", t, Kind.TEXT, source + "Subject")));
			text(info.getAuthor()).ifPresent(t -> EmbeddedMetadata.people(t)
					.forEach(person -> found.add(new Found("author", person, Kind.PERSON, source + "Author"))));
			text(info.getKeywords())
					.ifPresent(t -> found.add(new Found("keywords", t, Kind.KEYWORDS, source + "Keywords")));
			date(info.getCreationDate())
					.ifPresent(d -> found.add(new Found("dateCreated", d, Kind.DATE, source + "CreationDate")));
			date(info.getModificationDate())
					.ifPresent(d -> found.add(new Found("dateModified", d, Kind.DATE, source + "ModDate")));
			PDMetadata metadata = pdf.getDocumentCatalog().getMetadata();
			if (metadata != null) {
				try (InputStream xmp = metadata.createInputStream()) {
					found.addAll(EmbeddedMetadata.xmp(xmp, "PDF XMP "));
				}
			}
		} catch (IOException | RuntimeException e) {
			if (found.isEmpty()) {
				throw e;
			}
			// Broken further on: what was read so far
		}
		// Each value once, e.g. the title in both the information and the XMP
		java.util.Set<List<String>> seen = new java.util.HashSet<>();
		return found.stream().filter(f -> seen.add(List.of(f.property(), f.value()))).toList();
	}

	private static Optional<String> text(String text) {
		return Optional.ofNullable(text).map(String::strip).filter(t -> !t.isEmpty());
	}

	/** As ISO 8601, to the second, with the PDF's offset */
	private static Optional<String> date(Calendar calendar) {
		if (calendar == null || calendar.get(Calendar.YEAR) < 1900) {
			return Optional.empty();
		}
		return Optional.of(calendar.toInstant().atZone(calendar.getTimeZone().toZoneId()).toOffsetDateTime()
				.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
	}
}
