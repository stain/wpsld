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
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Link sets (RFC 9264), as FAIR Signposting level 2 has them: a page whose
 * <code>Link</code> header or HTML has only a <code>rel="linkset"</code>
 * link, to a document that lists all its links, as Zenodo's and ROHub's
 * landing pages have, with <code>describedby</code> for its metadata and
 * <code>item</code>s for its files. In the JSON format
 * (<code>application/linkset+json</code>, one object with a
 * <code>linkset</code> array of objects with an <code>anchor</code> and, for
 * each relation, an array of targets with <code>href</code> and
 * <code>type</code>), or as text (<code>application/linkset</code>, the
 * syntax of the <code>Link</code> header). All that is fetched is untrusted:
 * nothing is followed from here but what the caller asks for.
 */
final class Linkset {
	/** The media types a link set is asked for as, the JSON first */
	static final String ACCEPT = "application/linkset+json, application/linkset;q=0.8";
	private static final Pattern PARAM = Pattern.compile(";\\s*([\\w*-]+)\\s*=\\s*(?:\"([^\"]*)\"|([^;,\\s]*))");
	private static final Pattern LINK_TAG = Pattern.compile("<link\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
	private static final Pattern ATTRIBUTE = Pattern
			.compile("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))");

	/** A link to a target, by its relation */
	record Link(String rel, URI href, String type, String profile) {
		/** As the parameters of a link, to rank it as the retriever ranks any link */
		Map<String, String> params() {
			Map<String, String> params = new HashMap<>();
			params.put("rel", rel);
			if (type != null) {
				params.put("type", type);
			}
			if (profile != null) {
				params.put("profile", profile);
			}
			return params;
		}
	}

	/** The links of one anchor, what they are about: none if the entry gave none */
	record Entry(Optional<URI> anchor, List<Link> links) {
	}

	private Linkset() {
	}

	/** A link to a link set, and what it says it is */
	record Reference(URI href, String type) {
	}

	/**
	 * The links to link sets of a page, in the order to try them: the JSON ones
	 * first, from its <code>Link</code> header and its HTML.
	 */
	static List<Reference> discover(HttpHeaders headers, String html, URI base) {
		List<Reference> found = new ArrayList<>();
		for (String header : headers.allValues("Link")) {
			for (String link : header.split(",(?=\\s*<)")) {
				int start = link.indexOf('<');
				int end = link.indexOf('>', start + 1);
				if (start < 0 || end < 0) {
					continue;
				}
				Map<String, String> params = params(link.substring(end + 1));
				if (relations(params.get("rel")).contains("linkset")) {
					resolve(base, link.substring(start + 1, end)).ifPresent(u -> found.add(new Reference(u, params.get("type"))));
				}
			}
		}
		Matcher tags = LINK_TAG.matcher(html);
		while (tags.find()) {
			Map<String, String> attributes = new HashMap<>();
			Matcher m = ATTRIBUTE.matcher(tags.group(1));
			while (m.find()) {
				String value = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
				attributes.putIfAbsent(m.group(1).toLowerCase(Locale.ROOT), value.replace("&amp;", "&"));
			}
			if (relations(attributes.get("rel")).contains("linkset")) {
				resolve(base, attributes.getOrDefault("href", "")).ifPresent(u -> found.add(new Reference(u, attributes.get("type"))));
			}
		}
		return found.stream().distinct().sorted(java.util.Comparator.comparingInt(Linkset::order)).toList();
	}

	private static int order(Reference reference) {
		String type = reference.type() == null ? "" : reference.type().toLowerCase(Locale.ROOT);
		return type.contains("json") ? 0 : type.equals("application/linkset") ? 1 : 2;
	}

	/**
	 * The entries of a link set, of either format.
	 *
	 * @param type The media type it was served as, to tell the format; else by
	 *             its first character
	 * @param base Where it was fetched from, for relative addresses
	 * @return None if it can't be read as either
	 */
	static List<Entry> parse(byte[] body, String type, URI base) {
		String text = new String(body, StandardCharsets.UTF_8);
		boolean json = (type != null && type.toLowerCase(Locale.ROOT).contains("json")) || text.stripLeading().startsWith("{");
		try {
			return json ? parseJson(new ObjectMapper().readTree(body), base) : parseText(text, base);
		} catch (java.io.IOException | RuntimeException e) {
			return List.of();
		}
	}

	private static List<Entry> parseJson(JsonNode root, URI base) {
		List<Entry> entries = new ArrayList<>();
		for (JsonNode entry : root.path("linkset")) {
			Optional<URI> anchor = resolve(base, entry.path("anchor").asText(""));
			List<Link> links = new ArrayList<>();
			entry.properties().forEach(field -> {
				if (field.getKey().equals("anchor")) {
					return;
				}
				JsonNode targets = field.getValue();
				for (JsonNode target : targets.isArray() ? targets : List.of(targets)) {
					if (target.isObject()) {
						resolve(base, target.path("href").asText("")).ifPresent(href -> links.add(new Link(field.getKey(), href,
								target.has("type") ? target.get("type").asText() : null,
								target.has("profile") ? target.get("profile").asText() : null)));
					}
				}
			});
			entries.add(new Entry(anchor, links));
		}
		return entries;
	}

	/** The text format: the syntax of the Link header, a link to a line or several, each with its own anchor */
	private static List<Entry> parseText(String text, URI base) {
		Map<Optional<URI>, List<Link>> byAnchor = new LinkedHashMap<>();
		for (String link : text.split(",(?=\\s*<)|\\r?\\n")) {
			int start = link.indexOf('<');
			int end = link.indexOf('>', start + 1);
			if (start < 0 || end < 0) {
				continue;
			}
			Map<String, String> params = params(link.substring(end + 1));
			Optional<URI> anchor = params.containsKey("anchor") ? resolve(base, params.get("anchor")) : Optional.empty();
			Optional<URI> href = resolve(base, link.substring(start + 1, end));
			if (href.isPresent()) {
				for (String rel : relations(params.get("rel"))) {
					byAnchor.computeIfAbsent(anchor, a -> new ArrayList<>())
							.add(new Link(rel, href.get(), params.get("type"), params.get("profile")));
				}
			}
		}
		return byAnchor.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList();
	}

	/**
	 * The links that are about the page: those of the entries whose anchor is one
	 * of its addresses (as it was asked for, where it ended up after redirects,
	 * its persistent identifier), else, as ROHub's is anchored at the API address
	 * of the resource it describes, those of the one entry that has any
	 * <code>describedby</code> or <code>item</code>. Not those of other anchors,
	 * such as a file's own link back to its page.
	 */
	static List<Link> about(List<Entry> entries, Set<URI> addresses) {
		Set<String> wanted = new java.util.HashSet<>();
		addresses.forEach(a -> wanted.add(normalise(a)));
		List<Link> about = new ArrayList<>();
		for (Entry entry : entries) {
			if (entry.anchor().isEmpty() || wanted.contains(normalise(entry.anchor().get()))) {
				about.addAll(entry.links());
			}
		}
		if (!about.isEmpty()) {
			return about;
		}
		List<Entry> describing = entries.stream()
				.filter(e -> e.links().stream().anyMatch(l -> l.rel().equals("describedby") || l.rel().equals("item"))).toList();
		return describing.size() == 1 ? describing.get(0).links() : List.of();
	}

	/** Without a fragment or a final /, so that the same address written either way is one */
	private static String normalise(URI uri) {
		String text = uri.toString().replaceFirst("#.*$", "");
		return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
	}

	private static Map<String, String> params(String text) {
		Map<String, String> params = new HashMap<>();
		Matcher m = PARAM.matcher(text);
		while (m.find()) {
			params.putIfAbsent(m.group(1).toLowerCase(Locale.ROOT), m.group(2) != null ? m.group(2) : m.group(3));
		}
		return params;
	}

	private static List<String> relations(String rel) {
		return rel == null ? List.of() : List.of(rel.toLowerCase(Locale.ROOT).split("\\s+"));
	}

	private static Optional<URI> resolve(URI base, String reference) {
		if (reference.isBlank()) {
			return Optional.empty();
		}
		try {
			URI uri = base.resolve(reference.trim());
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			// Never from the web to local files
			return scheme.equals("http") || scheme.equals("https") ? Optional.of(uri) : Optional.empty();
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
