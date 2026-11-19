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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.After;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * Retrieving a crate from a local server: the address asked for, and the
 * landing page that its Signposting led from, after redirects.
 */
public class TestCrateRetrieverLocal {
	private static final String CRATE = "{\"@context\": \"https://w3id.org/ro/crate/1.1/context\", \"@graph\": []}";
	private HttpServer server;

	@After
	public void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	private URI serve() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			byte[] body;
			switch (path) {
			case "/doi/1" -> {
				exchange.getResponseHeaders().add("Location", "/landing/1");
				exchange.sendResponseHeaders(302, -1);
				exchange.close();
				return;
			}
			case "/landing/1" -> {
				exchange.getResponseHeaders().add("Content-Type", "text/html");
				exchange.getResponseHeaders().add("Link",
						"</api/export?id=1>; rel=\"describedby\"; type=\"application/ld+json\"");
				body = "<html>landing</html>".getBytes(StandardCharsets.UTF_8);
			}
			case "/api/export", "/direct/ro-crate-metadata.json" -> {
				exchange.getResponseHeaders().add("Content-Type", "application/ld+json");
				body = CRATE.getBytes(StandardCharsets.UTF_8);
			}
			default -> {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			}
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
	}

	private static CrateRetriever retriever() {
		return new CrateRetriever(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(),
				Duration.ofSeconds(5));
	}

	/** A DOI-like address redirects to the landing page, which says where the metadata is */
	@Test
	public void landingPageAfterRedirect() throws Exception {
		URI site = serve();
		CrateRetriever.Retrieved retrieved = retriever().retrieve(site.resolve("/doi/1"));
		assertEquals(site.resolve("/api/export?id=1"), retrieved.source());
		assertEquals(site.resolve("/doi/1"), retrieved.asked());
		// Where the redirection ended, not the address asked for
		assertEquals(Optional.of(site.resolve("/landing/1")), retrieved.landing());
		assertEquals(List.of("Link header"), retrieved.steps());
	}

	@Test
	public void documentAskedForHasNoLandingPage() throws Exception {
		URI site = serve();
		CrateRetriever.Retrieved retrieved = retriever().retrieve(site.resolve("/direct/ro-crate-metadata.json"));
		assertEquals(site.resolve("/direct/ro-crate-metadata.json"), retrieved.source());
		assertEquals(site.resolve("/direct/ro-crate-metadata.json"), retrieved.asked());
		assertEquals(Optional.empty(), retrieved.landing());
	}
}
