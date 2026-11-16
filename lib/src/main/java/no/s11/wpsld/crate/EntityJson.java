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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * An entity's JSON-LD as typed or pasted by hand: checked, then put in place
 * of an entity's types and values. Entities written inside its values (as
 * ROHub writes a dataset's parts, <code>{"@id": …, "@type": "Dataset"}</code>)
 * are flattened as opening a crate does: referred to by their
 * <code>@id</code>, and added as entities of their own, or merged into the
 * crate's of that <code>@id</code>; those without one are added with a new
 * one. Links written with <code>@reverse</code> are made links of the
 * entities they come from, as flattening does. The text is untrusted: what doesn't fit is refused, saying why,
 * rather than guessed at.
 */
public final class EntityJson {
	private static final ObjectMapper MAPPER = new ObjectMapper();
	/** Keys of a value object: a literal with its language or datatype */
	private static final Set<String> VALUE_KEYS = Set.of("@value", "@language", "@type");
	/** How deep entities may be written in each other */
	static final int DEPTH = 20;
	/** Begins the stand-in @id of an entity written without one, until it is given one */
	private static final String STAND_IN = "\u0000nested-";

	private EntityJson() {
	}

	/**
	 * One entity, as parsed.
	 *
	 * @param id     Its <code>@id</code>
	 * @param types  Its <code>@type</code>s, at least one
	 * @param values Its other properties' values, in the order given (a
	 *               single value, or an array of several), entities written
	 *               in them as references
	 * @param nested The entities written in its values (and in theirs), in
	 *               the order written
	 */
	public record Parsed(String id, List<String> types, Map<String, JsonNode> values, List<Nested> nested,
			List<Reverse> reverse) {
	}

	/**
	 * A link written with <code>@reverse</code>: from the entity of the
	 * <code>@id</code> from, by the property, to the one it was written in
	 */
	public record Reverse(String from, String property, String to) {
	}

	/**
	 * An entity written in a value.
	 *
	 * @param id     Its <code>@id</code>, or a stand-in if it had none
	 * @param types  Its <code>@type</code>s, if any
	 * @param values Its other properties' values
	 */
	public record Nested(String id, List<String> types, Map<String, JsonNode> values) {
		/** Whether it was written without an <code>@id</code>, to be given one */
		public boolean anonymous() {
			return id.startsWith(STAND_IN);
		}
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
		List<String> types = types(json, "");
		if (types.isEmpty()) {
			throw new IllegalArgumentException(
					"Needs one or more @type, as text, e.g. \"@type\": \"Person\" or \"@type\": [\"File\", \"Dataset\"]");
		}
		List<Nested> nested = new ArrayList<>();
		List<Reverse> reverse = new ArrayList<>();
		Map<String, JsonNode> values = values(json, id.asText(), nested, reverse, 0);
		return new Parsed(id.asText(), types, values, List.copyOf(nested), List.copyOf(reverse));
	}

	/** An object's @type, as text, if any */
	private static List<String> types(JsonNode object, String where) {
		List<String> types = new ArrayList<>();
		JsonNode type = object.get("@type");
		if (type != null && type.isArray()) {
			type.forEach(t -> types.add(t.isTextual() ? t.asText() : ""));
		} else if (type != null) {
			types.add(type.isTextual() ? type.asText() : "");
		}
		if (types.stream().anyMatch(String::isBlank)) {
			throw new IllegalArgumentException(where + "@type should be text, e.g. \"Person\", or several in [ ]");
		}
		return List.copyOf(types);
	}

	/**
	 * An object's properties other than @id and @type, entities in them
	 * flattened into nested, and links written with @reverse into reverse
	 */
	private static Map<String, JsonNode> values(JsonNode object, String id, List<Nested> nested,
			List<Reverse> reverse, int depth) {
		Map<String, JsonNode> values = new LinkedHashMap<>();
		for (Map.Entry<String, JsonNode> property : object.properties()) {
			String name = property.getKey();
			if (name.equals("@id") || name.equals("@type")) {
				continue;
			}
			if (name.equals("@reverse") && property.getValue().isObject()) {
				for (Map.Entry<String, JsonNode> link : property.getValue().properties()) {
					if (link.getKey().startsWith("@") || link.getKey().isBlank()) {
						throw new IllegalArgumentException("@reverse has " + link.getKey() + ", not a property");
					}
					JsonNode from = link.getValue();
					for (JsonNode each : from.isArray() ? from : List.of(from)) {
						JsonNode ref = value("@reverse " + link.getKey(), each, nested, reverse, depth);
						if (!ref.isObject() || !ref.has("@id")) {
							throw new IllegalArgumentException("@reverse " + link.getKey()
									+ " should link from entities, {\"@id\": \"…\"}");
						}
						reverse.add(new Reverse(ref.get("@id").asText(), link.getKey(), id));
					}
				}
				continue;
			}
			if (name.startsWith("@") || name.isBlank()) {
				throw new IllegalArgumentException("Only @id and @type of the JSON-LD keywords, not " + name);
			}
			JsonNode value = property.getValue();
			if (value.isArray()) {
				ArrayNode flat = JsonNodeFactory.instance.arrayNode();
				value.forEach(each -> flat.add(value(name, each, nested, reverse, depth)));
				values.put(name, flat);
			} else {
				values.put(name, value(name, value, nested, reverse, depth));
			}
		}
		return values;
	}

