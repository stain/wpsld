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
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.crate.WebCrate;
import no.s11.wpsld.util.UserAgent;

/**
 * Whether a crate's web resources are available: each address answers, after
 * any redirects, with success (2xx). Asked with HEAD, which costs a server
 * least and is what RFC 9110 (9.3.2) has for finding out what a GET would
 * give, and then, for the answers that show a server that does not do HEAD
 * right (KU Leuven's file addresses answer it 403, and many answer 404 or
 * 405) with a GET for one byte (<code>Range: bytes=0-0</code>), whose body is
 * not read, so that a large file is not downloaded.
 * <p>
 * It asks as politely as a program doing it for a person can: a few at once,
 * and fewer to a host; every request names wpsld (see {@link UserAgent}); a
 * <code>Retry-After</code> (RFC 9110, 10.2.3) on a 429 or 503 pauses <em>all</em>
 * requests to that host until then, or, if it is longer than is worth waiting
 * for, ends asking it; a transient error is tried again, after a longer
 * pause each time; and a host that does not answer (a few timeouts or
 * refused connections running) is given up on, so that forty addresses on a
 * server that is down do not cost forty timeouts. The HTTP client's
 * connections are kept for the next request (one connection to a host
 * that speaks HTTP/2, a few to one that speaks HTTP/1.1), as long as the one
 * client is.
 * <p>
 * And it says what the server says about the content: its length and media
 * type, to compare with what the crate says (see {@link #mismatches}), as a
 * <code>contentUrl</code> or the <code>@id</code> of a file that is a web page
 * about the file, not the file, is a common mistake.
 */
public class WebAvailability {
	/**
	 * What a web address answered: its last status, 0 if none, and the
	 * problem, if it isn't available; and, if it answered, how long the
	 * content is and its media type, as far as the server said
	 */
	public record Result(String address, int status, Optional<String> problem, OptionalLong size,
			Optional<String> type) {
		public Result(String address, int status, Optional<String> problem) {
			this(address, status, problem, OptionalLong.empty(), Optional.empty());
		}

		public boolean available() {
			return problem.isEmpty();
		}
	}

	/**
	 * How politely and how patiently to ask.
	 *
	 * @param perHost                At most this many requests to a host at once
	 * @param attempts               Tries for an address that gets a transient answer (a 429,
	 *                               5xx, or a connection that failed)
	 * @param backoff                The pause before the second try, doubled for each after
	 *                               it, where the server says no other (<code>Retry-After</code>)
	 * @param longestWait            The longest <code>Retry-After</code> waited for: asked
	 *                               to wait longer, a host is given up on
	 * @param failuresBeforeGivingUp Timeouts and failed connections to a host, running,
	 *                               after which it is given up on
	 */
	public record Manners(int perHost, int attempts, Duration backoff, Duration longestWait,
			int failuresBeforeGivingUp) {
		public static final Manners DEFAULT = new Manners(2, 3, Duration.ofSeconds(1), Duration.ofMinutes(2), 3);

		public Manners {
			if (perHost < 1 || attempts < 1 || failuresBeforeGivingUp < 1) {
				throw new IllegalArgumentException("At least 1");
			}
		}
	}

	/** What a crate says of what is at an address: its sizes (several entities may have it) and media types */
	public record Expectation(Set<Long> sizes, Set<String> types) {
		static final Expectation NONE = new Expectation(Set.of(), Set.of());

		boolean isNone() {
			return sizes.isEmpty() && types.isEmpty();
		}

		/** Whether a length the server gave is not one the crate says, which a GET can check */
		boolean disagrees(OptionalLong size) {
			return !sizes.isEmpty() && size.isPresent() && !sizes.contains(size.getAsLong());
		}
	}

	/** At most this many asked at once, in all */
	private static final int AT_ONCE = 4;
	/** As a browser asks, for a web page, which some addresses (w3id.org's) answer only to */
	static final String ACCEPT = "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8";

	private final HttpClient http;
	private final Duration timeout;
	private final Manners manners;
	private final String userAgent;

