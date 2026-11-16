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
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import no.s11.wpsld.crate.Issue.Severity;

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
		return issues(crate).stream().map(Issue::message).toList();
	}

	/**
	 * The problems found in the crate, with where they are and what they are
	 * of (see {@link Issue}); empty if none
	 */
	public static List<Issue> issues(Crate crate) {
		List<Issue> issues = new ArrayList<>();
		Entity root = crate.getRoot();
		if (!root.getTypes().contains("Dataset")) {
			issues.add(Issue.of(Severity.REQUIRED, "rocrate:root-type", "Root data entity: type Dataset",
					"The root data entity must have the type Dataset", root, Optional.of("@type"), Optional.empty()));
		}
		for (String property : List.of("name", "description", "license")) {
			if (root.getAll(property).isEmpty()) {
				issues.add(Issue.of(Severity.REQUIRED, "rocrate:root-" + property, "Root data entity: " + property,
						"The root data entity must have a " + property, root, Optional.of(property), Optional.empty()));
			}
		}
		List<JsonNode> published = root.getAll("datePublished");
		String date = "Root data entity: datePublished";
		if (published.isEmpty()) {
			issues.add(Issue.of(Severity.REQUIRED, "rocrate:root-datePublished", date,
					"The root data entity must have a datePublished", root, Optional.of("datePublished"), Optional.empty()));
		} else if (published.size() > 1) {
			issues.add(Issue.of(Severity.REQUIRED, "rocrate:root-datePublished", date,
					"The root data entity must have a single datePublished", root, Optional.of("datePublished"),
					Optional.empty()));
		} else if (!isIsoDate(published.get(0).asText())) {
			issues.add(Issue.of(Severity.REQUIRED, "rocrate:root-datePublished", date,
					"The datePublished of the root data entity must be an ISO 8601 date, e.g. " + LocalDate.now()
							+ ", not " + published.get(0),
					root, Optional.of("datePublished"), Optional.of(published.get(0).asText())));
		}
		issues.addAll(danglingReferences(crate));
		return issues;
	}

	/**
	 * References to local identifiers (not absolute URIs) of entities that
	 * are not in the crate, e.g. a half-typed @id.
	 */
	private static List<Issue> danglingReferences(Crate crate) {
		List<Issue> issues = new ArrayList<>();
		for (Entity entity : crate.getEntities()) {
			entity.getValues().keySet().forEach(property -> entity.getAll(property).stream()
					.filter(v -> v.isObject() && v.has("@id")).map(v -> v.get("@id").asText())
					.filter(id -> crate.get(id).isEmpty() && !isAbsoluteUri(id))
					.forEach(id -> issues.add(new Issue(Severity.REQUIRED, "rocrate:reference",
							"References to entities in the crate", Issue.RO_CRATE, entity.getLabel() + " has "
									+ property + " " + id + ", which is not an entity in the crate",
							Optional.of(entity.getId()), Optional.of(property), Optional.of(id)))));
		}
		return issues;
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
