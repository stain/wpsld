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

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import no.s11.wpsld.soss.SchemaFromJsonLd;

public class TestTermRepairs {
	private static CrateTerms terms;

	@BeforeClass
	public static void load() {
		terms = new CrateTerms(new SchemaFromJsonLd());
	}

	private static Crate crate(String graph) throws Exception {
		return new CrateReader().read(new ObjectMapper().readTree("""
				{"@context": "https://w3id.org/ro/crate/1.3/context", "@graph": [
				 {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				  "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}},
				 {"@id": "./", "@type": "Dataset", "name": "A crate"},
				""" + graph + "]}"), "test").crate().orElseThrow();
	}

	/** Types written as IRIs, as ROHub writes them: by name, each once */
	@Test
	public void typeNames() throws Exception {
		Crate crate = crate("""
				{"@id": "#alice", "@type": ["http://schema.org/Person", "Person"], "name": "Alice"},
				{"@id": "#t", "@type": "https://schema.org/DefinedTerm", "name": "Term"},
				{"@id": "#x", "@type": "https://example.org/Thing"}""");
		assertEquals(List.of("#alice: http://schema.org/Person → Person",
				"#t: https://schema.org/DefinedTerm → DefinedTerm"), TermRepairs.typeNames(crate, terms));
		assertEquals(List.of("Person"), crate.get("#alice").orElseThrow().getTypes());
		assertEquals(List.of("DefinedTerm"), crate.get("#t").orElseThrow().getTypes());
		assertEquals(List.of("https://example.org/Thing"), crate.get("#x").orElseThrow().getTypes());
		// Again: nothing
		assertEquals(List.of(), TermRepairs.typeNames(crate, terms));
	}

	/**
	 * Enumeration members as text (or relative references) become
	 * references to them; not other text, nor references to entities
	 */
	@Test
	public void enumerationMembers() throws Exception {
		Crate crate = crate("""
				{"@id": "#run", "@type": "CreateAction", "actionStatus": "PotentialActionStatus"},
				{"@id": "#two", "@type": "CreateAction",
				 "actionStatus": [{"@id": "CompletedActionStatus"}, "schema:FailedActionStatus"]},
				{"@id": "#ok", "@type": "CreateAction", "actionStatus": {"@id": "http://schema.org/ActiveActionStatus"}},
				{"@id": "#other", "@type": "CreateAction", "actionStatus": "Done, mostly"},
				{"@id": "#local", "@type": "CreateAction", "actionStatus": {"@id": "#mine"}},
				{"@id": "#mine", "@type": "Thing"},
				{"@id": "#named", "@type": "Thing", "name": "PotentialActionStatus"}""");
		assertEquals(List.of("#run: actionStatus PotentialActionStatus", "#two: actionStatus CompletedActionStatus",
				"#two: actionStatus FailedActionStatus"), TermRepairs.enumerationMembers(crate, terms));
		assertEquals("{\"@id\":\"http://schema.org/PotentialActionStatus\"}",
				crate.get("#run").orElseThrow().get("actionStatus").orElseThrow().toString());
		assertEquals("[{\"@id\":\"http://schema.org/CompletedActionStatus\"},"
				+ "{\"@id\":\"http://schema.org/FailedActionStatus\"}]",
				crate.get("#two").orElseThrow().get("actionStatus").orElseThrow().toString());
		assertEquals("\"Done, mostly\"", crate.get("#other").orElseThrow().get("actionStatus").orElseThrow().toString());
		assertEquals("{\"@id\":\"#mine\"}", crate.get("#local").orElseThrow().get("actionStatus").orElseThrow().toString());
		// A name is no member: name's range is Text
		assertEquals("PotentialActionStatus", crate.get("#named").orElseThrow().get("name").orElseThrow().asText());
		assertEquals(List.of(), TermRepairs.enumerationMembers(crate, terms));
		// Terms, not entities missing from the crate
		assertEquals(List.of(), crate.addReferenced());
	}
}
