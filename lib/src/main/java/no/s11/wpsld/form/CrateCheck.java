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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import no.s11.wpsld.crate.CrateChecks;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateDocument.Problem;
import no.s11.wpsld.crate.CrateDocument.Severity;
import no.s11.wpsld.crate.CrateFolder;
import no.s11.wpsld.crate.CrateTerms;

/**
 * A crate checked as the editor checks it before saving, as
 * <code>wpsld-cli check</code> reports it: the problems reading it; the
 * properties its profiles require (see {@link CrateProfiles} and
 * {@link RequiredFields}); RO-Crate's own requirements of the root data
 * entity ({@link CrateChecks}); and for an attached crate, its files and
 * folders ({@link CrateFolder#problems}), and as warnings, the files in
 * its folder that it doesn't describe. Not a full validator: see
 * rocrate-validator for that.
 *
 * @param reading  The problems reading the crate, errors first
 * @param profiles The profiles checked, and those not found
 * @param problems What the crate lacks or has wrong, each once
 * @param warnings What may be meant otherwise, but is allowed: the files in
 *                 an attached crate's folder that it doesn't describe (at
 *                 most {@link #FILES} of them)
 */
public record CrateCheck(List<Problem> reading, CrateProfiles profiles, List<String> problems,
		List<String> warnings) {
	/** How many of the folder's files to look at, at most, as the editor's Entities by Folder */
	public static final int FILES = 10_000;

	public CrateCheck {
		reading = List.copyOf(reading);
		problems = List.copyOf(problems);
		warnings = List.copyOf(warnings);
	}

	/**
	 * Check a crate as read
	 *
	 * @param folder The folder of an attached crate, whose files to check
	 */
	public static CrateCheck of(CrateDocument document, CrateProfiles profiles, CrateTerms terms,
			Optional<Path> folder) {
		List<Problem> reading = document.problems().stream()
				.sorted((a, b) -> a.severity().compareTo(b.severity())).toList();
		if (document.crate().isEmpty()) {
			return new CrateCheck(reading, profiles, List.of(), List.of());
		}
		var crate = document.crate().get();
		SchemaOrgFormFields schemaOrg = new SchemaOrgFormFields(terms);
		// Each profile's required properties, then RO-Crate's own checks
		// (worded alike, so each problem only once)
		List<String> problems = Stream.of(
				profiles.matched().stream()
						.flatMap(p -> RequiredFields.check(crate, new ProfileFormFields(p, schemaOrg), terms).stream()),
				CrateChecks.check(crate).stream(),
				folder.map(f -> CrateFolder.problems(crate, f)).orElse(List.of()).stream())
				.flatMap(s -> s).distinct().toList();
		List<String> warnings = folder.map(f -> CrateFolder.match(crate, f, FILES).undescribed()).orElse(List.of())
				.stream().map(id -> id + " is in the crate's folder, but not in the crate").toList();
		return new CrateCheck(reading, profiles, problems, warnings);
	}

	/** Whether it could be read, without errors, and has no problems; warnings don't count */
	public boolean passed() {
		return reading.stream().noneMatch(p -> p.severity() == Severity.ERROR) && problems.isEmpty();
	}
}
