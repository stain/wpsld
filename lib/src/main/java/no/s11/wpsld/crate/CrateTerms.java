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

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.jsonld.ContextCache;
import no.s11.wpsld.jsonld.JsonLdContext;
import no.s11.wpsld.soss.ClassDef;
import no.s11.wpsld.soss.DefinedTerm;
import no.s11.wpsld.soss.PropertyDef;
import no.s11.wpsld.soss.SchemaFromJsonLd;
import no.s11.wpsld.util.Value;

/**
 * Names of schema.org types and properties as written in RO-Crates, and
 * back, through the RO-Crate 1.3 JSON-LD context.
 * <p>
 * A term keeps its schema.org name when the context maps that name to it
 * (<code>Dataset</code>, <code>contentUrl</code>), except where RO-Crate
 * asks for its own name: <code>schema:MediaObject</code> is written as
 * <code>File</code>, as files MUST have that type. The context's other
 * aliases, like <code>path</code> (RO-Crate 1.0's name for
 * <code>contentUrl</code>) and <code>Journal</code> (for
 * <code>Periodical</code>), are understood when reading but not written.
 */
public class CrateTerms {
	/** Names RO-Crate requires instead of the schema.org name */
	private static final Map<String, String> PREFERRED = Map.of("http://schema.org/MediaObject", "File");
	private static final Value<JsonLdContext> RO_CRATE_1_3 = Value.lazy(
			() -> JsonLdContext.of(TextNode.valueOf(Crate.RO_CRATE_1_3_CONTEXT), ContextCache.bundled()));

	private final SchemaFromJsonLd schema;
	private final JsonLdContext context;

	public CrateTerms(SchemaFromJsonLd schema) {
		this(schema, RO_CRATE_1_3.get());
	}

	/**
	 * Load and process the RO-Crate 1.3 context now, e.g. in the background
	 * while the application starts, so that creating CrateTerms later doesn't
	 * have to (about 150 ms on a cold JVM). Loaded once, however often or from
	 * however many threads this is called.
	 */
	public static JsonLdContext prewarm() {
		return RO_CRATE_1_3.get();
	}

	public CrateTerms(SchemaFromJsonLd schema, JsonLdContext context) {
		this.schema = Objects.requireNonNull(schema);
		this.context = Objects.requireNonNull(context);
	}

	public SchemaFromJsonLd getSchema() {
		return schema;
	}

	/**
	 * The name to write for a type or property, e.g. <code>File</code> for
	 * <code>schema:MediaObject</code>.
	 */
	public String name(DefinedTerm term) {
		String iri = term.getID().toString();
		String preferred = PREFERRED.get(iri);
		if (preferred != null) {
			return preferred;
		}
		if (context.expandTerm(term.getName()).filter(iri::equals).isPresent()) {
			return term.getName();
		}
		return context.compact(iri);
	}

	/**
	 * The schema.org class for a type name as written in a crate, e.g.
	 * <code>MediaObject</code> for <code>File</code>.
	 */
	public Optional<ClassDef> classDef(String typeName) {
		return context.expandTerm(typeName).map(URI::create).map(schema.getClasses()::get);
	}

	/**
	 * The schema.org property for a property name as written in a crate.
	 */
	public Optional<PropertyDef> propertyDef(String propertyName) {
		return context.expandTerm(propertyName).map(URI::create).map(schema.getProperties()::get);
	}
}
