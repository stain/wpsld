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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.tika.Tika;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;

import com.fasterxml.jackson.databind.node.TextNode;

import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

/**
 * A file's format, from its content and its name: its media type by Apache
 * Tika (the "magic" of its first bytes, then its name), and the PRONOM
 * format if only one fits that and the file's extension. A file whose
 * content says nothing known, e.g. a .dat or .bin of an instrument, is
 * application/octet-stream, without a PRONOM format, rather than a guess
 * from an extension that many formats share.
 */
public final class FileFormats {
	/** Any bytes, the media type of a file whose format isn't known */
	public static final String OCTET_STREAM = "application/octet-stream";
	/** HDF5, which has no registered media type; as the HDF Group writes it */
	static final String HDF5 = "application/x-hdf5";
	private static final byte[] HDF5_SIGNATURE = { (byte) 0x89, 'H', 'D', 'F', '\r', '\n', 0x1a, '\n' };
	/** NASA's Common Data Format, which Tika takes for NetCDF by its .cdf */
	static final String NASA_CDF = "application/x-cdf";
	/**
	 * Tika's names for types registered since by other names: Parquet
	 * (2024), YAML (RFC 9512)
	 */
	private static final java.util.Map<String, String> REGISTERED = java.util.Map.of(
			"application/x-parquet", "application/vnd.apache.parquet",
			"text/x-yaml", "application/yaml");
	/** Text that Tika doesn't tell apart, by extension */
	private static final java.util.Map<String, String> TEXT_BY_EXTENSION = java.util.Map.of(
			"jsonld", "application/ld+json",
			"ipynb", "application/x-ipynb+json",
			"geojson", "application/geo+json");
	/**
	 * Formats with registered media types that Tika doesn't know, by how
	 * they start: WMO's GRIB and BUFR, Apache Arrow (and Feather 2)
	 */
	private static final java.util.Map<String, String> BY_START = java.util.Map.of(
			"GRIB", "application/grib",
			"BUFR", "application/bufr",
			"ARROW1", "application/vnd.apache.arrow.file");

	/** What a file is */
	public record Detected(String mediaType, Optional<String> name, Optional<PRONOMFormats.Format> pronom) {
	}

	private final Tika tika = new Tika();
	private final MimeTypes types = MimeTypes.getDefaultMimeTypes();
	private final PRONOMFormats pronom;

	public FileFormats(PRONOMFormats pronom) {
		this.pronom = pronom;
	}

	/** With the PRONOM formats bundled */
	public static FileFormats bundled() {
		return Holder.BUNDLED;
	}

	private static final class Holder {
		static final FileFormats BUNDLED = new FileFormats(PRONOMFormats.bundled());
	}

	/**
	 * What a file is, from its content and name
	 *
	 * @throws IOException if it can't be read
	 */
	public Detected detect(Path file) throws IOException {
		String mediaType = tika.detect(file);
		byte[] head = head(file, 512);
		String start = new String(head, 0, Math.min(head.length, 16), java.nio.charset.StandardCharsets.ISO_8859_1);
		// HDF5 also with its superblock after a user block (512, 1024, 2048… bytes)
		if (!mediaType.equals(HDF5) && isHdf5(file)) {
			mediaType = HDF5;
		} else if (isNasaCdf(head)) {
			mediaType = NASA_CDF;
		} else {
			for (var known : BY_START.entrySet()) {
				if (start.startsWith(known.getKey())) {
					mediaType = known.getValue();
				}
			}
		}
		// Where Tika goes by an extension two formats share
		if (mediaType.equals("text/x-vcard") && start.startsWith("##fileformat=VCF")) {
			// Variant Call Format, not a vCard: no media type of its own
			mediaType = "text/plain";
		} else if (mediaType.equals("application/marc") && head.length >= 212
				&& new String(head, 208, 4, java.nio.charset.StandardCharsets.ISO_8859_1).equals("MAP ")) {
			// An MRC map of electron microscopy, not a MARC library record
			mediaType = OCTET_STREAM;
		}
		mediaType = REGISTERED.getOrDefault(mediaType, mediaType);
		String name = file.getFileName().toString();
		String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		if ((mediaType.equals("text/plain") || mediaType.equals("application/json"))
				&& TEXT_BY_EXTENSION.containsKey(extension) && name.contains(".")) {
			mediaType = TEXT_BY_EXTENSION.get(extension);
		}
		Optional<String> description = Optional.empty();
		try {
			MimeType type = types.forName(mediaType);
			description = Optional.of(type.getDescription()).filter(d -> !d.isBlank());
		} catch (MimeTypeException e) {
			// Not one Tika knows by name
		}
		return new Detected(mediaType, description, pronomFormat(mediaType, name));
	}

