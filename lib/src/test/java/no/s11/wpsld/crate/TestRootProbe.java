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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.After;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

public class TestRootProbe {
	private HttpServer server;
	/** Paths answered 200 to HEAD */
	private final Set<String> there = ConcurrentHashMap.newKeySet();
	private volatile boolean sayYesToAnything;

	@After
	public void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	private URI serve() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			boolean exists = sayYesToAnything || there.contains(exchange.getRequestURI().getRawPath());
			exchange.sendResponseHeaders(exists ? 200 : 404, -1);
			exchange.close();
		});
		server.start();
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
	}

	private static RootProbe probe() {
		return new RootProbe(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(),
				Duration.ofSeconds(5));
	}

	private static Crate crate() {
		return new CrateReader().read(new ByteArrayInputStream("""
				{"@context": "https://w3id.org/ro/crate/1.1/context",
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.1"}},
				  {"@id": "./", "@type": "Dataset", "name": "D",
				   "hasPart": [{"@id": "data/"}, {"@id": "my file.csv"}, {"@id": "second.csv"}]},
				  {"@id": "data/", "@type": "Dataset"},
				  {"@id": "my file.csv", "@type": "File"},
				  {"@id": "second.csv", "@type": "File"}]}""".getBytes(StandardCharsets.UTF_8)), "test").crate()
				.orElseThrow();
	}

	/** The crate's address is an export with a query, found from a landing page */
	private static WebCrate web(URI site) {
		return WebCrate.of(new CrateRetriever.Retrieved(site.resolve("/api/export?id=1"), new byte[0], List.of("Link header"),
				site.resolve("/doi/1"), Optional.of(site.resolve("/a/landing?id=1"))), Optional.empty()).orElseThrow();
	}

	@Test
	public void firstFileIsTheFirstOfTheParts() {
		// A file, before the folder listed first, as a folder may not answer HEAD
		assertEquals(Optional.of("my file.csv"), RootProbe.firstFile(crate()));
	}

	@Test
	public void foundBesideTheLandingPage() throws Exception {
		URI site = serve();
		there.add("/a/my%20file.csv");
		Optional<WebCrate.Root> found = probe().find(crate(), web(site));
		assertEquals(site.resolve("/a/landing?id=1"), found.orElseThrow().address());
	}

	@Test
	public void notThereIsNoAnswer() throws Exception {
		URI site = serve();
		// The files may be inside a zip: nothing is said
		assertEquals(Optional.empty(), probe().find(crate(), web(site)));
	}

	/** A server that has a page for any path says nothing about this one */
	@Test
	public void yesToAnythingIsNoAnswer() throws Exception {
		URI site = serve();
		sayYesToAnything = true;
		assertEquals(Optional.empty(), probe().find(crate(), web(site)));
	}
}
