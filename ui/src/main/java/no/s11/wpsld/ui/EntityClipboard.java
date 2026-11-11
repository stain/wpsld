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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * Entities on the clipboard, as JSON-LD three times: as plain text, for text
 * editors such as VSCode or Word (an entity's object, or several separated by
 * commas, as in a <code>@graph</code>); as {@link #LD_JSON}, a JSON-LD
 * document with the crate's <code>@context</code>, for moving them between
 * crates whose contexts differ, and for other JSON-LD tools; and as
 * {@link #ENTITY}, by which wpsld knows they are entities rather than text,
 * and which crate they came from.
 * <p>
 * wpsld's own text fields don't take the JSON as text: those of references
 * paste a single entity's <code>@id</code>, linking it, and the others
 * nothing (see {@link #guard(TextInputControl, boolean)}).
 */
final class EntityClipboard {
	/**
	 * wpsld's own: <code>{"version": 1, "crate": base, "referenced":
	 * [entities], "document": {…}}</code>, the {@link #LD_JSON} document,
	 * with the entities the copied ones refer to (one level, not copied
	 * themselves) and the <code>arcp://uuid,…/</code> base of the crate
	 * copied from. The version is of this JSON, see {@link #VERSION}.
	 */
	static final DataFormat ENTITY = Optional
			.ofNullable(DataFormat.lookupMimeType("application/vnd.s11.wpsld.entities+json"))
			.orElseGet(() -> new DataFormat("application/vnd.s11.wpsld.entities+json"));

	/**
	 * The version of {@link #ENTITY}'s JSON: raised when a change would
	 * mislead an older wpsld, which then pastes from {@link #LD_JSON}
	 * instead; not for added keys, which older ones leave out.
	 */
	static final int VERSION = 1;

	/**
	 * A JSON-LD document of the entities: its crate's <code>@context</code>,
	 * with the crate's <code>arcp://uuid,…/</code> base as <code>@base</code>
	 * (so that <code>#uom</code> of one crate expands to another IRI than
	 * <code>#uom</code> of another), and a <code>@graph</code> of the copied
	 * entities only
	 */
	static final DataFormat LD_JSON = Optional.ofNullable(DataFormat.lookupMimeType("application/ld+json"))
			.orElseGet(() -> new DataFormat("application/ld+json"));

	/**
	 * Entities from the clipboard.
	 *
	 * @param entities   Those copied
	 * @param referenced Those they refer to, not copied, by <code>@id</code>,
	 *                   as they were when copied (only from wpsld)
	 * @param crate      The base of the crate they came from, if known
	 */
	record Clip(List<Entity> entities, Map<String, Entity> referenced, Optional<String> crate) {
	}

	private EntityClipboard() {
	}

	/**
	 * Put entities on the clipboard.
	 *
	 * @param referenced The entities they refer to that are not copied
	 * @param context    The <code>@context</code> of their crate
	 * @param base       Their crate's <code>arcp://uuid,…/</code> base
	 */
	static void copy(List<Entity> entities, List<Entity> referenced, JsonNode context, String base) {
		ObjectNode document = JsonNodeFactory.instance.objectNode();
		ArrayNode contexts = document.putArray("@context");
		if (context.isArray()) {
			contexts.addAll(((ArrayNode) context).deepCopy());
		} else {
			contexts.add(context.deepCopy());
		}
		contexts.addObject().put("@base", base);
		ArrayNode graph = document.putArray("@graph");
		entities.forEach(e -> graph.add(e.toJson()));
		ObjectNode wpsld = JsonNodeFactory.instance.objectNode();
		wpsld.put("version", VERSION);
		wpsld.put("crate", base);
		ArrayNode others = wpsld.putArray("referenced");
		referenced.forEach(e -> others.add(e.toJson()));
		wpsld.set("document", document);
		ClipboardContent content = new ClipboardContent();
		try {
			List<String> objects = new ArrayList<>();
			for (Entity entity : entities) {
				objects.add(Crate.WRITER.writeValueAsString(entity.toJson()));
			}
			content.putString(String.join(",\n", objects));
			content.put(ENTITY, Crate.WRITER.writeValueAsString(wpsld));
			content.put(LD_JSON, Crate.WRITER.writeValueAsString(document));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Can't write the entities as JSON", e);
		}
		Clipboard.getSystemClipboard().setContent(content);
	}

	/**
	 * The entities on the clipboard: those copied by
	 * {@link #copy(List, List, JsonNode, String)} (of a {@link #VERSION} this
	 * wpsld knows), else from a JSON-LD
	 * document, e.g. from another JSON-LD tool, else their JSON-LD as text,
	 * e.g. copied in a text editor: an object, objects separated by commas,
	 * an array of them, or a document. The document's <code>@context</code>
	 * is not used yet, apart from its <code>@base</code>: its terms are taken
	 * as they are. None if there are no entities.
	 */
	static Optional<Clip> entities() {
		Clipboard clipboard = Clipboard.getSystemClipboard();
		try {
			JsonNode wpsld = clipboard.getContent(ENTITY) instanceof String text ? new ObjectMapper().readTree(text)
					: null;
			// A later wpsld's, if changed so this one would get it wrong
			if (wpsld != null && wpsld.path("version").asInt(0) <= VERSION) {
				Map<String, Entity> referenced = new LinkedHashMap<>();
				wpsld.path("referenced").forEach(
						node -> Entity.fromJson(node).ifPresent(e -> referenced.putIfAbsent(e.getId(), e)));
				return clip(fromDocument(wpsld.path("document")), referenced,
						Optional.of(wpsld.path("crate").asText("")).filter(c -> !c.isBlank()));
			}
			if (clipboard.getContent(LD_JSON) instanceof String text) {
				JsonNode document = new ObjectMapper().readTree(text);
				return clip(fromDocument(document), Map.of(), base(document.path("@context")));
			}
			if (clipboard.getString() instanceof String text && !text.isBlank()) {
				// Several objects separated by commas are a JSON array in brackets
				JsonNode nodes = new ObjectMapper().readTree("[" + text + "]");
				if (nodes.size() == 1 && (nodes.get(0).isArray() || nodes.get(0).has("@graph"))) {
					JsonNode node = nodes.get(0);
					return clip(fromDocument(node), Map.of(), base(node.path("@context")));
				}
				return clip(entities(nodes), Map.of(), Optional.empty());
			}
		} catch (JsonProcessingException | IllegalArgumentException e) {
			// Not JSON-LD
		}
		return Optional.empty();
	}

	private static Optional<Clip> clip(List<Entity> entities, Map<String, Entity> referenced, Optional<String> crate) {
		return entities.isEmpty() ? Optional.empty() : Optional.of(new Clip(entities, referenced, crate));
	}

	/**
	 * The entities of a document's <code>@graph</code> (an array, or one
	 * object), or of an array of them.
	 */
	private static List<Entity> fromDocument(JsonNode document) {
		JsonNode graph = document.isArray() ? document : document.path("@graph");
		return entities(graph.isObject() ? JsonNodeFactory.instance.arrayNode().add(graph) : graph);
	}

	private static List<Entity> entities(JsonNode nodes) {
		return StreamSupport.stream(nodes.spliterator(), false).map(Entity::fromJson).flatMap(Optional::stream)
				.toList();
	}

	/**
	 * The <code>@base</code> of a <code>@context</code>: of its last object
	 * that has one.
	 */
	private static Optional<String> base(JsonNode context) {
		List<JsonNode> contexts = context.isArray() ? StreamSupport.stream(context.spliterator(), false).toList()
				: List.of(context);
		return contexts.stream().map(c -> c.path("@base")).filter(JsonNode::isTextual).map(JsonNode::asText)
				.reduce((first, last) -> last);
	}

	/**
	 * Keep entities copied by {@link #copy(List, List, JsonNode, String)}
	 * from being pasted into one of wpsld's own text controls as their JSON:
	 * a reference field pastes a single entity's <code>@id</code>; other
	 * fields, and reference fields with several entities, nothing. Typing,
	 * and pasting other text, are as usual.
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
			String text = Clipboard.getSystemClipboard().getString();
			// A single line field leaves out the line ends of what is pasted
			if (text == null || !oneLine(text).equals(oneLine(added))) {
				return change;
			}
			List<Entity> entities = reference ? entities().map(Clip::entities).orElse(List.of()) : List.of();
			if (entities.size() != 1) {
				return null;
			}
			String id = entities.get(0).getId();
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
