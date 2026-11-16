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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateChecks;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateFolder;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Issue;
import no.s11.wpsld.crate.Issue.Severity;

/**
 * A crate checked as the editor checks it before saving, as
 * <code>wpsld-cli check</code> reports it (see {@link Issue}): the problems
 * reading it (errors REQUIRED, warnings RECOMMENDED); RO-Crate's own
 * requirements of the root data entity ({@link CrateChecks}); the
 * properties its profiles require (see {@link CrateProfiles} and
 * {@link RequiredFields}); for an attached crate, its files and folders
 * ({@link CrateFolder#issues}), and, RECOMMENDED, the files in its folder
 * that it doesn't describe. An issue worded as one before it is left out,
 * e.g. a profile's that RO-Crate requires too. Not a full validator: see
 * rocrate-validator for that.
 *
 * @param read     Whether the crate could be read
 * @param profiles The profiles checked, and those not found
 * @param issues   What the crate lacks or has wrong
 */
public record CrateCheck(boolean read, CrateProfiles profiles, List<Issue> issues) {
	/** How many of the folder's files to look at, at most, as the editor's Entities by Folder */
	public static final int FILES = 10_000;

	public CrateCheck {
		issues = List.copyOf(issues);
	}

	/**
	 * Check a crate as read
	 *
	 * @param folder The folder of an attached crate, whose files to check
	 */
	public static CrateCheck of(CrateDocument document, CrateProfiles profiles, CrateTerms terms,
			Optional<Path> folder) {
		Stream<Issue> reading = document.problems().stream()
				.map(p -> new Issue(p.severity() == CrateDocument.Severity.ERROR ? Severity.REQUIRED
						: Severity.RECOMMENDED, "wpsld:reading", "Reading the crate's JSON-LD", Issue.RO_CRATE,
						p.message(), Optional.empty(), Optional.empty(), Optional.empty()));
		if (document.crate().isEmpty()) {
			return new CrateCheck(false, profiles, reading.toList());
		}
		Crate crate = document.crate().get();
		SchemaOrgFormFields schemaOrg = new SchemaOrgFormFields(terms);
		Stream<Issue> undescribed = folder.map(f -> CrateFolder.match(crate, f, FILES).undescribed())
				.orElse(List.of()).stream()
				.map(id -> new Issue(Severity.RECOMMENDED, "rocrate:file-undescribed",
						"Attached crate: the files in its folder described", Issue.RO_CRATE,
						id + " is in the crate's folder, but not in the crate", Optional.empty(), Optional.empty(),
						Optional.of(id)));
		Set<String> said = new HashSet<>();
		List<Issue> issues = Stream.of(reading, CrateChecks.issues(crate).stream(),
				profiles.matched().stream()
						.flatMap(p -> RequiredFields.issues(crate, new ProfileFormFields(p, schemaOrg), terms).stream()),
				folder.map(f -> CrateFolder.issues(crate, f)).orElse(List.of()).stream(), undescribed)
				.flatMap(s -> s).filter(i -> said.add(i.message())).toList();
		return new CrateCheck(true, profiles, issues);
	}

	/** Whether it could be read and has no REQUIRED issues; others (warnings) don't count */
	public boolean passed() {
		return read && issues.stream().noneMatch(Issue::isRequired);
	}
}
