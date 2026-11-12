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
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

/**
 * Publications, datasets and software by their DOI, from the two agencies
 * that register most research DOIs: Crossref (journal articles, books) and
 * DataCite (datasets, software, e.g. Zenodo). A DOI is looked up at its
 * agency's REST API, which <code>https://doi.org/ra/</code> names, or as
 * CSL-JSON by content negotiation at doi.org for other agencies; text is
 * searched at both. DOIs are written as RO-Crate's examples have them,
 * <code>https://doi.org/10.…</code>.
 *
 * @see <a href="https://w3id.org/ro/crate/1.3/contextual-entities#publications-via-citation-property">RO-Crate 1.3: Publications via citation property</a>
 */
public class DOI {
	public static final String NAMESPACE = "https://doi.org/";
	public static final URI CROSSREF = URI.create("https://api.crossref.org/");
	public static final URI DATACITE = URI.create("https://api.datacite.org/");
	public static final URI RESOLVER = URI.create("https://doi.org/");
	/** Authors linked as Person entities; all are in the creditText */
	public static final int MAX_AUTHORS = 10;
	private static final int ROWS = 10;
	/**
	 * A DOI alone (10.1234/abc), as <code>doi:</code>, or at doi.org or
	 * dx.doi.org, http or https; percent-encoded in addresses
	 */
	private static final Pattern DOI_PATTERN = Pattern.compile(
			"(?:(?:https?://(?:dx\\.)?doi\\.org/)|(?:doi:\\s*)|(?:info:doi/))?(10\\.\\d{4,9}/\\S+)",
			Pattern.CASE_INSENSITIVE);
	/** Characters of Elasticsearch's query syntax, escaped for DataCite */
	private static final Pattern LUCENE = Pattern.compile("([+\\-!(){}\\[\\]^\"~*?:\\\\/&|])");
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/** An author of a work, with their ORCID iD if the agency has it */
	public record Author(String name, Optional<String> givenName, Optional<String> familyName,
			Optional<String> orcid) {
	}

	/**
	 * A work registered with a DOI, as the agency describes it.
	 *
	 * @param doi       e.g. 10.5281/zenodo.3541888
	 * @param type      Its schema.org type, e.g. ScholarlyArticle
	 * @param issued    Year, or year-month or year-month-day
	 * @param container e.g. the journal
	 * @param summary   Its abstract, as plain text, if the agency has one
	 * @param agency    Crossref, DataCite, or another registration agency
	 */
	public record Work(String doi, String type, String title, List<Author> authors, Optional<String> issued,
			Optional<String> container, List<String> issn, Optional<String> volume, Optional<String> issue,
			Optional<String> pages, Optional<String> publisher, Optional<String> summary, String agency) {
		/** The DOI as RO-Crate writes it, the <code>@id</code> of the work */
		public String iri() {
			return DOI.iri(doi);
		}

		/** The year it was issued, if known */
		public Optional<String> year() {
			return issued.map(i -> i.substring(0, Math.min(4, i.length())));
		}

		/**
		 * A short citation of it as text, as its <code>creditText</code>,
		 * with the journal it is in, if any, e.g. "Xuan et al. (2015): Topic
		 * Model for Graph Mining. IEEE Transactions on Cybernetics", "Sefton
		 * &amp; Soiland-Reyes (2019): …", or "Title (2019)" without authors.
		 */
		public String creditText() {
			List<String> names = authors.stream().map(a -> a.familyName().orElse(a.name())).toList();
			String who = names.size() > 2 ? names.get(0) + " et al."
					: String.join(" & ", names);
			String cited = who.isEmpty() ? title + year().map(y -> " (" + y + ")").orElse("")
					: who + year().map(y -> " (" + y + ")").orElse("") + ": " + title;
			return container.map(c -> cited + (cited.endsWith(".") ? " " : ". ") + c).orElse(cited);
		}
	}

	/** An agency said no, with its reason */
	public static class DoiException extends IOException {
		private static final long serialVersionUID = 1L;

		public DoiException(String message) {
			super(message);
		}
	}

