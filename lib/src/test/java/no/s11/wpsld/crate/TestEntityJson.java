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
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": [1]}").startsWith("Needs one or more @type"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"@reverse\": {}}").contains("@reverse"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"name\": null}").contains("null"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"name\": [[\"x\"]]}").contains("array in an array"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"author\": {\"@id\": \"#b\", \"name\": \"B\"}}")
				.contains("entity of its own"));
		assertTrue(refused("{\"@id\": \"#a\", \"@type\": \"Thing\", \"author\": {\"name\": \"B\"}}")
				.contains("neither a reference"));
	}

	@Test
	public void applied() {
		Entity entity = new Entity("#alice", "Person");
		entity.set("name", Optional.of(TextNode.valueOf("Alice")));
		entity.set("email", Optional.of(TextNode.valueOf("alice@example.org")));
		EntityJson.apply(EntityJson.parse("""
				{"@id": "#renamed", "@type": ["Person", "Author"], "name": "Alice Smith",
				 "knows": [{"@id": "#bob"}], "keywords": ["a", "b"]}"""), entity);
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
