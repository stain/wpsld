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
import java.util.Set;

/**
 * Human readable names for properties, as neither schema.org nor the MASP
 * profiles give names other than the terms themselves.
 */
public final class Labels {
	/** Words written in capitals, e.g. in <code>isbn</code> or <code>url</code> */
	private static final Set<String> ACRONYMS = Set.of("doi", "gtin", "id", "isbn", "issn", "iso", "sku", "url");

	private Labels() {
	}

	/**
	 * A name from a camelCase term: <code>datePublished</code> becomes "Date
	 * published", <code>url</code> "URL". Terms starting with <code>@</code>
	 * stay as they are.
	 */
	public static String fromTerm(String term) {
		if (term.isEmpty() || term.startsWith("@")) {
			return term;
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
			if (ACRONYMS.contains(lower) || word.length() > 1 && word.equals(word.toUpperCase(Locale.ROOT))) {
				name.append(word.toUpperCase(Locale.ROOT));
			} else {
				name.append(lower);
			}
		}
		name.setCharAt(0, Character.toUpperCase(name.charAt(0)));
		return name.toString();
	}
}
