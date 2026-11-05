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
package no.s11.wpsld.jsonld;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

public class TestContextCache {
	/** The bundled RO-Crate contexts, as fetched from w3id.org on 2026-11-05 */
	private static final Map<String, String> RO_CRATE_SHA256 = Map.of(
			"1.0", "c3c167338f2ce967598abe0a9414ec3b207392896149d10524e423a31d37c5e7",
			"1.1", "4a8c4cbdfd73d3d11be5ebe135f6122a0fc711fd1433e4258a748c80cc257ab7",
			"1.2", "27a661f6082735563d9f83145a63876cbe0c0b78624332d3ca05053253eb0d41",
			"1.3", "5a3df1a43185501db4d45cdde5a478c57eeb1d673eedfe400488fc4c4b21dd91");

	@Test
	public void roCrateContextsBundled() throws Exception {
		ContextCache cache = ContextCache.bundled();
		for (Map.Entry<String, String> version : RO_CRATE_SHA256.entrySet()) {
			String url = "https://w3id.org/ro/crate/" + version.getKey() + "/context";
			assertTrue(url, cache.getLocations().contains(url));
			// Also the http:// form some crates use
			assertTrue(cache.getLocations().contains(url.replace("https:", "http:")));
			JsonNode context = cache.get(url).orElseThrow();
			assertTrue(context.get("@context").isObject());
			try (InputStream in = getClass().getResourceAsStream(
					"/jsonld-contexts/ro-crate-" + version.getKey() + "-context.jsonld")) {
				assertNotNull(in);
				byte[] digest = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
				assertEquals(url, version.getValue(), HexFormat.of().formatHex(digest));
			}
		}
	}

	@Test
	public void parsedOnceAndKept() {
		ContextCache cache = new ContextCache();
		String url = "https://w3id.org/ro/crate/1.3/context";
		assertSame(cache.get(url).orElseThrow(), cache.get(url).orElseThrow());
		assertSame(ContextCache.bundled(), ContextCache.bundled());
	}

	@Test
	public void unknownNotFetched() {
		assertEquals(Optional.empty(), ContextCache.bundled().get("https://example.com/not-a-known-context"));
	}

	/**
	 * Other jars can add contexts with their own jarcache.json, with paths
	 * relative to it, in JSON-LD Java's format.
	 */
	@Test
	public void additionalJarcache() throws Exception {
		Path dir = Files.createTempDirectory("jarcache");
		Files.createDirectories(dir.resolve("contexts"));
		Files.writeString(dir.resolve("contexts/example.jsonld"), "{\"@context\": {\"ex\": \"http://example.com/ns#\"}}");
		Files.writeString(dir.resolve(ContextCache.JARCACHE), "[{\"Content-Location\": \"http://example.com/context\","
				+ " \"X-Classpath\": \"contexts/example.jsonld\", \"Content-Type\": \"application/ld+json\"}]");
		try (URLClassLoader loader = new URLClassLoader(new URL[] { dir.toUri().toURL() },
				getClass().getClassLoader())) {
			ContextCache cache = new ContextCache(loader);
			assertEquals("http://example.com/ns#",
					cache.get("http://example.com/context").orElseThrow().get("@context").get("ex").asText());
			// The bundled ones still found through the parent class loader
			assertTrue(cache.get("https://w3id.org/ro/crate/1.3/context").isPresent());
		}
	}
}