	private final HttpClient http;
	private final Duration timeout;
	private final String userAgent;
	private final Map<String, URI> services;

	public DOI(HttpClient http, Duration timeout, String userAgent) {
		this(http, timeout, userAgent, Map.of("crossref", CROSSREF, "datacite", DATACITE, "resolver", RESOLVER));
	}

	/**
	 * @param services The addresses of <code>crossref</code>,
	 *                 <code>datacite</code> and <code>resolver</code>
	 *                 (doi.org), e.g. a test server
	 */
	public DOI(HttpClient http, Duration timeout, String userAgent, Map<String, URI> services) {
		this.http = Objects.requireNonNull(http);
		this.timeout = Objects.requireNonNull(timeout);
		this.userAgent = Objects.requireNonNull(userAgent);
		this.services = Map.copyOf(services);
	}

	/**
	 * The DOI in a text, e.g. <code>10.5281/zenodo.3541888</code> from
	 * <code>doi:10.5281/zenodo.3541888</code>,
	 * <code>http://dx.doi.org/10.5281/zenodo.3541888</code> or
	 * <code>https://doi.org/10.5281%2Fzenodo.3541888</code>.
	 */
	public static Optional<String> doiOf(String text) {
		String doi = text.strip();
		if (doi.contains("%")) {
			// e.g. https://doi.org/10.5281%2Fzenodo.3541888; a + stays a +
			try {
				doi = URLDecoder.decode(doi.replace("+", "%2B"), StandardCharsets.UTF_8);
			} catch (IllegalArgumentException e) {
				return Optional.empty();
			}
		}
		Matcher matcher = DOI_PATTERN.matcher(doi);
		return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
	}

	/**
	 * A DOI as RO-Crate writes it, e.g.
	 * <code>https://doi.org/10.5281/zenodo.3541888</code>, with characters
	 * an address can't have percent-encoded.
	 */
	public static String iri(String doi) {
		StringBuilder iri = new StringBuilder(NAMESPACE);
		for (char c : doi.toCharArray()) {
			if (" \"#%<>?\\^`{|}".indexOf(c) >= 0 || c < 0x20) {
				iri.append(URLEncoder.encode(String.valueOf(c), StandardCharsets.UTF_8).replace("+", "%20"));
			} else {
				iri.append(c);
			}
		}
		return iri.toString();
	}

