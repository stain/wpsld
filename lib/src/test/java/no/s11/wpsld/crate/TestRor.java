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

import com.sun.net.httpserver.HttpServer;

import no.s11.wpsld.crate.Ror.Name;
import no.s11.wpsld.crate.Ror.Organization;

public class TestRor {
	/** As ROR's API answers for the University of Oslo, shortened */
	private static final String OSLO = """
			{"id": "https://ror.org/01xtthb56", "names": [
			  {"lang": "fi", "types": ["label"], "value": "Oslon Yliopisto"},
			  {"lang": "en", "types": ["alias"], "value": "Royal Frederick University"},
			  {"lang": null, "types": ["acronym"], "value": "UiO"},
			  {"lang": "no", "types": ["label"], "value": "Universitetet i Oslo"},
			  {"lang": "en", "types": ["ror_display", "label"], "value": "University of Oslo"}],
			 "types": ["education", "funder"], "status": "active",
			 "links": [{"type": "wikipedia", "value": "http://en.wikipedia.org/wiki/University_of_Oslo"},
			  {"type": "website", "value": "https://www.uio.no/"}],
			 "locations": [{"geonames_id": 3143244, "geonames_details": {"name": "Oslo", "lat": 59.91273,
			  "lng": 10.74609, "country_name": "Norway", "country_subdivision_name": "Oslo"}}]}""";
	/** A search: Oslo, one without names, and one with wrong check digits */
	private static final String SEARCH = """
			{"number_of_results": 3, "items": [%s,
			 {"id": "https://ror.org/027m9bs27", "names": []},
			 {"id": "https://ror.org/027m9bs28", "names": [{"types": ["ror_display"], "value": "Wrong"}]}]}"""
			.formatted(OSLO);

	@Test
	public void parse() throws Exception {
		List<Organization> found = Ror.parse(new ObjectMapper().readTree(SEARCH).path("items"));
		assertEquals(1, found.size());
		Organization oslo = found.get(0);
		assertEquals("01xtthb56", oslo.id());
		assertEquals("https://ror.org/01xtthb56", oslo.iri());
		assertEquals("University of Oslo", oslo.name());
		// The display name first, then labels, aliases and acronyms
		assertEquals(List.of("University of Oslo", "Oslon Yliopisto", "Universitetet i Oslo",
				"Royal Frederick University", "UiO"), oslo.names().stream().map(Name::value).toList());
		assertEquals("Universitetet i Oslo (no, label)", oslo.names().get(2).toString());
		assertEquals("University of Oslo (en, label)", oslo.names().get(0).toString());
		assertEquals("UiO (acronym)", oslo.names().get(4).toString());
		assertEquals(List.of("education", "funder"), oslo.types());
		assertEquals(Optional.of("https://www.uio.no/"), oslo.website());
		assertEquals("Oslo, Norway", oslo.where());
		GeoNames.Place city = oslo.location().orElseThrow();
		assertEquals("http://sws.geonames.org/3143244/", city.iri());
		assertEquals(59.91273, city.latitude(), 0);
		// A single record, as for a ROR identifier
		assertEquals(List.of(oslo), Ror.parse(new ObjectMapper().readTree(OSLO)));
	}

	@Test
	public void idOf() {
		assertEquals(Optional.of("027m9bs27"), Ror.idOf("https://ror.org/027m9bs27"));
		assertEquals(Optional.of("027m9bs27"), Ror.idOf("http://www.ror.org/027M9BS27/"));
		assertEquals(Optional.of("027m9bs27"), Ror.idOf("ror.org/027m9bs27"));
		assertEquals(Optional.of("01xtthb56"), Ror.idOf("01xtthb56"));
		assertEquals(Optional.empty(), Ror.idOf("https://ror.org/027m9bs28"));
		// Not in Crockford's Base32, which has no i, l, o or u
		assertEquals(Optional.empty(), Ror.idOf("https://ror.org/027u9bs27"));
		assertEquals(Optional.empty(), Ror.idOf("#uio"));
	}

