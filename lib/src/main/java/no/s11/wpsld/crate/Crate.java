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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The entities of an RO-Crate, starting with its root data entity
 * <code>./</code>. The metadata descriptor is not an entity here, it is
 * added when writing.
 */
public class Crate {
	public static final String RO_CRATE_METADATA_JSON = "ro-crate-metadata.json";
	public static final String RO_CRATE_1_3 = "https://w3id.org/ro/crate/1.3";
	public static final String RO_CRATE_1_3_CONTEXT = RO_CRATE_1_3 + "/context";
	public static final String ROOT_ID = "./";

	private final List<Entity> entities = new ArrayList<>();

	public Crate() {
		entities.add(new Entity(ROOT_ID, "Dataset"));
	}

	public Entity getRoot() {
		return entities.get(0);
	}

	public List<Entity> getEntities() {
		return Collections.unmodifiableList(entities);
	}

	public Optional<Entity> get(String id) {
		return entities.stream().filter(e -> e.getId().equals(id)).findFirst();
	}

	/**
	 * Add an entity with a new local identifier like <code>#person-1</code>.
	 */
	public Entity add(String type) {
		String prefix = "#" + type.toLowerCase() + "-";
		int n = 1;
		while (get(prefix + n).isPresent()) {
			n++;
		}
		Entity entity = new Entity(prefix + n, type);
		entities.add(entity);
		return entity;
	}

	/**
	 * Remove an entity, except the root data entity.
	 */
	public boolean remove(Entity entity) {
		if (entity == getRoot()) {
			throw new IllegalArgumentException("Can't remove the root data entity");
		}
		return entities.remove(entity);
	}

	/**
	 * The crate as flattened RO-Crate 1.3 JSON-LD, with the metadata
	 * descriptor first.
	 */
	public ObjectNode toJson() {
		ObjectNode crate = JsonNodeFactory.instance.objectNode();
		crate.put("@context", RO_CRATE_1_3_CONTEXT);
		ArrayNode graph = crate.putArray("@graph");
		ObjectNode descriptor = graph.addObject();
		descriptor.put("@id", RO_CRATE_METADATA_JSON);
		descriptor.put("@type", "CreativeWork");
		descriptor.putObject("about").put("@id", ROOT_ID);
		descriptor.putObject("conformsTo").put("@id", RO_CRATE_1_3);
		entities.forEach(e -> graph.add(e.toJson()));
		return crate;
	}
}
