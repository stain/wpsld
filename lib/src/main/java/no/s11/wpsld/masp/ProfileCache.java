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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateDocument.Problem;
import no.s11.wpsld.crate.CrateDocument.Severity;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateRetriever;
import no.s11.wpsld.jsonld.JsonLdContext;
import no.s11.wpsld.util.AppDirectories;
import no.s11.wpsld.util.Value;

/**
 * MASP profiles: those bundled with the application, those the user loaded
 * before and kept in their store, found by identifier, and others loaded when
 * asked for by URL.
 * <p>
 * Bundled profiles are listed in <code>masp-profiles.json</code> files at the
 * root of the class path (written by <code>scripts/update-masp-profiles.sh</code>),
 * each with its <code>identifiers</code> and <code>X-Classpath</code>, and read
 * on first use. Separate from the JSON-LD {@link no.s11.wpsld.jsonld.ContextCache}:
 * profiles are RO-Crates using contexts.
 * <p>
 * With a store folder ({@link #forUser()}: <code>profiles</code> in the user's
 * data folder), profiles loaded by URL or file are copied there and listed in
 * its <code>profiles.json</code> with where they came from, so they are
 * available next time, also offline. Loading the same profile again (the same
 * URL, or a profile with one of its identifiers) replaces the copy.
 * <p>
 * Registries ({@link ProfileRegistry}) list profiles to choose from, and
 * when a profile's identifier doesn't lead to its crate, say where it is.
 * <p>
 * {@link #load(URI)} never throws and never waits longer than its time
 * limits: a profile that can't be loaded comes back with ERROR problems, and
 * is not cached, so it can be tried again. Run it in the background, as it
 * may wait for the network.
 */
public class ProfileCache {
	public static final String INDEX = "masp-profiles.json";
	/** The index of a store folder */
	public static final String STORE_INDEX = "profiles.json";
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
	private final Optional<Path> store;
	/** Entries of the store's index, in the order loaded */
	private final List<ObjectNode> storedEntries = new ArrayList<>();
	/** Stored profiles read, by file name */
	private final Map<String, Profile> storedRead = new ConcurrentHashMap<>();
	private final Value<CrateRetriever> retriever;
	private final List<ProfileRegistry> registries;
	/** Names of registries not to use */
	private volatile Set<String> disabledRegistries = Set.of();
	/** Entries of the registries read without problems, kept for the session */
	private final Map<ProfileRegistry, List<ProfileRegistry.Entry>> registryEntries = new ConcurrentHashMap<>();

	/**
	 * A profile kept in the store, as its index lists it; also one whose copy
	 * can no longer be read, so that it can be removed.
	 *
	 * @param file      Its file in the store
	 * @param name      Its name, if known
	 * @param version   Its version, if given
	 * @param location  Where it was loaded from, as asked
	 * @param source    The URL its crate came from in the end
	 * @param retrieved When, if known
	 */
	public record Kept(String file, Optional<String> name, Optional<String> version, String location, String source,
			Optional<Instant> retrieved) {
	}

	/**
	 * The profiles listed by the registries, and problems reading them.
	 */
	public record Listing(List<ProfileRegistry.Entry> entries, List<Problem> problems) {
		public Listing {
			entries = List.copyOf(entries);
			problems = List.copyOf(problems);
		}
	}

	/**
	 * The profiles bundled with the application, shared.
	 */
	public static ProfileCache bundled() {
		return BUNDLED.get();
	}

	/**
	 * The bundled profiles, and those the user loaded, kept in the
	 * <code>profiles</code> folder of their data folder (see
	 * {@link AppDirectories#data()}).
	 */
	public static ProfileCache forUser() {
		return new ProfileCache(ProfileCache.class.getClassLoader(), new CrateReader(), DEFAULT_TIMEOUT,
				Optional.of(AppDirectories.system().data().resolve("profiles")),
				ProfileRegistry.known(httpClient(DEFAULT_TIMEOUT), DEFAULT_TIMEOUT));
	}

