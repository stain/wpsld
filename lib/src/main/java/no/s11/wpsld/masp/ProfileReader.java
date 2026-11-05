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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateDocument.Problem;
import no.s11.wpsld.crate.CrateDocument.Severity;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.masp.RangeItem.AllowedValues;
import no.s11.wpsld.masp.RangeItem.ClassRuleRange;
import no.s11.wpsld.masp.RangeItem.IriRange;
import no.s11.wpsld.masp.RangeItem.ValueConstraint;

/**
 * Reads the rules of an RO-Crate MASP profile (or schema) crate, read by
 * {@link CrateReader}, as described in MASP.md:
 * <ol>
 * <li>the root's <code>hasResource</code> includes a
 * <code>ResourceDescriptor</code> with role
 * <code>http://www.w3.org/ns/dx/prof/role/schema</code>, whose
 * <code>hasPart</code> lists the rules; nothing else is a rule;</li>
 * <li><code>rdfs:Class</code> entities are {@link ClassRule}s,
 * <code>rdf:Property</code> entities {@link PropertyRule}s, whose
 * <code>rangeIncludes</code> may be class rules, IRIs,
 * <code>PropertyValue</code> constraints or <code>ItemList</code>s;</li>
 * <li>the metadata descriptor's class rule is the domain of the property
 * rule labelled <code>@id</code> with the value constraint
 * <code>ro-crate-metadata.json</code>; its property rule <code>about</code>
 * ranges over the root's class rule.</li>
 * </ol>
 * Like the crate reader it never throws for what is in the crate, and reads
 * what it can. MASP profiles write <code>sh:minCount</code> without defining
 * <code>sh:</code>, so both that and the SHACL IRI are accepted, as numbers
 * or strings.
 */
public class ProfileReader {
	private static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	private static final String RDFS = "http://www.w3.org/2000/01/rdf-schema#";
	private static final String SH = "http://www.w3.org/ns/shacl#";
	private static final String PROF = "http://www.w3.org/ns/dx/prof/";
	private static final String PROV_SPECIALIZATION_OF = "http://www.w3.org/ns/prov#specializationOf";
	private static final String S = "http://schema.org/";
	private static final String SCHEMA_ROLE = PROF + "role/schema";
	/** A constraint written like a JavaScript regular expression, e.g. /^readme\.md$/i */
	private static final Pattern JS_REGEX = Pattern.compile("^/(.*)/([a-z]*)$", Pattern.DOTALL);

	private final CrateReader crateReader;

	public ProfileReader() {
		this(new CrateReader());
	}

	public ProfileReader(CrateReader crateReader) {
		this.crateReader = crateReader;
	}

	public Profile read(Path path) {
		return read(crateReader.read(path));
	}

	public Profile read(CrateDocument document) {
		return new Reading(document).read();
	}

	private static class Reading {
		private final CrateDocument document;
		private final List<Problem> problems = new ArrayList<>();
		private final Map<String, ClassRule> classRules = new LinkedHashMap<>();
		private final Map<String, PropertyRule> propertyRules = new LinkedHashMap<>();

		Reading(CrateDocument document) {
			this.document = document;
		}

		private void error(String message) {
			problems.add(new Problem(Severity.ERROR, message));
		}

		private void warn(String message) {
			problems.add(new Problem(Severity.WARNING, message));
		}

		Profile read() {
			Optional<Crate> crate = document.crate();
			if (crate.isEmpty()) {
				error("The profile crate could not be read, see its problems");
				return profile("", Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.empty());
			}
			Entity root = crate.get().getRoot();
			if (!document.hasType(root, PROF + "Profile")) {
				warn("The root data entity " + root.getId() + " is not a Profile");
			}
			Optional<String> name = text(root, S + "name");
			Optional<String> version = text(root, S + "version");
			List<String> isProfileOf = document.references(root, PROF + "isProfileOf");

			Set<String> ruleIds = ruleIds(root);
			for (String id : ruleIds) {
				Optional<Entity> entity = document.entity(id);
				if (entity.isEmpty()) {
					warn("Rule " + id + " is listed in the schema resource, but not described");
				} else if (document.hasType(entity.get(), RDFS + "Class")) {
					classRules.put(id, classRule(id, entity.get()));
				}
			}
			for (String id : ruleIds) {
				document.entity(id).filter(e -> document.hasType(e, RDF + "Property"))
						.ifPresent(e -> propertyRules.put(id, propertyRule(id, e, ruleIds)));
			}
			checkDomains();
			Optional<String> descriptorRule = descriptorRule();
			Optional<String> rootRule = descriptorRule.flatMap(this::rootRule);
			return profile(root.getId(), name, version, isProfileOf, descriptorRule, rootRule);
		}

