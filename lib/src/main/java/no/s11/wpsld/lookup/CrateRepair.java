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
package no.s11.wpsld.lookup;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.crate.TermRepairs;

/**
 * Repair Crate, as the editor's Crate menu and <code>wpsld-cli repair</code>
 * do it: objects written in values without an <code>@id</code> made entities
 * of their own ({@link Crate#flattenNested()}); types written as IRIs given
 * the names the RO-Crate context gives them, and members of Enumerations
 * written as text made references to them ({@link TermRepairs}); SPDX
 * licences, PRONOM formats, ORCID iDs, ROR identifiers and DOIs written
 * otherwise made their usual IRIs ({@link SPDXLicenses#repair},
 * {@link PRONOMFormats#repair}, {@link ORCID#repair}, {@link ROR#repair},
 * {@link DOI#repair}); then a Thing added for each reference to an entity
 * not in the crate ({@link Crate#addReferenced()}), entities linked
 * otherwise no longer mentioned by the root
 * ({@link Crate#unmentionLinked()}), and those not linked mentioned
 * ({@link Crate#mentionUnlinked()}). All offline, from what wpsld bundles.
 */
public final class CrateRepair {
	/**
	 * What one kind of repair did
	 *
	 * @param heading what was repaired, e.g. "DOIs"
	 * @param items   each change, or each entity changed
	 * @param advice  what to do next, if anything
	 */
	public record Repaired(String heading, List<String> items, Optional<String> advice) {
		public Repaired {
			items = List.copyOf(items);
		}

		/** As the editor's report shows it: the heading, each item, and the advice */
		@Override
		public String toString() {
			return heading + ":\n" + items.stream().map(i -> "• " + i).collect(Collectors.joining("\n"))
					+ advice.map(a -> "\n" + a).orElse("");
		}
	}

	private CrateRepair() {
	}

	/**
	 * Repair the crate in place
	 *
	 * @return What was repaired, by kind, in the order done; empty if nothing
	 */
	public static List<Repaired> repair(Crate crate, CrateTerms terms) {
		List<Entity> flattened = crate.flattenNested();
		List<String> types = TermRepairs.typeNames(crate, terms);
		List<String> members = TermRepairs.enumerationMembers(crate, terms);
		List<String> licences = SPDXLicenses.bundled().repair(crate);
		List<String> formats = PRONOMFormats.bundled().repair(crate);
		List<String> people = ORCID.repair(crate);
		List<String> organizations = ROR.repair(crate);
		List<String> dois = DOI.repair(crate);
		List<Entity> added = crate.addReferenced();
		List<Entity> unmentioned = crate.unmentionLinked();
		List<Entity> mentioned = crate.mentionUnlinked();
		List<Repaired> done = new ArrayList<>();
		add(done, "Written in values without an @id, which flattened RO-Crate JSON-LD doesn't allow, now"
				+ " entities of their own", described(flattened), Optional.empty());
		add(done, "Types written by their names", types, Optional.empty());
		add(done, "Values of schema.org's lists (Enumerations), as references to them", members, Optional.empty());
		add(done, "Licences of the SPDX License List", licences, Optional.empty());
		add(done, "DOIs", dois, Optional.empty());
		add(done, "ORCID iDs", people, Optional.empty());
		add(done, "ROR identifiers", organizations, Optional.empty());
		add(done, "File formats of PRONOM", formats, Optional.empty());
		add(done, "Added as Thing, referred to but not in the crate", described(added),
				Optional.of("Give them a name and type, or if an @id is mistyped, change it to the one meant"
						+ " to merge them."));
		add(done, "No longer mentioned by the root, as linked otherwise", described(unmentioned), Optional.empty());
		add(done, "Mentioned by the root, as not linked otherwise", described(mentioned), Optional.empty());
		return done;
	}

	private static void add(List<Repaired> done, String heading, List<String> items, Optional<String> advice) {
		if (!items.isEmpty()) {
			done.add(new Repaired(heading, items, advice));
		}
	}

	/** Entities by their names and @ids, or @ids alone where they have no names */
	public static List<String> described(List<Entity> entities) {
		return entities.stream()
				.map(e -> e.getLabel().equals(e.getId()) ? e.getId() : e.getLabel() + " (" + e.getId() + ")").toList();
	}
}