	@Test
	public void search() throws Exception {
		AtomicReference<String> asked = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			asked.set(exchange.getRequestURI().toString());
			boolean record = exchange.getRequestURI().getPath().startsWith("/organizations/");
			boolean known = exchange.getRequestURI().getPath().endsWith("01xtthb56");
			byte[] body = (record ? OSLO : SEARCH).getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(record && !known ? 404 : 200, record && !known ? -1 : body.length);
			if (!record || known) {
				exchange.getResponseBody().write(body);
			}
			exchange.close();
		});
		server.start();
		try {
			Ror ror = new Ror(HttpClient.newHttpClient(), Duration.ofSeconds(5), "wpsld/test",
					URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
			assertEquals("University of Oslo", ror.search("Universitetet i Oslo").get(0).name());
			assertEquals("/organizations?query=Universitetet+i+Oslo", asked.get());
			// An identifier finds that organisation
			assertEquals("01xtthb56", ror.search("http://ror.org/01xtthb56").get(0).id());
			assertEquals("/organizations/01xtthb56", asked.get());
			assertEquals(List.of(), ror.search("027m9bs27"));
		} finally {
			server.stop(0);
		}
	}

	@Test
	public void describe() throws Exception {
		Organization oslo = Ror.parse(new ObjectMapper().readTree(OSLO)).get(0);
		Crate crate = new Crate();
		Entity uio = crate.add("Thing");
		Ror.describe(uio, oslo, "Universitetet i Oslo", crate::get, (type, id) -> {
			Entity added = crate.add(type);
			if (id != null) {
				crate.rename(added, id);
			}
			return added;
		});
		assertEquals(List.of("Organization"), uio.getTypes());
		assertEquals("Universitetet i Oslo", uio.getLabel());
		assertEquals("https://www.uio.no/", uio.get("url").orElseThrow().asText());
		assertEquals("http://sws.geonames.org/3143244/", uio.get("location").orElseThrow().path("@id").asText());
		Entity city = crate.get("http://sws.geonames.org/3143244/").orElseThrow();
		assertEquals("Oslo", city.getLabel());
		assertTrue(city.get("geo").isPresent());

		// A second organisation in Oslo links the same place, and keeps its own url
		int entities = crate.getEntities().size();
		Entity other = crate.add("Organization");
		other.set("url", Optional.of(JsonNodeFactory.instance.textNode("https://example.org/")));
		Ror.describe(other, oslo, oslo.name(), crate::get, (type, id) -> crate.add(type));
		assertEquals(entities + 1, crate.getEntities().size());
		assertEquals("https://example.org/", other.get("url").orElseThrow().asText());
		assertEquals("University of Oslo", other.getLabel());
	}

	@Test
	public void repair() {
		Crate crate = new Crate();
		Entity root = crate.getRoot();
		Entity uio = crate.add("Organization");
		crate.rename(uio, "http://ror.org/01xtthb56/");
		Entity again = crate.add("Organization");
		crate.rename(again, "ror.org/01XTTHB56");
		Entity wrong = crate.add("Organization");
		crate.rename(wrong, "https://ror.org/01xtthb57");
		root.setAll("publisher", List.of(reference("http://ror.org/01xtthb56/"), reference("ror.org/01XTTHB56"),
				reference("https://ror.org/01xtthb57")));

		assertFalse(Ror.repair(crate).isEmpty());
		assertSame(uio, crate.get("https://ror.org/01xtthb56").orElseThrow());
		assertTrue(crate.get("ror.org/01XTTHB56").isEmpty());
		assertEquals(List.of(reference("https://ror.org/01xtthb56"), reference("https://ror.org/01xtthb57")),
				root.getAll("publisher"));
		// Wrong check digits: left for the user
		assertSame(wrong, crate.get("https://ror.org/01xtthb57").orElseThrow());
		assertEquals(List.of(), Ror.repair(crate));
	}

	private static JsonNode reference(String id) {
		return JsonNodeFactory.instance.objectNode().put("@id", id);
	}
}
