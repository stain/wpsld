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
package no.s11.wpsld.form;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateFolder;
import no.s11.wpsld.crate.Entity;

/**
 * The entities of a crate as a tree of folders, as a file manager shows
 * them. Each entity is in it once, so it can be found by its entity.
 * <p>
 * The physical tree breaks the relative <code>@id</code>s of entities down
 * into folders by their paths, from the root data entity, with folders for
 * the paths even without a Dataset entity for them, and with the entities
 * that have a path but are contextual (not in <code>hasPart</code>), not
 * being data entities (see {@link Node#data()}). An entity of a part of a
 * file, e.g. <code>page.html#table2</code>, is under the file's entity, if
 * it has one. Then "External resources": the data entities (in
 * <code>hasPart</code>) with absolute URIs, by URI. Then the contextual
 * entities and the parts of files, in a folder for each of their types: in
 * several, if they have several types.
 * <p>
 * An entity is in the tree at least once, so it can be found by its
 * entity, and may be in it more than once, each with its own key.
 */
public class EntityTree {
	/** What a node of the tree is */
	public enum Kind {
		/** The root data entity, the crate's folder */
		ROOT,
		/** A folder of the path, with its Dataset entity, if it has one */
		FOLDER,
		/** A file, or other entity with a relative path */
		FILE,
		/** The folder of external resources */
		EXTERNAL,
		/** A data entity with an absolute URI */
		RESOURCE,
		/** A folder of contextual entities of a type */
		TYPE,
		/** A contextual entity */
		CONTEXTUAL
	}

	/**
	 * A node: its key, unique in the tree, to keep it open when the tree is
	 * made again; its name; its entity, if it has one; and whether that is
	 * a data entity (the root, or in its <code>hasPart</code>), not so for
	 * a folder of the path without an entity, nor a file that is contextual.
	 */
	public record Node(String key, String name, Kind kind, Optional<Entity> entity, boolean data,
			List<Node> children) {
		/** Whether it is a folder of other nodes, even an empty one */
		public boolean isFolder() {
			return switch (kind) {
			case ROOT, FOLDER, EXTERNAL, TYPE -> true;
			default -> !children.isEmpty();
			};
		}
	}

	private EntityTree() {
	}

	/**
	 * The physical tree: its top nodes, the root data entity first.
	 */
	public static List<Node> physical(Crate crate, EntityListing listing) {
		return physical(crate, listing, List.of());
	}

	/**
	 * The physical tree, with the files and folders of the crate's folder
	 * that it doesn't describe, by their relative <code>@id</code>s (see
	 * {@link CrateFolder#list}): files without an entity, greyed out as the
	 * folders without one are.
	 */
	public static List<Node> physical(Crate crate, EntityListing listing, Collection<String> onDisk) {
		Collator collator = Collator.getInstance();
		collator.setStrength(Collator.SECONDARY);
		Entity root = crate.getRoot();
		Set<Entity> data = Set.copyOf(crate.dataEntities());
		Map<Entity, List<Entity>> parts = new HashMap<>();
		Folder top = new Folder("", "", data, parts);
		top.entity = root;
		List<Entity> external = new ArrayList<>();
		List<Entity> fragments = new ArrayList<>();
		Map<String, List<Entity>> contextual = new TreeMap<>(collator);
		for (Entity entity : crate.getEntities()) {
			String id = entity.getId();
			if (entity == root) {
				continue;
			}
			if (isPath(id) && id.contains("#")) {
				fragments.add(entity);
			} else if (isPath(id)) {
				top.add(entity, path(id));
			} else if (data.contains(entity) && Crate.isAbsolute(id)) {
				external.add(entity);
			} else {
				listing.typeNames(entity).forEach(t -> contextual.computeIfAbsent(t, k -> new ArrayList<>()).add(entity));
			}
		}
		// Files and folders on disk without an entity
		Set<List<String>> described = new HashSet<>();
		crate.getEntities().stream().map(Entity::getId).filter(EntityTree::isPath).map(EntityTree::path)
				.forEach(described::add);
		for (String id : onDisk) {
			List<String> path = path(id);
			if (described.contains(path)) {
				continue;
			}
			if (id.endsWith("/")) {
				Folder folder = top;
				for (String name : path) {
					folder = folder.folder(name);
				}
			} else {
				top.addMissing(path);
			}
		}
		// Parts of files: under the file's entity if it has one, and by type
		for (Entity fragment : fragments) {
			String file = fragment.getId().substring(0, fragment.getId().indexOf('#'));
			crate.get(file).filter(f -> f != root && isPath(file))
					.ifPresent(f -> parts.computeIfAbsent(f, k -> new ArrayList<>()).add(fragment));
			listing.typeNames(fragment).forEach(t -> contextual.computeIfAbsent(t, k -> new ArrayList<>()).add(fragment));
		}
		List<Node> nodes = new ArrayList<>();
		nodes.add(top.node(Kind.ROOT, collator));
		if (!external.isEmpty()) {
			nodes.add(new Node("external", "External resources", Kind.EXTERNAL, Optional.empty(), false,
					external.stream().sorted(Comparator.comparing(Entity::getId)).map(
							e -> new Node("id:" + e.getId(), e.getId(), Kind.RESOURCE, Optional.of(e), true, List.of()))
							.toList()));
		}
		nodes.addAll(typeFolders(contextual, listing.order(root)));
		return nodes;
	}

