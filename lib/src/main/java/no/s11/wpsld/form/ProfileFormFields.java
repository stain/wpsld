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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.masp.ClassRule;
import no.s11.wpsld.masp.Profile;
import no.s11.wpsld.masp.PropertyRule;
import no.s11.wpsld.masp.RangeItem.ClassRuleRange;
import no.s11.wpsld.masp.RangeItem.IriRange;
import no.s11.wpsld.soss.ClassDef;
import no.s11.wpsld.soss.PropertyDef;

/**
 * Form fields from a MASP profile, in three tiers (see MASP.md): the
 * properties the matching class rules require, those they name but don't
 * require, then the other schema.org properties of the type, as
 * {@link SchemaOrgFormFields}.
 * <p>
 * The profile's rule for the root data entity applies to the root only, as
 * RO-Crate types don't tell the root from other entities of its type (both
 * are Datasets). Rules for other entities match by their
 * <code>prov:specializationOf</code> types.
 */
public class ProfileFormFields implements FormFields {
	public static final String REQUIRED = "Required";
	public static final String SUGGESTED = "Suggested";

	private final Profile profile;
	private final SchemaOrgFormFields vocabulary;
	private final CrateTerms terms;

	public ProfileFormFields(Profile profile, SchemaOrgFormFields vocabulary) {
		this.profile = Objects.requireNonNull(profile);
		this.vocabulary = Objects.requireNonNull(vocabulary);
		this.terms = vocabulary.getTerms();
	}

	public Profile getProfile() {
		return profile;
	}

	@Override
	public String getName() {
		return profile.name().orElse(profile.id()) + profile.version().map(v -> " " + v).orElse("");
	}

	@Override
	public List<FormField> fieldsFor(Set<URI> types) {
		return fieldsFor(types, false);
	}

	@Override
	public List<FormField> retiredFieldsFor(Set<URI> types) {
		return vocabulary.retiredFieldsFor(types);
	}

	@Override
	public List<FormField> fieldsFor(Set<URI> types, boolean root) {
		List<FormField> fields = new ArrayList<>();
		Set<String> names = new LinkedHashSet<>();
		for (ClassRule rule : rulesFor(types, root)) {
			for (PropertyRule property : profile.propertiesOf(rule.id())) {
				// Constraints on @id and the like are not properties to fill in
				if (!property.label().startsWith("@") && names.add(property.label())) {
					fields.add(field(property));
				}
			}
		}
		// Required before suggested, whichever rule they come from
		fields.sort((a, b) -> a.tier().compareTo(b.tier()));
		vocabulary.fieldsFor(types).stream().filter(f -> !names.contains(f.name())).forEach(fields::add);
		return fields;
	}

	/**
	 * For the root data entity, the types its rule specialises (the core
	 * RO-Crate profile: Dataset).
	 */
	@Override
	public List<URI> requiredTypes(Set<URI> types, boolean root) {
		if (!root) {
			return List.of();
		}
		return profile.rootRule().flatMap(profile::classRule).stream()
				.flatMap(r -> r.specializationOf().stream()).map(URI::create).toList();
	}

	private List<ClassRule> rulesFor(Set<URI> types, boolean root) {
		if (root && profile.rootRule().isPresent()) {
			return profile.rootRule().flatMap(profile::classRule).stream().toList();
		}
		Set<String> special = new LinkedHashSet<>();
		profile.rootRule().ifPresent(special::add);
		profile.descriptorRule().ifPresent(special::add);
		return profile.classRulesFor(types.stream().map(URI::toString).collect(Collectors.toSet())).stream()
				.filter(r -> !special.contains(r.id())).toList();
	}

	private FormField field(PropertyRule rule) {
		Optional<PropertyDef> property = rule.specializationOf().stream().map(URI::create)
				.map(terms.getSchema().getProperties()::get).filter(Objects::nonNull).findFirst()
				.or(() -> terms.propertyDef(rule.label()));
		List<ClassDef> range = range(rule);
		if (range.isEmpty()) {
			// Only constraints, or nothing given: as the vocabulary has it
			range = property.map(PropertyDef::getRangeIncludes).orElse(List.of());
		}
		URI iri = property.map(PropertyDef::getID)
				.orElseGet(() -> URI.create(rule.specializationOf().stream().findFirst().orElse(rule.id())));
		FieldTier tier = rule.isRequired() ? FieldTier.REQUIRED : FieldTier.SUGGESTED;
		return new FormField(rule.label(), iri, rule.name().orElseGet(() -> Labels.fromTerm(rule.label())),
				rule.comment().or(() -> property.flatMap(PropertyDef::getComment)),
				tier == FieldTier.REQUIRED ? REQUIRED : SUGGESTED, range, vocabulary.kindOf(range),
				rule.values().min().orElse(0), rule.values().max(), tier);
	}

	/**
	 * The vocabulary classes of a rule's range: class rules by what they
	 * specialise, and IRIs that are schema.org classes or data types.
	 */
	private List<ClassDef> range(PropertyRule rule) {
		List<ClassDef> range = new ArrayList<>();
		rule.range().forEach(item -> {
			if (item instanceof ClassRuleRange c) {
				profile.classRule(c.classRuleId()).ifPresent(r -> r.specializationOf().stream().map(URI::create)
						.map(terms.getSchema().getClasses()::get).filter(Objects::nonNull).forEach(range::add));
			} else if (item instanceof IriRange i) {
				Optional.ofNullable(terms.getSchema().getClasses().get(URI.create(i.iri()))).ifPresent(range::add);
			}
		});
		return range.stream().distinct().toList();
	}

	@Override
	public String toString() {
		return getName();
	}
}