	/**
	 * @param http following redirects, e.g. ProfileCache's: the one client, kept,
	 *             so that its connections are
	 */
	public WebAvailability(HttpClient http, Duration timeout) {
		this(http, timeout, Manners.DEFAULT);
	}

	public WebAvailability(HttpClient http, Duration timeout, Manners manners) {
		this.http = Objects.requireNonNull(http);
		this.timeout = Objects.requireNonNull(timeout);
		this.manners = Objects.requireNonNull(manners);
		this.userAgent = UserAgent.get();
	}

	/** The data entities with web (http, https) addresses, to check */
	public static List<String> addresses(Crate crate) {
		return crate.dataEntities().stream().map(Entity::getId).filter(WebAvailability::isWeb).distinct().toList();
	}

	/**
	 * A web address to check and what it is for.
	 *
	 * @param address  The address, absolute
	 * @param id       The <code>@id</code> of the entity it belongs to
	 * @param property <code>@id</code> if the entity's own <code>@id</code> is
	 *                 it (or leads to it, relative to the crate's web base),
	 *                 else <code>contentUrl</code> or <code>url</code>
	 */
	public record Target(String address, String id, String property) {
	}

	/**
	 * What to check in a crate, across all its entities, not only files: every
	 * <code>contentUrl</code> and <code>url</code> that is a web address (or
	 * relative to the crate's web base, if it has one), and every
	 * <code>@id</code> that is a web address, or, according to the web base
	 * (see {@link WebCrate}), would be one: the latter only for files and
	 * folders, as other entities' relative <code>@id</code>s (a person's name)
	 * are not paths. Not the metadata descriptor, nor the schema.org terms a
	 * crate refers to without describing them, nor the <em>root data entity's
	 * own</em> <code>@id</code>: RO-Crate asks only that it is <code>./</code> or
	 * an absolute URI, with no say in what is at it (a folder may have no page
	 * at all). What must work for an attached crate on the web is its metadata
	 * file, <code>ro-crate-metadata.json</code> beside it, which is asked
	 * instead, as of the root. An address several entities have is listed for
	 * each, to be asked once (see {@link #addresses(List)}).
	 *
	 * @param web Where the crate is on the web, if anywhere
	 */
	public static List<Target> targetsOf(Crate crate, Optional<WebCrate> web) {
		java.util.Set<Target> targets = new java.util.LinkedHashSet<>();
		java.util.Set<Entity> data = new java.util.HashSet<>(crate.dataEntities());
		for (Entity entity : crate.getEntities()) {
			String id = entity.getId();
			if (entity == crate.getRoot()) {
				// Not its @id, but the metadata file that an attached crate on the web must have
				web.filter(WebCrate::attached).flatMap(WebCrate::base).ifPresent(base -> targets.add(
						new Target(base.resolve(Crate.RO_CRATE_METADATA_JSON).toString(), id, Crate.RO_CRATE_METADATA_JSON)));
			} else if (isWeb(id)) {
				if (!Crate.isTerm(id)) {
					targets.add(new Target(id, id, "@id"));
				}
			} else if (data.contains(entity)) {
				web.flatMap(w -> w.resolve(id)).ifPresent(a -> targets.add(new Target(a.toString(), id, "@id")));
			}
			for (String property : List.of("contentUrl", "url")) {
				for (JsonNode value : entity.getAll(property)) {
					String text = value.isTextual() ? value.asText() : value.path("@id").asText("");
					if (isWeb(text)) {
						targets.add(new Target(text, id, property));
					} else if (!text.isEmpty()) {
						web.flatMap(w -> w.resolve(text)).ifPresent(a -> targets.add(new Target(a.toString(), id, property)));
					}
				}
			}
		}
		return List.copyOf(targets);
	}

	/** The addresses of targets, each once, in order */
	public static List<String> addresses(List<Target> targets) {
		return targets.stream().map(Target::address).distinct().toList();
	}

	private static boolean isWeb(String id) {
		String lower = id.toLowerCase(java.util.Locale.ROOT);
		return lower.startsWith("http://") || lower.startsWith("https://");
	}

