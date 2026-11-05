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
package no.s11.wpsld.masp;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.Test;

import no.s11.wpsld.crate.CrateDocument.Problem;
import no.s11.wpsld.crate.CrateDocument.Severity;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.masp.RangeItem.AllowedValues;
import no.s11.wpsld.masp.RangeItem.ClassRuleRange;
import no.s11.wpsld.masp.RangeItem.IriRange;
import no.s11.wpsld.masp.RangeItem.ValueConstraint;

public class TestProfileReader {
	private static final String S = "http://schema.org/";
	private static final String LDAC = "https://w3id.org/ldac/terms#";

	private final CrateReader crateReader = new CrateReader();
	private final ProfileReader reader = new ProfileReader(crateReader);

	private Profile fixture(String name) throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/no/s11/wpsld/crate/masp/" + name)) {
			assertNotNull(name, in);
			return reader.read(crateReader.read(in, name));
		}
	}

	private static List<String> labels(List<PropertyRule> rules) {
		return rules.stream().map(PropertyRule::label).toList();
	}

	@Test
	public void maspProfile() throws Exception {
		Profile profile = fixture("ro-crate-masp-profile.json");
		assertEquals(List.of(), profile.problems());
		assertFalse(profile.hasErrors());
		assertEquals("RO-Crate MASP Profile", profile.name().orElseThrow());
		assertEquals("0.1.0", profile.version().orElseThrow());
		assertEquals(List.of("https://w3id.org/ro/crate/1.2"), profile.isProfileOf());
		assertEquals(13, profile.classRules().size());
		assertEquals(29, profile.propertyRules().size());
		assertEquals("#class_MetadataDescriptor", profile.descriptorRule().orElseThrow());
		assertEquals("#class_ProfileDataset", profile.rootRule().orElseThrow());

		ClassRule root = profile.classRule("#class_ProfileDataset").orElseThrow();
		assertEquals(new Cardinality(OptionalInt.of(1), OptionalInt.of(1)), root.instances());
		// Required first, then the others, each by label
		assertEquals(List.of("hasResource", "isProfileOf", "license", "name", "author", "description", "hasPart",
				"version"), labels(profile.propertiesOf(root.id())));
		assertEquals(List.of(root),
				profile.classRulesFor(Set.of(S + "Dataset", "http://www.w3.org/ns/dx/prof/Profile")).stream()
						.filter(c -> c.id().equals(root.id())).toList());
	}

	@Test
	public void maspPropertyRules() throws Exception {
		Profile profile = fixture("ro-crate-masp-profile.json");
		PropertyRule name = profile.propertyRules().get("#prop_name_ProfileDataset");
		assertEquals("name", name.label());
		assertEquals(List.of(S + "name"), name.specializationOf());
		assertEquals(List.of("#class_ProfileDataset"), name.domain());
		assertEquals(List.of(new IriRange(S + "Text")), name.range());
		assertEquals(new Cardinality(OptionalInt.of(1), OptionalInt.of(1)), name.values());
		assertTrue(name.isRequired());

		PropertyRule about = profile.propertyRules().get("#prop_about_MetadataDescriptor");
		assertEquals(List.of(new ClassRuleRange("#class_ProfileDataset")), about.range());

		PropertyRule id = profile.propertyRules().get("#prop_id_MetadataDescriptor");
		assertEquals("@id", id.label());
		ValueConstraint constraint = (ValueConstraint) id.range().get(0);
		assertEquals("ro-crate-metadata.json", constraint.value().asText());
		assertTrue(constraint.regex().isEmpty());

		assertFalse(profile.propertyRules().get("#prop_description_ProfileDataset").isRequired());
	}

	@Test
	public void roCrateProfile() throws Exception {
		Profile profile = fixture("ro-crate-profile.json");
		assertEquals(List.of(), profile.problems());
		assertEquals(3, profile.classRules().size());
		assertEquals(6, profile.propertyRules().size());
		assertEquals("#Root_Data_Entity", profile.rootRule().orElseThrow());
		assertEquals(List.of("datePublished", "description", "license", "name"),
				labels(profile.propertiesOf("#Root_Data_Entity")));
		// A rule whose @id is the schema.org property itself, and a range
		// written as {"@id": "Text"}
		PropertyRule name = profile.propertyRules().get(S + "name");
		assertEquals("name", name.label());
		assertEquals(List.of(new IriRange(S + "Text")), name.range());
		assertEquals(new Cardinality(OptionalInt.of(0), OptionalInt.of(1)),
				profile.classRule("#Root_Data_Entity").orElseThrow().instances());
	}

	@Test
	public void ldacProfile() throws Exception {
		Profile profile = fixture("ldac-profile.json");
		assertEquals(List.of(), profile.problems());
		// As in MASP's catalogue
		assertEquals(30, profile.classRules().size());
		assertEquals(99, profile.propertyRules().size());
		assertEquals("#Root_Data_Entity", profile.rootRule().orElseThrow());
		// sh:minCount written as a string
		assertEquals(OptionalInt.of(1),
				profile.classRule("#RO-Crate_Metadata_Descriptor").orElseThrow().instances().min());
		// A type outside schema.org, with schema.org superclasses
		ClassRule event = profile.classRule("#class_CollectionEvent").orElseThrow();
		assertEquals(List.of(LDAC + "CollectionEvent"), event.specializationOf());
		assertEquals(List.of(S + "Event", S + "CreateAction"), event.subClassOf());
		assertEquals(List.of(event), profile.classRulesFor(Set.of(LDAC + "CollectionEvent")));
	}

	@Test
	public void allowedValues() throws Exception {
		Profile profile = fixture("ldac-profile.json");
		// An ItemList
		AllowedValues conformsTo = (AllowedValues) profile.propertiesOf("#Root_Data_Entity").stream()
				.filter(p -> p.label().equals("conformsTo")).findFirst().orElseThrow().range().get(0);
		assertEquals(List.of("https://w3id.org/ldac/profile#Collection"), conformsTo.ids());
		// The terms of a DefinedTermSet (written with compact @ids)
		AllowedValues access = (AllowedValues) profile.propertyRules().get("#prop_ldac:access_DataReuseLicense")
				.range().get(0);
		assertEquals(LDAC + "AccessTypes", access.id());
		assertEquals(Set.of(LDAC + "AuthorizedAccess", LDAC + "OpenAccess"), Set.copyOf(access.ids()));
	}

	private Profile profile(String rules, String... entities) {
		String json = "{\"@context\": [\"https://w3id.org/ro/crate/1.2/context\", {\"@vocab\": \"http://schema.org/\"}],"
				+ " \"@graph\": [{\"@id\": \"ro-crate-metadata.json\", \"@type\": \"CreativeWork\","
				+ " \"about\": {\"@id\": \"./\"}, \"conformsTo\": {\"@id\": \"https://w3id.org/ro/crate/1.2\"}},"
				+ " {\"@id\": \"./\", \"@type\": [\"Dataset\", \"Profile\"], \"name\": \"Test\","
				+ " \"hasResource\": {\"@id\": \"#schema\"}},"
				+ " {\"@id\": \"#schema\", \"@type\": \"ResourceDescriptor\","
				+ " \"hasRole\": {\"@id\": \"http://www.w3.org/ns/dx/prof/role/schema\"},"
				+ " \"hasPart\": [" + rules + "]}"
				+ (entities.length > 0 ? ", " + String.join(", ", entities) : "") + "]}";
		return reader.read(crateReader.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test"));
	}

	private static boolean reports(Profile profile, Severity severity, String text) {
		return profile.problems().stream().anyMatch(p -> p.severity() == severity && p.message().contains(text));
	}

	@Test
	public void noRulesWithoutSchemaResource() {
		Profile profile = reader.read(crateReader.read(new ByteArrayInputStream(
				("{\"@context\": \"https://w3id.org/ro/crate/1.2/context\", \"@graph\": [{\"@id\": \"./\","
						+ " \"@type\": [\"Dataset\", \"Profile\"]}]}").getBytes(StandardCharsets.UTF_8)), "test"));
		assertTrue(profile.hasErrors());
		assertTrue(reports(profile, Severity.ERROR, "so the profile has no rules"));
		assertTrue(profile.classRules().isEmpty());
	}

	@Test
	public void unreadableCrate() {
		Profile profile = reader.read(crateReader.read(
				new ByteArrayInputStream("not json".getBytes(StandardCharsets.UTF_8)), "test"));
		assertTrue(profile.hasErrors());
		assertTrue(reports(profile, Severity.ERROR, "could not be read"));
	}

	@Test
	public void cardinalities() {
		Profile profile = profile("{\"@id\": \"#c\"}, {\"@id\": \"#full\"}, {\"@id\": \"#bad\"}, {\"@id\": \"#inverted\"}",
				"{\"@id\": \"#c\", \"@type\": \"rdfs:Class\", \"prov:specializationOf\": {\"@id\": \"http://schema.org/Thing\"}}",
				// The SHACL IRI, as a string
				"{\"@id\": \"#full\", \"@type\": \"rdf:Property\", \"rdfs:label\": \"a\", \"domainIncludes\": {\"@id\": \"#c\"},"
						+ " \"http://www.w3.org/ns/shacl#minCount\": \"2\", \"http://www.w3.org/ns/shacl#maxCount\": 3}",
				"{\"@id\": \"#bad\", \"@type\": \"rdf:Property\", \"rdfs:label\": \"b\", \"domainIncludes\": {\"@id\": \"#c\"},"
						+ " \"sh:minCount\": \"one\"}",
				"{\"@id\": \"#inverted\", \"@type\": \"rdf:Property\", \"rdfs:label\": \"c\", \"domainIncludes\": {\"@id\": \"#c\"},"
						+ " \"sh:minCount\": 2, \"sh:maxCount\": 1}");
		assertEquals(new Cardinality(OptionalInt.of(2), OptionalInt.of(3)), profile.propertyRules().get("#full").values());
		assertEquals(Cardinality.ANY, profile.propertyRules().get("#bad").values());
		assertTrue(reports(profile, Severity.WARNING, "#bad has an invalid sh:minCount"));
		assertTrue(reports(profile, Severity.WARNING, "#inverted has sh:minCount 2 above sh:maxCount 1"));
		assertEquals("1..*", new Cardinality(OptionalInt.of(1), OptionalInt.empty()).toString());
	}

	@Test
	public void regexConstraint() {
		Profile profile = profile("{\"@id\": \"#c\"}, {\"@id\": \"#readme\"}, {\"@id\": \"#pv\"}, {\"@id\": \"#broken\"},"
				+ " {\"@id\": \"#bpv\"}",
				"{\"@id\": \"#c\", \"@type\": \"rdfs:Class\"}",
				"{\"@id\": \"#readme\", \"@type\": \"rdf:Property\", \"rdfs:label\": \"@id\","
						+ " \"domainIncludes\": {\"@id\": \"#c\"}, \"rangeIncludes\": {\"@id\": \"#pv\"}}",
				"{\"@id\": \"#pv\", \"@type\": \"PropertyValue\", \"value\": \"/^readme\\\\.md$/i\"}",
				"{\"@id\": \"#broken\", \"@type\": \"rdf:Property\", \"rdfs:label\": \"x\","
						+ " \"domainIncludes\": {\"@id\": \"#c\"}, \"rangeIncludes\": {\"@id\": \"#bpv\"}}",
				"{\"@id\": \"#bpv\", \"@type\": \"PropertyValue\", \"value\": \"/[unclosed/\"}");
		ValueConstraint readme = (ValueConstraint) profile.propertyRules().get("#readme").range().get(0);
		assertTrue(readme.regex().orElseThrow().matcher("README.MD").matches());
		assertFalse(readme.regex().orElseThrow().matcher("readme.txt").matches());
		assertTrue(reports(profile, Severity.WARNING, "#bpv has an invalid regular expression"));
		// No @id constrained to ro-crate-metadata.json in this profile
		assertTrue(profile.descriptorRule().isEmpty());
		assertTrue(reports(profile, Severity.WARNING, "metadata descriptor's class rule is unknown"));
	}

	@Test
	public void unresolvedReferencesReported() {
		Profile profile = profile("{\"@id\": \"#p\"}, {\"@id\": \"#missing\"}",
				"{\"@id\": \"#p\", \"@type\": \"rdf:Property\", \"rdfs:label\": \"p\","
						+ " \"domainIncludes\": {\"@id\": \"#notAClass\"}}");
		assertTrue(reports(profile, Severity.WARNING, "Rule #missing is listed in the schema resource, but not described"));
		assertTrue(reports(profile, Severity.WARNING, "has the domain #notAClass, which is not a class rule"));
		List<Problem> errors = profile.problems().stream().filter(p -> p.severity() == Severity.ERROR).toList();
		assertEquals(List.of(), errors);
	}
}
