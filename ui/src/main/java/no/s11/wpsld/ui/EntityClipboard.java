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
package no.s11.wpsld.ui;

import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

/**
 * An entity on the clipboard, as its JSON-LD three times: as plain text, for
 * text editors such as VSCode or Word; as {@link #ENTITY}, by which wpsld
 * knows it is an entity rather than text; and as {@link #LD_JSON}, a JSON-LD
 * document with the crate's <code>@context</code>, for moving it between
 * crates whose contexts differ, and for other JSON-LD tools.
 * <p>
 * wpsld's own text fields don't take the JSON as text: those of references
 * paste the entity's <code>@id</code>, linking it, and the others nothing
 * (see {@link #guard(TextInputControl, boolean)}).
 */
final class EntityClipboard {
	/** An entity's JSON-LD, as a node of a flattened <code>@graph</code> */
	static final DataFormat ENTITY = Optional.ofNullable(DataFormat.lookupMimeType("application/x-wpsld-entity+json"))
			.orElseGet(() -> new DataFormat("application/x-wpsld-entity+json"));

	/**
	 * A JSON-LD document of the entity: its crate's <code>@context</code>, and
	 * a <code>@graph</code> of the entity alone
	 */
	static final DataFormat LD_JSON = Optional.ofNullable(DataFormat.lookupMimeType("application/ld+json"))
			.orElseGet(() -> new DataFormat("application/ld+json"));

	private EntityClipboard() {
	}

	/**
	 * Put the entity on the clipboard.
	 *
	 * @param context The <code>@context</code> of its crate
	 */
	static void copy(Entity entity, JsonNode context) {
		ObjectNode document = JsonNodeFactory.instance.objectNode();
		document.set("@context", context);
		document.putArray("@graph").add(entity.toJson());
		String json;
		String ldJson;
		try {
			json = Crate.WRITER.writeValueAsString(entity.toJson());
			ldJson = Crate.WRITER.writeValueAsString(document);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Can't write " + entity.getId() + " as JSON", e);
		}
		ClipboardContent content = new ClipboardContent();
		content.putString(json);
		content.put(ENTITY, json);
		content.put(LD_JSON, ldJson);
		Clipboard.getSystemClipboard().setContent(content);
	}

	/**
	 * The entity on the clipboard: one copied by {@link #copy(Entity, JsonNode)},
	 * else from a JSON-LD document of one entity, e.g. from another JSON-LD
	 * tool, else its JSON-LD as text, e.g. copied in a text editor. The
	 * document's <code>@context</code> is not used yet: its terms are taken
	 * as they are.
	 */
	static Optional<Entity> entity() {
		Clipboard clipboard = Clipboard.getSystemClipboard();
		Object json = clipboard.hasContent(ENTITY) ? clipboard.getContent(ENTITY)
				: clipboard.hasContent(LD_JSON) ? clipboard.getContent(LD_JSON) : clipboard.getString();
		if (!(json instanceof String text)) {
			return Optional.empty();
		}
		try {
			JsonNode node = new ObjectMapper().readTree(text);
			// A document of one entity: its node
			JsonNode graph = node == null ? null : node.get("@graph");
			if (graph != null) {
				node = graph.isArray() && graph.size() == 1 ? graph.get(0) : graph.isObject() ? graph : null;
			}
			return node == null ? Optional.empty() : Entity.fromJson(node);
		} catch (JsonProcessingException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	/**
	 * Keep an entity copied by {@link #copy(Entity, JsonNode)} from being
	 * pasted into one of wpsld's own text controls as its JSON: a reference
	 * field pastes its <code>@id</code>, others nothing. Typing, and pasting
	 * other text, are as usual.
	 *
	 * @param reference Whether the control takes the <code>@id</code> of an
	 *                  entity
	 */
	static void guard(TextInputControl control, boolean reference) {
		control.setTextFormatter(new TextFormatter<String>(change -> {
			String added = change.getText();
			// A key typed is never the whole entity; don't ask the clipboard
			if (added.length() < 2 || !Clipboard.getSystemClipboard().hasContent(ENTITY)) {
				return change;
			}
			Object json = Clipboard.getSystemClipboard().getContent(ENTITY);
			// A single line field leaves out the line ends of what is pasted
			if (!(json instanceof String text) || !oneLine(text).equals(oneLine(added))) {
				return change;
			}
			Optional<Entity> entity = reference ? entity() : Optional.empty();
			if (entity.isEmpty()) {
				return null;
			}
			String id = entity.get().getId();
			change.setText(id);
			// After the @id, as after the text pasted
			int end = change.getRangeStart() + id.length();
			change.selectRange(end, end);
			return change;
		}));
	}

	private static String oneLine(String text) {
		return text.replaceAll("[\\r\\n\\t]", "");
	}
}
