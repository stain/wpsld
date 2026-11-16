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
import no.s11.wpsld.crate.Issue;
import no.s11.wpsld.crate.Issue.Severity;

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
		return issues(crate, fields, terms).stream().map(Issue::message).toList();
	}

	/**
	 * The problems of {@link #check}, with where they are and what they are
	 * of: the profile of the fields, else schema.org (see {@link Issue})
	 */
	public static List<Issue> issues(Crate crate, FormFields fields, CrateTerms terms) {
		Issue.Source source = fields instanceof ProfileFormFields p
				? new Issue.Source(p.getProfile().identifiers().stream().findFirst().orElse(p.getProfile().id()),
						p.getProfile().name().orElse(fields.getName()))
				: new Issue.Source("https://schema.org/", fields.getName());
		List<Issue> issues = new ArrayList<>();
		for (Entity entity : crate.getEntities()) {
			boolean root = entity == crate.getRoot();
			String who = root ? "The root data entity" : entity.getLabel();
			Set<URI> types = entity.getTypes().stream().map(terms::iri).flatMap(Optional::stream)
					.collect(Collectors.toSet());
			for (FormField field : fields.fieldsFor(types, root)) {
				int values = entity.getAll(field.name()).size();
				if (values < field.minCount()) {
					issues.add(new Issue(Severity.REQUIRED, "profile:min-count", field.label() + ": at least "
							+ field.minCount(), source, who + " must have " + (field.minCount() == 1
									? "a " + field.name()
									: field.minCount() + " values of " + field.name()),
							Optional.of(entity.getId()), Optional.of(field.name()), Optional.empty()));
				} else if (field.maxCount().isPresent() && values > field.maxCount().getAsInt()) {
					issues.add(new Issue(Severity.REQUIRED, "profile:max-count", field.label() + ": at most "
							+ field.maxCount().getAsInt(), source, who + " may have at most "
									+ field.maxCount().getAsInt() + " " + field.name(),
							Optional.of(entity.getId()), Optional.of(field.name()), Optional.empty()));
				}
			}
		}
		return issues;
	}
}
