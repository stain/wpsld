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
package no.s11.wpsld.crate;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.crate.SpdxLicenses.License;

public class TestSpdxLicenses {
	@Test
	public void bundled() throws Exception {
		SpdxLicenses spdx = SpdxLicenses.bundled();
		assertEquals("3.29.0", spdx.getVersion());
		assertEquals(740, spdx.getLicenses().size());
		License ccBy = spdx.get("cc-by-4.0").orElseThrow();
		assertEquals(new License("CC-BY-4.0", "Creative Commons Attribution 4.0 International", false, true, false,
				List.of("https://creativecommons.org/licenses/by/4.0/legalcode")), ccBy);
		assertEquals("http://spdx.org/licenses/CC-BY-4.0", ccBy.iri());
		assertEquals("https://spdx.org/licenses/CC-BY-4.0.html", ccBy.page());
		assertTrue(spdx.get("Apache-2.0").orElseThrow().osiApproved());
		assertTrue(spdx.get("GPL-2.0").orElseThrow().deprecated());
		// The source.json and README of the bundled file agree on its source
		JsonSource source = JsonSource.read();
		assertEquals(source.licences(), spdx.getLicenses().size());
	}

	/** source.json, as the update script writes it */
	private record JsonSource(String version, int licences) {
		static JsonSource read() throws Exception {
			try (InputStream in = SpdxLicenses.class.getResourceAsStream("/spdx-licenses/source.json")) {
				var json = new ObjectMapper().readTree(in);
				return new JsonSource(json.path("version").asText(), json.path("licences").asInt());
			}
		}
	}

	@Test
	public void search() {
		SpdxLicenses spdx = SpdxLicenses.bundled();
		// The identifier itself first, then those starting with it
		List<String> apache = spdx.search("apache-2.0").stream().map(License::id).toList();
		assertEquals("Apache-2.0", apache.get(0));
		// Words of the identifier and name, in any order
		List<String> ccBy = spdx.search("attribution 4.0 creative").stream().map(License::id).toList();
		assertTrue(ccBy.toString(), ccBy.contains("CC-BY-4.0"));
		assertTrue(ccBy.toString(), ccBy.contains("CC-BY-SA-4.0"));
		assertFalse(ccBy.toString(), ccBy.contains("CC-BY-3.0"));
		// Deprecated identifiers after the others
		List<License> gpl = spdx.search("gpl-2.0");
		assertEquals("GPL-2.0", gpl.get(0).id());
		assertTrue(gpl.indexOf(spdx.get("GPL-2.0-only").orElseThrow()) < gpl
				.indexOf(spdx.get("GPL-2.0+").orElseThrow()));
		assertEquals(740, spdx.search(" ").size());
		assertEquals(List.of(), spdx.search("no such licence"));
	}

	@Test
	public void idOf() {
		assertEquals(Optional.of("CC-BY-4.0"), SpdxLicenses.idOf("http://spdx.org/licenses/CC-BY-4.0"));
		assertEquals(Optional.of("CC-BY-4.0"), SpdxLicenses.idOf("https://spdx.org/licenses/CC-BY-4.0.html"));
		assertEquals(Optional.of("GPL-2.0+"), SpdxLicenses.idOf("http://spdx.org/licenses/GPL-2.0+"));
		assertEquals(Optional.empty(), SpdxLicenses.idOf("https://creativecommons.org/licenses/by/4.0/"));
	}

	@Test
	public void describe() {
		Crate crate = new Crate();
		Entity licence = crate.add("Thing");
		License ccBy = SpdxLicenses.bundled().get("CC-BY-4.0").orElseThrow();
		Entity identifier = SpdxLicenses.describe(licence, ccBy, crate::get, id -> {
			Entity created = crate.add("PropertyValue");
			created.setId(id);
			return created;
		});
		assertEquals(List.of("CreativeWork"), licence.getTypes());
		assertEquals("Creative Commons Attribution 4.0 International", licence.getLabel());
		assertEquals("#spdx-CC-BY-4.0", identifier.getId());
		assertEquals(List.of("PropertyValue"), identifier.getTypes());
		assertEquals("CC-BY-4.0", identifier.get("value").orElseThrow().asText());
		assertEquals("SPDX", identifier.get("name").orElseThrow().asText());
		assertEquals(SpdxLicenses.PROPERTY_ID, identifier.get("propertyID").orElseThrow().asText());
		assertEquals("https://spdx.org/licenses/CC-BY-4.0.html", identifier.get("url").orElseThrow().asText());
		assertEquals("#spdx-CC-BY-4.0", licence.get("identifier").orElseThrow().path("@id").asText());
		// Again: the same PropertyValue, linked once
		assertSame(identifier, SpdxLicenses.describe(licence, ccBy, crate::get, id -> {
			throw new AssertionError("Not again");
		}));
		assertEquals(1, licence.getAll("identifier").size());
	}

