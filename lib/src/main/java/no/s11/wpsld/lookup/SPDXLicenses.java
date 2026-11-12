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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.util.Value;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

/**
 * The licences of the <a href="https://spdx.org/licenses/">SPDX License
 * List</a>, bundled as JSON-LD (see <code>scripts/update-spdx-licenses.sh</code>),
 * to find a licence by its identifier or name and describe it in a crate as
 * RO-Crate recommends: a CreativeWork whose <code>@id</code> is the licence's
 * SPDX address, with its SPDX identifier as a PropertyValue.
 */
public final class SPDXLicenses {
	/** The bundled list, in the resources */
	public static final String RESOURCE = "/spdx-licenses/licenses.jsonld";
	private static final String SOURCE = "/spdx-licenses/source.json";
	/** The licences' IRIs, as in SPDX's own JSON-LD */
	public static final String NAMESPACE = "http://spdx.org/licenses/";
	/** SPDX licence identifiers in identifiers.org, the PropertyValue's propertyID */
	public static final String PROPERTY_ID = "https://registry.identifiers.org/registry/spdx";
	/** e.g. http://spdx.org/licenses/CC-BY-4.0, or https://spdx.org/licenses/CC-BY-4.0.html */
	private static final Pattern SPDX_IRI = Pattern
			.compile("https?://(?:www\\.)?spdx\\.org/licenses/([0-9A-Za-z.+-]+?)(?:\\.html|\\.json|\\.jsonld)?");
	private static final Value<SPDXLicenses> BUNDLED = Value.lazy(SPDXLicenses::readBundled);

	/**
	 * A licence of the list.
	 *
	 * @param id         Its SPDX identifier, e.g. <code>CC-BY-4.0</code>
	 * @param deprecated Whether SPDX has deprecated the identifier, e.g.
	 *                   <code>GPL-2.0</code> for <code>GPL-2.0-only</code>
	 * @param seeAlso    Where the licence itself is, e.g. its legal code
	 */
	public record License(String id, String name, boolean osiApproved, boolean fsfLibre, boolean deprecated,
			List<String> seeAlso) {
		/** Its IRI, the <code>@id</code> of the licence in a crate */
		public String iri() {
			return NAMESPACE + id;
		}

		/** Its page on the SPDX License List */
		public String page() {
			return "https://spdx.org/licenses/" + id + ".html";
		}

		/** The <code>@id</code> of the PropertyValue of its SPDX identifier */
		public String identifierId() {
			return "#spdx-" + id;
		}
	}

	private final String version;
	private final List<License> licenses;
	/** Licences by their links, see {@link #byLink} */
	private final Value<Map<String, License>> links;

	public SPDXLicenses(String version, List<License> licenses) {
		this.version = Objects.requireNonNull(version);
		this.licenses = List.copyOf(licenses);
		this.links = Value.lazy(this::indexLinks);
	}

	/**
	 * The licences by their links (<code>rdfs:seeAlso</code>), as
	 * {@link #linkKey} gives them; only current licences, and only links one
	 * licence has (not e.g. GNU's GPL page, which both GPL-3.0-only and
	 * GPL-3.0-or-later list).
	 */
	private Map<String, License> indexLinks() {
		Map<String, List<License>> byKey = new HashMap<>();
		licenses.stream().filter(l -> !l.deprecated()).forEach(l -> l.seeAlso().stream().map(SPDXLicenses::linkKey)
				.distinct().forEach(key -> byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(l)));
		Map<String, License> unique = new HashMap<>();
		byKey.forEach((key, found) -> {
			if (found.size() == 1) {
				unique.put(key, found.get(0));
			}
		});
		return Map.copyOf(unique);
	}

	/**
	 * A link compared without what doesn't change the licence: http or
	 * https, <code>www.</code>, case, a fragment, a trailing slash, and the
	 * Creative Commons page's <code>legalcode</code> or <code>deed.en</code>,
	 * e.g. <code>creativecommons.org/licenses/by/4.0</code>.
	 */
	static String linkKey(String link) {
		String key = link.strip().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("^www\\.", "")
				.replaceFirst("#.*$", "").replaceFirst("/+$", "");
		return key.replaceFirst("/(legalcode|deed)(\\.[a-z-]+)?$", "").replaceFirst("/+$", "");
	}

