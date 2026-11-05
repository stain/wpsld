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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import no.s11.wpsld.crate.CrateDocument.Problem;
import no.s11.wpsld.crate.CrateDocument.Severity;
import no.s11.wpsld.jsonld.ContextCache;
import no.s11.wpsld.jsonld.JsonLdContext;

/**
 * Reads an RO-Crate Metadata Document (<code>ro-crate-metadata.json</code>)
 * written by any tool, e.g. a MASP profile crate.
 * <p>
 * Reading never throws for what is in the document: it loads what it can and
 * reports the rest as {@link Problem}s, so that unexpected input can't break
 * the application. Values are kept as written, also those the editor can't
 * edit (e.g. nested objects).
 */
public class CrateReader {
	/** RO-Crate 1.0 used this name for the metadata file */
	private static final List<String> DESCRIPTOR_IDS = List.of(Crate.RO_CRATE_METADATA_JSON,
			"ro-crate-metadata.jsonld");
	private static final String RO_CRATE_SPEC = "https://w3id.org/ro/crate/";
	/** URI schemes expected in RO-Crates; others in a term are probably an undefined prefix */
	private static final Set<String> KNOWN_SCHEMES = Set.of("http", "https", "urn", "mailto", "file", "arcp", "tag",
			"data", "ftp", "did", "info", "ark", "doi", "geo");
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final ContextCache contexts;

	public CrateReader() {
		this(ContextCache.bundled());
	}

	public CrateReader(ContextCache contexts) {
		this.contexts = contexts;
	}

	/**
	 * Read <code>ro-crate-metadata.json</code>, or the one in a directory.
	 */
	public CrateDocument read(Path path) {
		Path file = Files.isDirectory(path) ? path.resolve(Crate.RO_CRATE_METADATA_JSON) : path;
		try (InputStream in = Files.newInputStream(file)) {
			return read(in, file.toString());
		} catch (IOException e) {
			return failed(file.toString(), "Can't read " + file + ": " + e.getMessage());
		}
	}

	public CrateDocument read(InputStream in, String source) {
		JsonNode json;
		try {
			json = MAPPER.readTree(in);
		} catch (JsonProcessingException e) {
			return failed(source, "Not JSON: " + e.getOriginalMessage());
		} catch (IOException e) {
			return failed(source, "Can't read " + source + ": " + e.getMessage());
		}
		return read(json, source);
	}

	private CrateDocument failed(String source, String message) {
		return new CrateDocument(source, Optional.empty(), JsonLdContext.EMPTY, Optional.empty(), List.of(),
				List.of(new Problem(Severity.ERROR, message)));
	}

	public CrateDocument read(JsonNode json, String source) {
		return new Reading(source).read(json);
	}

	/**
	 * State while reading one document.
	 */
	private class Reading {
		private final String source;
		private final List<Problem> problems = new ArrayList<>();
		private JsonLdContext context = JsonLdContext.EMPTY;
		/** Terms not defined by the context and undefined prefixes, with where first used and how often */
		private final Map<String, String> unknownTerms = new LinkedHashMap<>();
		private final Map<String, String> undefinedPrefixes = new LinkedHashMap<>();
		private final Map<String, Integer> uses = new LinkedHashMap<>();

		Reading(String source) {
			this.source = source;
		}

		private void error(String message) {
			problems.add(new Problem(Severity.ERROR, message));
		}

		private void warn(String message) {
			problems.add(new Problem(Severity.WARNING, message));
		}

		private CrateDocument document(Optional<Crate> crate, Optional<Entity> descriptor, List<String> conformsTo) {
			// Once per term or prefix, not for each use
			unknownTerms.forEach((term, first) -> warn(term + " is not defined by the @context, so not understood ("
					+ times(term) + ", first in " + first + ")"));
			undefinedPrefixes.forEach((prefix, first) -> warn(prefix + ": looks like the prefix of a compact IRI, but"
					+ " is not defined by the @context (" + times(prefix + ":") + ", first in " + first + ")"));
			unknownTerms.clear();
			undefinedPrefixes.clear();
			List<Problem> sorted = problems.stream().sorted(Comparator.comparing(Problem::severity)).toList();
			return new CrateDocument(source, crate, context, descriptor, conformsTo, sorted);
		}

		CrateDocument read(JsonNode json) {
			if (!json.isObject()) {
				error("Not an RO-Crate Metadata Document: expected a JSON object, not " + json.getNodeType());
				return document(Optional.empty(), Optional.empty(), List.of());
			}
			if (json.has("@context")) {
				context = JsonLdContext.of(json.get("@context"), contexts);
				context.getProblems().forEach(p -> warn("@context: " + p));
			} else {
				warn("No @context, so no terms can be understood");
			}
			JsonNode graph = json.get("@graph");
			if (graph == null || !graph.isArray()) {
				error("No @graph array: RO-Crate JSON-LD must be flattened, with all entities in @graph");
				return document(Optional.empty(), Optional.empty(), List.of());
			}
			Map<String, Entity> entities = readEntities(graph);

			// The metadata descriptor says which entity is the root
			Optional<Entity> descriptor = DESCRIPTOR_IDS.stream().map(entities::get)
					.filter(e -> e != null).findFirst();
			List<String> conformsTo = List.of();
			String rootId = null;
			if (descriptor.isEmpty()) {
				warn("No metadata descriptor (@id " + Crate.RO_CRATE_METADATA_JSON + ")");
			} else {
				if (descriptor.get().getId().equals("ro-crate-metadata.jsonld")) {
					warn("Metadata descriptor uses RO-Crate 1.0's name ro-crate-metadata.jsonld");
				}
				conformsTo = descriptor.get().getAll("conformsTo").stream()
						.filter(v -> v.isObject() && v.path("@id").isTextual()).map(v -> v.get("@id").asText())
						.toList();
				if (conformsTo.stream().noneMatch(c -> c.startsWith(RO_CRATE_SPEC))) {
					warn("Metadata descriptor doesn't declare an RO-Crate version in conformsTo");
				}
				List<JsonNode> about = descriptor.get().getAll("about");
				if (about.size() == 1 && about.get(0).path("@id").isTextual()) {
					rootId = about.get(0).get("@id").asText();
				} else {
					warn("Metadata descriptor has no single about reference to the root data entity");
				}
			}
			if (rootId == null || !entities.containsKey(rootId)) {
				if (rootId != null) {
					warn("The metadata descriptor's about, " + rootId + ", is not an entity in @graph");
				}
				if (entities.containsKey(Crate.ROOT_ID)) {
					warn("Using " + Crate.ROOT_ID + " as the root data entity");
					rootId = Crate.ROOT_ID;
				} else {
					error("No root data entity found");
					return document(Optional.empty(), descriptor, conformsTo);
				}
			}
			Entity root = entities.get(rootId);
			if (!root.getTypes().contains("Dataset")) {
				warn("The root data entity " + rootId + " is not a Dataset");
			}
			List<Entity> others = entities.values().stream()
					.filter(e -> e != root && !DESCRIPTOR_IDS.contains(e.getId())).toList();
			return document(Optional.of(Crate.of(root, others)), descriptor, conformsTo);
		}

