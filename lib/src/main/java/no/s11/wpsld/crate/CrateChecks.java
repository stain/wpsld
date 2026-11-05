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

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Checks of the RO-Crate 1.3 requirements (MUST) for the root data entity,
 * as reported by rocrate-validator at "required" level, and of references to
 * local identifiers that are not in the crate.
 * <p>
 * Not a full validator: the metadata descriptor is always written correctly
 * by {@link Crate#toJson()}, and profile rules (e.g. from RO-Crate MASP
 * profiles, see MASP.md) are not checked yet.
 */
public class CrateChecks {

	/**
	 * Problems found in the crate, empty if none.
	 */
	public static List<String> check(Crate crate) {
		List<String> problems = new ArrayList<>();
		Entity root = crate.getRoot();
		if (!root.getTypes().contains("Dataset")) {
			problems.add("The root data entity must have the type Dataset");
		}
		for (String property : List.of("name", "description", "license")) {
			if (root.getAll(property).isEmpty()) {
				problems.add("The root data entity must have a " + property);
			}
		}
		List<JsonNode> published = root.getAll("datePublished");
		if (published.isEmpty()) {
			problems.add("The root data entity must have a datePublished");
		} else if (published.size() > 1) {
			problems.add("The root data entity must have a single datePublished");
		} else if (!isIsoDate(published.get(0).asText())) {
			problems.add("The datePublished of the root data entity must be an ISO 8601 date, e.g. "
					+ LocalDate.now() + ", not " + published.get(0));
		}
		problems.addAll(danglingReferences(crate));
		return problems;
	}

	/**
	 * References to local identifiers (not absolute URIs) of entities that
	 * are not in the crate, e.g. a half-typed @id.
	 */
	private static List<String> danglingReferences(Crate crate) {
		List<String> problems = new ArrayList<>();
		for (Entity entity : crate.getEntities()) {
			entity.getValues().keySet().forEach(property -> entity.getAll(property).stream()
					.filter(v -> v.isObject() && v.has("@id")).map(v -> v.get("@id").asText())
					.filter(id -> crate.get(id).isEmpty() && !isAbsoluteUri(id))
					.forEach(id -> problems.add(entity.getLabel() + " has " + property + " " + id
							+ ", which is not an entity in the crate")));
		}
		return problems;
	}

	private static boolean isAbsoluteUri(String id) {
		try {
			return new URI(id).isAbsolute();
		} catch (URISyntaxException e) {
			return false;
		}
	}

	private static boolean isIsoDate(String text) {
		try {
			LocalDate.parse(text);
			return true;
		} catch (DateTimeParseException notDate) {
			try {
				OffsetDateTime.parse(text);
				return true;
			} catch (DateTimeParseException notDateTime) {
				return false;
			}
		}
	}
}
