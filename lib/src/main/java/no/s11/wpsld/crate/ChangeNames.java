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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;

import no.s11.wpsld.form.Labels;

/**
 * Names a change between two states of a crate's JSON-LD, for menu items
 * such as "Undo Add Person" or "Redo Edit “Date published”": what was added,
 * removed, renamed or edited, from the difference alone.
 */
public final class ChangeNames {
	/** For changes to several entities at once */
	public static final String CHANGES = "Changes";
	public static final String CHOOSE_PROFILE = "Choose Profile";

	private ChangeNames() {
	}

	/**
	 * The name of the change from <code>before</code> to <code>after</code>:
	 * <ul>
	 * <li>one entity added: "Add Person" (also if a field now refers to it);
	 * <li>one removed: "Remove Person" (references to it go too);
	 * <li>one removed and one added: "Change @id";
	 * <li>one entity's types changed: "Change Type";
	 * <li>one property of one entity: "Edit “Date published”", or for the
	 * root's <code>conformsTo</code> "Choose Profile";
	 * <li>otherwise "Edit Person" for several properties of one entity, and
	 * "Changes" for several entities.
	 * </ul>
	 * The metadata descriptor is left out, as it follows the root.
	 */
	public static String describe(JsonNode before, JsonNode after) {
		Map<String, JsonNode> earlier = entities(before);
		Map<String, JsonNode> later = entities(after);
		List<String> added = later.keySet().stream().filter(id -> !earlier.containsKey(id)).toList();
		List<String> removed = earlier.keySet().stream().filter(id -> !later.containsKey(id)).toList();
		if (added.size() == 1 && removed.size() == 1) {
			return "Change @id";
		}
		if (added.size() == 1 && removed.isEmpty()) {
			return "Add " + type(later.get(added.get(0)));
		}
		if (removed.size() == 1 && added.isEmpty()) {
			return "Remove " + type(earlier.get(removed.get(0)));
		}
		if (!added.isEmpty() || !removed.isEmpty()) {
			return CHANGES;
		}
		List<String> changed = later.keySet().stream().filter(id -> !Objects.equals(earlier.get(id), later.get(id)))
				.toList();
		if (changed.size() != 1) {
			return CHANGES;
		}
		JsonNode old = earlier.get(changed.get(0));
		JsonNode now = later.get(changed.get(0));
		Set<String> properties = new HashSet<>();
		old.fieldNames().forEachRemaining(properties::add);
		now.fieldNames().forEachRemaining(properties::add);
		List<String> edited = properties.stream().filter(p -> !Objects.equals(old.get(p), now.get(p))).sorted()
				.toList();
		if (edited.equals(List.of("@type"))) {
			return "Change Type";
		}
		if (edited.size() == 1) {
			String property = edited.get(0);
			if (property.equals("conformsTo") && isRoot(changed.get(0), before)) {
				return CHOOSE_PROFILE;
			}
			return "Edit “" + Labels.fromTerm(term(property)) + "”";
		}
		return "Edit " + type(now);
	}

	/** The first type of an entity, e.g. Person, as written */
	private static String type(JsonNode entity) {
		JsonNode type = entity.path("@type");
		String first = type.isArray() ? type.path(0).asText("") : type.asText("");
		return first.isEmpty() ? "Entity" : term(first);
	}

	/** The last part of a prefixed name or IRI, which is what Labels expects */
	private static String term(String name) {
		int cut = Math.max(Math.max(name.lastIndexOf(':'), name.lastIndexOf('/')), name.lastIndexOf('#'));
		return cut >= 0 && cut + 1 < name.length() ? name.substring(cut + 1) : name;
	}

	/** Whether this is the root data entity, as the metadata descriptor says */
	private static boolean isRoot(String id, JsonNode crate) {
		return StreamSupport.stream(crate.path("@graph").spliterator(), false)
				.filter(e -> isDescriptor(e.path("@id").asText()))
				.anyMatch(e -> id.equals(e.path("about").path("@id").asText()));
	}

	/**
	 * Whether an @id is the metadata descriptor's: ro-crate-metadata.json,
	 * also prefixed (a detached crate's) or as in RO-Crate 1.0 (.jsonld)
	 */
	private static boolean isDescriptor(String id) {
		String name = id.substring(id.lastIndexOf('/') + 1);
		return name.equals("ro-crate-metadata.json") || name.endsWith("-ro-crate-metadata.json")
				|| name.equals("ro-crate-metadata.jsonld");
	}

	/** Entities by @id, without the metadata descriptor */
	private static Map<String, JsonNode> entities(JsonNode crate) {
		Map<String, JsonNode> entities = new LinkedHashMap<>();
		crate.path("@graph").forEach(e -> {
			String id = e.path("@id").asText();
			if (!isDescriptor(id)) {
				entities.put(id, e);
			}
		});
		return entities;
	}
}
