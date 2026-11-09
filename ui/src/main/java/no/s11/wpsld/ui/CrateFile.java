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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The RO-Crate Metadata File being edited: where it is, once opened or
 * saved, and the crate as it was then, to tell whether it has unsaved
 * changes; the file as it was on disk then, to tell whether another
 * application changed it since; and the window title for it.
 */
final class CrateFile {
	static final String UNTITLED = "Untitled";

	/** Whether the file on disk is as it was opened or last saved */
	enum Disk {
		SAME, CHANGED, MISSING
	}

	/**
	 * The file on disk as it was opened or saved: its modification time and
	 * size, to notice a change cheaply, and its content's SHA-256, to tell
	 * whether a changed time (e.g. git checking out the same content again)
	 * really is a change.
	 */
	private record Stamp(FileTime modified, long size, String sha256) {
	}

	private Optional<Path> path = Optional.empty();
	private JsonNode saved;
	private Optional<Stamp> stamp = Optional.empty();
	/** The line ends to write: as the file had them, else \n */
	private String lineEnd = "\n";
	/** The SHA-256 of a changed file the user chose to keep their changes over */
	private Optional<String> keptOver = Optional.empty();

	/**
	 * @param saved A new crate, not saved anywhere yet
	 */
	CrateFile(JsonNode saved) {
		this.saved = saved;
	}

	/**
	 * A crate opened from, or saved to, a file, remembering the file as it is
	 * on disk now; or a new one (no path).
	 */
	void reset(Optional<Path> path, JsonNode saved) {
		this.path = path;
		this.saved = saved;
		this.stamp = path.flatMap(CrateFile::stamp);
		this.keptOver = Optional.empty();
		// Kept for Save As too; a new crate gets \n
		if (path.isEmpty()) {
			lineEnd = "\n";
		} else {
			try {
				lineEnd = lineEnd(Files.readAllBytes(path.get()));
			} catch (IOException e) {
				// As before
			}
		}
	}

	/**
	 * The line ends to save with: those of the file as opened or saved (\r\n
	 * only if every line has them, as Vim tells), else \n, on any platform.
	 */
	String lineEnd() {
		return lineEnd;
	}

	static String lineEnd(byte[] content) {
		boolean any = false;
		for (int i = 0; i < content.length; i++) {
			if (content[i] == '\n') {
				if (i == 0 || content[i - 1] != '\r') {
					return "\n";
				}
				any = true;
			}
		}
		return any ? "\r\n" : "\n";
	}

	/**
	 * Whether the file on disk is as it was opened or last saved; SAME for a
	 * new crate. Reads the file only if its time or size differ.
	 */
	Disk disk() {
		if (path.isEmpty() || stamp.isEmpty()) {
			return Disk.SAME;
		}
		BasicFileAttributes now;
		try {
			now = Files.readAttributes(path.get(), BasicFileAttributes.class);
		} catch (IOException e) {
			return Disk.MISSING;
		}
		Stamp before = stamp.get();
		if (now.lastModifiedTime().equals(before.modified()) && now.size() == before.size()) {
			return Disk.SAME;
		}
		Optional<Stamp> current = stamp(path.get());
		if (current.isEmpty()) {
			return Disk.MISSING;
		}
		if (current.get().sha256().equals(before.sha256())) {
			// Touched, not changed: no need to read it again next time
			stamp = current;
			return Disk.SAME;
		}
		return Disk.CHANGED;
	}

	/**
	 * The user keeps their changes over the file as it is on disk now, so
	 * that they are not asked again until it changes again (or they save).
	 */
	void keepChangesOverDisk() {
		keptOver = path.flatMap(CrateFile::stamp).map(Stamp::sha256);
	}

	/**
	 * Whether the user chose to keep their changes over the file as it is
	 * now (see {@link #keepChangesOverDisk()}).
	 */
	boolean keptChangesOverDisk() {
		return keptOver.isPresent() && keptOver.equals(path.flatMap(CrateFile::stamp).map(Stamp::sha256));
	}

	private static Optional<Stamp> stamp(Path file) {
		try {
			BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
			byte[] content = Files.readAllBytes(file);
			return Optional.of(new Stamp(attributes.lastModifiedTime(), attributes.size(), sha256(content)));
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		} catch (NoSuchAlgorithmException e) {
			// Every Java has SHA-256
			throw new IllegalStateException(e);
		}
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
