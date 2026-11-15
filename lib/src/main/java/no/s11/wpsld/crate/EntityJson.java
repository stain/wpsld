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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An entity's JSON-LD as typed or pasted by hand: checked to be one entity
 * of flattened RO-Crate JSON-LD, then put in place of an entity's types and
 * values. The text is untrusted: what doesn't fit is refused, saying why,
 * rather than guessed at.
 */
public final class EntityJson {
	private static final ObjectMapper MAPPER = new ObjectMapper();
	/** Keys of a value object: a reference, or a literal with its language or datatype */
	private static final Set<String> VALUE_KEYS = Set.of("@value", "@language", "@type");

	private EntityJson() {
	}

	/**
	 * One entity, as parsed.
	 *
	 * @param id     Its <code>@id</code>
	 * @param types  Its <code>@type</code>s, at least one
	 * @param values Its other properties' values, in the order given (a
	 *               single value, or an array of several)
	 */
	public record Parsed(String id, List<String> types, Map<String, JsonNode> values) {
	}

	/**
	 * Parse and check an entity's JSON-LD.
	 *
	 * @throws IllegalArgumentException saying what is wrong, for people to
	 *                                  read
	 */
	public static Parsed parse(String text) {
		JsonNode json;
		try {
			json = MAPPER.readTree(text);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Not JSON: " + e.getOriginalMessage()
					+ (e.getLocation() == null ? ""
							: " (line " + e.getLocation().getLineNr() + ", column " + e.getLocation().getColumnNr()
									+ ")"));
		}
		if (json == null || !json.isObject()) {
			throw new IllegalArgumentException("Should be one JSON object, {\"@id\": …, \"@type\": …, …}");
		}
		if (json.has("@graph") || json.has("@context")) {
			throw new IllegalArgumentException("A JSON-LD document, not one entity: to add its entities, copy it"
					+ " and paste it in the entity list (Edit > Paste)");
		}
		JsonNode id = json.get("@id");
		if (id == null || !id.isTextual() || id.asText().isBlank()) {
			throw new IllegalArgumentException("Needs an @id, as text, e.g. \"@id\": \"#person-1\"");
		}
		List<String> types = new ArrayList<>();
		JsonNode type = json.get("@type");
		if (type != null && type.isArray()) {
			type.forEach(t -> types.add(t.isTextual() ? t.asText() : ""));
		} else if (type != null && type.isTextual()) {
			types.add(type.asText());
		}
		if (types.isEmpty() || types.stream().anyMatch(String::isBlank)) {
			throw new IllegalArgumentException(
					"Needs one or more @type, as text, e.g. \"@type\": \"Person\" or \"@type\": [\"File\", \"Dataset\"]");
		}
		Map<String, JsonNode> values = new LinkedHashMap<>();
		for (Map.Entry<String, JsonNode> property : json.properties()) {
			String name = property.getKey();
			if (name.equals("@id") || name.equals("@type")) {
				continue;
			}
			if (name.startsWith("@") || name.isBlank()) {
				throw new IllegalArgumentException("Only @id and @type of the JSON-LD keywords, not " + name);
			}
			JsonNode value = property.getValue();
			for (JsonNode each : value.isArray() ? value : List.of(value)) {
				check(name, each);
			}
			values.put(name, value);
		}
		return new Parsed(id.asText(), List.copyOf(types), values);
	}

	/** A value: text, a number or true/false, a reference, or a literal of a language or datatype */
	private static void check(String property, JsonNode value) {
		if (value.isNull()) {
			throw new IllegalArgumentException(property + " has null: leave the property out instead");
		}
		if (value.isArray()) {
			throw new IllegalArgumentException(property + " has an array in an array");
		}
		if (!value.isObject()) {
			return;
		}
		if (value.has("@id")) {
			if (value.size() != 1 || !value.get("@id").isTextual()) {
				throw new IllegalArgumentException(property + " has an entity in it: refer to it by its @id alone,"
						+ " {\"@id\": \"…\"}, and describe it as an entity of its own");
			}
			return;
		}
		if (!value.has("@value") || !VALUE_KEYS.containsAll(keys(value))) {
			throw new IllegalArgumentException(property + " has an object that is neither a reference,"
					+ " {\"@id\": \"…\"}, nor a value, {\"@value\": \"…\"}");
		}
	}

	private static List<String> keys(JsonNode object) {
		List<String> keys = new ArrayList<>();
		object.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	/**
	 * Put the types and values parsed in place of the entity's (not its
	 * <code>@id</code>, which renaming changes, with its references)
	 */
	public static void apply(Parsed parsed, Entity entity) {
		entity.setTypes(parsed.types());
		for (String property : List.copyOf(entity.getValues().keySet())) {
			if (!parsed.values().containsKey(property)) {
				entity.set(property, Optional.empty());
			}
		}
		parsed.values().forEach((property, value) -> {
			List<JsonNode> all = new ArrayList<>();
			(value.isArray() ? value : List.of(value)).forEach(all::add);
			entity.setAll(property, all);
		});
	}
}
