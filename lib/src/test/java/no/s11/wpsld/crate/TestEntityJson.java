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
import com.fasterxml.jackson.databind.node.TextNode;

public class TestEntityJson {
	private static String refused(String text) {
		try {
			EntityJson.parse(text);
		} catch (IllegalArgumentException e) {
			return e.getMessage();
		}
		fail("Not refused: " + text);
		return null;
	}

	@Test
	public void parsed() {
		EntityJson.Parsed parsed = EntityJson.parse("""
				{"@id": "#alice", "@type": ["Person", "Thing"], "name": "Alice",
				 "affiliation": [{"@id": "https://ror.org/027m9bs27"}, {"@id": "#lab"}],
				 "description": {"@value": "En forsker", "@language": "no"}, "age": 42}""");
		assertEquals("#alice", parsed.id());
		assertEquals(List.of("Person", "Thing"), parsed.types());
		assertEquals(List.of("name", "affiliation", "description", "age"), List.copyOf(parsed.values().keySet()));
		assertEquals(List.of("Person"), EntityJson.parse("{\"@id\": \"#a\", \"@type\": \"Person\"}").types());
	}

	@Test
	public void refusedSayingWhy() {
		assertTrue(refused("{\"@id\": \"#a\", ").startsWith("Not JSON"));
		assertTrue(refused("[{\"@id\": \"#a\", \"@type\": \"Thing\"}]").startsWith("Should be one JSON object"));
		assertTrue(refused("\"#a\"").startsWith("Should be one JSON object"));
		assertTrue(refused("{\"@context\": \"https://w3id.org/ro/crate/1.2/context\", \"@graph\": []}")
				.contains("Edit > Paste"));
		assertTrue(refused("{\"@type\": \"Thing\"}").startsWith("Needs an @id"));
		assertTrue(refused("{\"@id\": \" \", \"@type\": \"Thing\"}").startsWith("Needs an @id"));
		assertTrue(refused("{\"@id\": \"#a\"}").startsWith("Needs one or more @type"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": []}").startsWith("Needs one or more @type"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": [1]}").contains("@type should be text"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"@reverse\": {\"isPartOf\": \"x\"}}")
				.contains("should link from entities"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"@reverse\": {\"@id\": \"x\"}}")
				.contains("not a property"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"@reverse\": \"x\"}").contains("@reverse"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"name\": null}").contains("null"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"name\": [[\"x\"]]}").contains("array in an array"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"author\": {}}").contains("empty object"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"author\": {\"@id\": 3}}").contains("isn't text"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"author\": {\"name\": {\"@list\": []}}}")
				.contains("@list"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"d\": {\"@value\": \"x\", \"name\": \"y\"}}")
				.contains("more than @language or @type"));
		// Written ever deeper in each other
		String deep = "{\"@id\": \"#a\", \"@type\": \"Thing\", \"p\": ".repeat(EntityJson.DEPTH + 2) + "1"
				+ "}".repeat(EntityJson.DEPTH + 2);
		assertTrue(refused(deep).contains("more than " + EntityJson.DEPTH + " deep"));
	}

