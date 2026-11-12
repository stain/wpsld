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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import no.s11.wpsld.crate.GeoNames.GeoNamesException;
import no.s11.wpsld.crate.GeoNames.Place;

public class TestGeoNames {
	/** As GeoNames answers searchJSON, abbreviated */
	private static final String CATALINA = """
			{"totalResultsCount": 2, "geonames": [
			 {"adminCode1": "02", "lng": "150.30119", "geonameId": 8152662, "toponymName": "Catalina Park",
			  "countryId": "2077456", "fcl": "L", "countryCode": "AU", "name": "Catalina Park",
			  "fclName": "parks,area, ...", "countryName": "Australia", "fcodeName": "park",
			  "adminName1": "New South Wales", "lat": "-33.7152", "fcode": "PRK"},
			 {"geonameId": 1, "name": "No coordinates"}
			]}""";

	@Test
	public void parse() throws Exception {
		List<Place> places = GeoNames.parse(new ObjectMapper().readTree(CATALINA));
		assertEquals(1, places.size());
		Place park = places.get(0);
		assertEquals(new Place(8152662, "Catalina Park", Optional.of("New South Wales"), Optional.of("Australia"),
				Optional.of("park"), -33.7152, 150.30119), park);
		assertEquals("http://sws.geonames.org/8152662/", park.iri());
		assertEquals("https://www.geonames.org/8152662/", park.page());
		assertEquals("New South Wales, Australia — park", park.details());
	}

	@Test
	public void refused() throws Exception {
		String refused = """
				{"status": {"message": "user account not enabled to use the free webservice. Please enable it on your account page: https://www.geonames.org/manageaccount ", "value": 10}}""";
		try {
			GeoNames.parse(new ObjectMapper().readTree(refused));
			fail("Expected GeoNamesException");
		} catch (GeoNamesException e) {
			assertTrue(e.getMessage().startsWith("user account not enabled"));
		}
	}

	@Test
	public void idOf() {
		assertEquals(Optional.of(8152662L), GeoNames.idOf("http://sws.geonames.org/8152662/"));
		assertEquals(Optional.of(8152662L), GeoNames.idOf("https://www.geonames.org/8152662/catalina-park.html"));
		assertEquals(Optional.of(8152662L), GeoNames.idOf("https://sws.geonames.org/8152662"));
		assertEquals(Optional.empty(), GeoNames.idOf("#place-1"));
		assertEquals(Optional.empty(), GeoNames.idOf("https://example.org/8152662/"));
	}

	@Test
	public void search() throws Exception {
		AtomicReference<String> asked = new AtomicReference<>();
		AtomicReference<String> agent = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			asked.set(exchange.getRequestURI().toString());
			agent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
			byte[] body = CATALINA.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		try {
			GeoNames geoNames = new GeoNames(HttpClient.newHttpClient(), Duration.ofSeconds(5), "wpsld/test",
					URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
			List<Place> places = geoNames.search("Catalina Park", "someone");
			assertEquals("Catalina Park", places.get(0).name());
			assertEquals("/searchJSON?q=Catalina+Park&maxRows=25&style=MEDIUM&username=someone", asked.get());
			assertEquals("wpsld/test", agent.get());
			assertThrows(GeoNamesException.class, () -> geoNames.search("Catalina Park", " "));
		} finally {
			server.stop(0);
		}
	}
}
