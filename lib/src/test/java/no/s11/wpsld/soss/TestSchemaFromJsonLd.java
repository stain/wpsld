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
package no.s11.wpsld.soss;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

public class TestSchemaFromJsonLd {
	private static final String S = "http://schema.org/";
	private static SchemaFromJsonLd schema;

	@BeforeClass
	public static void loadSchema() {
		// Also runs the quality checks
		schema = new SchemaFromJsonLd();
	}

	@Test
	public void pinnedSchemaOrgRelease() throws Exception {
		MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
		try (InputStream in = SchemaFromJsonLd.class.getResourceAsStream(SchemaFromJsonLd.SCHEMAORG_JSONLD)) {
			assertNotNull(in);
			sha256.update(in.readAllBytes());
		}
		assertEquals(SchemaFromJsonLd.SCHEMAORG_JSONLD_SHA256, HexFormat.of().formatHex(sha256.digest()));
	}

	@Test
	public void classes() {
		ClassDef dataset = schema.getClasses().get(URI.create(S + "Dataset"));
		assertNotNull(dataset);
		assertEquals("Dataset", dataset.getName());
		assertTrue(dataset.getComment().isPresent());
		assertEquals(List.of(URI.create(S + "CreativeWork")),
				dataset.getSubClassOf().stream().map(ClassDef::getID).toList());
		// Superseded terms the RO-Crate 1.3 context keeps are included
		assertTrue(schema.getClasses().containsKey(URI.create(S + "DeliveryTimeSettings")));
		assertTrue(schema.rootClasses().anyMatch(c -> c.getID().equals(URI.create(S + "Thing"))));
	}

	@Test
	public void properties() {
		PropertyDef author = schema.getProperties().get(URI.create(S + "author"));
		assertNotNull(author);
		assertEquals("author", author.getName());
		assertTrue(author.getDomainIncludes().stream().anyMatch(c -> c.getID().equals(URI.create(S + "CreativeWork"))));
		assertTrue(author.getRangeIncludes().stream().anyMatch(c -> c.getID().equals(URI.create(S + "Person"))));
		assertTrue(author.getRangeIncludes().stream().anyMatch(c -> c.getID().equals(URI.create(S + "Organization"))));
	}

	@Test
	public void languageTaggedLabels() {
		// One of the few terms with {"@language": "en", "@value": ...} labels
		PropertyDef archiveHeld = schema.getProperties().get(URI.create(S + "archiveHeld"));
		assertNotNull(archiveHeld);
		assertEquals("archiveHeld", archiveHeld.getName());
	}

	private static ClassDef classDef(String name) {
		return schema.getClasses().get(URI.create(S + name));
	}

	private static PropertyDef property(String name) {
		return schema.getProperties().get(URI.create(S + name));
	}

	@Test
	public void noWarningsForPinnedRelease() {
		assertEquals(List.of(), schema.getWarnings());
	}

	@Test
	public void subclassesDirectAndSorted() {
		List<String> subclasses = schema.subclasses(classDef("CreativeWork")).map(ClassDef::getName).toList();
		assertTrue(subclasses.contains("Dataset"));
		assertTrue(subclasses.contains("SoftwareSourceCode"));
		// Not the subclasses of subclasses
		assertFalse(subclasses.contains("DataFeed"));
		assertEquals(subclasses.stream().sorted().toList(), subclasses);
		assertEquals(List.of(classDef("CompleteDataFeed")), schema.subclasses(classDef("DataFeed")).toList());
		assertEquals(List.of(), schema.subclasses(classDef("CompleteDataFeed")).toList());
	}

	@Test
	public void propertiesOfDirectOnly() {
		List<String> properties = schema.propertiesOf(classDef("Dataset")).map(PropertyDef::getName).toList();
		assertTrue(properties.contains("distribution"));
		// name is a property of Thing, not listed for Dataset itself
		assertFalse(properties.contains("name"));
		assertEquals(properties.stream().sorted().toList(), properties);
	}

	@Test
	public void superseded() {
		assertTrue(schema.isSuperseded(property("fileFormat")));
		assertFalse(schema.isSuperseded(property("encodingFormat")));
		assertTrue(schema.isSuperseded(classDef("DeliveryTimeSettings")));
		assertFalse(schema.isSuperseded(classDef("Dataset")));
	}

