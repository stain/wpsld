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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

public class TestCrateDefinedFields {
	private static final String TERMS = "https://example.org/terms#";
	private static final String CORE = "https://language-research-technology.github.io/ro-crate-masp/profiles/ro-crate/profile-crate/";
	private static CrateTerms terms;
	private static SchemaOrgFormFields schemaOrg;

	/** A crate defining a type and two properties of its own, as RO-Crate's ad hoc terms */
	private static final String DEFINING = """
			{"@context": ["https://w3id.org/ro/crate/1.2/context",
			   {"SiteVisit": "https://example.org/terms#SiteVisit", "siteName": "https://example.org/terms#siteName",
			    "education": "https://example.org/terms#education"}],
			 "@graph": [{"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
			   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.2"}},
			  {"@id": "./", "@type": "Dataset", "name": "Visits", "mentions": [{"@id": "#alice"}, {"@id": "#visit"}]},
			  {"@id": "#alice", "@type": "Person", "name": "Alice"},
			  {"@id": "#visit", "@type": "SiteVisit", "name": "First visit"},
			  {"@id": "https://example.org/terms#SiteVisit", "@type": "rdfs:Class", "rdfs:label": "SiteVisit",
			   "rdfs:comment": "A visit to a site", "rdfs:subClassOf": {"@id": "http://schema.org/Event"}},
			  {"@id": "https://example.org/terms#siteName", "@type": "rdf:Property", "rdfs:label": "siteName",
			   "rdfs:comment": "The site visited", "domainIncludes": {"@id": "https://example.org/terms#SiteVisit"},
			   "rangeIncludes": {"@id": "http://schema.org/Text"}, "sh:minCount": 1, "sh:maxCount": 1},
			  {"@id": "https://example.org/terms#education", "@type": "rdf:Property", "rdfs:label": "education",
			   "rdfs:comment": "Highest degree", "domainIncludes": {"@id": "http://schema.org/Person"},
			   "rangeIncludes": {"@id": "http://schema.org/Text"}}
			 ]}""";

	@BeforeClass
	public static void load() {
		terms = new CrateTerms(new SchemaFromJsonLd());
		schemaOrg = new SchemaOrgFormFields(terms);
	}

	private static Crate crate(String json) {
		return new CrateReader().read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test").crate()
				.orElseThrow();
	}

	private static FormField field(List<FormField> fields, String name) {
		return fields.stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError(name));
	}

	@Test
	public void typesByTheCratesContext() {
		Crate crate = crate(DEFINING);
		Entity visit = crate.get("#visit").orElseThrow();
		// The RO-Crate context alone doesn't know it
		assertEquals(Set.of(), schemaOrg.typesOf(visit, terms));
		assertEquals(Set.of(URI.create(TERMS + "SiteVisit")), CrateDefinedFields.of(schemaOrg, crate).typesOf(visit, terms));
	}

	@Test
	public void fieldsOfTheCratesOwnType() {
		Crate crate = crate(DEFINING);
		FormFields fields = CrateDefinedFields.of(schemaOrg, crate);
		Entity visit = crate.get("#visit").orElseThrow();
		List<FormField> visitFields = fields.fieldsFor(fields.typesOf(visit, terms));
		FormField siteName = visitFields.get(0);
		assertEquals("siteName", siteName.name());
		assertEquals(URI.create(TERMS + "siteName"), siteName.property());
		assertEquals(CrateDefinedFields.REQUIRED, siteName.group());
		assertEquals(FieldTier.REQUIRED, siteName.tier());
		assertEquals("The site visited", siteName.comment().orElseThrow());
		assertTrue(fields.otherTypes().contains(URI.create(TERMS + "SiteVisit")));
		// Required by the crate itself
		assertTrue(RequiredFields.check(crate, fields, terms).toString(),
				RequiredFields.check(crate, fields, terms).contains("First visit must have a siteName"));
		visit.set("siteName", Optional.of(TextNode.valueOf("Oslo")));
		assertEquals(List.of(), RequiredFields.check(crate, CrateDefinedFields.of(schemaOrg, crate), terms));
	}

	@Test
	public void propertyOfAVocabularyType() {
		Crate crate = crate(DEFINING);
		FormFields fields = CrateDefinedFields.of(schemaOrg, crate);
		List<FormField> person = fields.fieldsFor(Set.of(URI.create("http://schema.org/Person")));
		FormField education = person.get(0);
		assertEquals("education", education.name());
		assertEquals(CrateDefinedFields.DEFINED, education.group());
		assertEquals(FieldTier.SUGGESTED, education.tier());
		// Then schema.org's
		assertNotNull(field(person, "givenName"));
		// With a profile, after its own
		FormFields profile = CrateDefinedFields.of(
				new ProfileFormFields(ProfileCache.bundled().bundled(CORE).orElseThrow(), schemaOrg), crate);
		assertNotNull(field(profile.fieldsFor(Set.of(URI.create("http://schema.org/Person"))), "education"));
	}

	@Test
	public void noDefinitions() {
		Crate crate = new Crate();
		Set<URI> person = Set.of(URI.create("http://schema.org/Person"));
		assertEquals(schemaOrg.fieldsFor(person), CrateDefinedFields.of(schemaOrg, crate).fieldsFor(person));
		assertTrue(CrateDefinedFields.of(schemaOrg, crate).getDefinitions().isEmpty());
	}
}