	/**
	 * What each web address of targets is to be, by what the crate says of the
	 * entity it is of: the sizes (<code>contentSize</code>, in bytes) and media
	 * types (<code>encodingFormat</code>) of its <code>@id</code> and
	 * <code>contentUrl</code>, not of its <code>url</code>, which may be a page
	 * about it.
	 */
	public static Map<String, Expectation> expectations(Crate crate, List<Target> targets) {
		Map<String, Set<Long>> sizes = new LinkedHashMap<>();
		Map<String, Set<String>> types = new LinkedHashMap<>();
		for (Target target : targets) {
			if (!target.property().equals("@id") && !target.property().equals("contentUrl")) {
				continue;
			}
			crate.get(target.id()).ifPresent(entity -> {
				sizes.computeIfAbsent(target.address(), a -> new HashSet<>()).addAll(sizesOf(entity));
				types.computeIfAbsent(target.address(), a -> new HashSet<>()).addAll(typesOf(entity));
			});
		}
		Map<String, Expectation> expectations = new LinkedHashMap<>();
		sizes.keySet().forEach(a -> expectations.put(a, new Expectation(sizes.get(a), types.get(a))));
		return expectations;
	}

	/** The <code>contentSize</code> of an entity as a number of bytes, where it is one (not "1.2 MB") */
	static Set<Long> sizesOf(Entity entity) {
		Set<Long> sizes = new HashSet<>();
		for (JsonNode value : entity.getAll("contentSize")) {
			String text = value.isTextual() ? value.asText().strip() : value.isNumber() ? value.asText() : "";
			if (text.matches("[0-9]{1,18}")) {
				sizes.add(Long.parseLong(text));
			}
		}
		return sizes;
	}

	/** The media types an entity says it is, in <code>encodingFormat</code>: those written as one, not a reference */
	static Set<String> typesOf(Entity entity) {
		Set<String> types = new HashSet<>();
		for (JsonNode value : entity.getAll("encodingFormat")) {
			String text = value.isTextual() ? value.asText().replaceFirst(";.*", "").strip().toLowerCase(Locale.ROOT) : "";
			if (text.matches("[a-z0-9.+\\-]+/[a-z0-9.+\\-]+")) {
				types.add(text);
			}
		}
		return types;
	}

	private static boolean isPage(String type) {
		return type.equals("text/html") || type.equals("application/xhtml+xml");
	}

	/**
	 * What does not match, of what is available: for each target that is an
	 * <code>@id</code> or <code>contentUrl</code> of an entity that says how long
	 * its content is (<code>contentSize</code>) or what it is
	 * (<code>encodingFormat</code>), what the server says of the content: a
	 * length that is not the one the crate says, or a web page (HTML) where the
	 * crate says it is something else. The usual cause is an address of a page
	 * about the file, a landing page (a DOI's, a repository's), where the file's
	 * own address is wanted, as RO-Crate asks of a file on the web.
	 *
	 * @return The warnings, by target, for the entity to show: not that it is
	 *         unavailable
	 */
	public static Map<Target, String> mismatches(Crate crate, List<Target> targets, List<Result> results) {
		Map<String, Result> byAddress = new LinkedHashMap<>();
		results.forEach(r -> byAddress.putIfAbsent(r.address(), r));
		Map<Target, String> found = new LinkedHashMap<>();
		for (Target target : targets) {
			Result result = byAddress.get(target.address());
			if ((!target.property().equals("@id") && !target.property().equals("contentUrl")) || result == null
					|| !result.available()) {
				continue;
			}
			Entity entity = crate.get(target.id()).orElse(null);
			if (entity == null) {
				continue;
			}
			List<String> notes = new ArrayList<>();
			Set<Long> sizes = sizesOf(entity);
			if (!sizes.isEmpty() && result.size().isPresent() && !sizes.contains(result.size().getAsLong())) {
				notes.add(String.format(Locale.ROOT, "it is %,d bytes, but contentSize says %s", result.size().getAsLong(),
						sizes.stream().sorted().map(n -> String.format(Locale.ROOT, "%,d", n)).toList()
								.toString().replaceAll("[\\[\\]]", "")));
			}
			Set<String> types = typesOf(entity);
			if (!types.isEmpty() && result.type().filter(WebAvailability::isPage).isPresent()
					&& types.stream().noneMatch(WebAvailability::isPage)) {
				notes.add("it is a web page (" + result.type().get() + "), not " + String.join(" or ", types.stream().sorted().toList())
						+ ": a landing page, not the file?");
			}
			if (!notes.isEmpty()) {
				found.put(target, (target.property().equals("@id") ? "" : target.property() + " ") + target.address() + ": "
						+ String.join("; ", notes));
			}
		}
		return found;
	}