	/**
	 * A value: text, a number or true/false, a reference, a literal of a
	 * language or datatype, or an entity, as a reference to it
	 */
	private static JsonNode value(String property, JsonNode value, List<Nested> nested, List<Reverse> reverse,
			int depth) {
		if (value.isNull()) {
			throw new IllegalArgumentException(property + " has null: leave the property out instead");
		}
		if (value.isArray()) {
			throw new IllegalArgumentException(property + " has an array in an array");
		}
		if (!value.isObject()) {
			return value;
		}
		if (value.has("@value")) {
			if (!VALUE_KEYS.containsAll(keys(value))) {
				throw new IllegalArgumentException(property + " has a value, {\"@value\": …}, with more than"
						+ " @language or @type");
			}
			return value;
		}
		JsonNode id = value.get("@id");
		if (id != null && (!id.isTextual() || id.asText().isBlank())) {
			throw new IllegalArgumentException(property + " has an @id that isn't text");
		}
		if (id != null && value.size() == 1) {
			return value;
		}
		// An entity written in the value
		if (depth >= DEPTH) {
			throw new IllegalArgumentException(property + " has entities written more than " + DEPTH + " deep");
		}
		if (value.isEmpty()) {
			throw new IllegalArgumentException(property + " has an empty object, {}");
		}
		String nestedId = id != null ? id.asText() : STAND_IN + (nested.size() + 1);
		List<String> types = types(value, property + ": ");
		// Its place kept, as entities written in it come after it
		int at = nested.size();
		nested.add(null);
		nested.set(at, new Nested(nestedId, types, values(value, nestedId, nested, reverse, depth + 1)));
		return JsonNodeFactory.instance.objectNode().put("@id", nestedId);
	}

	private static List<String> keys(JsonNode object) {
		List<String> keys = new ArrayList<>();
		object.fieldNames().forEachRemaining(keys::add);
		return keys;
	}

	/**
	 * What applying added and merged, besides the entity edited.
	 *
	 * @param added  Entities written in it, added to the crate
	 * @param merged Entities of the crate that some written in it were
	 *               merged into
	 * @param linked Entities of the crate linked to it by links written
	 *               with <code>@reverse</code>
	 */
	public record Applied(List<Entity> added, List<Entity> merged, List<Entity> linked) {
	}

	/**
	 * Put the types and values parsed in place of the entity's (not its
	 * <code>@id</code>, which renaming changes, with its references); the
	 * entities written in it added to the crate, or merged into its of the
	 * same <code>@id</code>, those without one given a new one
	 */
	public static Applied apply(Parsed parsed, Entity entity, Crate crate) {
		Map<String, String> given = new HashMap<>();
		Map<Nested, Entity> anonymous = new LinkedHashMap<>();
		for (Nested nested : parsed.nested()) {
			if (nested.anonymous()) {
				Entity added = crate.add(nested.types().isEmpty() ? "Thing" : nested.types().get(0));
				given.put(nested.id(), added.getId());
				anonymous.put(nested, added);
			}
		}
		entity.setTypes(parsed.types());
		for (String property : List.copyOf(entity.getValues().keySet())) {
			if (!parsed.values().containsKey(property)) {
				entity.set(property, Optional.empty());
			}
		}
		parsed.values().forEach((property, value) -> entity.setAll(property, all(value, given)));

		Set<Entity> added = new LinkedHashSet<>(anonymous.values());
		Set<Entity> merged = new LinkedHashSet<>();
		for (Nested nested : parsed.nested()) {
			Entity target = anonymous.get(nested);
			if (target != null) {
				if (!nested.types().isEmpty()) {
					target.setTypes(nested.types());
				}
				nested.values().forEach((property, value) -> target.setAll(property, all(value, given)));
				continue;
			}
			Optional<Entity> existing = crate.get(nested.id());
			if (existing.isPresent()) {
				Entity copy = new Entity(nested.id(), (nested.types().isEmpty() ? existing.get().getTypes()
						: nested.types()).toArray(String[]::new));
				nested.values().forEach((property, value) -> copy.setAll(property, all(value, given)));
				crate.merge(existing.get(), copy);
				if (existing.get() != entity && !added.contains(existing.get())) {
					merged.add(existing.get());
				}
			} else {
				Entity add = crate.add(nested.types().isEmpty() ? "Thing" : nested.types().get(0), nested.id());
				add.setTypes(nested.types().isEmpty() ? List.of("Thing") : nested.types());
				nested.values().forEach((property, value) -> add.setAll(property, all(value, given)));
				added.add(add);
			}
		}
		Set<Entity> linked = new LinkedHashSet<>();
		for (Reverse link : parsed.reverse()) {
			String fromId = given.getOrDefault(link.from(), link.from());
			// To the entity edited by its @id now, renamed after with its references
			String toId = link.to().equals(parsed.id()) ? entity.getId() : given.getOrDefault(link.to(), link.to());
			Entity from = crate.get(fromId).orElseGet(() -> {
				Entity thing = crate.add("Thing", fromId);
				added.add(thing);
				return thing;
			});
			JsonNode to = JsonNodeFactory.instance.objectNode().put("@id", toId);
			List<JsonNode> values = new ArrayList<>(from.getAll(link.property()));
			if (!values.contains(to)) {
				values.add(to);
				from.setAll(link.property(), values);
			}
			if (!added.contains(from) && from != entity) {
				linked.add(from);
			}
		}
		return new Applied(List.copyOf(added), List.copyOf(merged), List.copyOf(linked));
	}

	/** A property's values, references to stand-ins to the @ids given */
	private static List<JsonNode> all(JsonNode value, Map<String, String> given) {
		List<JsonNode> all = new ArrayList<>();
		for (JsonNode each : value.isArray() ? value : List.of(value)) {
			String id = each.path("@id").asText(null);
			all.add(id != null && given.containsKey(id)
					? JsonNodeFactory.instance.objectNode().put("@id", given.get(id))
					: each);
		}
		return all;
	}
}
