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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Looks up places by name in <a href="https://www.geonames.org/">GeoNames</a>,
 * whose identifiers RO-Crate recommends for places. Its web services need a
 * (free) account: the user's own username, with the free web services
 * enabled on their account page.
 *
 * @see <a href="https://www.geonames.org/export/geonames-search.html">GeoNames search web service</a>
 */
public class GeoNames {
	/** The HTTPS address of GeoNames' web services */
	public static final URI SERVICE = URI.create("https://secure.geonames.org/");
	/** Where to make an account */
	public static final String SIGN_UP = "https://www.geonames.org/login";
	/** Where to enable the free web services for an account */
	public static final String ACCOUNT = "https://www.geonames.org/manageaccount";
	private static final int MAX_ROWS = 25;
	/** e.g. http://sws.geonames.org/8152662/ or https://www.geonames.org/8152662/catalina-park.html */
	private static final Pattern GEONAMES_IRI = Pattern
			.compile("https?://(?:sws\\.|www\\.)?geonames\\.org/(\\d+)(?:/.*)?");
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * A place found in GeoNames.
	 *
	 * @param region e.g. New South Wales
	 * @param kind   e.g. park, or populated place
	 */
	public record Place(long id, String name, Optional<String> region, Optional<String> country,
			Optional<String> kind, double latitude, double longitude) {
		/**
		 * Its identifier in GeoNames' Linked Data, as RO-Crate uses for the
		 * <code>@id</code> of a place, e.g. <code>http://sws.geonames.org/8152662/</code>.
		 */
		public String iri() {
			return GeoNames.iri(id);
		}

		/** Its page on the GeoNames web site */
		public String page() {
			return "https://www.geonames.org/" + id + "/";
		}

		/** Where it is and what it is, e.g. "New South Wales, Australia — park" */
		public String details() {
			String where = Stream.of(region, country).flatMap(Optional::stream).filter(s -> !s.isBlank())
					.distinct().collect(Collectors.joining(", "));
			return Stream.of(Optional.of(where), kind).flatMap(Optional::stream).filter(s -> !s.isBlank())
					.collect(Collectors.joining(" — "));
		}
	}

	/** GeoNames said no, e.g. for an unknown username, with its reason */
	public static class GeoNamesException extends IOException {
		private static final long serialVersionUID = 1L;

		public GeoNamesException(String message) {
			super(message);
		}
	}

	private final HttpClient http;
	private final Duration timeout;
	private final URI service;
	private final String userAgent;

	/**
	 * @param userAgent Names the application to GeoNames, e.g.
	 *                  <code>wpsld/0.4.0</code>
	 */
	public GeoNames(HttpClient http, Duration timeout, String userAgent) {
		this(http, timeout, userAgent, SERVICE);
	}

	public GeoNames(HttpClient http, Duration timeout, String userAgent, URI service) {
		this.http = Objects.requireNonNull(http);
		this.timeout = Objects.requireNonNull(timeout);
		this.userAgent = Objects.requireNonNull(userAgent);
		this.service = Objects.requireNonNull(service);
	}

	/**
	 * The GeoNames identifier of a place, e.g.
	 * <code>http://sws.geonames.org/8152662/</code> for 8152662.
	 */
	public static String iri(long id) {
		return "http://sws.geonames.org/" + id + "/";
	}

	/**
	 * The GeoNames id in an identifier or web page address, e.g. 8152662 for
	 * <code>http://sws.geonames.org/8152662/</code> or
	 * <code>https://www.geonames.org/8152662/catalina-park.html</code>.
	 */
	public static Optional<Long> idOf(String address) {
		Matcher matcher = GEONAMES_IRI.matcher(address.strip());
		return matcher.matches() ? Optional.of(Long.parseLong(matcher.group(1))) : Optional.empty();
	}

	/**
	 * Places whose names match the query, most relevant first.
	 *
	 * @throws GeoNamesException if GeoNames refuses, e.g. an unknown username
	 *                           or one without the web services enabled
	 */
	public List<Place> search(String query, String username) throws IOException, InterruptedException {
		if (username.isBlank()) {
			throw new GeoNamesException("No GeoNames username");
		}
		URI address = service.resolve("searchJSON?q=" + encode(query.strip()) + "&maxRows=" + MAX_ROWS
				+ "&style=MEDIUM&username=" + encode(username.strip()));
		HttpRequest request = HttpRequest.newBuilder(address).timeout(timeout).header("Accept", "application/json")
				.header("User-Agent", userAgent).GET().build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		JsonNode json;
		try {
			json = MAPPER.readTree(response.body());
		} catch (IOException e) {
			throw new GeoNamesException("GeoNames answered " + response.statusCode() + " with no results");
		}
		return parse(json);
	}

	private static String encode(String text) {
		return URLEncoder.encode(text, StandardCharsets.UTF_8);
	}

	/**
	 * The places of a GeoNames search result, skipping any without a name or
	 * coordinates.
	 *
	 * @throws GeoNamesException for GeoNames' error status
	 */
	public static List<Place> parse(JsonNode json) throws GeoNamesException {
		JsonNode status = json.path("status");
		if (status.isObject()) {
			throw new GeoNamesException(status.path("message").asText("GeoNames reported an error").strip());
		}
		List<Place> places = new ArrayList<>();
		for (JsonNode place : json.path("geonames")) {
			Optional<Double> latitude = number(place.path("lat"));
			Optional<Double> longitude = number(place.path("lng"));
			String name = place.path("name").asText("");
			if (!place.path("geonameId").canConvertToLong() || name.isBlank() || latitude.isEmpty()
					|| longitude.isEmpty()) {
				continue;
			}
			places.add(new Place(place.path("geonameId").asLong(), name, text(place.path("adminName1")),
					text(place.path("countryName")), text(place.path("fcodeName")), latitude.get(),
					longitude.get()));
		}
		return places;
	}

	private static Optional<String> text(JsonNode value) {
		return value.isTextual() && !value.asText().isBlank() ? Optional.of(value.asText()) : Optional.empty();
	}

	private static Optional<Double> number(JsonNode value) {
		if (value.isNumber()) {
			return Optional.of(value.asDouble());
		}
		try {
			return value.isTextual() ? Optional.of(Double.parseDouble(value.asText())) : Optional.empty();
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}
}