	/** A folder for each type, of its entities */
	private static List<Node> typeFolders(Map<String, List<Entity>> byType, Comparator<Entity> order) {
		List<Node> nodes = new ArrayList<>();
		byType.forEach((type, entities) -> nodes.add(new Node("type:" + type, type, Kind.TYPE, Optional.empty(),
				false, entities.stream().sorted(order).map(e -> new Node("type:" + type + " id:" + e.getId(),
						e.getLabel(), Kind.CONTEXTUAL, Optional.of(e), false, List.of())).toList())));
		return nodes;
	}

	/**
	 * The logical tree: the root data entity's parts (<code>hasPart</code>),
	 * each Dataset, or other entity with parts, a folder of its own parts,
	 * wherever their files are; an entity that is a part of several is in
	 * each. Parts are named by their path from the dataset's, where it has
	 * one (<code>counts.csv</code> in <code>data/</code>), else by their
	 * <code>@id</code>. Then the other entities, in a folder for each of
	 * their types, as in {@link #physical}.
	 */
	public static List<Node> logical(Crate crate, EntityListing listing) {
		Collator collator = Collator.getInstance();
		collator.setStrength(Collator.SECONDARY);
		Entity root = crate.getRoot();
		Map<String, Entity> byId = new HashMap<>();
		crate.getEntities().forEach(e -> byId.putIfAbsent(e.getId(), e));
		Set<Entity> data = Set.copyOf(crate.dataEntities());
		List<Node> nodes = new ArrayList<>();
		nodes.add(new Node("part:", root.getLabel(), Kind.ROOT, Optional.of(root), true,
				parts(root, "part:", Set.of(root), byId, listing, collator)));
		Map<String, List<Entity>> contextual = new TreeMap<>(collator);
		for (Entity entity : crate.getEntities()) {
			if (entity != root && !data.contains(entity)) {
				listing.typeNames(entity).forEach(t -> contextual.computeIfAbsent(t, k -> new ArrayList<>()).add(entity));
			}
		}
		nodes.addAll(typeFolders(contextual, listing.order(root)));
		return nodes;
	}

	/**
	 * The parts of an entity: those with parts, or Datasets, first, as
	 * folders; not again within themselves, if parts are in a cycle
	 */
	private static List<Node> parts(Entity whole, String key, Set<Entity> within, Map<String, Entity> byId,
			EntityListing listing, Collator collator) {
		List<Entity> parts = whole.getAll("hasPart").stream().filter(v -> v.isObject() && v.path("@id").isTextual())
				.map(v -> byId.get(v.get("@id").asText())).filter(Objects::nonNull).distinct().toList();
		Comparator<Entity> folders = Comparator.comparing(part -> !isWhole(part, listing));
		return parts.stream()
				.sorted(folders.thenComparing(part -> partName(whole, part), collator).thenComparing(Entity::getId))
				.map(part -> {
					String partKey = key + "/" + part.getId();
					boolean folder = isWhole(part, listing);
					Kind kind = folder ? Kind.FOLDER : Crate.isAbsolute(part.getId()) ? Kind.RESOURCE : Kind.FILE;
					List<Node> children = folder && !within.contains(part)
							? parts(part, partKey, plus(within, part), byId, listing, collator)
							: List.of();
					return new Node(partKey, partName(whole, part), kind, Optional.of(part), true, children);
				}).toList();
	}

