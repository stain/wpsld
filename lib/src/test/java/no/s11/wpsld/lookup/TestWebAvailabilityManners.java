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

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.lookup.WebAvailability.Manners;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.util.UserAgent;

/**
 * How Check Web Availability asks: HEAD, and a GET for a byte where HEAD is
 * not done right; <code>Retry-After</code> pausing every request to a host;
 * tries again after a transient answer; not waiting forty timeouts on a
 * server that is down; and what it says of the content, to compare with the
 * crate's.
 */
public class TestWebAvailabilityManners {
	/** A request the server saw */
	private record Seen(String method, String path, String range, String accept, String agent, int port, long at) {
	}

	private HttpServer server;
	private final List<Seen> seen = new CopyOnWriteArrayList<>();
	private final CountDownLatch release = new CountDownLatch(1);
	private long start;

	@After
	public void stop() {
		release.countDown();
		if (server != null) {
			server.stop(0);
		}
	}

	private interface Answer {
		void to(HttpExchange exchange) throws Exception;
	}

	private URI serve(Answer answer) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r, "test server");
			t.setDaemon(true);
			return t;
		}));
		server.createContext("/", exchange -> {
			seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
					exchange.getRequestHeaders().getFirst("Range"), exchange.getRequestHeaders().getFirst("Accept"),
					exchange.getRequestHeaders().getFirst("User-Agent"), exchange.getRemoteAddress().getPort(), System.nanoTime()));
			try {
				answer.to(exchange);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				exchange.sendResponseHeaders(500, -1);
			} finally {
				exchange.close();
			}
		});
		server.start();
		start = System.nanoTime();
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
	}

	private static void status(HttpExchange exchange, int status) throws IOException {
		exchange.sendResponseHeaders(status, -1);
	}

	private static WebAvailability web(Manners manners) {
		return new WebAvailability(ProfileCache.httpClient(Duration.ofSeconds(5)), Duration.ofSeconds(5), manners);
	}

	/** Quick, for the tests: the pauses are the server's to say, and the defaults' are seconds */
	private static final Manners FAST = new Manners(2, 3, Duration.ofMillis(20), Duration.ofSeconds(10), 3);

	private static List<String> addresses(URI site, String path, int count) {
		List<String> addresses = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			addresses.add(site.resolve(path + i).toString());
		}
		return addresses;
	}

	private long ms(Seen s) {
		return (s.at() - start) / 1_000_000;
	}

	// ---- HEAD and GET

	@Test
	public void headFirst_andAGetForAByteWhereHeadIsNotDoneRight() throws Exception {
		URI site = serve(exchange -> {
			String path = exchange.getRequestURI().getPath();
			boolean head = exchange.getRequestMethod().equals("HEAD");
			switch (path) {
			// Fine as it is
			case "/ok" -> status(exchange, 200);
			// A server that answers HEAD not found, and GET found
			case "/head-404" -> {
				if (head) {
					status(exchange, 404);
				} else {
					exchange.sendResponseHeaders(200, 5);
					exchange.getResponseBody().write("hello".getBytes(StandardCharsets.UTF_8));
				}
			}
			// KU Leuven's file addresses: HEAD is forbidden, GET (for a byte) gives it, with the whole length
			case "/head-403" -> {
				if (head) {
					status(exchange, 403);
				} else {
					exchange.getResponseHeaders().add("Content-Range", "bytes 0-0/1234");
					exchange.sendResponseHeaders(206, 1);
					exchange.getResponseBody().write('x');
				}
			}
			// A file with nothing in it: a range of it can't be satisfied, but it is there
			case "/empty" -> {
				if (head) {
					status(exchange, 405);
				} else {
					exchange.getResponseHeaders().add("Content-Range", "bytes */0");
					status(exchange, 416);
				}
			}
			// Final answers: GET would say no more
			case "/gone" -> status(exchange, 410);
			case "/login" -> status(exchange, 401);
			// Not there for either
			default -> status(exchange, 404);
			}
		});
		WebAvailability.Result[] results = web(FAST).check(List.of(site.resolve("/ok").toString(), site.resolve("/head-404").toString(),
				site.resolve("/head-403").toString(), site.resolve("/empty").toString(), site.resolve("/gone").toString(),
				site.resolve("/login").toString(), site.resolve("/nothing").toString())).toArray(WebAvailability.Result[]::new);
		assertEquals(List.of(true, true, true, true, false, false, false),
				java.util.Arrays.stream(results).map(WebAvailability.Result::available).toList());
		assertEquals(1234, results[2].size().getAsLong());
		assertEquals(0, results[3].size().getAsLong());
		assertEquals("Not available: HTTP 410", results[4].problem().get());
		assertEquals("Not available: HTTP 401", results[5].problem().get());
		assertEquals("Not available: HTTP 404", results[6].problem().get());
		// HEAD for all; a GET only where HEAD was not enough, for a byte, not for the whole: the final answers got none
		assertEquals(List.of("HEAD"), methodsOf("/ok"));
		assertEquals(List.of("HEAD", "GET"), methodsOf("/head-404"));
		assertEquals(List.of("HEAD", "GET"), methodsOf("/head-403"));
		assertEquals(List.of("HEAD", "GET"), methodsOf("/empty"));
		assertEquals(List.of("HEAD"), methodsOf("/gone"));
		assertEquals(List.of("HEAD"), methodsOf("/login"));
		assertEquals(List.of("HEAD", "GET"), methodsOf("/nothing"));
		seen.stream().filter(s -> s.method().equals("GET")).forEach(s -> assertEquals("bytes=0-0", s.range()));
		// Every request names wpsld, and asks as a browser does for a page, as w3id.org's addresses want
		seen.forEach(s -> {
			assertEquals(UserAgent.get(), s.agent());
			assertEquals(WebAvailability.ACCEPT, s.accept());
		});
	}

	private List<String> methodsOf(String path) {
		return seen.stream().filter(s -> s.path().equals(path)).map(Seen::method).toList();
	}

	// ---- Retry-After

	/**
	 * A 429 with Retry-After pauses every request to the host, from every thread: those that were in
	 * flight finish, but none starts before the time is up
	 */
	@Test
	public void retryAfterPausesAllRequestsToTheHost() throws Exception {
		AtomicInteger count = new AtomicInteger();
		URI site = serve(exchange -> {
			int n = count.incrementAndGet();
			if (n == 1) {
				exchange.getResponseHeaders().add("Retry-After", "1");
				status(exchange, 429);
			} else {
				// Held, so that the other threads' requests are in flight, and wanting the next ones to start
				Thread.sleep(150);
				status(exchange, 200);
			}
		});
		List<WebAvailability.Result> results = web(new Manners(4, 3, Duration.ofMillis(20), Duration.ofSeconds(10), 3))
				.check(addresses(site, "/f", 12));
		assertTrue(results.toString(), results.stream().allMatch(WebAvailability.Result::available));
		long limited = ms(seen.get(0));
		// The first four were asked together, before the 429 was known; nothing was asked for a second after it
		// except the one it was for, asked again
		List<Long> later = seen.stream().skip(1).map(this::ms).filter(t -> t > limited + 100).toList();
		assertFalse(later.isEmpty());
		assertTrue(later.toString() + " after " + limited, later.stream().allMatch(t -> t >= limited + 900));
	}

	@Test
	public void retryAfterAsAnHttpDate() throws Exception {
		AtomicInteger count = new AtomicInteger();
		URI site = serve(exchange -> {
			if (count.incrementAndGet() == 1) {
				exchange.getResponseHeaders().add("Retry-After", DateTimeFormatter.RFC_1123_DATE_TIME
						.format(ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(2)));
				status(exchange, 503);
			} else {
				status(exchange, 200);
			}
		});
		List<WebAvailability.Result> results = web(new Manners(1, 3, Duration.ofMillis(20), Duration.ofSeconds(10), 3))
				.check(addresses(site, "/f", 3));
		assertTrue(results.toString(), results.stream().allMatch(WebAvailability.Result::available));
		// The date is to the second, so 1 to 2 s from asking
		assertTrue(ms(seen.get(1)) - ms(seen.get(0)) >= 800);
	}

	@Test
	public void retryAfterParsing() {
		java.net.http.HttpHeaders seconds = java.net.http.HttpHeaders.of(Map.of("Retry-After", List.of("120")), (a, b) -> true);
		assertEquals(Optional.of(Duration.ofSeconds(120)), WebAvailability.retryAfter(seconds));
		java.net.http.HttpHeaders past = java.net.http.HttpHeaders.of(Map.of("Retry-After", List.of("Wed, 21 Oct 2015 07:28:00 GMT")), (a, b) -> true);
		assertEquals(Optional.of(Duration.ZERO), WebAvailability.retryAfter(past));
		for (String bad : List.of("soon", "-5", "1.5", "")) {
			assertEquals(bad, Optional.empty(),
					WebAvailability.retryAfter(java.net.http.HttpHeaders.of(Map.of("Retry-After", List.of(bad)), (a, b) -> true)));
		}
		assertEquals(Optional.empty(), WebAvailability.retryAfter(java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true)));
	}

	/** Asked to wait longer than is worth it: that host is not asked any more, and nothing waits for an hour */
	@Test
	public void retryAfterLongerThanIsWorthWaitingGivesUpOnTheHost() throws Exception {
		URI site = serve(exchange -> {
			exchange.getResponseHeaders().add("Retry-After", "3600");
			status(exchange, 503);
		});
		long t = System.nanoTime();
		List<WebAvailability.Result> results = web(new Manners(2, 3, Duration.ofMillis(20), Duration.ofSeconds(2), 3))
				.check(addresses(site, "/f", 10));
		assertTrue("took " + (System.nanoTime() - t) / 1_000_000 + " ms", (System.nanoTime() - t) < 3_000_000_000L);
		assertTrue(results.get(0).problem().get(), results.get(0).problem().get().contains("asked to wait"));
		assertEquals(10, results.size());
		assertTrue(results.stream().allMatch(r -> !r.available()));
		assertTrue(results.stream().skip(1).anyMatch(r -> r.problem().get().startsWith("Could not check: 127.0.0.1 is not asked any more")));
		// Not asked again once it said so, but for those that were already on their way: a thread that took the
		// place of one that had finished may ask before the host is marked, so at most as many as ask at once
		assertTrue("asked: " + seen.size(), seen.size() <= 4);
	}

	// ---- retries

	@Test
	public void aTransientAnswerIsTriedAgainAfterAPause() throws Exception {
		AtomicInteger flaky = new AtomicInteger();
		URI site = serve(exchange -> {
			String path = exchange.getRequestURI().getPath();
			if (path.equals("/flaky")) {
				status(exchange, flaky.incrementAndGet() <= 2 ? 503 : 200);
			} else if (path.equals("/bad-gateway")) {
				status(exchange, 502);
			} else {
				status(exchange, 200);
			}
		});
		Manners manners = new Manners(1, 3, Duration.ofMillis(100), Duration.ofSeconds(10), 3);
		List<WebAvailability.Result> results = web(manners).check(List.of(site.resolve("/flaky").toString(), site.resolve("/bad-gateway").toString()));
		assertTrue(results.get(0).available());
		assertEquals("Not available: HTTP 502 (tried 3 times)", results.get(1).problem().get());
		assertEquals(3, methodsOf("/flaky").size());
		// Tried three times, and no GET: a 5xx is not a server that does not do HEAD
		assertEquals(List.of("HEAD", "HEAD", "HEAD"), methodsOf("/bad-gateway"));
		// With the pause growing, 100 ms then 200 ms, between the tries of the address
		List<Seen> gateway = seen.stream().filter(s -> s.path().equals("/bad-gateway")).toList();
		assertTrue(ms(gateway.get(1)) - ms(gateway.get(0)) >= 90);
		assertTrue(ms(gateway.get(2)) - ms(gateway.get(1)) >= 190);
	}

	@Test
	public void nothingListeningIsTriedAgainAndSaid() throws Exception {
		// A port nothing listens on: one that was a socket's, closed (not an HTTP server's, which lingers)
		int port;
		try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
			port = socket.getLocalPort();
		}
		long t = System.nanoTime();
		WebAvailability.Result result = web(FAST).check("http://127.0.0.1:" + port + "/x");
		assertFalse(result.available());
		assertTrue(result.problem().get(), result.problem().get().startsWith("Not available: ") && result.problem().get().endsWith("(tried 3 times)"));
		assertTrue((System.nanoTime() - t) < 3_000_000_000L);
	}

	// ---- a host that does not answer

	/**
	 * Forty addresses on a server that never answers do not cost forty timeouts: after a few that
	 * time out the host is given up on, and the rest are not asked, so it is a few waits, not forty
	 */
	@Test
	public void aServerThatDoesNotAnswerIsNotWaitedForFortyTimes() throws Exception {
		URI site = serve(exchange -> release.await());
		WebAvailability web = new WebAvailability(HttpClient.newHttpClient(), Duration.ofSeconds(1),
				new Manners(2, 3, Duration.ofMillis(20), Duration.ofSeconds(10), 3));
		long t = System.nanoTime();
		List<WebAvailability.Result> results = web.check(addresses(site, "/f", 40));
		long took = (System.nanoTime() - t) / 1_000_000;
		// Forty at one wait of a second each, two at a time, would be twenty seconds, and with tries sixty
		assertTrue("took " + took + " ms", took < 8000);
		assertEquals(40, results.size());
		assertTrue(results.stream().noneMatch(WebAvailability.Result::available));
		assertTrue(results.get(0).problem().get(), results.get(0).problem().get().startsWith("Not available: no answer within 1.0 s"));
		long notAsked = results.stream().filter(r -> r.problem().get().startsWith("Could not check: 127.0.0.1 is not asked any more")).count();
		assertTrue("not asked: " + notAsked, notAsked >= 30);
		assertTrue("asked: " + seen.size(), seen.size() <= 8);
	}

	// ---- what the server says of the content

	private Crate crate(URI site) {
		String json = """
				{"@context": "https://w3id.org/ro/crate/1.1/context", "@graph": [
				 {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"},
				  "conformsTo": {"@id": "https://w3id.org/ro/crate/1.1"}},
				 {"@id": "./", "@type": "Dataset", "name": "D", "hasPart": [{"@id": "a"}, {"@id": "b"}, {"@id": "c"}, {"@id": "d"}, {"@id": "e"}, {"@id": "f"}]},
				 {"@id": "a", "@type": "File", "contentUrl": "SITE/right", "contentSize": "100", "encodingFormat": "application/pdf"},
				 {"@id": "b", "@type": "File", "contentUrl": "SITE/wrong-size", "contentSize": 100, "encodingFormat": "application/pdf"},
				 {"@id": "c", "@type": "File", "contentUrl": "SITE/head-says-zero", "contentSize": "100"},
				 {"@id": "d", "@type": "File", "contentUrl": "SITE/landing-page", "encodingFormat": "application/pdf"},
				 {"@id": "e", "@type": "File", "contentUrl": "SITE/landing-page-too", "encodingFormat": "text/html", "contentSize": "250"},
				 {"@id": "f", "@type": "File", "contentUrl": "SITE/no-size", "contentSize": "1.2 MB"}]}"""
				.replace("SITE", site.toString());
		return new CrateReader().read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), "test").crate().orElseThrow();
	}

	@Test
	public void aSizeOrAKindThatIsNotTheCratesIsWarnedOf() throws Exception {
		URI site = serve(exchange -> {
			String path = exchange.getRequestURI().getPath();
			boolean head = exchange.getRequestMethod().equals("HEAD");
			String type = "application/pdf";
			long length = 100;
			switch (path) {
			case "/wrong-size" -> length = 5000;
			case "/head-says-zero" -> length = head ? 0 : 100;
			case "/landing-page" -> {
				type = "text/html; charset=utf-8";
				length = 32790;
			}
			case "/landing-page-too" -> {
				type = "text/html";
				length = 250;
			}
			case "/no-size" -> length = 77;
			default -> {
			}
			}
			exchange.getResponseHeaders().add("Content-Type", type);
			if (head) {
				exchange.getResponseHeaders().add("Content-Length", String.valueOf(length));
				exchange.sendResponseHeaders(200, -1);
			} else {
				// A range of one byte, as asked, and the whole length with it
				exchange.getResponseHeaders().add("Content-Range", "bytes 0-0/" + length);
				exchange.sendResponseHeaders(206, 1);
				exchange.getResponseBody().write('x');
			}
		});
		Crate crate = crate(site);
		List<WebAvailability.Target> targets = WebAvailability.targetsOf(crate, Optional.empty());
		List<WebAvailability.Result> results = web(FAST).check(WebAvailability.addresses(targets), WebAvailability.expectations(crate, targets));
		assertTrue(results.toString(), results.stream().allMatch(WebAvailability.Result::available));
		Map<String, String> warnings = new java.util.TreeMap<>();
		WebAvailability.mismatches(crate, targets, results).forEach((target, text) -> warnings.put(target.id(), text.replace(site.toString(), "SITE")));
		assertEquals(Map.of(
				"b", "contentUrl SITE/wrong-size: it is 5,000 bytes, but contentSize says 100",
				"d", "contentUrl SITE/landing-page: it is a web page (text/html), not application/pdf: a landing page, not the file?"),
				warnings);
		// a is right; c's HEAD said 0 and the GET for a byte, which is asked to be sure, said 100; e says it is a page
		// and is; f's size is "1.2 MB", which is not bytes to compare
		assertEquals(List.of("HEAD"), methodsOf("/right"));
		assertEquals(List.of("HEAD", "GET"), methodsOf("/head-says-zero"));
		assertEquals(List.of("HEAD", "GET"), methodsOf("/wrong-size"));
		assertEquals(List.of("HEAD"), methodsOf("/no-size"));
	}

	// ---- connections

	@Test
	public void connectionsAreKeptForTheNextRequest() throws Exception {
		URI site = serve(exchange -> status(exchange, 200));
		List<WebAvailability.Result> results = web(FAST).check(addresses(site, "/f", 40));
		assertTrue(results.stream().allMatch(WebAvailability.Result::available));
		// Two at once to a host, so two connections, kept, not forty
		long connections = seen.stream().map(Seen::port).distinct().count();
		assertTrue("connections: " + connections, connections <= FAST.perHost());
	}

	@Test
	public void mannersAreChecked() {
		assertThrows(IllegalArgumentException.class, () -> new Manners(0, 3, Duration.ZERO, Duration.ZERO, 3));
		assertThrows(IllegalArgumentException.class, () -> new Manners(2, 0, Duration.ZERO, Duration.ZERO, 3));
	}
}
