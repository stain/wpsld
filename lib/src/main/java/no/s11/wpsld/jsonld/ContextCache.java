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
package no.s11.wpsld.jsonld;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import no.s11.wpsld.util.Value;

/**
 * A pre-warmed cache of JSON-LD context documents, bundled with the
 * application and looked up by their URL, without network access.
 * <p>
 * Contexts are listed in <code>jarcache.json</code> files at the root of the
 * class path, in the same format as
 * <a href="https://github.com/jsonld-java/jsonld-java#loading-contexts-from-classpath">JSON-LD
 * Java</a>, so other jars can add contexts:
 *
 * <pre>
 * [ { "Content-Location": "https://w3id.org/ro/crate/1.3/context",
 *     "X-Classpath": "jsonld-contexts/ro-crate-1.3-context.jsonld",
 *     "Content-Type": "application/ld+json" } ]
 * </pre>
 *
 * <code>X-Classpath</code> is relative to the <code>jarcache.json</code>. The
 * folder and index are not Java packages, so the module system doesn't hide
 * them from other modules. Each context is parsed on first use and then kept.
 * <p>
 * Contexts only: profiles (e.g. RO-Crate MASP profile crates) are RO-Crates
 * using contexts, and are loaded and cached separately.
 */
public class ContextCache {
	public static final String JARCACHE = "jarcache.json";
	private static final Logger LOGGER = System.getLogger(ContextCache.class.getName());
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final Value<ContextCache> BUNDLED = Value.lazy(ContextCache::new);

	/** Context URL to the bundled resource with its content */
	private final Map<String, URL> locations = new LinkedHashMap<>();
	private final Map<String, Optional<JsonNode>> loaded = new ConcurrentHashMap<>();

	/**
	 * The contexts bundled with the application, shared.
	 */
	public static ContextCache bundled() {
		return BUNDLED.get();
	}

	public ContextCache() {
		this(ContextCache.class.getClassLoader());
	}

	/**
	 * The contexts listed in all <code>jarcache.json</code> files the class
	 * loader finds.
	 */
	public ContextCache(ClassLoader classLoader) {
		try {
			Enumeration<URL> indexes = classLoader.getResources(JARCACHE);
			while (indexes.hasMoreElements()) {
				addIndex(indexes.nextElement());
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Can't list " + JARCACHE + " resources", e);
		}
	}

	private void addIndex(URL index) throws IOException {
		JsonNode entries;
		try (InputStream in = index.openStream()) {
			entries = MAPPER.readTree(in);
		}
		String base = index.toString().substring(0, index.toString().lastIndexOf('/') + 1);
		for (JsonNode entry : entries) {
			String location = entry.path("Content-Location").asText("");
			String path = entry.path("X-Classpath").asText("");
			if (location.isEmpty() || path.isEmpty()) {
				LOGGER.log(Logger.Level.WARNING, "Incomplete entry in {0}: {1}", index, entry);
				continue;
			}
			// The first jar listing a context wins
			locations.putIfAbsent(location, URI.create(base + path).toURL());
		}
	}

	/**
	 * URLs of the contexts in the cache.
	 */
	public Set<String> getLocations() {
		return Collections.unmodifiableSet(locations.keySet());
	}

	/**
	 * The context document for a URL, if it is in the cache. A bundled
	 * document that can't be read is an error in the application, reported as
	 * an exception rather than as a missing context.
	 */
	public Optional<JsonNode> get(String url) {
		return loaded.computeIfAbsent(url, this::load);
	}

	/**
	 * The schema.org release a cached context declares it is based on, in
	 * its <code>schemaVersion</code>, as RO-Crate contexts do: e.g.
	 * <code>30.0</code> from <code>https://schema.org/docs/releases.html#v30.0</code>
	 * (RO-Crate 1.3) or <code>5.0</code> from
	 * <code>http://schema.org/version/5.0/</code> (RO-Crate 1.0).
	 */
	public Optional<String> schemaOrgVersion(String url) {
		return get(url).map(document -> document.path("schemaVersion"))
				.map(v -> v.isObject() ? v.path("@id").asText("") : v.asText(""))
				.flatMap(id -> SCHEMA_VERSION.matcher(id).results().map(m -> m.group(1)).reduce((a, b) -> b));
	}

	private static final Pattern SCHEMA_VERSION = Pattern.compile("(\\d+\\.\\d+)");

	private Optional<JsonNode> load(String url) {
		URL resource = locations.get(url);
		if (resource == null) {
			return Optional.empty();
		}
		try (InputStream in = resource.openStream()) {
			return Optional.of(MAPPER.readTree(in));
		} catch (IOException e) {
			throw new UncheckedIOException("Can't read bundled context " + url + " from " + resource, e);
		}
	}
}
