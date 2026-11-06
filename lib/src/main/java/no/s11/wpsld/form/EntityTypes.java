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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.soss.ClassDef;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Rules for changing the schema.org <code>@type</code>s of an entity, given
 * as compacted type names like <code>Dataset</code>.
 * <ul>
 * <li>Adding a subclass of existing types replaces them (Thing becomes
 * Person), as the subclass implies them.</li>
 * <li>Adding a superclass of existing types is redundant; the user can add it
 * anyway or replace those types with it, see {@link #implying}.</li>
 * <li>Unrelated types are kept side by side.</li>
 * <li>Removing the last type falls back to Thing.</li>
 * </ul>
 */
public class EntityTypes {
	public static final String THING = "Thing";

	private final CrateTerms terms;

	public EntityTypes(SchemaFromJsonLd schema) {
		this(new CrateTerms(schema));
	}

	public EntityTypes(CrateTerms terms) {
		this.terms = terms;
	}

	private Optional<ClassDef> classDef(String typeName) {
		return terms.classDef(typeName);
	}

	private boolean isSubClass(String type, ClassDef superClass) {
		return classDef(type).map(c -> !c.equals(superClass) && c.getSelfAndSuperClasses().contains(superClass))
				.orElse(false);
	}

	/**
	 * Whether an entity with these types is a kind of the given class, i.e.
	 * one of its types is the class or a subclass of it. Used to suggest
	 * only fitting entities for a property's range.
	 */
	public boolean isA(List<String> types, ClassDef classDef) {
		return types.stream().anyMatch(t -> classDef(t).map(c -> c.getSelfAndSuperClasses().contains(classDef))
				.orElse(false));
	}

	/**
	 * The existing types that are subclasses of the type to add, and so
	 * already imply it.
	 */
	public List<String> implying(List<String> types, ClassDef added) {
		return types.stream().filter(t -> isSubClass(t, added)).toList();
	}

	/**
	 * Add a type, replacing existing types that are its superclasses. If
	 * existing types already imply it (see {@link #implying}), it is added
	 * anyway alongside them.
	 */
	public List<String> add(List<String> types, ClassDef added) {
		String name = terms.name(added);
		if (types.contains(name)) {
			return types;
		}
		List<String> result = new ArrayList<>(types);
		result.removeIf(t -> classDef(t).map(c -> added.getSelfAndSuperClasses().contains(c)).orElse(false));
		result.add(name);
		return List.copyOf(result);
	}

	/**
	 * Add a type in place of the existing types that imply it, e.g. replace
	 * Dataset and SoftwareSourceCode with CreativeWork.
	 */
	public List<String> replaceImplying(List<String> types, ClassDef added) {
		List<String> result = new ArrayList<>(types);
		result.removeAll(implying(types, added));
		return add(result, added);
	}

	/**
	 * Leave out types implied by others (superclasses of other types), e.g.
	 * after merging entities: DataDownload and Thing become DataDownload.
	 */
	public List<String> simplify(List<String> types) {
		List<String> result = types.stream().filter(t -> classDef(t)
				.map(c -> types.stream().noneMatch(other -> isSubClass(other, c))).orElse(true)).toList();
		return result.isEmpty() ? types : result;
	}

	/**
	 * Remove a type; removing the last one falls back to Thing.
	 */
	public List<String> remove(List<String> types, String type) {
		List<String> result = new ArrayList<>(types);
		result.remove(type);
		return result.isEmpty() ? List.of(THING) : List.copyOf(result);
	}

	/**
	 * Whether a type can be removed, which is not the case only for a
	 * single Thing, as it would come back.
	 */
	public boolean canRemove(List<String> types, String type) {
		return !(types.size() == 1 && types.get(0).equals(THING) && type.equals(THING));
	}
}
