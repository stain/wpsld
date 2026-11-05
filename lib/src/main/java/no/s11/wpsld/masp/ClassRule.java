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
 * A MASP class rule (<code>rdfs:Class</code>): a type of entity in crates
 * conforming to the profile.
 *
 * @param id               The rule's <code>@id</code> in the profile crate, e.g. <code>#class_Dataset</code>
 * @param label            Human readable label
 * @param comment          Description in the profile
 * @param specializationOf The vocabulary types the rule specialises
 *                         (<code>prov:specializationOf</code>); entities of
 *                         these types match the rule
 * @param subClassOf       Superclasses (<code>rdfs:subClassOf</code>), e.g.
 *                         for a type outside schema.org
 * @param instances        How many instances a crate must/may have
 */
public record ClassRule(String id, String label, Optional<String> comment, List<String> specializationOf,
		List<String> subClassOf, Cardinality instances) {
}
