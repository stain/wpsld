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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Copying files into a crate's folder, e.g. for Insert Files: each written
 * in full under a hidden temporary name beside where it goes, then renamed,
 * so that a full disk or a file that can't be read never leaves half a
 * file; the others are still copied, and each failure is told. Can be
 * cancelled between blocks, removing the file being copied.
 */
public final class FileCopy {
	/** What to do with a file of the same name where one is copied */
	public enum Conflict {
		/** Copied over it */
		REPLACE,
		/** Copied beside it, as e.g. counts (2).csv */
		KEEP_BOTH,
		/** Not copied */
		SKIP
	}

	/** A file copied, from where to where */
	public record Copied(Path from, Path to) {
	}

	/** A file not copied, and why */
	public record Failed(Path from, String why) {
	}

	/** What was copied, skipped and not, and whether it was cancelled */
	public record Result(List<Copied> copied, List<Path> skipped, List<Failed> failed, boolean cancelled) {
	}

	/** Told as the copying goes: the file being copied, and bytes so far of all */
	public interface Progress {
		void update(Path file, long done, long total);
	}

	private static final int BLOCK = 1 << 16;

	private FileCopy() {
	}

	/**
	 * The files of these that would be copied over a file of the same name
	 * in this folder
	 */
	public static List<Path> conflicts(List<Path> files, Path folder) {
		return files.stream().filter(f -> Files.exists(folder.resolve(f.getFileName().toString()))).toList();
	}

	/**
	 * Copy files into a folder, as above.
	 *
	 * @param cancelled asked between blocks whether to stop
	 */
	public static Result copy(List<Path> files, Path folder, Conflict conflict, Progress progress,
			BooleanSupplier cancelled) {
		long total = files.stream().mapToLong(FileCopy::size).sum();
		long done = 0;
		List<Copied> copied = new ArrayList<>();
		List<Path> skipped = new ArrayList<>();
		List<Failed> failed = new ArrayList<>();
		byte[] buffer = new byte[BLOCK];
		for (Path file : files) {
			if (cancelled.getAsBoolean()) {
				return new Result(copied, skipped, failed, true);
			}
			Path target = folder.resolve(file.getFileName().toString());
			if (Files.exists(target)) {
				if (conflict == Conflict.SKIP) {
					skipped.add(file);
					done += size(file);
					continue;
				}
				if (conflict == Conflict.KEEP_BOTH) {
					target = free(target);
				}
			}
			Path temp = folder.resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".copying");
			try (InputStream in = Files.newInputStream(file);
					OutputStream out = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW)) {
				for (int read; (read = in.read(buffer)) > 0;) {
					if (cancelled.getAsBoolean()) {
						out.close();
						Files.deleteIfExists(temp);
						return new Result(copied, skipped, failed, true);
					}
					out.write(buffer, 0, read);
					done += read;
					progress.update(file, done, total);
				}
			} catch (IOException e) {
				failed.add(new Failed(file, why(e)));
				deleteQuietly(temp);
				continue;
			}
			try {
				Files.setLastModifiedTime(temp, Files.getLastModifiedTime(file));
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				copied.add(new Copied(file, target));
			} catch (IOException e) {
				failed.add(new Failed(file, why(e)));
				deleteQuietly(temp);
			}
		}
		return new Result(copied, skipped, failed, false);
	}

	/** A name not taken yet beside a file: counts (2).csv, counts (3).csv… */
	static Path free(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		String extension = dot > 0 ? name.substring(dot) : "";
		Path free = file;
		for (int n = 2; Files.exists(free); n++) {
			free = file.resolveSibling(base + " (" + n + ")" + extension);
		}
		return free;
	}

	private static long size(Path file) {
		try {
			return Files.size(file);
		} catch (IOException e) {
			return 0;
		}
	}

	/** Why it failed, for people: e.g. "No space left on device" */
	private static String why(IOException e) {
		String message = e.getMessage();
		if (e instanceof java.nio.file.AccessDeniedException) {
			return "Not allowed to read or write it" + (message == null ? "" : ": " + message);
		}
		if (e instanceof java.nio.file.NoSuchFileException) {
			return "Not found" + (message == null ? "" : ": " + message);
		}
		return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
	}

	private static void deleteQuietly(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			// Left: hidden, and named as being copied
		}
	}
}
