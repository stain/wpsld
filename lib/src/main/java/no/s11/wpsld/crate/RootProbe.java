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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Finds out, without asking the user, whether a candidate for the RO-Crate
 * Root of a semi-detached crate (see {@link WebCrate#suggestedRoots}) is where
 * its files are: with one HTTP <code>HEAD</code> for the first file of the
 * root's <code>hasPart</code>, relative to the candidate. It is there if the
 * server answers 200 for it, and, as a server that answers 200 for any path
 * (a web application's page) says nothing, 404 or the like for a path made up
 * for the purpose. A server that does not allow HEAD, or gives anything else,
 * says nothing either: the user chooses then. Nothing is fetched but headers,
 * from at most {@link #MAX_CANDIDATES} candidates.
 */
public class RootProbe {
	public static final int MAX_CANDIDATES = 4;
	private final HttpClient http;
	private final Duration timeout;

	/**
	 * @param http    Following redirects
	 * @param timeout Longest wait for each response
	 */
	public RootProbe(HttpClient http, Duration timeout) {
		this.http = http;
		this.timeout = timeout;
	}

	/**
	 * The <code>@id</code> of the file whose place is looked for: the first of
	 * the root's parts that is a relative file, else the first relative part.
	 * Taken from the crate before the probe runs in the background, as a crate
	 * being edited is not read by other threads.
	 */
	public static Optional<String> firstFile(Crate crate) {
		List<Entity> parts = crate.getRoot().getAll("hasPart").stream().map(v -> v.path("@id").asText(""))
				.filter(WebCrate::isRelative).map(crate::get).flatMap(Optional::stream).toList();
		return parts.stream().filter(e -> e.getTypes().contains("File")).findFirst()
				.or(() -> parts.stream().findFirst()).map(Entity::getId);
	}

	/** As {@link #find(String, List)} for the crate's first file and the candidates its {@link WebCrate} suggests */
	public Optional<WebCrate.Root> find(Crate crate, WebCrate web) throws InterruptedException {
		Optional<String> file = firstFile(crate);
		return file.isEmpty() ? Optional.empty() : find(file.get(), web.suggestedRoots(crate.getRoot()));
	}

	/**
	 * The first candidate beside which a file is. <em>Not finding one does not
	 * mean there is no root</em>: the files may be inside an archive among the
	 * record's items, which is not looked into, so the user is left to choose.
	 *
	 * @param file       The file's relative <code>@id</code>
	 * @param candidates In the order to try them
	 * @return None if there is none, or it can't be told
	 */
	public Optional<WebCrate.Root> find(String file, List<WebCrate.Root> candidates) throws InterruptedException {
		for (WebCrate.Root candidate : candidates.stream().limit(MAX_CANDIDATES).toList()) {
			Optional<URI> there = resolve(candidate.address(), file);
			// A candidate that says yes to anything says nothing
			Optional<URI> nonsense = resolve(candidate.address(), "wpsld-probe-" + UUID.randomUUID());
			if (there.isPresent() && nonsense.isPresent() && status(there.get()) == 200 && status(nonsense.get()) != 200) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}

	private static Optional<URI> resolve(URI base, String id) {
		try {
			URI reference;
			try {
				reference = new URI(id);
			} catch (URISyntaxException e) {
				reference = new URI(null, null, id, null);
			}
			return Optional.of(base.resolve(reference).normalize());
		} catch (URISyntaxException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	/** The status of a HEAD request, 0 if there is none */
	private int status(URI uri) throws InterruptedException {
		try {
			return http.send(HttpRequest.newBuilder(uri).timeout(timeout).method("HEAD", HttpRequest.BodyPublishers.noBody())
					.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
		} catch (IOException e) {
			return 0;
		}
	}
}
