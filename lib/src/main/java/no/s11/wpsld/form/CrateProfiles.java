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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.masp.Profile;
import no.s11.wpsld.masp.ProfileCache;

/**
 * The profiles a crate says it conforms to, as the editor finds them on
 * opening it: by the root's <code>conformsTo</code> (and, as in RO-Crate
 * 1.1, its descriptor's), not RO-Crate's own versions, among the profiles
 * bundled or kept, else loaded from where the identifier leads; without
 * any, the core RO-Crate profile the editor uses by default.
 *
 * @param declared The identifiers the crate gives, other than RO-Crate's
 * @param matched  The profiles found for them, in their order, or the core
 *                 RO-Crate profile if none
 * @param unknown  The identifiers no profile was found for, and why
 */
public record CrateProfiles(List<String> declared, List<Profile> matched, Map<String, String> unknown) {

	public CrateProfiles {
		declared = List.copyOf(declared);
		matched = List.copyOf(matched);
		unknown = Map.copyOf(unknown);
	}

	/**
	 * The profiles of a crate as read
	 *
	 * @param fetch Whether to load a profile not bundled nor kept from where
	 *              its identifier leads, on the web (see
	 *              {@link ProfileCache#load(URI)})
	 */
	public static CrateProfiles of(CrateDocument document, ProfileCache cache, boolean fetch) {
		Crate crate = document.crate().orElseThrow();
		List<String> declared = Stream.concat(
				crate.getRoot().getAll("conformsTo").stream().map(v -> v.path("@id").asText("")),
				document.conformsTo().stream())
				.filter(id -> !id.isBlank() && !id.startsWith(Crate.RO_CRATE_SPEC)).distinct().toList();
		List<Profile> matched = new ArrayList<>();
		Map<String, String> unknown = new HashMap<>();
		for (String id : declared) {
			Optional<Profile> found = cache.find(id).or(() -> cache.find(otherSlash(id)));
			if (found.isEmpty() && fetch) {
				try {
					Profile loaded = cache.load(new URI(id));
					if (loaded.hasErrors()) {
						unknown.put(id, loaded.allProblems().get(0).message());
					} else {
						found = Optional.of(loaded);
					}
				} catch (URISyntaxException e) {
					unknown.put(id, "not a URI");
				}
			} else if (found.isEmpty()) {
				unknown.put(id, "not bundled nor kept, and not fetched");
			}
			found.filter(p -> !matched.contains(p)).ifPresent(matched::add);
		}
		if (matched.isEmpty()) {
			cache.bundledIdentifiers().stream().sorted().findFirst().flatMap(cache::bundled).ifPresent(matched::add);
		}
		return new CrateProfiles(declared, matched, unknown);
	}

	/** The identifier with its trailing slash, or without, as profiles are written either way */
	private static String otherSlash(String id) {
		return id.endsWith("/") ? id.substring(0, id.length() - 1) : id + "/";
	}

	/** The fields of the first profile matched, or schema.org's alone if none */
	public FormFields fields(SchemaOrgFormFields schemaOrg) {
		return matched.isEmpty() ? schemaOrg : new ProfileFormFields(matched.get(0), schemaOrg);
	}

	/**
	 * Properties' labels as the editor's form shows them for these fields:
	 * the fields' own labels for the crate's entities' types, else made from
	 * the term (see {@link Labels#fromTerm})
	 */
	public static Function<String, String> labels(Crate crate, FormFields fields, CrateTerms terms) {
		Map<String, String> labels = new HashMap<>();
		Map<Set<URI>, Boolean> seen = new HashMap<>();
		for (Entity entity : crate.getEntities()) {
			Set<URI> types = entity.getTypes().stream().map(terms::iri).flatMap(Optional::stream)
					.collect(Collectors.toSet());
			boolean root = entity == crate.getRoot();
			if (seen.putIfAbsent(types, root) == null || root) {
				fields.fieldsFor(types, root).forEach(f -> labels.putIfAbsent(f.name(), f.label()));
			}
		}
		return property -> labels.getOrDefault(property, Labels.fromTerm(property));
	}
}
