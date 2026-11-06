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
package no.s11.wpsld.jsonld;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestJsonLdContext {
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String S = "http://schema.org/";

	private static JsonLdContext context(String json) throws Exception {
		return JsonLdContext.of(MAPPER.readTree(json), ContextCache.bundled());
	}

	@Test
	public void roCrate13() throws Exception {
		JsonLdContext context = context("\"https://w3id.org/ro/crate/1.3/context\"");
		assertEquals(List.of(), context.getProblems());
		assertEquals(Optional.of(S + "name"), context.expandTerm("name"));
		assertEquals(Optional.of(S + "Dataset"), context.expandTerm("Dataset"));
		// RO-Crate's own names
		assertEquals(Optional.of(S + "MediaObject"), context.expandTerm("File"));
		assertEquals(Optional.of("http://purl.org/dc/terms/conformsTo"), context.expandTerm("conformsTo"));
		assertEquals(Optional.of("http://www.w3.org/ns/dx/prof/Profile"), context.expandTerm("Profile"));
		// Compact and absolute IRIs
		assertEquals(Optional.of(S + "newTerm"), context.expandTerm("schema:newTerm"));
		assertEquals(Optional.of("http://example.com/p"), context.expandTerm("http://example.com/p"));
		// Not in the context and no @vocab: JSON-LD would drop it
		assertEquals(Optional.empty(), context.expandTerm("notATerm"));
		// Keywords as they are
		assertEquals(Optional.of("@id"), context.expandTerm("@id"));
	}

	@Test
	public void allRoCrateVersions() throws Exception {
		for (String version : List.of("1.0", "1.1", "1.2", "1.3")) {
			JsonLdContext context = context("\"https://w3id.org/ro/crate/" + version + "/context\"");
			assertEquals(version, List.of(), context.getProblems());
			assertEquals(version, Optional.of(S + "author"), context.expandTerm("author"));
			assertEquals(version, Optional.of(S + "MediaObject"), context.expandTerm("File"));
		}
		// 1.0 has "@base": null
		assertEquals(Optional.empty(), context("\"https://w3id.org/ro/crate/1.0/context\"").getBase());
	}

	@Test
	public void compact() throws Exception {
		JsonLdContext context = context("\"https://w3id.org/ro/crate/1.3/context\"");
		assertEquals("name", context.compact(S + "name"));
		assertEquals("conformsTo", context.compact("http://purl.org/dc/terms/conformsTo"));
		// File and MediaObject both map to schema:MediaObject: the shortest wins
		assertEquals("File", context.compact(S + "MediaObject"));
		// Not a term, but in a known namespace
		assertEquals("schema:notYetATerm", context.compact(S + "notYetATerm"));
		assertEquals("http://example.com/other", context.compact("http://example.com/other"));
	}

	/**
	 * Like MASP profile crates: the RO-Crate context plus @vocab and prefixes.
	 */
	@Test
	public void contextArrayWithVocabAndPrefixes() throws Exception {
		JsonLdContext context = context("[\"https://w3id.org/ro/crate/1.2/context\","
				+ " {\"@vocab\": \"http://schema.org/\", \"ldac\": \"https://w3id.org/ldac/terms#\"}]");
		assertEquals(List.of(), context.getProblems());
		assertEquals(Optional.of("https://w3id.org/ldac/terms#Speaker"), context.expandTerm("ldac:Speaker"));
		// Not a term of the RO-Crate context, but expanded with @vocab
		assertEquals(Optional.of(S + "notYetATerm"), context.expandTerm("notYetATerm"));
		assertEquals("ldac:Speaker", context.compact("https://w3id.org/ldac/terms#Speaker"));
		assertEquals(Optional.of(S), context.getVocab());
	}

	@Test
	public void termDefinitions() throws Exception {
		JsonLdContext context = context("{"
				// Refers to the prefix defined after it
				+ "\"author\": {\"@id\": \"ex:author\", \"@type\": \"@id\"},"
				+ "\"ex\": \"http://example.com/ns#\","
				+ "\"ex:defined\": \"ex:defined\","
				+ "\"age\": {\"@id\": \"ex:age\", \"@type\": \"xsd:integer\"},"
				+ "\"xsd\": \"http://www.w3.org/2001/XMLSchema#\","
				+ "\"dropped\": null}");
		assertEquals(List.of(), context.getProblems());
		assertEquals(new JsonLdContext.Term("http://example.com/ns#author", Optional.of("@id")),
				context.getTerm("author").orElseThrow());
		assertEquals(Optional.of("http://www.w3.org/2001/XMLSchema#integer"), context.getTerm("age").orElseThrow().type());
		// A term defined as its own compact IRI is not a cycle
		assertEquals(Optional.of("http://example.com/ns#defined"), context.expandTerm("ex:defined"));
		assertEquals(Optional.empty(), context.expandTerm("dropped"));
		assertEquals("http://example.com/ns#x", context.expandId("ex:x"));
		// Relative references are kept as they are
		assertEquals("data/file.txt", context.expandId("data/file.txt"));
		assertEquals("#person-1", context.expandId("#person-1"));
	}

	@Test
	public void vocabAsCompactIri() throws Exception {
		JsonLdContext context = context("{\"schema\": \"http://schema.org/\", \"@vocab\": \"schema:\"}");
		assertEquals(Optional.of(S), context.getVocab());
		assertEquals(Optional.of(S + "anything"), context.expandTerm("anything"));
	}

	@Test
	public void problemsReportedNotThrown() throws Exception {
		JsonLdContext context = context("[\"https://example.com/unknown-context\","
				+ " {\"a\": \"b:x\", \"b\": \"a:y\", \"rev\": {\"@reverse\": \"http://example.com/r\"},"
				+ " \"scoped\": {\"@id\": \"http://example.com/s\", \"@context\": {}},"
				+ " \"@import\": \"https://example.com/other\", \"ok\": \"http://example.com/ok\"},"
				+ " 42]");
		List<String> problems = context.getProblems();
		assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("not in the cache")));
		assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("Cyclic")));
		assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("Reverse")));
		assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("Scoped")));
		assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("@import")));
		assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("Not a context")));
		// The rest is still used, scoped term without its own context
		assertEquals(Optional.of("http://example.com/ok"), context.expandTerm("ok"));
		assertEquals(Optional.of("http://example.com/s"), context.expandTerm("scoped"));
	}

	@Test
	public void jsonLd11ReportedAsNotRoCrate() throws Exception {
		JsonLdContext context = context("{\"@version\": 1.1, \"@protected\": true, \"x\": \"http://example.com/x\"}");
		assertEquals(2, context.getProblems().size());
		assertTrue(context.getProblems().stream().allMatch(p -> p.contains("JSON-LD 1.1, not valid in RO-Crate")));
		assertEquals(Optional.of("http://example.com/x"), context.expandTerm("x"));
	}

	@Test
	public void nullResets() throws Exception {
		JsonLdContext context = context("[\"https://w3id.org/ro/crate/1.3/context\", null, {\"x\": \"http://example.com/x\"}]");
		assertEquals(Optional.empty(), context.expandTerm("name"));
		assertEquals(Optional.of("http://example.com/x"), context.expandTerm("x"));
	}

	@Test
	public void laterDefinitionsOverride() throws Exception {
		JsonLdContext context = context("[\"https://w3id.org/ro/crate/1.3/context\", {\"name\": \"http://example.com/name\"}]");
		assertEquals(Optional.of("http://example.com/name"), context.expandTerm("name"));
	}

	@Test
	public void ofDocument() throws Exception {
		JsonNode crate = MAPPER.readTree("{\"@context\": \"https://w3id.org/ro/crate/1.1/context\", \"@graph\": []}");
		assertEquals(Optional.of(S + "name"), JsonLdContext.ofDocument(crate, ContextCache.bundled()).expandTerm("name"));
		assertSame(JsonLdContext.EMPTY, JsonLdContext.ofDocument(MAPPER.readTree("{}"), ContextCache.bundled()));
	}
}