	/**
	 * The licence whose own address this is, as the SPDX License List links
	 * it, e.g. CC-BY-4.0 for <code>https://creativecommons.org/licenses/by/4.0/</code>
	 * (listed as <code>…/legalcode</code>) or MIT for
	 * <code>https://opensource.org/license/mit</code>; none if no licence or
	 * several have the link.
	 */
	public Optional<License> byLink(String address) {
		return Optional.ofNullable(links.get().get(linkKey(address)));
	}

	/**
	 * The list bundled with wpsld, read once.
	 */
	public static SPDXLicenses bundled() {
		return BUNDLED.get();
	}

	private static SPDXLicenses readBundled() {
		ObjectMapper mapper = new ObjectMapper();
		try (InputStream list = SPDXLicenses.class.getResourceAsStream(RESOURCE);
				InputStream source = SPDXLicenses.class.getResourceAsStream(SOURCE)) {
			if (list == null || source == null) {
				throw new IllegalStateException("Can't find the bundled SPDX License List " + RESOURCE);
			}
			return new SPDXLicenses(mapper.readTree(source).path("version").asText("?"), parse(mapper.readTree(list)));
		} catch (IOException e) {
			throw new UncheckedIOException("Can't read the bundled SPDX License List", e);
		}
	}

	/**
	 * The licences of SPDX's JSON-LD of the list (its compact form, as SPDX
	 * publishes it, with the <code>spdx:</code> and <code>rdfs:</code>
	 * prefixes), skipping anything that isn't a listed licence.
	 */
	public static List<License> parse(JsonNode jsonld) {
		List<License> licenses = new ArrayList<>();
		for (JsonNode node : jsonld.path("@graph")) {
			Optional<String> id = idOf(node.path("@id").asText(""));
			String name = node.path("spdx:name").asText("");
			if (id.isEmpty() || name.isBlank() || !"spdx:ListedLicense".equals(node.path("@type").asText())) {
				continue;
			}
			JsonNode seeAlso = node.path("rdfs:seeAlso");
			List<String> links = new ArrayList<>();
			for (JsonNode link : seeAlso.isArray() ? seeAlso : List.of(seeAlso)) {
				if (link.isTextual()) {
					links.add(link.asText());
				}
			}
			licenses.add(new License(id.get(), name, flag(node.path("spdx:isOsiApproved")),
					flag(node.path("spdx:isFsfLibre")), flag(node.path("spdx:isDeprecatedLicenseId")), links));
		}
		return licenses;
	}

	/** <code>{"@value": "true", "@type": "xsd:boolean"}</code>, or a plain boolean */
	private static boolean flag(JsonNode value) {
		return value.isBoolean() ? value.asBoolean() : "true".equals(value.path("@value").asText(value.asText()));
	}

	/** The version of the list, e.g. 3.29.0 */
	public String getVersion() {
		return version;
	}

	public List<License> getLicenses() {
		return licenses;
	}

	/**
	 * The licence with this SPDX identifier, ignoring case, e.g.
	 * <code>cc-by-4.0</code>.
	 */
	public Optional<License> get(String id) {
		return licenses.stream().filter(l -> l.id().equalsIgnoreCase(id.strip())).findFirst();
	}

	/**
	 * The SPDX identifier in a licence's address, e.g. <code>CC-BY-4.0</code>
	 * for <code>http://spdx.org/licenses/CC-BY-4.0</code> or
	 * <code>https://spdx.org/licenses/CC-BY-4.0.html</code>.
	 */
	public static Optional<String> idOf(String address) {
		Matcher matcher = SPDX_IRI.matcher(address.strip());
		return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
	}

	/**
	 * The licences whose identifier or name have all the words of the query,
	 * ignoring case, e.g. "cc by 4" or "apache": the identifier itself first,
	 * then those whose identifier starts with the query, then the others by
	 * name, deprecated ones last. All of them for an empty query.
	 */
	public List<License> search(String query) {
		String q = query.strip().toLowerCase(Locale.ROOT);
		List<String> words = List.of(q.split("[\\s,;]+")).stream().filter(w -> !w.isEmpty()).toList();
		Comparator<License> order = Comparator.<License>comparingInt(l -> rank(l, q))
				.thenComparing(License::deprecated).thenComparing(l -> l.name().toLowerCase(Locale.ROOT));
		return licenses.stream().filter(l -> {
			String text = (l.id() + " " + l.name()).toLowerCase(Locale.ROOT);
			return words.stream().allMatch(text::contains);
		}).sorted(order).toList();
	}

