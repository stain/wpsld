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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds and fetches the metadata document of an RO-Crate from a URL, as in
 * "Retrieving an RO-Crate" of the RO-Crate specification. A URL, such as a
 * persistent identifier or a profile's <code>conformsTo</code> identifier,
 * often leads to a web page rather than to the crate, so after following
 * redirects, stopping at the first that works:
 * <ol>
 * <li>the response itself if it is JSON, as asked for with content negotiation
 * (<code>application/ld+json;profile="https://w3id.org/ro/crate"</code>);
 * <li>FAIR Signposting: a <code>Link</code> header with
 * <code>rel="describedby"</code> to JSON(-LD) or with the RO-Crate profile;
 * <li>the same as a <code>&lt;link&gt;</code> in an HTML page, as RO-Crate
 * previews and MASP profile pages have;
 * <li>for a folder (a path ending with <code>/</code>), its
 * <code>ro-crate-metadata.json</code>.
 * </ol>
 * Zip and BagIt archives (<code>rel="item"</code>) are not supported yet.
 * Whether what comes back is a crate is for {@link CrateReader} to say, read
 * against {@link Retrieved#source()}, the URL it finally came from.
 */
public class CrateRetriever {
	public static final String RO_CRATE_PROFILE = "https://w3id.org/ro/crate";
	public static final String ACCEPT = "application/ld+json;profile=\"" + RO_CRATE_PROFILE
			+ "\", application/ld+json;q=0.9, application/json;q=0.8, text/html;q=0.5, */*;q=0.1";
	/** Largest metadata document read, as anything fetched is untrusted */
	public static final int MAX_BYTES = 16 * 1024 * 1024;
	/** Most requests to retrieve one crate: links followed, then the folder */
	private static final int MAX_REQUESTS = 5;
	private static final Pattern LINK_TAG = Pattern.compile("<link\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
	private static final Pattern ATTRIBUTE = Pattern
			.compile("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))");
	private static final Pattern LINK_PARAM = Pattern.compile(";\\s*([\\w*-]+)\\s*=\\s*(?:\"([^\"]*)\"|([^;,\\s]*))");

	/**
	 * A metadata document as fetched.
	 *
	 * @param source  Where it came from in the end, the base for its relative
	 *                identifiers
	 * @param content Its bytes
	 * @param steps   How it was found from the URL asked for, e.g. "Link
	 *                header", "HTML link", "folder"; empty if the URL gave it
	 */
	public record Retrieved(URI source, byte[] content, List<String> steps) {
		public Retrieved {
			steps = List.copyOf(steps);
		}
	}

	private final HttpClient http;
	private final Duration timeout;

	/**
	 * @param http    For <code>http:</code> and <code>https:</code> URLs,
	 *                following redirects
	 * @param timeout Longest wait for each response
	 */
	public CrateRetriever(HttpClient http, Duration timeout) {
		this.http = http;
		this.timeout = timeout;
	}

	/**
	 * Retrieve the metadata document of a crate from an <code>http:</code>,
	 * <code>https:</code> or <code>file:</code> URL.
	 *
	 * @throws IOException With a message to show, if it can't be found or
	 *                     fetched; {@link java.net.http.HttpTimeoutException}
	 *                     when a response took too long
	 */
	public Retrieved retrieve(URI location) throws IOException, InterruptedException {
		String scheme = location.getScheme() == null ? "" : location.getScheme().toLowerCase(Locale.ROOT);
		return switch (scheme) {
		case "file" -> {
			Path path = Path.of(location);
			Path file = Files.isDirectory(path) ? path.resolve(Crate.RO_CRATE_METADATA_JSON) : path;
			try (InputStream in = Files.newInputStream(file)) {
				yield new Retrieved(file.toUri(), limited(in, file.toUri()), List.of());
			}
		}
		case "http", "https" -> fetch(location, location, new LinkedHashSet<>(), new ArrayList<>());
		default -> throw new IOException("Only https:, http: and file: URLs, not " + location);
		};
	}

	private Retrieved fetch(URI uri, URI asked, Set<URI> requested, List<String> steps)
			throws IOException, InterruptedException {
		if (!requested.add(uri) || requested.size() > MAX_REQUESTS) {
			throw new IOException("Can't find the RO-Crate metadata of " + asked + ": followed " + requested);
		}
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", ACCEPT).GET().build();
		HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
		URI at = response.uri();
		int status = response.statusCode();
		String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
		byte[] body;
		try (InputStream in = response.body()) {
			body = status == 200 ? limited(in, at) : new byte[0];
		}
		if (status == 200 && isJson(type, body)) {
			return new Retrieved(at, body, steps);
		}
		Optional<URI> described = linkHeader(response.headers(), at);
		if (described.isPresent()) {
			return fetch(described.get(), asked, requested, with(steps, "Link header"));
		}
		boolean html = status == 200 && isHtml(type, body);
		if (html) {
			Optional<URI> linked = htmlLink(new String(body, StandardCharsets.UTF_8), at);
			if (linked.isPresent()) {
				return fetch(linked.get(), asked, requested, with(steps, "HTML link"));
			}
		}
		if (at.getPath() != null && at.getPath().endsWith("/")) {
			return fetch(at.resolve(Crate.RO_CRATE_METADATA_JSON), asked, requested, with(steps, "folder"));
		}
		if (html) {
			throw new IOException(at + " is a web page without a link to its RO-Crate metadata");
		}
		if (status == 200) {
			// Not recognised as JSON: let CrateReader say what is wrong
			return new Retrieved(at, body, steps);
		}
		throw new IOException("Can't load " + at + ": HTTP " + status);
	}

	private static List<String> with(List<String> steps, String step) {
		List<String> more = new ArrayList<>(steps);
		more.add(step);
		return more;
	}

	private static byte[] limited(InputStream in, URI from) throws IOException {
		byte[] bytes = in.readNBytes(MAX_BYTES + 1);
		if (bytes.length > MAX_BYTES) {
			throw new IOException(from + " is larger than " + MAX_BYTES / (1024 * 1024) + " MB");
		}
		return bytes;
	}

	private static boolean isJson(String type, byte[] body) {
		if (type.contains("json")) {
			return true;
		}
		return !type.contains("html") && firstCharacter(body) == '{';
	}

	private static boolean isHtml(String type, byte[] body) {
		return type.contains("html") || type.isEmpty() && firstCharacter(body) == '<';
	}

	private static int firstCharacter(byte[] body) {
		for (byte b : body) {
			if (!Character.isWhitespace(b) && b != (byte) 0xEF && b != (byte) 0xBB && b != (byte) 0xBF) {
				return b;
			}
		}
		return -1;
	}

	/**
	 * Whether a link with these parameters or attributes leads to RO-Crate
	 * metadata: <code>rel</code> describedby, and the RO-Crate profile or a
	 * JSON(-LD) type. Those with the profile come first.
	 */
	private static int rank(Map<String, String> params) {
		List<String> rels = List.of(params.getOrDefault("rel", "").toLowerCase(Locale.ROOT).split("\\s+"));
		if (!rels.contains("describedby")) {
			return 0;
		}
		if (params.getOrDefault("profile", "").contains(RO_CRATE_PROFILE)) {
			return 2;
		}
		String type = params.getOrDefault("type", "").toLowerCase(Locale.ROOT).replaceFirst(";.*", "").trim();
		return type.equals("application/ld+json") || type.equals("application/json") ? 1 : 0;
	}

	/**
	 * The best <code>describedby</code> link of Link headers (RFC 8288).
	 */
	static Optional<URI> linkHeader(HttpHeaders headers, URI base) {
		URI best = null;
		int bestRank = 0;
		for (String header : headers.allValues("Link")) {
			for (String link : header.split(",(?=\\s*<)")) {
				int start = link.indexOf('<');
				int end = link.indexOf('>', start + 1);
				if (start < 0 || end < 0) {
					continue;
				}
				Map<String, String> params = new HashMap<>();
				Matcher m = LINK_PARAM.matcher(link.substring(end + 1));
				while (m.find()) {
					params.putIfAbsent(m.group(1).toLowerCase(Locale.ROOT), m.group(2) != null ? m.group(2) : m.group(3));
				}
				int rank = rank(params);
				Optional<URI> target = resolve(base, link.substring(start + 1, end));
				if (rank > bestRank && target.isPresent()) {
					best = target.get();
					bestRank = rank;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * The best <code>&lt;link rel="describedby"&gt;</code> of an HTML page.
	 */
	static Optional<URI> htmlLink(String html, URI base) {
		URI best = null;
		int bestRank = 0;
		Matcher tags = LINK_TAG.matcher(html);
		while (tags.find()) {
			Map<String, String> attributes = new HashMap<>();
			Matcher m = ATTRIBUTE.matcher(tags.group(1));
			while (m.find()) {
				String value = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(4);
				attributes.putIfAbsent(m.group(1).toLowerCase(Locale.ROOT), value.replace("&amp;", "&"));
			}
			int rank = rank(attributes);
			Optional<URI> target = resolve(base, attributes.getOrDefault("href", ""));
			if (rank > bestRank && target.isPresent()) {
				best = target.get();
				bestRank = rank;
			}
		}
		return Optional.ofNullable(best);
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
