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

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
import org.junit.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * FAIR Signposting level 2: a landing page whose only link is to a link set
 * (RFC 9264), which lists its <code>describedby</code> and <code>item</code>s,
 * in the shapes Zenodo's and ROHub's have, with a local server.
 */
public class TestLinksetRetrieval {
	private static final String CRATE = "{\"@context\": \"https://w3id.org/ro/crate/1.1/context\", \"@graph\": []}";
	private HttpServer server;

	@After
	public void stop() {
		if (server != null) {
			server.stop(0);
		}
	}

	private URI serve() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			String origin = "http://" + exchange.getRequestHeaders().getFirst("Host");
			String path = exchange.getRequestURI().getPath();
			String type = "text/html";
			String link = null;
			String body;
			switch (path) {
			// A link set anchored at the page, in a Link header, as the JSON format
			case "/l1/page" -> {
				link = "</l1/ls.json>; rel=\"linkset\"; type=\"application/linkset+json\"";
				body = "<html>landing</html>";
			}
			case "/l1/ls.json" -> {
				type = "application/linkset+json";
				body = "{\"linkset\": [{\"anchor\": \"" + origin + "/l1/page\", \"describedby\": ["
						+ "{\"href\": \"/l1/meta.xml\", \"type\": \"application/xml\"},"
						+ "{\"href\": \"/l1/crate.json\", \"type\": \"application/ld+json\"}],"
						+ "\"item\": [{\"href\": \"/l1/file.csv\", \"type\": \"text/csv\"}]}]}";
			}
			// ROHub's: one entry, anchored at an API address that is not the page's
			case "/l2/page" -> {
				link = "</l2/ls>; rel=\"linkset\"; type=\"application/linkset\", </l2/ls.json>; rel=\"linkset\"; type=\"application/linkset+json\"";
				body = "<html>landing</html>";
			}
			case "/l2/ls.json" -> {
				type = "application/linkset+json; charset=utf-8";
				body = "{\"linkset\": [{\"anchor\": \"" + origin + "/l2/api/ros/1/\", \"cite_as\": [{\"href\": \"https://doi.org/10.1/x\"}],"
						+ "\"describedby\": {\"href\": \"" + origin + "/l2/crate.json\", \"type\": \"application/ld+json\"},"
						+ "\"item\": [{\"href\": \"/l2/r/1\"}, {\"href\": \"/l2/r/2\"}]}]}";
			}
			// Zenodo's: other anchors too, a file's own link back to the page, and another page's metadata
			case "/l3/page" -> {
				link = "</l3/ls.json>; rel=\"linkset\"; type=\"application/linkset+json\"";
				body = "<html>landing</html>";
			}
			case "/l3/ls.json" -> {
				type = "application/linkset+json";
				body = "{\"linkset\": [{\"anchor\": \"" + origin + "/l3/files/a.zip\", \"collection\": [{\"href\": \"" + origin + "/l3/page\"}]},"
						+ "{\"anchor\": \"" + origin + "/l3/other\", \"describedby\": [{\"href\": \"/l3/wrong.json\", \"type\": \"application/ld+json\"}]},"
						+ "{\"anchor\": \"" + origin + "/l3/page\", \"describedby\": [{\"href\": \"/l3/crate.json\", \"type\": \"application/ld+json\"}]}]}";
			}
			// Only in the HTML, and as text
			case "/l4/page" -> {
				body = "<html><head><link rel=\"linkset\" type=\"application/linkset\" href=\"/l4/ls.txt\"></head></html>";
			}
			case "/l4/ls.txt" -> {
				type = "application/linkset";
				body = "</l4/other.json>; rel=\"describedby\"; type=\"application/ld+json\"; anchor=\"" + origin + "/elsewhere\",\n"
						+ "</l4/crate.json>; rel=\"describedby\"; type=\"application/ld+json\"; anchor=\"" + origin + "/l4/page\"\n"
						+ "</l4/file.csv>; rel=\"item\"; type=\"text/csv\"; anchor=\"" + origin + "/l4/page\"\n";
			}
			// A link set with only items: no crate to find
			case "/l5/page" -> {
				link = "</l5/ls.json>; rel=\"linkset\"; type=\"application/linkset+json\"";
				body = "<html>landing</html>";
			}
			case "/l5/ls.json" -> {
				type = "application/linkset+json";
				body = "{\"linkset\": [{\"anchor\": \"" + origin + "/l5/page\", \"item\": [{\"href\": \"/l5/a.csv\", \"type\": \"text/csv\"}]}]}";
			}
			// A link set that can't be had
			case "/l6/page" -> {
				link = "</l6/missing>; rel=\"linkset\"; type=\"application/linkset+json\"";
				body = "<html>landing</html>";
			}
			case "/l1/crate.json", "/l2/crate.json", "/l3/crate.json", "/l4/crate.json" -> {
				type = "application/ld+json";
				body = CRATE;
			}
			default -> {
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			}
			if (link != null) {
				exchange.getResponseHeaders().add("Link", link);
			}
			exchange.getResponseHeaders().add("Content-Type", type);
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
	}

