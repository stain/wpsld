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
		assertEquals(Optional.of(URI.create("http://example.com/crate1/")), crate.base());
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
		assertEquals(Optional.of(URI.create("https://example.com/crate1/")), crate.base());
	}

	@Test
	public void otherNamesAndQueriesAreDetached() {
		WebCrate named = of("https://example.com/crates/my-ro-crate-metadata.json");
		assertFalse(named.attached());
		// No folder, so no base: not resolved against the document, which is no telling where files are
		assertEquals(Optional.empty(), named.base());
		assertEquals(Optional.empty(), named.resolve("file.txt"));
		WebCrate api = of("https://data.example.com/api/object?id=abc&meta=original");
		assertFalse(api.attached());
		assertEquals(Optional.empty(), api.resolve("Calibration.Trial26.enf"));
		// A query with the metadata file's name is still a service
		assertFalse(of("https://example.com/ro-crate-metadata.json?version=2").attached());
	}

	@Test
	public void contextBaseTakesPlaceOfTheAddress() {
		WebCrate crate = WebCrate.of(URI.create("https://example.com/a/ro-crate-metadata.json"),
				Optional.of("https://files.example.org/b/")).orElseThrow();
		assertTrue(crate.attached());
		assertEquals(Optional.of(URI.create("https://files.example.org/b/")), crate.base());
		assertEquals(Optional.of(URI.create("https://files.example.org/b/x.csv")), crate.resolve("x.csv"));
		// A relative @base is against the address
		assertEquals(Optional.of(URI.create("https://example.com/other/")), WebCrate
				.of(URI.create("https://example.com/a/ro-crate-metadata.json"), Optional.of("../other/")).orElseThrow().base());
		// The one thing that gives a detached crate a base
		WebCrate detached = WebCrate.of(URI.create("https://example.com/api/crate?id=1"),
				Optional.of("https://files.example.org/b/")).orElseThrow();
		assertFalse(detached.attached());
		assertEquals(Optional.of(URI.create("https://files.example.org/b/x.csv")), detached.resolve("x.csv"));
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
		// A file name with a space, written as it is
		assertEquals(Optional.of(URI.create("http://example.com/crate1/a%20b")), crate.resolve("a b"));
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

	/** Detached with relative files and no @base: semi-detached, there is no telling where they are */
	@Test
	public void semiDetachedHasRelativeFilesWithNoBase() {
		Crate crate = crate();
		WebCrate api = of("https://data.example.com/api/export?id=abc");
		assertEquals(List.of("data/", "data/counts.csv", "README.md"),
				crate.unanchoredDataEntities(api).stream().map(Entity::getId).toList());
		// Nothing to make absolute, as nothing leads anywhere
		assertEquals(List.of(), crate.relativeEntities(api));
		assertEquals(Map.of(), crate.absolutize(api, false));
		// Attached, or with a base, they are anchored
		assertEquals(List.of(), crate.unanchoredDataEntities(of("http://example.com/crate1/ro-crate-metadata.json")));
		assertEquals(List.of(), crate.unanchoredDataEntities(WebCrate.of(URI.create("https://data.example.com/api/export?id=abc"),
				Optional.of("https://files.example.org/")).orElseThrow()));
	}

	@Test
	public void relativeReferences() {
		assertTrue(WebCrate.isRelative("data/counts.csv"));
		assertTrue(WebCrate.isRelative("./"));
		assertTrue(WebCrate.isRelative("van der Have, Tuur"));
		assertFalse(WebCrate.isRelative("#alice"));
		assertFalse(WebCrate.isRelative("_:b0"));
		assertFalse(WebCrate.isRelative("https://example.org/x"));
		assertFalse(WebCrate.isRelative(""));
	}

	private static Crate crateWithIdentifiers(String id, String identifier) {
		return new CrateReader().read(new ByteArrayInputStream(("""
				{"@context": "https://w3id.org/ro/crate/1.1/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "%s"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.1"}},
				  {"@id": "%s", "@type": "Dataset", "name": "D", "identifier": "%s",
				   "cite-as": {"@id": "%s"}}]}""".formatted(id, id, identifier, identifier)).getBytes(StandardCharsets.UTF_8)),
				"test").crate().orElseThrow();
	}

	private static List<URI> roots(WebCrate web, Crate crate) {
		return web.suggestedRoots(crate.getRoot()).stream().map(WebCrate.Root::address).toList();
	}

	/** Never an address earlier in the redirections, such as the DOI that was asked for, nor a resolver's */
	@Test
	public void suggestedRootsAreNotEarlierRedirections() {
		Crate crate = crateWithIdentifiers("./", "https://doi.org/10.1/X");
		URI doi = URI.create("https://doi.org/10.1/X");
		URI landing = URI.create("https://rdr.example.org/dataset.xhtml?persistentId=doi:10.1/X");
		URI export = URI.create("https://rdr.example.org/api/datasets/export?exporter=x&persistentId=doi:10.1/X");
		WebCrate web = WebCrate.of(new CrateRetriever.Retrieved(export, new byte[0], List.of("Link header"), doi,
				Optional.of(landing)), Optional.empty()).orElseThrow();
		// The landing page, and not the DOI (a resolver, and what was asked); no folder, as the export has a query
		assertEquals(List.of(landing), roots(web, crate));
		assertEquals("the landing page the metadata was found from", web.suggestedRoots(crate.getRoot()).get(0).why());
		// What the crate says itself, as long as it is not a resolver's address, or what was asked for
		Crate own = crateWithIdentifiers("https://data.example.org/dataset/1/", "https://data.example.org/dataset/1");
		assertEquals(List.of(landing, URI.create("https://data.example.org/dataset/1/"),
				URI.create("https://data.example.org/dataset/1")), roots(web, own));
		// No landing page: the data came from the address asked for
		WebCrate direct = of("https://rdr.example.org/api/export?id=1");
		assertEquals(List.of(), roots(direct, crate));
		// Beside a metadata document with another name, a folder it could be attached in
		WebCrate named = of("https://example.com/crates/my-ro-crate-metadata.json");
		assertEquals(List.of(URI.create("https://example.com/crates/")), roots(named, crate));
	}

	@Test
	public void adoptedBase() {
		WebCrate api = of("https://data.example.com/api/export?id=abc");
		WebCrate found = api.withBase(URI.create("https://files.example.org/a/"), "found");
		assertFalse(found.attached());
		assertEquals(Optional.of(URI.create("https://files.example.org/a/x.csv")), found.resolve("x.csv"));
		assertTrue(found.reason().endsWith("; found"));
	}
}
