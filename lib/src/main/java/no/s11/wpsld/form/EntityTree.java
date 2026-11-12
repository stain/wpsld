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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import no.s11.wpsld.crate.Crate;
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
		Comparator<Entity> order = listing.order(root);
		contextual.forEach((type, entities) -> nodes.add(new Node("type:" + type, type, Kind.TYPE, Optional.empty(),
				false, entities.stream().sorted(order).map(e -> new Node("type:" + type + " id:" + e.getId(),
						e.getLabel(), Kind.CONTEXTUAL, Optional.of(e), false, List.of())).toList())));
		return nodes;
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
		String path = node.key().substring("path:".length());
		StringBuilder id = new StringBuilder();
		for (String name : path.split("/")) {
			id.append(encode(name)).append('/');
		}
		return Optional.of(id.toString());
	}

	/**
	 * A name in a path, percent-encoded where a relative URI must be: spaces,
	 * <code>%</code>, <code>#</code>, <code>?</code> and other characters not
	 * allowed in a URI; letters of other scripts stay, as IRIs allow them.
	 */
	static String encode(String name) {
		StringBuilder encoded = new StringBuilder();
		name.codePoints().forEach(c -> {
			if (c < 0x21 || c == 0x7f || " %#?[]\"<>\\^`{|}".indexOf(c) >= 0) {
				for (byte b : new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8)) {
					encoded.append(String.format("%%%02X", b & 0xff));
				}
			} else {
				encoded.appendCodePoint(c);
			}
		});
		return encoded.toString();
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

		Folder folder(String name) {
			return folders.computeIfAbsent(name, n -> new Folder(key + n + "/", n, data, parts));
		}

		Node node(Kind kind, Collator collator) {
			List<Node> children = new ArrayList<>();
			folders.values().stream().sorted(Comparator.comparing((Folder f) -> f.name, collator)
					.thenComparing(f -> f.key)).map(f -> f.node(Kind.FOLDER, collator)).forEach(children::add);
			files.stream().sorted(Comparator.comparing((Entity e) -> name(e), collator).thenComparing(Entity::getId))
					.map(e -> new Node("id:" + e.getId(), name(e), Kind.FILE, Optional.of(e), data.contains(e),
							parts(e, collator)))
					.forEach(children::add);
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
