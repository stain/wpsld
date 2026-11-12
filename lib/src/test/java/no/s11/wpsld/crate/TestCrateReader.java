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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import no.s11.wpsld.crate.CrateDocument.Problem;
import no.s11.wpsld.crate.CrateDocument.Severity;

public class TestCrateReader {
	private static final String RDFS_CLASS = "http://www.w3.org/2000/01/rdf-schema#Class";
	private static final String RDF_PROPERTY = "http://www.w3.org/1999/02/22-rdf-syntax-ns#Property";
	private static final String PROV_SPECIALIZATION_OF = "http://www.w3.org/ns/prov#specializationOf";
	private static final String S = "http://schema.org/";
	private static final String CONTEXT = "\"@context\": \"https://w3id.org/ro/crate/1.3/context\"";
	private static final String DESCRIPTOR = "{\"@id\": \"ro-crate-metadata.json\", \"@type\": \"CreativeWork\","
			+ " \"about\": {\"@id\": \"./\"}, \"conformsTo\": {\"@id\": \"https://w3id.org/ro/crate/1.3\"}}";

	private final CrateReader reader = new CrateReader();

	private CrateDocument resource(String name) throws Exception {
		try (InputStream in = getClass().getResourceAsStream(name)) {
			assertNotNull(name, in);
			return reader.read(in, name);
		}
	}

	private CrateDocument json(String json) {
		return reader.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test");
	}

	/** A crate with the descriptor and these entities after the root */
	private CrateDocument graph(String... entities) {
		return json("{" + CONTEXT + ", \"@graph\": [" + DESCRIPTOR + ", {\"@id\": \"./\", \"@type\": \"Dataset\"}"
				+ (entities.length > 0 ? ", " + String.join(", ", entities) : "") + "]}");
	}

	private static boolean reports(CrateDocument document, Severity severity, String text) {
		return document.problems().stream().anyMatch(p -> p.severity() == severity && p.message().contains(text));
	}

	@Test
	public void maspProfileCrate() throws Exception {
		CrateDocument document = resource("masp/ro-crate-masp-profile.json");
		assertFalse(document.problems().toString(), document.hasErrors());
		assertEquals(List.of("https://w3id.org/ro/crate/1.2"), document.conformsTo());
		Crate crate = document.crate().orElseThrow();
		Entity root = crate.getRoot();
		assertEquals("https://language-research-technology.github.io/ro-crate-masp/profiles/ro-crate-masp/profile-crate/",
				root.getId());
		assertEquals(Set.of(S + "Dataset", "http://www.w3.org/ns/dx/prof/Profile"), document.types(root));
		// As in MASP's own catalogue: 13 classes, 29 properties
		assertEquals(13, document.entitiesOfType(RDFS_CLASS).size());
		assertEquals(29, document.entitiesOfType(RDF_PROPERTY).size());
		// The descriptor is not one of the crate's entities
		assertTrue(crate.get("ro-crate-metadata.json").isEmpty());
		assertTrue(document.descriptor().isPresent());
	}

	@Test
	public void maspRulesByIri() throws Exception {
		CrateDocument document = resource("masp/ro-crate-masp-profile.json");
		Entity about = document.entity("#prop_about_MetadataDescriptor").orElseThrow();
		assertTrue(document.hasType(about, RDF_PROPERTY));
		assertEquals(List.of(S + "about"), document.references(about, PROV_SPECIALIZATION_OF));
		// domainIncludes via @vocab and the RO-Crate context, whatever it is called
		assertEquals(List.of("#class_MetadataDescriptor"), document.references(about, S + "domainIncludes"));
		assertEquals(List.of("#class_MetadataDescriptor"), document.references(about, "https://schema.org/domainIncludes"));
	}

	/**
	 * MASP crates use sh:minCount without defining sh: in their context;
	 * reported once, the values kept under the written name.
	 */
	@Test
	public void undefinedPrefixReportedOnce() throws Exception {
		CrateDocument document = resource("masp/ro-crate-masp-profile.json");
		List<Problem> prefix = document.problems().stream().filter(p -> p.message().startsWith("sh:")).toList();
		assertEquals(document.problems().toString(), 1, prefix.size());
		assertEquals(Severity.WARNING, prefix.get(0).severity());
		assertTrue(prefix.get(0).message().contains("used 31 times"));
		Entity classRule = document.entity("#class_MetadataDescriptor").orElseThrow();
		assertEquals(1, classRule.getAll("sh:minCount").get(0).asInt());
		// Taken as an absolute IRI with the scheme sh, as JSON-LD 1.0 does
		assertEquals(1, document.values(classRule, "sh:minCount").size());
	}

