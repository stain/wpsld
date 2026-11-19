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
package no.s11.wpsld.masp;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateRetriever;
import no.s11.wpsld.crate.RootProbe;
import no.s11.wpsld.crate.WebCrate;
import no.s11.wpsld.util.UserAgent;

/**
 * Every request wpsld makes names wpsld and its version in its
 * <code>User-Agent</code> (see {@link UserAgent}): the ones the lookups,
 * the availability check and the map tiles make are given it by the editor,
 * and checked here are the ones that make themselves: retrieving a crate
 * (and the link set, profiles and other pages that leads to), looking for
 * a crate's files, and the profile registries.
 */
public class TestUserAgent {
	private static final String CRATE = "{\"@context\": \"https://w3id.org/ro/crate/1.1/context\", \"@graph\": ["
			+ "{\"@id\": \"ro-crate-metadata.json\", \"@type\": \"CreativeWork\", \"about\": {\"@id\": \"./\"}},"
			+ "{\"@id\": \"./\", \"@type\": \"Dataset\", \"name\": \"D\", \"hasPart\": [{\"@id\": \"file.csv\"}]},"
			+ "{\"@id\": \"file.csv\", \"@type\": \"File\"}]}";
	private HttpServer server;
	/** The User-Agent of each request, by "METHOD /path" */
	private final List<String> agents = new CopyOnWriteArrayList<>();

	@After
	public void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	private URI serve() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			agents.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
					+ exchange.getRequestHeaders().getFirst("User-Agent"));
			String path = exchange.getRequestURI().getPath();
			String type = "text/html";
			byte[] body;
			switch (path) {
			case "/doi" -> {
				exchange.getResponseHeaders().add("Location", "/page");
				exchange.sendResponseHeaders(302, -1);
				exchange.close();
				return;
			}
			case "/page" -> {
				exchange.getResponseHeaders().add("Link", "</linkset>; rel=\"linkset\"; type=\"application/linkset+json\"");
				body = "<html>landing</html>".getBytes(StandardCharsets.UTF_8);
			}
			case "/linkset" -> {
				type = "application/linkset+json";
				body = ("{\"linkset\": [{\"anchor\": \"http://" + exchange.getRequestHeaders().getFirst("Host")
						+ "/page\", \"describedby\": [{\"href\": \"/crate.json\", \"type\": \"application/ld+json\"}]}]}")
						.getBytes(StandardCharsets.UTF_8);
			}
			case "/crate.json" -> {
				type = "application/ld+json";
				body = CRATE.getBytes(StandardCharsets.UTF_8);
			}
			case "/registry" -> {
				type = "application/json";
				body = "{}".getBytes(StandardCharsets.UTF_8);
			}
			case "/files/file.csv" -> {
				exchange.sendResponseHeaders(200, -1);
				exchange.close();
				return;
			}
			default -> {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			}
			exchange.getResponseHeaders().add("Content-Type", type);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
	}

	private static HttpClient client() {
		return ProfileCache.httpClient(Duration.ofSeconds(5));
	}

	@Test
	public void namesWpsldAndItsVersion() {
		String agent = UserAgent.get();
		assertTrue(agent, agent.matches("wpsld/(\\d[\\w.\\-]*|development) \\(https://github\\.com/stain/wpsld\\)"));
		assertFalse(agent, agent.contains("${"));
		// The build fills in the version of the project (gradle.properties), here under Gradle, not "development"
		assertNotEquals(agent, "wpsld/development (https://github.com/stain/wpsld)", agent);
	}

	@Test
	public void allOfWhatRetrievingAsksForNamesWpsld() throws Exception {
		URI site = serve();
		CrateRetriever.Retrieved retrieved = new CrateRetriever(client(), Duration.ofSeconds(5)).retrieve(site.resolve("/page"));
		assertEquals(List.of("linkset"), retrieved.steps());
		// The page, the link set, and the crate
		assertEquals(3, agents.size());
		agents.forEach(a -> assertTrue(a, a.endsWith(" " + UserAgent.get())));
	}

	/** A request after a redirect, as a DOI's gives, is made with it too: the client does not drop it */
	@Test
	public void afterARedirectToo() throws Exception {
		URI site = serve();
		new CrateRetriever(client(), Duration.ofSeconds(5)).retrieve(site.resolve("/doi"));
		// The DOI's address, the page it redirected to, the link set, and the crate
		assertEquals(agents.toString(), 4, agents.size());
		agents.forEach(a -> assertTrue(a, a.endsWith(" " + UserAgent.get())));
	}

	@Test
	public void lookingForFilesNamesWpsld() throws Exception {
		URI site = serve();
		var crate = new CrateReader().read(new java.io.ByteArrayInputStream(CRATE.getBytes(StandardCharsets.UTF_8)), "test").crate()
				.orElseThrow();
		WebCrate web = WebCrate.of(new CrateRetriever.Retrieved(site.resolve("/api/crate?id=1"), new byte[0], List.of(), site.resolve("/x"),
				Optional.of(site.resolve("/files/landing"))), Optional.empty()).orElseThrow();
		Optional<WebCrate.Root> found = new RootProbe(client(), Duration.ofSeconds(5)).find(crate, web);
		assertEquals(site.resolve("/files/landing"), found.orElseThrow().address());
		// The file and, to check it is not a server that says yes to anything, a made-up one
		assertEquals(2, agents.size());
		agents.forEach(a -> assertTrue(a, a.startsWith("HEAD ") && a.endsWith(" " + UserAgent.get())));
	}

	@Test
	public void theProfileRegistriesNameWpsld() throws Exception {
		URI site = serve();
		ProfileRegistry.getJson(client(), HttpRequest.newBuilder(site.resolve("/registry")), Duration.ofSeconds(5), "application/json");
		assertEquals(1, agents.size());
		assertTrue(agents.toString(), agents.get(0).endsWith(" " + UserAgent.get()));
	}
}
