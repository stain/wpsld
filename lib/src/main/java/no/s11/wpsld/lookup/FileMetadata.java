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
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import no.s11.wpsld.lookup.EmbeddedMetadata.Found;

/**
 * What files say about themselves, by all the {@link MetadataReader}s
 * there are: the built-in ones (see {@link EmbeddedMetadata#builtIn()}) and
 * those of other modules. Files are untrusted, and the libraries that read
 * them may fail on them in any way, so each reader runs on a thread of its
 * own with a time limit, and whatever it throws (also a
 * {@link LinkageError}, {@link StackOverflowError} or
 * {@link OutOfMemoryError}) is a problem to report rather than thrown; a
 * reader that takes too long is interrupted and left behind (on a daemon
 * thread). A reader left behind on two files that is still at them is
 * skipped for further files until it ends (a file it hangs on is likely
 * not the only one of its kind), so that threads stuck in a library don't
 * add up. Problems are logged, with what was thrown, to find the fault.
 * Readings without problems are remembered by the file's path,
 * size and date modified, so that a file is not read twice. Not to be
 * called on the JavaFX thread: a reading may take up to the time limit.
 */
public final class FileMetadata {
	private static final System.Logger LOGGER = System.getLogger(FileMetadata.class.getName());
	/** How long a reader may take on a file, by default */
	public static final Duration LIMIT = Duration.ofSeconds(20);
	/** How many readings to remember */
	private static final int REMEMBERED = 1000;
	/** How many threads of a reader may be left behind, still running, before it is skipped */
	static final int STUCK = 2;

	/**
	 * What a file says about itself, by all readers, each value once
	 *
	 * @param found    The values, in the order of the readers
	 * @param problems What went wrong, e.g. "PDF (PDFBox)
	 *                 took over 20 s"; none if all went well
	 */
	public record Reading(List<Found> found, List<String> problems) {
		public Reading {
			found = List.copyOf(found);
			problems = List.copyOf(problems);
		}
	}

	private record Key(Path file, long size, FileTime modified) {
	}

	private static FileMetadata installed;