	private static int rank(License license, String query) {
		String id = license.id().toLowerCase(Locale.ROOT);
		if (query.isEmpty()) {
			return 0;
		}
		if (id.equals(query)) {
			return 0;
		}
		if (id.startsWith(query)) {
			return 1;
		}
		return license.name().toLowerCase(Locale.ROOT).startsWith(query) ? 2 : 3;
	}

	/**
	 * Describe a licence entity as SPDX lists it: a CreativeWork named as SPDX
	 * names it, with its SPDX identifier as a PropertyValue (with the
	 * <code>@id</code> {@link License#identifierId()}, reused if the crate
	 * has it), as RO-Crate recommends for identifiers. Its <code>@id</code>
	 * is left to the caller, normally {@link License#iri()}.
	 *
	 * @param lookup           The crate's entities by <code>@id</code>
	 * @param newPropertyValue Adds a new entity of type PropertyValue to the
	 *                         crate, given its <code>@id</code>
	 * @return the PropertyValue of its identifier
	 */
	public static Entity describe(Entity licence, License license, Function<String, Optional<Entity>> lookup,
			Function<String, Entity> newPropertyValue) {
		if (!licence.getTypes().contains("CreativeWork")) {
			List<String> types = new ArrayList<>(licence.getTypes());
			types.remove("Thing");
			types.add("CreativeWork");
			licence.setTypes(types);
		}
		licence.set("name", Optional.of(TextNode.valueOf(license.name())));
		Entity identifier = lookup.apply(license.identifierId())
				.orElseGet(() -> newPropertyValue.apply(license.identifierId()));
		identifier.set("name", Optional.of(TextNode.valueOf("SPDX")));
		identifier.set("propertyID", Optional.of(TextNode.valueOf(PROPERTY_ID)));
		identifier.set("value", Optional.of(TextNode.valueOf(license.id())));
		identifier.set("url", Optional.of(TextNode.valueOf(license.page())));
		boolean linked = licence.getAll("identifier").stream()
				.anyMatch(v -> identifier.getId().equals(v.path("@id").asText(null)));
		if (!linked) {
			List<JsonNode> identifiers = new ArrayList<>(licence.getAll("identifier"));
			identifiers.add(JsonNodeFactory.instance.objectNode().put("@id", identifier.getId()));
			licence.setAll("identifier", identifiers);
		}
		return identifier;
	}

	/**
	 * The SPDX licence a reference or <code>license</code> text means: an
	 * SPDX address in any of its forms (e.g.
	 * <code>https://spdx.org/licenses/Apache-2.0.html</code>), or, for text,
	 * an SPDX identifier itself (e.g. <code>CC-BY-4.0</code>), if the list
	 * has it.
	 */
	public Optional<License> meant(String text, boolean identifier) {
		Optional<License> byAddress = idOf(text).flatMap(this::get);
		return byAddress.isPresent() || !identifier ? byAddress : get(text);
	}