	@Test
	public void dataTypes() {
		assertTrue(schema.isDataType(classDef("Text")));
		// URL is a subclass of Text
		assertTrue(schema.isDataType(classDef("URL")));
		assertTrue(schema.isDataType(classDef("Date")));
		assertTrue(schema.isDataType(classDef("Boolean")));
		assertTrue(schema.isDataType(classDef("Integer")));
		assertFalse(schema.isDataType(classDef("Person")));
		assertFalse(schema.isDataType(classDef("Thing")));
	}

	@Test
	public void subPropertyOf() {
		// A table of contents entry is a part
		assertEquals(List.of(property("hasPart")), property("tocEntry").getSubPropertyOf());
		assertEquals(List.of(), property("hasPart").getSubPropertyOf());
	}

	/**
	 * Loading builds everything up front: the JSON is dropped and no lazy
	 * value is left to compute later, when an exception could be lost.
	 */
	@Test
	public void fullyLinkedAfterLoading() throws Exception {
		Field jsonld = SchemaFromJsonLd.class.getDeclaredField("jsonld");
		jsonld.setAccessible(true);
		assertNull("JSON should be dropped after loading", jsonld.get(schema));
		for (ClassDef classDef : schema.getClasses().values()) {
			assertComputed(classDef, ClassDef.class, "subClassOf");
			assertComputed(classDef, ClassDef.class, "selfAndSuperClasses");
		}
		for (PropertyDef propertyDef : schema.getProperties().values()) {
			assertComputed(propertyDef, PropertyDef.class, "domainIncludes");
			assertComputed(propertyDef, PropertyDef.class, "rangeIncludes");
			assertComputed(propertyDef, PropertyDef.class, "subPropertyOf");
		}
	}

	private static void assertComputed(Object term, Class<?> type, String fieldName) throws Exception {
		Field field = type.getDeclaredField(fieldName);
		field.setAccessible(true);
		Object lazy = field.get(term);
		Field supplier = lazy.getClass().getDeclaredField("supplier");
		supplier.setAccessible(true);
		assertNull(fieldName + " of " + term + " not computed while loading", supplier.get(lazy));
	}

	private static List<String> names(List<? extends DefinedTerm> terms) {
		return terms.stream().map(DefinedTerm::getName).toList();
	}

	private static void assertProperty(String name, List<String> domains, List<String> ranges) {
		PropertyDef property = property(name);
		assertNotNull(name, property);
		assertEquals(URI.create(S + name), property.getID());
		assertEquals(name, property.getName());
		// Sorted by name
		assertEquals(name + " domainIncludes", domains, names(property.getDomainIncludes()));
		assertEquals(name + " rangeIncludes", ranges, names(property.getRangeIncludes()));
		for (String domain : domains) {
			assertTrue(name + " listed for " + domain, schema.propertiesOf(classDef(domain)).anyMatch(property::equals));
		}
	}

	/**
	 * The usual suspects of RO-Crates come out of loading as defined in
	 * schema.org 30.0 (descriptions not compared).
	 */
	@Test
	public void usualSuspects() {
		for (String type : List.of("CreativeWork", "Person", "Organization")) {
			ClassDef classDef = classDef(type);
			assertNotNull(type, classDef);
			assertEquals(type, classDef.getName());
			assertTrue(classDef.getComment().isPresent());
			assertEquals(List.of(classDef("Thing")), classDef.getSubClassOf());
			assertFalse(schema.isSuperseded(classDef));
			assertFalse(schema.isDataType(classDef));
		}
		assertEquals(List.of(classDef("CreativeWork")), classDef("Dataset").getSubClassOf());
		assertEquals(List.of(classDef("Dataset"), classDef("CreativeWork"), classDef("Thing")),
				List.copyOf(classDef("Dataset").getSelfAndSuperClasses()));

		assertProperty("affiliation", List.of("Person"), List.of("Organization"));
		assertProperty("author", List.of("CreativeWork", "Rating"), List.of("Organization", "Person"));
		assertProperty("name", List.of("Thing"), List.of("Text"));
		assertProperty("license", List.of("CreativeWork"), List.of("CreativeWork", "URL"));
		assertProperty("datePublished", List.of("Certification", "CreativeWork"), List.of("Date", "DateTime"));
		assertProperty("hasPart", List.of("CreativeWork"), List.of("CreativeWork"));
		assertProperty("email", List.of("ContactPoint", "Organization", "Person"), List.of("Text"));
		assertProperty("givenName", List.of("Person"), List.of("Text"));
	}
}
