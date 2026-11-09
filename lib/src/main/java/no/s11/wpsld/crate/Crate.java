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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

/**
 * The entities of an RO-Crate, starting with its root data entity
 * <code>./</code>. The metadata descriptor is not an entity here, it is
 * added when writing.
 */
public class Crate {
	public static final String RO_CRATE_METADATA_JSON = "ro-crate-metadata.json";
	/** The crate's HTML preview, beside its metadata file */
	public static final String RO_CRATE_PREVIEW_HTML = "ro-crate-preview.html";

	/**
	 * Writes an RO-Crate Metadata File as Jackson's pretty printer does, but
	 * with \n line ends on every platform rather than the system's (\r\n on
	 * Windows), so that a crate saved on any of them is the same file, e.g.
	 * for git. See {@link #writer(String)} to keep a file's own line ends.
	 */
	public static final ObjectWriter WRITER = writer("\n");

	/**
	 * Writes an RO-Crate Metadata File with these line ends, e.g. \r\n for
	 * a file that had them when opened.
	 */
	public static ObjectWriter writer(String lineEnd) {
		return new ObjectMapper()
				.writer(new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", lineEnd)));
	}
	/** The RO-Crate specification's versions start with this */
	public static final String RO_CRATE_SPEC = "https://w3id.org/ro/crate/";
	public static final String RO_CRATE_1_3 = "https://w3id.org/ro/crate/1.3";
	public static final String RO_CRATE_1_3_CONTEXT = RO_CRATE_1_3 + "/context";
	/** Identifier of a new crate's root data entity; it may also be an absolute URI */
	public static final String ROOT_ID = "./";
	/**
	 * Properties of the root data entity that link the crate's entities to
	 * it: its parts (data entities) and the other entities it mentions
	 */
	public static final List<String> ROOT_LINKS = List.of("hasPart", "mentions");

	private final List<Entity> entities = new ArrayList<>();
	/** The <code>@context</code> to write, as read or RO-Crate 1.3's */
	private final JsonNode context;
	/** The metadata descriptor as read, to write again; else a new one for 1.3 */
	private final Optional<Entity> descriptor;

	public Crate() {
		entities.add(new Entity(ROOT_ID, "Dataset"));
		context = TextNode.valueOf(RO_CRATE_1_3_CONTEXT);
		descriptor = Optional.empty();
	}

	private Crate(Entity root, List<Entity> others, JsonNode context, Optional<Entity> descriptor) {
		entities.add(root);
		entities.addAll(others);
		this.context = context.deepCopy();
		this.descriptor = descriptor;
	}

	/**
	 * A crate of entities read from a file, with its root data entity first,
	 * written again with its own <code>@context</code> and metadata
	 * descriptor, so with the same RO-Crate version and extra terms.
	 */
	static Crate of(Entity root, List<Entity> others, JsonNode context, Optional<Entity> descriptor) {
		return new Crate(root, others, context, descriptor);
	}

	/**
	 * The <code>@context</code> the crate is written with.
	 */
	public JsonNode getContext() {
		return context.deepCopy();
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
		// e.g. #person-1, or #ldac-speaker-1 for ldac:Speaker
		String prefix = "#" + type.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-") + "-";
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
	 * Change an entity's <code>@id</code>, updating all references to it from
	 * the crate's entities. Use {@link #merge} if another entity already has
	 * that identifier.
	 *
	 * @throws IllegalArgumentException if another entity has the new identifier
	 */
	public void rename(Entity entity, String newId) {
		String oldId = entity.getId();
		if (oldId.equals(newId)) {
			return;
		}
		if (get(newId).isPresent()) {
			throw new IllegalArgumentException("Another entity already has @id " + newId);
		}
		entity.setId(newId);
		replaceReferences(oldId, newId);
	}

	/**
	 * Merge another entity into this one: its types and values are added
	 * (values of the same property are combined, without duplicates), it is
	 * removed from the crate, and references to it now point to the entity.
	 */
	public void merge(Entity entity, Entity other) {
		if (entity == other) {
			return;
		}
		List<String> types = new ArrayList<>(entity.getTypes());
		other.getTypes().stream().filter(t -> !types.contains(t)).forEach(types::add);
		entity.setTypes(types);
		other.getValues().keySet().forEach(property -> {
			List<JsonNode> values = new ArrayList<>(entity.getAll(property));
			other.getAll(property).stream().filter(v -> !values.contains(v)).forEach(values::add);
			entity.setAll(property, values);
		});
		if (other == getRoot()) {
			// The root data entity stays first, now as the merged entity
			entities.set(0, entity);
			entities.remove(entities.lastIndexOf(entity));
		} else {
			entities.remove(other);
		}
		replaceReferences(other.getId(), entity.getId());
	}

	/**
	 * The data entities: the root data entity's parts (<code>hasPart</code>),
	 * their parts, and so on. A File or Dataset only linked otherwise, e.g.
	 * as a <code>citation</code>, is a contextual entity.
	 */
	public List<Entity> dataEntities() {
		Map<String, Entity> byId = byId();
		Set<Entity> parts = new LinkedHashSet<>();
		Deque<Entity> queue = new ArrayDeque<>(List.of(getRoot()));
		while (!queue.isEmpty()) {
			references(queue.poll(), "hasPart").map(byId::get)
					.filter(e -> e != null && e != getRoot() && parts.add(e)).forEach(queue::add);
		}
		return List.copyOf(parts);
	}

	/**
	 * Entities not linked from the root data entity: not referred to by any
	 * of its properties (e.g. its parts in <code>hasPart</code>, or
	 * <code>mentions</code>), nor by any property of an entity that is,
	 * recursively. These are left dangling, see {@link #mentionUnlinked()}.
	 * The metadata descriptor and the HTML preview need no link.
	 */
	public List<Entity> unlinked() {
		Entity root = getRoot();
		Set<Entity> linked = linkedFrom(root.getId(), List.copyOf(root.getValues().keySet()), new HashSet<>());
		return entities.stream().filter(e -> e != root && !linked.contains(e) && !isCrateFile(e)).toList();
	}

	/**
	 * Add the entities not linked from the root data entity (see
	 * {@link #unlinked()}) to its <code>mentions</code>, so that none is left
	 * dangling: in the crate's order, leaving out those linked from an entity
	 * mentioned before.
	 *
	 * @return The entities added to <code>mentions</code>
	 */
	public List<Entity> mentionUnlinked() {
		Entity root = getRoot();
		Set<Entity> linked = linkedFrom(root.getId(), List.copyOf(root.getValues().keySet()), new HashSet<>());
		List<Entity> mentioned = new ArrayList<>();
		for (Entity entity : entities) {
			if (entity != root && !linked.contains(entity) && !isCrateFile(entity)) {
				mentioned.add(entity);
				linked.add(entity);
				linkedFrom(entity.getId(), List.copyOf(entity.getValues().keySet()), linked);
			}
		}
		if (!mentioned.isEmpty()) {
			List<JsonNode> mentions = new ArrayList<>(root.getAll("mentions"));
			mentioned.forEach(e -> mentions.add(JsonNodeFactory.instance.objectNode().put("@id", e.getId())));
			root.setAll("mentions", mentions);
		}
		return mentioned;
	}

	/**
	 * Add to <code>linked</code> the entities referred to by these properties
	 * of an entity, and recursively those referred to by any of their
	 * properties.
	 */
	private Set<Entity> linkedFrom(String id, List<String> properties, Set<Entity> linked) {
		Map<String, Entity> byId = byId();
		Deque<Entity> queue = new ArrayDeque<>();
		Entity start = byId.get(id);
		for (String property : properties) {
			references(start, property).map(byId::get).filter(e -> e != null && linked.add(e)).forEach(queue::add);
		}
		while (!queue.isEmpty()) {
			Entity entity = queue.poll();
			for (String property : entity.getValues().keySet()) {
				references(entity, property).map(byId::get).filter(e -> e != null && linked.add(e))
						.forEach(queue::add);
			}
		}
		return linked;
	}

	/**
	 * Whether the entity is the crate's metadata descriptor or HTML preview,
	 * by its <code>@id</code> relative to the crate or to the root's.
	 */
	private boolean isCrateFile(Entity entity) {
		return List.of(RO_CRATE_METADATA_JSON, RO_CRATE_PREVIEW_HTML).stream()
				.anyMatch(name -> entity.getId().equals(name) || entity.getId().equals(getRoot().getId() + name));
	}

	private Map<String, Entity> byId() {
		Map<String, Entity> byId = new HashMap<>();
		entities.forEach(e -> byId.putIfAbsent(e.getId(), e));
		return byId;
	}

	private static Stream<String> references(Entity entity, String property) {
		return entity.getAll(property).stream().filter(v -> v.isObject() && v.has("@id"))
				.map(v -> v.get("@id").asText());
	}

	private void replaceReferences(String oldId, String newId) {
		for (Entity entity : entities) {
			for (String property : List.copyOf(entity.getValues().keySet())) {
				List<JsonNode> values = entity.getAll(property);
				List<JsonNode> replaced = values.stream().map(v -> replaceReference(v, oldId, newId)).toList();
				// Once each, e.g. mentions of two entities merged
				entity.setAll(property, replaced.equals(values) ? values : replaced.stream().distinct().toList());
			}
		}
	}

	private static JsonNode replaceReference(JsonNode value, String oldId, String newId) {
		if (value.isObject() && value.path("@id").asText("").equals(oldId)) {
			ObjectNode copy = ((ObjectNode) value).deepCopy();
			copy.put("@id", newId);
			return copy;
		}
		return value;
	}

	/**
	 * The crate as flattened RO-Crate JSON-LD, with the metadata descriptor
	 * first: for a new crate RO-Crate 1.3, for one read from a file with its
	 * own context and descriptor.
	 */
	public ObjectNode toJson() {
		ObjectNode crate = JsonNodeFactory.instance.objectNode();
		crate.set("@context", context.deepCopy());
		ArrayNode graph = crate.putArray("@graph");
		if (this.descriptor.isPresent()) {
			ObjectNode descriptor = this.descriptor.get().toJson();
			// About the root, also if it was renamed
			descriptor.putObject("about").put("@id", getRoot().getId());
			graph.add(descriptor);
		} else {
			ObjectNode descriptor = graph.addObject();
			descriptor.put("@id", RO_CRATE_METADATA_JSON);
			descriptor.put("@type", "CreativeWork");
			descriptor.putObject("about").put("@id", getRoot().getId());
			descriptor.putObject("conformsTo").put("@id", RO_CRATE_1_3);
		}
		entities.forEach(e -> graph.add(e.toJson()));
		return crate;
	}
}
