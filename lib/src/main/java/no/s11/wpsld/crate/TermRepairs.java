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
package no.s11.wpsld.crate;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import no.s11.wpsld.soss.ClassDef;
import no.s11.wpsld.soss.PropertyDef;

/**
 * Repairs of how a crate writes schema.org terms (see Crate > Repair
 * Crate): types written as IRIs, by the names the RO-Crate context gives
 * them; and members of Enumerations written as text, as references to
 * them, as JSON-LD needs to know they are terms rather than strings.
 */
public final class TermRepairs {
	private static final String SCHEMA = "http://schema.org/";

	private TermRepairs() {
	}

	/**
	 * Types written as IRIs, e.g. <code>http://schema.org/Person</code> (as
	 * ROHub writes them), written by the names the RO-Crate context gives
	 * them (<code>Person</code>), each type once; others as they are
	 *
	 * @return What was renamed, e.g. "#alice: http://schema.org/Person → Person"
	 */
	public static List<String> typeNames(Crate crate, CrateTerms terms) {
		List<String> done = new ArrayList<>();
		for (Entity entity : crate.getEntities()) {
			LinkedHashSet<String> types = new LinkedHashSet<>();
			List<String> renamed = new ArrayList<>();
			for (String type : entity.getTypes()) {
				Optional<String> name = terms.typeName(type);
				name.ifPresent(n -> renamed.add(type + " → " + n));
				types.add(name.orElse(type));
			}
			if (!renamed.isEmpty()) {
				entity.setTypes(List.copyOf(types));
				done.add(entity.getId() + ": " + String.join(", ", renamed));
			}
		}
		return done;
	}

	/**
	 * Members of an Enumeration written as text or as a relative reference,
	 * e.g. <code>"actionStatus": "PotentialActionStatus"</code> (also
	 * <code>schema:PotentialActionStatus</code> or with
	 * <code>https://schema.org/</code>), where the property's range is that
	 * Enumeration, as references to them,
	 * <code>{"@id": "http://schema.org/PotentialActionStatus"}</code>; not a
	 * reference to an entity of the crate, nor other text, which the range
	 * may allow too
	 *
	 * @return What was changed, e.g. "#run: actionStatus PotentialActionStatus"
	 */
	public static List<String> enumerationMembers(Crate crate, CrateTerms terms) {
		List<String> done = new ArrayList<>();
		for (Entity entity : crate.getEntities()) {
			for (String property : List.copyOf(entity.getValues().keySet())) {
				List<URI> members = members(property, terms);
				if (members.isEmpty()) {
					continue;
				}
				List<JsonNode> values = new ArrayList<>(entity.getAll(property));
				boolean changed = false;
				for (int i = 0; i < values.size(); i++) {
					JsonNode value = values.get(i);
					Optional<URI> member = member(value, members, crate);
					if (member.isPresent()) {
						values.set(i, JsonNodeFactory.instance.objectNode().put("@id", member.get().toString()));
						done.add(entity.getId() + ": " + property + " "
								+ member.get().toString().substring(SCHEMA.length()));
						changed = true;
					}
				}
				if (changed) {
					entity.setAll(property, values);
				}
			}
		}
		return done;
	}

	/** The members of the schema.org Enumerations in a property's range */
	private static List<URI> members(String property, CrateTerms terms) {
		Map<URI, PropertyDef> properties = terms.getSchema().getProperties();
		Optional<PropertyDef> def = terms.iri(property).map(properties::get);
		return def.stream().flatMap(d -> d.getRangeIncludes().stream())
				.filter(terms.getSchema()::isEnumeration)
				.flatMap((ClassDef range) -> terms.getSchema().members(range).stream())
				.filter(m -> m.toString().startsWith(SCHEMA)).distinct().toList();
	}

	/** The member a value means, if it is written otherwise than as a reference to it */
	private static Optional<URI> member(JsonNode value, List<URI> members, Crate crate) {
		String written;
		if (value.isTextual()) {
			written = value.asText().strip();
		} else if (value.isObject() && value.size() == 1 && value.path("@id").isTextual()) {
			written = value.path("@id").asText();
			// Already a reference to it, or to an entity of the crate
			if (written.startsWith(SCHEMA) || crate.get(written).isPresent()) {
				return Optional.empty();
			}
		} else {
			return Optional.empty();
		}
		String local = written.startsWith(SCHEMA) ? written.substring(SCHEMA.length())
				: written.startsWith("https://schema.org/") ? written.substring("https://schema.org/".length())
						: written.startsWith("schema:") ? written.substring("schema:".length()) : written;
		return members.stream().filter(m -> m.toString().equals(SCHEMA + local)).findFirst();
	}
}
