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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.masp.Profile;
import no.s11.wpsld.masp.ProfileReader;

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
 * replace the vocabulary's.
 */
public class CrateDefinedFields implements FormFields {
	public static final String REQUIRED = "Required by the crate";
	public static final String DEFINED = "Defined in the crate";

	private final FormFields fields;
	private final CrateDocument document;
	private final Optional<ProfileFormFields> definitions;

	private CrateDefinedFields(FormFields fields, CrateDocument document, Optional<ProfileFormFields> definitions) {
		this.fields = fields;
		this.document = document;
		this.definitions = definitions;
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
		return new CrateDefinedFields(chosen, document, definitions);
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
		Map<String, FormField> own = definitions.get().ruleFieldsFor(types, root).stream()
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
