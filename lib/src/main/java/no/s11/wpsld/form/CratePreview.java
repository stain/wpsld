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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

/**
 * An RO-Crate's HTML preview, <code>ro-crate-preview.html</code>: the crate
 * described for people, as a plain web page without scripts (but for a copy
 * of the metadata, as JSON-LD for machines). The crate's
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

	/** The page's stylesheet, a resource beside this class, copied into each page */
	private static final String STYLE = readStyle();

	private CratePreview() {
	}

	private static String readStyle() {
		try (InputStream in = CratePreview.class.getResourceAsStream("ro-crate-preview.css")) {
			if (in == null) {
				throw new IllegalStateException("Can't find the preview's stylesheet ro-crate-preview.css");
			}
			String css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			// Not its opening comment, which is about the file, not the page
			return css.startsWith("/*") ? css.substring(css.indexOf("*/") + 2).stripLeading() : css;
		} catch (IOException e) {
			throw new UncheckedIOException("Can't read the preview's stylesheet", e);
		}
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
				.append("<style>\n").append(STYLE).append("</style>\n");
		// The metadata itself, as the RO-Crate specification asks of previews;
		// </ escaped (as JSON allows) so that it can't end the script
		try {
			html.append("<script type=\"application/ld+json\">\n")
					.append(Crate.WRITER.writeValueAsString(crate.toJson()).replace("</", "<\\/"))
					.append("\n</script>\n");
		} catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
		html.append("</head>\n<body>\n");
		html.append("<header id=\"top\">\n<h1>").append(escape(root.getLabel())).append("</h1>\n");
		root.getAll("description").stream().filter(JsonNode::isValueNode).forEach(d -> description(html, d.asText()));
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
		entity(html, crate, root, anchors, byId, label);
		groups.forEach((group, entities) -> {
			html.append("<h2>").append(escape(group)).append("</h2>\n");
			entities.forEach(e -> entity(html, crate, e, anchors, byId, label));
		});
		html.append("</main>\n<footer>Made by ").append(escape(generator))
				.append(" from the crate's metadata.</footer>\n</body>\n</html>\n");
		return html.toString();
	}

	/** Whether a page is a preview wpsld made, by its generator */
	public static boolean isOurs(String html) {
		return html.contains("<meta name=\"generator\" content=\"" + GENERATOR);
	}

	private static void entity(StringBuilder html, Crate crate, Entity entity, Map<Entity, String> anchors,
			Map<String, Entity> byId, java.util.function.Function<String, String> label) {
		html.append("<section class=\"entity\" id=\"").append(anchors.get(entity)).append("\">\n")
				.append("<p class=\"top\"><a href=\"#top\">Top</a></p>\n<h3>")
				.append(escape(entity.getLabel())).append("</h3>\n<p class=\"types\">");
		entity.getTypes().forEach(t -> html.append("<span>").append(escape(t)).append("</span>"));
		html.append("</p>\n<p class=\"id\">");
		String id = entity.getId();
		// Files and folders by their paths, wherever they are linked from
		if (isWeb(crate.expandId(id)) || isRelative(id)) {
			html.append("<a href=\"").append(escape(isRelative(id) ? id : crate.expandId(id))).append("\">")
					.append(escape(id)).append("</a>");
		} else {
			html.append(escape(id));
		}
		html.append("</p>\n");
		Map<String, List<JsonNode>> values = new LinkedHashMap<>();
		entity.getValues().keySet().forEach(p -> values.put(p, entity.getAll(p)));
		values.remove("name");
		if (entity == crate.getRoot()) {
			// In the header
			values.remove("description");
		}
		if (!values.isEmpty()) {
			html.append("<table>\n");
			values.forEach((property, list) -> {
				html.append("<tr><th>").append(escape(label.apply(property))).append("</th><td>");
				if (list.size() == 1) {
					value(html, crate, property, list.get(0), anchors, byId);
				} else {
					html.append("<ul class=\"values\">");
					list.forEach(v -> {
						html.append("<li>");
						value(html, crate, property, v, anchors, byId);
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

	private static void value(StringBuilder html, Crate crate, String property, JsonNode value, Map<Entity, String> anchors,
			Map<String, Entity> byId) {
		if (value.isObject() && value.path("@id").isTextual()) {
			String id = value.get("@id").asText();
			Entity linked = byId.get(id);
			if (linked != null) {
				html.append("<a href=\"#").append(anchors.get(linked)).append("\">").append(escape(linked.getLabel()))
						.append("</a>");
			} else if (isWeb(crate.expandId(id))) {
				html.append("<a href=\"").append(escape(crate.expandId(id))).append("\">").append(escape(id))
						.append("</a>");
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
			if (property.equals("description")) {
				description(html, text);
			} else if (isWeb(text)) {
				html.append("<a href=\"").append(escape(text)).append("\">").append(escape(text)).append("</a>");
			} else {
				html.append(escape(text));
			}
		} else {
			html.append("<code>").append(escape(value.toString())).append("</code>");
		}
	}

	/** A description: as Markdown if it looks like it, else paragraphs with their line breaks */
	private static void description(StringBuilder html, String text) {
		html.append("<div class=\"description\">\n").append(Markdown.toHtml(text)).append("</div>\n");
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
