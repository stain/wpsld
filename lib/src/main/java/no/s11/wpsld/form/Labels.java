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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Human readable names for properties, as neither schema.org nor the MASP
 * profiles give names other than the terms themselves.
 */
public final class Labels {
	/** Words written in capitals, e.g. in <code>isbn</code> or <code>url</code> */
	private static final Set<String> ACRONYMS = Set.of("acriss", "asin", "bcc", "cc", "css", "doi", "duns", "eidr",
			"exif", "gtin", "http", "iata", "icao", "id", "isbn", "isic", "iso", "isrc", "issn", "iswc", "iupac", "lei", "mpn",
			"naics", "npi", "nsn", "pos", "rsvp", "sku", "sms", "smiles", "toc", "url", "us", "vat");

	/** Names of schema.org terms that splitting the term gets wrong */
	private static final Map<String, String> NAMES = Map.ofEntries(
			Map.entry("cvdCollectionDate", "COVID-19 collection date"),
			Map.entry("cvdFacilityCounty", "COVID-19 facility county"),
			Map.entry("cvdFacilityId", "COVID-19 facility ID"),
			Map.entry("cvdNumBeds", "COVID-19 inpatient beds"),
			Map.entry("cvdNumBedsOcc", "COVID-19 inpatient beds occupied"),
			Map.entry("cvdNumC19Died", "COVID-19 deaths"),
			Map.entry("cvdNumC19HOPats", "COVID-19 hospital onset patients"),
			Map.entry("cvdNumC19HospPats", "COVID-19 hospitalized patients"),
			Map.entry("cvdNumC19MechVentPats", "COVID-19 ventilated patients"),
			Map.entry("cvdNumC19OFMechVentPats", "COVID-19 ventilated overflow patients"),
			Map.entry("cvdNumC19OverflowPats", "COVID-19 overflow patients"),
			Map.entry("cvdNumICUBeds", "COVID-19 ICU beds"),
			Map.entry("cvdNumICUBedsOcc", "COVID-19 ICU beds occupied"),
			Map.entry("cvdNumTotBeds", "COVID-19 all hospital beds"),
			Map.entry("cvdNumVent", "COVID-19 ventilators"),
			Map.entry("cvdNumVentUse", "COVID-19 ventilators in use"),
			Map.entry("emissionsCO2", "CO2 emissions"),
			Map.entry("gtin8", "GTIN-8"), Map.entry("gtin12", "GTIN-12"), Map.entry("gtin13", "GTIN-13"),
			Map.entry("gtin14", "GTIN-14"),
			Map.entry("hasGS1DigitalLink", "Has GS1 Digital Link"),
			Map.entry("inChI", "InChI"), Map.entry("inChIKey", "InChIKey"),
			Map.entry("isicV4", "ISIC Rev. 4"),
			Map.entry("rxcui", "RxCUI"),
			// "sd": the structured data, i.e. the metadata itself
			Map.entry("sdDatePublished", "Structured data date published"),
			Map.entry("sdLicense", "Structured data license"),
			Map.entry("sdPublisher", "Structured data publisher"),
			Map.entry("sha256", "SHA-256"),
			Map.entry("xpath", "XPath"));

	private Labels() {
	}

	/**
	 * A name from a camelCase term: <code>datePublished</code> becomes "Date
	 * published", <code>url</code> "URL", <code>numTracks</code> "Number of
	 * tracks". Terms starting with <code>@</code> stay as they are. An IRI
	 * is named by its local name, after its last <code>#</code> or
	 * <code>/</code>, with <code>_</code> between words:
	 * <code>https://w3id.org/ro-id/rohub/model#has_version</code> is "Has
	 * version".
	 */
	public static String fromTerm(String term) {
		if (term.isEmpty() || term.startsWith("@")) {
			return term;
		}
		int colon = term.indexOf(':');
		if (colon > 0 && term.startsWith("//", colon + 1)) {
			String local = term.substring(Math.max(term.lastIndexOf('#'), term.lastIndexOf('/')) + 1);
			return local.isBlank() ? term : fromTerm(local.replace('_', ' ').strip());
		}
		if (NAMES.containsKey(term)) {
			return NAMES.get(term);
		}
		// Split before capitals and digits that follow a lower case letter
		List<String> words = new ArrayList<>();
		int start = 0;
		for (int i = 1; i < term.length(); i++) {
			char previous = term.charAt(i - 1);
			char c = term.charAt(i);
			boolean split = Character.isUpperCase(c) && !Character.isUpperCase(previous)
					|| Character.isDigit(c) && !Character.isDigit(previous)
					// The last capital of a run starts a word: "URLTemplate"
					|| Character.isUpperCase(c) && Character.isUpperCase(previous) && i + 1 < term.length()
							&& Character.isLowerCase(term.charAt(i + 1));
			if (split) {
				words.add(term.substring(start, i));
				start = i;
			}
		}
		words.add(term.substring(start));
		StringBuilder name = new StringBuilder();
		for (String word : words) {
			if (!name.isEmpty()) {
				name.append(' ');
			}
			String lower = word.toLowerCase(Locale.ROOT);
			if (name.isEmpty() && lower.equals("num") && words.size() > 1) {
				name.append("number of");
			} else if (lower.equals("on") && (name.toString().equals("add ") || name.toString().endsWith(" add "))) {
				// "Software add-on"
				name.setCharAt(name.length() - 1, '-');
				name.append(lower);
			} else if (ACRONYMS.contains(lower) || word.length() > 1 && word.equals(word.toUpperCase(Locale.ROOT))) {
				name.append(word.toUpperCase(Locale.ROOT));
			} else {
				name.append(lower);
			}
		}
		name.setCharAt(0, Character.toUpperCase(name.charAt(0)));
		return name.toString();
	}

	private static final Pattern TERM_LINK = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");
	private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]*\\)");
	private static final Pattern BREAK = Pattern.compile("<br\\s*/?>|</?p>|</?ul>", Pattern.CASE_INSENSITIVE);
	private static final Pattern ITEM = Pattern.compile("<li>", Pattern.CASE_INSENSITIVE);
	private static final Pattern TAG = Pattern.compile("</?[a-zA-Z][^>]*>");

	/**
	 * A description as plain text, without the markup of schema.org's
	 * comments: <code>[[CreativeWork]]</code> becomes "CreativeWork",
	 * Markdown and HTML links their text, and HTML line breaks, paragraphs
	 * and list items new lines.
	 */
	public static String plainText(String comment) {
		String text = TERM_LINK.matcher(comment).replaceAll("$1");
		text = MARKDOWN_LINK.matcher(text).replaceAll("$1");
		text = BREAK.matcher(text).replaceAll("\n");
		text = ITEM.matcher(text).replaceAll("\n• ");
		text = TAG.matcher(text).replaceAll("");
		text = text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
		return text.replaceAll("[ \t]*\n[ \t]*", "\n").replaceAll("\n{3,}", "\n\n").strip();
	}
}
