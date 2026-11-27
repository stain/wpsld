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

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.masp.Profile;
import no.s11.wpsld.masp.ProfileReader;
import no.s11.wpsld.masp.PropertyRule;
import no.s11.wpsld.soss.ClassDef;

/**
 * Form fields with what a crate defines itself: its <code>rdfs:Class</code>
 * and <code>rdf:Property</code> entities (RO-Crate's ad hoc terms, or MASP
 * rules written in the crate) as rules, see
 * {@link ProfileReader#readDefinitions}, and its types expanded with its own
 * <code>@context</code> where the RO-Crate context doesn't know them (e.g. a
 * type of its own the crate's context names).
 * <p>
 * For a property both name, the chosen fields' own (e.g. a profile's rule)
 * are kept, with the crate's description if they have none, and the crate's
 * replace the vocabulary's. The crate's definitions are of vocabulary types
 * as RDFS has them, so they apply to subtypes too: a property for
 * <code>CreativeWork</code> (or without <code>domainIncludes</code>, for
 * <code>Thing</code>) to a Dataset.
 * <p>
 * Types and properties the crate uses without anything defining them are
 * offered apart, as guesses: {@link #undefinedTypes}, {@link #guessedFieldsFor}.
 */
public class CrateDefinedFields implements FormFields {
	public static final String REQUIRED = "Required by the crate";
	public static final String DEFINED = "Defined in the crate";
	public static final String GUESSED = "Used in the crate, defined nowhere";
	private static final String S = "http://schema.org/";
	private static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	private static final String RDFS = "http://www.w3.org/2000/01/rdf-schema#";

	private final FormFields fields;
	private final CrateDocument document;
	private final Optional<ProfileFormFields> definitions;
	private final Crate crate;

	private CrateDefinedFields(FormFields fields, CrateDocument document, Optional<ProfileFormFields> definitions,
			Crate crate) {
		this.fields = fields;
		this.document = document;
		this.definitions = definitions;
		this.crate = crate;
	}

	/**
	 * The fields with what the crate defines: read now, so to be made again
	 * for the crate as changed.
	 *
	 * @param fields The fields chosen, e.g. of a profile, or schema.org's
	 */
	public static CrateDefinedFields of(FormFields fields, Crate crate) {
		FormFields chosen = fields instanceof CrateDefinedFields c ? c.fields : Objects.requireNonNull(fields);
		CrateDocument document = new CrateDocument("the crate", Optional.of(crate), crate.jsonLdContext(),
				Optional.empty(), List.of(), List.of());
		Optional<SchemaOrgFormFields> vocabulary = chosen instanceof ProfileFormFields p
				? Optional.of(p.getVocabulary())
				: chosen instanceof SchemaOrgFormFields s ? Optional.of(s) : Optional.empty();
		Optional<ProfileFormFields> definitions = vocabulary.flatMap(v -> {
			Profile defined = new ProfileReader().readDefinitions(document);
			return defined.classRules().isEmpty() && defined.propertyRules().isEmpty() ? Optional.empty()
					: Optional.of(new ProfileFormFields(defined, v));
		});
		return new CrateDefinedFields(chosen, document, definitions, crate);
	}

	/** The fields chosen, without the crate's */
	public FormFields getFields() {
		return fields;
	}

	/** The crate's definitions, as a profile, if it has any */
	public Optional<Profile> getDefinitions() {
		return definitions.map(ProfileFormFields::getProfile);
	}

	@Override
	public String getName() {
		return fields.getName();
	}

	@Override
	public List<FormField> fieldsFor(Set<URI> types) {
		return fieldsFor(types, false);
	}

