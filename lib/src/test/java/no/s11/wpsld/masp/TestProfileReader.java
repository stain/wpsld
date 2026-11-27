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
		// The absolute root @id and the conformance indicator
		assertTrue(profile.identifiers().contains(profile.id()));
		assertTrue(profile.identifiers().contains(profile.id() + "#profile"));

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
		// The root is ./, the identifier comes from the conformance indicators
		assertEquals("./", profile.id());
		assertEquals(List.of("https://language-research-technology.github.io/ro-crate-masp/profiles/ro-crate/profile-crate/"),
				profile.identifiers());
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
	public void classRulesMatchAllTheirTypes() {
		String prov = "http://www.w3.org/ns/prov#";
		Profile profile = profile("{\"@id\": \"#assess\"}, {\"@id\": \"#send\"}, {\"@id\": \"#plan\"}, {\"@id\": \"#bare\"}",
				"{\"@id\": \"#assess\", \"@type\": \"rdfs:Class\", \"prov:specializationOf\":"
						+ " [{\"@id\": \"https://schema.org/AssessAction\"}, {\"@id\": \"prov:Activity\"}]}",
				"{\"@id\": \"#send\", \"@type\": \"rdfs:Class\", \"prov:specializationOf\":"
						+ " [{\"@id\": \"http://schema.org/SendAction\"}, {\"@id\": \"prov:Activity\"}]}",
				"{\"@id\": \"#plan\", \"@type\": \"rdfs:Class\", \"prov:specializationOf\":"
						+ " [{\"@id\": \"http://schema.org/CreativeWork\"}, {\"@id\": \"prov:Plan\"}]}",
				"{\"@id\": \"#bare\", \"@type\": \"rdfs:Class\"}");
		ClassRule assess = profile.classRule("#assess").orElseThrow();
		ClassRule plan = profile.classRule("#plan").orElseThrow();
		// Sharing prov:Activity with #send is not enough; the https schema.org IRI is read as http
		assertEquals(List.of(assess), profile.classRulesFor(Set.of(S + "AssessAction", prov + "Activity")));
		assertEquals(List.of(), profile.classRulesFor(Set.of(prov + "Activity")));
		// The metadata descriptor is a CreativeWork, but no prov:Plan
		assertEquals(List.of(), profile.classRulesFor(Set.of(S + "CreativeWork")));
		assertEquals(List.of(plan), profile.classRulesFor(Set.of(S + "CreativeWork", prov + "Plan", S + "Thing")));
	}

	@Test
	public void rulesOfTheirOwnTerms() {
		String terms = "https://example.org/terms#";
		Profile profile = profile("{\"@id\": \"https://example.org/terms#SiteVisit\"}, {\"@id\": \"https://example.org/terms#siteName\"},"
				+ " {\"@id\": \"#local\"}",
				"{\"@id\": \"https://example.org/terms#SiteVisit\", \"@type\": \"rdfs:Class\"}",
				"{\"@id\": \"https://example.org/terms#siteName\", \"@type\": \"rdf:Property\","
						+ " \"domainIncludes\": {\"@id\": \"https://example.org/terms#SiteVisit\"}}",
				"{\"@id\": \"#local\", \"@type\": \"rdfs:Class\"}");
		ClassRule visit = profile.classRule(terms + "SiteVisit").orElseThrow();
		// No prov:specializationOf: the rule's own @id is the type
		assertEquals(List.of(terms + "SiteVisit"), visit.types());
		assertEquals("SiteVisit", visit.label());
		assertEquals(List.of(visit), profile.classRulesFor(Set.of(terms + "SiteVisit", S + "Thing")));
		PropertyRule siteName = profile.propertyRules().get(terms + "siteName");
		assertEquals(List.of(terms + "siteName"), siteName.properties());
		assertEquals("siteName", siteName.label());
		assertEquals(List.of(siteName), profile.propertiesOf(visit.id()));
		// A local @id names no type
		assertEquals(List.of(), profile.classRule("#local").orElseThrow().types());
		assertEquals(List.of(), profile.classRulesFor(Set.of("#local")));
	}

	@Test
	public void definitionsInACrate() {
		String terms = "https://example.org/terms#";
		Profile defined = reader.readDefinitions(crateReader.read(new ByteArrayInputStream("""
				{"@context": ["https://w3id.org/ro/crate/1.2/context", {"education": "https://example.org/terms#education"}],
				 "@graph": [{"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"}},
				  {"@id": "./", "@type": "Dataset"},
				  {"@id": "https://example.org/terms#education", "@type": "rdf:Property", "rdfs:label": "education",
				   "rdfs:comment": "Highest degree", "domainIncludes": {"@id": "http://schema.org/Person"},
				   "sh:minCount": 1}
				 ]}""".getBytes(StandardCharsets.UTF_8)), "test"));
		PropertyRule education = defined.propertyRules().get(terms + "education");
		assertEquals("education", education.label());
		assertTrue(education.isRequired());
		// The vocabulary type of its domain stands for a class rule
		assertEquals(List.of(S + "Person"), defined.classRule(S + "Person").orElseThrow().types());
		assertEquals(List.of(education), defined.propertiesOf(S + "Person"));
		assertTrue(defined.rootRule().isEmpty());
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

	/**
	 * As FAIRSCAPE's profile: a schema resource, but JSON Schema files rather
	 * than MASP rules.
	 */
	@Test
	public void noRulesInSchemaResource() {
		Profile profile = reader.read(crateReader.read(new ByteArrayInputStream("""
				{"@context": ["https://w3id.org/ro/crate/1.2/context", {"dct": "http://purl.org/dc/terms/"}],
				 "@graph": [
				  {"@id": "./", "@type": ["Dataset", "Profile"], "hasResource": {"@id": "#schema"}},
				  {"@id": "#schema", "@type": "ResourceDescriptor",
				   "hasRole": {"@id": "http://www.w3.org/ns/dx/prof/role/schema"},
				   "hasArtifact": {"@id": "https://example.com/schemas/"}, "dct:format": "application/schema+json"}
				 ]}""".getBytes(StandardCharsets.UTF_8)), "test"));
		assertTrue(profile.hasErrors());
		assertTrue(profile.problems().toString(), reports(profile, Severity.ERROR,
				"lists no MASP rules in hasPart, so the profile has no rules; its schema is"
						+ " https://example.com/schemas/ (application/schema+json)"));
	}

	@Test
	public void noIdentifiersReported() {
		Profile profile = profile("");
		assertEquals(List.of(), profile.identifiers());
		assertTrue(reports(profile, Severity.WARNING, "crates can't say they conform to this profile"));
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
