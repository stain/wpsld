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

import static org.junit.Assert.*;

import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class TestCrate {
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	public void newCrateHasRootDataset() {
		Crate crate = new Crate();
		assertEquals(1, crate.getEntities().size());
		assertEquals("./", crate.getRoot().getId());
		assertEquals(List.of("Dataset"), crate.getRoot().getTypes());
	}

	@Test
	public void addAndRemoveEntities() {
		Crate crate = new Crate();
		Entity alice = crate.add("Person");
		Entity bob = crate.add("Person");
		assertEquals("#person-1", alice.getId());
		assertEquals("#person-2", bob.getId());
		assertTrue(crate.remove(alice));
		// Identifier not reused while #person-2 exists, but #person-1 is free
		assertEquals("#person-1", crate.add("Person").getId());
		assertEquals(Optional.of(bob), crate.get("#person-2"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rootCantBeRemoved() {
		Crate crate = new Crate();
		crate.remove(crate.getRoot());
	}

	@Test
	public void valuesSurviveTypeChanges() {
		Entity entity = new Entity("#thing", "Dataset");
		entity.set("name", Optional.of(JSON.textNode("Shared by every Thing")));
		entity.set("distribution", Optional.of(JSON.objectNode().put("@id", "#download")));
		entity.setTypes(List.of("Person"));
		// Person has no distribution, but the value is kept
		assertEquals("#download", entity.get("distribution").get().get("@id").asText());
		entity.setTypes(List.of("Dataset"));
		assertEquals("Shared by every Thing", entity.get("name").get().asText());
		assertEquals("#download", entity.get("distribution").get().get("@id").asText());
		entity.set("distribution", Optional.empty());
		assertFalse(entity.get("distribution").isPresent());
	}

	@Test
	public void severalValues() {
		Entity root = new Crate().getRoot();
		assertEquals(List.of(), root.getAll("hasPart"));
		JsonNode a = JSON.objectNode().put("@id", "a.txt");
		JsonNode b = JSON.objectNode().put("@id", "b.txt");
		root.setAll("hasPart", List.of(a));
		// A single value is not wrapped in an array
		assertTrue(root.get("hasPart").get().isObject());
		assertEquals(List.of(a), root.getAll("hasPart"));
		root.setAll("hasPart", List.of(a, b));
		assertTrue(root.get("hasPart").get().isArray());
		assertEquals(List.of(a, b), root.getAll("hasPart"));
		root.setAll("hasPart", List.of());
		assertFalse(root.get("hasPart").isPresent());
	}

	@Test
	public void label() {
		Entity entity = new Entity("#person-1", "Person");
		assertEquals("#person-1", entity.getLabel());
		entity.set("name", Optional.of(JSON.textNode("Alice")));
		assertEquals("Alice", entity.getLabel());
	}

	@Test
	public void flattenedJsonLd() {
		Crate crate = new Crate();
		crate.getRoot().set("name", Optional.of(JSON.textNode("My crate")));
		Entity alice = crate.add("Person");
		alice.setTypes(List.of("Person", "Researcher"));
		crate.getRoot().set("author", Optional.of(JSON.objectNode().put("@id", alice.getId())));

		JsonNode json = crate.toJson();
		assertEquals("https://w3id.org/ro/crate/1.3/context", json.get("@context").asText());
		JsonNode graph = json.get("@graph");
		assertEquals(3, graph.size());
		assertEquals("ro-crate-metadata.json", graph.get(0).get("@id").asText());
		assertEquals("https://w3id.org/ro/crate/1.3", graph.get(0).get("conformsTo").get("@id").asText());
		assertEquals("./", graph.get(1).get("@id").asText());
		assertEquals("Dataset", graph.get(1).get("@type").asText());
		assertEquals("#person-1", graph.get(1).get("author").get("@id").asText());
		assertEquals(2, graph.get(2).get("@type").size());
	}
}