	/**
	 * The PRONOM format of a media type and file name: the only one with
	 * both that media type and the name's extension. Not by the media type
	 * alone: unregistered ones are shared (application/x-cdf is both NASA's
	 * CDF and CD Audio), and a .bam is gzip but not just a GZIP file. None
	 * for application/octet-stream, or if several fit (e.g. PDF's versions).
	 */
	Optional<PRONOMFormats.Format> pronomFormat(String mediaType, String fileName) {
		if (mediaType.equals(OCTET_STREAM)) {
			return Optional.empty();
		}
		String type = mediaType.replaceAll(";.*", "").strip();
		List<PRONOMFormats.Format> both = pronom.withExtension(fileName).stream()
				.filter(f -> f.mediaTypes().stream().anyMatch(type::equalsIgnoreCase)).toList();
		return both.size() == 1 ? Optional.of(both.get(0)) : Optional.empty();
	}

	/** Whether a file starts as NASA's CDF does: CD F3 00 01, or CD F2 60 02 (before version 2.6) */
	static boolean isNasaCdf(byte[] head) {
		return head.length >= 4 && (head[0] & 0xff) == 0xcd
				&& ((head[1] & 0xff) == 0xf3 && head[2] == 0 && head[3] == 1
						|| (head[1] & 0xff) == 0xf2 && (head[2] & 0xff) == 0x60 && head[3] == 2);
	}

	/** A file's first bytes, as many as it has up to this many */
	private static byte[] head(Path file, int count) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			return in.readNBytes(count);
		}
	}

	/** Whether a file has HDF5's signature at 0, 512, 1024, 2048… bytes */
	static boolean isHdf5(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			long size = Files.size(file);
			byte[] head = new byte[HDF5_SIGNATURE.length];
			long at = 0;
			for (long offset = 0; offset + head.length <= size; offset = offset == 0 ? 512 : offset * 2) {
				in.skipNBytes(offset - at);
				if (in.readNBytes(head, 0, head.length) < head.length) {
					return false;
				}
				at = offset + head.length;
				if (Arrays.equals(head, HDF5_SIGNATURE)) {
					return true;
				}
				// Not past the first few megabytes
				if (offset >= 1 << 22) {
					return false;
				}
			}
		} catch (IOException e) {
			return false;
		}
		return false;
	}

	/**
	 * Describe a file's format, where it has none: its media type in
	 * <code>encodingFormat</code>, and a link to its PRONOM format, if one
	 * fits, described in the crate.
	 *
	 * @return What was detected, if anything was added
	 */
	public Optional<Detected> addTo(Crate crate, Entity file, Path path) {
		if (!file.getAll("encodingFormat").isEmpty()) {
			return Optional.empty();
		}
		Detected detected;
		try {
			detected = detect(path);
		} catch (IOException e) {
			return Optional.empty();
		}
		if (detected.pronom().isPresent()) {
			file.setAll("encodingFormat", List.of(TextNode.valueOf(detected.mediaType())));
			PRONOMFormats.addTo(file, detected.pronom().get());
			PRONOMFormats.describeIn(crate, detected.pronom().get());
		} else {
			file.setAll("encodingFormat", List.of(TextNode.valueOf(detected.mediaType().toLowerCase(Locale.ROOT))));
		}
		return Optional.of(detected);
	}
}