	/**
	 * Edit > Repair Crate: SPDX licences as people copy them from the browser,
	 * or as RO-Crate 1.1 crates wrote them, become wpsld's form.
	 */
	@Test
	public void repair() {
		Crate crate = new Crate();
		Entity root = crate.getRoot();
		root.set("license", Optional.of(TextNode.valueOf("https://spdx.org/licenses/Apache-2.0.html")));
		Entity data = crate.add("File");
		data.set("license", Optional.of(TextNode.valueOf("CC-BY-4.0")));
		Entity code = crate.add("File");
		code.set("license", Optional.of(reference("https://spdx.org/licenses/MIT")));
		Entity mit = crate.add("CreativeWork");
		crate.rename(mit, "https://spdx.org/licenses/MIT");
		mit.set("name", Optional.of(TextNode.valueOf("MIT")));
		Entity docs = crate.add("File");
		docs.setAll("license", List.of(reference("http://spdx.org/licenses/mit"), TextNode.valueOf("All rights reserved")));

		List<String> done = SpdxLicenses.bundled().repair(crate);
		assertFalse(done.isEmpty());
		assertEquals("http://spdx.org/licenses/Apache-2.0", root.get("license").orElseThrow().path("@id").asText());
		assertEquals("http://spdx.org/licenses/CC-BY-4.0", data.get("license").orElseThrow().path("@id").asText());
		assertEquals("http://spdx.org/licenses/MIT", code.get("license").orElseThrow().path("@id").asText());
		// Text that isn't an SPDX licence stays
		assertEquals(List.of(reference("http://spdx.org/licenses/MIT"), TextNode.valueOf("All rights reserved")),
				docs.getAll("license"));
		// One MIT, keeping its own name, with its SPDX identifier
		assertSame(mit, crate.get("http://spdx.org/licenses/MIT").orElseThrow());
		assertEquals("MIT", mit.getLabel());
		assertEquals("#spdx-MIT", mit.get("identifier").orElseThrow().path("@id").asText());
		assertTrue(crate.get("https://spdx.org/licenses/MIT").isEmpty());
		// The licences only referred to are added, named as SPDX names them
		Entity apache = crate.get("http://spdx.org/licenses/Apache-2.0").orElseThrow();
		assertEquals(List.of("CreativeWork"), apache.getTypes());
		assertEquals("Apache License 2.0", apache.getLabel());
		assertEquals("Apache-2.0", crate.get("#spdx-Apache-2.0").orElseThrow().get("value").orElseThrow().asText());
		// Nothing more to do
		assertEquals(List.of(), SpdxLicenses.bundled().repair(crate));
	}

	@Test
	public void byLink() {
		SpdxLicenses spdx = SpdxLicenses.bundled();
		// Creative Commons' readable page, though SPDX lists …/legalcode
		assertEquals("CC-BY-4.0", spdx.byLink("https://creativecommons.org/licenses/by/4.0/").orElseThrow().id());
		assertEquals("CC-BY-4.0", spdx.byLink("http://creativecommons.org/licenses/by/4.0/deed.en").orElseThrow().id());
		assertEquals("CC0-1.0", spdx.byLink("https://creativecommons.org/publicdomain/zero/1.0/").orElseThrow().id());
		assertEquals("MIT", spdx.byLink("https://opensource.org/license/mit").orElseThrow().id());
		// Listed for both GPL-3.0-only and GPL-3.0-or-later: can't tell which
		assertEquals(Optional.empty(), spdx.byLink("https://www.gnu.org/licenses/gpl-3.0-standalone.html"));
		assertEquals(Optional.empty(), spdx.byLink("https://example.org/licence"));
	}

	/**
	 * Repair Crate: a licence known by Creative Commons' own address, as in
	 * RO-Crate's examples, becomes the SPDX licence, keeping that address as
	 * its URL; as text too.
	 */
	@Test
	public void repairLicenceLinks() {
		Crate crate = new Crate();
		Entity root = crate.getRoot();
		root.set("license", Optional.of(reference("https://creativecommons.org/licenses/by/4.0/")));
		Entity cc = crate.add("CreativeWork");
		crate.rename(cc, "https://creativecommons.org/licenses/by/4.0/");
		cc.set("name", Optional.of(TextNode.valueOf("CC BY 4.0")));
		Entity data = crate.add("File");
		data.set("license", Optional.of(TextNode.valueOf("https://creativecommons.org/publicdomain/zero/1.0/")));
		Entity code = crate.add("File");
		code.set("license", Optional.of(reference("https://www.gnu.org/licenses/gpl-3.0-standalone.html")));

		SpdxLicenses.bundled().repair(crate);
		assertEquals("http://spdx.org/licenses/CC-BY-4.0", root.get("license").orElseThrow().path("@id").asText());
		assertSame(cc, crate.get("http://spdx.org/licenses/CC-BY-4.0").orElseThrow());
		assertEquals("CC BY 4.0", cc.getLabel());
		assertEquals("https://creativecommons.org/licenses/by/4.0/", cc.get("url").orElseThrow().asText());
		assertEquals("#spdx-CC-BY-4.0", cc.get("identifier").orElseThrow().path("@id").asText());
		assertEquals("http://spdx.org/licenses/CC0-1.0", data.get("license").orElseThrow().path("@id").asText());
		// GPL-3.0-only or -or-later? Left as it was
		assertEquals("https://www.gnu.org/licenses/gpl-3.0-standalone.html",
				code.get("license").orElseThrow().path("@id").asText());
		assertEquals(List.of(), SpdxLicenses.bundled().repair(crate));
	}

	private static JsonNode reference(String id) {
		return JsonNodeFactory.instance.objectNode().put("@id", id);
	}
}
