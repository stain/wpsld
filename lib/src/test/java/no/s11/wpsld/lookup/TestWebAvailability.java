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

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.net.httpserver.HttpServer;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.crate.WebCrate;
import no.s11.wpsld.masp.ProfileCache;

/** Against a web server of the test's own, offline */
public class TestWebAvailability {
	private HttpServer server;
	private String base;

	@Before
	public void serve() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/ok", exchange -> {
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.createContext("/moved", exchange -> {
			exchange.getResponseHeaders().add("Location", base + "/ok");
			exchange.sendResponseHeaders(301, -1);
			exchange.close();
		});
		// Allows only GET, as some servers do
		server.createContext("/get-only", exchange -> {
			exchange.sendResponseHeaders(exchange.getRequestMethod().equals("GET") ? 200 : 405, -1);
			exchange.close();
		});
		server.createContext("/gone", exchange -> {
			exchange.sendResponseHeaders(410, -1);
			exchange.close();
		});
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@After
	public void stop() {
		server.stop(0);
	}

	@Test
	public void check() throws Exception {
		WebAvailability web = new WebAvailability(ProfileCache.httpClient(Duration.ofSeconds(5)), Duration.ofSeconds(5));
		List<WebAvailability.Result> results = web
				.check(List.of(base + "/ok", base + "/moved", base + "/get-only", base + "/gone"));
		assertEquals(List.of(true, true, true, false), results.stream().map(WebAvailability.Result::available).toList());
		assertEquals(200, results.get(1).status());
		assertEquals("Not available: HTTP 410", results.get(3).problem().orElseThrow());
		// Nothing listening there
		server.stop(0);
		assertFalse(web.check(base + "/ok").available());
	}

	/** Only the data entities with web addresses */
	@Test
	public void addresses() {
		Crate crate = new Crate();
		for (String id : List.of("https://example.com/a.csv", "https://orcid.org/0000-0002-1825-0097", "data.csv")) {
			Entity entity = crate.add("File");
			crate.rename(entity, id);
		}
		crate.getRoot().setAll("hasPart", List.of(
				JsonNodeFactory.instance.objectNode().put("@id", "https://example.com/a.csv"),
				JsonNodeFactory.instance.objectNode().put("@id", "data.csv")));
		assertEquals(List.of("https://example.com/a.csv"), WebAvailability.addresses(crate));
	}

