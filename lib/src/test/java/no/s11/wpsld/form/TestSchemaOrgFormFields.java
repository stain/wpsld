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

	@Test
	public void unknownTypesHaveNoFields() {
		assertTrue(new SchemaOrgFormFields(new SchemaFromJsonLd())
				.fieldsFor(Set.of(URI.create("http://example.com/Unknown"))).isEmpty());
	}
}
