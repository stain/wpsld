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
package no.s11.wpsld.ui;

import java.nio.file.Path;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The RO-Crate Metadata File being edited: where it is, once opened or
 * saved, and the crate as it was then, to tell whether it has unsaved
 * changes; and the window title for it.
 */
final class CrateFile {
	static final String UNTITLED = "Untitled";

	private Optional<Path> path = Optional.empty();
	private JsonNode saved;

	/**
	 * @param saved A new crate, not saved anywhere yet
	 */
	CrateFile(JsonNode saved) {
		this.saved = saved;
	}

	/**
	 * A crate opened from, or saved to, a file; or a new one (no path).
	 */
	void reset(Optional<Path> path, JsonNode saved) {
		this.path = path;
		this.saved = saved;
	}

	Optional<Path> path() {
		return path;
	}

	boolean isModified(JsonNode crate) {
		return !crate.equals(saved);
	}

	/**
	 * The file's name for people: its folder and name, e.g.
	 * <code>my-crate/ro-crate-metadata.json</code>, as most crates' files have
	 * the same name; "Untitled" for a new crate.
	 */
	String name() {
		return path.map(p -> p.getParent() == null || p.getParent().getFileName() == null ? p.getFileName()
				: p.getParent().getFileName().resolve(p.getFileName())).map(Path::toString).orElse(UNTITLED);
	}

	/**
	 * The window's title, as the operating system has it: on macOS the
	 * document's name, followed by "Edited" while it has unsaved changes;
	 * elsewhere the name and the application's, with a * in front while
	 * unsaved, as in Windows' own applications.
	 */
	String title(JsonNode crate, boolean mac) {
		boolean modified = isModified(crate);
		if (mac) {
			return name() + (modified ? " — Edited" : "");
		}
		return (modified ? "*" : "") + name() + " – wpsld";
	}

	/**
	 * Whether wpsld runs on macOS, where some conventions differ.
	 */
	static boolean onMac() {
		return System.getProperty("os.name", "").startsWith("Mac");
	}
}