	/**
	 * Across all entities, not only files: every contentUrl and url, every
	 * absolute @id, and, with a web base, the relative @ids of files and
	 * folders at the address they lead to; not the descriptor, schema.org's
	 * terms, other entities' relative @ids nor #local ones
	 */
	@Test
	public void targetsAcrossAllEntities() throws Exception {
		Crate crate = new CrateReader().read(new java.io.ByteArrayInputStream("""
				{"@context": "https://w3id.org/ro/crate/1.1/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.1"}},
				  {"@id": "./", "@type": "Dataset", "name": "D", "url": "https://example.org/dataset",
				   "author": {"@id": "https://orcid.org/0000-0002-1825-0097"}, "license": {"@id": "https://creativecommons.org/licenses/by/4.0/"},
				   "hasPart": [{"@id": "ok"}, {"@id": "https://example.com/a.csv"}, {"@id": "by-url.csv"}]},
				  {"@id": "ok", "@type": "File", "contentUrl": "https://example.com/ok-download", "url": ["https://example.com/ok-page", {"@id": "rel/page"}]},
				  {"@id": "https://example.com/a.csv", "@type": "File"},
				  {"@id": "by-url.csv", "@type": "File", "url": "https://example.com/by-url"},
				  {"@id": "https://orcid.org/0000-0002-1825-0097", "@type": "Person", "name": "Josiah", "url": "https://example.edu/~josiah"},
				  {"@id": "https://creativecommons.org/licenses/by/4.0/", "@type": "CreativeWork", "name": "CC BY"},
				  {"@id": "van der Have, Tuur", "@type": "Person", "name": "Tuur"},
				  {"@id": "#alice", "@type": "Person", "name": "Alice"},
				  {"@id": "http://schema.org/Thing", "@type": "Thing"}]}""".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
				"test").crate().orElseThrow();
		java.util.function.Function<List<WebAvailability.Target>, List<String>> shown = targets -> targets.stream()
				.map(t -> t.property() + " " + t.address() + " of " + t.id()).toList();
		// Without a base: absolute addresses only, whatever the entity (the relative url has no base to lead from)
		assertEquals(java.util.Set.of("url https://example.org/dataset of ./",
				"@id https://orcid.org/0000-0002-1825-0097 of https://orcid.org/0000-0002-1825-0097",
				"url https://example.edu/~josiah of https://orcid.org/0000-0002-1825-0097",
				"@id https://creativecommons.org/licenses/by/4.0/ of https://creativecommons.org/licenses/by/4.0/",
				"@id https://example.com/a.csv of https://example.com/a.csv",
				"contentUrl https://example.com/ok-download of ok", "url https://example.com/ok-page of ok",
				"url https://example.com/by-url of by-url.csv"),
				java.util.Set.copyOf(shown.apply(WebAvailability.targetsOf(crate, Optional.empty()))));
		// The root's own @id is not asked about, whatever it is: RO-Crate does not say what is at it
		Crate absoluteRoot = new CrateReader().read(new java.io.ByteArrayInputStream("""
				{"@context": "https://w3id.org/ro/crate/1.1/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "https://example.org/crate/"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.1"}},
				  {"@id": "https://example.org/crate/", "@type": "Dataset", "name": "D", "contentUrl": "https://example.org/crate.zip"}]}"""
				.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "test").crate().orElseThrow();
		assertEquals(List.of("contentUrl https://example.org/crate.zip of https://example.org/crate/"),
				shown.apply(WebAvailability.targetsOf(absoluteRoot, Optional.empty())));
		// What an attached crate on the web must have is its metadata file, beside it: asked for the root
		List<String> attachedRoot = shown.apply(WebAvailability.targetsOf(absoluteRoot,
				Optional.of(WebCrate.attachedTo(URI.create("https://files.example.org/c/")))));
		assertTrue(attachedRoot.toString(), attachedRoot.contains(
				"ro-crate-metadata.json https://files.example.org/c/ro-crate-metadata.json of https://example.org/crate/"));
		// Not a crate with no folder, which has no such file
		assertFalse(shown.apply(WebAvailability.targetsOf(absoluteRoot,
				Optional.of(WebCrate.attachedTo(URI.create("https://files.example.org/c/")).withoutBase()))).toString()
				.contains("ro-crate-metadata.json"));
		// With a base: the files' relative @ids and the relative url too, not the person's name
		List<String> withBase = shown.apply(WebAvailability.targetsOf(crate,
				Optional.of(WebCrate.attachedTo(URI.create("https://files.example.org/c/")))));
		assertTrue(withBase.toString(), withBase.contains("@id https://files.example.org/c/ok of ok"));
		assertTrue(withBase.toString(), withBase.contains("url https://files.example.org/c/rel/page of ok"));
		assertTrue(withBase.toString(), withBase.contains("@id https://files.example.org/c/by-url.csv of by-url.csv"));
		assertFalse(withBase.toString(), withBase.stream().anyMatch(t -> t.contains("van%20der") || t.contains("schema.org")
				|| t.contains("#alice")));
		// The metadata file beside the base, as the root's, and not the root itself (./ is the folder)
		assertTrue(withBase.toString(), withBase.contains(
				"ro-crate-metadata.json https://files.example.org/c/ro-crate-metadata.json of ./"));
		assertFalse(withBase.toString(), withBase.contains("@id https://files.example.org/c/ of ./"));
		// An address several entities have is asked once, for each
		WebAvailability.Target one = new WebAvailability.Target("https://x.example/a", "a", "@id");
		assertEquals(List.of("https://x.example/a"), WebAvailability.addresses(List.of(one,
				new WebAvailability.Target("https://x.example/a", "b", "url"))));
	}
}
