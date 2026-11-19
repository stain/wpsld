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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

import no.s11.wpsld.util.UserAgent;

/**
 * The header values wpsld sends are as RFC 9110 (HTTP Semantics) has them:
 * <code>User-Agent</code> (section 10.1.5) and <code>Accept</code> (12.5.1),
 * with their grammar written out here, with strings that must not match, so that
 * the check is not one that anything passes.
 */
public class TestHttpHeaders {
	/** token = 1*tchar, section 5.6.2 */
	private static final String TOKEN = "[!#$%&'*+\\-.^_`|~0-9A-Za-z]+";
	/** product = token [ "/" product-version ], section 10.1.5 (a version is a token) */
	private static final String PRODUCT = TOKEN + "(?:/" + TOKEN + ")?";
	/** comment = "(" *( ctext / quoted-pair / comment ) ")", section 5.6.5, without nesting */
	private static final String COMMENT = "\\((?:[\\t \\x21-\\x27\\x2A-\\x5B\\x5D-\\x7E\\x80-\\xFF]|\\\\[\\t \\x21-\\x7E\\x80-\\xFF])*\\)";
	/** User-Agent = product *( RWS ( product / comment ) ) */
	private static final Pattern USER_AGENT = Pattern.compile(PRODUCT + "(?:[ \\t]+(?:" + PRODUCT + "|" + COMMENT + "))*");

	@Test
	public void userAgentIsAProductAndAComment() {
		String agent = UserAgent.get();
		assertTrue(agent, USER_AGENT.matcher(agent).matches());
		// What sites read: wpsld, then its version, then where it is
		assertTrue(agent, agent.matches("wpsld/\\S+ \\(https://github\\.com/stain/wpsld\\)"));
		// As a field value: no leading or trailing whitespace, no control characters
		assertEquals(agent, agent.strip());
		assertFalse(agent, agent.chars().anyMatch(c -> c < 0x20 && c != '\t' || c == 0x7f));
		// Not strings the grammar allows
		for (String bad : List.of("wpsld/ (x)", "/1.0", "wpsld (x", "wpsld/1.0 (a\\)", "wpsld/1.0 (a(b))x", "wpsld/1.0\nX: y", "wps ld/1/2", "")) {
			assertFalse(bad, USER_AGENT.matcher(bad).matches());
		}
		for (String good : List.of("wpsld/0.8.0-SNAPSHOT (https://github.com/stain/wpsld)", "wpsld/development (a; b, c)", "wpsld", "a/1 b/2 (c)")) {
			assertTrue(good, USER_AGENT.matcher(good).matches());
		}
	}

	/** media-range [ weight ], section 12.5.1, as a list of members split at commas outside quotes */
	private static final Pattern RANGE = Pattern.compile(
			"(?:\\*/\\*|" + TOKEN + "/\\*|" + TOKEN + "/" + TOKEN + ")"
					+ "(?:[ \\t]*;[ \\t]*(?!q=)" + TOKEN + "=(?:" + TOKEN + "|\"(?:[^\"\\\\]|\\\\.)*\"))*"
					+ "(?:[ \\t]*;[ \\t]*[qQ]=(?:0(?:\\.[0-9]{0,3})?|1(?:\\.0{0,3})?))?");

	private static List<String> members(String accept) {
		List<String> members = new ArrayList<>();
		StringBuilder member = new StringBuilder();
		boolean quoted = false;
		for (char c : accept.toCharArray()) {
			if (c == '"') {
				quoted = !quoted;
			}
			if (c == ',' && !quoted) {
				members.add(member.toString().strip());
				member.setLength(0);
			} else {
				member.append(c);
			}
		}
		members.add(member.toString().strip());
		return members;
	}

	private static void assertAccept(String accept) {
		for (String member : members(accept)) {
			assertTrue(member + " of " + accept, RANGE.matcher(member).matches());
		}
	}

	@Test
	public void acceptHeadersAreMediaRangesWithWeights() {
		assertAccept(CrateRetriever.ACCEPT);
		assertAccept(CrateRetriever.ACCEPT_ANY);
		assertAccept(Linkset.ACCEPT);
		// Those the lookups and registries send
		assertAccept("application/json");
		assertAccept("application/ld+json");
		// The RO-Crate profile is a parameter of the media type, in a quoted string, before any weight
		assertAccept("application/ld+json;profile=\"https://w3id.org/ro/crate\", application/ld+json;q=0.9");
		// Not ranges
		for (String bad : List.of("application/json;q=1.5", "application/json;q=0.1234", "json", "application/json;;q=1",
				"application/json; profile", "text/html; q=0.5; q=0.6x")) {
			assertFalse(bad, members(bad).stream().allMatch(m -> RANGE.matcher(m).matches()));
		}
	}
}
