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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * An entity of an RO-Crate: its <code>@id</code>, <code>@type</code>s and
 * property values, as compacted JSON-LD.
 * <p>
 * Values are kept whatever the entity's types are, so changing the type
 * doesn't lose values of properties the new type doesn't have; they can
 * still be seen, removed, or used again when changing back.
 */
public class Entity {
	private String id;
	private List<String> types;
	private final Map<String, JsonNode> values = new LinkedHashMap<>();

	public Entity(String id, String... types) {
		setId(id);
		setTypes(List.of(types));
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = Objects.requireNonNull(id);
	}

	/**
	 * Compacted type names as used in RO-Crate, e.g. <code>Dataset</code>
	 */
	public List<String> getTypes() {
		return types;
	}

	public void setTypes(List<String> types) {
		if (types.isEmpty()) {
			throw new IllegalArgumentException("An entity must have at least one @type");
		}
		this.types = List.copyOf(types);
	}

	/**
	 * All property values, keyed by compacted property name, in the order
	 * they were added.
	 */
	public Map<String, JsonNode> getValues() {
		return Collections.unmodifiableMap(values);
	}

	public Optional<JsonNode> get(String property) {
		return Optional.ofNullable(values.get(property));
	}

	/**
	 * Set the value of a property, or remove it if the value is empty.
	 */
	public void set(String property, Optional<JsonNode> value) {
		if (property.startsWith("@")) {
			throw new IllegalArgumentException("Use setId/setTypes for " + property);
		}
		value.ifPresentOrElse(v -> values.put(property, v), () -> values.remove(property));
	}

	/**
	 * All values of a property: the elements of a JSON array, the single
	 * value, or none.
	 */
	public List<JsonNode> getAll(String property) {
		return get(property).map(v -> v.isArray() ? StreamSupport.stream(v.spliterator(), false).toList() : List.of(v))
				.orElse(List.of());
	}

	/**
	 * Set all values of a property: removed if none, a single value as is
	 * and several as a JSON array, as in flattened RO-Crate JSON-LD.
	 */
	public void setAll(String property, List<JsonNode> values) {
		set(property, switch (values.size()) {
		case 0 -> Optional.empty();
		case 1 -> Optional.of(values.get(0));
		default -> Optional.of(JsonNodeFactory.instance.arrayNode().addAll(values));
		});
	}

	/**
	 * The <code>name</code> if it is text, otherwise the <code>@id</code>.
	 */
	public String getLabel() {
		return get("name").filter(JsonNode::isTextual).map(JsonNode::asText).filter(n -> !n.isBlank()).orElse(id);
	}

	/**
	 * The entity as a node of a flattened JSON-LD <code>@graph</code>.
	 */
	public ObjectNode toJson() {
		ObjectNode json = JsonNodeFactory.instance.objectNode();
		json.put("@id", id);
		if (types.size() == 1) {
			json.put("@type", types.get(0));
		} else {
			types.forEach(json.putArray("@type")::add);
		}
		values.forEach(json::set);
		return json;
	}
}
