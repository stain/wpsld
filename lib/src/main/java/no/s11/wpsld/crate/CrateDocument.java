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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import no.s11.wpsld.jsonld.JsonLdContext;

/**
 * An RO-Crate Metadata Document as read by {@link CrateReader}: the crate's
 * entities as written, the processed JSON-LD context to understand them, and
 * the problems found reading it.
 * <p>
 * The entities keep their compacted names (as in the file), while
 * {@link #types(Entity)} and {@link #values(Entity, String)} work with
 * expanded IRIs, so that e.g. <code>domainIncludes</code> and
 * <code>schema:domainIncludes</code> are the same property, and
 * <code>https://schema.org/</code> IRIs are taken as <code>http://schema.org/</code>
 * as in the RO-Crate contexts.
 *
 * @param source     Where the document was read from, for messages
 * @param crate      The crate, empty if it could not be loaded at all (see
 *                   {@link #problems()})
 * @param context    The document's processed <code>@context</code>
 * @param descriptor The metadata descriptor entity, if found
 * @param conformsTo What the descriptor says the crate conforms to, e.g.
 *                   <code>https://w3id.org/ro/crate/1.2</code>
 * @param problems   Problems found, most serious first
 */
public record CrateDocument(String source, Optional<Crate> crate, JsonLdContext context, Optional<Entity> descriptor,
		List<String> conformsTo, List<Problem> problems) {

	private static final String SCHEMA_HTTPS = "https://schema.org/";
	private static final String SCHEMA_HTTP = "http://schema.org/";

	/**
	 * A problem found reading a crate.
	 *
	 * @param severity How serious
	 * @param message  What and where
	 */
	public record Problem(Severity severity, String message) {
		@Override
		public String toString() {
			return severity + ": " + message;
		}
	}

	public enum Severity {
		/** The crate, or part of it, could not be loaded or was changed */
		ERROR,
		/** Loaded, but not as RO-Crate or JSON-LD say it should be */
		WARNING
	}

	public CrateDocument {
		Objects.requireNonNull(source);
		problems = List.copyOf(problems);
		conformsTo = List.copyOf(conformsTo);
	}

	public boolean hasErrors() {
		return problems.stream().anyMatch(p -> p.severity() == Severity.ERROR);
	}

	/**
	 * Expand a term (property name or type) as written in the crate to an
	 * IRI with the crate's context, with schema.org as http://.
	 */
	public Optional<String> expand(String term) {
		return context.expandTerm(term).map(CrateDocument::normalise);
	}

	/**
	 * <code>https://schema.org/</code> IRIs as <code>http://schema.org/</code>,
	 * as used by the RO-Crate contexts; other IRIs as they are.
	 */
	public static String normalise(String iri) {
		return iri.startsWith(SCHEMA_HTTPS) ? SCHEMA_HTTP + iri.substring(SCHEMA_HTTPS.length()) : iri;
	}

	/**
	 * The expanded IRIs of an entity's types.
	 */
	public Set<String> types(Entity entity) {
		return entity.getTypes().stream().map(this::expand).flatMap(Optional::stream)
				.collect(Collectors.toUnmodifiableSet());
	}

	public boolean hasType(Entity entity, String typeIri) {
		return types(entity).contains(normalise(typeIri));
	}

	/**
	 * The values of a property given by its expanded IRI, whatever name the
	 * crate uses for it.
	 */
	public List<JsonNode> values(Entity entity, String propertyIri) {
		String iri = normalise(propertyIri);
		return entity.getValues().keySet().stream().filter(name -> expand(name).filter(iri::equals).isPresent())
				.flatMap(name -> entity.getAll(name).stream()).toList();
	}

	/**
	 * The identifiers referenced by a property, i.e. its <code>{"@id": ...}</code>
	 * values, expanded with the context (for compact IRIs).
	 */
	public List<String> references(Entity entity, String propertyIri) {
		return values(entity, propertyIri).stream().filter(v -> v.isObject() && v.path("@id").isTextual())
				.map(v -> normalise(context.expandId(v.get("@id").asText()))).toList();
	}

	public Optional<Entity> entity(String id) {
		return crate.flatMap(c -> c.get(id));
	}

	/**
	 * The crate's entities with the given type (expanded IRI).
	 */
	public List<Entity> entitiesOfType(String typeIri) {
		return crate.map(c -> c.getEntities().stream().filter(e -> hasType(e, typeIri)).toList()).orElse(List.of());
	}
}
