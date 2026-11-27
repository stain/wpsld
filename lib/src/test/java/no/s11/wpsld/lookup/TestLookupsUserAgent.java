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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

import no.s11.wpsld.util.UserAgent;

/**
 * Every lookup sends wpsld's own <code>User-Agent</code> (see {@link UserAgent}),
 * and cannot be given another: each is asked something of a server of the test's,
 * which answers nothing useful, as the request is what is looked at.
 */
public class TestLookupsUserAgent {
	private HttpServer server;
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
			agents.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + ": "
					+ exchange.getRequestHeaders().getFirst("User-Agent"));
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
		});
		server.start();
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
	}

	private static HttpClient http() {
		return HttpClient.newHttpClient();
	}

	/** Asks, whatever the answer: it is the request that counts */
	private interface Ask {
		void run() throws Exception;
	}

	private void ask(Ask ask) {
		try {
			ask.run();
		} catch (Exception e) {
			// The server has nothing to say
		}
	}

	private void assertAllNameWpsld(int atLeast) {
		assertTrue(agents.toString(), agents.size() >= atLeast);
		agents.forEach(a -> assertTrue(a, a.endsWith(": " + UserAgent.get())));
	}

	@Test
	public void orcid() throws Exception {
		URI site = serve();
		ask(() -> new ORCID(http(), Duration.ofSeconds(5), site).search("Josiah Carberry"));
		assertAllNameWpsld(1);
	}

	@Test
	public void ror() throws Exception {
		URI site = serve();
		ask(() -> new ROR(http(), Duration.ofSeconds(5), site).search("University of Oslo"));
		assertAllNameWpsld(1);
	}

	@Test
	public void doi() throws Exception {
		URI site = serve();
		DOI doi = new DOI(http(), Duration.ofSeconds(5), Map.of("crossref", site, "datacite", site, "resolver", site));
		ask(() -> doi.lookUp("10.1234/example"));
		ask(() -> doi.search("a title"));
		assertAllNameWpsld(2);
	}

	@Test
	public void geonames() throws Exception {
		URI site = serve();
		ask(() -> new GeoNames(http(), Duration.ofSeconds(5), site).search("Oslo", "a-user"));
		assertAllNameWpsld(1);
	}

	@Test
	public void webAvailability() throws Exception {
		URI site = serve();
		new WebAvailability(http(), Duration.ofSeconds(5)).check(site.resolve("x").toString());
		// HEAD, and where that is not enough, GET
		assertAllNameWpsld(2);
	}
}
