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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.util.Value;

/**
 * The file formats of <a href="https://pronom.nationalarchives.gov.uk/">PRONOM</a>,
 * The National Archives' registry, bundled from its DROID signature file
 * (see <code>scripts/update-pronom-formats.sh</code>), to find a format by
 * name, PUID, extension or media type and describe it in a crate as RO-Crate
 * recommends: the file's <code>encodingFormat</code> has its media type and
 * a link to a <code>["WebPage", "Standard"]</code> entity whose
 * <code>@id</code> is the format's PRONOM address.
 *
 * @see <a href="https://w3id.org/ro/crate/1.3/data-entities#adding-detailed-descriptions-of-file-encodings">RO-Crate 1.3: File encodings</a>
 */
public final class PronomFormats {
	public static final String RESOURCE = "/pronom/formats.json";
	/** Formats' addresses as RO-Crate writes them, e.g. …/PRONOM/fmt/19 */
	public static final String NAMESPACE = "https://www.nationalarchives.gov.uk/PRONOM/";
	/** The types of a format's entity, as in RO-Crate's examples */
	public static final List<String> TYPES = List.of("WebPage", "Standard");
	/**
	 * e.g. https://www.nationalarchives.gov.uk/PRONOM/fmt/19, the http:
	 * form, or the new site's https://pronom.nationalarchives.gov.uk/fmt/19
	 */
	private static final Pattern PRONOM_IRI = Pattern.compile(
			"https?://(?:www\\.nationalarchives\\.gov\\.uk/(?i:pronom)/|pronom\\.nationalarchives\\.gov\\.uk/)((?:x-)?(?:fmt|sfw|chr)/\\d+)(?:\\.(?:json|xml|html))?/?");
	private static final Value<PronomFormats> BUNDLED = Value.lazy(PronomFormats::readBundled);

	/**
	 * A file format of PRONOM.
	 *
	 * @param puid       Its PRONOM unique identifier, e.g. <code>fmt/19</code>
	 * @param version    e.g. 1.5, if it has one
	 * @param mediaTypes e.g. <code>application/pdf</code>; often none
	 * @param extensions e.g. <code>pdf</code>, without the dot
	 */
	public record Format(String puid, String name, Optional<String> version, List<String> mediaTypes,
			List<String> extensions) {
		/** Its address, the <code>@id</code> of the format in a crate */
		public String iri() {
			return NAMESPACE + puid;
		}

		/** Its page on PRONOM's new site */
		public String page() {
			return "https://pronom.nationalarchives.gov.uk/" + puid;
		}
	}

	private final String version;
	private final List<Format> formats;

	public PronomFormats(String version, List<Format> formats) {
		this.version = Objects.requireNonNull(version);
		this.formats = List.copyOf(formats);
	}

	/**
	 * The formats bundled with wpsld, read once.
	 */
	public static PronomFormats bundled() {
		return BUNDLED.get();
	}

