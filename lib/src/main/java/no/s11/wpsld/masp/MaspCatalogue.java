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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The catalogue of the RO-Crate MASP example profiles, the
 * <code>catalogue.json</code> its landing page is made from:
 * <code>{"entries": [{"kind": "profile", "name", "version", "status",
 * "crate": "profiles/x/profile-crate/ro-crate-metadata.json"}, …]}</code>,
 * paths relative to the catalogue. Schemas (<code>"kind": "schema"</code>)
 * are left out. The catalogue gives no identifiers; MASP profiles use their
 * <code>profile-crate/</code> folder's URL, as the bundled core profile does.
 */
public class MaspCatalogue implements ProfileRegistry {
	public static final URI CATALOGUE = URI
			.create("https://language-research-technology.github.io/ro-crate-masp/catalogue.json");

	private final URI catalogue;
	private final HttpClient http;
	private final Duration timeout;

	public MaspCatalogue(URI catalogue, HttpClient http, Duration timeout) {
		this.catalogue = catalogue;
		this.http = http;
		this.timeout = timeout;
	}

	@Override
	public String name() {
		return "RO-Crate MASP examples";
	}

	@Override
	public Optional<URI> address() {
		return Optional.of(catalogue);
	}

	@Override
	public List<Entry> entries() throws IOException, InterruptedException {
		JsonNode json = ProfileRegistry.getJson(http, HttpRequest.newBuilder(catalogue), timeout, "application/json");
		List<Entry> entries = new ArrayList<>();
		for (JsonNode entry : json.path("entries")) {
			String crate = entry.path("crate").asText("");
			if (!entry.path("kind").asText().equals("profile") || crate.isEmpty() || entry.path("name").asText("").isEmpty()) {
				continue;
			}
			URI location;
			try {
				location = catalogue.resolve(crate);
			} catch (IllegalArgumentException e) {
				continue;
			}
			List<String> identifiers = location.getPath().endsWith("/ro-crate-metadata.json")
					? List.of(location.resolve("./").toString())
					: List.of();
			entries.add(new Entry(identifiers, entry.path("name").asText(), text(entry, "version"),
					text(entry, "status"), location, name()));
		}
		return entries;
	}

	private static Optional<String> text(JsonNode entry, String field) {
		return Optional.of(entry.path(field).asText("")).filter(s -> !s.isBlank());
	}
}