	@Override
	public List<FormField> fieldsFor(Set<URI> types, boolean root) {
		List<FormField> chosen = fields.fieldsFor(types, root);
		if (definitions.isEmpty()) {
			return chosen;
		}
		Map<String, FormField> own = definitions.get().ruleFieldsFor(withSuperclasses(types), root).stream()
				.map(f -> new FormField(f.name(), f.property(), f.label(), f.comment(),
						f.isRequired() ? REQUIRED : DEFINED, f.range(), f.kind(), f.minCount(), f.maxCount(), f.tier()))
				.collect(Collectors.toMap(FormField::name, Function.identity(), (a, b) -> a,
						LinkedHashMap::new));
		List<FormField> merged = new ArrayList<>(chosen.stream()
				.map(f -> f.comment().isPresent() || !own.containsKey(f.name()) ? f
						: new FormField(f.name(), f.property(), f.label(), own.get(f.name()).comment(), f.group(),
								f.range(), f.kind(), f.minCount(), f.maxCount(), f.tier()))
				.toList());
		merged.removeIf(f -> f.tier() == FieldTier.OTHER && own.containsKey(f.name()));
		Set<String> names = merged.stream().map(FormField::name).collect(Collectors.toSet());
		own.values().stream().filter(f -> !names.contains(f.name())).forEach(merged::add);
		// The crate's required and suggested with the others of their tier, after the profile's
		merged.sort(Comparator.comparing(FormField::tier));
		return merged;
	}

	/**
	 * The types with their superclasses, by the vocabulary and the crate's own
	 * <code>rdfs:subClassOf</code>, and Thing, as every entity is one.
	 */
	private Set<URI> withSuperclasses(Set<URI> types) {
		Map<URI, ClassDef> classes = definitions.get().getVocabulary().getTerms().getSchema().getClasses();
		Set<URI> all = new LinkedHashSet<>();
		List<URI> todo = new ArrayList<>(types);
		todo.add(URI.create(S + "Thing"));
		while (!todo.isEmpty()) {
			URI type = todo.remove(todo.size() - 1);
			if (!all.add(type)) {
				continue;
			}
			ClassDef vocabulary = classes.get(type);
			if (vocabulary != null) {
				vocabulary.getSelfAndSuperClasses().stream().map(ClassDef::getID).forEach(todo::add);
			}
			definitions.get().getProfile().classRule(type.toString())
					.ifPresent(r -> r.subClassOf().stream().map(URI::create).forEach(todo::add));
		}
		return all;
	}

	/**
	 * Properties defined nowhere, but used in the crate by entities sharing a
	 * type (as written) with this one, as fields: a guess, with a range
	 * guessed from their values. Not the vocabulary's, the RO-Crate
	 * context's, nor those the crate defines; the caller leaves out those its
	 * other fields have, e.g. by a profile.
	 */
	public List<FormField> guessedFieldsFor(Entity entity, CrateTerms terms) {
		Set<String> types = Set.copyOf(entity.getTypes());
		Set<String> defined = definitions.map(d -> d.getProfile().propertyRules().values().stream()
				.map(PropertyRule::label).collect(Collectors.toSet())).orElse(Set.of());
		Map<String, List<JsonNode>> used = new LinkedHashMap<>();
		Map<String, Set<String>> usedWith = new LinkedHashMap<>();
		Map<String, Integer> users = new LinkedHashMap<>();
		for (Entity other : crate.getEntities()) {
			List<String> shared = other.getTypes().stream().filter(types::contains).toList();
			if (shared.isEmpty()) {
				continue;
			}
			for (String name : other.getValues().keySet()) {
				if (name.startsWith("@") || defined.contains(name) || isKnownProperty(name, terms)) {
					continue;
				}
				used.computeIfAbsent(name, n -> new ArrayList<>()).addAll(other.getAll(name));
				usedWith.computeIfAbsent(name, n -> new TreeSet<>()).addAll(shared);
				users.merge(name, 1, Integer::sum);
			}
		}
		SchemaOrgFormFields vocabulary = new SchemaOrgFormFields(terms);
		List<FormField> guessed = new ArrayList<>();
		for (Map.Entry<String, List<JsonNode>> property : used.entrySet()) {
			String name = property.getKey();
			List<ClassDef> range = guessRange(property.getValue(), terms);
			int count = users.get(name);
			guessed.add(new FormField(name, propertyIri(name), Labels.fromTerm(name),
					Optional.of("Not defined by the vocabulary, the profile nor the crate, but used in the crate by "
							+ count + (count == 1 ? " entity" : " entities") + " of type "
							+ String.join(", ", usedWith.get(name)) + ": a guess, and its kind of values too"),
					GUESSED, range, vocabulary.kindOf(range), 0, OptionalInt.empty(), FieldTier.GUESSED));
		}
		return guessed;
	}

