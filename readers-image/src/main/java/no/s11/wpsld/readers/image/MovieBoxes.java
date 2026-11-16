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
package no.s11.wpsld.readers.image;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;

/**
 * A check of an MP4's or QuickTime movie's boxes (atoms) before
 * metadata-extractor reads them, as it allocates for the counts of
 * entries they give (in 2.22.0, a sample description's), so that one
 * damaged makes it run out of memory: a box with more entries than fit
 * in it is refused, as the file is broken. Reads only boxes' headers, and
 * leaves other faults (boxes larger than the file, cut short) to
 * metadata-extractor, which survives them.
 */
final class MovieBoxes {
	/** Boxes of boxes, down to the sample tables */
	private static final Set<String> CONTAINERS = Set.of("moov", "trak", "mdia", "minf", "stbl", "edts", "udta",
			"dinf", "mvex", "moof", "traf");
	/** Boxes with a count of entries after their version and flags, and the least size of each entry */
	private static final Map<String, Integer> COUNTED = Map.of("stsd", 8, "stts", 8);
	/** How deep boxes may be nested */
	private static final int DEPTH = 16;

	private MovieBoxes() {
	}

	/** @throws IOException If a box has more entries than fit in it, or the file can't be read */
	static void check(Path file) throws IOException {
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
			check(channel, 0, channel.size(), 0);
		}
	}

	private static void check(FileChannel channel, long start, long end, int depth) throws IOException {
		if (depth > DEPTH) {
			throw new IOException("Broken movie: boxes nested over " + DEPTH + " deep");
		}
		ByteBuffer header = ByteBuffer.allocate(16);
		long at = start;
		while (at + 8 <= end) {
			read(channel, header.clear().limit(8), at);
			long size = Integer.toUnsignedLong(header.getInt(0));
			String type = new String(header.array(), 4, 4, StandardCharsets.ISO_8859_1);
			int length = 8;
			if (size == 1) {
				if (at + 16 > end) {
					return;
				}
				read(channel, header.clear().position(8).limit(16), at + 8);
				size = header.getLong(8);
				length = 16;
			} else if (size == 0) {
				size = end - at;
			}
			if (size < length || size > end - at) {
				// metadata-extractor's to deal with
				return;
			}
			Integer entry = COUNTED.get(type);
			if (entry != null && size >= length + 8) {
				read(channel, header.clear().limit(8), at + length);
				long count = Integer.toUnsignedLong(header.getInt(4));
				if (count * entry > size - length - 8) {
					throw new IOException("Broken movie: its " + type + " box has " + count + " entries in "
							+ size + " bytes");
				}
			}
			if (CONTAINERS.contains(type)) {
				check(channel, at + length, at + size, depth + 1);
			}
			at += size;
		}
	}

	private static void read(FileChannel channel, ByteBuffer buffer, long at) throws IOException {
		long position = at;
		while (buffer.hasRemaining()) {
			int n = channel.read(buffer, position);
			if (n < 0) {
				throw new EOFException();
			}
			position += n;
		}
	}
}
