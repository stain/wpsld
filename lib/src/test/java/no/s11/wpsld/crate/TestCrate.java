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
import java.util.Map;
import java.util.Optional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

	private static JsonNode ref(String id) {
		return JSON.objectNode().put("@id", id);
	}

	@Test
	public void linkedFromAnyPropertyOfTheRoot() {
		Crate crate = new Crate();
		Entity file = crate.add("File");
		Entity alice = crate.add("Person");
		Entity org = crate.add("Organization");
		Entity topic = crate.add("DefinedTerm");
		Entity workflow = crate.add("File");
		Entity lone = crate.add("Person");
		// Need no link
		Entity preview = crate.add("CreativeWork");
		crate.rename(preview, Crate.RO_CRATE_PREVIEW_HTML);
		crate.getRoot().set("hasPart", Optional.of(ref(file.getId())));
		// Contextual entities, linked from a data entity, or from another
		// contextual entity
		file.set("author", Optional.of(ref(alice.getId())));
		alice.set("affiliation", Optional.of(ref(org.getId())));
		// Other properties of the root link too, not only hasPart and mentions
		crate.getRoot().set("about", Optional.of(ref(topic.getId())));
		crate.getRoot().set("mainEntity", Optional.of(ref(workflow.getId())));
		assertEquals(List.of(lone), crate.unlinked());
	}

	@Test
	public void mentionUnlinked() {
		Crate crate = new Crate();
		Entity alice = crate.add("Person");
		Entity org = crate.add("Organization");
		Entity bob = crate.add("Person");
		crate.getRoot().set("mentions", Optional.of(ref("http://example.com/elsewhere")));
		alice.set("affiliation", Optional.of(ref(org.getId())));
		bob.set("affiliation", Optional.of(ref(org.getId())));
		// The organization is linked through Alice, mentioned before it
		assertEquals(List.of(alice, bob), crate.mentionUnlinked());
		assertEquals(List.of(ref("http://example.com/elsewhere"), ref(alice.getId()), ref(bob.getId())),
				crate.getRoot().getAll("mentions"));
		assertEquals(List.of(), crate.unlinked());
		assertEquals(List.of(), crate.mentionUnlinked());
	}

	@Test
	public void unmentionLinked() {
		Crate crate = new Crate();
		Entity file = crate.add("File");
		Entity alice = crate.add("Person");
		Entity bob = crate.add("Person");
		Entity org = crate.add("Organization");
		crate.getRoot().set("hasPart", Optional.of(ref(file.getId())));
		file.set("author", Optional.of(ref(alice.getId())));
		// Organization is linked only from a mentioned entity
		bob.set("affiliation", Optional.of(ref(org.getId())));
		crate.getRoot().setAll("mentions", List.of(ref(file.getId()), ref("http://example.com/elsewhere"),
				ref(alice.getId()), ref(bob.getId()), ref(org.getId()), ref("#missing")));
		assertEquals(List.of(file, alice), crate.unmentionLinked());
		// Kept: other mentions, in their order, even those linked through them
		assertEquals(List.of(ref("http://example.com/elsewhere"), ref(bob.getId()), ref(org.getId()),
				ref("#missing")), crate.getRoot().getAll("mentions"));
		assertEquals(List.of(), crate.unmentionLinked());
		assertEquals(List.of(), crate.unlinked());
	}

	@Test
	public void addReferenced() {
		Crate crate = new Crate();
		Entity alice = crate.add("Person");
		crate.getRoot().setAll("author", List.of(ref(alice.getId()), ref("#bob")));
		alice.set("affiliation", Optional.of(ref("https://ror.org/027m9bs27")));
		// Once each, and not for the metadata descriptor
		alice.set("knows", Optional.of(ref("#bob")));
		alice.set("subjectOf", Optional.of(ref(Crate.RO_CRATE_METADATA_JSON)));
		List<Entity> added = crate.addReferenced();
		assertEquals(List.of("#bob", "https://ror.org/027m9bs27"), added.stream().map(Entity::getId).toList());
		assertTrue(added.stream().allMatch(e -> e.getTypes().equals(List.of("Thing"))));
		assertEquals(Optional.of(added.get(0)), crate.get("#bob"));
		assertEquals(List.of(), crate.addReferenced());
	}

	@Test
	public void dataEntitiesArePartsOfParts() {
		Crate crate = new Crate();
		Entity folder = crate.add("Dataset");
		Entity file = crate.add("File");
		Entity cited = crate.add("File");
		crate.getRoot().set("hasPart", Optional.of(ref(folder.getId())));
		folder.set("hasPart", Optional.of(ref(file.getId())));
		// A File only cited is a contextual entity
		file.set("citation", Optional.of(ref(cited.getId())));
		assertEquals(List.of(folder, file), crate.dataEntities());
		assertEquals(List.of(), crate.unlinked());
	}

	@Test
	public void pasteKeepsIdUnlessTaken() {
		Crate crate = new Crate();
		Entity alice = crate.add("Person");
		alice.set("knows", Optional.of(JSON.arrayNode().add(ref("#bob"))));
		Entity copy = crate.paste(alice);
		assertEquals("#person-2", copy.getId());
		assertEquals(alice.getValues(), copy.getValues());
		// A copy of the values, not the same
		((com.fasterxml.jackson.databind.node.ArrayNode) copy.get("knows").orElseThrow()).add(ref("#carol"));
		assertEquals(List.of(ref("#bob")), alice.getAll("knows"));

		Entity other = new Entity("https://orcid.org/0000-0001-9842-9718", "Person");
		assertEquals("https://orcid.org/0000-0001-9842-9718", crate.paste(other).getId());
	}

	/**
	 * Two persons with a shared affiliation, as copied from another crate
	 */
	private static List<Entity> aliceBobAndUom() {
		Entity alice = new Entity("https://orcid.org/0000-0002-1825-0097", "Person");
		alice.set("name", Optional.of(JSON.textNode("Alice")));
		alice.set("affiliation", Optional.of(ref("#uom")));
		Entity bob = new Entity("#bob", "Person");
		bob.set("name", Optional.of(JSON.textNode("Bob")));
		bob.set("affiliation", Optional.of(ref("#uom")));
		bob.set("knows", Optional.of(ref("https://orcid.org/0000-0002-1825-0097")));
		Entity uom = new Entity("#uom", "Organization");
		uom.set("name", Optional.of(JSON.textNode("University of Manchester")));
		return List.of(alice, bob, uom);
	}

	@Test
	public void pasteSeveralIntoAnotherCrate() {
		Crate crate = new Crate();
		Crate.Pasted pasted = crate.paste(aliceBobAndUom(), Map.of(), false);
		assertEquals(List.of("https://orcid.org/0000-0002-1825-0097", "#bob", "#uom"),
				pasted.added().stream().map(Entity::getId).toList());
		assertEquals(Map.of(), pasted.renamed());
		assertEquals(List.of(), pasted.dangling());
		assertEquals(List.of(), pasted.ambiguous());
		assertEquals(Map.of(), pasted.existing());
	}

	@Test
	public void pasteSeveralRenamesTakenLocalIds() {
		Crate crate = new Crate();
		Entity uppsala = crate.add("Organization");
		crate.rename(uppsala, "#uom");
		crate.add("Organization");
		Crate.Pasted pasted = crate.paste(aliceBobAndUom(), Map.of(), false);
		// #organization-1 is taken too
		assertEquals(Map.of("#uom", "#organization-2"), pasted.renamed());
		Entity alice = crate.get("https://orcid.org/0000-0002-1825-0097").orElseThrow();
		Entity bob = crate.get("#bob").orElseThrow();
		assertEquals(ref("#organization-2"), alice.get("affiliation").orElseThrow());
		assertEquals(ref("#organization-2"), bob.get("affiliation").orElseThrow());
		assertEquals("University of Manchester", crate.get("#organization-2").orElseThrow().getLabel());
		// The crate's own #uom is left alone
		assertEquals("#uom", uppsala.getId());

		// Pasted again: the local ones are copies, the absolute one is there
		List<Entity> again = aliceBobAndUom();
		Crate.Pasted second = crate.paste(again, Map.of(), false);
		assertEquals(List.of("#person-1", "#organization-3"), second.added().stream().map(Entity::getId).toList());
		assertEquals(ref("#organization-3"), second.added().get(0).get("affiliation").orElseThrow());
		// Alice there, but with another affiliation: to merge
		assertEquals(1, second.existing().size());
		Map.Entry<Entity, Entity> merge = second.existing().entrySet().iterator().next();
		assertSame(alice, merge.getValue());
		assertEquals(ref("#organization-3"), merge.getKey().get("affiliation").orElseThrow());
		// Copies left as they were
		assertEquals(ref("#uom"), again.get(1).get("affiliation").orElseThrow());
	}

	@Test
	public void pasteRenamesAtOnce() {
		Crate crate = new Crate();
		crate.rename(crate.add("Organization"), "#a");
		crate.rename(crate.add("Organization"), "#organization-1");
		// #a becomes #organization-2: a later copy's rename doesn't take it on
		Entity a = new Entity("#a", "Organization");
		Entity org = new Entity("#organization-1", "Organization");
		Entity x = new Entity("#x", "Person");
		x.setAll("affiliation", List.of(ref("#a"), ref("#organization-1")));
		crate.paste(List.of(a, org, x), Map.of(), false);
		assertEquals(List.of(ref("#organization-2"), ref("#organization-3")),
				crate.get("#x").orElseThrow().getAll("affiliation"));
	}

	@Test
	public void pasteSameAsExistingLeavesItOut() {
		Crate crate = new Crate();
		Crate.Pasted first = crate.paste(aliceBobAndUom(), Map.of(), false);
		Crate.Pasted pasted = crate.paste(aliceBobAndUom().subList(0, 1), Map.of(), true);
		assertEquals(List.of(), pasted.added());
		assertEquals(Map.of(), pasted.existing());
		assertEquals(3, first.added().size());
		assertEquals(4, crate.getEntities().size());
	}

	@Test
	public void pasteBlankNodesGetLocalIds() {
		Crate crate = new Crate();
		Entity b0 = new Entity("_:b0", "Organization");
		Entity x = new Entity("#x", "Person");
		x.set("affiliation", Optional.of(ref("_:b0")));
		Crate.Pasted pasted = crate.paste(List.of(b0, x), Map.of(), false);
		assertEquals(Map.of("_:b0", "#organization-1"), pasted.renamed());
		assertEquals(ref("#organization-1"), crate.get("#x").orElseThrow().get("affiliation").orElseThrow());
	}

	@Test
	public void pasteReportsReferencesNotCopied() {
		List<Entity> copies = aliceBobAndUom();
		Entity uom = copies.get(2);
		List<Entity> bobOnly = List.of(copies.get(1));
		Map<String, Entity> referenced = Map.of("#uom", uom, copies.get(0).getId(), copies.get(0));

		// Neither Alice nor #uom here
		Crate empty = new Crate();
		Crate.Pasted pasted = empty.paste(bobOnly, referenced, false);
		assertEquals(List.of("#uom", "https://orcid.org/0000-0002-1825-0097"), pasted.dangling());
		assertEquals(List.of(), pasted.ambiguous());

		// Another #uom: may not be the one meant
		Crate other = new Crate();
		Entity uppsala = other.add("Organization");
		uppsala.set("name", Optional.of(JSON.textNode("Uppsala University")));
		other.rename(uppsala, "#uom");
		other.paste(List.of(copies.get(0)), Map.of(), false);
		pasted = other.paste(bobOnly, referenced, false);
		assertEquals(List.of(), pasted.dangling());
		assertEquals(List.of("#uom"), pasted.ambiguous());

		// The same #uom as copied
		Crate same = new Crate();
		same.paste(List.of(uom), Map.of(), false);
		assertEquals(List.of("https://orcid.org/0000-0002-1825-0097"),
				same.paste(bobOnly, referenced, false).dangling());
		assertEquals(List.of(), same.paste(bobOnly, referenced, false).ambiguous());

		// From this crate: its #uom is the one meant, whatever it is now
		assertEquals(List.of(), other.paste(bobOnly, Map.of(), true).ambiguous());
	}

	@Test
	public void entityFromJson() {
		Entity alice = crate("Person").get();
		alice.set("name", Optional.of(JSON.textNode("Alice")));
		alice.setAll("knows", List.of(ref("#bob"), ref("#carol")));
		assertEquals(alice.toJson(), Entity.fromJson(alice.toJson()).orElseThrow().toJson());

		ObjectNode json = JSON.objectNode().put("@id", "#x");
		json.putArray("@type").add("Person").add(1);
		json.put("@reverse", "ignored");
		json.putNull("name");
		Entity x = Entity.fromJson(json).orElseThrow();
		assertEquals(List.of("Person"), x.getTypes());
		assertEquals(Map.of(), x.getValues());
		assertEquals(List.of("Thing"), Entity.fromJson(JSON.objectNode().put("@id", "#y")).orElseThrow().getTypes());
		assertTrue(Entity.fromJson(JSON.objectNode().put("name", "No @id")).isEmpty());
		assertTrue(Entity.fromJson(JSON.textNode("#z")).isEmpty());
	}

	private static Optional<Entity> crate(String type) {
		return Optional.of(new Crate().add(type));
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
	public void renameUpdatesReferences() {
		Crate crate = new Crate();
		Entity alice = crate.add("Person");
		Entity file = crate.add("File");
		crate.getRoot().setAll("author", List.of(ref(alice)));
		file.setAll("author", List.of(ref(alice), JSON.objectNode().put("@id", "#bob")));
		crate.rename(alice, "https://orcid.org/0000-0002-1825-0097");
		assertEquals("https://orcid.org/0000-0002-1825-0097", alice.getId());
		assertEquals(List.of(ref(alice)), crate.getRoot().getAll("author"));
		assertEquals(List.of(ref(alice), JSON.objectNode().put("@id", "#bob")), file.getAll("author"));
	}

	@Test
	public void renameRootUpdatesDescriptor() {
		Crate crate = new Crate();
		Entity part = crate.add("File");
		part.setAll("isPartOf", List.of(ref(crate.getRoot())));
		crate.rename(crate.getRoot(), "https://example.com/crate/");
		JsonNode graph = crate.toJson().get("@graph");
		assertEquals("https://example.com/crate/", graph.get(0).get("about").get("@id").asText());
		assertEquals("https://example.com/crate/", graph.get(1).get("@id").asText());
		assertEquals("https://example.com/crate/", part.get("isPartOf").get().get("@id").asText());
	}

	@Test(expected = IllegalArgumentException.class)
	public void renameToExistingNotAllowed() {
		Crate crate = new Crate();
		Entity a = crate.add("Person");
		Entity b = crate.add("Person");
		crate.rename(a, b.getId());
	}

	@Test
	public void merge() {
		Crate crate = new Crate();
		Entity alice = crate.add("Person");
		Entity duplicate = crate.add("Researcher");
		alice.setAll("name", List.of(JSON.textNode("Alice")));
		duplicate.setAll("name", List.of(JSON.textNode("Alice"), JSON.textNode("A. Smith")));
		duplicate.setAll("email", List.of(JSON.textNode("alice@example.com")));
		crate.getRoot().setAll("author", List.of(ref(duplicate)));

		crate.merge(alice, duplicate);
		assertEquals(List.of(crate.getRoot(), alice), crate.getEntities());
		assertEquals(List.of("Person", "Researcher"), alice.getTypes());
		assertEquals(List.of(JSON.textNode("Alice"), JSON.textNode("A. Smith")), alice.getAll("name"));
		assertEquals(List.of(JSON.textNode("alice@example.com")), alice.getAll("email"));
		assertEquals(List.of(ref(alice)), crate.getRoot().getAll("author"));
	}

	@Test
	public void mergeRootKeepsItFirst() {
		Crate crate = new Crate();
		Entity other = crate.add("Dataset");
		Entity file = crate.add("File");
		file.setAll("isPartOf", List.of(ref(crate.getRoot())));
		crate.merge(other, crate.getRoot());
		assertSame(other, crate.getRoot());
		assertEquals(2, crate.getEntities().size());
		assertEquals(List.of(ref(other)), file.getAll("isPartOf"));
	}

	private static JsonNode ref(Entity entity) {
		return JSON.objectNode().put("@id", entity.getId());
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

	@Test(expected = IllegalArgumentException.class)
	public void typeRequired() {
		new Entity("#x", "Thing").setTypes(List.of());
	}

	@Test(expected = IllegalArgumentException.class)
	public void idNotAProperty() {
		new Entity("#x", "Thing").set("@id", Optional.of(JSON.textNode("#y")));
	}

	@Test
	public void labelFallsBackToId() {
		Entity entity = new Entity("#x", "Thing");
		entity.set("name", Optional.of(JSON.objectNode().put("@id", "#name")));
		assertEquals("#x", entity.getLabel());
		entity.set("name", Optional.of(JSON.textNode("  ")));
		assertEquals("#x", entity.getLabel());
	}

	@Test
	public void identifiersFromProfileTypes() {
		// No colon from a compact type name like ldac:Speaker in the @id
		assertEquals("#ldac-speaker-1", new Crate().add("ldac:Speaker").getId());
	}

	@Test
	public void renameToSameIdChangesNothing() {
		Crate crate = new Crate();
		Entity alice = crate.add("Person");
		crate.rename(alice, alice.getId());
		assertEquals("#person-1", alice.getId());
	}

	@Test
	public void mergeWithItselfChangesNothing() {
		Crate crate = new Crate();
		Entity alice = crate.add("Person");
		alice.setAll("name", List.of(JSON.textNode("Alice")));
		crate.merge(alice, alice);
		assertEquals(2, crate.getEntities().size());
		assertEquals(List.of(JSON.textNode("Alice")), alice.getAll("name"));
	}

	/**
	 * The exact JSON written, so that changes to the output are noticed.
	 */
	@Test
	public void goldenFile() throws Exception {
		Crate crate = new Crate();
		Entity root = crate.getRoot();
		root.setAll("name", List.of(JSON.textNode("Example crate")));
		root.setAll("description", List.of(JSON.textNode("An example, with ÆØÅ")));
		root.setAll("datePublished", List.of(JSON.textNode("2026-11-05")));
		root.setAll("license", List.of(JSON.objectNode().put("@id", "http://spdx.org/licenses/CC0-1.0")));
		Entity alice = crate.add("Person");
		alice.setAll("name", List.of(JSON.textNode("Alice")));
		Entity file = crate.add("File");
		file.setTypes(List.of("File", "SoftwareSourceCode"));
		// Scripts MUST have a name (RO-Crate 1.3)
		file.setAll("name", List.of(JSON.textNode("Example script")));
		crate.rename(file, "code.py");
		root.setAll("author", List.of(JSON.objectNode().put("@id", alice.getId())));
		root.setAll("hasPart", List.of(JSON.objectNode().put("@id", "code.py")));
		String written = Crate.WRITER.writeValueAsString(crate.toJson());
		try (InputStream expected = getClass().getResourceAsStream("expected-ro-crate-metadata.json")) {
			assertNotNull(expected);
			assertEquals(new String(expected.readAllBytes(), StandardCharsets.UTF_8).strip(), written.strip());
		}
	}

	private static List<String> parts(Entity entity) {
		return entity.getAll("hasPart").stream().map(v -> v.get("@id").asText()).toList();
	}

	/** Files become parts of the dataset of their folder, else of the root */
	@Test
	public void addParts() {
		Crate crate = new Crate();
		Entity data = crate.add("Dataset");
		crate.rename(data, "data/");
		crate.getRoot().setAll("hasPart", List.of(ref("data/")));
		Entity counts = crate.add("File");
		crate.rename(counts, "data/raw/counts.csv");
		Entity readme = crate.add("File");
		crate.rename(readme, "./README.md");
		assertSame(data, crate.partOf("data/raw/counts.csv"));
		assertSame(crate.getRoot(), crate.partOf("README.md"));
		assertEquals(List.of(readme, counts), crate.addParts(List.of(counts, readme, crate.getRoot(), data)));
		assertEquals(List.of("data/raw/counts.csv"), parts(data));
		assertEquals(List.of("data/", "./README.md"), parts(crate.getRoot()));
		// Already parts: not again
		assertEquals(List.of(), crate.addParts(List.of(counts)));
	}

	/** Datasets for folders, the outer one first, so the inner is its part */
	@Test
	public void addFolders() {
		Crate crate = new Crate();
		Entity counts = crate.add("File");
		crate.rename(counts, "raw/2025/counts.csv");
		List<Entity> added = crate.addFolders(List.of("raw/2025/", "raw/"));
		assertEquals(List.of("raw/2025/", "raw/"), added.stream().map(Entity::getId).toList());
		assertEquals(List.of("Dataset"), added.get(0).getTypes());
		assertEquals(List.of("raw/"), parts(crate.getRoot()));
		assertEquals(List.of("raw/2025/"), parts(added.get(1)));
		assertSame(added.get(0), crate.partOf("raw/2025/counts.csv"));
	}

	@Test
	public void specVersion() {
		assertEquals(Optional.of("1.3"), new Crate().specVersion());
		CrateReader reader = new CrateReader();
		String v12 = "{\"@context\": \"https://w3id.org/ro/crate/1.2/context\", \"@graph\": ["
				+ "{\"@id\": \"ro-crate-metadata.json\", \"@type\": \"CreativeWork\", \"about\": {\"@id\": \"./\"},"
				+ " \"conformsTo\": {\"@id\": \"https://w3id.org/ro/crate/1.2\"}},"
				+ "{\"@id\": \"./\", \"@type\": \"Dataset\"}]}";
		assertEquals(Optional.of("1.2"), reader.read(new java.io.ByteArrayInputStream(v12.getBytes(StandardCharsets.UTF_8)),
				"test").crate().orElseThrow().specVersion());
		String v10 = "{\"@context\": \"https://w3id.org/ro/crate/1.0/context\", \"@graph\": ["
				+ "{\"@id\": \"ro-crate-metadata.jsonld\", \"@type\": \"CreativeWork\", \"about\": {\"@id\": \"./\"}},"
				+ "{\"@id\": \"./\", \"@type\": \"Dataset\"}]}";
		assertEquals(Optional.of("1.0"), reader.read(new java.io.ByteArrayInputStream(v10.getBytes(StandardCharsets.UTF_8)),
				"test").crate().orElseThrow().specVersion());
	}
}
