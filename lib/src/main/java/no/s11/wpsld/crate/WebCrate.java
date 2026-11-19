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
import java.util.function.BiConsumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * </ul>
 * A detached crate's relative <code>@id</code>s are <em>not</em> resolved
 * against the address of the document, though JSON-LD would: services give
 * a crate at an address that has nothing to do with where its files are, e.g.
 * KU Leuven's Dataverse gives <code>Calibration.Trial26.enf</code> in the
 * crate at
 * <code>https://rdr.kuleuven.be/api/datasets/export?exporter=rocrate_json&amp;persistentId=doi:10.48804/XES6PY</code>,
 * which is not at <code>https://rdr.kuleuven.be/api/datasets/Calibration.Trial26.enf</code>
 * (it is one of the dataset's files, with a <code>url</code> of its own).
 * Such a crate is <em>semi-detached</em> if it still has relative data
 * entities (see {@link Crate#unanchoredDataEntities}): they are most likely
 * other files of the same record, or files in an archive it has, but there
 * is no telling which, so they lead nowhere until the user says where. Only
 * an <code>@base</code> in the document's own context (the one thing that
 * says where they are) gives a base to a detached crate.
 * The address is where the document came from in the end, after redirects
 * (see {@link CrateRetriever.Retrieved#source()}), not the one asked for,
 * which may be a DOI. An <code>@base</code> in the document's own
 * <code>@context</code> takes the place of the folder, as in JSON-LD. The
 * crate itself is not changed: its <code>@id</code>s stay as written.
 *
 * @param source   Where the metadata document came from
 * @param base     What relative <code>@id</code>s are resolved against: for
 *                 an attached crate its folder, ending with <code>/</code>;
 *                 for a detached one only the context's <code>@base</code>,
 *                 else none
 * @param attached Whether it is an attached crate, see above
 * @param reason   Why, to show, e.g. "named ro-crate-metadata.json at
 *                 http://example.com/crate1/"
 */
public record WebCrate(URI source, Optional<URI> base, boolean attached, String reason) {
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
		Optional<URI> base = attached ? Optional.of(folder) : Optional.empty();
		Optional<URI> declared = contextBase.flatMap(b -> {
			try {
				return Optional.of(source.resolve(b));
			} catch (IllegalArgumentException e) {
				return Optional.empty();
			}
		});
		if (declared.isPresent()) {
			base = declared;
			reason += "; the context's @base " + declared.get() + " is used to resolve identifiers";
		}
		return Optional.of(new WebCrate(source, base, attached, reason));
	}

	/**
	 * A web address a crate's relative <code>@id</code>s could be relative to.
	 *
	 * @param address Its address, ending with <code>/</code> if it is a folder
	 * @param why     Where it comes from, to show
	 */
	public record Root(URI address, String why) {
	}

	/**
	 * What the RO-Crate Root of a crate with no folder of its own could be,
	 * for the user to choose from (nothing here chooses): the root data
	 * entity's own <code>@id</code> if it is a web address, its
	 * <code>identifier</code> and <code>cite-as</code> (the dataset as
	 * published, e.g. a DOI), and the folder beside the metadata document
	 * where a file named <code>ro-crate-metadata.json</code> would make it an
	 * attached crate, unless its address has a query, as a service's has, which
	 * is not a place for files.
	 *
	 * @param root The root data entity
	 */
	public List<Root> suggestedRoots(Entity root) {
		Map<URI, Root> found = new LinkedHashMap<>();
		BiConsumer<String, String> add = (text, why) -> {
			try {
				URI uri = new URI(text.strip());
				String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
				if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null) {
					found.putIfAbsent(uri, new Root(uri, why));
				}
			} catch (java.net.URISyntaxException e) {
				// Not an address
			}
		};
		add.accept(root.getId(), "the root data entity's own @id");
		for (String property : List.of("identifier", "cite-as")) {
			for (var value : root.getAll(property)) {
				add.accept(value.isTextual() ? value.asText() : value.path("@id").asText(""),
						"the crate's " + property + ", the dataset as published");
			}
		}
		if (source.getRawQuery() == null) {
			URI folder = source.resolve("./");
			found.putIfAbsent(folder, new Root(folder, "the folder beside the metadata document"));
		}
		return List.copyOf(found.values());
	}

	/**
	 * Whether an <code>@id</code> is a relative reference that has a base to
	 * be relative to, rather than <code>#local</code>, <code>_:blank</code> or
	 * absolute.
	 */
	public static boolean isRelative(String id) {
		// Also with spaces and other characters a URI may not have, as file names do
		return !id.isEmpty() && !id.startsWith("#") && !id.startsWith("_:") && !SCHEME.matcher(id).find();
	}

	private static final java.util.regex.Pattern SCHEME = java.util.regex.Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");

	/**
	 * Where a relative <code>@id</code> leads on the web: none if the crate
	 * has no base (see above), for a
	 * <code>#local</code> or <code>_:blank</code> one, for an absolute one
	 * (it is its own address), and, for an attached crate, for one that is
	 * outside its folder, such as <code>../other</code>, which an attached
	 * crate can't have.
	 */
	public Optional<URI> resolve(String id) {
		if (base.isEmpty() || !isRelative(id)) {
			return Optional.empty();
		}
		try {
			URI reference;
			try {
				reference = new URI(id);
			} catch (java.net.URISyntaxException e) {
				// A file name with a space, not encoded: as a path
				reference = new URI(null, null, id, null);
			}
			URI resolved = base.get().resolve(reference).normalize();
			if (attached && !resolved.toString().startsWith(base.get().toString())) {
				return Optional.empty();
			}
			return Optional.of(resolved);
		} catch (java.net.URISyntaxException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