	private static CrateRetriever retriever() {
		return new CrateRetriever(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build(), Duration.ofSeconds(5));
	}

	private void found(String page, String crate) throws Exception {
		URI site = serve();
		CrateRetriever.Retrieved got = retriever().retrieve(site.resolve(page));
		assertEquals(site.resolve(crate), got.source());
		assertEquals(List.of("linkset"), got.steps());
		assertEquals(Optional.of(site.resolve(page)), got.landing());
	}

	/** The crate is the describedby of the JSON type, of the page's own entry, not the XML one before it */
	@Test
	public void anchoredAtThePage() throws Exception {
		found("/l1/page", "/l1/crate.json");
	}

	/** An entry anchored at the API address of what the page describes is the only one that describes anything: it is the page's */
	@Test
	public void theOnlyEntryThatDescribes() throws Exception {
		found("/l2/page", "/l2/crate.json");
	}

	/** Other anchors' links are not the page's: a file's link back to it, and another page's metadata */
	@Test
	public void manyAnchors() throws Exception {
		found("/l3/page", "/l3/crate.json");
	}

	/** A link in the HTML to a link set in text, in the Link header's syntax, with an anchor for each link */
	@Test
	public void textFormatLinkedFromHtml() throws Exception {
		found("/l4/page", "/l4/crate.json");
	}

	/** Items are not what describes: with no describedby, as before, there is nothing to follow, and it is said so */
	@Test
	public void noDescribedby() throws Exception {
		URI site = serve();
		IOException e = assertThrows(IOException.class, () -> retriever().retrieve(site.resolve("/l5/page")));
		assertTrue(e.getMessage(), e.getMessage().contains("/l5/page"));
	}

	@Test
	public void linksetNotThere() throws Exception {
		URI site = serve();
		IOException e = assertThrows(IOException.class, () -> retriever().retrieve(site.resolve("/l6/page")));
		assertTrue(e.getMessage(), e.getMessage().contains("/l6/page"));
	}

	@Test
	public void parsing() {
		URI base = URI.create("https://example.org/linkset");
		// Either format, by the media type or by its first character; garbage is nothing
		assertEquals(1, Linkset.parse("{\"linkset\": [{\"anchor\": \"https://example.org/p\", \"item\": [{\"href\": \"a\"}]}]}"
				.getBytes(StandardCharsets.UTF_8), "text/plain", base).size());
		assertEquals(List.of(), Linkset.parse("not a link set".getBytes(StandardCharsets.UTF_8), "application/linkset", base));
		assertEquals(List.of(), Linkset.parse("{\"linkset\": 3, ".getBytes(StandardCharsets.UTF_8), "application/linkset+json", base));
		// The page's own address with or without a final /, or a fragment
		List<Linkset.Entry> entries = Linkset.parse(("{\"linkset\": [{\"anchor\": \"https://example.org/p/\", \"describedby\": [{\"href\": \"m\", \"type\": \"application/json\"}]},"
				+ "{\"anchor\": \"https://example.org/q\", \"describedby\": [{\"href\": \"n\"}]}]}").getBytes(StandardCharsets.UTF_8),
				"application/linkset+json", base);
		assertEquals(List.of(URI.create("https://example.org/m")), Linkset.about(entries, Set.of(URI.create("https://example.org/p#top")))
				.stream().map(Linkset.Link::href).toList());
		// Two entries that describe, and neither is the page's: no telling which
		assertEquals(List.of(), Linkset.about(entries, Set.of(URI.create("https://example.org/other"))));
		// Not a link to a file
		assertEquals(List.of(), Linkset.parse("{\"linkset\": [{\"anchor\": \"https://example.org/p\", \"item\": [{\"href\": \"file:///etc/passwd\"}]}]}"
				.getBytes(StandardCharsets.UTF_8), "application/linkset+json", base).get(0).links());
	}
}
