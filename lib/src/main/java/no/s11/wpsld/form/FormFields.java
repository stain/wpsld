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
import java.util.List;
import java.util.Set;

/**
 * Decides which fields an editor shows for an entity.
 * <p>
 * {@link SchemaOrgFormFields} offers every schema.org property of the types;
 * a profile (e.g. RO-Crate MASP, see MASP.md) can narrow that down to the
 * properties it specifies, with required ones and cardinalities.
 */
public interface FormFields {

	/**
	 * Fields for an entity with the given <code>@type</code>s, in the order
	 * they should be shown.
	 */
	List<FormField> fieldsFor(Set<URI> types);

	/**
	 * Fields for an entity, knowing whether it is the crate's root data
	 * entity: a profile's rules for the root only apply to it, not to other
	 * entities of the same type (e.g. a folder's Dataset).
	 */
	default List<FormField> fieldsFor(Set<URI> types, boolean root) {
		return fieldsFor(types);
	}

	/**
	 * Fields of properties the vocabulary has retired (e.g. schema.org's
	 * superseded <code>fileFormat</code>) for an entity with the given
	 * <code>@type</code>s, left out of {@link #fieldsFor(Set)}: for values
	 * already using them, or for adding one on purpose; none by default. A
	 * field of the same name from {@link #fieldsFor(Set, boolean)}, e.g. as a
	 * profile names the property, takes precedence.
	 */
	default List<FormField> retiredFieldsFor(Set<URI> types) {
		return List.of();
	}

	/**
	 * Types the entity must have (or a subtype of one), e.g. Dataset for the
	 * root data entity by a profile; none by default.
	 */
	default List<URI> requiredTypes(Set<URI> types, boolean root) {
		return List.of();
	}

	/**
	 * Types outside the vocabulary that this choice of fields names, e.g. a
	 * profile's <code>pcdm:Collection</code>, to offer when adding a type;
	 * none by default.
	 */
	default List<URI> otherTypes() {
		return List.of();
	}

	/**
	 * A name to show for this choice of fields, e.g. in a drop-down.
	 */
	String getName();
}
