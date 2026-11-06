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

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import no.s11.wpsld.crate.CrateDocument.Severity;
import no.s11.wpsld.crate.CrateReader;

public class TestProfileCache {
	private static final String CORE = "https://language-research-technology.github.io/ro-crate-masp/profiles/ro-crate/profile-crate/";
	private static final String FIXTURES = "/no/s11/wpsld/crate/masp/";

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private HttpServer server;
	private final AtomicInteger requests = new AtomicInteger();

	/**
	 * Serves the LDAC profile fixture in the ways profiles are published, and
	 * paths that fail in different ways
	 */
	@Before
	public void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requests.incrementAndGet();
			String path = exchange.getRequestURI().getPath();
			String accept = exchange.getRequestHeaders().getFirst("Accept");
			byte[] body;
			int status = 200;
			switch (path) {
			case "/ldac/ro-crate-metadata.json" -> body = ldac();
			case "/slow/ro-crate-metadata.json" -> {
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				body = new byte[0];
			}
			case "/notjson/ro-crate-metadata.json" -> body = "Not a crate".getBytes(StandardCharsets.UTF_8);
			// As GitHub Pages serves MASP profiles: the HTML page links to its crate
			case "/pages/" -> body = html(exchange,
					"<link rel='stylesheet' href='style.css'><link rel=\"describedby\" href=\"../ldac/ro-crate-metadata.json\" type=\"application/ld+json\">");
			// FAIR Signposting
			case "/header" -> {
				exchange.getResponseHeaders().add("Link", "<https://example.com/pid>; rel=\"cite-as\", <"
						+ url("/ldac/ro-crate-metadata.json")
						+ ">; rel=\"describedby\"; type=\"application/ld+json\"; profile=\"https://w3id.org/ro/crate\"");
				body = html(exchange, "");
			}
			// As w3id.org redirects to the folder
			case "/w3id" -> {
				exchange.getResponseHeaders().add("Location", url("/pages/").toString());
				status = 302;
				body = new byte[0];
			}
			// Content negotiation
			case "/conneg" -> {
				if (accept != null && accept.contains("application/ld+json")) {
					exchange.getResponseHeaders().add("Content-Type", "application/ld+json");
					body = ldac();
				} else {
					body = html(exchange, "");
				}
			}
			// Registries: the MASP catalogue, and SPARQL results as from profiles.ro-crate.org
			case "/catalogue.json" -> body = """
					{"entries": [
					  {"id": "ldac", "kind": "profile", "name": "LDAC Profile", "version": "0.1.0", "status": "",
					   "crate": "ldac/ro-crate-metadata.json"},
					  {"id": "draft", "kind": "profile", "name": "Draft Profile", "status": "draft",
					   "crate": "draft/profile-crate/ro-crate-metadata.json"},
					  {"id": "ldac", "kind": "schema", "name": "LDAC Schema", "crate": "schemas/ldac/ro-crate-metadata.json"},
					  {"kind": "profile", "crate": "nameless/ro-crate-metadata.json"}
					]}""".getBytes(StandardCharsets.UTF_8);
			case "/sparql" -> {
				exchange.getResponseHeaders().add("Content-Type", "application/sparql-results+json");
				body = ("""
						{"head": {"vars": ["profile", "name", "version", "descriptor", "aliases"]},
						 "results": {"bindings": [
						  {"profile": {"type": "uri", "value": "%s"},
						   "name": {"type": "literal", "value": "Registered Profile"},
						   "version": {"type": "literal", "value": "1.0"},
						   "descriptor": {"type": "uri", "value": "%s"},
						   "aliases": {"type": "literal", "value": "https://profiles.example.org/data/profile/123"}},
						  {"name": {"type": "literal", "value": "No identifier"}}
						 ]}}""").formatted(url("/unresolvable"), url("/ldac/ro-crate-metadata.json"))
						.getBytes(StandardCharsets.UTF_8);
			}
			case "/webpage" -> body = html(exchange, "<link rel='alternate' href='other.html'>");
			case "/loop/" -> body = html(exchange, "<link rel='describedby' type='application/ld+json' href='./'>");
			default -> {
				status = 404;
				body = "Not found".getBytes(StandardCharsets.UTF_8);
			}
			}
			exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
	}

	private byte[] ldac() throws IOException {
		try (InputStream in = getClass().getResourceAsStream(FIXTURES + "ldac-profile.json")) {
			return in.readAllBytes();
		}
	}

	private static byte[] html(HttpExchange exchange, String head) {
		exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
		return ("<!DOCTYPE html><html><head>" + head + "</head><body>A profile</body></html>")
				.getBytes(StandardCharsets.UTF_8);
	}

	@After
	public void stopServer() {
		server.stop(0);
	}

	private URI url(String path) {
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
	}

	private ProfileCache cache(Duration timeout) {
		return new ProfileCache(getClass().getClassLoader(), new CrateReader(), timeout);
	}

	@Test
	public void coreProfileBundled() {
		ProfileCache cache = ProfileCache.bundled();
		assertTrue(cache.bundledIdentifiers().contains(CORE));
		Profile core = cache.bundled(CORE).orElseThrow();
		assertFalse(core.allProblems().toString(), core.hasErrors());
		assertEquals("RO-Crate Machine Actionable Profile", core.name().orElseThrow());
		assertEquals(3, core.classRules().size());
		assertEquals(6, core.propertyRules().size());
		// Read once
		assertSame(core, cache.bundled(CORE).orElseThrow());
		assertSame(core, cache.find(CORE).orElseThrow());
		assertTrue(cache.bundled("https://example.com/not-bundled").isEmpty());
	}

	/**
	 * The index made by scripts/update-masp-profiles.sh matches the files.
	 */
	@Test
	public void bundledIndexChecksums() throws Exception {
		JsonNode index;
		try (InputStream in = getClass().getResourceAsStream("/" + ProfileCache.INDEX)) {
			index = new ObjectMapper().readTree(in);
		}
		assertTrue(index.size() >= 1);
		for (JsonNode entry : index) {
			try (InputStream in = getClass().getResourceAsStream("/" + entry.get("X-Classpath").asText())) {
				assertNotNull(entry.toString(), in);
				byte[] digest = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
				assertEquals(entry.get("X-Classpath").asText(), entry.get("sha256").asText(),
						HexFormat.of().formatHex(digest));
			}
			assertEquals(40, entry.get("commit").asText().length());
			for (JsonNode id : entry.get("identifiers")) {
				assertEquals(entry.get("name").asText(),
						ProfileCache.bundled().bundled(id.asText()).orElseThrow().name().orElseThrow());
			}
		}
	}

	@Test
	public void loadByHttp() {
		ProfileCache cache = cache(Duration.ofSeconds(10));
		// The folder URL, as profiles are usually published
		Profile ldac = cache.load(url("/ldac/"));
		assertFalse(ldac.allProblems().toString(), ldac.hasErrors());
		assertEquals(30, ldac.classRules().size());
		assertEquals(99, ldac.propertyRules().size());
		// Kept, and found by identifier afterwards
		assertSame(ldac, cache.load(url("/ldac/ro-crate-metadata.json")));
		// The folder, then its ro-crate-metadata.json
		assertEquals(2, requests.get());
		assertSame(ldac, cache.find("https://w3id.org/ldac/profile").orElseThrow());
	}

	/**
	 * The ways a profile's identifier can lead to its crate.
	 */
	@Test
	public void retrieveFromIdentifier() {
		for (String path : List.of("/pages/", "/header", "/w3id", "/conneg")) {
			Profile ldac = cache(Duration.ofSeconds(10)).load(url(path));
			assertFalse(path + ": " + ldac.allProblems(), ldac.hasErrors());
			assertEquals(path, 99, ldac.propertyRules().size());
			assertTrue(path, ldac.identifiers().contains("https://w3id.org/ldac/profile"));
		}
	}

	@Test
	public void webPageWithoutCrate() {
		Profile page = cache(Duration.ofSeconds(10)).load(url("/webpage"));
		assertTrue(page.hasErrors());
		assertTrue(page.allProblems().toString(),
				page.allProblems().get(0).message().contains("web page without a link to its RO-Crate metadata"));
		Profile loop = cache(Duration.ofSeconds(10)).load(url("/loop/"));
		assertTrue(loop.hasErrors());
		assertTrue(loop.allProblems().toString(), loop.allProblems().get(0).message().startsWith("Can't"));
	}

	/**
	 * Loaded profiles are kept in the store for next time, also offline.
	 */
	@Test
	public void keptInStore() throws Exception {
		Path store = temp.getRoot().toPath().resolve("profiles");
		ProfileCache cache = new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(10),
				Optional.of(store));
		assertEquals(List.of(), cache.stored());
		assertEquals(cache.available().toString(), 1, cache.available().size());
		Profile ldac = cache.load(url("/pages/"));
		assertFalse(ldac.allProblems().toString(), ldac.hasErrors());
		assertTrue(Files.isRegularFile(store.resolve(ProfileCache.STORE_INDEX)));
		JsonNode index = new ObjectMapper().readTree(store.resolve(ProfileCache.STORE_INDEX).toFile());
		assertEquals(1, index.size());
		assertEquals(url("/pages/").toString(), index.get(0).get("location").asText());
		assertEquals(url("/ldac/ro-crate-metadata.json").toString(), index.get(0).get("source").asText());
		assertTrue(Files.isRegularFile(store.resolve(index.get(0).get("file").asText())));

		// Next time, without the network
		int before = requests.get();
		ProfileCache next = new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(2),
				Optional.of(store));
		assertEquals(1, next.stored().size());
		Profile stored = next.find("https://w3id.org/ldac/profile").orElseThrow();
		assertEquals(99, stored.propertyRules().size());
		assertEquals(ldac.identifiers(), stored.identifiers());
		assertEquals(2, next.available().size());
		assertEquals(before, requests.get());

		// Loading it again, here from a file, replaces the copy
		Path file = Path.of(getClass().getResource(FIXTURES + "ldac-profile.json").toURI());
		assertFalse(next.load(file.toUri()).hasErrors());
		assertEquals(1, next.stored().size());
		try (Stream<Path> files = Files.list(store)) {
			assertEquals(2, files.count());
		}
	}

	@Test
	public void forgetKept() throws Exception {
		Path store = temp.getRoot().toPath().resolve("profiles");
		ProfileCache cache = new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(10),
				Optional.of(store));
		Profile ldac = cache.load(url("/pages/"));
		List<ProfileCache.Kept> kept = cache.kept();
		assertEquals(1, kept.size());
		assertEquals("Language Data Commons Comprehensive Collection Profile", kept.get(0).name().orElseThrow());
		assertEquals(url("/pages/").toString(), kept.get(0).location());
		assertTrue(kept.get(0).retrieved().isPresent());

		assertSame(ldac, cache.forget(kept.get(0)).orElseThrow());
		assertEquals(List.of(), cache.kept());
		assertEquals(List.of(), cache.stored());
		assertTrue(cache.find("https://w3id.org/ldac/profile").isEmpty());
		try (Stream<Path> files = Files.list(store)) {
			assertEquals(List.of(store.resolve(ProfileCache.STORE_INDEX)), files.toList());
		}
		assertEquals(List.of(), new ProfileCache(getClass().getClassLoader(), new CrateReader(),
				Duration.ofSeconds(2), Optional.of(store)).kept());
	}

	@Test
	public void disabledRegistry() {
		ProfileCache cache = new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
				Optional.empty(), registries("/sparql"));
		cache.setDisabledRegistries(Set.of("profiles.ro-crate.org"));
		assertEquals(2, cache.registries().size());
		assertEquals(List.of("RO-Crate MASP examples"),
				cache.registryEntries().entries().stream().map(ProfileRegistry.Entry::registry).distinct().toList());
		// Not used to find an identifier either
		assertTrue(cache.load(url("/unresolvable")).hasErrors());
		cache.setDisabledRegistries(Set.of());
		assertFalse(cache.load(url("/unresolvable")).hasErrors());
	}

	/**
	 * A broken store doesn't stop the application.
	 */
	@Test
	public void brokenStore() throws Exception {
		Path store = temp.newFolder("profiles").toPath();
		Files.writeString(store.resolve(ProfileCache.STORE_INDEX),
				"[{\"file\": \"missing.json\", \"identifiers\": [\"https://example.com/p\"]}, 3]");
		ProfileCache cache = new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(2),
				Optional.of(store));
		assertEquals(List.of(), cache.stored());
		assertTrue(cache.find("https://example.com/p").isEmpty());
		Files.writeString(store.resolve(ProfileCache.STORE_INDEX), "not json");
		assertEquals(List.of(), new ProfileCache(getClass().getClassLoader(), new CrateReader(),
				Duration.ofSeconds(2), Optional.of(store)).stored());
	}

	private List<ProfileRegistry> registries(String sparql) {
		HttpClient http = ProfileCache.httpClient(Duration.ofSeconds(5));
		return List.of(new MaspCatalogue(url("/catalogue.json"), http, Duration.ofSeconds(5)),
				new RoCrateProfilesRegistry(url(sparql), http, Duration.ofSeconds(5)));
	}

	@Test
	public void registryEntries() {
		ProfileCache cache = new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
				Optional.empty(), registries("/sparql"));
		ProfileCache.Listing listing = cache.registryEntries();
		assertEquals(List.of(), listing.problems());
		assertEquals(List.of("LDAC Profile 0.1.0", "Draft Profile (draft)", "Registered Profile 1.0"),
				listing.entries().stream().map(ProfileRegistry.Entry::label).toList());
		ProfileRegistry.Entry ldac = listing.entries().get(0);
		assertEquals("RO-Crate MASP examples", ldac.registry());
		assertEquals(url("/ldac/ro-crate-metadata.json"), ldac.location());
		// The profile-crate folder, as MASP profiles are identified
		assertEquals(List.of(url("/draft/profile-crate/").toString()), listing.entries().get(1).identifiers());
		ProfileRegistry.Entry registered = listing.entries().get(2);
		assertEquals("profiles.ro-crate.org", registered.registry());
		assertTrue(registered.identifiedBy(url("/unresolvable").toString()));
		assertTrue(registered.identifiedBy(url("/unresolvable/").toString()));
		assertTrue(registered.identifiedBy("https://profiles.example.org/data/profile/123"));
		// Read once in a session
		int before = requests.get();
		cache.registryEntries();
		assertEquals(before, requests.get());
	}

	@Test
	public void registryUnavailable() {
		ProfileCache cache = new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
				Optional.empty(), registries("/missing-sparql"));
		ProfileCache.Listing listing = cache.registryEntries();
		assertEquals(2, listing.entries().size());
		assertEquals(1, listing.problems().size());
		assertEquals(Severity.WARNING, listing.problems().get(0).severity());
		assertTrue(listing.problems().get(0).message(),
				listing.problems().get(0).message().startsWith("Can't read profiles.ro-crate.org: HTTP 404"));
		// Tried again next time
		int before = requests.get();
		cache.registryEntries();
		assertEquals(before + 1, requests.get());
	}

	/**
	 * An identifier that doesn't lead to the crate, found by a registry.
	 */
	@Test
	public void loadThroughRegistry() {
		ProfileCache cache = new ProfileCache(getClass().getClassLoader(), new CrateReader(), Duration.ofSeconds(5),
				Optional.empty(), registries("/sparql"));
		Profile profile = cache.load(url("/unresolvable"));
		assertFalse(profile.allProblems().toString(), profile.hasErrors());
		assertEquals(99, profile.propertyRules().size());
		// Not listed anywhere: the original problem
		Profile missing = cache.load(url("/missing/"));
		assertTrue(missing.allProblems().get(0).message(), missing.allProblems().get(0).message().contains("HTTP 404"));
	}

	@Test
	public void loadByFile() throws Exception {
		Path file = Path.of(getClass().getResource(FIXTURES + "ldac-profile.json").toURI());
		Profile ldac = cache(Duration.ofSeconds(10)).load(file.toUri());
		assertFalse(ldac.hasErrors());
		assertEquals(99, ldac.propertyRules().size());
	}

	@Test
	public void notFoundReportedAndRetried() {
		ProfileCache cache = cache(Duration.ofSeconds(10));
		Profile missing = cache.load(url("/missing/"));
		assertTrue(missing.hasErrors());
		assertTrue(missing.allProblems().toString(), missing.allProblems().get(0).message().contains("HTTP 404"));
		assertEquals(Severity.ERROR, missing.allProblems().get(0).severity());
		// Not cached: tried again
		cache.load(url("/missing/"));
		// Each time the folder, then its ro-crate-metadata.json
		assertEquals(4, requests.get());
	}

	@Test
	public void timeLimit() {
		long start = System.nanoTime();
		Profile slow = cache(Duration.ofMillis(500)).load(url("/slow/"));
		long millis = (System.nanoTime() - start) / 1_000_000;
		assertTrue(slow.hasErrors());
		assertTrue(slow.allProblems().toString(), slow.allProblems().get(0).message().contains("no response within"));
		assertTrue("gave up after " + millis + " ms", millis < 2500);
	}

	@Test
	public void notACrate() {
		Profile html = cache(Duration.ofSeconds(10)).load(url("/notjson/"));
		assertTrue(html.hasErrors());
		assertTrue(html.allProblems().toString(), html.allProblems().stream().anyMatch(p -> p.message().contains("Not JSON")));
	}

	@Test
	public void unsupportedScheme() {
		Profile ftp = cache(Duration.ofSeconds(10)).load(URI.create("ftp://example.com/profile/"));
		assertTrue(ftp.hasErrors());
		assertTrue(ftp.allProblems().stream().anyMatch(p -> p.message().contains("only https:, http: and file:")));
		List<String> none = ftp.identifiers();
		assertEquals(List.of(), none);
	}

	@Test
	public void unreachableHost() {
		// Nothing listens on port 9 (discard) locally
		Profile unreachable = cache(Duration.ofSeconds(2)).load(URI.create("http://127.0.0.1:9/profile/"));
		assertTrue(unreachable.hasErrors());
		assertTrue(unreachable.allProblems().get(0).message().startsWith("Can't load"));
	}
}