		private Map<String, Entity> readEntities(JsonNode graph) {
			Map<String, Entity> entities = new LinkedHashMap<>();
			int index = 0;
			for (JsonNode node : graph) {
				String where = "@graph[" + index++ + "]";
				if (!node.isObject()) {
					error(where + " is not an object, ignored");
					continue;
				}
				JsonNode id = node.get("@id");
				if (id == null || !id.isTextual() || id.asText().isEmpty()) {
					error(where + " has no @id, ignored");
					continue;
				}
				Entity entity = readEntity(node, id.asText());
				Entity existing = entities.get(entity.getId());
				if (existing != null) {
					warn("More than one entity with @id " + entity.getId() + ", merged");
					merge(existing, entity);
				} else {
					entities.put(entity.getId(), entity);
				}
			}
			return entities;
		}

		private Entity readEntity(JsonNode node, String id) {
			List<String> types = readTypes(node.get("@type"), id);
			Entity entity = new Entity(id, types.toArray(String[]::new));
			node.properties().forEach(e -> {
				String key = e.getKey();
				if (key.equals("@id") || key.equals("@type")) {
					return;
				}
				if (key.startsWith("@")) {
					warn(id + " has " + key + ", which is not supported in an RO-Crate entity, ignored");
					return;
				}
				checkTerm(key, id + " property");
				JsonNode value = e.getValue();
				List<JsonNode> values = new ArrayList<>();
				if (value.isArray()) {
					value.forEach(v -> {
						if (!v.isNull()) {
							values.add(v);
						}
					});
				} else if (!value.isNull()) {
					values.add(value);
				}
				// JSON-LD drops null and empty values
				values.forEach(v -> checkValue(v, id + " " + key));
				entity.setAll(key, values);
			});
			return entity;
		}

		private List<String> readTypes(JsonNode type, String id) {
			List<String> types = new ArrayList<>();
			if (type == null) {
				warn(id + " has no @type, read as Thing");
			} else if (type.isTextual()) {
				types.add(type.asText());
			} else if (type.isArray()) {
				type.forEach(t -> {
					if (t.isTextual()) {
						types.add(t.asText());
					} else {
						warn(id + " has a @type that is not a string, ignored: " + t);
					}
				});
			} else {
				warn(id + " has an invalid @type, read as Thing: " + type);
			}
			types.forEach(t -> checkTerm(t, id + " type"));
			if (types.isEmpty()) {
				types.add("Thing");
			}
			return types;
		}

		private String times(String key) {
			int n = uses.getOrDefault(key, 0);
			return n == 1 ? "used once" : "used " + n + " times";
		}

		/**
		 * Note terms the context doesn't define, and compact IRIs with an
		 * undefined prefix (e.g. sh:minCount without sh), reported once each.
		 */
		private void checkTerm(String term, String where) {
			Optional<String> iri = context.expandTerm(term);
			if (iri.isEmpty()) {
				unknownTerms.putIfAbsent(term, where);
				uses.merge(term, 1, Integer::sum);
				return;
			}
			int colon = term.indexOf(':');
			if (colon > 0 && iri.get().equals(term)
					&& !KNOWN_SCHEMES.contains(term.substring(0, colon).toLowerCase(Locale.ROOT))) {
				String prefix = term.substring(0, colon);
				undefinedPrefixes.putIfAbsent(prefix, where + " " + term);
				uses.merge(prefix + ":", 1, Integer::sum);
			}
		}

		private void checkValue(JsonNode value, String where) {
			if (value.isObject() && !(value.has("@id") && value.size() == 1) && !value.has("@value")
					&& !value.has("@list")) {
				warn(where + " has a nested object; RO-Crate JSON-LD should be flattened, kept as it is");
			} else if (value.isArray()) {
				warn(where + " has a nested array, kept as it is");
			}
		}

		private void merge(Entity entity, Entity other) {
			List<String> types = new ArrayList<>(entity.getTypes());
			other.getTypes().stream().filter(t -> !types.contains(t)).forEach(types::add);
			entity.setTypes(types);
			other.getValues().keySet().forEach(property -> {
				List<JsonNode> values = new ArrayList<>(entity.getAll(property));
				other.getAll(property).stream().filter(v -> !values.contains(v)).forEach(values::add);
				entity.setAll(property, values);
			});
		}
	}
}
