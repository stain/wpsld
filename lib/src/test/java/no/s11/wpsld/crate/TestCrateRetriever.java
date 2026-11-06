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

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

public class TestCrateRetriever {
	private static final URI BASE = URI.create("https://example.org/profiles/p/");

	private static Optional<URI> header(String... links) {
		return CrateRetriever.linkHeader(HttpHeaders.of(Map.of("Link", List.of(links)), (a, b) -> true), BASE);
	}

	@Test
	public void linkHeader() {
		// The RO-Crate profile wins over a plain JSON-LD link, whatever the order
		assertEquals(URI.create("https://example.org/crate.json"),
				header("<https://example.org/other.jsonld>; rel=describedby; type=\"application/ld+json\", "
						+ "</crate.json>; rel=\"describedby\"; profile=\"https://w3id.org/ro/crate\"").orElseThrow());
		// Several values of rel, a type with parameters, in separate headers
		assertEquals(URI.create("https://example.org/profiles/p/ro-crate-metadata.json"),
				header("<https://doi.org/10.1234/x>; rel=\"cite-as\"",
						"<ro-crate-metadata.json>; rel=\"alternate describedby\"; type=\"application/json; charset=utf-8\"")
						.orElseThrow());
		// Other metadata formats, items and local files are not followed
		assertEquals(Optional.empty(), header("<meta.xml>; rel=describedby; type=application/xml",
				"<crate.zip>; rel=item; type=application/zip", "<file:///etc/passwd>; rel=describedby; type=application/json"));
		assertEquals(Optional.empty(), header());
	}

	@Test
	public void htmlLink() {
		// As in a MASP profile's index.html
		assertEquals(URI.create("https://example.org/profiles/p/ro-crate-metadata.json"),
				CrateRetriever.htmlLink("<head><LINK rel=\"stylesheet\" href=\"s.css\">\n"
						+ "<link rel=\"describedby\" href=\"ro-crate-metadata.json\" type=\"application/ld+json\"></head>", BASE)
						.orElseThrow());
		assertEquals(URI.create("https://example.org/m.json?a=1&b=2"),
				CrateRetriever.htmlLink("<link type='application/json' rel='describedby' href='/m.json?a=1&amp;b=2'/>", BASE)
						.orElseThrow());
		assertEquals(Optional.empty(), CrateRetriever.htmlLink("<a rel=describedby href=x.json>"
				+ "<link rel=describedby href=x.ttl type=text/turtle><link rel=describedby type=application/ld+json>", BASE));
	}

	@Test
	public void folderNextToResolvedUrl() {
		assertEquals(Optional.of(URI.create("https://example.com/profile/0.2/ro-crate-metadata.json")),
				CrateRetriever.folder(URI.create("https://example.com/profile/0.2/")));
		// As the specification's example
		assertEquals(Optional.of(URI.create("https://example.com/Workflow-RO-Crate/1.0/ro-crate-metadata.json")),
				CrateRetriever.folder(URI.create("https://example.com/Workflow-RO-Crate/1.0/index.html")));
		assertEquals(Optional.empty(), CrateRetriever.folder(URI.create("https://example.com/profile.ttl")));
		assertEquals(Optional.empty(), CrateRetriever.folder(URI.create("https://example.com/profile")));
	}
}
