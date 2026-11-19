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
package no.s11.wpsld.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * What wpsld calls itself to the web servers it asks, in the
 * <code>User-Agent</code> of every request: <code>wpsld/0.8.0 (https://github.com/stain/wpsld)</code>,
 * as sites such as OpenStreetMap, GeoNames, ORCID and the repositories crates
 * are fetched from ask programs to do, so that they can tell wpsld, and which
 * version, from other clients, and reach its authors. Every request wpsld
 * makes, to look something up, to fetch a crate or a profile, to check that a
 * web address answers, sends this.
 */
public final class UserAgent {
	/** Where wpsld is, for those who see its requests */
	public static final String PROJECT = "https://github.com/stain/wpsld";
	private static final String VALUE = "wpsld/" + version() + " (" + PROJECT + ")";

	private UserAgent() {
	}

	/** The <code>User-Agent</code> to send, naming wpsld and its version */
	public static String get() {
		return VALUE;
	}

	/**
	 * The version the build wrote in version.properties; "development" when run
	 * without it having been filled in.
	 */
	static String version() {
		Properties properties = new Properties();
		try (InputStream in = UserAgent.class.getResourceAsStream("version.properties")) {
			if (in != null) {
				properties.load(in);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		String version = properties.getProperty("version", "").strip();
		return version.isEmpty() || version.contains("${") ? "development" : version;
	}
}
