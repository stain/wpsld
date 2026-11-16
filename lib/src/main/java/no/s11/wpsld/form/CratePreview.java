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
import java.io.StringReader;
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
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

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
	private static final String STYLE = stripComment(resource("ro-crate-preview.css"));

	/**
	 * The page, ro-crate-preview.html.mustache beside this class, with its
	 * partials there too; escaped as {@link #escape(String)}
	 */
	private static final Template PAGE = Mustache.compiler().withEscaper(CratePreview::escape)
			.withLoader(name -> new StringReader(resource(name + ".mustache")))
			.compile(resource("ro-crate-preview.html.mustache"));

	private CratePreview() {
	}

	private static String resource(String name) {
		try (InputStream in = CratePreview.class.getResourceAsStream(name)) {
			if (in == null) {
				throw new IllegalStateException("Can't find the preview's " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Can't read the preview's " + name, e);
		}
	}

	/** Not a stylesheet's opening comment, which is about the file, not the page */
	private static String stripComment(String css) {
		return css.startsWith("/*") ? css.substring(css.indexOf("*/") + 2).stripLeading() : css;
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
		Map<String, List<Entity>> grouped = new LinkedHashMap<>();
		if (!data.isEmpty()) {
			grouped.put("Files and folders", data);
		}
		grouped.putAll(byType);

		// What the template shows, as maps and lists (see its opening comment)
		List<Map<String, Object>> groups = new ArrayList<>();
		grouped.forEach((heading, entities) -> groups.add(Map.of("heading", heading, "entities",
				entities.stream().map(e -> entity(crate, e, anchors, byId, label)).toList())));
		List<Map<String, Object>> sections = new ArrayList<>();
		sections.add(Map.of("heading", "The crate", "entities", List.of(entity(crate, root, anchors, byId, label))));
		sections.addAll(groups);
		Map<String, Object> page = new HashMap<>();
		page.put("generator", generator);
		page.put("title", root.getLabel());
		page.put("style", STYLE);
		// The metadata itself, as the RO-Crate specification asks of previews;
		// </ escaped (as JSON allows) so that it can't end the script
		try {
			page.put("metadata", Crate.WRITER.writeValueAsString(crate.toJson()).replace("</", "<\\/"));
		} catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
		page.put("metadataFile", Crate.RO_CRATE_METADATA_JSON);
		page.put("rootAnchor", anchors.get(root));
		page.put("descriptions", root.getAll("description").stream().filter(JsonNode::isValueNode)
				.map(d -> Markdown.toHtml(d.asText())).toList());
		page.put("groups", groups);
		page.put("sections", sections);
		return PAGE.execute(page);
	}

	/** Whether a page is a preview wpsld made, by its generator */
	public static boolean isOurs(String html) {
		return html.contains("<meta name=\"generator\" content=\"" + GENERATOR);
	}

	private static Map<String, Object> entity(Crate crate, Entity entity, Map<Entity, String> anchors,
			Map<String, Entity> byId, java.util.function.Function<String, String> label) {
		Map<String, Object> shown = new HashMap<>();
		shown.put("anchor", anchors.get(entity));
		shown.put("label", entity.getLabel());
		shown.put("types", entity.getTypes());
		String id = entity.getId();
		shown.put("idText", id);
		// Files and folders by their paths, wherever they are linked from
		shown.put("idHref", isRelative(id) ? id : isWeb(crate.expandId(id)) ? crate.expandId(id) : false);
		Map<String, List<JsonNode>> values = new LinkedHashMap<>();
		entity.getValues().keySet().forEach(p -> values.put(p, entity.getAll(p)));
		values.remove("name");
		if (entity == crate.getRoot()) {
			// In the header
			values.remove("description");
		}
		List<Map<String, Object>> properties = new ArrayList<>();
		values.forEach((property, list) -> properties.add(Map.of("label", label.apply(property), "many",
				list.size() > 1, "values", list.stream().map(v -> value(crate, property, v, anchors, byId)).toList())));
		shown.put("table", !properties.isEmpty());
		shown.put("properties", properties);
		return shown;
	}

	/** One value, each of the template's keys there, false where not used */
	private static Map<String, Object> value(Crate crate, String property, JsonNode value,
			Map<Entity, String> anchors, Map<String, Entity> byId) {
		Map<String, Object> shown = new HashMap<>(Map.of("linkHref", false, "linkText", false, "plainId", false,
				"literal", false, "language", false, "description", false, "json", false));
		if (value.isObject() && value.path("@id").isTextual()) {
			String id = value.get("@id").asText();
			Entity linked = byId.get(id);
			if (linked != null) {
				shown.put("linkHref", "#" + anchors.get(linked));
				shown.put("linkText", linked.getLabel());
			} else if (isWeb(crate.expandId(id))) {
				shown.put("linkHref", crate.expandId(id));
				shown.put("linkText", id);
			} else {
				shown.put("plainId", id);
			}
		} else if (value.isObject() && value.has("@value")) {
			shown.put("literal", value.get("@value").asText());
			if (value.path("@language").isTextual()) {
				shown.put("language", value.get("@language").asText());
			}
		} else if (value.isValueNode()) {
			String text = value.asText();
			if (property.equals("description")) {
				shown.put("description", Markdown.toHtml(text));
			} else if (isWeb(text)) {
				shown.put("linkHref", text);
				shown.put("linkText", text);
			} else {
				shown.put("literal", text);
			}
		} else {
			shown.put("json", value.toString());
		}
		return shown;
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
