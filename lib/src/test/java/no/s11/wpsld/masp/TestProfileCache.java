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

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import no.s11.wpsld.crate.CrateDocument.Severity;
import no.s11.wpsld.crate.CrateReader;

public class TestProfileCache {
	private static final String CORE = "https://language-research-technology.github.io/ro-crate-masp/profiles/ro-crate/profile-crate/";
	private static final String FIXTURES = "/no/s11/wpsld/crate/masp/";

	private HttpServer server;
	private final AtomicInteger requests = new AtomicInteger();

	/** Serves the test fixtures, and paths that fail in different ways */
	@Before
	public void startServer() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requests.incrementAndGet();
			String path = exchange.getRequestURI().getPath();
			byte[] body;
			int status = 200;
			if (path.equals("/slow/ro-crate-metadata.json")) {
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				body = new byte[0];
			} else if (path.equals("/notjson/ro-crate-metadata.json")) {
				body = "<html>Not a crate</html>".getBytes(StandardCharsets.UTF_8);
			} else {
				try (InputStream in = getClass().getResourceAsStream(FIXTURES + path.replaceFirst("^/ldac/", "")
						.replace("ro-crate-metadata.json", "ldac-profile.json"))) {
					if (in == null || !path.startsWith("/ldac/")) {
						status = 404;
						body = "Not found".getBytes(StandardCharsets.UTF_8);
					} else {
						body = in.readAllBytes();
					}
				}
			}
			exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
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
		assertEquals(1, requests.get());
		assertSame(ldac, cache.find("https://w3id.org/ldac/profile").orElseThrow());
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
		assertEquals(2, requests.get());
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
