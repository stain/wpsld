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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.jsonld.ContextCache;
import no.s11.wpsld.jsonld.JsonLdContext;

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

	/**
	 * The line ends of a file's content: \r\n if every line ends so (as a
	 * file saved on Windows may), else \n; to write it back with the same
	 * (see {@link #writer(String)})
	 */
	public static String lineEnd(byte[] content) {
		boolean any = false;
		for (int i = 0; i < content.length; i++) {
			if (content[i] == '\n') {
				if (i == 0 || content[i - 1] != '\r') {
					return "\n";
				}
				any = true;
			}
		}
		return any ? "\r\n" : "\n";
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

	private final Entities entities = new Entities();
	/**
	 * The entities by @id, the first of each, for {@link #get}: rebuilt
	 * when entities were added or removed, or any @id changed (see
	 * {@link Entity#idChanges()}), as a crate may have thousands
	 */
	private Map<String, Entity> index;
	private int indexedChanges;
	private long indexedIds;

	/** The entities, counting changes to them (as ArrayList does, for its iterators) */
	private static final class Entities extends ArrayList<Entity> {
		private static final long serialVersionUID = 1L;

		int changes() {
			return modCount;
		}
	}
	/** The <code>@context</code> to write, as read or RO-Crate 1.3's */
	private JsonNode context;
	/** The metadata descriptor as read, to write again; else a new one for 1.3 */
	private final Optional<Entity> descriptor;
	/** The context processed, when first needed */
	private JsonLdContext processed;

	public Crate() {
		append(new Entity(ROOT_ID, "Dataset"));
		context = TextNode.valueOf(RO_CRATE_1_3_CONTEXT);
		descriptor = Optional.empty();
	}

	private Crate(Entity root, List<Entity> others, JsonNode context, Optional<Entity> descriptor) {
		append(root);
		others.forEach(this::append);
		this.context = context.deepCopy();
		this.descriptor = descriptor;
	}

	/**
	 * The RO-Crate version the crate conforms to, e.g. <code>1.3</code>, by
	 * its metadata descriptor's <code>conformsTo</code>; 1.3 for a new crate,
	 * 1.0 for a descriptor of RO-Crate 1.0 (<code>ro-crate-metadata.jsonld</code>,
	 * which had no <code>conformsTo</code>); none if it can't tell.
	 */
	public Optional<String> specVersion() {
		if (descriptor.isEmpty()) {
			return Optional.of("1.3");
		}
		Optional<String> version = descriptor.get().getAll("conformsTo").stream().map(v -> v.path("@id").asText(""))
				.filter(id -> id.startsWith(RO_CRATE_SPEC)).map(id -> id.substring(RO_CRATE_SPEC.length()))
				.map(v -> v.replaceAll("[/#].*", "")).filter(v -> !v.isEmpty()).findFirst();
		if (version.isEmpty() && descriptor.get().getId().endsWith(".jsonld")) {
			return Optional.of("1.0");
		}
		return version;
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

	/** The <code>@base</code> of the crate's <code>@context</code>, if it has one */
	public Optional<String> getBase() {
		return base(context);
	}

	private static Optional<String> base(JsonNode context) {
		Optional<String> found = Optional.empty();
		for (JsonNode part : context.isArray() ? context : List.of(context)) {
			if (part.path("@base").isTextual()) {
				found = Optional.of(part.get("@base").asText());
			}
		}
		return found;
	}

	/**
	 * Set the <code>@base</code> of the crate's <code>@context</code>, as the
	 * last of an array of contexts, or remove it. RO-Crate mentions it only
	 * for reading a crate as a graph, to say what relative <code>@id</code>s
	 * are relative to (e.g. an arcp base from a fresh UUID, which makes a
	 * detached crate's identifiers absolute in the graph, "harmless, but can be
	 * removed"): the <code>@id</code>s themselves are what a detached crate
	 * has to have absolute.
	 *
	 * @param base The address, or none to remove it
	 */
	public void setBase(Optional<String> base) {
		com.fasterxml.jackson.databind.node.ArrayNode parts = JsonNodeFactory.instance.arrayNode();
		for (JsonNode part : context.isArray() ? context : List.of(context)) {
			if (part.isObject() && part.has("@base")) {
				ObjectNode without = ((ObjectNode) part.deepCopy());
				without.remove("@base");
				if (!without.isEmpty()) {
					parts.add(without);
				}
			} else {
				parts.add(part.deepCopy());
			}
		}
		base.ifPresent(b -> parts.addObject().put("@base", b));
		context = parts.size() == 1 ? parts.get(0) : parts;
		processed = null;
	}

	/**
	 * An <code>@id</code> as an IRI: a compact IRI such as
	 * <code>s:ActiveActionStatus</code> expanded with the crate's
	 * <code>@context</code> (from the bundled contexts, without network
	 * access); others, e.g. absolute IRIs and relative paths, as they are.
	 */
	public String expandId(String id) {
		if (processed == null) {
			processed = JsonLdContext.of(context, ContextCache.bundled());
		}
		return processed.expandId(id);
	}

	public Entity getRoot() {
		return entities.get(0);
	}

	public List<Entity> getEntities() {
		return Collections.unmodifiableList(entities);
	}

	public Optional<Entity> get(String id) {
		if (!indexed()) {
			indexedIds = Entity.idChanges();
			indexedChanges = entities.changes();
			index = byId();
		}
		return Optional.ofNullable(index.get(id));
	}

	private boolean indexed() {
		return index != null && indexedChanges == entities.changes() && indexedIds == Entity.idChanges();
	}

	/** Add an entity, to the index by @id too if it is up to date (pasting thousands looks up as it adds) */
	private void append(Entity entity) {
		boolean was = indexed();
		entities.add(entity);
		entity.inCrate = true;
		if (was) {
			index.putIfAbsent(entity.getId(), entity);
			indexedChanges = entities.changes();
		}
	}

	/**
	 * The last number of each prefix of new identifiers, counting on from
	 * it, rather than from 1 for each new entity (thousands, when pasting)
	 */
	private final Map<String, Integer> last = new HashMap<>();

	/** The entities by @id, the first of each, up to date (see {@link #get}); not to change */
	private Map<String, Entity> index() {
		get("");
		return index;
	}

	/**
	 * Add an entity with a new local identifier like <code>#person-1</code>.
	 */
	public Entity add(String type) {
		Entity entity = new Entity(newId(type), type);
		append(entity);
		return entity;
	}

	/**
	 * Add an entity with this identifier, e.g. a file's path, which no
	 * entity of the crate has: as {@link #add(String)} and renaming it, but
	 * without looking through all references for the new one's
	 *
	 * @throws IllegalArgumentException if another entity has it
	 */
	public Entity add(String type, String id) {
		if (get(id).isPresent()) {
			throw new IllegalArgumentException("Another entity already has @id " + id);
		}
		Entity entity = new Entity(id, type);
		append(entity);
		return entity;
	}

	/**
	 * What {@link #paste(List, Map, boolean)} did.
	 *
	 * @param added     The entities added, in the order pasted
	 * @param existing  Copies whose <code>@id</code> (absolute, or a relative
	 *                  path) names an entity the crate already has, which
	 *                  differs: each with that entity, to merge into it
	 *                  (see {@link Crate#merge}) or leave out
	 * @param same      The crate's entities that copies were the same as,
	 *                  so left out, in the order pasted
	 * @param renamed   New <code>@id</code>s of copies, by their own
	 * @param dangling  <code>@id</code>s the pasted entities refer to, not
	 *                  pasted, that the crate has no entity for
	 * @param ambiguous Local <code>@id</code>s the pasted entities refer to,
	 *                  not pasted, that the crate has an entity for, but
	 *                  from another crate and not as copied: it may be
	 *                  another entity of the same <code>@id</code>
	 */
	public record Pasted(List<Entity> added, Map<Entity, Entity> existing, List<Entity> same,
			Map<String, String> renamed, List<String> dangling, List<String> ambiguous) {
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
		List<Entity> same = new ArrayList<>();
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
				if (taken.get().toJson().equals(copy.toJson())) {
					same.add(taken.get());
				} else {
					existing.put(entity, taken.get());
				}
				continue;
			}
			// Added straight away, so that newId sees it
			append(entity);
			added.add(entity);
		}
		List<Entity> pasted = new ArrayList<>(added);
		pasted.addAll(existing.keySet());
		replaceReferences(pasted, renamed);

		Set<String> dangling = new LinkedHashSet<>();
		Set<String> ambiguous = new LinkedHashSet<>();
		Map<String, Entity> byId = index();
		pasted.stream().flatMap(e -> e.getValues().keySet().stream().flatMap(p -> references(e, p)))
				.filter(id -> !copiedIds.contains(id) && !renamed.containsValue(id) && !isDescriptor(id) && !isTerm(id))
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
		return new Pasted(added, existing, List.copyOf(same), renamed, List.copyOf(dangling), List.copyOf(ambiguous));
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

	/**
	 * Whether an <code>@id</code> is a schema.org term, e.g.
	 * <code>http://schema.org/PotentialActionStatus</code> (a member of an
	 * Enumeration), which a crate refers to without describing it
	 */
	public static boolean isTerm(String id) {
		return id.startsWith("http://schema.org/") || id.startsWith("https://schema.org/");
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
		int n = last.getOrDefault(prefix, 0) + 1;
		while (get(prefix + n).isPresent()) {
			n++;
		}
		last.put(prefix, n);
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
	 * The entities, other than the root data entity, whose relative
	 * <code>@id</code>s lead somewhere on the web (see
	 * {@link WebCrate#resolve}): files and folders, and other entities named
	 * by a path, but not <code>#local</code> and <code>_:blank</code> ones,
	 * nor absolute ones.
	 */
	public List<Entity> relativeEntities(WebCrate web) {
		return entities.stream().filter(e -> e != getRoot()).filter(e -> web.resolve(e.getId()).isPresent())
				.toList();
	}

	/**
	 * The entities, other than the root data entity, with a relative
	 * <code>@id</code>: not <code>#local</code>, <code>_:blank</code> or
	 * absolute ones. What a detached crate has to have absolute (see
	 * {@link #absolutize}), files and folders, but also other entities written
	 * with a path or a name.
	 */
	public List<Entity> relativeEntities() {
		return entities.stream().filter(e -> e != getRoot()).filter(e -> WebCrate.isRelative(e.getId())).toList();
	}

	/**
	 * The files and folders, other than the root data entity, with a relative
	 * <code>@id</code> in a crate that has no base to resolve it against (a
	 * detached crate, see {@link WebCrate}, with no <code>@base</code>): none if
	 * the crate has one. What a semi-detached crate is, by having any.
	 */
	public List<Entity> unanchoredDataEntities(WebCrate web) {
		if (web.base().isPresent()) {
			return List.of();
		}
		return entities.stream().filter(e -> e != getRoot())
				.filter(e -> e.getTypes().contains("File") || e.getTypes().contains("Dataset"))
				.filter(e -> WebCrate.isRelative(e.getId())).toList();
	}

	/**
	 * Make the relative <code>@id</code>s of a crate fetched from the web
	 * absolute, as the RO-Crate specification's "Converting from Attached to
	 * Detached RO-Crate Package" has it, so that they still lead there when
	 * the crate is saved elsewhere: each entity listed by
	 * {@link #relativeEntities} gets the address it leads to as its
	 * <code>@id</code>, and every reference follows. An entity whose
	 * address another entity already has is left as it is.
	 *
	 * @param root Whether the root data entity too: its <code>./</code>
	 *             becomes the crate's address, as a detached crate's should be
	 *             when available online; not for a crate to be saved as
	 *             <code>ro-crate-metadata.json</code>, an attached crate whose
	 *             root is its folder
	 * @return The <code>@id</code>s as they were, and as they are now
	 */
	public Map<String, String> absolutize(WebCrate web, boolean root) {
		Map<String, String> renamed = new LinkedHashMap<>();
		List<Entity> relative = new ArrayList<>(relativeEntities(web));
		if (root && web.resolve(getRoot().getId()).isPresent()) {
			relative.add(getRoot());
		}
		for (Entity entity : relative) {
			String oldId = entity.getId();
			URI address = web.resolve(oldId).orElseThrow();
			if (get(address.toString()).isEmpty()) {
				rename(entity, address.toString());
				renamed.put(oldId, address.toString());
			}
		}
		return renamed;
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
			// Not a change ArrayList counts
			index = null;
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
		Map<String, Entity> byId = index();
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
		return partOf(id, dataFolders());
	}

	/** The data entities by their paths without ./, e.g. data/ */
	private Map<String, Entity> dataFolders() {
		Map<String, Entity> folders = new HashMap<>();
		dataEntities().forEach(e -> folders.putIfAbsent(withoutDot(e.getId()), e));
		return folders;
	}

	private Entity partOf(String id, Map<String, Entity> folders) {
		String path = withoutDot(id);
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		for (int slash = path.lastIndexOf('/'); slash > 0; slash = path.lastIndexOf('/', slash - 1)) {
			Entity dataset = folders.get(path.substring(0, slash + 1));
			if (dataset != null) {
				return dataset;
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
		// Each dataset's parts set once, as there may be thousands
		Set<Entity> data = new HashSet<>(dataEntities());
		Map<String, Entity> folders = dataFolders();
		Map<Entity, List<JsonNode>> hasParts = new LinkedHashMap<>();
		for (Entity part : sorted) {
			if (!data.add(part)) {
				continue;
			}
			Entity dataset = partOf(part.getId(), folders);
			hasParts.computeIfAbsent(dataset, d -> new ArrayList<>(d.getAll("hasPart")))
					.add(JsonNodeFactory.instance.objectNode().put("@id", part.getId()));
			folders.putIfAbsent(withoutDot(part.getId()), part);
			added.add(part);
		}
		hasParts.forEach((dataset, values) -> dataset.setAll("hasPart", values));
		return added;
	}

	/**
	 * References written as text, e.g.
	 * <code>"author": "{\"@id\": \"https://orcid.org/…\"}"</code> (a JSON
	 * object in a string, as a tool converting to JSON may write them): each
	 * made the reference it says. Only text that is, as a whole, a JSON
	 * object of an <code>@id</code> alone; other text, also JSON, stays.
	 *
	 * @return What was changed, e.g. "./: author https://orcid.org/…"
	 */
	public List<String> referencesFromText() {
		List<String> done = new ArrayList<>();
		ObjectMapper json = new ObjectMapper();
		for (Entity entity : entities) {
			for (String property : List.copyOf(entity.getValues().keySet())) {
				List<JsonNode> values = new ArrayList<>(entity.getAll(property));
				boolean changed = false;
				for (int i = 0; i < values.size(); i++) {
					String text = values.get(i).isTextual() ? values.get(i).asText().strip() : "";
					if (!text.startsWith("{") || !text.endsWith("}")) {
						continue;
					}
					try {
						JsonNode written = json.readTree(text);
						if (written.isObject() && written.size() == 1 && written.path("@id").isTextual()
								&& !written.get("@id").asText().isBlank()) {
							String id = written.get("@id").asText().strip();
							values.set(i, JsonNodeFactory.instance.objectNode().put("@id", id));
							done.add(entity.getId() + ": " + property + " " + id);
							changed = true;
						}
					} catch (JsonProcessingException notJson) {
						// Text, as it is
					}
				}
				if (changed) {
					entity.setAll(property, values);
				}
			}
		}
		return done;
	}

	/**
	 * Objects written in values without an <code>@id</code> (e.g. an inline
	 * <code>{"@type": "PropertyValue", "name": "depth"}</code>), which
	 * flattened RO-Crate JSON-LD doesn't allow (see {@link #isAnonymous}):
	 * each made an entity of its own, with a new <code>@id</code> from its
	 * type, and referred to by it; also those written in them.
	 *
	 * @return The entities made, in the order found
	 */
	public List<Entity> flattenNested() {
		List<Entity> added = new ArrayList<>();
		// Those added are looked through too, for objects written in them
		for (int i = 0; i < entities.size(); i++) {
			Entity entity = entities.get(i);
			for (String property : List.copyOf(entity.getValues().keySet())) {
				List<JsonNode> values = entity.getAll(property);
				if (values.stream().noneMatch(Crate::isAnonymous)) {
					continue;
				}
				List<JsonNode> flat = new ArrayList<>();
				for (JsonNode value : values) {
					if (!isAnonymous(value)) {
						flat.add(value);
						continue;
					}
					List<String> types = new ArrayList<>();
					JsonNode type = value.path("@type");
					(type.isArray() ? type : List.of(type)).forEach(t -> {
						if (t.isTextual() && !t.asText().isBlank()) {
							types.add(t.asText());
						}
					});
					Entity made = add(types.isEmpty() ? "Thing" : types.get(0));
					if (!types.isEmpty()) {
						made.setTypes(types);
					}
					value.properties().forEach(e -> {
						if (!e.getKey().equals("@type")) {
							made.set(e.getKey(), Optional.of(e.getValue()));
						}
					});
					added.add(made);
					flat.add(JsonNodeFactory.instance.objectNode().put("@id", made.getId()));
				}
				entity.setAll(property, flat);
			}
		}
		return added;
	}

	/**
	 * Whether a value is an object written in it without an
	 * <code>@id</code>, that could be an entity of its own: not a literal
	 * (<code>@value</code>) nor a list, with properties other than JSON-LD's
	 * keywords, or a <code>@type</code> as text
	 */
	public static boolean isAnonymous(JsonNode value) {
		if (!value.isObject() || value.isEmpty() || value.has("@id") || value.has("@value")) {
			return false;
		}
		boolean typed = true;
		for (Map.Entry<String, JsonNode> e : value.properties()) {
			if (e.getKey().equals("@type")) {
				JsonNode type = e.getValue();
				for (JsonNode t : type.isArray() ? type : List.of(type)) {
					typed &= t.isTextual();
				}
			} else if (e.getKey().startsWith("@") || e.getKey().isBlank()) {
				return false;
			}
		}
		return typed;
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
			append(dataset);
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
	 * (see {@link #merge}). Not for the metadata descriptor, nor schema.org
	 * terms, e.g. an Enumeration's member (see {@link #isTerm}).
	 *
	 * @return The entities added, in the order first referred to
	 */
	public List<Entity> addReferenced() {
		Map<String, Entity> byId = index();
		List<Entity> added = new ArrayList<>();
		for (Entity entity : List.copyOf(entities)) {
			for (String property : entity.getValues().keySet()) {
				references(entity, property)
						.filter(id -> !byId.containsKey(id) && !isDescriptor(id) && !isTerm(id))
						.forEach(id -> {
							Entity thing = new Entity(id, "Thing");
							byId.put(id, thing);
							append(thing);
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
		Map<String, Entity> byId = index();
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
		Map<String, Entity> byId = index();
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
	/**
	 * Describe the crate's HTML preview, ro-crate-preview.html, as RO-Crate
	 * shows it: a CreativeWork about the root data entity; not one of its
	 * parts (hasPart), which it SHOULD NOT be. Kept as it is if described.
	 *
	 * @return Its entity
	 */
	public Entity describePreview() {
		Entity preview = get(RO_CRATE_PREVIEW_HTML).orElseGet(() -> {
			Entity added = add("CreativeWork");
			rename(added, RO_CRATE_PREVIEW_HTML);
			return added;
		});
		if (preview.get("about").isEmpty()) {
			preview.set("about", Optional.of(JsonNodeFactory.instance.objectNode().put("@id", getRoot().getId())));
		}
		return preview;
	}

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
