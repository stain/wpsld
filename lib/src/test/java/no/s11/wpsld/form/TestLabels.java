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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TestLabels {
	@Test
	public void fromTerm() {
		assertEquals("Name", Labels.fromTerm("name"));
		assertEquals("Has version", Labels.fromTerm("https://w3id.org/ro-id/rohub/model#has_version"));
		assertEquals("Is part of", Labels.fromTerm("https://schema.org/isPartOf"));
		assertEquals("Creation mode", Labels.fromTerm("http://w3id.org/ro-id/rohub/model#creation_mode"));
		assertEquals("https://example.com/", Labels.fromTerm("https://example.com/"));
		assertEquals("Date published", Labels.fromTerm("datePublished"));
		assertEquals("Is based on", Labels.fromTerm("isBasedOn"));
		assertEquals("URL", Labels.fromTerm("url"));
		assertEquals("Content URL", Labels.fromTerm("contentUrl"));
		assertEquals("As WKT", Labels.fromTerm("asWKT"));
		assertEquals("ISBN", Labels.fromTerm("isbn"));
		assertEquals("GTIN-13", Labels.fromTerm("gtin13"));
		assertEquals("IATA code", Labels.fromTerm("iataCode"));
		assertEquals("Number of tracks", Labels.fromTerm("numTracks"));
		assertEquals("Software add-on", Labels.fromTerm("softwareAddOn"));
		assertEquals("Add-on", Labels.fromTerm("addOn"));
		assertEquals("COVID-19 ICU beds", Labels.fromTerm("cvdNumICUBeds"));
		assertEquals("Has POS", Labels.fromTerm("hasPOS"));
		assertEquals("URL template", Labels.fromTerm("URLTemplate"));
		assertEquals("@id", Labels.fromTerm("@id"));
	}

	@Test
	public void plainText() {
		assertEquals("Date of first publication or broadcast. For example the date a CreativeWork was broadcast",
				Labels.plainText("Date of first publication or broadcast. For example the date a [[CreativeWork]] was broadcast"));
		assertEquals("See the NATO stock number of a Product.", Labels.plainText(
				"See the [NATO stock number](https://en.wikipedia.org/wiki/NATO_Stock_Number) of a [[Product]]."));
		assertEquals("The GS1 digital link.\nUse <code>",
				Labels.plainText("The <a href=\"https://www.gs1.org/\">GS1 digital link</a>.<br/>Use &lt;code&gt;"));
	}
}
