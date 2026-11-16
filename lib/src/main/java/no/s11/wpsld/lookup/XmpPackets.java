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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import no.s11.wpsld.lookup.EmbeddedMetadata.Found;

/**
 * A file's XMP found by scanning it for XMP packets
 * (<code>&lt;?xpacket begin=…?&gt;</code> … <code>&lt;?xpacket
 * end=…?&gt;</code>), as Adobe's XMP specification allows for formats it
 * isn't known how to read: PDFs (Acrobat, InDesign, LibreOffice and most
 * others write their XMP uncompressed so that it can be found), PostScript
 * and EPS (also old Illustrator <code>.ai</code>), Photoshop's PSD and
 * InDesign files. Needs only the JDK. A PDF may have several packets,
 * e.g. its images' own: its document's is the one with PDF's own terms
 * (<code>pdf:Producer</code>, …), the last if updated, else its only one;
 * of other formats the first. What PDFBox reads besides (the document
 * information, compressed XMP) is in no.s11.wpsld.readers.pdf.
 */
final class XmpPackets implements MetadataReader {
	private static final byte[] BEGIN = "<?xpacket begin=".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] END = "<?xpacket end=".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] PDF_TERMS = "http://ns.adobe.com/pdf/1.3/".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] PDF = { '%', 'P', 'D', 'F', '-' };
	/** PostScript and EPS, Photoshop, and DOS EPS with its binary header */
	private static final List<byte[]> OTHERS = List.of(new byte[] { '%', '!', 'P', 'S' },
			new byte[] { '8', 'B', 'P', 'S' }, new byte[] { (byte) 0xC5, (byte) 0xD0, (byte) 0xD3, (byte) 0xC6 },
			// InDesign's GUID
			new byte[] { 0x06, 0x06, (byte) 0xED, (byte) 0xF5, (byte) 0xD8, 0x1D, 0x46, (byte) 0xE5, (byte) 0xBD,
					0x31, (byte) 0xEF, (byte) 0xE7, (byte) 0xFE, 0x74, (byte) 0xB7, 0x1D });
	/** The most of a packet, as of an XML part */
	private static final int MAX_PACKET = 1 << 20;
	/** The most packets to keep */
	private static final int MAX_PACKETS = 64;
	/**
	 * How much of each end of a large file to scan: its document's XMP is
	 * near the start, or near the end if it was updated
	 */
	static final long SCAN = 64L << 20;

	@Override
	public String name() {
		return "XMP packets";
	}

	@Override
	public boolean reads(Path file, byte[] head) {
		return startsWith(head, PDF) || OTHERS.stream().anyMatch(start -> startsWith(head, start));
	}

	private static boolean startsWith(byte[] head, byte[] start) {
		return head.length >= start.length && Arrays.equals(head, 0, start.length, start, 0, start.length);
	}

	@Override
	public List<Found> read(Path file) throws IOException {
		boolean pdf;
		List<byte[]> packets;
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
			byte[] head = new byte[PDF.length];
			pdf = channel.read(java.nio.ByteBuffer.wrap(head)) == head.length && startsWith(head, PDF);
			packets = packets(channel);
		}
		List<byte[]> chosen;
		if (pdf) {
			List<byte[]> document = packets.stream().filter(p -> indexOf(p, PDF_TERMS, 0) >= 0).toList();
			chosen = !document.isEmpty() ? List.of(document.get(document.size() - 1))
					: packets.size() == 1 ? packets : List.of();
		} else {
			chosen = packets.stream().limit(1).toList();
		}
		List<Found> found = new ArrayList<>();
		for (byte[] packet : chosen) {
			found.addAll(EmbeddedMetadata.xmp(new ByteArrayInputStream(packet), pdf ? "PDF XMP " : "XMP "));
		}
		return found;
	}

	/**
	 * The packets in a file, each from its begin to its end processing
	 * instruction: all of a file up to twice {@link #SCAN}, else its start
	 * and end
	 */
	static List<byte[]> packets(FileChannel channel) throws IOException {
		long size = channel.size();
		List<byte[]> packets = new ArrayList<>();
		if (size <= 2 * SCAN) {
			scan(Channels.newInputStream(channel.position(0)), size, packets);
		} else {
			scan(Channels.newInputStream(channel.position(0)), SCAN, packets);
			scan(Channels.newInputStream(channel.position(size - SCAN)), SCAN, packets);
		}
		return packets;
	}

	/** Scan up to so many bytes for packets, adding them */
	private static void scan(InputStream in, long length, List<byte[]> packets) throws IOException {
		byte[] buffer = new byte[1 << 16];
		ByteArrayOutputStream packet = null;
		// How much of BEGIN (outside a packet) or END (inside) matches, and
		// whether END was, so that the packet ends at the next "?>"
		int matched = 0;
		boolean ended = false;
		byte last = 0;
		long left = length;
		int n;
		while (left > 0 && (n = in.read(buffer, 0, (int) Math.min(buffer.length, left))) > 0) {
			left -= n;
			for (int i = 0; i < n; i++) {
				byte b = buffer[i];
				if (packet == null) {
					matched = b == BEGIN[matched] ? matched + 1 : b == BEGIN[0] ? 1 : 0;
					if (matched == BEGIN.length) {
						packet = new ByteArrayOutputStream();
						packet.write(BEGIN, 0, BEGIN.length);
						matched = 0;
						ended = false;
					}
					continue;
				}
				packet.write(b);
				if (packet.size() > MAX_PACKET) {
					// Too large, or never ended: look for the next
					packet = null;
					matched = 0;
				} else if (ended) {
					if (last == '?' && b == '>') {
						if (packets.size() < MAX_PACKETS) {
							packets.add(packet.toByteArray());
						}
						packet = null;
					}
				} else {
					matched = b == END[matched] ? matched + 1 : b == END[0] ? 1 : 0;
					ended = matched == END.length;
				}
				last = b;
			}
		}
	}

	private static int indexOf(byte[] bytes, byte[] part, int from) {
		outer: for (int i = from; i <= bytes.length - part.length; i++) {
			for (int j = 0; j < part.length; j++) {
				if (bytes[i + j] != part[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}
}
