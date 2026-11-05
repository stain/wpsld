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

import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class TestCrateChecks {
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	@Test
	public void newCrateMissesRequiredProperties() {
		List<String> problems = CrateChecks.check(new Crate());
		assertEquals(4, problems.size());
		assertTrue(problems.stream().anyMatch(p -> p.contains("license")));
		assertTrue(problems.stream().anyMatch(p -> p.contains("datePublished")));
	}

	@Test
	public void completeRoot() {
		Crate crate = new Crate();
		Entity root = crate.getRoot();
		root.setAll("name", List.of(JSON.textNode("My crate")));
		root.setAll("description", List.of(JSON.textNode("Some data")));
		root.setAll("license", List.of(JSON.objectNode().put("@id", "http://spdx.org/licenses/CC0-1.0")));
		root.setAll("datePublished", List.of(JSON.textNode("2026-11-05")));
		assertEquals(List.of(), CrateChecks.check(crate));
		root.setAll("datePublished", List.of(JSON.textNode("2026-11-05T13:00:00Z")));
		assertEquals(List.of(), CrateChecks.check(crate));
		root.setAll("datePublished", List.of(JSON.textNode("5 November")));
		assertEquals(1, CrateChecks.check(crate).size());
		root.setTypes(List.of("Thing"));
		assertTrue(CrateChecks.check(crate).get(0).contains("Dataset"));
	}

	@Test
	public void danglingReferences() {
		Crate crate = new Crate();
		Entity download = crate.add("DataDownload");
		crate.getRoot().setAll("distribution", List.of(JSON.objectNode().put("@id", download.getId()),
				JSON.objectNode().put("@id", "dat")));
		crate.getRoot().setAll("license", List.of(JSON.objectNode().put("@id", "http://spdx.org/licenses/CC0-1.0")));
		List<String> dangling = CrateChecks.check(crate).stream().filter(p -> p.contains("not an entity")).toList();
		assertEquals(List.of("./ has distribution dat, which is not an entity in the crate"), dangling);
	}

	private static Crate completeCrate() {
		Crate crate = new Crate();
		Entity root = crate.getRoot();
		root.setAll("name", List.of(JSON.textNode("My crate")));
		root.setAll("description", List.of(JSON.textNode("Some data")));
		root.setAll("license", List.of(JSON.objectNode().put("@id", "http://spdx.org/licenses/CC0-1.0")));
		root.setAll("datePublished", List.of(JSON.textNode("2026-11-05")));
		return crate;
	}

	@Test
	public void singleDatePublished() {
		Crate crate = completeCrate();
		crate.getRoot().setAll("datePublished", List.of(JSON.textNode("2026-11-05"), JSON.textNode("2026-11-06")));
		assertEquals(List.of("The root data entity must have a single datePublished"), CrateChecks.check(crate));
	}

	@Test
	public void absoluteReferencesAreFine() {
		Crate crate = completeCrate();
		crate.getRoot().setAll("author", List.of(JSON.objectNode().put("@id", "https://orcid.org/0000-0002-1825-0097")));
		assertEquals(List.of(), CrateChecks.check(crate));
	}

	@Test
	public void rootMayHaveMoreTypes() {
		Crate crate = completeCrate();
		crate.getRoot().setTypes(List.of("Dataset", "Profile"));
		assertEquals(List.of(), CrateChecks.check(crate));
	}
}
