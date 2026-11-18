/*
 * Copyright 2026 The University of Manchester, UK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package no.s11.wpsld.tui;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.EntityListing;
import no.s11.wpsld.soss.ClassDef;
import no.s11.wpsld.soss.SchemaFromJsonLd;

final class TypeFilter {
	record Type(String key, String name, Optional<ClassDef> definition) {
	}

	record Choice(Type type, int depth) {
	}

	private final CrateDocument document;
	private final CrateTerms terms;
	private final EntityListing listing;

	TypeFilter(CrateDocument document, SchemaFromJsonLd schema) {
		this.document = document;
		terms = new CrateTerms(schema);
		listing = new EntityListing(terms);
	}

	List<Entity> ordered(List<Entity> entities) {
		return entities.stream().sorted(listing.order(document.crate().orElseThrow().getRoot())).toList();
	}

	private Type known(ClassDef definition) {
		return new Type(definition.getID().toString(), terms.name(definition), Optional.of(definition));
	}

	private Type type(String name) {
		Optional<ClassDef> definition = document.expand(name).flatMap(terms::classDef)
				.or(() -> terms.classDef(name));
		return definition.map(this::known).orElseGet(() -> new Type(document.expand(name).orElse(name),
				name, Optional.empty()));
	}

	boolean matches(Entity entity, Type selected) {
		if (selected.name().equals("Thing") && selected.definition().isPresent()) return true;
		return entity.getTypes().stream().map(this::type).anyMatch(t -> t.key().equals(selected.key())
				|| selected.definition().isPresent() && t.definition().isPresent()
						&& t.definition().get().getSelfAndSuperClasses().contains(selected.definition().get()));
	}

	List<Choice> choices(List<Entity> entities, String query) {
		Map<String, Type> types = new LinkedHashMap<>();
		Map<String, Set<String>> children = new HashMap<>();
		Set<String> hasParent = new HashSet<>();
		for (Entity entity : entities) {
			for (String name : entity.getTypes()) {
				Type type = type(name);
				types.putIfAbsent(type.key(), type);
				type.definition().ifPresent(c -> addAncestors(c, types, children, hasParent, new HashSet<>()));
			}
		}
		Collator collator = Collator.getInstance();
		collator.setStrength(Collator.SECONDARY);
		List<Type> sorted = types.values().stream().sorted((a, b) -> {
			int comparison = collator.compare(a.name(), b.name());
			return comparison != 0 ? comparison : a.key().compareTo(b.key());
		}).toList();
		List<Choice> result = new ArrayList<>();
		String lower = query.toLowerCase(Locale.ROOT);
		for (Type root : sorted) {
			if (!hasParent.contains(root.key())) {
				append(root, 0, lower, sorted, children, new HashSet<>(), result);
			}
		}
		return result;
	}

	private void addAncestors(ClassDef child, Map<String, Type> types, Map<String, Set<String>> children,
			Set<String> hasParent, Set<String> visited) {
		if (!visited.add(child.getID().toString())) return;
		for (ClassDef parent : child.getSubClassOf()) {
			Type parentType = known(parent);
			types.putIfAbsent(parentType.key(), parentType);
			children.computeIfAbsent(parentType.key(), ignored -> new HashSet<>()).add(child.getID().toString());
			hasParent.add(child.getID().toString());
			addAncestors(parent, types, children, hasParent, visited);
		}
	}

	private boolean append(Type type, int depth, String query, List<Type> sorted, Map<String, Set<String>> children,
			Set<String> path, List<Choice> result) {
		if (!path.add(type.key())) return false;
		List<Choice> descendants = new ArrayList<>();
		for (Type child : sorted) {
			if (children.getOrDefault(type.key(), Set.of()).contains(child.key())) {
				append(child, depth + 1, query, sorted, children, path, descendants);
			}
		}
		path.remove(type.key());
		if (type.name().toLowerCase(Locale.ROOT).contains(query) || !descendants.isEmpty()) {
			result.add(new Choice(type, depth));
			result.addAll(descendants);
			return true;
		}
		return false;
	}
}