	private static boolean isWhole(Entity entity, EntityListing listing) {
		return !entity.getAll("hasPart").isEmpty() || listing.isA(entity, "Dataset");
	}

	private static Set<Entity> plus(Set<Entity> set, Entity entity) {
		Set<Entity> more = new HashSet<>(set);
		more.add(entity);
		return more;
	}

	/**
	 * A part's name: its path from the folder of the dataset it is a part
	 * of, decoded, if it is in it (<code>raw/a.csv</code> in
	 * <code>data/</code> for <code>data/raw/a.csv</code>); else its path
	 * from the crate, or its <code>@id</code>.
	 */
	static String partName(Entity whole, Entity part) {
		String id = part.getId();
		if (!isPath(id) || id.contains("#")) {
			return id;
		}
		String path = String.join("/", path(id));
		String folder = whole.getId().equals("./") ? "" : String.join("/", isPath(whole.getId()) ? path(whole.getId()) : List.of());
		if (!folder.isEmpty() && path.startsWith(folder + "/")) {
			return path.substring(folder.length() + 1);
		}
		return path;
	}

	/**
	 * Whether an <code>@id</code> is a path relative to the crate, e.g.
	 * <code>data/counts.csv</code>, rather than absolute, local
	 * (<code>#bob</code>) or a blank node.
	 */
	public static boolean isPath(String id) {
		return !id.isEmpty() && !Crate.isAbsolute(id) && !Crate.isLocal(id) && !Crate.isBlankNode(id)
				&& !id.equals("./");
	}

	/**
	 * The names in a path, decoded (<code>my%20data/</code> is
	 * <code>my data</code>), without <code>./</code> and the last
	 * <code>/</code> of a folder.
	 */
	static List<String> path(String id) {
		String path = id;
		while (path.startsWith("./")) {
			path = path.substring(2);
		}
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		return List.of(path.split("/+", -1)).stream().map(EntityTree::decode).toList();
	}

	/**
	 * The <code>@id</code> for a folder of the path, e.g. <code>raw/</code>,
	 * with its names percent-encoded where they must be, e.g.
	 * <code>my%20notes/</code>; none for other nodes.
	 */
	public static Optional<String> folderId(Node node) {
		if (node.kind() != Kind.FOLDER || !node.key().startsWith("path:")) {
			return Optional.empty();
		}
		return Optional.of(pathId(node.key().substring("path:".length())));
	}

	/**
	 * The <code>@id</code> for a file in the crate's folder that has no
	 * entity, e.g. <code>my%20notes.txt</code>; none for other nodes.
	 */
	public static Optional<String> fileId(Node node) {
		if (node.kind() != Kind.FILE || node.entity().isPresent() || !node.key().startsWith("path:")) {
			return Optional.empty();
		}
		return Optional.of(pathId(node.key().substring("path:".length())));
	}

	/** A decoded path, e.g. a/my notes/, as an @id: a/my%20notes/ */
	private static String pathId(String path) {
		List<String> names = new ArrayList<>();
		for (String name : path.split("/")) {
			names.add(encode(name));
		}
		return String.join("/", names) + (path.endsWith("/") ? "/" : "");
	}

	static String encode(String name) {
		return CrateFolder.encode(name);
	}