	/** Check these addresses, a few at once, in their order */
	public List<Result> check(List<String> addresses) throws InterruptedException {
		return check(addresses, Map.of());
	}

	/**
	 * Check these addresses, a few at once, in their order, and, where it is
	 * said what an address is to be (see {@link #expectations}), confirm a length
	 * that is not the one expected, which HEAD does not always give right.
	 */
	public List<Result> check(List<String> addresses, Map<String, Expectation> expectations)
			throws InterruptedException {
		Run run = new Run();
		ExecutorService pool = Executors.newFixedThreadPool(AT_ONCE, runnable -> {
			Thread thread = new Thread(runnable, "Web availability");
			thread.setDaemon(true);
			return thread;
		});
		try {
			List<Future<Result>> checks = addresses.stream()
					.map(a -> pool.submit(() -> run.check(a, expectations.getOrDefault(a, Expectation.NONE)))).toList();
			List<Result> results = new ArrayList<>();
			for (Future<Result> check : checks) {
				try {
					results.add(check.get());
				} catch (ExecutionException e) {
					throw new IllegalStateException(e.getCause());
				}
			}
			return results;
		} finally {
			pool.shutdownNow();
		}
	}

	/** Check one address */
	public Result check(String address) throws InterruptedException {
		return new Run().check(address, Expectation.NONE);
	}

	/** What a request came to: a status, or why there is none; and what the server said of the content */
	private record Reply(int status, Optional<Duration> retryAfter, OptionalLong size, Optional<String> type,
			Optional<IOException> error) {
		static Reply failed(IOException e) {
			return new Reply(0, Optional.empty(), OptionalLong.empty(), Optional.empty(), Optional.of(e));
		}

		/** Found, also by a ranged GET for a file with no content at all, which is not satisfiable */
		boolean found() {
			return error.isEmpty() && ((status >= 200 && status < 300) || status == 416);
		}
	}

	/** Asking one set of addresses: what is known of each host, from what it has answered */
	private final class Run {
		private final Map<String, Host> hosts = new ConcurrentHashMap<>();

