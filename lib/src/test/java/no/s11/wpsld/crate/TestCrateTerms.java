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

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.BeforeClass;
import org.junit.Test;

import no.s11.wpsld.jsonld.JsonLdContext;
import no.s11.wpsld.soss.ClassDef;
import no.s11.wpsld.soss.SchemaFromJsonLd;

public class TestCrateTerms {
	private static final String S = "http://schema.org/";
	private static SchemaFromJsonLd schema;
	private static CrateTerms terms;

	@BeforeClass
	public static void load() {
		schema = new SchemaFromJsonLd();
		terms = new CrateTerms(schema);
	}

	private static ClassDef classDef(String name) {
		return schema.getClasses().get(URI.create(S + name));
	}

	@Test
	public void fileForMediaObject() {
		assertEquals("File", terms.name(classDef("MediaObject")));
		assertEquals(Optional.of(classDef("MediaObject")), terms.classDef("File"));
		// The schema.org name is understood too
		assertEquals(Optional.of(classDef("MediaObject")), terms.classDef("MediaObject"));
	}

	@Test
	public void schemaOrgNamesKept() {
		assertEquals("Dataset", terms.name(classDef("Dataset")));
		// Not RO-Crate's other, shorter aliases
		assertEquals("Periodical", terms.name(classDef("Periodical")));
		assertEquals("contentUrl", terms.name(schema.getProperties().get(URI.create(S + "contentUrl"))));
		assertEquals("author", terms.name(schema.getProperties().get(URI.create(S + "author"))));
	}

	@Test
	public void aliasesUnderstood() {
		assertEquals(Optional.of(classDef("Periodical")), terms.classDef("Journal"));
		assertEquals(Optional.of(schema.getProperties().get(URI.create(S + "contentUrl"))), terms.propertyDef("path"));
		assertEquals(Optional.empty(), terms.classDef("NotAType"));
		// Not schema.org
		assertEquals(Optional.empty(), terms.classDef("Profile"));
	}

	/**
	 * The RO-Crate 1.3 context maps every schema.org 30.0 name to that term,
	 * so names only differ where RO-Crate asks for its own (File).
	 */
	@Test
	public void schemaOrgNamesAsInContext() {
		for (ClassDef classDef : schema.getClasses().values()) {
			String expected = classDef.getName().equals("MediaObject") ? "File" : classDef.getName();
			assertEquals(expected, terms.name(classDef));
			assertEquals(Optional.of(classDef), terms.classDef(terms.name(classDef)));
		}
		schema.getProperties().values().forEach(p -> {
			assertEquals(p.getName(), terms.name(p));
			assertEquals(Optional.of(p), terms.propertyDef(p.getName()));
		});
	}

	@Test
	public void prewarmedOnce() {
		JsonLdContext context = CrateTerms.prewarm();
		assertSame(context, CrateTerms.prewarm());
		assertEquals(List.of(), context.getProblems());
		assertEquals(Optional.of("http://schema.org/MediaObject"), context.expandTerm("File"));
	}

	@Test
	public void otherClasses() {
		List<String> names = terms.contextClasses().stream().map(terms::name).toList();
		assertTrue(names.toString(), names.containsAll(List.of("ComputationalWorkflow", "FormalParameter",
				"RepositoryCollection", "RepositoryObject", "RepositoryFile", "Profile")));
		// Not schema.org's, nor datatypes
		assertFalse(names.contains("File"));
		assertFalse(names.contains("HTML"));

		ClassDef collection = terms.otherClass(URI.create("http://pcdm.org/models#Collection"), Optional.empty());
		assertEquals("RepositoryCollection", terms.name(collection));
		assertTrue(collection.getSelfAndSuperClasses().contains(classDef("Thing")));
		assertEquals(Optional.of(URI.create("http://pcdm.org/models#Collection")), terms.iri("RepositoryCollection"));
	}

	@Test
	public void bioschemasTypesOfEarlierVersions() {
		assertEquals("https://bioschemas.org/terms/ComputationalWorkflow",
				CrateDocument.normalise("https://bioschemas.org/ComputationalWorkflow"));
		assertEquals("https://bioschemas.org/terms/ComputationalWorkflow",
				CrateDocument.normalise("https://bioschemas.org/terms/ComputationalWorkflow"));
		// Not profiles
		assertEquals("https://bioschemas.org/profiles/ComputationalWorkflow/1.0-RELEASE",
				CrateDocument.normalise("https://bioschemas.org/profiles/ComputationalWorkflow/1.0-RELEASE"));
	}
}