	private static PronomFormats readBundled() {
		try (InputStream in = PronomFormats.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("Can't find the bundled PRONOM formats " + RESOURCE);
			}
			JsonNode json = new ObjectMapper().readTree(in);
			return new PronomFormats(json.path("version").asText("?"), parse(json));
		} catch (IOException e) {
			throw new UncheckedIOException("Can't read the bundled PRONOM formats", e);
		}
	}

	/** The formats of the bundled JSON, skipping any without PUID or name */
	public static List<Format> parse(JsonNode json) {
		List<Format> formats = new ArrayList<>();
		for (JsonNode format : json.path("formats")) {
			String puid = format.path("puid").asText("");
			String name = format.path("name").asText("");
			if (puid.isBlank() || name.isBlank()) {
				continue;
			}
			formats.add(new Format(puid, name,
					Optional.of(format.path("version").asText("")).filter(v -> !v.isBlank()),
					texts(format.path("mediaTypes")), texts(format.path("extensions"))));
		}
		return formats;
	}

	private static List<String> texts(JsonNode array) {
		List<String> texts = new ArrayList<>();
		array.forEach(t -> texts.add(t.asText()));
		return texts;
	}

	/** The version of PRONOM's DROID signature file, e.g. 125 */
	public String getVersion() {
		return version;
	}

	public List<Format> getFormats() {
		return formats;
	}

	public Optional<Format> get(String puid) {
		return formats.stream().filter(f -> f.puid().equalsIgnoreCase(puid.strip())).findFirst();
	}

	/**
	 * The PUID in a PRONOM address, e.g. <code>fmt/19</code> for
	 * <code>https://www.nationalarchives.gov.uk/PRONOM/fmt/19</code> or
	 * <code>https://pronom.nationalarchives.gov.uk/fmt/19.json</code>.
	 */
	public static Optional<String> puidOf(String address) {
		Matcher matcher = PRONOM_IRI.matcher(address.strip());
		return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
	}

	/** The formats with this media type, ignoring case */
	public List<Format> withMediaType(String mediaType) {
		String wanted = mediaType.strip().toLowerCase(Locale.ROOT);
		return formats.stream().filter(f -> f.mediaTypes().stream().anyMatch(m -> m.equalsIgnoreCase(wanted)))
				.toList();
	}

	/**
	 * The formats matching all words of the query, ignoring case: words of
	 * its name or version, its PUID, an extension (with or without its dot,
	 * e.g. <code>.csv</code>) or a media type. A PUID, extension or media
	 * type matched exactly come first, then names starting with the query,
	 * then by name. All of them for an empty query.
	 */
	public List<Format> search(String query) {
		String q = query.strip().toLowerCase(Locale.ROOT);
		List<String> words = List.of(q.split("[\\s,;]+")).stream().filter(w -> !w.isEmpty())
				.map(w -> w.startsWith(".") ? w.substring(1) : w).filter(w -> !w.isEmpty()).toList();
		Comparator<Format> order = Comparator.<Format>comparingInt(f -> rank(f, q))
				.thenComparing(f -> f.name().toLowerCase(Locale.ROOT))
				.thenComparing(f -> f.version().orElse(""));
		return formats.stream().filter(f -> {
			String text = String.join(" ", f.puid(), f.name(), f.version().orElse(""), String.join(" ", f.mediaTypes()),
					String.join(" ", f.extensions())).toLowerCase(Locale.ROOT);
			return words.stream().allMatch(text::contains);
		}).sorted(order).toList();
	}

	private static int rank(Format format, String query) {
		String q = query.startsWith(".") ? query.substring(1) : query;
		if (q.isEmpty()) {
			return 0;
		}
		if (format.puid().equalsIgnoreCase(q) || format.extensions().stream().anyMatch(q::equalsIgnoreCase)
				|| format.mediaTypes().stream().anyMatch(q::equalsIgnoreCase)) {
			return 0;
		}
		return format.name().toLowerCase(Locale.ROOT).startsWith(q) ? 1 : 2;
	}

	/**
	 * Describe a format's entity as RO-Crate's examples do: of the types
	 * <code>WebPage</code> and <code>Standard</code> (keeping others it has,
	 * but not Thing), named as PRONOM names it, unless it has a name. Its
	 * <code>@id</code> is left to the caller, normally {@link Format#iri()}.
	 */
	public static void describe(Entity entity, Format format) {
		List<String> types = new ArrayList<>(entity.getTypes());
		types.remove("Thing");
		TYPES.stream().filter(t -> !types.contains(t)).forEach(types::add);
		entity.setTypes(types);
		if (entity.get("name").isEmpty()) {
			entity.set("name", Optional.of(TextNode.valueOf(format.name())));
		}
	}

	/**
	 * Add a format to an entity's <code>encodingFormat</code>: a link to it,
	 * and its media type if it has just one and the entity has none yet.
	 *
	 * @return whether anything was added
	 */
	public static boolean addTo(Entity entity, Format format) {
		List<JsonNode> values = new ArrayList<>(entity.getAll("encodingFormat"));
		boolean hasMediaType = values.stream().anyMatch(JsonNode::isTextual);
		JsonNode link = JsonNodeFactory.instance.objectNode().put("@id", format.iri());
		boolean changed = false;
		if (!hasMediaType && format.mediaTypes().size() == 1) {
			values.add(0, TextNode.valueOf(format.mediaTypes().get(0)));
			changed = true;
		}
		if (!values.contains(link)) {
			values.add(link);
			changed = true;
		}
		entity.setAll("encodingFormat", values);
		return changed;
	}

	/**
	 * Bring the crate's PRONOM formats to the form RO-Crate writes, telling
	 * what was done: other forms of PRONOM addresses (<code>http:</code>, the
	 * new site's, <code>.json</code>) become the format's address, merging
	 * entities that turn out to be the same; an <code>encodingFormat</code>
	 * media type that only one PRONOM format has (e.g.
	 * <code>text/csv</code>, but not <code>application/pdf</code>, which has
	 * many versions) gets a link to that format, unless the entity links to
	 * a PRONOM format already; and each PRONOM format linked to is a
	 * WebPage and Standard with a name.
	 *
	 * @return What was changed, one line each
	 */
	public List<String> repair(Crate crate) {
		List<String> done = new ArrayList<>();
		for (Entity entity : List.copyOf(crate.getEntities())) {
			Optional<Format> format = puidOf(entity.getId()).flatMap(this::get);
			if (format.isEmpty() || entity.getId().equals(format.get().iri())) {
				continue;
			}
			String old = entity.getId();
			Optional<Entity> existing = crate.get(format.get().iri());
			if (existing.isPresent()) {
				crate.merge(existing.get(), entity);
				done.add(old + " merged into " + format.get().iri());
			} else {
				crate.rename(entity, format.get().iri());
				done.add(old + " is now " + format.get().iri());
			}
		}
		for (Entity entity : List.copyOf(crate.getEntities())) {
			for (String property : List.copyOf(entity.getValues().keySet())) {
				for (JsonNode value : entity.getAll(property)) {
					String id = value.path("@id").asText(null);
					Optional<Format> format = id == null ? Optional.empty() : puidOf(id).flatMap(this::get);
					if (format.isPresent() && !id.equals(format.get().iri())) {
						crate.replaceReferences(id, format.get().iri());
						done.add("Links to " + id + " now go to " + format.get().iri());
					}
				}
			}
		}
		// A media type only one format has, e.g. text/csv
		for (Entity entity : List.copyOf(crate.getEntities())) {
			List<JsonNode> values = entity.getAll("encodingFormat");
			boolean linked = values.stream()
					.anyMatch(v -> puidOf(v.path("@id").asText("")).isPresent());
			if (linked) {
				continue;
			}
			values.stream().filter(JsonNode::isTextual).map(JsonNode::asText).map(this::withMediaType)
					.filter(found -> found.size() == 1).map(found -> found.get(0)).findFirst().ifPresent(format -> {
						addTo(entity, format);
						done.add("Format of " + entity.getLabel() + ": " + format.name() + " (" + format.puid()
								+ "), the only one with the media type " + format.mediaTypes().get(0));
					});
		}
		// Each format linked to: in the crate, and described
		for (Entity entity : List.copyOf(crate.getEntities())) {
			for (JsonNode value : entity.getAll("encodingFormat")) {
				String id = value.path("@id").asText("");
				Optional<Format> format = puidOf(id).flatMap(this::get);
				if (format.isEmpty()) {
					continue;
				}
				Entity described = crate.get(id).orElseGet(() -> {
					Entity added = crate.add("WebPage");
					crate.rename(added, id);
					return added;
				});
				boolean complete = described.getTypes().containsAll(TYPES) && described.get("name").isPresent();
				if (!complete) {
					describe(described, format.get());
					done.add(format.get().name() + " (" + id + ") completed from PRONOM");
				}
			}
		}
		return done;
	}
}
