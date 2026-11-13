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

import static no.s11.wpsld.lookup.EmbeddedMetadata.date;
import static no.s11.wpsld.lookup.EmbeddedMetadata.people;
import static no.s11.wpsld.lookup.EmbeddedMetadata.text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.lang.KeyValuePair;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.png.PngDirectory;
import com.drew.metadata.webp.WebpDirectory;
import com.drew.metadata.xmp.XmpDirectory;

import no.s11.wpsld.lookup.EmbeddedMetadata;
import no.s11.wpsld.lookup.EmbeddedMetadata.Found;
import no.s11.wpsld.lookup.EmbeddedMetadata.Kind;
import no.s11.wpsld.lookup.MetadataReader;

/**
 * Images' own metadata (JPEG, PNG, TIFF, WebP), with metadata-extractor:
 * EXIF, IPTC, XMP and PNG text, their size, where they were taken (GPS),
 * and the camera. A module of its own, as metadata-extractor and its
 * xmpcore are libraries of their own.
 */
public final class ImageMetadata implements MetadataReader {
	private static final byte[] RIFF = { 'R', 'I', 'F', 'F' };
	/** JPEG, PNG, and TIFF both ways round */
	private static final List<byte[]> IMAGES = List.of(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF },
			new byte[] { (byte) 0x89, 'P', 'N', 'G' }, new byte[] { 'I', 'I', 42, 0 }, new byte[] { 'M', 'M', 0, 42 });

	@Override
	public String name() {
		return "Images (metadata-extractor)";
	}

	/** JPEG, PNG, TIFF and WebP, by what they start with */
	@Override
	public boolean reads(Path file, byte[] head) {
		return IMAGES.stream().anyMatch(image -> startsWith(head, image)) || startsWith(head, RIFF)
				&& head.length >= 12 && new String(head, 8, 4, StandardCharsets.ISO_8859_1).equals("WEBP");
	}

	private static boolean startsWith(byte[] head, byte[] start) {
		return head.length >= start.length && Arrays.equals(head, 0, start.length, start, 0, start.length);
	}

	/**
	 * An image's EXIF (description, artist, copyright, when taken and
	 * changed, where taken), IPTC (caption, by-line, copyright, keywords),
	 * PNG text and XMP; its size; and the camera and how it was set, to
	 * give only on purpose (it may identify the camera).
	 */
	@Override
	public List<Found> read(Path file) throws IOException {
		Metadata metadata;
		try {
			metadata = ImageMetadataReader.readMetadata(file.toFile());
		} catch (ImageProcessingException e) {
			throw new IOException(e.getMessage(), e);
		}
		List<Found> found = new ArrayList<>();
		for (ExifIFD0Directory exif : metadata.getDirectoriesOfType(ExifIFD0Directory.class)) {
			String source = "EXIF ";
			text(exif.getString(ExifDirectoryBase.TAG_IMAGE_DESCRIPTION))
					.ifPresent(t -> found.add(new Found("description", t, Kind.TEXT, source + "ImageDescription")));
			text(exif.getString(ExifDirectoryBase.TAG_ARTIST)).ifPresent(t -> people(t)
					.forEach(person -> found.add(new Found("author", person, Kind.PERSON, source + "Artist"))));
			text(exif.getString(ExifDirectoryBase.TAG_COPYRIGHT))
					.ifPresent(t -> found.add(new Found("copyrightNotice", t, Kind.TEXT, source + "Copyright")));
			exifDate(exif.getString(ExifDirectoryBase.TAG_DATETIME), null)
					.ifPresent(d -> found.add(new Found("dateModified", d, Kind.DATE, source + "DateTime")));
		}
		for (ExifSubIFDDirectory exif : metadata.getDirectoriesOfType(ExifSubIFDDirectory.class)) {
			exifDate(exif.getString(ExifDirectoryBase.TAG_DATETIME_ORIGINAL),
					exif.getString(ExifDirectoryBase.TAG_TIME_ZONE_ORIGINAL))
					.ifPresent(d -> found.add(new Found("dateCreated", d, Kind.DATE, "EXIF DateTimeOriginal")));
		}
		camera(metadata).ifPresent(c -> found.add(new Found("exifData", c, Kind.TEXT, "EXIF Make, Model, …")));
		size(metadata, found);
		for (GpsDirectory gps : metadata.getDirectoriesOfType(GpsDirectory.class)) {
			GeoLocation where = gps.getGeoLocation();
			if (where != null && !where.isZero()) {
				found.add(new Found("contentLocation", String.format(Locale.ROOT, "%.6f, %.6f", where.getLatitude(),
						where.getLongitude()), Kind.PLACE, "EXIF GPS"));
			}
		}
		for (IptcDirectory iptc : metadata.getDirectoriesOfType(IptcDirectory.class)) {
			String source = "IPTC ";
			text(iptc.getString(IptcDirectory.TAG_OBJECT_NAME))
					.ifPresent(t -> found.add(new Found("name", t, Kind.TEXT, source + "ObjectName")));
			text(iptc.getString(IptcDirectory.TAG_CAPTION))
					.ifPresent(t -> found.add(new Found("description", t, Kind.TEXT, source + "Caption")));
			Optional.ofNullable(iptc.getStringArray(IptcDirectory.TAG_BY_LINE)).stream().flatMap(Arrays::stream)
					.flatMap(t -> text(t).stream())
					.forEach(t -> found.add(new Found("author", t, Kind.PERSON, source + "By-line")));
			text(iptc.getString(IptcDirectory.TAG_COPYRIGHT_NOTICE))
					.ifPresent(t -> found.add(new Found("copyrightNotice", t, Kind.TEXT, source + "CopyrightNotice")));
			List<String> keywords = iptc.getKeywords();
			if (keywords != null && !keywords.isEmpty()) {
				found.add(new Found("keywords", String.join(", ", keywords), Kind.KEYWORDS, source + "Keywords"));
			}
		}
		for (PngDirectory png : metadata.getDirectoriesOfType(PngDirectory.class)) {
			if (png.getObject(PngDirectory.TAG_TEXTUAL_DATA) instanceof List<?> texts) {
				for (Object text : texts) {
					if (text instanceof KeyValuePair pair) {
						pngText(pair.getKey(), pair.getValue().toString(), found);
					}
				}
			}
		}
		for (XmpDirectory xmp : metadata.getDirectoriesOfType(XmpDirectory.class)) {
			found.addAll(EmbeddedMetadata.xmpProperties(xmp.getXmpProperties(), "XMP "));
		}
		return found;
	}

	/**
	 * The image's size in pixels, as schema.org's Distance (e.g.
	 * <code>605 px</code>), from its format's own header: JPEG's frame,
	 * PNG's IHDR, WebP's, else TIFF's (EXIF IFD0)
	 */
	private static void size(Metadata metadata, List<Found> found) {
		List<Map.Entry<Class<? extends Directory>, int[]>> sources = List.of(
				Map.entry(JpegDirectory.class, new int[] { JpegDirectory.TAG_IMAGE_WIDTH, JpegDirectory.TAG_IMAGE_HEIGHT }),
				Map.entry(PngDirectory.class, new int[] { PngDirectory.TAG_IMAGE_WIDTH, PngDirectory.TAG_IMAGE_HEIGHT }),
				Map.entry(WebpDirectory.class, new int[] { WebpDirectory.TAG_IMAGE_WIDTH, WebpDirectory.TAG_IMAGE_HEIGHT }),
				Map.entry(ExifIFD0Directory.class,
						new int[] { ExifDirectoryBase.TAG_IMAGE_WIDTH, ExifDirectoryBase.TAG_IMAGE_HEIGHT }));
		for (Map.Entry<Class<? extends Directory>, int[]> source : sources) {
			for (Directory d : metadata.getDirectoriesOfType(source.getKey())) {
				Integer width = d.getInteger(source.getValue()[0]);
				Integer height = d.getInteger(source.getValue()[1]);
				if (width != null && height != null && width > 0 && height > 0) {
					String where = d.getName() + " ";
					found.add(new Found("width", width + " px", Kind.TEXT, where + "width"));
					found.add(new Found("height", height + " px", Kind.TEXT, where + "height"));
					return;
				}
			}
		}
	}

	/**
	 * The camera and how the photo was taken, as text, e.g. "Canon EOS R6,
	 * RF 24-105mm F4 L, f/8.0, 1/350 sec, ISO 100, 105 mm"
	 */
	private static Optional<String> camera(Metadata metadata) {
		List<String> parts = new ArrayList<>();
		ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
		if (ifd0 != null) {
			String make = text(ifd0.getString(ExifDirectoryBase.TAG_MAKE)).orElse("");
			String model = text(ifd0.getString(ExifDirectoryBase.TAG_MODEL)).orElse("");
			String camera = model.toLowerCase(Locale.ROOT).startsWith(make.toLowerCase(Locale.ROOT)) ? model
					: (make + " " + model).strip();
			text(camera).ifPresent(parts::add);
		}
		ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
		if (sub != null) {
			for (int tag : new int[] { ExifDirectoryBase.TAG_LENS_MODEL, ExifDirectoryBase.TAG_FNUMBER,
					ExifDirectoryBase.TAG_EXPOSURE_TIME, ExifDirectoryBase.TAG_ISO_EQUIVALENT,
					ExifDirectoryBase.TAG_FOCAL_LENGTH }) {
				text(sub.getDescription(tag)).map(t -> tag == ExifDirectoryBase.TAG_ISO_EQUIVALENT ? "ISO " + t : t)
						.ifPresent(parts::add);
			}
		}
		return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", parts));
	}

	/** PNG's own text keywords (tEXt, iTXt), as the PNG specification names them */
	private static void pngText(String key, String value, List<Found> found) {
		String source = "PNG text ";
		text(value).ifPresent(t -> {
			switch (key) {
			case "Title" -> found.add(new Found("name", t, Kind.TEXT, source + key));
			case "Description" -> found.add(new Found("description", t, Kind.TEXT, source + key));
			case "Author" -> people(t).forEach(p -> found.add(new Found("author", p, Kind.PERSON, source + key)));
			case "Copyright" -> found.add(new Found("copyrightNotice", t, Kind.TEXT, source + key));
			case "Creation Time" -> date(t).ifPresent(d -> found.add(new Found("dateCreated", d, Kind.DATE,
					source + key)));
			default -> {
				// Others, e.g. Software and XML:com.adobe.xmp (read as XMP)
			}
			}
		});
	}

	/**
	 * An EXIF date, <code>2026:04:20 15:36:54</code>, as ISO 8601, with
	 * its offset if EXIF 2.31's OffsetTime* gives it
	 */
	public static Optional<String> exifDate(String text, String offset) {
		if (text == null) {
			return Optional.empty();
		}
		java.util.regex.Matcher m = Pattern.compile("(\\d{4}):(\\d{2}):(\\d{2}) (\\d{2}:\\d{2}:\\d{2})")
				.matcher(text.strip());
		if (!m.matches() || m.group(1).compareTo("1900") < 0) {
			return Optional.empty();
		}
		String zone = offset != null && offset.strip().matches("[+-]\\d{2}:\\d{2}") ? offset.strip() : "";
		return Optional.of(m.group(1) + "-" + m.group(2) + "-" + m.group(3) + "T" + m.group(4) + zone);
	}
}
