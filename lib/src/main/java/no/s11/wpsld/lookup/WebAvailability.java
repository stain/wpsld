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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

/**
 * Whether a crate's web resources are available: each data entity with a
 * web address answers, after any redirects, with success (2xx). Asked with
 * HEAD, else GET where a server doesn't allow HEAD.
 */
public class WebAvailability {
	/**
	 * What a web address answered: its last status, 0 if none, and the
	 * problem, if it isn't available
	 */
	public record Result(String address, int status, Optional<String> problem) {
		public boolean available() {
			return problem.isEmpty();
		}
	}

	/** At most this many asked at once, not to burden one server */
	private static final int AT_ONCE = 4;

	private final HttpClient http;
	private final Duration timeout;
	private final String userAgent;

	/**
	 * @param http following redirects, e.g. ProfileCache's
	 */
	public WebAvailability(HttpClient http, Duration timeout, String userAgent) {
		this.http = Objects.requireNonNull(http);
		this.timeout = Objects.requireNonNull(timeout);
		this.userAgent = Objects.requireNonNull(userAgent);
	}

	/** The data entities with web (http, https) addresses, to check */
	public static List<String> addresses(Crate crate) {
		return crate.dataEntities().stream().map(Entity::getId).filter(WebAvailability::isWeb).distinct().toList();
	}

	private static boolean isWeb(String id) {
		String lower = id.toLowerCase(java.util.Locale.ROOT);
		return lower.startsWith("http://") || lower.startsWith("https://");
	}

	/** Check these addresses, a few at once, in their order */
	public List<Result> check(List<String> addresses) throws InterruptedException {
		ExecutorService pool = Executors.newFixedThreadPool(AT_ONCE, runnable -> {
			Thread thread = new Thread(runnable, "Web availability");
			thread.setDaemon(true);
			return thread;
		});
		try {
			List<Future<Result>> checks = addresses.stream().map(a -> pool.submit(() -> check(a))).toList();
			List<Result> results = new java.util.ArrayList<>();
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
		URI uri;
		try {
			uri = URI.create(address);
		} catch (IllegalArgumentException e) {
			return new Result(address, 0, Optional.of("Not a web address"));
		}
		try {
			int status = status(uri, "HEAD");
			// Some servers don't allow HEAD, or answer it otherwise than GET
			if (status == 405 || status == 501 || status == 403 || status == 404) {
				status = status(uri, "GET");
			}
			if (status >= 200 && status < 300) {
				return new Result(address, status, Optional.empty());
			}
			return new Result(address, status, Optional.of("Not available: HTTP " + status
					+ (status >= 300 && status < 400 ? " (a redirect not followed)" : "")));
		} catch (IOException e) {
			return new Result(address, 0, Optional.of("Not available: "
					+ (e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName()
							: e.getMessage())));
		}
	}

	private int status(URI uri, String method) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).header("User-Agent", userAgent)
				.method(method, HttpRequest.BodyPublishers.noBody()).build();
		return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
	}
}
