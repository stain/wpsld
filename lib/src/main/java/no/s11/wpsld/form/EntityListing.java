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

import java.text.Collator;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.soss.ClassDef;

/**
 * The entities of a crate as listed to choose from: the root data entity
 * first, then by their types, names and <code>@id</code>s; and the types to
 * show only entities of, with their subtypes.
 */
public class EntityListing {
	/** A type to list entities of, and how many are of it or its subtypes */
	public record TypeCount(String type, int count) {
		@Override
		public String toString() {
			return type + " (" + count + ")";
		}
	}

	private final CrateTerms terms;
	/** The classes each type name is a kind of, itself included */
	private final Map<String, Set<ClassDef>> kinds = new HashMap<>();

	public EntityListing(CrateTerms terms) {
		this.terms = terms;
	}

	/**
	 * The root data entity first; then by types (as listed, e.g.
	 * <code>File, SoftwareSourceCode</code> after <code>File</code>), name
	 * (else <code>@id</code>, as shown) and <code>@id</code>, ignoring case
	 * and as sorted in the user's language.
	 */
	public static Comparator<Entity> order(Entity root) {
		Collator collator = Collator.getInstance();
		collator.setStrength(Collator.SECONDARY);
		return Comparator.comparing((Entity e) -> e != root)
				.thenComparing(e -> String.join(", ", e.getTypes()), collator)
				.thenComparing(Entity::getLabel, collator).thenComparing(Entity::getId);
	}

	/**
	 * The crate's entities in {@link #order}, only those of a type (or its
	 * subtypes), all if it is empty.
	 */
	public List<Entity> list(Crate crate, String type) {
		return crate.getEntities().stream().filter(e -> type.isEmpty() || isA(e, type))
				.sorted(order(crate.getRoot())).toList();
	}

	/**
	 * Whether an entity is of a type: has it, or a subtype of it, e.g. a
	 * File is a CreativeWork.
	 */
	public boolean isA(Entity entity, String type) {
		if (entity.getTypes().contains(type)) {
			return true;
		}
		return terms.classDef(type).map(c -> entity.getTypes().stream().anyMatch(t -> kinds(t).contains(c)))
				.orElse(false);
	}

	private Set<ClassDef> kinds(String type) {
		return kinds.computeIfAbsent(type,
				t -> terms.classDef(t).map(ClassDef::getSelfAndSuperClasses).orElse(Set.of()));
	}

	/**
	 * The types to list entities of: those the entities have, and the types
	 * that two or more of those are kinds of, e.g. CreativeWork for Dataset
	 * and File, though not Thing; by name, with how many entities are of
	 * each.
	 */
	public List<TypeCount> types(Collection<Entity> entities) {
		Set<String> had = entities.stream().flatMap(e -> e.getTypes().stream())
				.collect(Collectors.toCollection(LinkedHashSet::new));
		Map<String, Integer> shared = new HashMap<>();
		for (String type : had) {
			kinds(type).stream().map(terms::name).filter(s -> !s.equals(type) && !s.equals(EntityTypes.THING))
					.distinct().forEach(s -> shared.merge(s, 1, Integer::sum));
		}
		Set<String> listed = new LinkedHashSet<>(had);
		shared.forEach((type, kinds) -> {
			if (kinds > 1) {
				listed.add(type);
			}
		});
		Collator collator = Collator.getInstance();
		collator.setStrength(Collator.SECONDARY);
		return listed.stream().sorted(collator)
				.map(type -> new TypeCount(type, (int) entities.stream().filter(e -> isA(e, type)).count()))
				.toList();
	}
}
