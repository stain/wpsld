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
package no.s11.wpsld.test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import no.s11.wpsld.lookup.FileMetadata;
import no.s11.wpsld.lookup.MetadataReader;

/**
 * Damaged copies of a file, as files from elsewhere may be: cut short,
 * with bytes changed, with runs of 0xFF or 0 (huge lengths and counts),
 * garbage after the first bytes (which readers choose files by), and a
 * part repeated (deep nesting). The same copies on every run (seeded), so
 * that a failure can be repeated.
 */
public final class Damage {
	/** A damaged copy, and how it was damaged */
	public record Copy(String how, byte[] bytes) {
	}

	private Damage() {
	}

	/** The damaged copies of a file's bytes */
	public static List<Copy> of(byte[] original, long seed) {
		Random random = new Random(seed);
		List<Copy> copies = new ArrayList<>();
		int n = original.length;
		for (int i = 0; i < 40; i++) {
			int cut = (int) ((long) n * i / 40);
			copies.add(new Copy("cut at " + cut, Arrays.copyOf(original, cut)));
		}
		for (int i = 0; i < 30; i++) {
			byte[] bytes = original.clone();
			int changes = 1 + random.nextInt(Math.max(1, n / 50));
			for (int c = 0; c < changes; c++) {
				bytes[random.nextInt(n)] = (byte) random.nextInt(256);
			}
			copies.add(new Copy(changes + " bytes changed, seed " + seed + " #" + i, bytes));
		}
		for (int i = 0; i < 30; i++) {
			byte[] bytes = original.clone();
			int at = random.nextInt(n);
			Arrays.fill(bytes, at, Math.min(n, at + 2 + random.nextInt(8)), (byte) (i % 2 == 0 ? 0xFF : 0));
			copies.add(new Copy((i % 2 == 0 ? "0xFF" : "0") + " run at " + at, bytes));
		}
		for (int i = 0; i < 5; i++) {
			byte[] bytes = original.clone();
			for (int b = Math.min(16, n); b < n; b++) {
				bytes[b] = (byte) random.nextInt(256);
			}
			copies.add(new Copy("garbage after 16 bytes, seed " + seed + " #" + i, bytes));
		}
		if (n > 64) {
			int from = n / 4;
			int to = Math.min(n / 2, from + 4096);
			byte[] part = Arrays.copyOfRange(original, from, to);
			byte[] bytes = new byte[n + part.length * 50];
			System.arraycopy(original, 0, bytes, 0, from);
			for (int r = 0; r < 50; r++) {
				System.arraycopy(part, 0, bytes, from + r * part.length, part.length);
			}
			System.arraycopy(original, from, bytes, from + part.length * 50, n - from);
			copies.add(new Copy("part repeated 50 times", bytes));
		}
		return copies;
	}

	/**
	 * How many rounds of copies to read, each with its own seed: the system
	 * property wpsld.test.damageRounds, by default 20 (about 2000 copies)
	 */
	private static int rounds() {
		return Integer.getInteger("wpsld.test.damageRounds", 20);
	}

	/**
	 * Read every damaged copy of a file with these readers, in
	 * {@link #rounds()} rounds of seeds from the seed given: each reading
	 * must come back, and no reader may take over the time limit, run out
	 * of memory or overflow its stack (failing otherwise, e.g. with an
	 * IOException, is as it should be)
	 *
	 * @param name The file's name, as readers may go by it
	 */
	public static void readAll(List<MetadataReader> readers, String name, byte[] original, long seed)
			throws IOException {
		FileMetadata metadata = new FileMetadata(readers, Duration.ofSeconds(10));
		Path dir = Files.createTempDirectory("damaged");
		List<String> bad = new ArrayList<>();
		List<Copy> copies = new ArrayList<>();
		for (int round = 0; round < rounds(); round++) {
			copies.addAll(of(original, seed * 1000 + round));
		}
		for (int i = 0; i < copies.size(); i++) {
			Copy copy = copies.get(i);
			// Each its own file, not taken for one read already
			Path file = Files.write(dir.resolve(i + "-" + name), copy.bytes());
			for (String problem : metadata.read(file).problems()) {
				if (problem.contains(" took over ") || problem.contains("OutOfMemoryError")
						|| problem.contains("StackOverflowError")) {
					bad.add(name + ", " + copy.how() + ": " + problem);
				}
			}
		}
		assertTrue(String.join("\n", bad), bad.isEmpty());
	}
}
