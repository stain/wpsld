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
		// HDF5 also with its superblock after a user block (512, 1024, 2048… bytes)
		if (!mediaType.equals(HDF5) && isHdf5(file)) {
			mediaType = HDF5;
		}
		Optional<String> name = Optional.empty();
		try {
			MimeType type = types.forName(mediaType);
			name = Optional.of(type.getDescription()).filter(d -> !d.isBlank());
		} catch (MimeTypeException e) {
			// Not one Tika knows by name
		}
		return new Detected(mediaType, name, pronomFormat(mediaType, file.getFileName().toString()));
	}

	/**
	 * The PRONOM format of a media type and file name: the only one with
	 * that media type, else the only one with it and the name's extension;
	 * none for application/octet-stream, or if several fit (e.g. PDF's
	 * versions).
	 */
	Optional<PRONOMFormats.Format> pronomFormat(String mediaType, String fileName) {
		if (mediaType.equals(OCTET_STREAM)) {
			return Optional.empty();
		}
		List<PRONOMFormats.Format> withType = pronom.getFormats().stream()
				.filter(f -> f.mediaTypes().stream().anyMatch(mediaType::equalsIgnoreCase)).toList();
		if (withType.size() == 1) {
			return Optional.of(withType.get(0));
		}
		List<PRONOMFormats.Format> byName = pronom.withExtension(fileName);
		List<PRONOMFormats.Format> both = withType.stream().filter(byName::contains).toList();
		return both.size() == 1 ? Optional.of(both.get(0)) : Optional.empty();
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