	/**
	 * Bring the crate's SPDX licences to the form wpsld writes, telling what
	 * was done: <code>@id</code>s and references with another form of an SPDX
	 * address (<code>https:</code>, <code>.html</code>, another case) use the
	 * licence's IRI, merging entities that turn out to be the same; a
	 * <code>license</code> given as text that is an SPDX address or
	 * identifier becomes a reference; a licence known by an address the list
	 * links it with (see {@link #byLink}), e.g. Creative Commons' own, uses
	 * the licence's IRI instead, keeping that address as its
	 * <code>url</code>; and each SPDX licence referred to is a
	 * CreativeWork with a name and its SPDX identifier (see
	 * {@link #describe}), keeping a name it has.
	 *
	 * @return What was changed, one line each
	 */
	public List<String> repair(Crate crate) {
		List<String> done = new ArrayList<>();
		// license as text, e.g. "CC-BY-4.0" or "https://spdx.org/licenses/MIT.html"
		for (Entity entity : List.copyOf(crate.getEntities())) {
			List<JsonNode> values = entity.getAll("license");
			List<JsonNode> repaired = new ArrayList<>();
			boolean changed = false;
			for (JsonNode value : values) {
				Optional<License> license = value.isTextual()
						? meant(value.asText(), true).or(() -> byLink(value.asText()))
						: Optional.empty();
				if (license.isPresent()) {
					repaired.add(JsonNodeFactory.instance.objectNode().put("@id", license.get().iri()));
					done.add("License of " + entity.getLabel() + ": the text \"" + value.asText()
							+ "\" is now a link to " + license.get().iri());
					changed = true;
				} else {
					repaired.add(value);
				}
			}
			if (changed) {
				entity.setAll("license", repaired.stream().distinct().toList());
			}
		}
		// Licences known by their own address, e.g. Creative Commons'
		Set<String> licenceLinks = new LinkedHashSet<>();
		crate.getEntities().forEach(e -> e.getAll("license").stream().map(v -> v.path("@id").asText(""))
				.filter(id -> !id.isEmpty()).forEach(licenceLinks::add));
		for (String old : licenceLinks) {
			Optional<License> license = meant(old, false).isPresent() ? Optional.empty() : byLink(old);
			if (license.isEmpty()) {
				continue;
			}
			String iri = license.get().iri();
			Optional<Entity> entity = crate.get(old);
			Optional<Entity> existing = crate.get(iri);
			if (entity.isEmpty()) {
				crate.replaceReferences(old, iri);
				done.add("Links to the licence " + old + " now go to " + iri);
				continue;
			}
			if (existing.isPresent()) {
				crate.merge(existing.get(), entity.get());
				done.add("The licence " + old + " merged into " + iri);
			} else {
				crate.rename(entity.get(), iri);
				done.add("The licence " + old + " is now " + iri);
			}
			// Its own page, still to be found
			Entity licence = crate.get(iri).orElseThrow();
			if (licence.get("url").isEmpty()) {
				licence.set("url", Optional.of(TextNode.valueOf(old)));
			}
		}
		// Entities known by another form of their SPDX address
		for (Entity entity : List.copyOf(crate.getEntities())) {
			Optional<License> license = meant(entity.getId(), false);
			if (license.isEmpty() || entity.getId().equals(license.get().iri())) {
				continue;
			}
			String old = entity.getId();
			Optional<Entity> existing = crate.get(license.get().iri());
			if (existing.isPresent()) {
				crate.merge(existing.get(), entity);
				done.add(old + " merged into " + license.get().iri());
			} else {
				crate.rename(entity, license.get().iri());
				done.add(old + " is now " + license.get().iri());
			}
		}
		// References to other forms of SPDX addresses without an entity
		for (Entity entity : List.copyOf(crate.getEntities())) {
			for (String property : List.copyOf(entity.getValues().keySet())) {
				for (JsonNode value : entity.getAll(property)) {
					String id = value.path("@id").asText(null);
					Optional<License> license = id == null ? Optional.empty() : meant(id, false);
					if (license.isPresent() && !id.equals(license.get().iri())) {
						crate.replaceReferences(id, license.get().iri());
						done.add("Links to " + id + " now go to " + license.get().iri());
					}
				}
			}
		}
		// Each SPDX licence linked to: in the crate, and described
		Function<String, Entity> newPropertyValue = id -> {
			Entity created = crate.add("PropertyValue");
			crate.rename(created, id);
			return created;
		};
		List<String> licences = crate.getEntities().stream()
				.flatMap(e -> e.getValues().values().stream().flatMap(v -> v.isArray() ? valuesOf(v) : Stream.of(v)))
				.map(v -> v.path("@id").asText(""))
				.filter(id -> get(idOf(id).orElse("")).map(l -> l.iri().equals(id)).orElse(false)).distinct().toList();
		for (String iri : licences) {
			License license = meant(iri, false).orElseThrow();
			Entity licence = crate.get(iri).orElseGet(() -> {
				Entity added = crate.add("CreativeWork");
				crate.rename(added, iri);
				return added;
			});
			boolean complete = licence.getTypes().contains("CreativeWork") && licence.get("name").isPresent()
					&& crate.get(license.identifierId()).isPresent() && licence.getAll("identifier").stream()
							.anyMatch(v -> license.identifierId().equals(v.path("@id").asText(null)));
			if (!complete) {
				Optional<JsonNode> name = licence.get("name");
				describe(licence, license, crate::get, newPropertyValue);
				// Its own name, if it had one
				name.ifPresent(n -> licence.set("name", Optional.of(n)));
				done.add(license.name() + " (" + iri + ") completed from the SPDX License List");
			}
		}
		return done;
	}

	private static Stream<JsonNode> valuesOf(JsonNode array) {
		List<JsonNode> values = new ArrayList<>();
		array.forEach(values::add);
		return values.stream();
	}
}
