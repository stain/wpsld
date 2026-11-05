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
}
