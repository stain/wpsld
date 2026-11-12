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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
				for (byte b : new String(Character.toChars(c)).getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
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
