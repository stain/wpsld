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

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import no.s11.wpsld.lookup.EmbeddedMetadata.Found;
import no.s11.wpsld.lookup.EmbeddedMetadata.Kind;

public class TestFileMetadata {
	/** A reader of every file, doing as told */
	private record Reader(String name, Action action) implements MetadataReader {
		interface Action {
			List<Found> read() throws Exception;
		}

		@Override
		public boolean reads(Path file, byte[] head) {
			return true;
		}

		@Override
		public List<Found> read(Path file) throws IOException {
			try {
				return action.read();
			} catch (IOException | RuntimeException | Error e) {
				throw e;
			} catch (Exception e) {
				throw new IOException(e);
			}
		}
	}

	private static int depth(int n) {
		return depth(n + 1) + 1;
	}

	/**
	 * Readers that fail in any way, or take too long, are problems; the
	 * others' values are found, each once, and licences named in text
	 */
	@Test
	public void isolated() throws Exception {
		Path file = Files.writeString(Files.createTempFile("file", ".txt"), "text");
		Found name = new Found("name", "Report", Kind.TEXT, "A");
		AtomicInteger reads = new AtomicInteger();
		FileMetadata metadata = new FileMetadata(List.of(new Reader("Good", () -> {
			reads.incrementAndGet();
			return List.of(name, new Found("copyrightNotice", "CC BY 4.0", Kind.TEXT, "A"));
		}), new Reader("Again", () -> List.of(name)),
				new Reader("Broken", () -> {
					throw new IOException("Unexpected end of file");
				}), new Reader("Deep", () -> List.of(new Found("name", "" + depth(0), Kind.TEXT, "D"))),
				new Reader("Missing", () -> {
					throw new NoClassDefFoundError("org/example/Missing");
				}), new Reader("Slow", () -> {
					Thread.sleep(60_000);
					return List.of();
				})), Duration.ofSeconds(2));
		FileMetadata.Reading reading = metadata.read(file);
		assertEquals(List.of("name Report", "copyrightNotice CC BY 4.0", "license CC-BY-4.0"),
				reading.found().stream().map(f -> f.property() + " " + f.value()).toList());
		assertEquals(List.of("Broken failed: IOException: Unexpected end of file", "Deep failed: StackOverflowError",
				"Missing failed: NoClassDefFoundError: org/example/Missing", "Slow took over 2 s"),
				reading.problems());
		// Read again, as it had problems
		metadata.read(file);
		assertEquals(2, reads.get());
	}

	/** A reading without problems is remembered, until the file changes */
	@Test
	public void remembered() throws Exception {
		Path file = Files.writeString(Files.createTempFile("file", ".txt"), "text");
		AtomicInteger reads = new AtomicInteger();
		FileMetadata metadata = new FileMetadata(List.of(new Reader("Counting", () -> {
			reads.incrementAndGet();
			return List.of();
		})), Duration.ofSeconds(5));
		metadata.read(file);
		metadata.read(file);
		assertEquals(1, reads.get());
		Files.writeString(file, "more text");
		metadata.read(file);
		assertEquals(2, reads.get());
		// Gone
		assertEquals(List.of(), metadata.read(file.resolveSibling("gone.txt")).found());
		assertEquals(1, metadata.read(file.resolveSibling("gone.txt")).problems().size());
	}

	/** Cancelled: the readers too, with what was found */
	@Test
	public void interrupted() throws Exception {
		Path file = Files.writeString(Files.createTempFile("file", ".txt"), "text");
		FileMetadata metadata = new FileMetadata(List.of(new Reader("Slow", () -> {
			Thread.sleep(60_000);
			return List.of();
		})), Duration.ofSeconds(30));
		Thread.currentThread().interrupt();
		long start = System.nanoTime();
		FileMetadata.Reading reading = metadata.read(file);
		assertTrue(Thread.interrupted());
		assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 5);
		assertEquals(List.of("Slow cancelled"), reading.problems());
	}

	/**
	 * A reader stuck on files, not even stopping when interrupted, is
	 * skipped once two of its threads are left behind, until they end
	 */
	@Test
	public void stuckSkipped() throws Exception {
		java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
		FileMetadata metadata = new FileMetadata(List.of(new Reader("Stuck", () -> {
			// As a library looping, not seeing interrupts
			while (release.getCount() > 0) {
				Thread.onSpinWait();
			}
			return List.of(new Found("name", "At last", Kind.TEXT, "test"));
		})), Duration.ofMillis(200));
		for (int i = 0; i < FileMetadata.STUCK; i++) {
			Path file = Files.writeString(Files.createTempFile("file", ".txt"), "text " + i);
			assertEquals(List.of("Stuck took over 0 s"), metadata.read(file).problems());
		}
		Path next = Files.writeString(Files.createTempFile("file", ".txt"), "next");
		long start = System.nanoTime();
		assertEquals(List.of("Stuck skipped: still at 2 earlier files it took too long over"),
				metadata.read(next).problems());
		// At once, not after the limit
		assertTrue(Duration.ofNanos(System.nanoTime() - start).toMillis() < 150);
		// Once they end, it reads again
		release.countDown();
		long until = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		FileMetadata.Reading reading;
		do {
			Thread.sleep(20);
			reading = metadata.read(next);
		} while (!reading.problems().isEmpty() && System.nanoTime() < until);
		assertEquals(List.of(), reading.problems());
		assertEquals("At last", reading.found().get(0).value());
	}

	/** The built-in readers, and no others in lib */
	@Test
	public void installed() {
		assertEquals(List.of("Office and OpenDocument", "SVG", "XMP packets"),
				FileMetadata.installed().readers().stream().map(MetadataReader::name).toList());
	}
}