		Result check(String address, Expectation expected) throws InterruptedException {
			URI uri;
			try {
				uri = URI.create(address);
			} catch (IllegalArgumentException e) {
				return new Result(address, 0, Optional.of("Not a web address"));
			}
			if (uri.getHost() == null) {
				return new Result(address, 0, Optional.of("Not a web address"));
			}
			Host host = hosts.computeIfAbsent(uri.getHost().toLowerCase(Locale.ROOT), Host::new);
			Optional<String> last = Optional.empty();
			int status = 0;
			for (int attempt = 1; attempt <= manners.attempts(); attempt++) {
				if (host.gaveUp().isPresent()) {
					return skipped(address, host);
				}
				host.awaitTurn();
				Reply reply;
				try {
					// It may have been given up on while this waited for its turn
					if (host.gaveUp().isPresent()) {
						return skipped(address, host);
					}
					reply = ask(uri, expected);
				} finally {
					host.done();
				}
				status = reply.status();
				if (reply.found()) {
					host.answered();
					return new Result(address, status, Optional.empty(), reply.size(), reply.type());
				}
				// What to do about it: a pause before trying again (none if it is not worth trying again)
				Optional<Duration> pause;
				if (reply.error().isPresent()) {
					IOException e = reply.error().get();
					boolean timedOut = e instanceof HttpTimeoutException;
					last = Optional.of(timedOut ? "no answer within " + seconds(timeout) : message(e));
					host.failed(timedOut ? "its requests time out" : "it can't be reached");
					// A timeout has cost a whole wait already: tried once more, not for as long as there are tries
					pause = timedOut && attempt > 1 ? Optional.empty() : Optional.of(timedOut ? Duration.ZERO : backoff(attempt));
				} else if (status == 429 || status == 503) {
					host.answered();
					// The server says when: for all requests to the host, not only this one
					Duration wait = reply.retryAfter().orElse(backoff(attempt));
					if (wait.compareTo(manners.longestWait()) > 0) {
						host.giveUp("it asked to wait " + seconds(wait) + ", longer than is worth it");
						return new Result(address, status, Optional.of("Not available: HTTP " + status
								+ ", asked to wait " + seconds(wait) + " before asking again"));
					}
					host.pause(wait);
					last = Optional.of("HTTP " + status);
					pause = Optional.of(Duration.ZERO);
				} else if (status >= 500) {
					host.answered();
					last = Optional.of("HTTP " + status);
					pause = Optional.of(backoff(attempt));
				} else {
					// A definite answer: not there, or not allowed
					host.answered();
					return new Result(address, status, Optional.of("Not available: HTTP " + status
							+ (status >= 300 && status < 400 ? " (a redirect not followed)" : "")));
				}
				if (pause.isEmpty()) {
					break;
				}
				if (attempt < manners.attempts() && !pause.get().isZero()) {
					TimeUnit.NANOSECONDS.sleep(pause.get().toNanos());
				}
			}
			return new Result(address, status, Optional.of("Not available: " + last.orElse("not answered")
					+ (manners.attempts() > 1 ? " (tried " + manners.attempts() + " times)" : "")));
		}

		/** The pause before the next try: the first, then twice as long each time */
		private Duration backoff(int attempt) {
			return manners.backoff().multipliedBy(1L << (attempt - 1));
		}

		private Result skipped(String address, Host host) {
			return new Result(address, 0, Optional.of("Could not check: " + host.name + " is not asked any more, as "
					+ host.gaveUp().orElse("it did not answer")));
		}

		/** HEAD, and a GET for one byte where it shows a server that does not do HEAD right, or a length to confirm */
		private Reply ask(URI uri, Expectation expected) throws InterruptedException {
			try {
				HttpResponse<Void> head = http.send(request(uri, "HEAD", false), HttpResponse.BodyHandlers.discarding());
				int status = head.statusCode();
				if (status >= 200 && status < 300) {
					OptionalLong size = lengthOf(head);
					if (expected.disagrees(size)) {
						// HEAD does not always say what a GET would: 0, or the page's, and a GET for a byte tells the file's
						Reply confirmed = get(uri);
						if (confirmed.found()) {
							return confirmed;
						}
					}
					return reply(head, size);
				}
				// Not allowed or not found by HEAD: the server may only not do HEAD right, as GET shows. Not so for
				// answers that are final (401 needs a login, 410 is gone for good), or say to wait (429, 503)
				boolean tryGet = (status >= 400 && status < 500 && status != 401 && status != 410 && status != 429)
						|| status == 501;
				if (tryGet) {
					return get(uri);
				}
				return reply(head, OptionalLong.empty());
			} catch (IOException e) {
				return Reply.failed(e);
			}
		}

		private Reply get(URI uri) throws IOException, InterruptedException {
			HttpResponse<InputStream> response = http.send(request(uri, "GET", true), HttpResponse.BodyHandlers.ofInputStream());
			// Not read: closed, so that a file that did not honour the range is not downloaded
			try (InputStream in = response.body()) {
				return reply(response, lengthOf(response));
			}
		}

		private Reply reply(HttpResponse<?> response, OptionalLong size) {
			return new Reply(response.statusCode(), retryAfter(response.headers()), size,
					response.headers().firstValue("Content-Type").map(t -> t.replaceFirst(";.*", "").strip().toLowerCase(Locale.ROOT)),
					Optional.empty());
		}
	}

	/** One host: how many are asked at once, and when to leave it be */
	private final class Host {
		final String name;
		private final Semaphore turns = new Semaphore(manners.perHost());
		private long notBefore;
		private int failures;
		private Optional<String> gaveUp = Optional.empty();

