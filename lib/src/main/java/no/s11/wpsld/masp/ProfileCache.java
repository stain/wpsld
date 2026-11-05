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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateDocument.Problem;
import no.s11.wpsld.crate.CrateDocument.Severity;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.jsonld.JsonLdContext;
import no.s11.wpsld.util.Value;

/**
 * MASP profiles: those bundled with the application, found by identifier,
 * and others loaded when asked for by URL.
 * <p>
 * Bundled profiles are listed in <code>masp-profiles.json</code> files at the
 * root of the class path (written by <code>scripts/update-masp-profiles.sh</code>),
 * each with its <code>identifiers</code> and <code>X-Classpath</code>, and read
 * on first use. Separate from the JSON-LD {@link no.s11.wpsld.jsonld.ContextCache}:
 * profiles are RO-Crates using contexts.
 * <p>
 * {@link #load(URI)} never throws and never waits longer than its time
 * limits: a profile that can't be loaded comes back with ERROR problems, and
 * is not cached, so it can be tried again. Run it in the background, as it
 * may wait for the network.
 */
public class ProfileCache {
	public static final String INDEX = "masp-profiles.json";
	public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final Value<ProfileCache> BUNDLED = Value.lazy(ProfileCache::new);

	private final ProfileReader reader;
	private final CrateReader crateReader;
	private final Duration timeout;
	/** Identifier of a bundled profile to its resource */
	private final Map<String, URL> bundled = new LinkedHashMap<>();
	/** Bundled profiles read, by resource */
	private final Map<URL, Profile> read = new ConcurrentHashMap<>();
	/** Profiles loaded by URL, only those without errors */
	private final Map<URI, Profile> loaded = new ConcurrentHashMap<>();
	private final Value<HttpClient> http;

	/**
	 * The profiles bundled with the application, shared.
	 */
	public static ProfileCache bundled() {
		return BUNDLED.get();
	}

	public ProfileCache() {
		this(ProfileCache.class.getClassLoader(), new CrateReader(), DEFAULT_TIMEOUT);
	}

	/**
	 * @param classLoader To find <code>masp-profiles.json</code> indexes
	 * @param crateReader To read profile crates
	 * @param timeout     Longest wait to connect, and for a response, when
	 *                    loading a profile by URL
	 */
	public ProfileCache(ClassLoader classLoader, CrateReader crateReader, Duration timeout) {
		this.crateReader = crateReader;
		this.reader = new ProfileReader(crateReader);
		this.timeout = timeout;
		this.http = Value.lazy(() -> HttpClient.newBuilder().connectTimeout(timeout)
				.followRedirects(HttpClient.Redirect.NORMAL).build());
		try {
			Enumeration<URL> indexes = classLoader.getResources(INDEX);
			while (indexes.hasMoreElements()) {
				addIndex(indexes.nextElement());
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Can't list " + INDEX + " resources", e);
		}
	}

	private void addIndex(URL index) throws IOException {
		JsonNode entries;
		try (InputStream in = index.openStream()) {
			entries = MAPPER.readTree(in);
		}
		String base = index.toString().substring(0, index.toString().lastIndexOf('/') + 1);
		for (JsonNode entry : entries) {
			String path = entry.path("X-Classpath").asText("");
			if (path.isEmpty()) {
				continue;
			}
			URL resource = URI.create(base + path).toURL();
			// The first jar listing an identifier wins
			entry.path("identifiers").forEach(id -> bundled.putIfAbsent(id.asText(), resource));
		}
	}

	/**
	 * Identifiers of the bundled profiles.
	 */
	public Set<String> bundledIdentifiers() {
		return Collections.unmodifiableSet(bundled.keySet());
	}

	/**
	 * A bundled profile by one of its identifiers. Bundled profiles are part
	 * of the application, so one that can't be read is an error in it,
	 * reported as an exception.
	 */
	public Optional<Profile> bundled(String identifier) {
		URL resource = bundled.get(identifier);
		if (resource == null) {
			return Optional.empty();
		}
		return Optional.of(read.computeIfAbsent(resource, r -> {
			try (InputStream in = r.openStream()) {
				return reader.read(crateReader.read(in, r.toString()));
			} catch (IOException e) {
				throw new UncheckedIOException("Can't read bundled profile " + r, e);
			}
		}));
	}

	/**
	 * A profile by identifier: bundled, or loaded before by URL.
	 */
	public Optional<Profile> find(String identifier) {
		return bundled(identifier).or(() -> loaded.values().stream()
				.filter(p -> p.identifiers().contains(identifier)).findFirst());
	}

	/**
	 * Load a profile crate from a URL: <code>https:</code>, <code>http:</code> or
	 * <code>file:</code>, of its <code>ro-crate-metadata.json</code> or its
	 * folder (ending with <code>/</code>). Never throws; may wait up to the
	 * time limits for the network.
	 */
	public Profile load(URI location) {
		URI uri = location.getPath() != null && location.getPath().endsWith("/")
				? location.resolve(Crate.RO_CRATE_METADATA_JSON)
				: location;
		Profile cached = loaded.get(uri);
		if (cached != null) {
			return cached;
		}
		Profile profile = switch (uri.getScheme() == null ? "" : uri.getScheme()) {
		case "file" -> reader.read(crateReader.read(Path.of(uri)));
		case "http", "https" -> fetch(uri);
		default -> failed(uri, "Can't load a profile from " + uri + ": only https:, http: and file: URLs");
		};
		if (!profile.hasErrors()) {
			loaded.put(uri, profile);
		}
		return profile;
	}

	private Profile fetch(URI uri) {
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
				.header("Accept", "application/ld+json, application/json;q=0.9").GET().build();
		try {
			HttpResponse<InputStream> response = http.get().send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (InputStream body = response.body()) {
				if (response.statusCode() != 200) {
					return failed(uri, "Can't load " + uri + ": HTTP " + response.statusCode());
				}
				return reader.read(crateReader.read(body, uri.toString()));
			}
		} catch (HttpTimeoutException e) {
			return failed(uri, "Can't load " + uri + ": no response within " + timeout.toSeconds() + " s");
		} catch (IOException e) {
			return failed(uri, "Can't load " + uri + ": " + e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return failed(uri, "Loading " + uri + " was interrupted");
		}
	}

	private Profile failed(URI uri, String message) {
		CrateDocument document = new CrateDocument(uri.toString(), Optional.empty(), JsonLdContext.EMPTY,
				Optional.empty(), List.of(), List.of(new Problem(Severity.ERROR, message)));
		return reader.read(document);
	}
}