		private Profile profile(String id, Optional<String> name, Optional<String> version, List<String> isProfileOf,
				Optional<String> descriptorRule, Optional<String> rootRule) {
			return new Profile(id, name, version, isProfileOf, Collections.unmodifiableMap(classRules),
					Collections.unmodifiableMap(propertyRules), descriptorRule, rootRule, document,
					problems);
		}

		/**
		 * The hasPart of the root's schema ResourceDescriptor(s).
		 */
		private Set<String> ruleIds(Entity root) {
			Set<String> ids = new LinkedHashSet<>();
			boolean found = false;
			for (String resourceId : document.references(root, PROF + "hasResource")) {
				Optional<Entity> resource = document.entity(resourceId);
				if (resource.isPresent() && document.references(resource.get(), PROF + "hasRole").contains(SCHEMA_ROLE)) {
					found = true;
					ids.addAll(document.references(resource.get(), S + "hasPart"));
				}
			}
			if (!found) {
				error("No hasResource with the role " + SCHEMA_ROLE + ", so the profile has no rules");
			} else if (ids.isEmpty()) {
				warn("The schema resource lists no rules in hasPart");
			}
			return ids;
		}

		/**
		 * @param id The rule's expanded <code>@id</code>, as used by references to it
		 */
		private ClassRule classRule(String id, Entity entity) {
			return new ClassRule(id, label(entity), comment(entity),
					document.references(entity, PROV_SPECIALIZATION_OF),
					document.references(entity, RDFS + "subClassOf"), cardinality(entity));
		}

		private PropertyRule propertyRule(String id, Entity entity, Set<String> ruleIds) {
			List<String> domain = document.references(entity, S + "domainIncludes");
			if (domain.isEmpty()) {
				warn("Property rule " + entity.getId() + " has no domainIncludes, so applies to no class rule");
			}
			List<RangeItem> range = new ArrayList<>();
			for (JsonNode value : document.values(entity, S + "rangeIncludes")) {
				String ref = value.isObject() ? value.path("@id").asText("") : value.asText("");
				if (ref.isEmpty()) {
					warn("Property rule " + entity.getId() + " has a rangeIncludes that is not a reference: " + value);
				} else {
					range.add(rangeItem(entity.getId(), ref, ruleIds));
				}
			}
			return new PropertyRule(id, label(entity), comment(entity),
					document.references(entity, PROV_SPECIALIZATION_OF), domain, range, cardinality(entity));
		}

		private RangeItem rangeItem(String ruleId, String ref, Set<String> ruleIds) {
			String id = document.expandId(ref);
			Optional<Entity> target = document.entity(id);
			if (target.isPresent()) {
				Entity entity = target.get();
				if (!ruleIds.contains(id)) {
					warn("Property rule " + ruleId + " ranges over " + id
							+ ", which is not listed in the schema resource");
				}
				if (document.hasType(entity, RDFS + "Class")) {
					return new ClassRuleRange(id);
				}
				if (document.hasType(entity, S + "PropertyValue")) {
					return valueConstraint(id, entity);
				}
				if (document.hasType(entity, S + "ItemList")) {
					return new AllowedValues(id, text(entity, S + "name"),
							document.references(entity, S + "itemListElement"));
				}
				if (document.hasType(entity, S + "DefinedTermSet")) {
					// One of the set's terms
					return new AllowedValues(id, text(entity, S + "name"), document.entitiesOfType(S + "DefinedTerm")
							.stream().filter(t -> document.references(t, S + "inDefinedTermSet").contains(id))
							.map(t -> document.expandId(t.getId())).toList());
				}
				return new IriRange(id);
			}
			if (isAbsolute(id)) {
				return new IriRange(CrateDocument.normalise(id));
			}
			// MASP writes e.g. {"@id": "Text"} for schema:Text: a term rather
			// than the document-relative IRI JSON-LD would make of it
			return new IriRange(document.expand(ref).orElse(ref));
		}

		private ValueConstraint valueConstraint(String id, Entity entity) {
			List<JsonNode> values = document.values(entity, S + "value");
			if (values.size() != 1) {
				warn("PropertyValue " + entity.getId() + " should have one value, not " + values.size());
			}
			JsonNode value = values.isEmpty() ? NullNode.getInstance()
					: values.get(0);
			Optional<Pattern> regex = Optional.empty();
			if (value.isTextual()) {
				Matcher m = JS_REGEX.matcher(value.asText());
				if (m.matches()) {
					try {
						regex = Optional.of(Pattern.compile(m.group(1), flags(m.group(2))));
					} catch (PatternSyntaxException e) {
						warn("PropertyValue " + entity.getId() + " has an invalid regular expression "
								+ value.asText() + ": " + e.getDescription());
					}
				}
			}
			return new ValueConstraint(id, text(entity, S + "name"), value, regex);
		}

