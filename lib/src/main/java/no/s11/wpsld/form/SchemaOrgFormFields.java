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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Set;

import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.soss.ClassDef;
import no.s11.wpsld.soss.PropertyDef;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * All schema.org properties of the types and their superclasses, grouped by
 * the class that defines them (most specific first, Thing last), leaving out
 * terms schema.org has superseded. No property is required.
 */
public class SchemaOrgFormFields implements FormFields {
	private static final String S = "http://schema.org/";
	private static final URI S_THING = URI.create(S + "Thing");
	private static final URI S_BOOLEAN = URI.create(S + "Boolean");
	private static final URI S_NUMBER = URI.create(S + "Number");
	private static final URI S_DATE = URI.create(S + "Date");
	private static final URI S_DATE_TIME = URI.create(S + "DateTime");

	private final SchemaFromJsonLd schema;
	private final CrateTerms terms;

	public CrateTerms getTerms() {
		return terms;
	}

	public SchemaOrgFormFields(SchemaFromJsonLd schema) {
		this(new CrateTerms(schema));
	}

	public SchemaOrgFormFields(CrateTerms terms) {
		this.terms = Objects.requireNonNull(terms);
		this.schema = terms.getSchema();
	}

	/**
	 * All properties of the types and their superclasses. Types that are not
	 * schema.org Things (e.g. from a profile's own vocabulary) are taken as
	 * Things, so every entity has Thing's properties, like name.
	 */
	@Override
	public List<FormField> fieldsFor(Set<URI> types) {
		List<FormField> fields = new ArrayList<>();
		Set<URI> seenProperties = new HashSet<>();
		Set<URI> withThing = new LinkedHashSet<>(types);
		boolean anyThing = types.stream().map(schema.getClasses()::get).filter(Objects::nonNull)
				.anyMatch(c -> isA(c, S_THING));
		if (!anyThing) {
			withThing.add(S_THING);
		}
		for (ClassDef classDef : classAndSuperclasses(withThing)) {
			schema.propertiesOf(classDef)
				.filter(p -> !schema.isSuperseded(p))
				.filter(p -> seenProperties.add(p.getID()))
				.map(p -> field(p, classDef))
				.forEach(fields::add);
		}
		return fields;
	}

	/**
	 * The classes and their superclasses, breadth first, so the most
	 * specific come first.
	 */
	private Set<ClassDef> classAndSuperclasses(Set<URI> types) {
		Set<ClassDef> classes = new LinkedHashSet<>();
		Queue<ClassDef> queue = new ArrayDeque<>();
		types.stream().map(schema.getClasses()::get).filter(Objects::nonNull).forEach(queue::add);
		while (!queue.isEmpty()) {
			ClassDef classDef = queue.poll();
			if (classes.add(classDef)) {
				queue.addAll(classDef.getSubClassOf());
			}
		}
		// Breadth first may still visit a shared superclass like Thing before
		// a longer path reaches it; move root classes last
		Set<ClassDef> ordered = new LinkedHashSet<>();
		classes.stream().filter(c -> !c.getSubClassOf().isEmpty()).forEach(ordered::add);
		ordered.addAll(classes);
		return ordered;
	}

	private FormField field(PropertyDef property, ClassDef group) {
		List<ClassDef> range = property.getRangeIncludes().stream()
				.filter(c -> !schema.isSuperseded(c)).toList();
		// Names as written in RO-Crates, e.g. the group File for MediaObject
		String name = terms.name(property);
		return new FormField(name, property.getID(), name, property.getComment(), terms.name(group), range,
				kindOf(range), 0, OptionalInt.empty(), FieldTier.OTHER);
	}

	@Override
	public String getName() {
		return "schema.org (no profile)";
	}

	/**
	 * The kind of values for a property with this range.
	 */
	public ValueKind kindOf(List<ClassDef> range) {
		if (range.isEmpty()) {
			return ValueKind.TEXT;
		}
		List<ClassDef> dataTypes = range.stream().filter(schema::isDataType).toList();
		if (dataTypes.isEmpty()) {
			return ValueKind.REFERENCE;
		}
		if (dataTypes.size() < range.size()) {
			return ValueKind.TEXT_OR_REFERENCE;
		}
		if (dataTypes.stream().allMatch(c -> isA(c, S_BOOLEAN))) {
			return ValueKind.BOOLEAN;
		}
		if (dataTypes.stream().allMatch(c -> isA(c, S_NUMBER))) {
			return ValueKind.NUMBER;
		}
		if (dataTypes.stream().allMatch(c -> isA(c, S_DATE) || isA(c, S_DATE_TIME))) {
			return ValueKind.DATE;
		}
		return ValueKind.TEXT;
	}

	private boolean isA(ClassDef classDef, URI type) {
		return classDef.getSelfAndSuperClasses().stream().anyMatch(c -> c.getID().equals(type));
	}
}
