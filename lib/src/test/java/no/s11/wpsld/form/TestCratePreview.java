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
}
