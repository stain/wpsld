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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

/**
 * Finds people by name in <a href="https://orcid.org/">ORCID</a>, whose
 * identifiers RO-Crate recommends for people, through its public API's
 * expanded search, which needs no credentials. Only names, other names and
 * institution names are used; e-mail addresses ORCID may give are ignored.
 *
 * @see <a href="https://info.orcid.org/documentation/api-tutorials/api-tutorial-searching-the-orcid-registry/">Searching the ORCID registry</a>
 */
public class ORCID {
	/** ORCID's public API */
	public static final URI SERVICE = URI.create("https://pub.orcid.org/v3.0/");
	/** ORCID iDs as RO-Crate writes them, e.g. https://orcid.org/0000-0002-1825-0097 */
	public static final String NAMESPACE = "https://orcid.org/";
	private static final int ROWS = 25;
	/** An iD, alone or in an address, e.g. orcid.org/0000-0002-1825-0097 */
	private static final Pattern ORCID_PATTERN = Pattern.compile(
			"(?:(?:https?://)?(?:www\\.)?orcid\\.org/)?(\\d{4})-?(\\d{4})-?(\\d{4})-?(\\d{3}[\\dXx])/?",
			Pattern.CASE_INSENSITIVE);
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * A person found in ORCID.
	 *
	 * @param id           Their ORCID iD, e.g. 0000-0002-1825-0097
	 * @param creditName   The name they publish under, if they gave one
	 * @param institutions Names of their institutions, as they wrote them
	 */
	public record Person(String id, Optional<String> givenNames, Optional<String> familyNames,
			Optional<String> creditName, List<String> otherNames, List<String> institutions) {
		/** The ORCID iD as RO-Crate writes it, the <code>@id</code> of the person */
		public String iri() {
			return NAMESPACE + id;
		}

		/** Their name: the name they publish under, else given and family names */
		public String name() {
			return creditName.orElseGet(() -> Stream.of(givenNames, familyNames).flatMap(Optional::stream)
					.collect(Collectors.joining(" ")));
		}
	}

	/** ORCID said no, e.g. an invalid query, with its reason */
	public static class ORCIDException extends IOException {
		private static final long serialVersionUID = 1L;

		public ORCIDException(String message) {
			super(message);
		}
	}

	private final HttpClient http;
	private final Duration timeout;
	private final URI service;
	private final String userAgent;

	public ORCID(HttpClient http, Duration timeout, String userAgent) {
		this(http, timeout, userAgent, SERVICE);
	}

	public ORCID(HttpClient http, Duration timeout, String userAgent, URI service) {
		this.http = Objects.requireNonNull(http);
		this.timeout = Objects.requireNonNull(timeout);
		this.userAgent = Objects.requireNonNull(userAgent);
		this.service = Objects.requireNonNull(service);
	}

	/**
	 * The ORCID iD in an identifier or address, in its usual form
	 * (<code>0000-0002-1825-0097</code>), if its check digit is right: e.g.
	 * from <code>http://orcid.org/0000-0002-1825-0097</code>,
	 * <code>orcid.org/0000-0002-1825-0097/</code> or
	 * <code>0000000218250097</code>.
	 */
	public static Optional<String> idOf(String text) {
		Matcher matcher = ORCID_PATTERN.matcher(text.strip());
		if (!matcher.matches()) {
			return Optional.empty();
		}
		String digits = (matcher.group(1) + matcher.group(2) + matcher.group(3) + matcher.group(4))
				.toUpperCase(Locale.ROOT);
		if (!checkDigit(digits.substring(0, 15)).equals(digits.substring(15))) {
			return Optional.empty();
		}
		return Optional.of(String.join("-", digits.substring(0, 4), digits.substring(4, 8), digits.substring(8, 12),
				digits.substring(12)));
	}

	/** ISO 7064 11,2, as ORCID iDs use */
	static String checkDigit(String base) {
		int total = 0;
		for (char c : base.toCharArray()) {
			total = (total + (c - '0')) * 2;
		}
		int result = (12 - total % 11) % 11;
		return result == 10 ? "X" : String.valueOf(result);
	}

