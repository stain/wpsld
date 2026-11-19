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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

public class TestWebCrate {
	private static WebCrate of(String source) {
		return WebCrate.of(URI.create(source), Optional.empty()).orElseThrow();
	}

	@Test
	public void metadataFileIsAttachedToItsFolder() {
		WebCrate crate = of("http://example.com/crate1/ro-crate-metadata.json");
		assertTrue(crate.attached());
		assertEquals(URI.create("http://example.com/crate1/"), crate.base());
		assertEquals(Optional.of(URI.create("http://example.com/crate1/file.txt")), crate.resolve("file.txt"));
		assertEquals(Optional.of(URI.create("http://example.com/crate1/data/")), crate.resolve("data/"));
		assertEquals(Optional.of(URI.create("http://example.com/crate1/")), crate.resolve("./"));
		// Encoded as written
		assertEquals(Optional.of(URI.create("http://example.com/crate1/my%20file.txt")), crate.resolve("my%20file.txt"));
		assertTrue(of("https://example.com/c/ro-crate-metadata.jsonld").attached());
		assertTrue(of("https://example.com/ro-crate-metadata.json").attached());
	}

	@Test
	public void folderIsAttached() {
		WebCrate crate = of("https://example.com/crate1/");
		assertTrue(crate.attached());
		assertEquals(URI.create("https://example.com/crate1/"), crate.base());
	}

	@Test
	public void otherNamesAndQueriesAreDetached() {
		WebCrate named = of("https://example.com/crates/my-ro-crate-metadata.json");
		assertFalse(named.attached());
		// Still resolved against the document, to lead somewhere
		assertEquals(Optional.of(URI.create("https://example.com/crates/file.txt")), named.resolve("file.txt"));
		WebCrate api = of("https://data.example.com/api/object?id=abc&meta=original");
		assertFalse(api.attached());
		// A query with the metadata file's name is still a service
		assertFalse(of("https://example.com/ro-crate-metadata.json?version=2").attached());
	}

	@Test
	public void contextBaseTakesPlaceOfTheAddress() {
		WebCrate crate = WebCrate.of(URI.create("https://example.com/a/ro-crate-metadata.json"),
				Optional.of("https://files.example.org/b/")).orElseThrow();
		assertTrue(crate.attached());
		assertEquals(URI.create("https://files.example.org/b/"), crate.base());
		assertEquals(Optional.of(URI.create("https://files.example.org/b/x.csv")), crate.resolve("x.csv"));
		// A relative @base is against the address
		assertEquals(URI.create("https://example.com/other/"), WebCrate
				.of(URI.create("https://example.com/a/ro-crate-metadata.json"), Optional.of("../other/")).orElseThrow().base());
	}

	@Test
	public void notEverythingLeadsSomewhere() {
		WebCrate crate = of("http://example.com/crate1/ro-crate-metadata.json");
		assertEquals(Optional.empty(), crate.resolve("#alice"));
		assertEquals(Optional.empty(), crate.resolve("_:b0"));
		assertEquals(Optional.empty(), crate.resolve(""));
		assertEquals(Optional.empty(), crate.resolve("https://orcid.org/0000-0002-1825-0097"));
		assertEquals(Optional.empty(), crate.resolve("urn:uuid:e47e41d9-f924-4c07-bc90-97e7ed34fe35"));
		// Outside the folder, which an attached crate can't have
		assertEquals(Optional.empty(), crate.resolve("../other/file.txt"));
		assertEquals(Optional.empty(), crate.resolve("/elsewhere"));
		assertEquals(Optional.empty(), crate.resolve("a b"));
		// Inside, however it is written
		assertEquals(Optional.of(URI.create("http://example.com/crate1/b.txt")), crate.resolve("a/../b.txt"));
	}

	@Test
	public void notWebCratesAreNone() {
		assertEquals(Optional.empty(), WebCrate.of(URI.create("file:///tmp/crate/ro-crate-metadata.json"), Optional.empty()));
	}

	private static final String CRATE = """
			{"@context": "https://w3id.org/ro/crate/1.2/context",
			 "@graph": [
			  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
			   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.2"}},
			  {"@id": "./", "@type": "Dataset", "name": "A crate", "author": {"@id": "#alice"},
			   "hasPart": [{"@id": "data/"}, {"@id": "README.md"}, {"@id": "https://example.org/x.csv"}]},
			  {"@id": "data/", "@type": "Dataset", "hasPart": {"@id": "data/counts.csv"}},
			  {"@id": "data/counts.csv", "@type": "File"},
			  {"@id": "README.md", "@type": "File"},
			  {"@id": "https://example.org/x.csv", "@type": "File"},
			  {"@id": "#alice", "@type": "Person", "name": "Alice"}]}""";

	private static Crate crate() {
		return new CrateReader().read(new ByteArrayInputStream(CRATE.getBytes(StandardCharsets.UTF_8)), "test")
				.crate().orElseThrow();
	}

	@Test
	public void relativeEntitiesAreThoseLeadingSomewhere() {
		WebCrate web = of("http://example.com/crate1/ro-crate-metadata.json");
		assertEquals(List.of("data/", "data/counts.csv", "README.md"),
				crate().relativeEntities(web).stream().map(Entity::getId).toList());
	}

	@Test
	public void absolutizeKeepsLocalAndAbsoluteIds() {
		Crate crate = crate();
		WebCrate web = of("http://example.com/crate1/ro-crate-metadata.json");
		Map<String, String> renamed = crate.absolutize(web, false);
		assertEquals(Map.of("data/", "http://example.com/crate1/data/", "data/counts.csv",
				"http://example.com/crate1/data/counts.csv", "README.md", "http://example.com/crate1/README.md"), renamed);
		// References follow, others stay
		assertEquals(List.of("http://example.com/crate1/data/", "http://example.com/crate1/README.md",
				"https://example.org/x.csv"), crate.getRoot().getAll("hasPart").stream().map(v -> v.path("@id").asText()).toList());
		assertEquals("./", crate.getRoot().getId());
		assertTrue(crate.get("#alice").isPresent());
		assertEquals(List.of(), crate.relativeEntities(web));
		// Nothing more to do
		assertEquals(Map.of(), crate.absolutize(web, false));
	}

	@Test
	public void absolutizeTheRootForADetachedCrate() {
		Crate crate = crate();
		crate.absolutize(of("http://example.com/crate1/ro-crate-metadata.json"), true);
		assertEquals("http://example.com/crate1/", crate.getRoot().getId());
		// The descriptor is about it
		assertEquals("http://example.com/crate1/", crate.toJson().get("@graph").get(0).get("about").get("@id").asText());
	}
}
