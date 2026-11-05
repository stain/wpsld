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

import org.junit.BeforeClass;
import org.junit.Test;

import no.s11.wpsld.soss.ClassDef;
import no.s11.wpsld.soss.SchemaFromJsonLd;

public class TestEntityTypes {
	private static SchemaFromJsonLd schema;
	private static EntityTypes types;

	@BeforeClass
	public static void loadSchema() {
		schema = new SchemaFromJsonLd();
		types = new EntityTypes(schema);
	}

	private static ClassDef type(String name) {
		return schema.getClasses().get(URI.create("http://schema.org/" + name));
	}

	@Test
	public void subclassReplacesSuperclass() {
		assertEquals(List.of("Person"), types.add(List.of("Thing"), type("Person")));
		assertEquals(List.of("Dataset"), types.add(List.of("CreativeWork"), type("Dataset")));
	}

	@Test
	public void unrelatedTypesKept() {
		assertEquals(List.of("Dataset", "SoftwareSourceCode"),
				types.add(List.of("Dataset"), type("SoftwareSourceCode")));
	}

	@Test
	public void addingExistingTypeChangesNothing() {
		assertEquals(List.of("Dataset"), types.add(List.of("Dataset"), type("Dataset")));
	}

	@Test
	public void superclassIsImplied() {
		List<String> current = List.of("Dataset", "SoftwareSourceCode", "Person");
		assertEquals(List.of("Dataset", "SoftwareSourceCode"), types.implying(current, type("CreativeWork")));
		assertEquals(List.of(), types.implying(List.of("Person"), type("CreativeWork")));
		// Add anyway keeps the more specific types
		assertEquals(List.of("Dataset", "SoftwareSourceCode", "Person", "CreativeWork"),
				types.add(current, type("CreativeWork")));
		// Replace swaps all the implying types for the superclass
		assertEquals(List.of("Person", "CreativeWork"), types.replaceImplying(current, type("CreativeWork")));
	}

	@Test
	public void isA() {
		assertTrue(types.isA(List.of("Place"), type("Place")));
		// Subclass of Place
		assertTrue(types.isA(List.of("Country"), type("Place")));
		assertTrue(types.isA(List.of("Person", "Country"), type("Place")));
		assertFalse(types.isA(List.of("Person"), type("Place")));
		// A Thing is not necessarily a Place
		assertFalse(types.isA(List.of("Thing"), type("Place")));
		assertFalse(types.isA(List.of("ldac:Speaker"), type("Place")));
	}

	@Test
	public void simplify() {
		assertEquals(List.of("DataDownload"), types.simplify(List.of("DataDownload", "Thing")));
		assertEquals(List.of("Dataset", "SoftwareSourceCode"),
				types.simplify(List.of("CreativeWork", "Dataset", "SoftwareSourceCode")));
		// Types unknown to schema.org are kept
		assertEquals(List.of("Person", "ldac:Speaker"), types.simplify(List.of("Person", "Thing", "ldac:Speaker")));
	}

	@Test
	public void removingLastTypeFallsBackToThing() {
		assertEquals(List.of("Dataset"), types.remove(List.of("Dataset", "Person"), "Person"));
		assertEquals(List.of("Thing"), types.remove(List.of("Dataset"), "Dataset"));
		assertTrue(types.canRemove(List.of("Dataset"), "Dataset"));
		assertFalse(types.canRemove(List.of("Thing"), "Thing"));
		assertTrue(types.canRemove(List.of("Thing", "Person"), "Thing"));
	}
}
