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
import java.net.URI;
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
import no.s11.wpsld.crate.WebCrate;

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
	 * A folder the user attached the crate to (Crate > Attach…), other than the
	 * one its file is in: its relative <code>@id</code>s are paths in it
	 */
	private Optional<Path> attachedTo = Optional.empty();
	/**
	 * Where the crate was fetched from on the web, and what its relative
	 * <code>@id</code>s mean there, until it is saved to a file
	 */
	private Optional<WebCrate> web = Optional.empty();
	/** The address asked for when it was fetched, e.g. a DOI, which led to {@link WebCrate#source()} */
	private Optional<URI> openedFrom = Optional.empty();

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
		attachedTo = Optional.empty();
		web = Optional.empty();
		openedFrom = Optional.empty();
		this.path = Optional.empty();
		reset(path, saved);
	}

	/**
	 * A crate fetched from the web: in no file yet, so Save is Save As, with
	 * its relative <code>@id</code>s leading where the {@link WebCrate} says,
	 * until it is saved to a file. Its JSON is not changed.
	 *
	 * @param openedFrom The address asked for, which may lead to the crate's
	 *                   after redirects
	 */
	void openWeb(WebCrate crate, URI openedFrom, JsonNode saved) {
		open(Optional.empty(), saved);
		web = Optional.of(crate);
		this.openedFrom = Optional.of(openedFrom);
	}

	/** The crate's place on the web, if fetched from there and not saved to a file since */
	Optional<WebCrate> web() {
		return web;
	}

	/**
	 * A base found for the relative <code>@id</code>s of the crate on the web
	 * (see {@link WebCrate#withBase}), without changing the crate.
	 */
	void setWeb(WebCrate crate) {
		web = Optional.of(crate);
	}

	/** The address the crate on the web was asked for by */
	Optional<URI> openedFrom() {
		return openedFrom;
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
		if (attachedTo.isPresent()) {
			return attachedTo;
		}
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
		attachedTo = Optional.empty();
		this.attached = attached && canAttach();
	}

	/**
	 * Say what the crate's relative <code>@id</code>s are relative to: a folder
	 * of the user's choice, a web address, or nothing. The crate is not
	 * changed. Attached to a folder, a crate fetched from the web is no
	 * longer attached on the web (its files are the folder's), and attached
	 * to a web address, a crate is not to a folder. Detached, a crate fetched
	 * from the web goes on being one, with no base for its relative
	 * <code>@id</code>s.
	 */
	void attach(Attachment attachment) {
		if (attachment.folder().isPresent()) {
			attachedTo = attachment.folder();
			attached = true;
			web = Optional.empty();
		} else if (attachment.web().isPresent()) {
			URI address = attachment.web().get();
			attachedTo = Optional.empty();
			attached = false;
			web = Optional.of(web.map(w -> w.withBase(address, "attached to " + address + " by the user"))
					.orElseGet(() -> WebCrate.attachedTo(address)));
		} else {
			attachedTo = Optional.empty();
			attached = false;
			web = web.map(WebCrate::withoutBase);
		}
	}

	/** What the crate is attached to now, as {@link #attach} takes it */
	Attachment attachment() {
		if (folder().isPresent()) {
			return Attachment.toFolder(folder().get());
		}
		return web.flatMap(WebCrate::base).map(Attachment::toWeb).orElse(Attachment.none());
	}

	/**
	 * Where Save writes without asking: the file opened or saved, or a new
	 * crate's folder's <code>ro-crate-metadata.json</code>.
	 */
	Optional<Path> saveTarget() {
		return path.or(() -> newIn.or(() -> attachedTo).map(folder -> folder.resolve(Crate.RO_CRATE_METADATA_JSON)));
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
		// Saved to a file, it is no longer the one on the web
		if (path.isPresent() && !path.equals(this.path)) {
			web = Optional.empty();
			openedFrom = Optional.empty();
		}
		// Attached by its name when first in a file, or in another; else as it was
		if (path.isPresent() && !path.equals(this.path)) {
			attached = isMetadataFile(path.get());
			attachedTo = Optional.empty();
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
		return Crate.lineEnd(content);
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
		if (web.isPresent() && saveTarget().isEmpty()) {
			return webName(web.get().source());
		}
		return saveTarget().map(p -> p.getParent() == null || p.getParent().getFileName() == null ? p.getFileName()
				: p.getParent().getFileName().resolve(p.getFileName())).map(Path::toString).orElse(UNTITLED);
	}

	/** The folder and name of a crate's address, as for a file: <code>crate1/ro-crate-metadata.json</code> */
	private static String webName(URI source) {
		String[] segments = Optional.ofNullable(source.getPath()).orElse("").split("/");
		String name = segments.length > 0 ? segments[segments.length - 1] : "";
		if (name.isEmpty()) {
			return Optional.ofNullable(source.getHost()).orElse(source.toString());
		}
		String query = source.getRawQuery() == null ? "" : "?" + source.getRawQuery();
		return (segments.length > 1 && !segments[segments.length - 2].isEmpty()
				? segments[segments.length - 2] + "/" : "") + name + query;
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
