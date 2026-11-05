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
package no.s11.wpsld.jsonld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * An active JSON-LD context, from the <code>@context</code> of a document, to
 * expand terms and compact IRIs (like <code>schema:name</code>) to full IRIs,
 * and back.
 * <p>
 * Enough of JSON-LD context processing for RO-Crates, which "MUST be a
 * document which is valid JSON-LD 1.0 in flattened and compacted form"
 * (RO-Crate 1.2 and 1.3): remote contexts (from a {@link ContextCache}, never
 * fetched), arrays of contexts, <code>null</code> to reset,
 * <code>@vocab</code>, <code>@base</code>, and term definitions as strings
 * or objects with <code>@id</code> and <code>@type</code>, referring to each
 * other in any order.
 * <p>
 * Processing never throws: unknown remote contexts, cyclic definitions, the
 * JSON-LD 1.1 features not valid in RO-Crates (scoped contexts,
 * <code>@import</code>, <code>@protected</code>, <code>@propagate</code>,
 * <code>"@version": 1.1</code>) and the JSON-LD 1.0 features not handled
 * (<code>@reverse</code>) are listed in {@link #getProblems()}, and the rest
 * of the context is still used.
 */
public final class JsonLdContext {

	/**
	 * A term definition.
	 *
	 * @param iri  The IRI the term expands to
	 * @param type The <code>@type</code> of its values, if given, e.g.
	 *             <code>@id</code> for references
	 */
	public record Term(String iri, Optional<String> type) {
	}

	public static final JsonLdContext EMPTY = new JsonLdContext(Map.of(), Optional.empty(), Optional.empty(),
			List.of());

	/** Terms; an empty Optional for a term explicitly mapped to null */
	private final Map<String, Optional<Term>> terms;
	private final Optional<String> vocab;
	private final Optional<String> base;
	private final List<String> problems;
	private Map<String, String> compactions;

	private JsonLdContext(Map<String, Optional<Term>> terms, Optional<String> vocab, Optional<String> base,
			List<String> problems) {
		this.terms = Collections.unmodifiableMap(terms);
		this.vocab = vocab;
		this.base = base;
		this.problems = List.copyOf(problems);
	}

	/**
	 * Process the value of a <code>@context</code>: a URL, an object, an array
	 * of those, or null.
	 */
	public static JsonLdContext of(JsonNode context, ContextCache cache) {
		Processor processor = new Processor(cache);
		processor.process(context, new ArrayList<>());
		return new JsonLdContext(processor.terms, processor.vocab, processor.base, processor.problems);
	}

	/**
	 * The <code>@context</code> of a JSON-LD document, or the empty context if
	 * it has none.
	 */
	public static JsonLdContext ofDocument(JsonNode document, ContextCache cache) {
		return document.has("@context") ? of(document.get("@context"), cache) : EMPTY;
	}

	/**
	 * Problems found while processing the context, e.g. a remote context not
	 * in the cache. Empty if the whole context could be used.
	 */
	public List<String> getProblems() {
		return problems;
	}

	public Optional<Term> getTerm(String term) {
		return terms.getOrDefault(term, Optional.empty());
	}

	public Set<String> getTerms() {
		return terms.keySet();
	}

	public Optional<String> getVocab() {
		return vocab;
	}

	public Optional<String> getBase() {
		return base;
	}

	/**
	 * Expand a property name or <code>@type</code> value: a term, a compact
	 * IRI like <code>schema:name</code>, an absolute IRI, or a word relative
	 * to <code>@vocab</code>. Empty if it can't be expanded (an unknown term
	 * without <code>@vocab</code>, or a term mapped to null), as JSON-LD then
	 * drops it.
	 */
	public Optional<String> expandTerm(String value) {
		if (value.startsWith("@")) {
			return Optional.of(value);
		}
		if (terms.containsKey(value)) {
			return terms.get(value).map(Term::iri);
		}
		Optional<String> compact = expandCompact(value);
		if (compact.isPresent()) {
			return compact;
		}
		if (isAbsolute(value)) {
			return Optional.of(value);
		}
		return vocab.map(v -> v + value);
	}

	/**
	 * Expand an <code>@id</code> value: compact IRIs like
	 * <code>orcid:0000-...</code> are expanded, absolute IRIs and relative
	 * references (e.g. <code>./</code>, <code>data/file.txt</code>,
	 * <code>#person</code>) are kept as they are, as RO-Crates are read
	 * relative to where they are.
	 */
	public String expandId(String value) {
		return expandCompact(value).orElse(value);
	}

	private Optional<String> expandCompact(String value) {
		int colon = value.indexOf(':');
		if (colon <= 0 || value.startsWith("//", colon + 1)) {
			return Optional.empty();
		}
		return getTerm(value.substring(0, colon)).map(prefix -> prefix.iri() + value.substring(colon + 1));
	}

	private static boolean isAbsolute(String value) {
		int colon = value.indexOf(':');
		return colon > 0 && value.substring(0, colon).matches("[A-Za-z][A-Za-z0-9+.-]*");
	}

	/**
	 * Compact an IRI: to a term defined for it (the shortest), else to a
	 * compact IRI with the longest matching prefix, else relative to
	 * <code>@vocab</code>, else the IRI itself.
	 */
	public String compact(String iri) {
		String term = compactions().get(iri);
		if (term != null) {
			return term;
		}
		Optional<String> prefixed = terms.entrySet().stream()
				.filter(e -> e.getValue().isPresent() && !e.getKey().contains(":"))
				.filter(e -> isPrefixIri(e.getValue().get().iri()) && iri.startsWith(e.getValue().get().iri())
						&& iri.length() > e.getValue().get().iri().length())
				.max(Comparator.comparingInt((Map.Entry<String, Optional<Term>> e) -> e.getValue().get().iri()
						.length()).thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
				.map(e -> e.getKey() + ":" + iri.substring(e.getValue().get().iri().length()));
		if (prefixed.isPresent()) {
			return prefixed.get();
		}
		if (vocab.isPresent() && iri.startsWith(vocab.get())) {
			String rest = iri.substring(vocab.get().length());
			if (!rest.isEmpty() && !rest.contains(":") && !terms.containsKey(rest)) {
				return rest;
			}
		}
		return iri;
	}

	private static boolean isPrefixIri(String iri) {
		return iri.endsWith("/") || iri.endsWith("#") || iri.endsWith(":");
	}

	private synchronized Map<String, String> compactions() {
		if (compactions == null) {
			Map<String, String> byIri = new HashMap<>();
			terms.forEach((term, definition) -> definition.ifPresent(d -> byIri.merge(d.iri(), term,
					(a, b) -> a.length() < b.length() || (a.length() == b.length() && a.compareTo(b) < 0) ? a : b)));
			compactions = byIri;
		}
		return compactions;
	}

	@Override
	public String toString() {
		return "JsonLdContext with " + terms.size() + " terms" + vocab.map(v -> ", @vocab " + v).orElse("");
	}

	/**
	 * Builds the active context while processing.
	 */
	private static class Processor {
		private static final Set<String> IGNORED = Set.of("@language");
		/** JSON-LD 1.1 only, not valid in RO-Crates, which are JSON-LD 1.0 */
		private static final Set<String> JSON_LD_11 = Set.of("@version", "@import", "@propagate", "@protected",
				"@direction");

		private final ContextCache cache;
		private Map<String, Optional<Term>> terms = new LinkedHashMap<>();
		private Optional<String> vocab = Optional.empty();
		private Optional<String> base = Optional.empty();
		private final List<String> problems = new ArrayList<>();

		/** While processing one context object: its definitions and which are done */
		private JsonNode local;
		private Map<String, Boolean> defined;

		Processor(ContextCache cache) {
			this.cache = cache;
		}

		void process(JsonNode context, List<String> remotes) {
			if (context == null || context.isNull()) {
				// Reset to the empty context
				terms = new LinkedHashMap<>();
				vocab = Optional.empty();
				base = Optional.empty();
			} else if (context.isArray()) {
				context.forEach(c -> process(c, remotes));
			} else if (context.isTextual()) {
				processRemote(context.asText(), remotes);
			} else if (context.isObject()) {
				processObject(context);
			} else {
				problems.add("Not a context: " + context);
			}
		}

		private void processRemote(String url, List<String> remotes) {
			if (remotes.contains(url)) {
				problems.add("Recursive context inclusion of " + url);
				return;
			}
			Optional<JsonNode> document = cache.get(url);
			if (document.isEmpty()) {
				problems.add("Context " + url + " is not in the cache of known contexts; its terms are unknown");
				return;
			}
			if (!document.get().has("@context")) {
				problems.add("Context document " + url + " has no @context");
				return;
			}
			remotes.add(url);
			process(document.get().get("@context"), remotes);
			remotes.remove(remotes.size() - 1);
		}

		private void processObject(JsonNode context) {
			if (context.has("@base")) {
				JsonNode value = context.get("@base");
				base = value.isTextual() ? Optional.of(value.asText()) : Optional.empty();
			}
			if (context.has("@vocab")) {
				JsonNode value = context.get("@vocab");
				if (value.isNull()) {
					vocab = Optional.empty();
				} else if (value.isTextual()) {
					// May itself be a term or compact IRI, e.g. "schema:"
					local = context;
					defined = new HashMap<>();
					vocab = Optional.of(expandIri(value.asText(), false, null));
				} else {
					problems.add("Invalid @vocab: " + value);
				}
			}
			local = context;
			defined = new HashMap<>();
			context.properties().forEach(e -> {
				String key = e.getKey();
				if (key.equals("@base") || key.equals("@vocab") || IGNORED.contains(key)) {
					return;
				}
				if (JSON_LD_11.contains(key)) {
					problems.add(key + " in a context is JSON-LD 1.1, not valid in RO-Crate (JSON-LD 1.0), ignored");
					return;
				}
				if (key.startsWith("@")) {
					problems.add("Unknown keyword " + key + " in a context, ignored");
					return;
				}
				define(key);
			});
			local = null;
			defined = null;
		}

		/**
		 * Create the definition of a term of the context being processed,
		 * first creating those it depends on (JSON-LD's "Create Term
		 * Definition").
		 */
		private void define(String term) {
			Boolean done = defined.get(term);
			if (Boolean.TRUE.equals(done)) {
				return;
			}
			if (Boolean.FALSE.equals(done)) {
				problems.add("Cyclic definition of term " + term + ", ignored");
				return;
			}
			defined.put(term, false);
			JsonNode value = local.get(term);
			if (value.isNull()) {
				terms.put(term, Optional.empty());
			} else if (value.isTextual()) {
				terms.put(term, Optional.of(new Term(expandIri(value.asText(), true, term), Optional.empty())));
			} else if (value.isObject()) {
				defineFromObject(term, value);
			} else {
				problems.add("Invalid definition of term " + term + ": " + value);
			}
			defined.put(term, true);
		}

		private void defineFromObject(String term, JsonNode value) {
			if (value.has("@reverse")) {
				problems.add("Reverse term " + term + " is not supported, ignored");
				return;
			}
			if (value.has("@context")) {
				problems.add("Scoped context of term " + term
						+ " is JSON-LD 1.1, not valid in RO-Crate (JSON-LD 1.0); its own context is ignored");
			}
			String iri;
			JsonNode id = value.get("@id");
			if (id != null && id.isNull()) {
				terms.put(term, Optional.empty());
				return;
			} else if (id != null && id.isTextual()) {
				iri = expandIri(id.asText(), true, term);
			} else if (term.contains(":")) {
				iri = expandIri(term, true, term);
			} else if (vocab.isPresent()) {
				iri = vocab.get() + term;
			} else {
				problems.add("Term " + term + " has no @id and there is no @vocab, ignored");
				return;
			}
			Optional<String> type = Optional.empty();
			JsonNode typeValue = value.get("@type");
			if (typeValue != null && typeValue.isTextual()) {
				String t = typeValue.asText();
				type = Optional.of(t.startsWith("@") ? t : expandIri(t, true, term));
			}
			terms.put(term, Optional.of(new Term(iri, type)));
		}

		/**
		 * Expand an IRI in a term definition, defining the terms and prefixes
		 * it uses from the same context first.
		 *
		 * @param self The term being defined, which may use its own name as
		 *             a compact IRI, e.g. <code>"prov:specializationOf":
		 *             "prov:specializationOf"</code>; null for <code>@vocab</code>
		 */
		private String expandIri(String value, boolean useVocab, String self) {
			if (value.startsWith("@")) {
				return value;
			}
			if (value.equals(self)) {
				// Not a reference to itself, but a compact or absolute IRI
				int colon = value.indexOf(':');
				if (colon > 0 && !value.startsWith("//", colon + 1)) {
					String prefix = value.substring(0, colon);
					if (local.has(prefix) && !prefix.equals(self)) {
						define(prefix);
					}
					Optional<Term> prefixTerm = terms.getOrDefault(prefix, Optional.empty());
					if (prefixTerm.isPresent()) {
						return prefixTerm.get().iri() + value.substring(colon + 1);
					}
				}
				return useVocab && colon < 0 && vocab.isPresent() ? vocab.get() + value : value;
			}
			if (local.has(value)) {
				define(value);
			}
			if (terms.containsKey(value) && terms.get(value).isPresent()) {
				return terms.get(value).get().iri();
			}
			int colon = value.indexOf(':');
			if (colon > 0) {
				String prefix = value.substring(0, colon);
				String suffix = value.substring(colon + 1);
				if (suffix.startsWith("//")) {
					return value;
				}
				if (local.has(prefix)) {
					define(prefix);
				}
				Optional<Term> prefixTerm = terms.getOrDefault(prefix, Optional.empty());
				if (prefixTerm.isPresent()) {
					return prefixTerm.get().iri() + suffix;
				}
				return value;
			}
			if (useVocab && vocab.isPresent()) {
				return vocab.get() + value;
			}
			return value;
		}
	}
}
