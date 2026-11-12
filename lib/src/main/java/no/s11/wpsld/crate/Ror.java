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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * Finds organisations in the <a href="https://ror.org/">Research
 * Organization Registry</a> (ROR), whose identifiers RO-Crate recommends for
 * organisations, through its REST API v2, which needs no credentials (without
 * a client ID, up to 50 requests in 5 minutes).
 * <p>
 * An organisation has many names, in many languages: RO-Crate rarely uses
 * <code>@language</code>, so one of them, by default ROR's display name, is
 * written as a plain <code>name</code>.
 *
 * @see <a href="https://ror.readme.io/docs/rest-api">ROR REST API</a>
 */
public class Ror {
	/** ROR's REST API */
	public static final URI SERVICE = URI.create("https://api.ror.org/v2/");
	/** ROR identifiers as RO-Crate writes them, e.g. https://ror.org/027m9bs27 */
	public static final String NAMESPACE = "https://ror.org/";
	/** Crockford's Base32, as ROR identifiers use */
	private static final String BASE32 = "0123456789abcdefghjkmnpqrstvwxyz";
	/** An identifier, alone or in an address, e.g. ror.org/027m9bs27 */
	private static final Pattern ROR = Pattern.compile(
			"(?:(?:https?://)?(?:www\\.)?ror\\.org/)?(0[0-9a-hjkmnp-tv-z]{6})(\\d{2})/?", Pattern.CASE_INSENSITIVE);
	/** Kinds of names, the more usual first */
	private static final List<String> NAME_TYPES = List.of("ror_display", "label", "alias", "acronym");
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * One of an organisation's names.
	 *
	 * @param language e.g. en, if ROR knows it
	 * @param types    e.g. ror_display, label, alias or acronym
	 */
	public record Name(String value, Optional<String> language, List<String> types) {
		/** e.g. "Universitetet i Oslo (no, label)" */
		@Override
		public String toString() {
			List<String> about = new ArrayList<>();
			language.ifPresent(about::add);
			types.stream().filter(t -> !t.equals("ror_display")).forEach(about::add);
			return about.isEmpty() ? value : value + " (" + String.join(", ", about) + ")";
		}
	}

	/**
	 * An organisation found in ROR.
	 *
	 * @param id       Its ROR identifier, e.g. 027m9bs27
	 * @param name     The name ROR shows for it
	 * @param names    All its names, the display name first, then labels,
	 *                 aliases and acronyms
	 * @param types    e.g. education, funder
	 * @param status   active, inactive or withdrawn
	 * @param location Its city in GeoNames, if ROR gives one
	 */
	public record Organization(String id, String name, List<Name> names, List<String> types, String status,
			Optional<String> website, Optional<GeoNames.Place> location) {
		/** The ROR identifier as RO-Crate writes it, the <code>@id</code> of the organisation */
		public String iri() {
			return NAMESPACE + id;
		}

		/** e.g. "Manchester, England, United Kingdom", or "Oslo, Norway" */
		public String where() {
			return location.map(l -> Stream.concat(Stream.of(l.name()), Stream.of(l.region(), l.country())
					.flatMap(Optional::stream)).distinct().collect(Collectors.joining(", "))).orElse("");
		}
	}

	/** ROR said no, e.g. too many requests, with its reason */
	public static class RorException extends IOException {
		private static final long serialVersionUID = 1L;

		public RorException(String message) {
			super(message);
		}
	}

	private final HttpClient http;
	private final Duration timeout;
	private final URI service;
	private final String userAgent;

	public Ror(HttpClient http, Duration timeout, String userAgent) {
		this(http, timeout, userAgent, SERVICE);
	}

	public Ror(HttpClient http, Duration timeout, String userAgent, URI service) {
		this.http = Objects.requireNonNull(http);
		this.timeout = Objects.requireNonNull(timeout);
		this.userAgent = Objects.requireNonNull(userAgent);
		this.service = Objects.requireNonNull(service);
	}

	/**
	 * The ROR identifier in an identifier or address, e.g.
	 * <code>027m9bs27</code> from <code>http://ror.org/027m9bs27/</code>, if
	 * its check digits are right.
	 */
	public static Optional<String> idOf(String text) {
		Matcher matcher = ROR.matcher(text.strip());
		if (!matcher.matches()) {
			return Optional.empty();
		}
		String base = matcher.group(1).toLowerCase(Locale.ROOT);
		return checkDigits(base.substring(1)).equals(matcher.group(2)) ? Optional.of(base + matcher.group(2))
				: Optional.empty();
	}

	/** ISO 7064 97-10 of the Base32 number, as ROR identifiers use */
	static String checkDigits(String base32) {
		long number = 0;
		for (char c : base32.toCharArray()) {
			number = number * 32 + BASE32.indexOf(c);
		}
		return String.format("%02d", 98 - (number * 100) % 97);
	}

	/**
	 * Organisations whose names (or other details ROR searches) match the
	 * query, most relevant first. A ROR identifier as the query finds that
	 * organisation.
	 */
	public List<Organization> search(String query) throws IOException, InterruptedException {
		Optional<String> id = idOf(query);
		URI address = id.map(i -> service.resolve("organizations/" + i))
				.orElseGet(() -> service.resolve("organizations?query="
						+ URLEncoder.encode(query.strip(), StandardCharsets.UTF_8)));
		HttpRequest request = HttpRequest.newBuilder(address).timeout(timeout).header("Accept", "application/json")
				.header("User-Agent", userAgent).GET().build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		if (id.isPresent() && response.statusCode() == 404) {
			return List.of();
		}
		if (response.statusCode() == 429) {
			throw new RorException("ROR has had too many searches from here; try again in a few minutes");
		}
		if (response.statusCode() != 200) {
			throw new RorException("ROR answered " + response.statusCode());
		}
		JsonNode json;
		try {
			json = MAPPER.readTree(response.body());
		} catch (IOException e) {
			throw new RorException("ROR answered with no results");
		}
		return parse(id.isPresent() ? json : json.path("items"));
	}

