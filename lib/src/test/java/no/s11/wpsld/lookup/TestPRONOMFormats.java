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

import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.lookup.PRONOMFormats.Format;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

public class TestPRONOMFormats {
	@Test
	public void bundled() {
		PRONOMFormats pronom = PRONOMFormats.bundled();
		assertEquals("125", pronom.getVersion());
		assertEquals(2571, pronom.getFormats().size());
		Format pdf = pronom.get("fmt/19").orElseThrow();
		assertEquals(new Format("fmt/19", "Acrobat PDF 1.5 - Portable Document Format", Optional.of("1.5"),
				List.of("application/pdf"), List.of("pdf")), pdf);
		assertEquals("https://www.nationalarchives.gov.uk/PRONOM/fmt/19", pdf.iri());
		assertEquals("https://pronom.nationalarchives.gov.uk/fmt/19", pdf.page());
	}

	@Test
	public void search() {
		PRONOMFormats pronom = PRONOMFormats.bundled();
		// An extension, with or without its dot, or a media type
		assertEquals("x-fmt/18", pronom.search(".csv").get(0).puid());
		assertEquals("x-fmt/18", pronom.search("text/csv").get(0).puid());
		assertEquals("fmt/1149", pronom.search("md").get(0).puid());
		// Words of the name and version, in any order
		List<String> pdf = pronom.search("pdf 1.7").stream().map(Format::puid).toList();
		assertTrue(pdf.toString(), pdf.contains("fmt/276"));
		assertFalse(pdf.toString(), pdf.contains("fmt/19"));
		assertEquals("fmt/19", pronom.search("fmt/19").get(0).puid());
		assertEquals(List.of(), pronom.search("no such format"));
	}

	@Test
	public void mediaTypes() {
		PRONOMFormats pronom = PRONOMFormats.bundled();
		assertEquals(List.of("x-fmt/18"), pronom.withMediaType("TEXT/CSV").stream().map(Format::puid).toList());
		// PDF has many versions, so a media type doesn't say which
		assertTrue(pronom.withMediaType("application/pdf").size() > 10);
	}

	@Test
	public void puidOf() {
		assertEquals(Optional.of("fmt/19"), PRONOMFormats.puidOf("https://www.nationalarchives.gov.uk/PRONOM/fmt/19"));
		assertEquals(Optional.of("x-fmt/263"), PRONOMFormats.puidOf("http://www.nationalarchives.gov.uk/PRONOM/x-fmt/263"));
		assertEquals(Optional.of("fmt/19"), PRONOMFormats.puidOf("https://pronom.nationalarchives.gov.uk/fmt/19.json"));
		assertEquals(Optional.of("fmt/19"), PRONOMFormats.puidOf("https://www.nationalarchives.gov.uk/pronom/fmt/19"));
		assertEquals(Optional.empty(), PRONOMFormats.puidOf("https://www.nationalarchives.gov.uk/fmt/19"));
		assertEquals(Optional.empty(), PRONOMFormats.puidOf("text/csv"));
	}

	@Test
	public void addTo() {
		Entity file = new Entity("data.csv", "File");
		Format csv = PRONOMFormats.bundled().get("x-fmt/18").orElseThrow();
		assertTrue(PRONOMFormats.addTo(file, csv));
		assertEquals(List.of(TextNode.valueOf("text/csv"), reference(csv.iri())), file.getAll("encodingFormat"));
		assertFalse(PRONOMFormats.addTo(file, csv));
		// A media type it has is kept, not added to
		Entity other = new Entity("data.txt", "File");
		other.set("encodingFormat", Optional.of(TextNode.valueOf("text/plain")));
		PRONOMFormats.addTo(other, csv);
		assertEquals(List.of(TextNode.valueOf("text/plain"), reference(csv.iri())), other.getAll("encodingFormat"));
	}

	/**
	 * Edit > Repair Crate: PRONOM addresses as copied from the new site, a
	 * media type only one format has, and formats not described.
	 */
	@Test
	public void repair() {
		Crate crate = new Crate();
		Entity table = crate.add("File");
		table.set("encodingFormat", Optional.of(TextNode.valueOf("text/csv")));
		Entity paper = crate.add("File");
		paper.set("encodingFormat", Optional.of(TextNode.valueOf("application/pdf")));
		Entity archive = crate.add("File");
		archive.setAll("encodingFormat", List.of(TextNode.valueOf("application/zip"),
				reference("https://pronom.nationalarchives.gov.uk/x-fmt/263")));
		Entity zip = crate.add("WebPage");
		crate.rename(zip, "http://www.nationalarchives.gov.uk/PRONOM/x-fmt/263");

		List<String> done = PRONOMFormats.bundled().repair(crate);
		assertFalse(done.isEmpty());
		assertEquals(List.of(TextNode.valueOf("text/csv"),
				reference("https://www.nationalarchives.gov.uk/PRONOM/x-fmt/18")), table.getAll("encodingFormat"));
		// Which PDF isn't known
		assertEquals(List.of(TextNode.valueOf("application/pdf")), paper.getAll("encodingFormat"));
		assertEquals(List.of(TextNode.valueOf("application/zip"),
				reference("https://www.nationalarchives.gov.uk/PRONOM/x-fmt/263")), archive.getAll("encodingFormat"));
		assertSame(zip, crate.get("https://www.nationalarchives.gov.uk/PRONOM/x-fmt/263").orElseThrow());
		assertEquals(List.of("WebPage", "Standard"), zip.getTypes());
		assertEquals("ZIP Format", zip.getLabel());
		Entity csv = crate.get("https://www.nationalarchives.gov.uk/PRONOM/x-fmt/18").orElseThrow();
		assertEquals("Comma Separated Values", csv.getLabel());
		assertEquals(List.of(), PRONOMFormats.bundled().repair(crate));
	}

	private static JsonNode reference(String id) {
		return JsonNodeFactory.instance.objectNode().put("@id", id);
	}
}