	/**
	 * Entities written in values, as ROHub writes a dataset's parts: referred
	 * to by their @id, added, or merged into the crate's; those without an
	 * @id added with a new one
	 */
	@Test
	public void nestedFlattened() {
		Crate crate = new Crate();
		Entity existing = crate.add("Dataset", "folders/a/");
		existing.set("name", Optional.of(TextNode.valueOf("Folder A")));
		Entity entity = crate.add("Dataset", "#data");
		EntityJson.Parsed parsed = EntityJson.parse("""
				{"@id": "#data", "@type": "Dataset",
				 "hasPart": [{"@id": "folders/a/", "@type": "Dataset", "description": "The first"},
				             {"@id": "folders/b/", "@type": "Dataset",
				              "author": {"@type": "Person", "name": "Bob"}},
				             {"@id": "data.csv"}],
				 "variableMeasured": {"@type": "PropertyValue", "name": "depth", "unitText": "m"}}""");
		assertEquals(4, parsed.nested().size());
		EntityJson.Applied applied = EntityJson.apply(parsed, entity, crate);
		assertEquals(List.of(existing), applied.merged());
		// Merged: its own values kept, the new added
		assertEquals("Folder A", existing.getLabel());
		assertEquals(Optional.of(TextNode.valueOf("The first")), existing.get("description"));
		Entity b = crate.get("folders/b/").orElseThrow();
		Entity bob = crate.get(b.getAll("author").get(0).get("@id").asText()).orElseThrow();
		assertEquals("Bob", bob.getLabel());
		assertEquals(List.of("Person"), bob.getTypes());
		assertTrue(bob.getId(), bob.getId().startsWith("#person-"));
		Entity depth = crate.get(entity.getAll("variableMeasured").get(0).get("@id").asText()).orElseThrow();
		assertEquals(List.of("PropertyValue"), depth.getTypes());
		assertEquals(Optional.of(TextNode.valueOf("m")), depth.get("unitText"));
		assertTrue(applied.added().containsAll(List.of(b, bob, depth)));
		assertEquals(3, applied.added().size());
		// A reference alone stays one, to an entity not (yet) in the crate
		assertEquals(List.of("folders/a/", "folders/b/", "data.csv"),
				entity.getAll("hasPart").stream().map(v -> v.get("@id").asText()).toList());
		assertTrue(crate.get("data.csv").isEmpty());
	}

	/** Links written with @reverse made links of the entities they come from */
	@Test
	public void reverseLinked() {
		Crate crate = new Crate();
		Entity data = crate.add("Dataset", "data/");
		Entity a = crate.add("File", "data/a.csv");
		EntityJson.Applied applied = EntityJson.apply(EntityJson.parse("""
				{"@id": "data/", "@type": "Dataset",
				 "@reverse": {"isPartOf": [{"@id": "data/a.csv"},
				                           {"@id": "data/b.csv", "@type": "File", "name": "B"},
				                           {"@id": "data/c.csv"}]}}"""), data, crate);
		JsonNode toData = JsonNodeFactory.instance.objectNode().put("@id", "data/");
		assertEquals(Optional.of(toData), a.get("isPartOf"));
		Entity b = crate.get("data/b.csv").orElseThrow();
		assertEquals(Optional.of(toData), b.get("isPartOf"));
		Entity c = crate.get("data/c.csv").orElseThrow();
		assertEquals(List.of("Thing"), c.getTypes());
		assertEquals(List.of(b, c), applied.added());
		assertEquals(List.of(a), applied.linked());
		assertTrue(data.get("@reverse").isEmpty());
	}

	@Test
	public void applied() {
		Crate crate = new Crate();
		Entity entity = crate.add("Person", "#alice");
		entity.set("name", Optional.of(TextNode.valueOf("Alice")));
		entity.set("email", Optional.of(TextNode.valueOf("alice@example.org")));
		EntityJson.apply(EntityJson.parse("""
				{"@id": "#renamed", "@type": ["Person", "Author"], "name": "Alice Smith",
				 "knows": [{"@id": "#bob"}], "keywords": ["a", "b"]}"""), entity, crate);
		// Not renamed, which is up to the crate
		assertEquals("#alice", entity.getId());
		assertEquals(List.of("Person", "Author"), entity.getTypes());
		assertEquals(Optional.of(TextNode.valueOf("Alice Smith")), entity.get("name"));
		assertTrue(entity.get("email").isEmpty());
		// One value alone, several as an array
		assertEquals(Optional.of(JsonNodeFactory.instance.objectNode().put("@id", "#bob")), entity.get("knows"));
		assertEquals(List.of(TextNode.valueOf("a"), TextNode.valueOf("b")), entity.getAll("keywords"));
	}
}