	@Test
	public void roCrateProfileCrate() throws Exception {
		CrateDocument document = resource("masp/ro-crate-profile.json");
		assertFalse(document.hasErrors());
		assertEquals("./", document.crate().orElseThrow().getRoot().getId());
		assertEquals(3, document.entitiesOfType(RDFS_CLASS).size());
		assertEquals(6, document.entitiesOfType(RDF_PROPERTY).size());
	}

	/**
	 * A crate written by Crate reads back without problems and writes the
	 * same again.
	 */
	@Test
	public void roundTrip() throws Exception {
		Path file = Path.of(getClass().getResource("expected-ro-crate-metadata.json").toURI());
		CrateDocument document = reader.read(file);
		assertEquals(List.of(), document.problems());
		String written = Crate.WRITER.writeValueAsString(document.crate().orElseThrow().toJson());
		assertEquals(Files.readString(file).strip(), written.strip());
	}

	@Test
	public void directory() throws Exception {
		Path dir = Files.createTempDirectory("crate");
		Files.copy(getClass().getResourceAsStream("expected-ro-crate-metadata.json"),
				dir.resolve("ro-crate-metadata.json"));
		// The root, #person-1 and code.py; the descriptor is not an entity of the crate
		assertEquals(3, reader.read(dir).crate().orElseThrow().getEntities().size());
	}

	@Test
	public void notJson() {
		CrateDocument document = json("{ not json");
		assertTrue(document.crate().isEmpty());
		assertTrue(reports(document, Severity.ERROR, "Not JSON"));
	}

	@Test
	public void missingFile() {
		CrateDocument document = reader.read(Path.of("/does/not/exist/ro-crate-metadata.json"));
		assertTrue(document.crate().isEmpty());
		assertTrue(reports(document, Severity.ERROR, "Can't read"));
	}

	@Test
	public void notAnObject() {
		assertTrue(reports(json("[]"), Severity.ERROR, "expected a JSON object"));
	}

	@Test
	public void notFlattened() {
		CrateDocument document = json("{" + CONTEXT + ", \"@id\": \"./\", \"@type\": \"Dataset\"}");
		assertTrue(document.crate().isEmpty());
		assertTrue(reports(document, Severity.ERROR, "must be flattened"));
	}

	@Test
	public void badEntitiesSkipped() {
		CrateDocument document = graph("42", "{\"@type\": \"Person\", \"name\": \"No id\"}",
				"{\"@id\": \"#ok\", \"@type\": \"Person\"}");
		assertTrue(reports(document, Severity.ERROR, "@graph[2] is not an object"));
		assertTrue(reports(document, Severity.ERROR, "@graph[3] has no @id"));
		assertTrue(document.entity("#ok").isPresent());
	}

	@Test
	public void duplicatesMerged() {
		CrateDocument document = graph("{\"@id\": \"#a\", \"@type\": \"Person\", \"name\": \"Alice\"}",
				"{\"@id\": \"#a\", \"@type\": \"Researcher\", \"email\": \"alice@example.com\"}");
		assertTrue(reports(document, Severity.WARNING, "More than one entity with @id #a"));
		Entity alice = document.entity("#a").orElseThrow();
		assertEquals(List.of("Person", "Researcher"), alice.getTypes());
		assertEquals("Alice", alice.get("name").orElseThrow().asText());
		assertEquals("alice@example.com", alice.get("email").orElseThrow().asText());
	}

	@Test
	public void missingTypeReadAsThing() {
		CrateDocument document = graph("{\"@id\": \"#x\", \"name\": \"X\"}", "{\"@id\": \"#y\", \"@type\": 42}");
		assertEquals(List.of("Thing"), document.entity("#x").orElseThrow().getTypes());
		assertEquals(List.of("Thing"), document.entity("#y").orElseThrow().getTypes());
		assertTrue(reports(document, Severity.WARNING, "#x has no @type"));
		assertTrue(reports(document, Severity.WARNING, "#y has an invalid @type"));
	}

	@Test
	public void valuesKeptAsWritten() {
		CrateDocument document = graph("{\"@id\": \"#x\", \"@type\": \"Thing\", \"empty\": [], \"nothing\": null,"
				+ " \"name\": {\"@value\": \"X\", \"@language\": \"en\"},"
				+ " \"author\": {\"@type\": \"Person\", \"name\": \"Nested\"},"
				+ " \"keywords\": [\"a\", null, \"b\"]}");
		Entity x = document.entity("#x").orElseThrow();
		// JSON-LD drops null and empty values
		assertFalse(x.get("empty").isPresent());
		assertFalse(x.get("nothing").isPresent());
		assertEquals(2, x.getAll("keywords").size());
		assertEquals("en", x.get("name").orElseThrow().get("@language").asText());
		// Nested, not flattened: reported, but not lost
		assertEquals("Nested", x.get("author").orElseThrow().get("name").asText());
		assertTrue(reports(document, Severity.WARNING, "#x author has a nested object"));
		assertFalse(document.problems().toString(), reports(document, Severity.WARNING, "#x name"));
	}

