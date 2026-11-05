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
package no.s11.wpsld.masp;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateDocument.Problem;
import no.s11.wpsld.crate.CrateDocument.Severity;

/**
 * An RO-Crate MASP profile (or schema) read from its profile crate: its class
 * and property rules, see MASP.md.
 *
 * @param id               The profile's root data entity
 * @param identifiers      The identifiers crates use in <code>conformsTo</code>
 *                         to say they conform to this profile: its conformance
 *                         indicators, and its root's <code>@id</code> if absolute
 * @param name             Its name
 * @param version          Its version, if given
 * @param isProfileOf      What it is a profile of, e.g. an RO-Crate version
 * @param classRules       Class rules by id, in the order of the profile crate
 * @param propertyRules    Property rules by id, in the order of the profile crate
 * @param descriptorRule   The class rule for the metadata descriptor, if found
 * @param rootRule         The class rule for the root data entity, if found
 * @param document         The profile crate as read
 * @param problems         Problems found reading the rules (see also the document's)
 */
public record Profile(String id, List<String> identifiers, Optional<String> name, Optional<String> version, List<String> isProfileOf,
		Map<String, ClassRule> classRules, Map<String, PropertyRule> propertyRules, Optional<String> descriptorRule,
		Optional<String> rootRule, CrateDocument document, List<Problem> problems) {

	public Profile {
		identifiers = List.copyOf(identifiers);
		isProfileOf = List.copyOf(isProfileOf);
		problems = List.copyOf(problems);
	}

	public boolean hasErrors() {
		return problems.stream().anyMatch(p -> p.severity() == Severity.ERROR) || document.hasErrors();
	}

	/**
	 * The problems reading the profile crate and its rules, errors first.
	 */
	public List<Problem> allProblems() {
		return Stream.concat(document.problems().stream(), problems.stream())
				.sorted(Comparator.comparing(Problem::severity)).toList();
	}

	public Optional<ClassRule> classRule(String id) {
		return Optional.ofNullable(classRules.get(id));
	}

	/**
	 * The property rules of a class rule: required first, then the others,
	 * each by label.
	 */
	public List<PropertyRule> propertiesOf(String classRuleId) {
		return propertyRules.values().stream().filter(p -> p.domain().contains(classRuleId))
				.sorted(Comparator.comparing((PropertyRule p) -> !p.isRequired()).thenComparing(PropertyRule::label))
				.toList();
	}

	/**
	 * The class rules for an entity with these types (expanded IRIs): those
	 * specialising one of the types.
	 */
	public List<ClassRule> classRulesFor(Set<String> typeIris) {
		Set<String> types = Set.copyOf(typeIris.stream().map(CrateDocument::normalise).toList());
		return classRules.values().stream().filter(c -> c.specializationOf().stream().anyMatch(types::contains))
				.toList();
	}
}