	/**
	 * The organisations of a ROR search result, or the one of a ROR record,
	 * skipping any without a ROR identifier or a name.
	 */
	public static List<Organization> parse(JsonNode json) {
		List<Organization> found = new ArrayList<>();
		for (JsonNode record : json.isArray() ? json : List.of(json)) {
			organization(record).ifPresent(found::add);
		}
		return found;
	}

	private static Optional<Organization> organization(JsonNode record) {
		Optional<String> id = idOf(record.path("id").asText(""));
		List<Name> names = new ArrayList<>();
		for (JsonNode name : record.path("names")) {
			Optional<String> value = text(name.path("value"));
			List<String> types = new ArrayList<>();
			name.path("types").forEach(t -> types.add(t.asText()));
			if (value.isPresent() && names.stream().noneMatch(n -> n.value().equals(value.get()))) {
				names.add(new Name(value.get(), text(name.path("lang")), types));
			}
		}
		names.sort(Comparator.comparingInt(Ror::rank));
		if (id.isEmpty() || names.isEmpty()) {
			return Optional.empty();
		}
		List<String> types = new ArrayList<>();
		record.path("types").forEach(t -> types.add(t.asText()));
		Optional<String> website = Optional.empty();
		for (JsonNode link : record.path("links")) {
			if (link.path("type").asText().equals("website")) {
				website = text(link.path("value"));
				break;
			}
		}
		return Optional.of(new Organization(id.get(), names.get(0).value(), names, types,
				record.path("status").asText("active"), website, location(record.path("locations").path(0))));
	}

	/** The first kind of name it is, e.g. 0 for ROR's display name */
	private static int rank(Name name) {
		return name.types().stream().mapToInt(NAME_TYPES::indexOf).filter(i -> i >= 0).min()
				.orElse(NAME_TYPES.size());
	}

	private static Optional<GeoNames.Place> location(JsonNode location) {
		JsonNode details = location.path("geonames_details");
		long id = location.path("geonames_id").asLong(0);
		Optional<String> name = text(details.path("name"));
		if (id <= 0 || name.isEmpty() || !details.path("lat").isNumber() || !details.path("lng").isNumber()) {
			return Optional.empty();
		}
		return Optional.of(new GeoNames.Place(id, name.get(), text(details.path("country_subdivision_name")),
				text(details.path("country_name")), Optional.empty(), details.path("lat").asDouble(),
				details.path("lng").asDouble()));
	}

	private static Optional<String> text(JsonNode value) {
		return value.isTextual() && !value.asText().isBlank() ? Optional.of(value.asText().strip())
				: Optional.empty();
	}

	/**
	 * Describe an organisation as found in ROR: an Organization (keeping
	 * other types it has, but not Thing) with the name chosen, its web site
	 * as <code>url</code> unless it has one, and its city as
	 * <code>location</code> unless it has one: the crate's Place for it, or a
	 * new one from GeoNames. Its <code>@id</code> is left to the caller,
	 * normally {@link Organization#iri()}.
	 *
	 * @param name      The one of its names to write, e.g. its display name
	 * @param lookup    Finds an entity of the crate by <code>@id</code>
	 * @param newEntity Adds an entity of a type to the crate, with the
	 *                  <code>@id</code> given, or its own for null
	 */
	public static void describe(Entity entity, Organization found, String name,
			Function<String, Optional<Entity>> lookup, BiFunction<String, String, Entity> newEntity) {
		List<String> types = new ArrayList<>(entity.getTypes());
		types.remove("Thing");
		if (!types.contains("Organization")) {
			types.add("Organization");
		}
		entity.setTypes(types);
		entity.set("name", Optional.of(TextNode.valueOf(name)));
		found.website().filter(w -> entity.get("url").isEmpty())
				.ifPresent(w -> entity.set("url", Optional.of(TextNode.valueOf(w))));
		if (found.location().isPresent() && entity.get("location").isEmpty()) {
			GeoNames.Place city = found.location().get();
			if (lookup.apply(city.iri()).isEmpty()) {
				Entity place = newEntity.apply("Place", city.iri());
				Places.describe(place, city, lookup, () -> newEntity.apply("Geometry", null));
			}
			entity.set("location", Optional.of(JsonNodeFactory.instance.objectNode().put("@id", city.iri())));
		}
	}

	/**
	 * Bring the crate's ROR identifiers to the form RO-Crate writes,
	 * <code>https://ror.org/027m9bs27</code>, telling what was done:
	 * <code>@id</code>s and references in another form (<code>http:</code>,
	 * without the scheme, a trailing slash, capitals), merging entities that
	 * turn out to be the same organisation. Addresses whose check digits are
	 * wrong are left as they are.
	 *
	 * @return What was changed, one line each
	 */
	public static List<String> repair(Crate crate) {
		return crate.normaliseIds(Ror::canonical);
	}

	/**
	 * The ROR address an <code>@id</code> means: only addresses on ror.org,
	 * not a bare identifier, which could be a local one.
	 */
	private static Optional<String> canonical(String id) {
		return id.toLowerCase(Locale.ROOT).matches("(https?://)?(www\\.)?ror\\.org/.*")
				? idOf(id).map(i -> NAMESPACE + i)
				: Optional.empty();
	}
}
