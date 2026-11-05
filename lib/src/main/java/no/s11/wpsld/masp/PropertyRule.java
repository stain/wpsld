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

/**
 * A MASP property rule (<code>rdf:Property</code>): a property of the
 * entities matching its domain class rules.
 *
 * @param id               The rule's <code>@id</code> in the profile crate
 * @param label            The property as written in crates (its
 *                         <code>rdfs:label</code>), e.g. <code>author</code>,
 *                         or <code>@id</code> for a constraint on identifiers
 * @param comment          Description in the profile
 * @param specializationOf The vocabulary properties the rule specialises
 * @param domain           Ids of the class rules it applies to
 * @param range            What the values may be
 * @param values           How many values each matching entity must/may have
 */
public record PropertyRule(String id, String label, Optional<String> comment, List<String> specializationOf,
		List<String> domain, List<RangeItem> range, Cardinality values) {

	public boolean isRequired() {
		return values.isRequired();
	}
}
