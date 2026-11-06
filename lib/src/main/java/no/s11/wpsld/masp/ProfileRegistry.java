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
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import no.s11.wpsld.crate.CrateRetriever;

/**
 * A registry listing RO-Crate profiles and where their profile crates are,
 * such as the MASP catalogue or profiles.ro-crate.org. Only profiles with
 * MASP rules are listed, as only those can be loaded.
 */
public interface ProfileRegistry {

	/**
	 * A profile listed by a registry.
	 *
	 * @param identifiers The identifiers crates use in <code>conformsTo</code>
	 *                    for it, as far as the registry says
	 * @param name        Its name
	 * @param version     Its version, if given
	 * @param status      E.g. "draft" or "experimental", if given
	 * @param location    Where to load its profile crate from
	 * @param registry    The name of the registry listing it
	 */
	record Entry(List<String> identifiers, String name, Optional<String> version, Optional<String> status,
			URI location, String registry) {
		public Entry {
			identifiers = List.copyOf(identifiers);
		}

		/**
		 * Whether crates name this profile with an identifier, ignoring a
		 * trailing slash.
		 */
		public boolean identifiedBy(String identifier) {
			String id = identifier.replaceFirst("/$", "");
			return identifiers.stream().anyMatch(i -> i.replaceFirst("/$", "").equals(id))
					|| location.toString().equals(identifier);
		}

		/**
		 * The name with version and status, to show.
		 */
		public String label() {
			return name + version.map(v -> " " + v).orElse("") + status.map(s -> " (" + s + ")").orElse("");
		}
	}

	/**
	 * The registry's name, to show.
	 */
	String name();

	/**
	 * Where it is read from, to show.
	 */
	default Optional<URI> address() {
		return Optional.empty();
	}

	/**
	 * The profiles listed.
	 *
	 * @throws IOException With a message to show if the registry can't be
	 *                     read
	 */
	List<Entry> entries() throws IOException, InterruptedException;

	/**
	 * The registries known to wpsld: the RO-Crate MASP catalogue and
	 * profiles.ro-crate.org.
	 */
	static List<ProfileRegistry> known(HttpClient http, Duration timeout) {
		return List.of(new MaspCatalogue(MaspCatalogue.CATALOGUE, http, timeout),
				new RoCrateProfilesRegistry(RoCrateProfilesRegistry.SPARQL, http, timeout));
	}

	/**
	 * GET JSON, for the implementations.
	 */
	static JsonNode getJson(HttpClient http, HttpRequest.Builder request, Duration timeout, String accept)
			throws IOException, InterruptedException {
		HttpResponse<InputStream> response = http.send(request.timeout(timeout).header("Accept", accept).GET().build(),
				HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream in = response.body()) {
			if (response.statusCode() != 200) {
				throw new IOException("HTTP " + response.statusCode() + " from " + response.uri());
			}
			return new ObjectMapper().readTree(in.readNBytes(CrateRetriever.MAX_BYTES));
		}
	}
}
