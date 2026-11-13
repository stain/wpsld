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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

/**
 * An RO-Crate's HTML preview, <code>ro-crate-preview.html</code>: the crate
 * described for people, as a plain web page without scripts. The crate's
 * name and description; a list of its entities, its files and folders
 * first, then the others by type; and a section for each entity with its
 * properties, linking to the entities it refers to by <code>#</code>
 * anchors, to its files by their paths, and to the web.
 */
public final class CratePreview {
	/** The name of the generator, in the page, to tell wpsld's previews from others' */
	public static final String GENERATOR = "wpsld";
	/** The preview's file name, beside the metadata file */
	public static final String FILE = "ro-crate-preview.html";

	private static final String STYLE = """
			body { font-family: system-ui, -apple-system, "Segoe UI", sans-serif; line-height: 1.5;
			  max-width: 60em; margin: 2em auto; padding: 0 1em; color: #1f2328; background: #fff; }
			h1 { margin-bottom: 0.2em; }
			h2 { margin-top: 2em; border-bottom: 1px solid #d0d7de; padding-bottom: 0.2em; }
			h3 { margin: 1.5em 0 0.3em; }
			.types { color: #59636e; margin: 0; }
			.id { font-family: ui-monospace, monospace; font-size: 0.9em; color: #59636e; word-break: break-all; }
			section.entity { border: 1px solid #d0d7de; border-radius: 6px; padding: 0.5em 1em; margin: 1em 0; }
			section.entity:target { border-color: #0969da; box-shadow: 0 0 0 2px #0969da33; }
			table { border-collapse: collapse; width: 100%; }
			th { text-align: left; vertical-align: top; width: 14em; padding: 0.3em 1em 0.3em 0; font-weight: 600; }
			td { padding: 0.3em 0; vertical-align: top; overflow-wrap: anywhere; }
			ul.values { margin: 0; padding-left: 1.2em; }
			nav ul { columns: 2; }
			a { color: #0969da; }
			footer { margin-top: 3em; color: #59636e; font-size: 0.85em; }
			@media (prefers-color-scheme: dark) {
			  body { color: #e6edf3; background: #0d1117; }
			  .types, .id, footer { color: #9198a1; }
			  section.entity, h2 { border-color: #3d444d; }
			  a { color: #4493f8; }
			}
			""";

	private CratePreview() {
	}

	/**
	 * The preview of a crate
	 *
	 * @param label     a property's label, e.g. "Date published"
	 * @param generator what made it, e.g. "wpsld 0.3", in the page
	 */
	public static String html(Crate crate, java.util.function.Function<String, String> label, String generator) {
		Entity root = crate.getRoot();
		List<Entity> data = crate.dataEntities();
		Map<Entity, String> anchors = new HashMap<>();
		Map<String, Entity> byId = new HashMap<>();
		int n = 0;
		for (Entity entity : crate.getEntities()) {
			anchors.put(entity, "entity-" + n++);
			byId.putIfAbsent(entity.getId(), entity);
		}
		// The others by their first type
		Map<String, List<Entity>> byType = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Entity entity : crate.getEntities()) {
			if (entity != root && !data.contains(entity)) {
				byType.computeIfAbsent(entity.getTypes().get(0), t -> new ArrayList<>()).add(entity);
			}
		}
		Map<String, List<Entity>> groups = new LinkedHashMap<>();
		if (!data.isEmpty()) {
			groups.put("Files and folders", data);
		}
		groups.putAll(byType);

		StringBuilder html = new StringBuilder();
		html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
				.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
				.append("<meta name=\"generator\" content=\"").append(escape(generator)).append("\">\n")
				.append("<title>").append(escape(root.getLabel())).append("</title>\n")
				.append("<style>\n").append(STYLE).append("</style>\n</head>\n<body>\n");
		html.append("<header>\n<h1>").append(escape(root.getLabel())).append("</h1>\n");
		root.getAll("description").stream().filter(JsonNode::isValueNode)
				.forEach(d -> html.append("<p>").append(escape(d.asText())).append("</p>\n"));
		html.append("<p>This is an <a href=\"https://w3id.org/ro/crate\">RO-Crate</a>: its metadata is in "
				+ "<a href=\"" + Crate.RO_CRATE_METADATA_JSON + "\">" + Crate.RO_CRATE_METADATA_JSON
				+ "</a>, described below.</p>\n</header>\n");

