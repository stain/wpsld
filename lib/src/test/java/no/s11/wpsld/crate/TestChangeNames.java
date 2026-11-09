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

import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import no.s11.wpsld.crate.UndoHistory.Place;

/**
 * Names of changes for Undo and Redo, from the difference between two
 * states of a crate.
 */
public class TestChangeNames {
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String DESCRIPTOR = """
			{"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
			 "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}}""";

	/** A crate of the descriptor, the root and these entities, as JSON */
	private static ObjectNode crate(String root, String... entities) throws Exception {
		return (ObjectNode) MAPPER.readTree("{\"@context\": \"https://w3id.org/ro/crate/1.3/context\", \"@graph\": ["
				+ DESCRIPTOR + "," + root + (entities.length == 0 ? "" : "," + String.join(",", entities)) + "]}");
	}

	private static final String ROOT = "{\"@id\": \"./\", \"@type\": \"Dataset\"}";
	private static final String ALICE = "{\"@id\": \"#alice\", \"@type\": \"Person\", \"name\": \"Alice\"}";

	@Test
	public void addAndRemove() throws Exception {
		assertEquals("Add Person", ChangeNames.describe(crate(ROOT), crate(ROOT, ALICE)));
		assertEquals("Remove Person", ChangeNames.describe(crate(ROOT, ALICE), crate(ROOT)));
		// Created from a field, which then refers to it: still adding it
		assertEquals("Add Person", ChangeNames.describe(crate(ROOT),
				crate("{\"@id\": \"./\", \"@type\": \"Dataset\", \"author\": {\"@id\": \"#alice\"}}", ALICE)));
		// Prefixed and several types: the first, as a term
		assertEquals("Add RepositoryObject", ChangeNames.describe(crate(ROOT),
				crate(ROOT, "{\"@id\": \"#o\", \"@type\": [\"pcdm:RepositoryObject\", \"Dataset\"]}")));
	}

	@Test
	public void edits() throws Exception {
		assertEquals("Edit “Name”", ChangeNames.describe(crate(ROOT, ALICE),
				crate(ROOT, ALICE.replace("Alice", "Alice B."))));
		assertEquals("Edit “Date published”", ChangeNames.describe(crate(ROOT),
				crate("{\"@id\": \"./\", \"@type\": \"Dataset\", \"datePublished\": \"2026-11-09\"}")));
		assertEquals("Change Type", ChangeNames.describe(crate(ROOT, ALICE),
				crate(ROOT, ALICE.replace("\"Person\"", "\"Patient\""))));
		assertEquals("Edit Person", ChangeNames.describe(crate(ROOT, ALICE),
				crate(ROOT, "{\"@id\": \"#alice\", \"@type\": \"Person\", \"name\": \"A\", \"email\": \"a@b\"}")));
		assertEquals("Change @id", ChangeNames.describe(crate(ROOT, ALICE),
				crate(ROOT, ALICE.replace("#alice", "#a"))));
		assertEquals(ChangeNames.CHANGES, ChangeNames.describe(crate(ROOT, ALICE),
				crate("{\"@id\": \"./\", \"@type\": \"Dataset\", \"name\": \"x\"}", ALICE.replace("Alice", "B"))));
	}

	@Test
	public void profile() throws Exception {
		assertEquals(ChangeNames.CHOOSE_PROFILE, ChangeNames.describe(crate(ROOT),
				crate("{\"@id\": \"./\", \"@type\": \"Dataset\", \"conformsTo\": {\"@id\": \"https://example.com/p\"}}")));
		// An entity that isn't the root: just an edit
		assertEquals("Edit “Conforms to”", ChangeNames.describe(crate(ROOT, ALICE), crate(ROOT,
				"{\"@id\": \"#alice\", \"@type\": \"Person\", \"name\": \"Alice\", \"conformsTo\": {\"@id\": \"x\"}}")));
	}

	/** A CreativeWork about something is not the metadata descriptor */
	@Test
	public void aboutIsNotDescriptor() throws Exception {
		String work = "{\"@id\": \"#w\", \"@type\": \"CreativeWork\", \"about\": {\"@id\": \"./\"}}";
		assertEquals("Add CreativeWork", ChangeNames.describe(crate(ROOT), crate(ROOT, work)));
	}

	/** The history names its points: what Undo and Redo would do */
	@Test
	public void historyNames() throws Exception {
		UndoHistory history = new UndoHistory(crate(ROOT));
		assertEquals(Optional.empty(), history.undoName(crate(ROOT)));
		// Not yet a point: named from the difference
		assertEquals(Optional.of("Add Person"), history.undoName(crate(ROOT, ALICE)));
		history.checkpoint(crate(ROOT, ALICE), Place.NONE);
		assertEquals(Optional.of("Add Person"), history.undoName(crate(ROOT, ALICE)));
		history.checkpoint(crate(ROOT), Place.NONE, Optional.of("Merge Entities"));
		assertEquals(Optional.of("Merge Entities"), history.undoName(crate(ROOT)));
		assertEquals(Optional.empty(), history.redoName());
		history.undo();
		assertEquals(Optional.of("Merge Entities"), history.redoName());
		assertEquals(Optional.of("Add Person"), history.undoName(crate(ROOT, ALICE)));
	}
}
