/*
 * Copyright 2026 The University of Manchester, UK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package no.s11.wpsld.tui;

import java.util.List;
import java.util.Map;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.form.CrateCheck;
import no.s11.wpsld.form.CrateProfiles;
import no.s11.wpsld.lookup.CrateRepair;
import no.s11.wpsld.lookup.CrateRepair.Repaired;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Check and Repair Crate on the crate being edited, with the same code as
 * the editor and wpsld-cli. Nothing is fetched from the web: profiles are
 * those bundled or kept by the editor.
 */
final class Maintenance {
	private final CrateSession session;
	private final SchemaFromJsonLd schema;
	private CrateTerms terms;
	private ProfileCache profiles;

	Maintenance(CrateSession session, SchemaFromJsonLd schema) {
		this.session = session;
		this.schema = schema;
	}

	private CrateTerms terms() {
		if (terms == null) {
			terms = new CrateTerms(schema);
		}
		return terms;
	}

	private ProfileCache profiles() {
		if (profiles == null) {
			profiles = ProfileCache.forUser();
		}
		return profiles;
	}

	/** The crate's problems and warnings as it is now, with its unsaved changes */
	CrateCheck check() {
		CrateDocument document = session.document();
		CrateProfiles found = document.crate().isPresent() ? CrateProfiles.of(document, profiles(), false)
				: new CrateProfiles(List.of(), List.of(), Map.of());
		return CrateCheck.of(document, found, terms(), session.folder());
	}

	/** What Repair Crate would do, on a copy: the crate is not changed */
	List<Repaired> preview() {
		Crate crate = session.document().crate().orElseThrow();
		Crate copy = new CrateReader().read(crate.toJson(), "repair").crate().orElseThrow();
		return CrateRepair.repair(copy, terms());
	}

	/** Repair the crate */
	List<Repaired> repair() {
		return CrateRepair.repair(session.document().crate().orElseThrow(), terms());
	}
}
