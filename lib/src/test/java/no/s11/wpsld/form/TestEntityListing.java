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

import java.util.List;
import java.util.Optional;

import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.EntityListing.TypeCount;
import no.s11.wpsld.soss.SchemaFromJsonLd;

public class TestEntityListing {
	private static EntityListing listing;

	@BeforeClass
	public static void loadSchema() {
		listing = new EntityListing(new CrateTerms(new SchemaFromJsonLd()));
	}

	private static Entity add(Crate crate, String id, String name, String... types) {
		Entity entity = crate.add(types[0]);
		crate.rename(entity, id);
		entity.setTypes(List.of(types));
		if (name != null) {
			entity.set("name", Optional.of(TextNode.valueOf(name)));
		}
		return entity;
	}

	private static Crate crate() {
		Crate crate = new Crate();
		add(crate, "#zoe", "Zoë", "Person");
		add(crate, "#bob", "bob", "Person");
		add(crate, "#alice", "Alice", "Person");
		add(crate, "data.csv", null, "File");
		add(crate, "results/", "Results", "Dataset");
		add(crate, "#oslo", "Oslo", "Place");
		add(crate, "script.py", "Script", "File", "SoftwareSourceCode");
		add(crate, "#bob-2", "Bob", "Person");
		return crate;
	}

	private static List<String> ids(List<Entity> entities) {
		return entities.stream().map(Entity::getId).toList();
	}

	/** Root first, then by type, name (ignoring case and accents) and @id */
	@Test
	public void order() {
		Crate crate = crate();
		assertEquals(List.of("./", "results/", "data.csv", "script.py", "#alice", "#bob", "#bob-2", "#zoe", "#oslo"),
				ids(listing.list(crate, "")));
	}

	/** Only entities of a type, or of its subtypes */
	@Test
	public void ofType() {
		Crate crate = crate();
		assertEquals(List.of("#alice", "#bob", "#bob-2", "#zoe"), ids(listing.list(crate, "Person")));
		assertEquals(List.of("data.csv", "script.py"), ids(listing.list(crate, "File")));
		assertEquals(List.of("script.py"), ids(listing.list(crate, "SoftwareSourceCode")));
		// The root is a Dataset, so a CreativeWork, and still first
		assertEquals(List.of("./", "results/", "data.csv", "script.py"), ids(listing.list(crate, "CreativeWork")));
		assertEquals(List.of(), ids(listing.list(crate, "Organization")));
	}

	/**
	 * The types entities have, and those several of them are kinds of, but
	 * not Thing
	 */
	@Test
	public void types() {
		Crate crate = crate();
		assertEquals(List.of(new TypeCount("CreativeWork", 4), new TypeCount("Dataset", 2), new TypeCount("File", 2),
				new TypeCount("Person", 4), new TypeCount("Place", 1), new TypeCount("SoftwareSourceCode", 1)),
				listing.types(crate.getEntities()));
		assertEquals("Person (4)", new TypeCount("Person", 4).toString());
	}

	/** Not the types only one type is a kind of */
	@Test
	public void noLoneSupertypes() {
		Crate crate = new Crate();
		add(crate, "#oslo", "Oslo", "Place");
		add(crate, "#point", null, "GeoCoordinates");
		assertEquals(List.of(new TypeCount("Dataset", 1), new TypeCount("GeoCoordinates", 1),
				new TypeCount("Place", 1)), listing.types(crate.getEntities()));
	}
}
