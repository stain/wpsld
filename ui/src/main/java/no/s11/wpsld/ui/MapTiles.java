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
package no.s11.wpsld.ui;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javafx.scene.image.Image;

/**
 * The square images a map is drawn from, 256 pixels each, by zoom level and
 * position as the web's "slippy maps" number them.
 */
public interface MapTiles {
	int SIZE = 256;

	/**
	 * The tile, once loaded; empty if there is none, e.g. offline.
	 */
	CompletableFuture<Optional<Image>> tile(int zoom, int x, int y);

	/** Where the map's data comes from, to credit it on the map */
	String attribution();

	/** Its licence and credits, if it has a page for them */
	Optional<String> attributionPage();

	/**
	 * No tiles: the map is blank, but places can still be shown and picked,
	 * e.g. in tests, which must not reach the web.
	 */
	MapTiles NONE = new MapTiles() {
		@Override
		public CompletableFuture<Optional<Image>> tile(int zoom, int x, int y) {
			return CompletableFuture.completedFuture(Optional.empty());
		}

		@Override
		public String attribution() {
			return "";
		}

		@Override
		public Optional<String> attributionPage() {
			return Optional.empty();
		}
	};

	/**
	 * OpenStreetMap's standard tiles, kept in memory while the application
	 * runs, so panning back doesn't ask for them again. Their tile usage
	 * policy asks applications to name themselves in the User-Agent.
	 *
	 * @see <a href="https://operations.osmfoundation.org/policies/tiles/">OSM tile usage policy</a>
	 */
	static MapTiles openStreetMap(String userAgent) {
		return new OpenStreetMap(userAgent);
	}

	/**
	 * The tiles the application uses: OpenStreetMap's, unless the system
	 * property <code>wpsld.mapTiles</code> is <code>none</code> (as in the
	 * tests).
	 */
	static MapTiles forApplication(String userAgent) {
		return "none".equals(System.getProperty("wpsld.mapTiles")) ? NONE : openStreetMap(userAgent);
	}

	final class OpenStreetMap implements MapTiles {
		/** About 16 MB of tiles at most */
		private static final int CACHED = 512;
		private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL).build();
		private final String userAgent;
		private final Map<String, CompletableFuture<Optional<Image>>> cache = new LinkedHashMap<>(64, 0.75f, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<Optional<Image>>> eldest) {
				return size() > CACHED;
			}
		};

		private OpenStreetMap(String userAgent) {
			this.userAgent = userAgent;
		}

		@Override
		public synchronized CompletableFuture<Optional<Image>> tile(int zoom, int x, int y) {
			String key = zoom + "/" + x + "/" + y;
			CompletableFuture<Optional<Image>> cached = cache.get(key);
			if (cached != null && !(cached.isDone() && cached.join().isEmpty())) {
				return cached;
			}
			HttpRequest request = HttpRequest.newBuilder(URI.create("https://tile.openstreetmap.org/" + key + ".png"))
					.timeout(Duration.ofSeconds(20)).header("User-Agent", userAgent).GET().build();
			CompletableFuture<Optional<Image>> tile = http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
					.thenApply(response -> response.statusCode() == 200
							? Optional.of(new Image(new ByteArrayInputStream(response.body())))
							: Optional.<Image>empty())
					.exceptionally(e -> Optional.empty());
			cache.put(key, tile);
			return tile;
		}

		@Override
		public String attribution() {
			return "© OpenStreetMap contributors";
		}

		@Override
		public Optional<String> attributionPage() {
			return Optional.of("https://www.openstreetmap.org/copyright");
		}
	}
}
