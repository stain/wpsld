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

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateChecks;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.masp.Profile;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.masp.ProfileReader;
import no.s11.wpsld.soss.SchemaFromJsonLd;

public class TestProfileFormFields {
	private static final String S = "http://schema.org/";
	private static final String CORE = "https://language-research-technology.github.io/ro-crate-masp/profiles/ro-crate/profile-crate/";
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private static CrateTerms terms;
	private static ProfileFormFields core;

	@BeforeClass
	public static void load() {
		terms = new CrateTerms(new SchemaFromJsonLd());
		core = new ProfileFormFields(ProfileCache.bundled().bundled(CORE).orElseThrow(), new SchemaOrgFormFields(terms));
	}

	private static Map<String, FormField> byName(List<FormField> fields) {
		return fields.stream().collect(Collectors.toMap(FormField::name, Function.identity()));
	}

	private static final Set<URI> DATASET = Set.of(URI.create(S + "Dataset"));

	@Test
	public void name() {
		assertEquals("RO-Crate Machine Actionable Profile 0.1.0", core.getName());
	}

	@Test
	public void rootTiers() {
		List<FormField> fields = core.fieldsFor(DATASET, true);
		// The profile's required properties first
		assertEquals(List.of("datePublished", "description", "license", "name"),
				fields.subList(0, 4).stream().map(FormField::name).toList());
		for (FormField required : fields.subList(0, 4)) {
			assertEquals(FieldTier.REQUIRED, required.tier());
			assertEquals(ProfileFormFields.REQUIRED, required.group());
			assertTrue(required.isRequired());
			assertEquals(1, required.minCount());
		}
		// Then all other schema.org properties, without repeating those
		List<FormField> others = fields.subList(4, fields.size());
		assertTrue(others.stream().allMatch(f -> f.tier() == FieldTier.OTHER));
		Map<String, FormField> other = byName(others);
		assertTrue(other.containsKey("distribution"));
		assertTrue(other.containsKey("author"));
		assertFalse(other.containsKey("name"));
		assertEquals(fields.size(), byName(fields).size());
	}

	@Test
	public void profileLabelsAndRanges() {
		Map<String, FormField> root = byName(core.fieldsFor(DATASET, true));
		assertTrue(root.get("name").comment().orElseThrow().startsWith("SHOULD identify the dataset to humans"));
		assertEquals(ValueKind.TEXT, root.get("name").kind());
		assertEquals(ValueKind.DATE, root.get("datePublished").kind());
		// A License (CreativeWork) entity, a URL or text
		assertEquals(ValueKind.TEXT_OR_REFERENCE, root.get("license").kind());
		assertEquals(URI.create(S + "license"), root.get("license").property());
	}

	/**
	 * The root's rules are for the root only, not for other Datasets.
	 */
	@Test
	public void otherDatasetsNotRoot() {
		List<FormField> folder = core.fieldsFor(DATASET, false);
		assertTrue(folder.stream().allMatch(f -> f.tier() == FieldTier.OTHER));
		assertEquals(new SchemaOrgFormFields(terms).fieldsFor(DATASET).size(), folder.size());
	}

	@Test
	public void noRulesForType() {
		Set<URI> person = Set.of(URI.create(S + "Person"));
		assertEquals(new SchemaOrgFormFields(terms).fieldsFor(person), core.fieldsFor(person, false));
	}

	@Test
	public void requiredFieldsChecked() {
		Crate crate = new Crate();
		List<String> problems = RequiredFields.check(crate, core, terms);
		assertEquals(List.of("The root data entity must have a datePublished", "The root data entity must have a description",
				"The root data entity must have a license", "The root data entity must have a name"), problems);
		// Worded as CrateChecks, so combined without repeating
		assertTrue(CrateChecks.check(crate).containsAll(problems));
		Entity root = crate.getRoot();
		root.setAll("name", List.of(JSON.textNode("Crate")));
		root.setAll("description", List.of(JSON.textNode("About it")));
		root.setAll("datePublished", List.of(JSON.textNode("2026-11-05")));
		root.setAll("license", List.of(JSON.objectNode().put("@id", "http://spdx.org/licenses/CC0-1.0")));
		assertEquals(List.of(), RequiredFields.check(crate, core, terms));
		// A folder Dataset doesn't need them
		crate.add("Dataset");
		assertEquals(List.of(), RequiredFields.check(crate, core, terms));
	}

	@Test
	public void schemaOrgOnlyRequiresNothing() {
		assertEquals(List.of(), RequiredFields.check(new Crate(), new SchemaOrgFormFields(terms), terms));
	}

	@Test
	public void requiredTypeOfRoot() {
		assertEquals(List.of(URI.create(S + "Dataset")), core.requiredTypes(Set.of(), true));
		assertEquals(List.of(), core.requiredTypes(DATASET, false));
		assertEquals(List.of(), new SchemaOrgFormFields(terms).requiredTypes(Set.of(), true));
	}

	/**
	 * A property schema.org has retired stays a field when a profile names
	 * it, in the profile's group; otherwise it is only among the retired.
	 */
	@Test
	public void retiredPropertyOfProfile() throws Exception {
		Path dir = Files.createTempDirectory("profile");
		try (InputStream in = getClass().getResourceAsStream("/masp-profiles/ro-crate.json")) {
			ObjectNode crate = (ObjectNode) new ObjectMapper().readTree(in);
			((ArrayNode) crate.get("@graph")).addObject().put("@id", "#prop_fileFormat").put("@type", "rdf:Property")
				.put("rdfs:label", "fileFormat").put("name", "fileFormat")
				.put("rdfs:comment", "The format, still")
				.set("domainIncludes", JSON.objectNode().put("@id", "#Root_Data_Entity"));
			// Listed among the profile's rules
			for (var entity : crate.get("@graph")) {
				if (entity.path("@id").asText().equals("#hasSpecializedSchema")) {
					((ArrayNode) entity.get("hasPart")).addObject().put("@id", "#prop_fileFormat");
				}
			}
			new ObjectMapper().writeValue(dir.resolve("ro-crate-metadata.json").toFile(), crate);
		}
		ProfileFormFields profile = new ProfileFormFields(new ProfileReader().read(dir), new SchemaOrgFormFields(terms));
		FormField fileFormat = byName(profile.fieldsFor(DATASET, true)).get("fileFormat");
		assertNotNull(fileFormat);
		assertNotEquals(FieldTier.OTHER, fileFormat.tier());
		// Without the profile, a retired property of the types
		assertFalse(core.fieldsFor(DATASET, true).stream().anyMatch(f -> f.name().equals("fileFormat")));
		assertTrue(core.retiredFieldsFor(DATASET).stream().anyMatch(f -> f.name().equals("fileFormat")));
	}

	@Test
	public void otherTypes() throws Exception {
		assertEquals(List.of(), core.otherTypes());
		try (InputStream in = getClass().getResourceAsStream("/no/s11/wpsld/crate/masp/ldac-profile.json")) {
			Profile ldac = new ProfileReader().read(new CrateReader().read(in, "ldac-profile.json"));
			ProfileFormFields fields = new ProfileFormFields(ldac, new SchemaOrgFormFields(terms));
			assertTrue(fields.otherTypes().toString(),
					fields.otherTypes().contains(URI.create("http://pcdm.org/models#Collection")));
			// The root must be a RepositoryCollection too
			assertTrue(fields.requiredTypes(DATASET, true).contains(URI.create("http://pcdm.org/models#Collection")));
		}
	}
}