	/**
	 * People whose names (or other details ORCID searches) match the query,
	 * most relevant first. An ORCID iD as the query finds that person.
	 */
	public List<Person> search(String query) throws IOException, InterruptedException {
		String q = query.strip();
		Optional<String> id = idOf(q);
		String solr = id.map(i -> "orcid:" + i).orElse(q);
		URI address = service.resolve("expanded-search/?q=" + URLEncoder.encode(solr, StandardCharsets.UTF_8)
				+ "&rows=" + ROWS);
		HttpRequest request = HttpRequest.newBuilder(address).timeout(timeout).header("Accept", "application/json")
				.header("User-Agent", userAgent).GET().build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new ORCIDException("ORCID answered " + response.statusCode());
		}
		try {
			return parse(MAPPER.readTree(response.body()));
		} catch (IOException e) {
			throw new ORCIDException("ORCID answered with no results");
		}
	}

	/**
	 * The people of an ORCID expanded search result, skipping any without an
	 * ORCID iD or a name.
	 */
	public static List<Person> parse(JsonNode json) {
		List<Person> people = new ArrayList<>();
		for (JsonNode found : json.path("expanded-result")) {
			Optional<String> id = idOf(found.path("orcid-id").asText(""));
			Person person = new Person(id.orElse(""), text(found.path("given-names")),
					text(found.path("family-names")), text(found.path("credit-name")),
					texts(found.path("other-name")), texts(found.path("institution-name")));
			if (id.isPresent() && !person.name().isBlank()) {
				people.add(person);
			}
		}
		return people;
	}

	private static Optional<String> text(JsonNode value) {
		return value.isTextual() && !value.asText().isBlank() ? Optional.of(value.asText().strip())
				: Optional.empty();
	}

	private static List<String> texts(JsonNode values) {
		List<String> texts = new ArrayList<>();
		values.forEach(v -> text(v).filter(t -> !texts.contains(t)).ifPresent(texts::add));
		return texts;
	}

	/**
	 * Describe a person as found in ORCID: a Person (keeping other types it
	 * has, but not Thing) with their name, and given and family names, unless
	 * it has a name. Its <code>@id</code> is left to the caller, normally
	 * {@link Person#iri()}.
	 */
	public static void describe(Entity entity, Person person) {
		List<String> types = new ArrayList<>(entity.getTypes());
		types.remove("Thing");
		if (!types.contains("Person")) {
			types.add("Person");
		}
		entity.setTypes(types);
		if (entity.get("name").isEmpty()) {
			entity.set("name", Optional.of(TextNode.valueOf(person.name())));
		}
		person.givenNames().filter(g -> entity.get("givenName").isEmpty())
				.ifPresent(g -> entity.set("givenName", Optional.of(TextNode.valueOf(g))));
		person.familyNames().filter(f -> entity.get("familyName").isEmpty())
				.ifPresent(f -> entity.set("familyName", Optional.of(TextNode.valueOf(f))));
	}

	/**
	 * Bring the crate's ORCID iDs to the form RO-Crate writes,
	 * <code>https://orcid.org/0000-0002-1825-0097</code>, telling what was
	 * done: <code>@id</code>s and references in another form
	 * (<code>http:</code>, without the scheme, a trailing slash, a small
	 * <code>x</code>), merging entities that turn out to be the same person.
	 * Addresses whose check digit is wrong are left as they are.
	 *
	 * @return What was changed, one line each
	 */
	public static List<String> repair(Crate crate) {
		return crate.normaliseIds(ORCID::canonical);
	}

	/**
	 * The ORCID address an <code>@id</code> means: only addresses on
	 * orcid.org (not its sandbox's test iDs), not a bare iD, which could be a
	 * local identifier.
	 */
	private static Optional<String> canonical(String id) {
		return id.toLowerCase(Locale.ROOT).matches("(https?://)?(www\\.)?orcid\\.org/.*")
				? idOf(id).map(i -> NAMESPACE + i)
				: Optional.empty();
	}
}