	/** Percent-decoded as UTF-8, or as it is if it can't be */
	static String decode(String segment) {
		if (!segment.contains("%")) {
			return segment;
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		for (int i = 0; i < segment.length(); i++) {
			char c = segment.charAt(i);
			if (c == '%' && i + 2 < segment.length()) {
				int value = hex(segment.charAt(i + 1)) * 16 + hex(segment.charAt(i + 2));
				if (value < 0) {
					return segment;
				}
				bytes.write(value);
				i += 2;
			} else if (c == '%') {
				return segment;
			} else {
				bytes.writeBytes(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
			}
		}
		return bytes.toString(StandardCharsets.UTF_8);
	}

	private static int hex(char c) {
		int digit = Character.digit(c, 16);
		return digit < 0 ? -1000 : digit;
	}

	/** A folder of the path while it is built */
	private static final class Folder {
		final String key;
		final String name;
		Entity entity;
		final Map<String, Folder> folders = new LinkedHashMap<>();
		final List<Entity> files = new ArrayList<>();
		/** Files on disk in it without an entity, by name */
		final List<String> missing = new ArrayList<>();
		/** The data entities of the crate */
		final Set<Entity> data;
		/** The entities of parts of files, e.g. page.html#table2, by file */
		final Map<Entity, List<Entity>> parts;

		Folder(String key, String name, Set<Entity> data, Map<Entity, List<Entity>> parts) {
			this.key = key;
			this.name = name;
			this.data = data;
			this.parts = parts;
		}

		void add(Entity entity, List<String> path) {
			Folder folder = this;
			for (String name : path.subList(0, path.size() - 1)) {
				folder = folder.folder(name);
			}
			String last = path.get(path.size() - 1);
			boolean isFolder = entity.getId().endsWith("/") || entity.getTypes().contains("Dataset");
			Folder existing = folder.folders.get(last);
			if (isFolder && (existing == null || existing.entity == null)) {
				folder.folder(last).entity = entity;
			} else {
				folder.files.add(entity);
			}
		}

		void addMissing(List<String> path) {
			Folder folder = this;
			for (String name : path.subList(0, path.size() - 1)) {
				folder = folder.folder(name);
			}
			String name = path.get(path.size() - 1);
			// Not as a file if an entity has its path as a folder
			if (!folder.folders.containsKey(name)) {
				folder.missing.add(name);
			}
		}

		Folder folder(String name) {
			return folders.computeIfAbsent(name, n -> new Folder(key + n + "/", n, data, parts));
		}

		Node node(Kind kind, Collator collator) {
			List<Node> children = new ArrayList<>();
			folders.values().stream().sorted(Comparator.comparing((Folder f) -> f.name, collator)
					.thenComparing(f -> f.key)).map(f -> f.node(Kind.FOLDER, collator)).forEach(children::add);
			// Files described and not, by name
			List<Node> fileNodes = new ArrayList<>();
			files.stream().sorted(Comparator.comparing(Entity::getId))
					.map(e -> new Node("id:" + e.getId(), name(e), Kind.FILE, Optional.of(e), data.contains(e),
							parts(e, collator)))
					.forEach(fileNodes::add);
			missing.stream().map(n -> new Node("path:" + key + n, n, Kind.FILE, Optional.empty(), false, List.of()))
					.forEach(fileNodes::add);
			fileNodes.stream().sorted(Comparator.comparing(Node::name, collator)).forEach(children::add);
			if (entity != null) {
				children.addAll(parts(entity, collator));
			}
			return new Node("path:" + key, kind == Kind.ROOT ? entity.getLabel() : name, kind,
					Optional.ofNullable(entity), kind == Kind.ROOT || (entity != null && data.contains(entity)),
					List.copyOf(children));
		}

		/** The parts of a file, by their fragments, e.g. #table2 */
		private List<Node> parts(Entity file, Collator collator) {
			return parts.getOrDefault(file, List.of()).stream()
					.sorted(Comparator.comparing((Entity e) -> fragment(e), collator).thenComparing(Entity::getId))
					.map(e -> new Node("id:" + e.getId(), fragment(e), Kind.FILE, Optional.of(e), data.contains(e),
							List.of()))
					.toList();
		}

		private static String fragment(Entity part) {
			return decode(part.getId().substring(part.getId().indexOf('#')));
		}

		private static String name(Entity file) {
			List<String> path = path(file.getId());
			return path.get(path.size() - 1);
		}
	}
}
