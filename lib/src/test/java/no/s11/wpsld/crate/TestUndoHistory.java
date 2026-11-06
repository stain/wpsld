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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.crate.UndoHistory.Place;

public class TestUndoHistory {

	private static String name(ObjectNode crate) {
		return crate.at("/@graph/1/name").asText("");
	}

	@Test
	public void undoAndRedo() {
		Crate crate = new Crate();
		UndoHistory history = new UndoHistory(crate.toJson());
		assertFalse(history.canUndo());
		assertEquals(Optional.empty(), history.undo());

		crate.getRoot().set("name", Optional.of(TextNode.valueOf("A")));
		assertTrue(history.checkpoint(crate.toJson(), Place.NONE));
		// No changes since: no new point
		assertFalse(history.checkpoint(crate.toJson(), Place.NONE));
		crate.getRoot().set("name", Optional.of(TextNode.valueOf("B")));
		assertTrue(history.checkpoint(crate.toJson(), Place.NONE));

		assertEquals("A", name(history.undo().orElseThrow().crate()));
		assertEquals("", name(history.undo().orElseThrow().crate()));
		assertFalse(history.canUndo());
		assertEquals("A", name(history.redo().orElseThrow().crate()));
		assertEquals("B", name(history.redo().orElseThrow().crate()));
		assertFalse(history.canRedo());

		// A change after undo leaves nothing to redo
		history.undo();
		crate.getRoot().set("name", Optional.of(TextNode.valueOf("C")));
		assertTrue(history.checkpoint(crate.toJson(), Place.NONE));
		assertFalse(history.canRedo());
		assertEquals("A", name(history.undo().orElseThrow().crate()));

		history.reset(crate.toJson());
		assertFalse(history.canUndo());
		assertFalse(history.canRedo());
	}

	/**
	 * Crate.toJson() shares the entities' value nodes, which may change in
	 * place; the snapshots don't.
	 */
	@Test
	public void snapshotsDontChangeWithTheCrate() {
		Crate crate = new Crate();
		ObjectNode author = JsonNodeFactory.instance.objectNode().put("@id", "#a");
		crate.getRoot().set("author", Optional.of(author));
		UndoHistory history = new UndoHistory(crate.toJson());
		author.put("@id", "#b");
		assertTrue(history.checkpoint(crate.toJson(), Place.NONE));
		assertEquals("#a", history.undo().orElseThrow().crate().at("/@graph/1/author/@id").asText());
	}

	@Test
	public void oldestDropped() {
		Crate crate = new Crate();
		UndoHistory history = new UndoHistory(crate.toJson());
		for (int i = 0; i < UndoHistory.MAX_POINTS + 10; i++) {
			crate.getRoot().set("name", Optional.of(TextNode.valueOf("v" + i)));
			history.checkpoint(crate.toJson(), Place.NONE);
		}
		int undone = 0;
		while (history.undo().isPresent()) {
			undone++;
		}
		assertEquals(UndoHistory.MAX_POINTS, undone);
	}

	@Test
	public void changedEntity() {
		Crate crate = new Crate();
		ObjectNode before = crate.toJson();
		Entity person = crate.add("Person");
		ObjectNode added = crate.toJson();
		assertEquals(Optional.of(person.getId()), UndoHistory.changedEntity(before, added));
		// Only removed: none to show
		assertEquals(Optional.empty(), UndoHistory.changedEntity(added, before));
		crate.getRoot().set("name", Optional.of(TextNode.valueOf("Root")));
		assertEquals(Optional.of("./"), UndoHistory.changedEntity(added, crate.toJson()));
	}

	/**
	 * Undo shows where the change undone was made; redo where the change
	 * redone was.
	 */
	@Test
	public void places() {
		Crate crate = new Crate();
		UndoHistory history = new UndoHistory(crate.toJson());
		crate.getRoot().set("name", Optional.of(TextNode.valueOf("A")));
		Place name = new Place(Optional.of("./"), Optional.of("name"));
		history.checkpoint(crate.toJson(), name);
		Entity person = crate.add("Person");
		Place added = new Place(Optional.of(person.getId()), Optional.empty());
		history.checkpoint(crate.toJson(), added);

		assertEquals(added, history.undo().orElseThrow().place());
		assertEquals(name, history.undo().orElseThrow().place());
		assertEquals(name, history.redo().orElseThrow().place());
		assertEquals(added, history.redo().orElseThrow().place());
	}
}