		private static int flags(String jsFlags) {
			int flags = 0;
			for (char c : jsFlags.toCharArray()) {
				flags |= switch (c) {
				case 'i' -> Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
				case 'm' -> Pattern.MULTILINE;
				case 's' -> Pattern.DOTALL;
				case 'u' -> Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
				default -> 0;
				};
			}
			return flags;
		}

		private void checkDomains() {
			for (PropertyRule property : propertyRules.values()) {
				for (String domain : property.domain()) {
					if (!classRules.containsKey(domain)) {
						warn("Property rule " + property.id() + " has the domain " + domain
								+ ", which is not a class rule of the profile");
					}
				}
			}
		}

		/**
		 * The domain of the property rule <code>@id</code> constrained to
		 * <code>ro-crate-metadata.json</code>.
		 */
		private Optional<String> descriptorRule() {
			for (PropertyRule property : propertyRules.values()) {
				boolean descriptorId = property.label().equals("@id") && property.range().stream()
						.anyMatch(r -> r instanceof ValueConstraint v
								&& v.value().asText("").equals(Crate.RO_CRATE_METADATA_JSON));
				if (descriptorId && !property.domain().isEmpty()) {
					return Optional.of(property.domain().get(0));
				}
			}
			warn("No property rule @id constrained to " + Crate.RO_CRATE_METADATA_JSON
					+ ", so the metadata descriptor's class rule is unknown");
			return Optional.empty();
		}

		private Optional<String> rootRule(String descriptorRule) {
			for (PropertyRule property : propertyRules.values()) {
				if (property.label().equals("about") && property.domain().contains(descriptorRule)) {
					Optional<String> root = property.range().stream().filter(ClassRuleRange.class::isInstance)
							.map(r -> ((ClassRuleRange) r).classRuleId()).findFirst();
					if (root.isPresent()) {
						return root;
					}
				}
			}
			warn("The metadata descriptor's class rule has no about property rule ranging over a class rule,"
					+ " so the root data entity's class rule is unknown");
			return Optional.empty();
		}

		private String label(Entity entity) {
			return text(entity, RDFS + "label").or(() -> text(entity, S + "name"))
					.or(() -> document.references(entity, PROV_SPECIALIZATION_OF).stream().findFirst()
							.map(iri -> iri.replaceFirst(".*[/#]", "")))
					.orElse(entity.getId());
		}

		private Optional<String> comment(Entity entity) {
			return text(entity, RDFS + "comment").or(() -> text(entity, S + "description"));
		}

		private Optional<String> text(Entity entity, String propertyIri) {
			return document.values(entity, propertyIri).stream().filter(JsonNode::isValueNode)
					.map(JsonNode::asText).findFirst()
					.or(() -> document.values(entity, propertyIri).stream().filter(v -> v.has("@value"))
							.map(v -> v.get("@value").asText()).findFirst());
		}

		/**
		 * sh:minCount/sh:maxCount, written as compact IRIs with an undefined
		 * prefix or as SHACL IRIs, numbers or strings.
		 */
		private Cardinality cardinality(Entity entity) {
			OptionalInt min = count(entity, "minCount");
			OptionalInt max = count(entity, "maxCount");
			if (min.isPresent() && max.isPresent() && min.getAsInt() > max.getAsInt()) {
				warn(entity.getId() + " has sh:minCount " + min.getAsInt() + " above sh:maxCount " + max.getAsInt());
			}
			return new Cardinality(min, max);
		}

		private OptionalInt count(Entity entity, String name) {
			List<JsonNode> values = new ArrayList<>(document.values(entity, "sh:" + name));
			values.addAll(document.values(entity, SH + name));
			if (values.isEmpty()) {
				return OptionalInt.empty();
			}
			JsonNode value = values.get(0);
			JsonNode literal = value.has("@value") ? value.get("@value") : value;
			if (literal.canConvertToInt() && literal.isIntegralNumber()) {
				return OptionalInt.of(literal.asInt());
			}
			if (literal.isTextual() && literal.asText().strip().matches("\\d+")) {
				return OptionalInt.of(Integer.parseInt(literal.asText().strip()));
			}
			warn(entity.getId() + " has an invalid sh:" + name + ": " + value);
			return OptionalInt.empty();
		}

		private static boolean isAbsolute(String id) {
			int colon = id.indexOf(':');
			return colon > 0 && id.substring(0, colon).matches("[A-Za-z][A-Za-z0-9+.-]*");
		}
	}
}
