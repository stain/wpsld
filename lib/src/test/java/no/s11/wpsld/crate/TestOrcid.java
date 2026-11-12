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

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sun.net.httpserver.HttpServer;

import no.s11.wpsld.crate.Orcid.Person;

public class TestOrcid {
	/**
	 * As ORCID's expanded search answers, for its fictional researcher; the
	 * second without a name, the third with a wrong check digit
	 */
	private static final String CARBERRY = """
			{"expanded-result": [
			 {"orcid-id": "0000-0002-1825-0097", "given-names": "Josiah", "family-names": "Carberry",
			  "credit-name": null, "other-name": ["Josiah Stinkney Carberry", "J. Carberry"],
			  "email": ["josiah@example.org"], "institution-name": ["Brown University", "Wesleyan University"]},
			 {"orcid-id": "0000-0001-5109-3700", "given-names": null, "family-names": null, "credit-name": null},
			 {"orcid-id": "0000-0002-1825-0098", "given-names": "Not", "family-names": "Valid"}
			], "num-found": 3}""";

	@Test
	public void parse() throws Exception {
		List<Person> people = Orcid.parse(new ObjectMapper().readTree(CARBERRY));
		assertEquals(List.of(new Person("0000-0002-1825-0097", Optional.of("Josiah"), Optional.of("Carberry"),
				Optional.empty(), List.of("Josiah Stinkney Carberry", "J. Carberry"),
				List.of("Brown University", "Wesleyan University"))), people);
		assertEquals("https://orcid.org/0000-0002-1825-0097", people.get(0).iri());
		assertEquals("Josiah Carberry", people.get(0).name());
		assertEquals("J. S. Carberry", new Person("0000-0002-1825-0097", Optional.of("Josiah"),
				Optional.of("Carberry"), Optional.of("J. S. Carberry"), List.of(), List.of()).name());
	}

	@Test
	public void idOf() {
		assertEquals(Optional.of("0000-0002-1825-0097"), Orcid.idOf("https://orcid.org/0000-0002-1825-0097"));
		assertEquals(Optional.of("0000-0002-1825-0097"), Orcid.idOf("http://orcid.org/0000-0002-1825-0097/"));
		assertEquals(Optional.of("0000-0002-1825-0097"), Orcid.idOf("orcid.org/0000-0002-1825-0097"));
		assertEquals(Optional.of("0000-0002-1825-0097"), Orcid.idOf("0000000218250097"));
		// X as the check digit, also written small
		assertEquals(Optional.of("0000-0000-0000-001X"), Orcid.idOf("https://orcid.org/0000-0000-0000-001x"));
		assertEquals(Optional.empty(), Orcid.idOf("https://orcid.org/0000-0002-1825-0098"));
		assertEquals(Optional.empty(), Orcid.idOf("https://sandbox.orcid.org/0000-0002-1825-0097"));
		assertEquals(Optional.empty(), Orcid.idOf("#josiah"));
	}

	@Test
	public void search() throws Exception {
		AtomicReference<String> asked = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			asked.set(exchange.getRequestURI().toString());
			byte[] body = CARBERRY.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		try {
			Orcid orcid = new Orcid(HttpClient.newHttpClient(), Duration.ofSeconds(5), "wpsld/test",
					URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
			assertEquals("Josiah Carberry", orcid.search("Josiah Carberry").get(0).name());
			assertEquals("/expanded-search/?q=Josiah+Carberry&rows=25", asked.get());
			// An iD finds that person
			orcid.search("https://orcid.org/0000-0002-1825-0097");
			assertEquals("/expanded-search/?q=orcid%3A0000-0002-1825-0097&rows=25", asked.get());
		} finally {
			server.stop(0);
		}
	}

	@Test
	public void describe() {
		Entity person = new Entity("#person-1", "Thing");
		Person carberry = new Person("0000-0002-1825-0097", Optional.of("Josiah"), Optional.of("Carberry"),
				Optional.empty(), List.of(), List.of("Brown University"));
		Orcid.describe(person, carberry);
		assertEquals(List.of("Person"), person.getTypes());
		assertEquals("Josiah Carberry", person.getLabel());
		assertEquals("Josiah", person.get("givenName").orElseThrow().asText());
		assertEquals("Carberry", person.get("familyName").orElseThrow().asText());
		// A name it has is kept
		Entity named = new Entity("#j", "Person");
		named.set("name", Optional.of(TextNode.valueOf("J. Carberry")));
		Orcid.describe(named, carberry);
		assertEquals("J. Carberry", named.getLabel());
	}

	@Test
	public void repair() {
		Crate crate = new Crate();
		Entity root = crate.getRoot();
		Entity josiah = crate.add("Person");
		crate.rename(josiah, "http://orcid.org/0000-0002-1825-0097/");
		Entity again = crate.add("Person");
		crate.rename(again, "orcid.org/0000-0002-1825-0097");
		Entity wrong = crate.add("Person");
		crate.rename(wrong, "https://orcid.org/0000-0002-1825-0098");
		root.setAll("author", List.of(reference("http://orcid.org/0000-0002-1825-0097/"),
				reference("orcid.org/0000-0002-1825-0097"), reference("https://orcid.org/0000-0002-1825-0098")));
		Entity paper = crate.add("ScholarlyArticle");
		paper.set("author", Optional.of(reference("https://www.orcid.org/0000-0000-0000-001x")));

		assertFalse(Orcid.repair(crate).isEmpty());
		assertSame(josiah, crate.get("https://orcid.org/0000-0002-1825-0097").orElseThrow());
		assertTrue(crate.get("orcid.org/0000-0002-1825-0097").isEmpty());
		assertEquals(List.of(reference("https://orcid.org/0000-0002-1825-0097"),
				reference("https://orcid.org/0000-0002-1825-0098")), root.getAll("author"));
		// A wrong check digit: left for the user
		assertSame(wrong, crate.get("https://orcid.org/0000-0002-1825-0098").orElseThrow());
		assertEquals("https://orcid.org/0000-0000-0000-001X", paper.get("author").orElseThrow().path("@id").asText());
		assertEquals(List.of(), Orcid.repair(crate));
	}

	private static JsonNode reference(String id) {
		return JsonNodeFactory.instance.objectNode().put("@id", id);
	}
}