	/** Entities nested with their @id are flattened, as ROHub's hasPart */
	@Test
	public void nestedEntitiesFlattened() {
		CrateDocument document = graph(
				"{\"@id\": \"#x\", \"@type\": \"Thing\", \"hasPart\": [{\"@id\": \"data/\", \"@type\": \"Dataset\"},"
						+ " {\"@id\": \"new.csv\", \"@type\": \"File\", \"name\": \"New\"}],"
						+ " \"author\": {\"@id\": \"#alice\", \"name\": \"Alice\"}}",
				"{\"@id\": \"data/\", \"@type\": [\"Dataset\", \"Collection\"], \"name\": \"Data\"}",
				"{\"@id\": \"#alice\", \"@type\": \"Person\"}");
		Entity x = document.entity("#x").orElseThrow();
		assertEquals(List.of("{\"@id\":\"data/\"}", "{\"@id\":\"new.csv\"}"),
				x.getAll("hasPart").stream().map(Object::toString).toList());
		assertEquals("{\"@id\":\"#alice\"}", x.get("author").orElseThrow().toString());
		Entity data = document.entity("data/").orElseThrow();
		assertEquals(List.of("Dataset", "Collection"), data.getTypes());
		assertEquals("Data", data.get("name").orElseThrow().asText());
		assertEquals("New", document.entity("new.csv").orElseThrow().get("name").orElseThrow().asText());
		Entity alice = document.entity("#alice").orElseThrow();
		assertEquals(List.of("Person"), alice.getTypes());
		assertEquals("Alice", alice.get("name").orElseThrow().asText());
		assertTrue(reports(document, Severity.WARNING, "3 entities were nested in values"));
		assertFalse(document.problems().toString(), reports(document, Severity.WARNING, "no @type"));
	}

	@Test
	public void unknownTermsReported() {
		CrateDocument document = json("{\"@context\": {\"name\": \"http://schema.org/name\"}, \"@graph\": ["
				+ DESCRIPTOR + ", {\"@id\": \"./\", \"@type\": \"Dataset\", \"unknownProperty\": \"x\"}]}");
		assertTrue(reports(document, Severity.WARNING, "unknownProperty is not defined by the @context"));
		// Types too
		assertTrue(reports(document, Severity.WARNING, "Dataset is not defined by the @context"));
		// Still kept
		assertEquals("x", document.crate().orElseThrow().getRoot().get("unknownProperty").orElseThrow().asText());
	}

	@Test
	public void unknownContextReported() {
		CrateDocument document = json("{\"@context\": [\"https://w3id.org/ro/crate/1.3/context\","
				+ " \"https://example.com/unknown/context\"], \"@graph\": [" + DESCRIPTOR
				+ ", {\"@id\": \"./\", \"@type\": \"Dataset\"}]}");
		assertTrue(reports(document, Severity.WARNING, "@context: Context https://example.com/unknown/context"));
		assertFalse(document.hasErrors());
	}

	@Test
	public void noDescriptorFallsBackToDotSlash() {
		CrateDocument document = json("{" + CONTEXT + ", \"@graph\": [{\"@id\": \"./\", \"@type\": \"Dataset\"}]}");
		assertTrue(reports(document, Severity.WARNING, "No metadata descriptor"));
		assertEquals("./", document.crate().orElseThrow().getRoot().getId());
	}

	@Test
	public void noRoot() {
		CrateDocument document = json("{" + CONTEXT + ", \"@graph\": [{\"@id\": \"ro-crate-metadata.json\","
				+ " \"@type\": \"CreativeWork\", \"about\": {\"@id\": \"https://example.com/missing\"}}]}");
		assertTrue(document.crate().isEmpty());
		assertTrue(reports(document, Severity.WARNING, "is about https://example.com/missing, which is not an entity"));
		assertTrue(reports(document, Severity.ERROR, "No root data entity"));
		assertTrue(reports(document, Severity.WARNING, "doesn't declare an RO-Crate version"));
	}

	@Test
	public void roCrate10Descriptor() {
		CrateDocument document = json("{\"@context\": \"https://w3id.org/ro/crate/1.0/context\", \"@graph\": ["
				+ "{\"@id\": \"ro-crate-metadata.jsonld\", \"@type\": \"CreativeWork\", \"about\": {\"@id\": \"./\"},"
				+ " \"conformsTo\": {\"@id\": \"https://w3id.org/ro/crate/1.0\"}}, {\"@id\": \"./\", \"@type\": \"Dataset\"}]}");
		assertTrue(reports(document, Severity.WARNING, "RO-Crate 1.0's name"));
		assertEquals(List.of("https://w3id.org/ro/crate/1.0"), document.conformsTo());
		assertEquals(1, document.crate().orElseThrow().getEntities().size());
	}