	private JsonNode get(URI address, String accept) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(address).timeout(timeout).header("Accept", accept)
				.header("User-Agent", userAgent).GET().build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 404) {
			throw new DoiException("Not found: " + address);
		}
		if (response.statusCode() != 200) {
			throw new DoiException(address.getHost() + " answered " + response.statusCode());
		}
		try {
			return MAPPER.readTree(response.body());
		} catch (IOException e) {
			throw new DoiException(address.getHost() + " answered with no data");
		}
	}

	private URI service(String name, String path) {
		return services.get(name).resolve(path);
	}

	private static String encode(String text) {
		return URLEncoder.encode(text, StandardCharsets.UTF_8);
	}

	/**
	 * The work of a DOI, from the agency that registered it.
	 */
	public Work lookUp(String doi) throws IOException, InterruptedException {
		JsonNode ra = get(service("resolver", "ra/" + encode(doi)), "application/json");
		String agency = ra.path(0).path("RA").asText("");
		if (agency.isEmpty() || ra.path(0).has("status")) {
			throw new DoiException("No such DOI: " + doi);
		}
		return switch (agency) {
		case "Crossref" -> crossref(get(service("crossref", "works/" + encode(doi)), "application/json")
				.path("message"), agency);
		case "DataCite" -> datacite(get(service("datacite", "dois/" + encode(doi)), "application/json")
				.path("data").path("attributes"));
		default -> crossref(get(service("resolver", encode(doi).replace("%2F", "/")),
				"application/vnd.citationstyles.csl+json"), agency);
		};
	}

	/**
	 * Works whose title, authors or other details match the text, from
	 * Crossref and DataCite: their best matches, alternately. A DOI as the
	 * text looks that DOI up.
	 *
	 * @throws DoiException if both agencies fail; one failing is told in
	 *                      the list's absence of its works only
	 */
	public List<Work> search(String text) throws IOException, InterruptedException {
		Optional<String> doi = doiOf(text);
		if (doi.isPresent()) {
			return List.of(lookUp(doi.get()));
		}
		List<Work> crossref = List.of();
		List<Work> datacite = List.of();
		IOException failed = null;
		try {
			crossref = parseCrossref(get(service("crossref", "works?query.bibliographic=" + encode(text.strip())
					+ "&rows=" + ROWS), "application/json"));
		} catch (IOException e) {
			failed = e;
		}
		try {
			datacite = parseDatacite(get(service("datacite", "dois?query=" + encode(dataciteQuery(text))
					+ "&page%5Bsize%5D=" + ROWS), "application/json"));
		} catch (IOException e) {
			if (failed != null) {
				throw failed;
			}
		}
		List<Work> works = new ArrayList<>();
		for (int i = 0; i < Math.max(crossref.size(), datacite.size()); i++) {
			if (i < crossref.size()) {
				works.add(crossref.get(i));
			}
			if (i < datacite.size()) {
				works.add(datacite.get(i));
			}
		}
		return works;
	}

	/**
	 * DataCite's search for all the words, as its ranking of loose words
	 * is poor: <code>sefton AND crate</code>
	 */
	static String dataciteQuery(String text) {
		return Stream.of(text.strip().split("\\s+")).filter(w -> !w.isEmpty())
				.map(w -> LUCENE.matcher(w).replaceAll("\\\\$1")).collect(Collectors.joining(" AND "));
	}

	/** The works of a Crossref search */
	public static List<Work> parseCrossref(JsonNode json) {
		List<Work> works = new ArrayList<>();
		for (JsonNode item : json.path("message").path("items")) {
			Work work = crossref(item, "Crossref");
			if (!work.doi().isEmpty() && !work.title().isEmpty()) {
				works.add(work);
			}
		}
		return works;
	}

	/** The works of a DataCite search */
	public static List<Work> parseDatacite(JsonNode json) {
		List<Work> works = new ArrayList<>();
		for (JsonNode item : json.path("data")) {
			Work work = datacite(item.path("attributes"));
			if (!work.doi().isEmpty() && !work.title().isEmpty()) {
				works.add(work);
			}
		}
		return works;
	}

	/**
	 * A work as Crossref's REST API has it, or as CSL-JSON (much the same,
	 * but with single values where Crossref has lists).
	 */
	public static Work crossref(JsonNode item, String agency) {
		List<Author> authors = new ArrayList<>();
		for (JsonNode a : item.path("author")) {
			Optional<String> given = text(a.path("given"));
			Optional<String> family = text(a.path("family"));
			String name = text(a.path("name")).or(() -> text(a.path("literal")))
					.orElse(Stream.of(given, family).flatMap(Optional::stream).collect(Collectors.joining(" ")));
			if (!name.isBlank()) {
				authors.add(new Author(name, given, family, text(a.path("ORCID")).flatMap(ORCID::idOf)));
			}
		}
		return new Work(text(item.path("DOI")).orElse(""), schemaType(text(item.path("type")).orElse("")),
				first(item.path("title")).orElse(""), authors, date(item.path("issued").path("date-parts").path(0)),
				first(item.path("container-title")), texts(item.path("ISSN")), text(item.path("volume")),
				text(item.path("issue")), text(item.path("page")), text(item.path("publisher")),
				text(item.path("abstract")).map(DOI::plainText).filter(a -> !a.isEmpty()), agency);
	}

	/** A work as DataCite's REST API has it */
	public static Work datacite(JsonNode attributes) {
		List<Author> authors = new ArrayList<>();
		for (JsonNode c : attributes.path("creators")) {
			Optional<String> orcid = Optional.empty();
			for (JsonNode id : c.path("nameIdentifiers")) {
				if ("ORCID".equalsIgnoreCase(id.path("nameIdentifierScheme").asText())) {
					orcid = ORCID.idOf(id.path("nameIdentifier").asText(""));
				}
			}
			Optional<String> given = text(c.path("givenName"));
			Optional<String> family = text(c.path("familyName"));
			String name = given.isPresent() && family.isPresent() ? given.get() + " " + family.get()
					: text(c.path("name")).orElse("");
			if (!name.isBlank()) {
				authors.add(new Author(name, given, family, orcid));
			}
		}
		Optional<String> issued = Optional.empty();
		for (JsonNode date : attributes.path("dates")) {
			if ("Issued".equals(date.path("dateType").asText())) {
				issued = text(date.path("date")).map(d -> d.length() > 10 ? d.substring(0, 10) : d);
			}
		}
		if (issued.isEmpty()) {
			issued = text(attributes.path("publicationYear"));
		}
		Optional<String> summary = Optional.empty();
		for (JsonNode description : attributes.path("descriptions")) {
			if (summary.isEmpty() && "Abstract".equals(description.path("descriptionType").asText())) {
				summary = text(description.path("description")).map(DOI::plainText).filter(a -> !a.isEmpty());
			}
		}
		JsonNode publisher = attributes.path("publisher");
		String type = text(attributes.path("types").path("schemaOrg")).orElse("CreativeWork");
		return new Work(text(attributes.path("doi")).orElse(""), type.equals("Collection") ? "CreativeWork" : type,
				first(attributes.path("titles").path(0).path("title")).orElse(""), authors, issued, Optional.empty(),
				List.of(), Optional.empty(), Optional.empty(), Optional.empty(),
				publisher.isObject() ? text(publisher.path("name")) : text(publisher), summary, "DataCite");
	}

	/**
	 * Plain text of an abstract in markup: Crossref's JATS XML
	 * (<code>&lt;jats:p&gt;</code>) or DataCite's HTML, paragraphs separated
	 * by a blank line, other tags dropped, the common entities resolved.
	 */
	static String plainText(String markup) {
		String text = markup.replaceAll("(?i)</?([a-z0-9]+:)?(p|title|sec|br|div|li)\\b[^>]*>", "\n\n")
				.replaceAll("<[^>]+>", "");
		text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
				.replace("&apos;", "'").replace("&nbsp;", " ").replace("&amp;", "&");
		return Stream.of(text.split("\\n\\s*\\n")).map(p -> p.replaceAll("\\s+", " ").strip())
				.filter(p -> !p.isEmpty()).collect(Collectors.joining("\n\n"));
	}

	/**
	 * The schema.org type of a CSL or Crossref type, e.g. ScholarlyArticle
	 * for <code>journal-article</code>
	 */
	static String schemaType(String type) {
		return switch (type.toLowerCase(Locale.ROOT)) {
		case "journal-article", "article-journal", "proceedings-article", "paper-conference", "posted-content",
				"article", "peer-review" -> "ScholarlyArticle";
		case "book", "monograph", "edited-book", "reference-book" -> "Book";
		case "book-chapter", "chapter", "book-section", "book-part" -> "Chapter";
		case "dataset", "database" -> "Dataset";
		case "report", "report-component" -> "Report";
		case "dissertation", "thesis" -> "Thesis";
		case "software" -> "SoftwareSourceCode";
		default -> "CreativeWork";
		};
	}

	private static Optional<String> date(JsonNode parts) {
		List<String> date = new ArrayList<>();
		for (JsonNode part : parts) {
			if (!part.canConvertToInt()) {
				break;
			}
			date.add(date.isEmpty() ? String.format("%04d", part.asInt()) : String.format("%02d", part.asInt()));
		}
		return date.isEmpty() ? Optional.empty() : Optional.of(String.join("-", date));
	}

	private static Optional<String> text(JsonNode value) {
		return value.isValueNode() && !value.asText().isBlank() ? Optional.of(value.asText().strip())
				: Optional.empty();
	}

	/** A value, or the first of a list */
	private static Optional<String> first(JsonNode value) {
		return value.isArray() ? text(value.path(0)) : text(value);
	}

	private static List<String> texts(JsonNode value) {
		List<String> texts = new ArrayList<>();
		if (value.isArray()) {
			value.forEach(v -> text(v).ifPresent(texts::add));
		} else {
			text(value).ifPresent(texts::add);
		}
		return texts;
	}

	/**
	 * Describe a work as RO-Crate's example of a citation has it: its type
	 * (keeping other types it has, but not Thing), its DOI as
	 * <code>identifier</code>, <code>name</code>,
	 * <code>datePublished</code>, <code>creditText</code> (a short citation,
	 * see {@link Work#creditText()}), its <code>abstract</code> as plain text if the agency has one, its first {@value #MAX_AUTHORS} authors as Persons (by ORCID iD,
	 * or a Person of the crate with the same name, or a new one), and the
	 * journal it is in as a Periodical, by its ISSN. Values it has are kept.
	 * Its <code>@id</code> is left to the caller, normally {@link Work#iri()}.
	 *
	 * @param entities  The crate's entities
	 * @param newEntity Adds an entity of a type to the crate, with the
	 *                  <code>@id</code> given, or a new one for null
	 */
	public static void describe(Entity entity, Work work, List<Entity> entities,
			BiFunction<String, String, Entity> newEntity) {
		List<String> types = new ArrayList<>(entity.getTypes());
		types.remove("Thing");
		if (!types.contains(work.type())) {
			types.add(work.type());
		}
		entity.setTypes(types);
		setIfAbsent(entity, "identifier", TextNode.valueOf(work.iri()));
		setIfAbsent(entity, "name", TextNode.valueOf(work.title()));
		work.issued().ifPresent(i -> setIfAbsent(entity, "datePublished", TextNode.valueOf(i)));
		setIfAbsent(entity, "creditText", TextNode.valueOf(work.creditText()));
		work.summary().ifPresent(a -> setIfAbsent(entity, "abstract", TextNode.valueOf(a)));
		if (entity.get("author").isEmpty() && !work.authors().isEmpty()) {
			List<JsonNode> authors = new ArrayList<>();
			for (Author author : work.authors().stream().limit(MAX_AUTHORS).toList()) {
				Entity person = person(author, entities, newEntity);
				authors.add(JsonNodeFactory.instance.objectNode().put("@id", person.getId()));
			}
			entity.setAll("author", authors);
		}
		if (entity.get("isPartOf").isEmpty() && work.container().isPresent()) {
			String id = work.issn().isEmpty() ? null : "https://portal.issn.org/resource/ISSN/" + work.issn().get(0);
			Entity periodical = id == null ? null
					: entities.stream().filter(e -> e.getId().equals(id)).findFirst().orElse(null);
			if (periodical == null) {
				periodical = newEntity.apply("Periodical", id);
				periodical.set("name", Optional.of(TextNode.valueOf(work.container().get())));
				if (!work.issn().isEmpty()) {
					periodical.setAll("issn", work.issn().stream().map(i -> (JsonNode) TextNode.valueOf(i)).toList());
				}
			}
			entity.set("isPartOf", Optional.of(JsonNodeFactory.instance.objectNode().put("@id", periodical.getId())));
		}
	}

	private static void setIfAbsent(Entity entity, String property, JsonNode value) {
		if (entity.get(property).isEmpty()) {
			entity.set(property, Optional.of(value));
		}
	}

	/** The crate's Person for an author, or a new one */
	private static Entity person(Author author, List<Entity> entities, BiFunction<String, String, Entity> newEntity) {
		Optional<String> iri = author.orcid().map(o -> ORCID.NAMESPACE + o);
		Optional<Entity> existing = iri.isPresent()
				? entities.stream().filter(e -> e.getId().equals(iri.get())).findFirst()
				: entities.stream().filter(e -> e.getTypes().contains("Person"))
						.filter(e -> e.get("name").map(JsonNode::asText).filter(author.name()::equals).isPresent())
						.findFirst();
		if (existing.isPresent()) {
			return existing.get();
		}
		Entity person = newEntity.apply("Person", iri.orElse(null));
		person.set("name", Optional.of(TextNode.valueOf(author.name())));
		author.givenName().ifPresent(g -> person.set("givenName", Optional.of(TextNode.valueOf(g))));
		author.familyName().ifPresent(f -> person.set("familyName", Optional.of(TextNode.valueOf(f))));
		return person;
	}

	/**
	 * Bring the crate's DOIs to the form RO-Crate writes,
	 * <code>https://doi.org/10.…</code>, telling what was done:
	 * <code>@id</code>s and references written otherwise
	 * (<code>http://dx.doi.org/</code>, <code>doi:</code>, percent-encoded),
	 * merging entities that turn out to be the same work (DOIs are the same
	 * whatever their case); <code>identifier</code> text and
	 * <code>citation</code> text that is a DOI likewise, the citation
	 * becoming a link, as RO-Crate asks. A DOI alone (<code>10.…/…</code>)
	 * as <code>@id</code> is left, as it would be a file's path.
	 *
	 * @return What was changed, one line each
	 */
	public static List<String> repair(Crate crate) {
		List<String> done = new ArrayList<>();
		for (Entity entity : List.copyOf(crate.getEntities())) {
			Optional<String> iri = canonical(entity.getId());
			if (iri.isEmpty() || !crate.getEntities().contains(entity)) {
				continue;
			}
			String old = entity.getId();
			Optional<Entity> same = crate.getEntities().stream()
					.filter(e -> e != entity && e.getId().equalsIgnoreCase(iri.get())).findFirst();
			if (same.isPresent()) {
				crate.merge(same.get(), entity);
				done.add(old + " merged into " + same.get().getId());
			} else if (!old.equals(iri.get())) {
				crate.rename(entity, iri.get());
				done.add(old + " is now " + iri.get());
			}
		}
		for (Entity entity : List.copyOf(crate.getEntities())) {
			for (String property : List.copyOf(entity.getValues().keySet())) {
				for (JsonNode value : entity.getAll(property)) {
					String id = value.path("@id").asText(null);
					Optional<String> iri = id == null ? Optional.empty() : canonical(id);
					if (iri.isPresent() && !id.equals(iri.get())) {
						Optional<Entity> target = crate.getEntities().stream()
								.filter(e -> e.getId().equalsIgnoreCase(iri.get())).findFirst();
						String to = target.map(Entity::getId).orElse(iri.get());
						if (!to.equals(id)) {
							crate.replaceReferences(id, to);
							done.add("Links to " + id + " now go to " + to);
						}
					}
				}
			}
			for (String property : List.of("identifier", "citation")) {
				List<JsonNode> values = entity.getAll(property);
				List<JsonNode> repaired = new ArrayList<>();
				for (JsonNode value : values) {
					Optional<String> doi = value.isTextual() ? doiOf(value.asText()) : Optional.empty();
					if (doi.isEmpty()) {
						repaired.add(value);
						continue;
					}
					String iri = iri(doi.get());
					JsonNode now = property.equals("citation") ? JsonNodeFactory.instance.objectNode().put("@id", iri)
							: TextNode.valueOf(iri);
					if (!now.equals(value)) {
						done.add(entity.getLabel() + "'s " + property + " " + value.asText() + " is now "
								+ (now.isObject() ? "a link to " : "") + iri);
					}
					repaired.add(now);
				}
				if (!repaired.equals(values)) {
					entity.setAll(property, repaired.stream().distinct().toList());
				}
			}
		}
		return done;
	}

	/**
	 * The DOI address an <code>@id</code> means: only addresses and
	 * <code>doi:</code>, not a DOI alone, which would be a relative path.
	 */
	private static Optional<String> canonical(String id) {
		String lower = id.strip().toLowerCase(Locale.ROOT);
		boolean address = lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("doi:")
				|| lower.startsWith("info:doi/");
		return address ? doiOf(id).map(DOI::iri) : Optional.empty();
	}
}
