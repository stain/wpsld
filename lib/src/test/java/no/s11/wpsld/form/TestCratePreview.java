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
package no.s11.wpsld.form;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

public class TestCratePreview {
	private static com.fasterxml.jackson.databind.JsonNode ref(String id) {
		return JsonNodeFactory.instance.objectNode().put("@id", id);
	}

	@Test
	public void preview() {
		Crate crate = new Crate();
		Entity root = crate.getRoot();
		root.set("name", Optional.of(TextNode.valueOf("Bird counts <2025>")));
		root.set("description", Optional.of(TextNode.valueOf("Counts & notes")));
		Entity file = crate.add("File");
		crate.rename(file, "data/my%20counts.csv");
		file.set("name", Optional.of(TextNode.valueOf("Counts")));
		Entity alice = crate.add("Person");
		crate.rename(alice, "#alice");
		alice.set("name", Optional.of(TextNode.valueOf("Alice")));
		alice.set("url", Optional.of(TextNode.valueOf("https://example.com/alice")));
		root.setAll("hasPart", List.of(ref("data/my%20counts.csv")));
		root.setAll("author", List.of(ref("#alice"), ref("https://orcid.org/0000-0002-1825-0097")));
		String html = CratePreview.html(crate, Labels::fromTerm, "wpsld 1.0");

		assertTrue(CratePreview.isOurs(html));
		assertTrue(html.contains("<title>Bird counts &lt;2025&gt;</title>"));
		assertTrue(html.contains("<p>Counts &amp; notes</p>"));
		// Each entity a section, linked to by its anchor
		assertTrue(html.contains("<section class=\"entity\" id=\"entity-0\">"));
		assertTrue(html.contains("<a href=\"#entity-2\">Alice</a>"));
		// The file by its path, the web by its address
		assertTrue(html, html.contains("<a href=\"data/my%20counts.csv\">data/my%20counts.csv</a>"));
		assertTrue(html.contains("<a href=\"https://orcid.org/0000-0002-1825-0097\">"));
		assertTrue(html.contains("<a href=\"https://example.com/alice\">"));
		assertTrue(html.contains("<th>Has part</th>"));
		assertTrue(html.contains("<h2>Files and folders</h2>"));
		assertTrue(html.contains("<h2>Person</h2>"));
		assertFalse(CratePreview.isOurs("<html><meta name=\"generator\" content=\"rochtml\"></html>"));
	}

	/** Compact IRIs link to their expanded address, shown as written */
	@Test
	public void compactIris() throws Exception {
		Crate crate = new no.s11.wpsld.crate.CrateReader().read(new com.fasterxml.jackson.databind.ObjectMapper()
				.readTree("""
						{"@context": ["https://w3id.org/ro/crate/1.3/context", {"s": "http://schema.org/"}],
						 "@graph": [
						  {"@id": "ro-crate-metadata.json", "@type": "CreativeWork", "about": {"@id": "./"}},
						  {"@id": "./", "@type": "Dataset", "mentions": {"@id": "s:CompletedActionStatus"}},
						  {"@id": "s:ActiveActionStatus", "@type": "Thing"}]}"""), "test").crate().orElseThrow();
		assertEquals("http://schema.org/ActiveActionStatus", crate.expandId("s:ActiveActionStatus"));
		assertEquals("data/a.csv", crate.expandId("data/a.csv"));
		assertEquals("https://example.com/x", crate.expandId("https://example.com/x"));
		String html = CratePreview.html(crate, Labels::fromTerm, "wpsld 1.0");
		assertTrue(html, html.contains(
				"<a href=\"http://schema.org/ActiveActionStatus\">s:ActiveActionStatus</a>"));
		assertTrue(html, html.contains(
				"<a href=\"http://schema.org/CompletedActionStatus\">s:CompletedActionStatus</a>"));
	}

	/**
	 * Files mentioned rather than parts link to their paths too; each
	 * entity links to the top; the metadata is in a script, which a
	 * description can't end.
	 */
	@Test
	public void linksTopAndScript() throws Exception {
		Crate crate = new Crate();
		Entity picture = crate.add("ImageObject");
		crate.rename(picture, "Picture1.png");
		crate.getRoot().setAll("mentions", List.of(ref("Picture1.png")));
		crate.getRoot().set("description", Optional.of(TextNode.valueOf("Not </script> here")));
		String html = CratePreview.html(crate, Labels::fromTerm, "wpsld 1.0");
		assertTrue(html, html.contains("<a href=\"Picture1.png\">Picture1.png</a>"));
		assertTrue(html.contains("<header id=\"top\">"));
		assertTrue(html.contains("<p class=\"top\"><a href=\"#top\">Top</a></p>"));
		int start = html.indexOf("<script type=\"application/ld+json\">");
		int end = html.indexOf("</script>", start);
		assertTrue(start > 0 && start < html.indexOf("</head>"));
		String json = html.substring(html.indexOf('\n', start) + 1, end);
		assertEquals(crate.toJson(), new com.fasterxml.jackson.databind.ObjectMapper().readTree(json));
		assertFalse(json.contains("</"));
		// The description in the header, not again in the crate's table
		assertTrue(html.contains("<p>Not &lt;/script&gt; here</p>"));
		assertFalse(html.contains("<th>Description</th>"));
	}

	/** Descriptions keep their paragraphs and line breaks, or are Markdown */
	@Test
	public void descriptions() {
		assertEquals("<p>One<br>\ntwo</p>\n<p>Three &amp; four</p>\n",
				Markdown.toHtml("One\ntwo\n\n  \nThree & four\n"));
		assertFalse(Markdown.looksLike("Counts in dim_date and air_quality_hourly, 2 * 3 = 6"));
		assertFalse(Markdown.looksLike("- one item only"));
		assertTrue(Markdown.looksLike("Some **bold** text"));
		assertTrue(Markdown.looksLike("# Title\n\nText"));
		assertTrue(Markdown.looksLike("Run `make`"));
		assertTrue(Markdown.looksLike("See [the site](https://example.com)"));
		assertTrue(Markdown.looksLike("- one\n- two"));
		assertTrue(Markdown.looksLike("| a | b |\n|---|---|\n| 1 | 2 |"));
		String html = Markdown.toHtml("# Title\n\n**DuckDB** <b>raw</b> [x](javascript:alert(1))\n\n| a | b |\n|---|---|\n| 1 | 2 |");
		assertTrue(html, html.contains("<h1>Title</h1>"));
		assertTrue(html.contains("<strong>DuckDB</strong>"));
		// HTML in the text stays text; scripts aren't links
		assertTrue(html.contains("&lt;b&gt;raw&lt;/b&gt;"));
		assertFalse(html.contains("javascript:"));
		assertTrue(html.contains("<table>"));
	}
}
