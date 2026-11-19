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

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * Where a crate fetched from the web is, and what its relative
 * <code>@id</code>s mean there, by RO-Crate 1.2's "Attached RO-Crate
 * Package" and "Detached RO-Crate Package":
 * <ul>
 * <li>a metadata document named <code>ro-crate-metadata.json</code> (or
 * RO-Crate 1.0's <code>.jsonld</code>), or a folder's address, is an
 * <em>attached</em> crate "exposed as a Web resource within a folder
 * structure": its RO-Crate Root is the folder, and relative
 * <code>@id</code>s are the paths of files in it, so
 * <code>http://example.com/crate1/ro-crate-metadata.json</code> with a part
 * <code>file.txt</code> has it at
 * <code>http://example.com/crate1/file.txt</code>;
 * <li>other names (<code>my-ro-crate-metadata.json</code>), and addresses
 * with a query, such as APIs, are <em>detached</em>: they have no root
 * folder, so their data entities should have absolute <code>@id</code>s.
 * Relative ones are still resolved against the document's address, as JSON-LD
 * does, to lead somewhere.
 * </ul>
 * The address is where the document came from in the end, after redirects
 * (see {@link CrateRetriever.Retrieved#source()}), not the one asked for,
 * which may be a DOI. An <code>@base</code> in the document's own
 * <code>@context</code> takes the place of the address for resolving, as in
 * JSON-LD. The crate itself is not changed: its <code>@id</code>s stay as
 * written.
 *
 * @param source   Where the metadata document came from
 * @param base     What relative <code>@id</code>s are resolved against: for
 *                 an attached crate its folder, ending with <code>/</code>
 * @param attached Whether it is an attached crate, see above
 * @param reason   Why, to show, e.g. "named ro-crate-metadata.json at
 *                 http://example.com/crate1/"
 */
public record WebCrate(URI source, URI base, boolean attached, String reason) {
	/**
	 * Decide what a crate fetched from an <code>http:</code> or
	 * <code>https:</code> address is.
	 *
	 * @param source      Where its metadata document came from in the end
	 * @param contextBase The <code>@base</code> of its context, if it has one
	 * @return None for other addresses, e.g. a file
	 */
	public static Optional<WebCrate> of(URI source, Optional<String> contextBase) {
		String scheme = source.getScheme() == null ? "" : source.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https")) {
			return Optional.empty();
		}
		String path = source.getRawPath() == null ? "" : source.getRawPath();
		String name = path.substring(path.lastIndexOf('/') + 1);
		boolean noQuery = source.getRawQuery() == null;
		URI folder = source.resolve("./");
		boolean attached;
		String reason;
		if (!noQuery) {
			attached = false;
			reason = "an address with a query, as a service gives it, so without a folder";
		} else if (name.equals(Crate.RO_CRATE_METADATA_JSON) || name.equals("ro-crate-metadata.jsonld")) {
			attached = true;
			reason = "named " + name + ", in the folder " + folder;
		} else if (name.isEmpty()) {
			attached = true;
			reason = "a folder, " + folder;
		} else {
			attached = false;
			reason = "named " + name + ", not ro-crate-metadata.json, so without a folder";
		}
		URI base = attached ? folder : source;
		Optional<URI> declared = contextBase.flatMap(b -> {
			try {
				return Optional.of(source.resolve(b));
			} catch (IllegalArgumentException e) {
				return Optional.empty();
			}
		});
		if (declared.isPresent()) {
			base = declared.get();
			reason += "; the context's @base " + base + " is used to resolve identifiers";
		}
		return Optional.of(new WebCrate(source, base, attached, reason));
	}

	/**
	 * Where a relative <code>@id</code> leads on the web: none for a
	 * <code>#local</code> or <code>_:blank</code> one, for an absolute one
	 * (it is its own address), and, for an attached crate, for one that is
	 * outside its folder, such as <code>../other</code>, which an attached
	 * crate can't have.
	 */
	public Optional<URI> resolve(String id) {
		if (id.isEmpty() || id.startsWith("#") || id.startsWith("_:")) {
			return Optional.empty();
		}
		try {
			URI reference = new URI(id);
			if (reference.isAbsolute()) {
				return Optional.empty();
			}
			URI resolved = base.resolve(reference).normalize();
			if (attached && !resolved.toString().startsWith(base.toString())) {
				return Optional.empty();
			}
			return Optional.of(resolved);
		} catch (java.net.URISyntaxException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
