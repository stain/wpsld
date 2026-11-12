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
import java.time.Duration;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.net.httpserver.HttpServer;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;
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
		WebAvailability web = new WebAvailability(ProfileCache.httpClient(Duration.ofSeconds(5)), Duration.ofSeconds(5),
				"wpsld-test");
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
}