	/** A property schema.org has, or the RO-Crate context names (e.g. <code>conformsTo</code>) */
	private static boolean isKnownProperty(String name, CrateTerms terms) {
		return terms.propertyDef(name).isPresent() || !name.contains(":") && terms.iri(name).isPresent();
	}

	private URI propertyIri(String name) {
		Optional<String> expanded = document.expand(name).filter(Crate::isAbsolute);
		try {
			return URI.create(expanded.orElse(name));
		} catch (IllegalArgumentException e) {
			return URI.create("#" + URLEncoder.encode(name, StandardCharsets.UTF_8));
		}
	}

	/** References if all values are, else numbers, booleans or web addresses if all are, else text */
	private static List<ClassDef> guessRange(List<JsonNode> values, CrateTerms terms) {
		String type;
		if (values.stream().allMatch(v -> v.isObject() && v.has("@id"))) {
			type = "Thing";
		} else if (values.stream().allMatch(JsonNode::isNumber)) {
			type = "Number";
		} else if (values.stream().allMatch(JsonNode::isBoolean)) {
			type = "Boolean";
		} else if (values.stream().allMatch(v -> v.isTextual() && v.asText().matches("https?://\\S+"))) {
			type = "URL";
		} else {
			type = "Text";
		}
		return Optional.ofNullable(terms.getSchema().getClasses().get(URI.create(S + type))).stream().toList();
	}

	/**
	 * Types used in the crate that nothing defines: not schema.org's, the
	 * RO-Crate context's, nor those of the chosen fields (e.g. a profile) or
	 * the crate's definitions; as written, with how many entities have each.
	 */
	public Map<String, Integer> undefinedTypes(CrateTerms terms) {
		Set<String> known = new LinkedHashSet<>();
		terms.contextClasses().forEach(c -> known.add(c.getID().toString()));
		otherTypes().forEach(t -> known.add(t.toString()));
		Map<String, Integer> undefined = new TreeMap<>();
		for (Entity entity : crate.getEntities()) {
			for (String type : entity.getTypes()) {
				Optional<String> iri = terms.iri(type).map(URI::toString)
						.or(() -> document.expand(type).filter(Crate::isAbsolute));
				boolean isKnown = iri.filter(i -> i.startsWith(S) || i.startsWith(RDF) || i.startsWith(RDFS)
						|| known.contains(i) || terms.getSchema().getClasses().containsKey(URI.create(i))).isPresent();
				if (!isKnown) {
					undefined.merge(type, 1, Integer::sum);
				}
			}
		}
		return undefined;
	}

	/**
	 * The name to write for a type, as the crate's own <code>@context</code>
	 * names it (e.g. <code>SiteVisit</code>), else the IRI.
	 */
	public String termFor(URI type) {
		return document.context().compact(type.toString());
	}

	@Override
	public List<FormField> retiredFieldsFor(Set<URI> types) {
		return fields.retiredFieldsFor(types);
	}

	@Override
	public List<URI> requiredTypes(Set<URI> types, boolean root) {
		return fields.requiredTypes(types, root);
	}

	@Override
	public List<URI> otherTypes() {
		Set<URI> others = new LinkedHashSet<>(fields.otherTypes());
		definitions.ifPresent(d -> others.addAll(d.otherTypes()));
		return List.copyOf(others);
	}

	/**
	 * As the RO-Crate context has them, and those it doesn't know as the
	 * crate's own <code>@context</code> does.
	 */
	@Override
	public Set<URI> typesOf(Entity entity, CrateTerms terms) {
		return entity.getTypes().stream()
				.flatMap(t -> terms.iri(t).or(() -> document.expand(t).filter(Crate::isAbsolute).map(URI::create))
						.stream())
				.collect(Collectors.toSet());
	}

	@Override
	public String toString() {
		return getName();
	}
}