	@Test
	public void rootNotDataset() {
		CrateDocument document = json("{" + CONTEXT + ", \"@graph\": [" + DESCRIPTOR
				+ ", {\"@id\": \"./\", \"@type\": \"CreativeWork\"}]}");
		assertTrue(reports(document, Severity.WARNING, "is not a Dataset"));
	}

	@Test
	public void errorsListedFirst() {
		CrateDocument document = graph("42", "{\"@id\": \"#x\"}");
		assertEquals(Severity.ERROR, document.problems().get(0).severity());
		assertEquals(Severity.WARNING, document.problems().get(document.problems().size() - 1).severity());
	}

	@Test
	public void schemaOrgHttpsNormalised() {
		assertEquals(S + "Dataset", CrateDocument.normalise("https://schema.org/Dataset"));
		assertEquals("https://example.com/x", CrateDocument.normalise("https://example.com/x"));
		CrateDocument document = graph("{\"@id\": \"#x\", \"@type\": \"https://schema.org/Person\"}");
		assertEquals(Optional.of(document.entity("#x").orElseThrow()),
				document.entitiesOfType(S + "Person").stream().findFirst());
	}

	/**
	 * As the specification: if ro-crate-metadata.json's about doesn't
	 * resolve, the legacy ro-crate-metadata.jsonld is tried.
	 */
	@Test
	public void legacyRootWhenRootDoesNotResolve() {
		CrateDocument document = json("{" + CONTEXT + ", \"@graph\": ["
				+ "{\"@id\": \"ro-crate-metadata.json\", \"@type\": \"CreativeWork\", \"about\": {\"@id\": \"#gone\"}},"
				+ "{\"@id\": \"ro-crate-metadata.jsonld\", \"@type\": \"CreativeWork\", \"about\": {\"@id\": \"#legacy\"}},"
				+ "{\"@id\": \"#legacy\", \"@type\": \"Dataset\"}]}");
		assertEquals("#legacy", document.crate().orElseThrow().getRoot().getId());
		assertTrue(reports(document, Severity.WARNING, "is about #gone, which is not an entity"));
		// Neither descriptor is an entity of the crate
		assertEquals(1, document.crate().orElseThrow().getEntities().size());
	}

	/**
	 * Not allowed, but found, as by the specification's SPARQL for finding
	 * roots: an absolute descriptor @id.
	 */
	@Test
	public void absoluteDescriptorId() {
		CrateDocument document = json("{" + CONTEXT + ", \"@graph\": ["
				+ "{\"@id\": \"https://example.com/crate/ro-crate-metadata.json\", \"@type\": \"CreativeWork\","
				+ " \"about\": {\"@id\": \"https://example.com/crate/\"},"
				+ " \"conformsTo\": {\"@id\": \"https://w3id.org/ro/crate/1.3\"}},"
				+ "{\"@id\": \"https://example.com/crate/\", \"@type\": \"Dataset\"}]}");
		assertEquals("https://example.com/crate/", document.crate().orElseThrow().getRoot().getId());
		assertTrue(reports(document, Severity.WARNING, "which MUST be ro-crate-metadata.json"));
		assertFalse(document.hasErrors());
	}

	/**
	 * A crate read is written again as it was: its own @context (RO-Crate
	 * 1.2 with an extra term here) and descriptor, not RO-Crate 1.3's.
	 */
	@Test
	public void writtenAsRead() throws Exception {
		String original = """
				{"@context": ["https://w3id.org/ro/crate/1.2/context", {"ex": "https://example.com/terms#"}],
				 "@graph": [
				  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				   "conformsTo": {"@id": "https://w3id.org/ro/crate/1.2"}, "ex:note": "kept"},
				  {"@id": "./", "@type": "Dataset", "name": "Old crate"},
				  {"@id": "#alice", "@type": "Person", "name": "Alice"}
				 ]}""";
		CrateDocument document = json(original);
		assertEquals(List.of(), document.problems());
		Crate crate = document.crate().orElseThrow();
		assertEquals(new ObjectMapper().readTree(original), crate.toJson());

		// Renaming the root keeps the descriptor about it
		crate.rename(crate.getRoot(), "https://example.com/crate/");
		assertEquals("https://example.com/crate/", crate.toJson().at("/@graph/0/about/@id").asText());
		assertEquals("https://w3id.org/ro/crate/1.2", crate.toJson().at("/@graph/0/conformsTo/@id").asText());
	}
}
