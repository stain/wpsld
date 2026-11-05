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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.Test;

import no.s11.wpsld.soss.SchemaFromJsonLd;

public class TestSchemaOrgFormFields {
	private static final String S = "http://schema.org/";
	private static List<FormField> datasetFields;
	private static Map<String, FormField> byName;

	@BeforeClass
	public static void datasetFields() {
		FormFields formFields = new SchemaOrgFormFields(new SchemaFromJsonLd());
		datasetFields = formFields.fieldsFor(Set.of(URI.create(S + "Dataset")));
		byName = datasetFields.stream().collect(Collectors.toMap(FormField::name, Function.identity()));
	}

	@Test
	public void groupedMostSpecificFirst() {
		assertEquals("Dataset", datasetFields.get(0).group());
		assertEquals("Thing", datasetFields.get(datasetFields.size() - 1).group());
		assertEquals("Dataset", byName.get("distribution").group());
		assertEquals("CreativeWork", byName.get("author").group());
		assertEquals("Thing", byName.get("name").group());
		// Each property only once
		assertEquals(datasetFields.size(), byName.size());
	}

	@Test
	public void valueKinds() {
		assertEquals(ValueKind.TEXT, byName.get("name").kind());
		assertEquals(ValueKind.REFERENCE, byName.get("author").kind());
		assertEquals(ValueKind.DATE, byName.get("datePublished").kind());
		assertEquals(ValueKind.BOOLEAN, byName.get("isAccessibleForFree").kind());
		// CreativeWork or URL
		assertEquals(ValueKind.TEXT_OR_REFERENCE, byName.get("license").kind());
	}

	@Test
	public void supersededLeftOut() {
		// schema:fileFormat is superseded by schema:encodingFormat
		assertFalse(byName.containsKey("fileFormat"));
		assertTrue(byName.containsKey("encodingFormat"));
	}

	/**
	 * Types outside schema.org are taken as Things.
	 */
	@Test
	public void unknownTypesAreThings() {
		List<FormField> fields = new SchemaOrgFormFields(new SchemaFromJsonLd())
				.fieldsFor(Set.of(URI.create("https://w3id.org/ldac/terms#RepositoryObject")));
		assertTrue(fields.stream().allMatch(f -> f.group().equals("Thing")));
		assertTrue(fields.stream().anyMatch(f -> f.name().equals("name")));
		assertTrue(fields.stream().anyMatch(f -> f.name().equals("description")));
	}

	@Test
	public void severalTypesShareGroups() {
		List<FormField> fields = new SchemaOrgFormFields(new SchemaFromJsonLd())
				.fieldsFor(Set.of(URI.create(S + "Dataset"), URI.create(S + "SoftwareSourceCode")));
		List<String> names = fields.stream().map(FormField::name).toList();
		// Each property once, though both types are CreativeWorks
		assertEquals(names.size(), Set.copyOf(names).size());
		assertTrue(names.contains("distribution"));
		assertTrue(names.contains("programmingLanguage"));
		List<String> groups = fields.stream().map(FormField::group).distinct().toList();
		assertEquals(1, groups.stream().filter("CreativeWork"::equals).count());
		// The shared superclasses come after both types
		assertTrue(groups.indexOf("CreativeWork") > groups.indexOf("Dataset"));
		assertTrue(groups.indexOf("CreativeWork") > groups.indexOf("SoftwareSourceCode"));
		assertEquals("Thing", groups.get(groups.size() - 1));
	}

	@Test
	public void numberKind() {
		FormField pages = new SchemaOrgFormFields(new SchemaFromJsonLd())
				.fieldsFor(Set.of(URI.create(S + "Book"))).stream().filter(f -> f.name().equals("numberOfPages"))
				.findFirst().orElseThrow();
		assertEquals(ValueKind.NUMBER, pages.kind());
	}

	@Test
	public void schemaOrgHasNoCardinality() {
		// 0..*: nothing required, no maximum
		for (FormField field : datasetFields) {
			assertEquals(0, field.minCount());
			assertFalse(field.isRequired());
			assertTrue(field.maxCount().isEmpty());
		}
	}

	@Test
	public void supersededTypesLeftOutOfRanges() {
		SchemaFromJsonLd schema = new SchemaFromJsonLd();
		for (FormField field : datasetFields) {
			assertTrue(field.name(), field.range().stream().noneMatch(schema::isSuperseded));
		}
	}

	@Test
	public void personFields() {
		Map<String, FormField> person = new SchemaOrgFormFields(new SchemaFromJsonLd())
				.fieldsFor(Set.of(URI.create(S + "Person"))).stream()
				.collect(Collectors.toMap(FormField::name, Function.identity()));
		assertEquals("Person", person.get("affiliation").group());
		assertEquals(ValueKind.REFERENCE, person.get("affiliation").kind());
		assertEquals(ValueKind.TEXT, person.get("givenName").kind());
		assertEquals(ValueKind.TEXT, person.get("email").kind());
		assertEquals(ValueKind.DATE, person.get("birthDate").kind());
		assertEquals("Thing", person.get("name").group());
		// Properties of CreativeWork are not offered for a Person
		assertFalse(person.containsKey("author"));
		assertFalse(person.containsKey("license"));
	}
}
