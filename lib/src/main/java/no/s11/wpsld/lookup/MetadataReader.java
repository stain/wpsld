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
package no.s11.wpsld.lookup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import no.s11.wpsld.lookup.EmbeddedMetadata.Found;

/**
 * Reads the metadata some kinds of files carry in them (see
 * {@link EmbeddedMetadata}): a service, so that readers with their own
 * libraries are modules of their own (e.g. no.s11.wpsld.readers.image),
 * found with {@link java.util.ServiceLoader} and declared with
 * <code>provides no.s11.wpsld.lookup.MetadataReader with …</code> in their
 * module-info and in <code>META-INF/services</code> (for the class path).
 * {@link FileMetadata} runs each on a thread of its own with a time limit,
 * and reports what it throws, as files are untrusted and libraries may
 * fail on them in any way.
 */
public interface MetadataReader {
	/** How many of a file's first bytes {@link #reads} is given */
	int HEAD = 1024;

	/** What it reads, to say which failed, e.g. "PDF (PDFBox)" */
	String name();

	/**
	 * Whether it reads this file, by its first bytes (at most {@link #HEAD},
	 * fewer if the file is shorter) and its name; quickly, without reading
	 * the file
	 */
	boolean reads(Path file, byte[] head);

	/**
	 * What the file says about itself, in the order found; none if it says
	 * nothing it reads
	 *
	 * @throws IOException If the file can't be read, or is broken
	 */
	List<Found> read(Path file) throws IOException;
}
