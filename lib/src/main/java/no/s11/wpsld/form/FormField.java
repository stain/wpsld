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
import java.util.Optional;
import java.util.OptionalInt;

import no.s11.wpsld.soss.ClassDef;

/**
 * A property an entity may (or must) have, as presented by an editor.
 *
 * @param name     JSON key of the property in RO-Crate metadata, e.g. <code>author</code>
 * @param property IRI of the property, e.g. <code>http://schema.org/author</code>
 * @param label    Human readable label
 * @param comment  Description of the property
 * @param group    Name of the group the field is shown in, e.g. the class defining it
 * @param range    Expected types of values
 * @param kind     Kind of values, derived from the range
 * @param minCount Minimum number of values, more than 0 if required
 * @param maxCount Maximum number of values, if limited
 */
public record FormField(String name, URI property, String label, Optional<String> comment, String group,
		List<ClassDef> range, ValueKind kind, int minCount, OptionalInt maxCount) {

	public boolean isRequired() {
		return minCount > 0;
	}
}