		Host(String name) {
			this.name = name;
		}

		synchronized Optional<String> gaveUp() {
			return gaveUp;
		}

		synchronized void giveUp(String why) {
			if (gaveUp.isEmpty()) {
				gaveUp = Optional.of(why);
			}
		}

		/** No requests to this host until then, from any thread: what a Retry-After asks */
		synchronized void pause(Duration wait) {
			notBefore = Math.max(notBefore, System.nanoTime() + wait.toNanos());
		}

		private synchronized long waitNanos() {
			return notBefore - System.nanoTime();
		}

		/** Wait for the pause to be over, and for a place among those asked at once */
		void awaitTurn() throws InterruptedException {
			while (true) {
				long wait = waitNanos();
				if (wait > 0) {
					TimeUnit.NANOSECONDS.sleep(wait);
					continue;
				}
				turns.acquire();
				if (waitNanos() > 0) {
					// Paused while it waited for its place
					turns.release();
					continue;
				}
				return;
			}
		}

		void done() {
			turns.release();
		}

		synchronized void answered() {
			failures = 0;
		}

		/** A timeout or a failed connection: after enough running, this host is not asked any more */
		synchronized void failed(String why) {
			failures++;
			if (failures >= manners.failuresBeforeGivingUp()) {
				giveUp("it did not answer: " + why + " " + failures + " times running");
			}
		}
	}

	private static final Pattern DELAY = Pattern.compile("[0-9]{1,9}");

	/**
	 * How long a server asks to wait before asking again: <code>Retry-After</code>
	 * (RFC 9110, 10.2.3) as a number of seconds or an HTTP date.
	 */
	static Optional<Duration> retryAfter(HttpHeaders headers) {
		return headers.firstValue("Retry-After").map(String::strip).flatMap(value -> {
			if (DELAY.matcher(value).matches()) {
				return Optional.of(Duration.ofSeconds(Long.parseLong(value)));
			}
			try {
				Duration until = Duration.between(ZonedDateTime.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
				return Optional.of(until.isNegative() ? Duration.ZERO : until);
			} catch (java.time.format.DateTimeParseException e) {
				return Optional.empty();
			}
		});
	}

	private static final Pattern CONTENT_RANGE = Pattern.compile("bytes\\s+(?:[0-9]+-[0-9]+|\\*)/([0-9]+)");

	/**
	 * How long the content is, as the response says: the whole of it from a
	 * <code>Content-Range</code> (of a ranged GET), else a
	 * <code>Content-Length</code> of a response that is not a range or encoded
	 * (as that is the encoded length, not the file's); none if it does not say.
	 */
	static OptionalLong lengthOf(HttpResponse<?> response) {
		HttpHeaders headers = response.headers();
		Optional<String> range = headers.firstValue("Content-Range");
		if (range.isPresent()) {
			java.util.regex.Matcher m = CONTENT_RANGE.matcher(range.get());
			return m.matches() ? OptionalLong.of(Long.parseLong(m.group(1))) : OptionalLong.empty();
		}
		boolean encoded = headers.firstValue("Content-Encoding").filter(e -> !e.equalsIgnoreCase("identity")).isPresent();
		return encoded || response.statusCode() == 206 ? OptionalLong.empty() : headers.firstValueAsLong("Content-Length");
	}

	/** @param oneByte Whether to ask for the first byte only (RFC 9110, 14.2), as a server may not give more */
	private HttpRequest request(URI uri, String method, boolean oneByte) {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(timeout).header("User-Agent", userAgent)
				.header("Accept", ACCEPT).method(method, HttpRequest.BodyPublishers.noBody());
		if (oneByte) {
			request.header("Range", "bytes=0-0");
		}
		return request.build();
	}

	private static String seconds(Duration duration) {
		return duration.toMillis() < 10_000 ? String.format(Locale.ROOT, "%.1f s", duration.toMillis() / 1000.0)
				: duration.toSeconds() + " s";
	}

	private static String message(IOException e) {
		return e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName() : e.getMessage();
	}
}