	private final List<MetadataReader> readers;
	private final Duration limit;
	/** Each reader's threads left behind, as it took too long */
	private final Map<MetadataReader, Set<Thread>> stuck = new java.util.concurrent.ConcurrentHashMap<>();
	private final Map<Key, Reading> remembered = Collections
			.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Key, Reading> eldest) {
					return size() > REMEMBERED;
				}
			});

	/**
	 * @param readers In the order to ask them, their values first
	 * @param limit   How long each may take on a file
	 */
	public FileMetadata(List<MetadataReader> readers, Duration limit) {
		this.readers = List.copyOf(readers);
		this.limit = limit;
	}

	/**
	 * The built-in readers, then those other modules provide (on the module
	 * path, or the class path); a reader that can't be loaded is left out
	 * and logged
	 */
	public static synchronized FileMetadata installed() {
		if (installed == null) {
			List<MetadataReader> all = new ArrayList<>(EmbeddedMetadata.builtIn());
			for (ServiceLoader.Provider<MetadataReader> provider : providers()) {
				try {
					all.add(provider.get());
				} catch (ServiceConfigurationError | LinkageError e) {
					LOGGER.log(Level.WARNING, "Metadata reader " + provider.type().getName() + " not loaded", e);
				}
			}
			installed = new FileMetadata(all, LIMIT);
		}
		return installed;
	}

	private static List<ServiceLoader.Provider<MetadataReader>> providers() {
		try {
			return ServiceLoader.load(MetadataReader.class).stream().toList();
		} catch (ServiceConfigurationError | LinkageError e) {
			LOGGER.log(Level.WARNING, "Metadata readers not loaded", e);
			return List.of();
		}
	}

	/** The readers, in the order asked */
	public List<MetadataReader> readers() {
		return readers;
	}

	/**
	 * What a file says about itself, by each reader that reads it, at the
	 * same time; with a problem for each that failed or took too long, and
	 * if the file can't be read. If the calling thread is interrupted (e.g.
	 * cancelled), the readers are too, and what was found so far is given.
	 */
	public Reading read(Path file) {
		Key key;
		byte[] head;
		try (InputStream in = Files.newInputStream(file)) {
			BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
			key = new Key(file.toAbsolutePath().normalize(), attributes.size(), attributes.lastModifiedTime());
			head = in.readNBytes(MetadataReader.HEAD);
		} catch (IOException | RuntimeException e) {
			LOGGER.log(Level.WARNING, "Not read: " + file, e);
			return new Reading(List.of(), List.of("Not read: " + describe(e)));
		}
		Reading known = remembered.get(key);
		if (known != null) {
			return known;
		}
		List<String> problems = new ArrayList<>();
		Map<MetadataReader, CompletableFuture<List<Found>>> running = new LinkedHashMap<>();
		Map<MetadataReader, Thread> threads = new LinkedHashMap<>();
		for (MetadataReader reader : readers) {
			try {
				if (!reader.reads(file, head.clone())) {
					continue;
				}
			} catch (Throwable e) {
				LOGGER.log(Level.WARNING, name(reader) + " failed on " + file, e);
				problems.add(name(reader) + " failed: " + describe(e));
				continue;
			}
			int busy = stillStuck(reader);
			if (busy >= STUCK) {
				LOGGER.log(Level.WARNING, () -> name(reader) + " skipped for " + file + ": still at " + busy
						+ " files it took too long over");
				problems.add(name(reader) + " skipped: still at " + busy + " earlier files it took too long over");
				continue;
			}
			CompletableFuture<List<Found>> result = new CompletableFuture<>();
			Thread thread = Thread.ofPlatform().daemon().name("Metadata: " + name(reader)).unstarted(() -> {
				try {
					result.complete(List.copyOf(reader.read(file)));
				} catch (Throwable e) {
					result.completeExceptionally(e);
				}
			});
			running.put(reader, result);
			threads.put(reader, thread);
			thread.start();
		}
		List<Found> found = new ArrayList<>();
		long deadline = System.nanoTime() + limit.toNanos();
		boolean interrupted = false;
		for (Map.Entry<MetadataReader, CompletableFuture<List<Found>>> entry : running.entrySet()) {
			MetadataReader reader = entry.getKey();
			try {
				found.addAll(entry.getValue().get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
			} catch (TimeoutException e) {
				threads.get(reader).interrupt();
				stuck.computeIfAbsent(reader, r -> java.util.concurrent.ConcurrentHashMap.newKeySet())
						.add(threads.get(reader));
				LOGGER.log(Level.WARNING, () -> name(reader) + " took over " + limit.toSeconds() + " s on " + file
						+ ", at " + stack(threads.get(reader)));
				problems.add(name(reader) + " took over " + limit.toSeconds() + " s");
			} catch (ExecutionException e) {
				LOGGER.log(Level.WARNING, name(reader) + " failed on " + file, e.getCause());
				problems.add(name(reader) + " failed: " + describe(e.getCause()));
			} catch (InterruptedException e) {
				threads.values().forEach(Thread::interrupt);
				problems.add(name(reader) + " cancelled");
				interrupted = true;
				deadline = System.nanoTime();
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
		Set<List<String>> seen = new HashSet<>();
		Reading reading = new Reading(EmbeddedMetadata.withNamedLicences(found).stream()
				.filter(f -> seen.add(List.of(f.property(), f.value()))).toList(), problems);
		if (problems.isEmpty()) {
			remembered.put(key, reading);
		}
		return reading;
	}

	/** How many of a reader's threads left behind are still running */
	private int stillStuck(MetadataReader reader) {
		Set<Thread> left = stuck.get(reader);
		if (left == null) {
			return 0;
		}
		left.removeIf(t -> !t.isAlive());
		return left.size();
	}

	private static String name(MetadataReader reader) {
		try {
			return reader.name();
		} catch (RuntimeException e) {
			return reader.getClass().getName();
		}
	}

	/** Where a thread is, to see where a reader got stuck */
	private static String stack(Thread thread) {
		StackTraceElement[] stack = thread.getStackTrace();
		return stack.length == 0 ? "(ended)"
				: String.join("\n\tat ", java.util.Arrays.stream(stack).limit(20).map(String::valueOf).toList());
	}

	/** A failure in a line, e.g. "EOFException: Unexpected end of file" */
	private static String describe(Throwable e) {
		String message = e.getMessage() == null ? null : e.getMessage().strip().replaceAll("\\s+", " ");
		return e.getClass().getSimpleName() + (message == null || message.isBlank() ? ""
				: ": " + (message.length() > 200 ? message.substring(0, 199) + "…" : message));
	}
}
