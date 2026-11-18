/*
 * Copyright 2026 The University of Manchester, UK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package no.s11.wpsld.tui;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;

import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.Entity;

/** How the viewer words entities, values and identifiers on one line */
final class Values {
	private Values() {
	}

	/** The entity a value refers to, if it is a reference and the crate has it */
	static Optional<Entity> target(JsonNode value, CrateDocument document) {
		JsonNode id = value.path("@id");
		if (!value.isObject() || !id.isTextual()) {
			return Optional.empty();
		}
		return find(id.asText(), document);
	}

	/** The entity with this identifier, the metadata descriptor too */
	static Optional<Entity> find(String identifier, CrateDocument document) {
		return document.entity(identifier).or(() -> document.descriptor().filter(e -> document.expandId(e.getId())
				.equals(document.expandId(identifier))));
	}

	static String valueText(JsonNode value, CrateDocument document) {
		if (value.isArray()) {
			return StreamSupport.stream(value.spliterator(), false).map(v -> valueText(v, document))
					.collect(Collectors.joining(", "));
		}
		if (value.isObject()) {
			JsonNode id = value.path("@id");
			if (id.isTextual()) {
				String identifier = id.asText();
				return target(value, document)
						.map(entity -> reference(identifier, entity)).orElseGet(() -> shortId(identifier));
			}
			if (value.has("@value")) {
				String literal = valueText(value.get("@value"), document);
				return value.path("@language").isTextual() ? literal + " (" + value.get("@language").asText() + ")"
						: literal;
			}
			return value.properties().stream().map(p -> p.getKey() + ": " + valueText(p.getValue(), document))
					.collect(Collectors.joining("; "));
		}
		return value.asText();
	}

	private static String reference(String id, Entity entity) {
		return shortId(id) + " (" + types(entity)
				+ entity.get("name").filter(JsonNode::isTextual).map(JsonNode::asText)
						.filter(n -> !n.isBlank()).map(n -> ": " + n).orElse("")
				+ ")";
	}

	/** An entity's types, short and comma separated */
	static String types(Entity entity) {
		return types(entity.getTypes());
	}

	static String types(List<String> types) {
		return types.stream().map(Values::shortType).collect(Collectors.joining(", "));
	}

	static String shortType(String type) {
		String name = shortId(type);
		if (name.startsWith("#") && name.length() > 1) {
			return name.substring(1);
		}
		int colon = name.lastIndexOf(':');
		return colon >= 0 && colon < name.length() - 1 ? name.substring(colon + 1) : name;
	}

	static String shortId(String id) {
		int hash = id.lastIndexOf('#');
		if (hash >= 0 && hash < id.length() - 1) {
			return id.substring(hash);
		}
		if (id.contains("://")) {
			int end = id.length();
			while (end > 0 && id.charAt(end - 1) == '/') {
				end--;
			}
			int slash = id.lastIndexOf('/', end - 1);
			if (slash > id.indexOf("://") + 2 && slash < end - 1) {
				return id.substring(slash + 1, end) + (end < id.length() ? "/" : "");
			}
		}
		return id;
	}

	/** An entity as the heading of its view: label and short ID, cut short */
	static String title(Entity entity) {
		return preview(entity.getLabel() + " [" + shortId(entity.getId()) + "]", 18);
	}

	static String preview(String text, int limit) {
		String line = oneLine(text);
		int length = line.codePointCount(0, line.length());
		return length <= limit ? line : line.substring(0, line.offsetByCodePoints(0, limit - 3)) + "...";
	}

	static String oneLine(String text) {
		return text.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]+", " ");
	}
}
