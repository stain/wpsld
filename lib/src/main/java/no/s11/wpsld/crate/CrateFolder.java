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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.node.TextNode;

/**
 * The files and folders of an attached crate's folder, as relative
 * <code>@id</code>s, to tell those the crate doesn't describe yet; and what
 * a file on disk tells about its entity.
 */
public final class CrateFolder {
	/** The crate's own files, not its parts */
	private static final Set<String> OWN = Set.of(Crate.RO_CRATE_METADATA_JSON, "ro-crate-metadata.jsonld",
			"ro-crate-preview.html", "ro-crate-preview_files");

	private CrateFolder() {
	}

	/**
	 * The files and folders in a folder, as relative <code>@id</code>s, e.g.
	 * <code>data/</code> and <code>data/my%20counts.csv</code>, percent-encoded
	 * where a URI must be; not the crate's metadata and preview files, nor
	 * hidden ones (<code>.git</code>), nor through symbolic links to folders;
	 * at most this many, in no particular order.
	 */
	public static List<String> list(Path folder, int limit) {
		List<String> ids = new ArrayList<>();
		try {
			Files.walkFileTree(folder, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (dir.equals(folder)) {
						return FileVisitResult.CONTINUE;
					}
					if (skipped(dir)) {
						return FileVisitResult.SKIP_SUBTREE;
					}
					ids.add(id(folder.relativize(dir)) + "/");
					return ids.size() >= limit ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (!skipped(file) && !attrs.isDirectory()) {
						ids.add(id(folder.relativize(file)));
					}
					return ids.size() >= limit ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException e) {
					// e.g. not allowed to read it
					return FileVisitResult.CONTINUE;
				}

				private boolean skipped(Path path) {
					String name = path.getFileName().toString();
					return name.startsWith(".") || folder.relativize(path).getNameCount() == 1 && OWN.contains(name);
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return ids;
	}

	/**
	 * How a crate's files and a folder's match: the crate's parts with
	 * relative paths (not parts of files, e.g. page.html#table2) that are in
	 * the folder, those that are not, and the folder's files that no entity
	 * describes.
	 */
	public record Match(List<String> present, List<String> absent, List<String> undescribed) {
		/** Whether the crate's files seem to be elsewhere: it has some, and none is here */
		public boolean elsewhere() {
			return present.isEmpty() && !absent.isEmpty();
		}
	}

	/**
	 * How a crate's files match a folder's (see {@link Match}), listing at
	 * most this many of the folder's files.
	 */
	public static Match match(Crate crate, Path folder, int limit) {
		List<String> present = new ArrayList<>();
		List<String> absent = new ArrayList<>();
		for (Entity part : crate.dataEntities()) {
			String id = part.getId();
			if (!isRelativePath(id)) {
				continue;
			}
			(exists(folder, id) ? present : absent).add(id);
		}
		Set<String> described = new HashSet<>();
		crate.getEntities().stream().map(Entity::getId).filter(CrateFolder::isRelativePath)
				.map(CrateFolder::normalise).forEach(described::add);
		List<String> undescribed = Files.isDirectory(folder) ? list(folder, limit).stream()
				.filter(id -> !id.endsWith("/") && !described.contains(normalise(id))).toList() : List.of();
		return new Match(present, absent, undescribed);
	}

	/**
	 * What is wrong with a crate attached to this folder, to tell before
	 * saving: a root data entity whose @id is relative, but not ./
	 * (RO-Crate allows ./ or a URI); relative paths out of the folder
	 * (../); folders' @ids not ending with /; and files and folders it
	 * describes that are not in the folder (for a part of a file, e.g.
	 * page.html#t2, the file).
	 */
	public static List<String> problems(Crate crate, Path folder) {
		List<String> problems = new ArrayList<>();
		String root = crate.getRoot().getId();
		if (!root.equals("./") && !Crate.isAbsolute(root)) {
			problems.add("The root data entity's @id must be ./ or an absolute URI, not " + root);
		}
		List<String> outside = new ArrayList<>();
		List<String> noSlash = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		List<String> otherCase = new ArrayList<>();
		for (Entity entity : crate.getEntities()) {
			String id = entity.getId();
			String file = id.contains("#") ? id.substring(0, id.indexOf('#')) : id;
			if (entity == crate.getRoot() || file.isEmpty() || !isRelativePath(file)) {
				continue;
			}
			if (isOutside(file)) {
				outside.add(id);
				continue;
			}
			if (!id.contains("#") && entity.getTypes().contains("Dataset") && !id.endsWith("/")) {
				noSlash.add(id);
			}
			Optional<String> problem = problem(folder, file);
			if (problem.isPresent()) {
				(problem.get().startsWith(NAMED) ? otherCase : missing).add(id);
			}
		}
		if (!outside.isEmpty()) {
			problems.add(outside.size() + (outside.size() == 1 ? " entity is" : " entities are")
					+ " outside the crate's folder, which an attached crate can't have: " + examples(outside));
		}
		if (!noSlash.isEmpty()) {
			problems.add(noSlash.size() + (noSlash.size() == 1 ? " Dataset's @id" : " Datasets' @ids")
					+ " should end with / as a folder's: " + examples(noSlash));
		}
		if (!otherCase.isEmpty()) {
			problems.add(otherCase.size() + (otherCase.size() == 1 ? " file or folder is" : " files or folders are")
					+ " named with other upper and lower case in the crate's folder, which other computers tell apart: "
					+ examples(otherCase));
		}
		if (!missing.isEmpty()) {
			problems.add(missing.size() + (missing.size() == 1 ? " file or folder is" : " files or folders are")
					+ " not in the crate's folder: " + examples(missing));
		}
		return problems;
	}

	/** Whether a relative path leads out of the crate's folder, e.g. ../other/data.csv */
	static boolean isOutside(String id) {
		try {
			return Path.of(normalise(id)).normalize().startsWith("..");
		} catch (InvalidPathException e) {
			return false;
		}
	}

	private static String examples(List<String> ids) {
		return String.join(", ", ids.stream().limit(5).toList()) + (ids.size() > 5 ? ", …" : "");
	}

	/** A path relative to the crate: not absolute, #local, _:blank, ./ nor a part of a file */
	private static boolean isRelativePath(String id) {
		return !id.isEmpty() && !Crate.isAbsolute(id) && !Crate.isLocal(id) && !Crate.isBlankNode(id)
				&& !id.equals("./") && !id.contains("#");
	}

	/** A path decoded, without ./ and a folder's last / */
	private static String normalise(String id) {
		String path = decode(id);
		while (path.startsWith("./")) {
			path = path.substring(2);
		}
		return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
	}

	/** Percent-decoded as UTF-8 (+ stays +), or as it is if it can't be */
	public static String decode(String id) {
		try {
			return URLDecoder.decode(id.replace("+", "%2B"), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return id;
		}
	}

	private static final String NAMED = "Named ";

	/**
	 * What is wrong with a relative <code>@id</code> as a file or folder in
	 * a folder, if anything: not there; or, where names are found ignoring
	 * upper and lower case (Windows, macOS), there with other case, which
	 * other computers (Linux, web servers) would not find
	 */
	public static Optional<String> problem(Path folder, String id) {
		if (!exists(folder, id)) {
			return Optional.of("Not found in the crate's folder: " + decode(id));
		}
		try {
			String written = normalise(id);
			Path real = folder.toRealPath().relativize(folder.resolve(written).toRealPath());
			String onDisk = String.join("/", java.util.stream.StreamSupport.stream(real.spliterator(), false)
					.map(Path::toString).toList());
			if (!onDisk.equals(written) && onDisk.equalsIgnoreCase(written)) {
				return Optional.of(NAMED + onDisk + " in the crate's folder: its upper and lower case differ from the"
						+ " @id's, which other computers tell apart");
			}
		} catch (IOException | IllegalArgumentException e) {
			// e.g. through a link out of the folder: there, as far as it goes
		}
		return Optional.empty();
	}

	/** Whether a relative <code>@id</code> is a file or folder in a folder */
	public static boolean exists(Path folder, String id) {
		try {
			return !isOutside(id) && Files.exists(folder.resolve(normalise(id)));
		} catch (InvalidPathException e) {
			return false;
		}
	}

	/** A relative path as an <code>@id</code>, each name percent-encoded */
	private static String id(Path relative) {
		List<String> names = new ArrayList<>();
		relative.forEach(name -> names.add(encode(name.toString())));
		return String.join("/", names);
	}

	/**
	 * A name in a path, percent-encoded where a relative URI must be: spaces,
	 * <code>%</code>, <code>#</code>, <code>?</code> and other characters not
	 * allowed in a URI; letters of other scripts stay, as IRIs allow them.
	 */
	public static String encode(String name) {
		StringBuilder encoded = new StringBuilder();
		name.codePoints().forEach(c -> {
			if (c < 0x21 || c == 0x7f || " %#?[]\"<>\\^`{|}".indexOf(c) >= 0) {
				for (byte b : new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8)) {
					encoded.append(String.format("%%%02X", b & 0xff));
				}
			} else {
				encoded.appendCodePoint(c);
			}
		});
		return encoded.toString();
	}

	/**
	 * Describe a file's entity by the file on disk, where it says nothing
	 * yet: its <code>contentSize</code> in bytes and its
	 * <code>dateModified</code>, to the second, in UTC.
	 */
	public static void describe(Entity entity, Path file) {
		try {
			BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
			if (!attrs.isDirectory() && entity.get("contentSize").isEmpty()) {
				entity.set("contentSize", Optional.of(TextNode.valueOf(Long.toString(attrs.size()))));
			}
			if (entity.get("dateModified").isEmpty()) {
				String modified = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
						attrs.lastModifiedTime().toInstant().truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC));
				entity.set("dateModified", Optional.of(TextNode.valueOf(modified)));
			}
		} catch (IOException e) {
			// Gone, or not readable: nothing to tell
		}
	}
}
