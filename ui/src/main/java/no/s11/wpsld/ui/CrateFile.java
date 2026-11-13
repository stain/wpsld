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
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import no.s11.wpsld.crate.Crate;

/**
 * The RO-Crate Metadata File being edited: where it is, once opened or
 * saved, and the crate as it was then, to tell whether it has unsaved
 * changes; the file as it was on disk then, to tell whether another
 * application changed it since; and the window title for it.
 * <p>
 * An attached crate is the folder its metadata file is in: the file is
 * named <code>ro-crate-metadata.json</code> (<code>.jsonld</code> in
 * RO-Crate 1.0), and the relative <code>@id</code>s of its entities are
 * paths in that folder. A file of another name is a detached crate, with
 * no folder: its relative <code>@id</code>s name no files. A crate opened
 * from a folder without a metadata file is attached to it before it is
 * saved there.
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
	/** The crate's base, to tell entities copied from it, see {@link #base()} */
	private String base = newBase();
	/** The folder of a new crate, opened from a folder, to save it in */
	private Optional<Path> newIn = Optional.empty();
	/** Whether the crate is its folder's, see {@link #folder()} */
	private boolean attached;

	/**
	 * @param saved A new crate, not saved anywhere yet
	 */
	CrateFile(JsonNode saved) {
		this.saved = saved;
	}

	/**
	 * Another crate, opened from a file or a new one (no path), remembering
	 * the file as it is on disk now: with a new {@link #base()}.
	 */
	void open(Optional<Path> path, JsonNode saved) {
		base = newBase();
		newIn = Optional.empty();
		attached = false;
		this.path = Optional.empty();
		reset(path, saved);
	}

	/**
	 * A new crate of a folder without a metadata file: attached to it, to be
	 * saved in it as <code>ro-crate-metadata.json</code>.
	 */
	void openIn(Path folder, JsonNode saved) {
		open(Optional.empty(), saved);
		newIn = Optional.of(folder.toAbsolutePath());
		attached = true;
	}

	/** Whether a file is named as an attached crate's metadata file */
	static boolean isMetadataFile(Path file) {
		String name = file.getFileName() == null ? "" : file.getFileName().toString();
		return name.equals(Crate.RO_CRATE_METADATA_JSON) || name.equals(RO_CRATE_METADATA_JSONLD);
	}

	/** RO-Crate 1.0's name for the metadata file */
	static final String RO_CRATE_METADATA_JSONLD = "ro-crate-metadata.jsonld";

	/**
	 * The folder of an attached crate, that its relative <code>@id</code>s
	 * are paths in; none for a detached crate.
	 */
	Optional<Path> folder() {
		if (!attached) {
			return Optional.empty();
		}
		return path.map(p -> p.toAbsolutePath().getParent()).or(() -> newIn);
	}

	boolean isAttached() {
		return folder().isPresent();
	}

	/**
	 * Whether the crate could be attached to its folder: its file is named
	 * so, or it is a new crate of a folder
	 */
	boolean canAttach() {
		return path.filter(CrateFile::isMetadataFile).isPresent() || (path.isEmpty() && newIn.isPresent());
	}

	/**
	 * Attach the crate to its folder, or treat it as detached, e.g. a crate
	 * downloaded on its own, whose files are elsewhere.
	 */
	void setAttached(boolean attached) {
		this.attached = attached && canAttach();
	}

	/**
	 * Where Save writes without asking: the file opened or saved, or a new
	 * crate's folder's <code>ro-crate-metadata.json</code>.
	 */
	Optional<Path> saveTarget() {
		return path.or(() -> newIn.map(folder -> folder.resolve(Crate.RO_CRATE_METADATA_JSON)));
	}

	/**
	 * The crate saved to, or reloaded from, a file, remembering the file as
	 * it is on disk now. Saved to another file (Save As…), it is another
	 * crate from now on, with a new {@link #base()}; not when a new crate is
	 * saved for the first time, or reloaded.
	 */
	void reset(Optional<Path> path, JsonNode saved) {
		if (this.path.isPresent() && !this.path.equals(path)) {
			base = newBase();
		}
		// Attached by its name when first in a file, or in another; else as it was
		if (path.isPresent() && !path.equals(this.path)) {
			attached = isMetadataFile(path.get());
			newIn = Optional.empty();
		}
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
	 * An <code>arcp://uuid,…/</code> base for the crate, as RO-Crate has for
	 * crates without a location: random for each crate opened or made, kept
	 * when saving or reloading it, but not in the crate itself. Entities
	 * copied tell by it which crate they came from (see
	 * {@link EntityClipboard}), so that pasting them into the same crate
	 * knows its references are right. Another window, being another process,
	 * has another base for the same file.
	 */
	String base() {
		return base;
	}

	private static String newBase() {
		return "arcp://uuid," + UUID.randomUUID() + "/";
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
		return saveTarget().map(p -> p.getParent() == null || p.getParent().getFileName() == null ? p.getFileName()
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