		// Contents
		html.append("<nav>\n<h2>Contents</h2>\n<ul>\n<li><a href=\"#").append(anchors.get(root)).append("\">")
				.append(escape(root.getLabel())).append("</a> (the crate)</li>\n</ul>\n");
		groups.forEach((group, entities) -> {
			html.append("<h3>").append(escape(group)).append("</h3>\n<ul>\n");
			entities.forEach(e -> html.append("<li><a href=\"#").append(anchors.get(e)).append("\">")
					.append(escape(e.getLabel())).append("</a></li>\n"));
			html.append("</ul>\n");
		});
		html.append("</nav>\n<main>\n");

		// The crate itself, then each group
		html.append("<h2>The crate</h2>\n");
		entity(html, root, anchors, byId, data, label);
		groups.forEach((group, entities) -> {
			html.append("<h2>").append(escape(group)).append("</h2>\n");
			entities.forEach(e -> entity(html, e, anchors, byId, data, label));
		});
		html.append("</main>\n<footer>Made by ").append(escape(generator))
				.append(" from the crate's metadata.</footer>\n</body>\n</html>\n");
		return html.toString();
	}

	/** Whether a page is a preview wpsld made, by its generator */
	public static boolean isOurs(String html) {
		return html.contains("<meta name=\"generator\" content=\"" + GENERATOR);
	}

	private static void entity(StringBuilder html, Entity entity, Map<Entity, String> anchors,
			Map<String, Entity> byId, List<Entity> data, java.util.function.Function<String, String> label) {
		html.append("<section class=\"entity\" id=\"").append(anchors.get(entity)).append("\">\n<h3>")
				.append(escape(entity.getLabel())).append("</h3>\n<p class=\"types\">")
				.append(escape(String.join(", ", entity.getTypes()))).append("</p>\n<p class=\"id\">");
		String id = entity.getId();
		if (isWeb(id) || data.contains(entity) && isRelative(id)) {
			html.append("<a href=\"").append(escape(id)).append("\">").append(escape(id)).append("</a>");
		} else {
			html.append(escape(id));
		}
		html.append("</p>\n");
		Map<String, List<JsonNode>> values = new LinkedHashMap<>();
		entity.getValues().keySet().forEach(p -> values.put(p, entity.getAll(p)));
		values.remove("name");
		if (!values.isEmpty()) {
			html.append("<table>\n");
			values.forEach((property, list) -> {
				html.append("<tr><th>").append(escape(label.apply(property))).append("</th><td>");
				if (list.size() == 1) {
					value(html, list.get(0), anchors, byId);
				} else {
					html.append("<ul class=\"values\">");
					list.forEach(v -> {
						html.append("<li>");
						value(html, v, anchors, byId);
						html.append("</li>");
					});
					html.append("</ul>");
				}
				html.append("</td></tr>\n");
			});
			html.append("</table>\n");
		}
		html.append("</section>\n");
	}

	private static void value(StringBuilder html, JsonNode value, Map<Entity, String> anchors,
			Map<String, Entity> byId) {
		if (value.isObject() && value.path("@id").isTextual()) {
			String id = value.get("@id").asText();
			Entity linked = byId.get(id);
			if (linked != null) {
				html.append("<a href=\"#").append(anchors.get(linked)).append("\">").append(escape(linked.getLabel()))
						.append("</a>");
			} else if (isWeb(id)) {
				html.append("<a href=\"").append(escape(id)).append("\">").append(escape(id)).append("</a>");
			} else {
				html.append("<span class=\"id\">").append(escape(id)).append("</span>");
			}
		} else if (value.isObject() && value.has("@value")) {
			html.append(escape(value.get("@value").asText()));
			if (value.path("@language").isTextual()) {
				html.append(" <span class=\"types\">(").append(escape(value.get("@language").asText())).append(")</span>");
			}
		} else if (value.isValueNode()) {
			String text = value.asText();
			if (isWeb(text)) {
				html.append("<a href=\"").append(escape(text)).append("\">").append(escape(text)).append("</a>");
			} else {
				html.append(escape(text));
			}
		} else {
			html.append("<code>").append(escape(value.toString())).append("</code>");
		}
	}

	private static boolean isWeb(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		return (lower.startsWith("http://") || lower.startsWith("https://")) && !text.contains(" ");
	}

	private static boolean isRelative(String id) {
		return !id.isEmpty() && !Crate.isAbsolute(id) && !Crate.isLocal(id) && !Crate.isBlankNode(id);
	}

	/** Text as HTML, also in attributes */
	static String escape(String text) {
		StringBuilder escaped = new StringBuilder(text.length());
		for (char c : text.toCharArray()) {
			switch (c) {
			case '&' -> escaped.append("&amp;");
			case '<' -> escaped.append("&lt;");
			case '>' -> escaped.append("&gt;");
			case '"' -> escaped.append("&quot;");
			case '\'' -> escaped.append("&#39;");
			default -> escaped.append(c);
			}
		}
		return escaped.toString();
	}
}
