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

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One item of a MASP property rule's <code>rangeIncludes</code>.
 */
public sealed interface RangeItem {

	/**
	 * Values are entities matching another class rule of the profile.
	 */
	record ClassRuleRange(String classRuleId) implements RangeItem {
	}

	/**
	 * An IRI outside the profile's rules: a data type like
	 * <code>http://schema.org/Text</code>, or a vocabulary class.
	 */
	record IriRange(String iri) implements RangeItem {
	}

	/**
	 * A <code>PropertyValue</code> constraint: the value must be the literal
	 * or <code>{"@id": ...}</code> given, or match the regular expression if
	 * the constraint is written like <code>/^readme\.md$/i</code>.
	 *
	 * @param id    The PropertyValue entity
	 * @param name  Its name, e.g. "RO-Crate Metadata Descriptor Identifier Constraint"
	 * @param value The constraint's <code>value</code> as written
	 * @param regex The value as a regular expression, if it is one
	 */
	record ValueConstraint(String id, Optional<String> name, JsonNode value, Optional<Pattern> regex)
			implements RangeItem {
	}

	/**
	 * An <code>ItemList</code> or <code>DefinedTermSet</code>: the value must
	 * be one of the listed <code>@id</code>s (the list's
	 * <code>itemListElement</code>s, or the set's <code>DefinedTerm</code>s).
	 */
	record AllowedValues(String id, Optional<String> name, List<String> ids) implements RangeItem {
	}
}
