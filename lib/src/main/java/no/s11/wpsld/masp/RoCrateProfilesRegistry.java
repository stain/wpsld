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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The RO-Crate profile registry, <a href="https://profiles.ro-crate.org/">profiles.ro-crate.org</a>,
 * queried through its SPARQL endpoint for profiles (<code>prof:Profile</code>)
 * with MASP rules: a resource with the role <code>prof:role/schema</code>
 * listing <code>rdfs:Class</code> or <code>rdf:Property</code> rules in its
 * <code>hasPart</code>. The crate is loaded from its metadata descriptor (what
 * is <code>about</code> the profile, named <code>ro-crate-metadata.json</code>),
 * else from the profile's identifier. The registry's own pages for a profile
 * (<code>owl:sameAs</code> it) are further identifiers.
 */
public class RoCrateProfilesRegistry implements ProfileRegistry {
	public static final URI SPARQL = URI.create("https://query.ro-crate.org/rocrate/sparql");
	static final String QUERY = """
			PREFIX prof: <http://www.w3.org/ns/dx/prof/>
			PREFIX s: <http://schema.org/>
			PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
			PREFIX owl: <http://www.w3.org/2002/07/owl#>
			SELECT ?profile (SAMPLE(?n) AS ?name) (SAMPLE(?v) AS ?version) (SAMPLE(?d) AS ?descriptor)
			  (GROUP_CONCAT(DISTINCT STR(?alias); separator=" ") AS ?aliases)
			WHERE {
			  ?profile a prof:Profile ; prof:hasResource ?resource .
			  ?resource prof:hasRole <http://www.w3.org/ns/dx/prof/role/schema> ; s:hasPart ?rule .
			  ?rule a ?type . FILTER(?type IN (rdfs:Class, rdf:Property))
			  OPTIONAL { ?profile s:name ?n }
			  OPTIONAL { ?profile s:version ?v }
			  OPTIONAL { ?d s:about ?profile . FILTER(REGEX(STR(?d), "ro-crate-metadata\\\\.json(ld)?$")) }
			  OPTIONAL { ?alias owl:sameAs ?profile }
			}
			GROUP BY ?profile
			""";

	private final URI endpoint;
	private final HttpClient http;
	private final Duration timeout;

	public RoCrateProfilesRegistry(URI endpoint, HttpClient http, Duration timeout) {
		this.endpoint = endpoint;
		this.http = http;
		this.timeout = timeout;
	}

	@Override
	public String name() {
		return "profiles.ro-crate.org";
	}

	@Override
	public Optional<URI> address() {
		return Optional.of(endpoint);
	}

	@Override
	public List<Entry> entries() throws IOException, InterruptedException {
		URI query = URI.create(endpoint + "?query=" + URLEncoder.encode(QUERY, StandardCharsets.UTF_8));
		JsonNode json = ProfileRegistry.getJson(http, HttpRequest.newBuilder(query), timeout,
				"application/sparql-results+json");
		List<Entry> entries = new ArrayList<>();
		for (JsonNode row : json.path("results").path("bindings")) {
			Optional<String> profile = value(row, "profile");
			if (profile.isEmpty()) {
				continue;
			}
			List<String> identifiers = Stream.concat(Stream.of(profile.get()),
					value(row, "aliases").stream().flatMap(a -> Stream.of(a.split(" "))).filter(a -> !a.isBlank()))
					.toList();
			try {
				URI location = URI.create(value(row, "descriptor").orElse(profile.get()));
				entries.add(new Entry(identifiers, value(row, "name").orElse(profile.get()), value(row, "version"),
						Optional.empty(), location, name()));
			} catch (IllegalArgumentException e) {
				// Not a URL to load from
			}
		}
		return entries;
	}

	private static Optional<String> value(JsonNode row, String variable) {
		return Optional.of(row.path(variable).path("value").asText("")).filter(s -> !s.isBlank());
	}
}
