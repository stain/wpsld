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
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
		Entity entity = new Entity(newId(type), type);
		entities.add(entity);
		return entity;
	}

	/**
	 * What {@link #paste(List, Map, boolean)} did.
	 *
	 * @param added     The entities added, in the order pasted
	 * @param existing  Copies whose <code>@id</code> (absolute, or a relative
	 *                  path) names an entity the crate already has, which
	 *                  differs: each with that entity, to merge into it
	 *                  (see {@link Crate#merge}) or leave out. Copies the
	 *                  same as the crate's entity are left out silently.
	 * @param renamed   New <code>@id</code>s of copies, by their own
	 * @param dangling  <code>@id</code>s the pasted entities refer to, not
	 *                  pasted, that the crate has no entity for
	 * @param ambiguous Local <code>@id</code>s the pasted entities refer to,
	 *                  not pasted, that the crate has an entity for, but
	 *                  from another crate and not as copied: it may be
	 *                  another entity of the same <code>@id</code>
	 */
	public record Pasted(List<Entity> added, Map<Entity, Entity> existing, Map<String, String> renamed,
			List<String> dangling, List<String> ambiguous) {
	}

	/**
	 * Add a copy of an entity, see {@link #paste(List, Map, boolean)}.
	 *
	 * @return The entity added, or else the crate's own of its
	 *         <code>@id</code>
	 */
	public Entity paste(Entity copy) {
		Pasted pasted = paste(List.of(copy), Map.of(), false);
		return pasted.added().isEmpty() ? get(copy.getId()).orElseThrow() : pasted.added().get(0);
	}

	/**
	 * Add copies of entities, e.g. pasted from the clipboard, with the
	 * values copied too. Their <code>@id</code>s:
	 * <ul>
	 * <li>absolute ones (<code>https:</code>, <code>mailto:</code>,
	 * <code>urn:</code>, …) name the same entity in any crate, and relative
	 * paths the crate's own file or folder: kept, and if the crate already
	 * has an entity with it, not added, but given in
	 * {@link Pasted#existing()} to merge (unless the same);
	 * <li>local identifiers (<code>#bob</code>) are kept, or if taken, a new
	 * one as for {@link #add(String)} of the first type;
	 * <li>blank nodes (<code>_:b0</code>) always get a new local identifier,
	 * as they only mean something within their document.
	 * </ul>
	 * References among the copies follow their new <code>@id</code>s, not
	 * references of the crate's own entities. Other references are kept as
	 * they are (a shallow copy), and reported if they may be wrong.
	 *
	 * @param referenced The entities the copies refer to that were not
	 *                   copied, as they were when copied, by
	 *                   <code>@id</code>: tells whether the crate's entity
	 *                   of the same <code>@id</code> is the one meant
	 * @param sameCrate  Whether copied from this crate, so that references
	 *                   to its entities are right as they are
	 */
	public Pasted paste(List<Entity> copies, Map<String, Entity> referenced, boolean sameCrate) {
		Map<String, String> renamed = new LinkedHashMap<>();
		Map<Entity, Entity> existing = new LinkedHashMap<>();
		List<Entity> added = new ArrayList<>();
		Set<String> copiedIds = new HashSet<>();
		for (Entity copy : copies) {
			String id = copy.getId();
			copiedIds.add(id);
			Optional<Entity> taken = get(id);
			Entity entity = new Entity(id, copy.getTypes().toArray(String[]::new));
			copy.getValues().forEach((property, value) -> entity.set(property, Optional.of(value.deepCopy())));
			if (isBlankNode(id) || (isLocal(id) && taken.isPresent())) {
				entity.setId(newId(copy.getTypes().get(0)));
				renamed.put(id, entity.getId());
			} else if (taken.isPresent()) {
				if (!taken.get().toJson().equals(copy.toJson())) {
					existing.put(entity, taken.get());
				}
				continue;
			}
			// Added straight away, so that newId sees it
			entities.add(entity);
			added.add(entity);
		}
		List<Entity> pasted = new ArrayList<>(added);
		pasted.addAll(existing.keySet());
		replaceReferences(pasted, renamed);

		Set<String> dangling = new LinkedHashSet<>();
		Set<String> ambiguous = new LinkedHashSet<>();
		Map<String, Entity> byId = byId();
		pasted.stream().flatMap(e -> e.getValues().keySet().stream().flatMap(p -> references(e, p)))
				.filter(id -> !copiedIds.contains(id) && !renamed.containsValue(id) && !isDescriptor(id))
				.forEach(id -> {
					Entity entity = byId.get(id);
					if (entity == null) {
						dangling.add(id);
					} else if (!sameCrate && (isLocal(id) || isBlankNode(id))
							&& !Optional.ofNullable(referenced.get(id)).map(Entity::toJson)
									.equals(Optional.of(entity.toJson()))) {
						ambiguous.add(id);
					}
				});
		return new Pasted(added, existing, renamed, List.copyOf(dangling), List.copyOf(ambiguous));
	}

	/**
	 * Whether an <code>@id</code> is an absolute IRI, with a scheme such as
	 * <code>https:</code>, rather than relative to the crate (a path, or a
	 * local <code>#</code> identifier) or a blank node.
	 */
	public static boolean isAbsolute(String id) {
		return id.matches("[A-Za-z][A-Za-z0-9+.-]*:.*") && !isBlankNode(id);
	}

	/**
	 * Whether an <code>@id</code> is a local identifier such as
	 * <code>#bob</code>, which only means something within its crate.
	 */
	public static boolean isLocal(String id) {
		return id.startsWith("#");
	}

	/**
	 * Whether an <code>@id</code> is a blank node such as <code>_:b0</code>.
	 */
	public static boolean isBlankNode(String id) {
		return id.startsWith("_:");
	}

	private boolean isDescriptor(String id) {
		return id.equals(RO_CRATE_METADATA_JSON) || id.equals(getRoot().getId() + RO_CRATE_METADATA_JSON);
	}

	/**
	 * A new local identifier for an entity of this type, e.g.
	 * <code>#person-1</code>, or <code>#ldac-speaker-1</code> for
	 * <code>ldac:Speaker</code>.
	 */
	private String newId(String type) {
		String prefix = "#" + type.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-") + "-";
		int n = 1;
		while (get(prefix + n).isPresent()) {
			n++;
		}
		return prefix + n;
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
	 * The dataset whose <code>hasPart</code> a path belongs in: the nearest
	 * folder above it that is a data entity, e.g. <code>data/</code> for
	 * <code>data/raw/a.csv</code>, else the root data entity.
	 */
	public Entity partOf(String id) {
		Set<Entity> data = Set.copyOf(dataEntities());
		String path = withoutDot(id);
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		for (int slash = path.lastIndexOf('/'); slash > 0; slash = path.lastIndexOf('/', slash - 1)) {
			String folder = path.substring(0, slash + 1);
			Optional<Entity> dataset = entities.stream()
					.filter(e -> data.contains(e) && withoutDot(e.getId()).equals(folder)).findFirst();
			if (dataset.isPresent()) {
				return dataset.get();
			}
		}
		return getRoot();
	}

	private static String withoutDot(String id) {
		return id.startsWith("./") ? id.substring(2) : id;
	}

	/**
	 * Make these entities data entities: each a part (<code>hasPart</code>)
	 * of the dataset of its folder (see {@link #partOf}), those in folders
	 * first, so that a folder added becomes the dataset of its files. Not the
	 * root data entity, nor those that already are.
	 *
	 * @return The entities added as parts
	 */
	public List<Entity> addParts(List<Entity> parts) {
		List<Entity> added = new ArrayList<>();
		List<Entity> sorted = parts.stream().filter(e -> e != getRoot())
				.sorted(Comparator.comparing((Entity e) -> withoutDot(e.getId()).replaceAll("/$", "").split("/").length))
				.toList();
		for (Entity part : sorted) {
			if (dataEntities().contains(part)) {
				continue;
			}
			Entity dataset = partOf(part.getId());
			List<JsonNode> hasPart = new ArrayList<>(dataset.getAll("hasPart"));
			hasPart.add(JsonNodeFactory.instance.objectNode().put("@id", part.getId()));
			dataset.setAll("hasPart", hasPart);
			added.add(part);
		}
		return added;
	}

	/**
	 * Add a Dataset for each of these folders, e.g. <code>raw/</code>, as a
	 * part of the dataset above it (see {@link #addParts}).
	 *
	 * @return The Datasets added, in the order of the folders
	 */
	public List<Entity> addFolders(List<String> folders) {
		List<Entity> added = new ArrayList<>();
		for (String folder : folders) {
			String id = folder.endsWith("/") ? folder : folder + "/";
			if (get(id).isPresent()) {
				continue;
			}
			Entity dataset = new Entity(id, "Dataset");
			entities.add(dataset);
			added.add(dataset);
		}
		addParts(added);
		return added;
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
	 * Add a Thing for each <code>@id</code> the crate's entities refer to
	 * that is not an entity in the crate, e.g. an external IRI, a local
	 * identifier of an entity removed or not yet added, or a typo, so that it
	 * can be given a name and type, or renamed to the <code>@id</code> meant
	 * (see {@link #merge}). Not for the metadata descriptor.
	 *
	 * @return The entities added, in the order first referred to
	 */
	public List<Entity> addReferenced() {
		Map<String, Entity> byId = byId();
		List<Entity> added = new ArrayList<>();
		for (Entity entity : List.copyOf(entities)) {
			for (String property : entity.getValues().keySet()) {
				references(entity, property)
						.filter(id -> !byId.containsKey(id) && !isDescriptor(id))
						.forEach(id -> {
							Entity thing = new Entity(id, "Thing");
							byId.put(id, thing);
							entities.add(thing);
							added.add(thing);
						});
			}
		}
		return added;
	}

	/**
	 * Remove from the root data entity's <code>mentions</code> the entities
	 * linked from its other properties (e.g. as parts, or as their authors),
	 * recursively through any property of the entities reached, as they need
	 * no mention. Entities only linked from other mentioned entities stay
	 * mentioned, as which of those could go would depend on their order.
	 *
	 * @return The entities removed from <code>mentions</code>
	 */
	public List<Entity> unmentionLinked() {
		Entity root = getRoot();
		List<String> properties = root.getValues().keySet().stream().filter(p -> !p.equals("mentions")).toList();
		Set<Entity> linked = linkedFrom(root.getId(), properties, new HashSet<>());
		Map<String, Entity> byId = byId();
		List<Entity> unmentioned = new ArrayList<>();
		List<JsonNode> mentions = new ArrayList<>();
		for (JsonNode mention : root.getAll("mentions")) {
			Entity entity = mention.isObject() && mention.has("@id") ? byId.get(mention.get("@id").asText()) : null;
			if (entity != null && entity != root && linked.contains(entity)) {
				unmentioned.add(entity);
			} else {
				mentions.add(mention);
			}
		}
		if (!unmentioned.isEmpty()) {
			root.setAll("mentions", mentions);
		}
		return unmentioned;
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

	/**
	 * Write <code>@id</code>s and references in the form an identifier
	 * scheme gives, e.g. ORCID iDs as <code>https://orcid.org/…</code>,
	 * merging entities that turn out to be the same.
	 *
	 * @param canonical The <code>@id</code> an <code>@id</code> means, if it
	 *                  is one of the scheme's
	 * @return What was changed, one line each
	 */
	public List<String> normaliseIds(Function<String, Optional<String>> canonical) {
		List<String> done = new ArrayList<>();
		for (Entity entity : List.copyOf(entities)) {
			Optional<String> iri = canonical.apply(entity.getId());
			if (iri.isEmpty() || entity.getId().equals(iri.get())) {
				continue;
			}
			String old = entity.getId();
			Optional<Entity> existing = get(iri.get());
			if (existing.isPresent()) {
				merge(existing.get(), entity);
				done.add(old + " merged into " + iri.get());
			} else {
				rename(entity, iri.get());
				done.add(old + " is now " + iri.get());
			}
		}
		for (Entity entity : List.copyOf(entities)) {
			for (String property : List.copyOf(entity.getValues().keySet())) {
				for (JsonNode value : entity.getAll(property)) {
					String id = value.path("@id").asText(null);
					Optional<String> iri = id == null ? Optional.empty() : canonical.apply(id);
					if (iri.isPresent() && !id.equals(iri.get())) {
						replaceReferences(id, iri.get());
						done.add("Links to " + id + " now go to " + iri.get());
					}
				}
			}
		}
		return done;
	}

	/**
	 * Point the references to one <code>@id</code> to another, e.g. to an
	 * entity known by another address.
	 */
	public void replaceReferences(String oldId, String newId) {
		replaceReferences(entities, Map.of(oldId, newId));
	}

	/**
	 * Replace references to the old <code>@id</code>s with the new ones, all
	 * at once, so that one renamed to another's old <code>@id</code> is not
	 * renamed again.
	 */
	private static void replaceReferences(List<Entity> entities, Map<String, String> renamed) {
		for (Entity entity : entities) {
			for (String property : List.copyOf(entity.getValues().keySet())) {
				List<JsonNode> values = entity.getAll(property);
				List<JsonNode> replaced = values.stream().map(v -> replaceReference(v, renamed)).toList();
				// Once each, e.g. mentions of two entities merged; others as they were
				if (!replaced.equals(values)) {
					entity.setAll(property, replaced.stream().distinct().toList());
				}
			}
		}
	}

	private static JsonNode replaceReference(JsonNode value, Map<String, String> renamed) {
		String newId = value.isObject() && value.path("@id").isTextual() ? renamed.get(value.get("@id").asText())
				: null;
		if (newId != null) {
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
