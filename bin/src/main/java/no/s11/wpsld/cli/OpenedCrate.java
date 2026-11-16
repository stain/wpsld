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
package no.s11.wpsld.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateReader;

/**
 * A crate given on the command line, as a folder or its metadata file, read
 *
 * @param metadata The metadata file
 * @param folder   The folder of an attached crate (its metadata file named
 *                 ro-crate-metadata.json, or RO-Crate 1.0's .jsonld); none
 *                 for a detached one
 * @param content  The file as it was read
 * @param document The crate as read, with any problems
 */
record OpenedCrate(Path metadata, Optional<Path> folder, byte[] content, CrateDocument document) {
	/** RO-Crate 1.0's name for the metadata file */
	static final String RO_CRATE_METADATA_JSONLD = "ro-crate-metadata.jsonld";

	static OpenedCrate open(Path given) throws IOException {
		Path metadata = given;
		if (Files.isDirectory(given)) {
			metadata = given.resolve(Crate.RO_CRATE_METADATA_JSON);
			if (!Files.exists(metadata) && Files.exists(given.resolve(RO_CRATE_METADATA_JSONLD))) {
				metadata = given.resolve(RO_CRATE_METADATA_JSONLD);
			}
		}
		String name = metadata.getFileName() == null ? "" : metadata.getFileName().toString();
		Optional<Path> folder = name.equals(Crate.RO_CRATE_METADATA_JSON) || name.equals(RO_CRATE_METADATA_JSONLD)
				? Optional.of(metadata.toAbsolutePath().getParent())
				: Optional.empty();
		byte[] content = Files.readAllBytes(metadata);
		return new OpenedCrate(metadata, folder, content,
				new CrateReader().read(new java.io.ByteArrayInputStream(content), metadata.toString()));
	}
}
