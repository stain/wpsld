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

import java.util.OptionalInt;

/**
 * <code>sh:minCount</code> and <code>sh:maxCount</code> of a MASP rule: on a
 * class rule, how many instances of the type a crate must/may have; on a
 * property rule, how many values each matching entity must/may have.
 */
public record Cardinality(OptionalInt min, OptionalInt max) {
	public static final Cardinality ANY = new Cardinality(OptionalInt.empty(), OptionalInt.empty());

	public boolean isRequired() {
		return min.orElse(0) > 0;
	}

	@Override
	public String toString() {
		return min.orElse(0) + ".." + (max.isPresent() ? String.valueOf(max.getAsInt()) : "*");
	}
}