	/**
	 * An HTTP client following redirects, as for loading profiles and reading
	 * registries.
	 */
	public static HttpClient httpClient(Duration timeout) {
		return HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL).build();
	}

	public ProfileCache() {
		this(ProfileCache.class.getClassLoader(), new CrateReader(), DEFAULT_TIMEOUT);
	}

	/**
	 * Without a store.
	 */
	public ProfileCache(ClassLoader classLoader, CrateReader crateReader, Duration timeout) {
		this(classLoader, crateReader, timeout, Optional.empty());
	}

	/**
	 * @param classLoader To find <code>masp-profiles.json</code> indexes
	 * @param crateReader To read profile crates
	 * @param timeout     Longest wait to connect, and for each response, when
	 *                    loading a profile by URL
	 * @param store       Folder to keep loaded profiles in for next time,
	 *                    created when first needed
	 */
	public ProfileCache(ClassLoader classLoader, CrateReader crateReader, Duration timeout, Optional<Path> store) {
		this(classLoader, crateReader, timeout, store, List.of());
	}

	/**
	 * @param classLoader To find <code>masp-profiles.json</code> indexes
	 * @param crateReader To read profile crates
	 * @param timeout     Longest wait to connect, and for each response, when
	 *                    loading a profile by URL
	 * @param store       Folder to keep loaded profiles in for next time,
	 *                    created when first needed
	 * @param registries  Registries to list profiles from, and to look up
	 *                    identifiers that don't lead to a profile crate
	 */
	public ProfileCache(ClassLoader classLoader, CrateReader crateReader, Duration timeout, Optional<Path> store,
			List<ProfileRegistry> registries) {
		this.registries = List.copyOf(registries);
		this.crateReader = crateReader;
		this.reader = new ProfileReader(crateReader);
		this.timeout = timeout;
		this.store = store;
		this.retriever = Value.lazy(() -> new CrateRetriever(httpClient(timeout), timeout));
		try {
			Enumeration<URL> indexes = classLoader.getResources(INDEX);
			while (indexes.hasMoreElements()) {
				addIndex(indexes.nextElement());
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Can't list " + INDEX + " resources", e);
		}
		store.map(s -> s.resolve(STORE_INDEX)).filter(Files::isRegularFile).ifPresent(this::readStoreIndex);
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
	 * Read the store's index. Stored copies are the user's, so a broken
	 * index or entry is skipped rather than stopping the application.
	 */
	private void readStoreIndex(Path index) {
		try {
			for (JsonNode entry : MAPPER.readTree(index.toFile())) {
				if (entry instanceof ObjectNode object && !object.path("file").asText("").isEmpty()) {
					storedEntries.add(object);
				}
			}
		} catch (IOException e) {
			// As if nothing was stored; the next load writes a new index
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
	 * The profiles kept in the store, most recently loaded last. Copies that
	 * can no longer be read are left out.
	 */
	public synchronized List<Profile> stored() {
		return storedEntries.stream().map(this::readStored).flatMap(Optional::stream).toList();
	}

	private Optional<Profile> readStored(ObjectNode entry) {
		String file = entry.path("file").asText();
		Profile profile = storedRead.computeIfAbsent(file, f -> {
			Path path = store.orElseThrow().resolve(f);
			String source = entry.path("source").asText(path.toUri().toString());
			try (InputStream in = Files.newInputStream(path)) {
				return reader.read(crateReader.read(in, source));
			} catch (IOException e) {
				return failed(URI.create(source), "Can't read the stored copy " + path + ": " + e.getMessage());
			}
		});
		return profile.hasErrors() ? Optional.empty() : Optional.of(profile);
	}

	/**
	 * The profiles to choose from: the bundled ones, then those kept in the
	 * store, then any others loaded in this session.
	 */
	public List<Profile> available() {
		Set<Profile> profiles = new LinkedHashSet<>();
		bundled.keySet().stream().map(this::bundled).flatMap(Optional::stream).forEach(profiles::add);
		profiles.addAll(stored());
		profiles.addAll(loaded.values());
		return List.copyOf(profiles);
	}

	/**
	 * A profile by identifier: bundled, loaded in this session, or kept in the
	 * store.
	 */
	public Optional<Profile> find(String identifier) {
		return bundled(identifier)
				.or(() -> loaded.values().stream().filter(p -> p.identifiers().contains(identifier)).findFirst())
				.or(() -> stored().stream().filter(p -> p.identifiers().contains(identifier)).findFirst());
	}

	/**
	 * Load a profile crate from a URL: <code>https:</code>, <code>http:</code> or
	 * <code>file:</code>, of its <code>ro-crate-metadata.json</code>, its folder,
	 * or a page or identifier leading to it (see {@link CrateRetriever}). Never
	 * throws; may wait up to the time limits for the network. Once loaded
	 * without errors, it is kept for this session, and in the store if there is
	 * one; a problem storing it is a warning of the profile.
	 */
	public Profile load(URI location) {
		Profile profile = load(location, location);
		if (!profile.hasErrors() || enabledRegistries().isEmpty()) {
			return profile;
		}
		// The identifier may not lead to the crate, but a registry knows where it is
		Optional<ProfileRegistry.Entry> listed = registryEntries().entries().stream()
				.filter(e -> e.identifiedBy(location.toString()) && !e.location().equals(location)).findFirst();
		if (listed.isEmpty()) {
			return profile;
		}
		Profile fromRegistry = load(listed.get().location(), location);
		return fromRegistry.hasErrors() ? profile : fromRegistry;
	}

	/**
	 * The folder loaded profiles are kept in, if any.
	 */
	public Optional<Path> store() {
		return store;
	}

	/**
	 * All the registries, also those turned off.
	 */
	public List<ProfileRegistry> registries() {
		return registries;
	}

	/**
	 * Turn registries off, by name; the others are used.
	 */
	public void setDisabledRegistries(Set<String> names) {
		disabledRegistries = Set.copyOf(names);
	}

	private List<ProfileRegistry> enabledRegistries() {
		Set<String> disabled = disabledRegistries;
		return registries.stream().filter(r -> !disabled.contains(r.name())).toList();
	}

	/**
	 * The profiles kept in the store, most recently loaded last.
	 */
	public synchronized List<Kept> kept() {
		return storedEntries.stream().map(e -> {
			Optional<Instant> retrieved;
			try {
				retrieved = Optional.of(Instant.parse(e.path("retrieved").asText()));
			} catch (RuntimeException ex) {
				retrieved = Optional.empty();
			}
			return new Kept(e.path("file").asText(), text(e, "name"), text(e, "version"),
					e.path("location").asText(""), e.path("source").asText(""), retrieved);
		}).toList();
	}

	private static Optional<String> text(JsonNode entry, String field) {
		return Optional.of(entry.path(field).asText("")).filter(s -> !s.isBlank());
	}

	/**
	 * Remove a kept profile from the store, and from this session.
	 *
	 * @return The profile as it was read, if it was, e.g. to stop offering it
	 */
	public synchronized Optional<Profile> forget(Kept kept) throws IOException {
		Path dir = store.orElseThrow();
		storedEntries.removeIf(e -> e.path("file").asText().equals(kept.file()));
		writeStoreIndex(dir);
		Files.deleteIfExists(dir.resolve(kept.file()));
		Optional<Profile> profile = Optional.ofNullable(storedRead.remove(kept.file()));
		profile.ifPresent(p -> loaded.values().removeIf(l -> l == p));
		return profile;
	}

	/**
	 * The profiles listed by the registries: those read before in this
	 * session, else read now. Never throws; a registry that can't be read is
	 * a warning, and is tried again next time.
	 */
	public Listing registryEntries() {
		List<ProfileRegistry.Entry> entries = new ArrayList<>();
		List<Problem> problems = new ArrayList<>();
		for (ProfileRegistry registry : enabledRegistries()) {
			List<ProfileRegistry.Entry> listed = registryEntries.get(registry);
			if (listed == null) {
				try {
					listed = registry.entries();
					registryEntries.put(registry, listed);
				} catch (HttpTimeoutException e) {
					problems.add(new Problem(Severity.WARNING,
							"Can't read " + registry.name() + ": no response within " + timeout.toSeconds() + " s"));
					continue;
				} catch (IOException | RuntimeException e) {
					problems.add(new Problem(Severity.WARNING, "Can't read " + registry.name() + ": "
							+ (e.getMessage() == null ? e.toString() : e.getMessage())));
					continue;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					problems.add(new Problem(Severity.WARNING, "Reading " + registry.name() + " was interrupted"));
					break;
				}
			}
			entries.addAll(listed);
		}
		return new Listing(entries, problems);
	}

	/**
	 * Load from one place, kept as loaded from another, e.g. the identifier
	 * asked for when a registry said where the crate is.
	 */
	private Profile load(URI from, URI location) {
		Profile cached = loaded.get(location);
		if (cached != null) {
			return cached;
		}
		String scheme = from.getScheme() == null ? "" : from.getScheme();
		if (!List.of("file", "http", "https").contains(scheme)) {
			return failed(from, "Can't load a profile from " + from + ": only https:, http: and file: URLs");
		}
		CrateRetriever.Retrieved retrieved;
		try {
			retrieved = retriever.get().retrieve(from);
		} catch (HttpTimeoutException e) {
			return failed(from, "Can't load " + from + ": no response within " + timeout.toSeconds() + " s");
		} catch (IOException e) {
			String message = e.getMessage() == null ? e.toString() : e.getMessage();
			return failed(from, message.startsWith("Can't") ? message : "Can't load " + from + ": " + message);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return failed(from, "Loading " + from + " was interrupted");
		}
		Profile profile = reader.read(
				crateReader.read(new ByteArrayInputStream(retrieved.content()), retrieved.source().toString()));
		if (profile.hasErrors()) {
			return profile;
		}
		if (store.isPresent()) {
			try {
				keep(location, retrieved, profile);
			} catch (IOException e) {
				profile = profile.withProblem(new Problem(Severity.WARNING,
						"Loaded, but can't keep a copy for next time in " + store.get() + ": " + e.getMessage()));
			}
		}
		loaded.put(location, profile);
		loaded.put(retrieved.source(), profile);
		return profile;
	}

	/**
	 * Copy a loaded profile to the store, replacing one from the same location
	 * or with one of the same identifiers.
	 */
	private synchronized void keep(URI location, CrateRetriever.Retrieved retrieved, Profile profile)
			throws IOException {
		Path dir = AppDirectories.create(store.orElseThrow());
		String file = "profile-" + sha256(location.toString().getBytes(StandardCharsets.UTF_8)).substring(0, 16)
				+ ".json";
		Path temp = Files.createTempFile(dir, "profile-", ".tmp");
		Files.write(temp, retrieved.content());
		Files.move(temp, dir.resolve(file), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

		List<ObjectNode> replaced = storedEntries.stream()
				.filter(e -> e.path("location").asText().equals(location.toString()) || e.path("file").asText().equals(file)
						|| texts(e.path("identifiers")).stream().anyMatch(profile.identifiers()::contains))
				.toList();
		for (ObjectNode old : replaced) {
			storedEntries.remove(old);
			storedRead.remove(old.path("file").asText());
			if (!old.path("file").asText().equals(file)) {
				Files.deleteIfExists(dir.resolve(old.path("file").asText()));
			}
		}
		ObjectNode entry = MAPPER.createObjectNode();
		ArrayNode identifiers = entry.putArray("identifiers");
		profile.identifiers().forEach(identifiers::add);
		entry.put("file", file);
		profile.name().ifPresent(n -> entry.put("name", n));
		profile.version().ifPresent(v -> entry.put("version", v));
		entry.put("location", location.toString());
		entry.put("source", retrieved.source().toString());
		entry.put("retrieved", Instant.now().toString());
		entry.put("sha256", sha256(retrieved.content()));
		storedEntries.add(entry);
		storedRead.put(file, profile);

		writeStoreIndex(dir);
	}

	private void writeStoreIndex(Path dir) throws IOException {
		AppDirectories.create(dir);
		Path index = Files.createTempFile(dir, "profiles-", ".tmp");
		MAPPER.writer(SerializationFeature.INDENT_OUTPUT).writeValue(index.toFile(), storedEntries);
		Files.move(index, dir.resolve(STORE_INDEX), StandardCopyOption.REPLACE_EXISTING,
				StandardCopyOption.ATOMIC_MOVE);
	}

	private static List<String> texts(JsonNode array) {
		List<String> texts = new ArrayList<>();
		array.forEach(t -> texts.add(t.asText()));
		return texts;
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private Profile failed(URI uri, String message) {
		CrateDocument document = new CrateDocument(uri.toString(), Optional.empty(), JsonLdContext.EMPTY,
				Optional.empty(), List.of(), List.of(new Problem(Severity.ERROR, message)));
		return reader.read(document);
	}
}
