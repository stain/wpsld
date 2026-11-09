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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Undo and redo of a crate's edits as snapshots of its JSON-LD
 * ({@link Crate#toJson()}) at undo points, e.g. after leaving a field or
 * adding an entity, each with the {@link Place} of the change, to show again
 * on undo and redo. A point without changes since the last one is not a new
 * point. Snapshots are kept as JSON strings, which can't change with the
 * crate's entities; at most {@link #MAX_POINTS}, or {@link #MAX_SIZE}
 * characters in all, dropping the oldest.
 * <p>
 * The history is for one crate: start again with {@link #reset} for a new or
 * opened one.
 */
public class UndoHistory {
	public static final int MAX_POINTS = 200;
	public static final long MAX_SIZE = 64 * 1024 * 1024;
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Where a change was made, to show it again.
	 *
	 * @param entity   The <code>@id</code> of the entity edited, if any
	 * @param property The property edited, if known
	 */
	public record Place(Optional<String> entity, Optional<String> property) {
		public static final Place NONE = new Place(Optional.empty(), Optional.empty());
	}

	/**
	 * The crate at an undo point, and where the change undone or redone was.
	 */
	public record Step(ObjectNode crate, Place place) {
	}

	/** A snapshot, where the change leading to it was made, and its name (see {@link ChangeNames}) */
	private record Point(String json, Place place, String name) {
	}

	private final Deque<Point> undo = new ArrayDeque<>();
	private final Deque<Point> redo = new ArrayDeque<>();
	/** The crate at the last undo point */
	private Point current;

	public UndoHistory(ObjectNode crate) {
		reset(crate);
	}

	/**
	 * Start again from this crate, forgetting the history.
	 */
	public void reset(ObjectNode crate) {
		undo.clear();
		redo.clear();
		current = new Point(json(crate), Place.NONE, "");
	}

	/**
	 * Mark an undo point, if the crate changed since the last one; then
	 * nothing is left to redo.
	 *
	 * @param place Where the change was made
	 * @return Whether it was a new point
	 */
	public boolean checkpoint(ObjectNode crate, Place place) {
		return checkpoint(crate, place, Optional.empty());
	}

	/**
	 * Mark an undo point, as {@link #checkpoint(ObjectNode, Place)}, with a
	 * name for the change when its difference doesn't tell, e.g. "Merge
	 * Entities"; otherwise named by {@link ChangeNames#describe}.
	 */
	public boolean checkpoint(ObjectNode crate, Place place, Optional<String> name) {
		String now = json(crate);
		if (now.equals(current.json())) {
			return false;
		}
		undo.push(current);
		current = new Point(now, place, name.orElseGet(() -> ChangeNames.describe(tree(undo.peek().json()), crate)));
		redo.clear();
		trim();
		return true;
	}

	/**
	 * Go back to the previous undo point. Mark one first, so that changes
	 * since the last point are what is undone.
	 *
	 * @return The crate there, with where the change undone was made
	 */
	public Optional<Step> undo() {
		if (undo.isEmpty()) {
			return Optional.empty();
		}
		Place undone = current.place();
		redo.push(current);
		current = undo.pop();
		return Optional.of(new Step(tree(current.json()), undone));
	}

	/**
	 * Go forward to the undo point last undone.
	 *
	 * @return The crate there, with where the change redone was made
	 */
	public Optional<Step> redo() {
		if (redo.isEmpty()) {
			return Optional.empty();
		}
		undo.push(current);
		current = redo.pop();
		return Optional.of(new Step(tree(current.json()), current.place()));
	}

	/**
	 * The crate as restored from the point gone to by {@link #undo} or
	 * {@link #redo}, in case reading it in changed how it is written, so
	 * that it doesn't count as a change.
	 */
	public void settle(ObjectNode crate) {
		current = new Point(json(crate), current.place(), current.name());
	}

	/**
	 * The name of what Undo would undo, e.g. "Add Person": the change since
	 * the last undo point, if this crate differs from it, else the change
	 * that led to that point.
	 */
	public Optional<String> undoName(ObjectNode crate) {
		String now = json(crate);
		if (!now.equals(current.json())) {
			return Optional.of(ChangeNames.describe(tree(current.json()), crate));
		}
		return undo.isEmpty() ? Optional.empty() : Optional.of(current.name());
	}

	/**
	 * The name of what Redo would redo.
	 */
	public Optional<String> redoName() {
		return Optional.ofNullable(redo.peek()).map(Point::name);
	}

	public boolean canUndo() {
		return !undo.isEmpty();
	}

	public boolean canRedo() {
		return !redo.isEmpty();
	}

	private void trim() {
		long size = current.json().length() + undo.stream().mapToLong(p -> p.json().length()).sum();
		while (!undo.isEmpty() && (undo.size() > MAX_POINTS || size > MAX_SIZE)) {
			size -= undo.removeLast().json().length();
		}
	}

	/**
	 * The entity that differs between two states of a crate, to show after
	 * undo or redo if the place of the change is unknown: the first in
	 * <code>after</code> that is new or changed, except the metadata
	 * descriptor; empty if only entities were removed.
	 */
	public static Optional<String> changedEntity(JsonNode before, JsonNode after) {
		Map<String, JsonNode> earlier = entities(before);
		return entities(after).entrySet().stream().skip(1)
				.filter(e -> !Objects.equals(earlier.get(e.getKey()), e.getValue())).map(Map.Entry::getKey)
				.findFirst();
	}

	/** Entities by @id, the metadata descriptor first as Crate writes it */
	private static Map<String, JsonNode> entities(JsonNode crate) {
		Map<String, JsonNode> entities = new LinkedHashMap<>();
		crate.path("@graph").forEach(e -> entities.put(e.path("@id").asText(), e));
		return entities;
	}

	private static String json(ObjectNode crate) {
		try {
			return MAPPER.writeValueAsString(crate);
		} catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static ObjectNode tree(String json) {
		try {
			return (ObjectNode) MAPPER.readTree(json);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
