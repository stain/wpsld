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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.EntityTree.Kind;
import no.s11.wpsld.form.EntityTree.Node;

public class TestEntityTree {
	private static Entity add(Crate crate, String id, String name, String... types) {
		Entity entity = crate.add(types[0]);
		crate.rename(entity, id);
		entity.setTypes(List.of(types));
		if (name != null) {
			entity.set("name", Optional.of(TextNode.valueOf(name)));
		}
		return entity;
	}

	private static void hasPart(Entity entity, String... ids) {
		var parts = JsonNodeFactory.instance.arrayNode();
		for (String id : ids) {
			parts.addObject().put("@id", id);
		}
		entity.set("hasPart", Optional.of(parts));
	}

	/**
	 * The tree as indented lines: kind, name, "*" if it has no entity, "-"
	 * if it has a path but is not a data entity
	 */
	private static List<String> lines(List<Node> nodes) {
		List<String> lines = new ArrayList<>();
		lines(nodes, "", lines);
		return lines;
	}

	private static boolean isPath(Node node) {
		return node.kind() == Kind.FILE || node.kind() == Kind.FOLDER;
	}

	private static void lines(List<Node> nodes, String indent, List<String> lines) {
		for (Node node : nodes) {
			lines.add(indent + node.kind() + " " + node.name()
					+ (node.entity().isEmpty() ? " *" : node.data() || !isPath(node) ? "" : " -"));
			lines(node.children(), indent + "  ", lines);
		}
	}

	@Test
	public void physical() {
		Crate crate = new Crate();
		crate.getRoot().set("name", Optional.of(TextNode.valueOf("Survey")));
		add(crate, "data/counts.csv", "Counts", "File");
		add(crate, "README.md", null, "File");
		add(crate, "data/", "Data", "Dataset");
		// No entity for raw/ nor raw/2025/
		add(crate, "raw/2025/my%20notes.txt", null, "File");
		add(crate, "raw/2025/a.txt", null, "File");
		add(crate, "https://example.com/z.csv", null, "File");
		add(crate, "https://example.com/a.csv", null, "File");
		// Absolute, but not a part: contextual
		add(crate, "https://example.com/cited.pdf", null, "File");
		add(crate, "#bob", "Bob", "Person");
		add(crate, "#alice", "Alice", "Person");
		add(crate, "https://ror.org/01xtthb56", "University of Oslo", "Organization");
		// A path, but contextual: e.g. cited, not a part
		add(crate, "docs/cited.pdf", null, "File");
		hasPart(crate.getRoot(), "data/", "README.md", "https://example.com/z.csv", "https://example.com/a.csv");
		hasPart(crate.get("data/").orElseThrow(), "data/counts.csv");

		assertEquals(List.of(
				"ROOT Survey",
				"  FOLDER data",
				"    FILE counts.csv",
				"  FOLDER docs *",
				"    FILE cited.pdf -",
				"  FOLDER raw *",
				"    FOLDER 2025 *",
				"      FILE a.txt -",
				"      FILE my notes.txt -",
				"  FILE README.md",
				"EXTERNAL External resources *",
				"  RESOURCE https://example.com/a.csv",
				"  RESOURCE https://example.com/z.csv",
				"TYPE File *",
				"  CONTEXTUAL https://example.com/cited.pdf",
				"TYPE Organization *",
				"  CONTEXTUAL University of Oslo",
				"TYPE Person *",
				"  CONTEXTUAL Alice",
				"  CONTEXTUAL Bob"), lines(EntityTree.physical(crate)));
	}

	/** Each entity once, with keys unique in the tree */
	@Test
	public void keys() {
		Crate crate = new Crate();
		add(crate, "data/", null, "Dataset");
		add(crate, "data", null, "File");
		add(crate, "#data", null, "Thing");
		List<Node> nodes = EntityTree.physical(crate);
		List<String> keys = new ArrayList<>();
		List<Entity> entities = new ArrayList<>();
		collect(nodes, keys, entities);
		assertEquals(keys.size(), keys.stream().distinct().count());
		assertEquals(crate.getEntities().size(), entities.size());
		assertTrue(entities.containsAll(crate.getEntities()));
	}

	private static void collect(List<Node> nodes, List<String> keys, List<Entity> entities) {
		for (Node node : nodes) {
			keys.add(node.key());
			node.entity().ifPresent(entities::add);
			collect(node.children(), keys, entities);
		}
	}

	@Test
	public void paths() {
		assertEquals(List.of("data", "my notes.txt"), EntityTree.path("./data/my%20notes.txt"));
		assertEquals(List.of("data"), EntityTree.path("data/"));
		assertEquals(List.of("ø"), EntityTree.path("%C3%B8"));
		// Not percent-encoding: as it is
		assertEquals(List.of("100%"), EntityTree.path("100%"));
		assertEquals(List.of("%zz"), EntityTree.path("%zz"));
		assertTrue(EntityTree.isPath("data/"));
		assertFalse(EntityTree.isPath("./"));
		assertFalse(EntityTree.isPath("#bob"));
		assertFalse(EntityTree.isPath("_:b0"));
		assertFalse(EntityTree.isPath("https://example.com/"));
	}
}
