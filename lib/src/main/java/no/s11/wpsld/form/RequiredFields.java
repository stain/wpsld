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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.soss.ClassDef;

/**
 * Checks that the crate's entities have the values their form fields
 * require, e.g. by a profile, and no more than they allow.
 */
public class RequiredFields {

	/**
	 * Problems, worded like {@link no.s11.wpsld.crate.CrateChecks} so the two
	 * lists can be combined without repeating the same problem.
	 */
	public static List<String> check(Crate crate, FormFields fields, CrateTerms terms) {
		List<String> problems = new ArrayList<>();
		for (Entity entity : crate.getEntities()) {
			boolean root = entity == crate.getRoot();
			String who = root ? "The root data entity" : entity.getLabel();
			Set<URI> types = entity.getTypes().stream().map(terms::classDef).flatMap(Optional::stream)
					.map(ClassDef::getID).collect(Collectors.toSet());
			for (FormField field : fields.fieldsFor(types, root)) {
				int values = entity.getAll(field.name()).size();
				if (values < field.minCount()) {
					problems.add(who + " must have " + (field.minCount() == 1 ? "a " + field.name()
							: field.minCount() + " values of " + field.name()));
				} else if (field.maxCount().isPresent() && values > field.maxCount().getAsInt()) {
					problems.add(who + " may have at most " + field.maxCount().getAsInt() + " " + field.name());
				}
			}
		}
		return problems;
	}
}
