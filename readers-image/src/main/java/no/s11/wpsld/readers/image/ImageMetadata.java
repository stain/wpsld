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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.lang.KeyValuePair;
import com.drew.lang.Rational;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.bmp.BmpHeaderDirectory;
import com.drew.metadata.eps.EpsDirectory;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.gif.GifHeaderDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mov.media.QuickTimeVideoDirectory;
import com.drew.metadata.mov.metadata.QuickTimeMetadataDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.mp4.media.Mp4VideoDirectory;
import com.drew.metadata.photoshop.PsdHeaderDirectory;
import com.drew.metadata.png.PngDirectory;
import com.drew.metadata.webp.WebpDirectory;
import com.drew.metadata.xmp.XmpDirectory;

import no.s11.wpsld.lookup.EmbeddedMetadata;
import no.s11.wpsld.lookup.EmbeddedMetadata.Found;
import no.s11.wpsld.lookup.EmbeddedMetadata.Kind;
import no.s11.wpsld.lookup.MetadataReader;

/**
 * Images' and videos' own metadata, with metadata-extractor: of images
 * (JPEG, PNG, TIFF, WebP, HEIC/HEIF, AVIF, GIF, BMP, PSD, EPS and camera
 * raw formats: CR2, CR3, NEF, ARW, ORF, RW2, RAF and DNG, as TIFF) their
 * EXIF, IPTC, XMP and PNG text, their size, where they were taken (GPS),
 * and the camera; of videos (MP4, QuickTime MOV) their title, author,
 * when and where they were made, duration and size, and the phone or
 * camera, as Apple's QuickTime metadata and MP4's movie header give them.
 * Which formats by metadata-extractor's own detection, from what files
 * start with. A module of its own, as metadata-extractor and its xmpcore
 * are libraries of their own.
 */
public final class ImageMetadata implements MetadataReader {
	/**
	 * The formats metadata-extractor reads that say something of
	 * themselves; not icons, PCX, AVI, WAV nor MP3, of which it reads only
	 * how they are encoded
	 */
	private static final Set<FileType> READ = EnumSet.of(FileType.Jpeg, FileType.Tiff, FileType.Png,
			FileType.WebP, FileType.Heif, FileType.Avif, FileType.Gif, FileType.Bmp, FileType.Psd, FileType.Eps,
			FileType.Arw, FileType.Cr2, FileType.Crx, FileType.Nef, FileType.Orf, FileType.Raf, FileType.Rw2,
			FileType.Mp4, FileType.QuickTime);
	/** A date QuickTime's movie header has when not set: its epoch, 1904 */
	private static final Instant UNSET = Instant.parse("1971-01-01T00:00:00Z");
	/** Latitude and longitude of a point in ISO 6709, each with its sign */
	private static final Pattern ISO_6709 = Pattern.compile("([+-])(\\d+)(\\.\\d+)?([+-])(\\d+)(\\.\\d+)?");

	@Override
	public String name() {
		return "Images and videos (metadata-extractor)";
	}

	/** Those of {@link #READ}, by metadata-extractor's detection */
	@Override
	public boolean reads(Path file, byte[] head) {
		return READ.contains(type(head));
	}

	/** What metadata-extractor takes a file starting so to be */
	static FileType type(byte[] head) {
		try {
			return FileTypeDetector.detectFileType(new BufferedInputStream(new ByteArrayInputStream(head)));
		} catch (IOException | RuntimeException e) {
			return FileType.Unknown;
		}
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
			FileType type;
			try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
				type = FileTypeDetector.detectFileType(in);
			}
			if (type == FileType.Mp4 || type == FileType.QuickTime) {
				MovieBoxes.check(file);
			}
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
		camera(metadata).ifPresent(c -> found.add(new Found("exifData", c, Kind.TEXT, "Make, Model, …")));
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
		video(metadata, found);
		return found;
	}

	/**
	 * A video's title, description, author, copyright, keywords, when and
	 * where it was made (Apple's QuickTime metadata, as phones write it,
	 * with the time's offset), when (MP4's and QuickTime's movie header, in
	 * UTC) and how long
	 */
	private static void video(Metadata metadata, List<Found> found) {
		for (QuickTimeMetadataDirectory qt : metadata.getDirectoriesOfType(QuickTimeMetadataDirectory.class)) {
			String source = "QuickTime ";
			text(qt.getString(QuickTimeMetadataDirectory.TAG_TITLE))
					.ifPresent(t -> found.add(new Found("name", t, Kind.TEXT, source + "title")));
			text(qt.getString(QuickTimeMetadataDirectory.TAG_DESCRIPTION))
					.ifPresent(t -> found.add(new Found("description", t, Kind.TEXT, source + "description")));
			for (int tag : new int[] { QuickTimeMetadataDirectory.TAG_AUTHOR, QuickTimeMetadataDirectory.TAG_ARTIST }) {
				text(qt.getString(tag)).ifPresent(t -> people(t)
						.forEach(person -> found.add(new Found("author", person, Kind.PERSON, source + "author"))));
			}
			text(qt.getString(QuickTimeMetadataDirectory.TAG_COPYRIGHT))
					.ifPresent(t -> found.add(new Found("copyrightNotice", t, Kind.TEXT, source + "copyright")));
			text(qt.getString(QuickTimeMetadataDirectory.TAG_KEYWORDS))
					.ifPresent(t -> found.add(new Found("keywords", t, Kind.KEYWORDS, source + "keywords")));
			quickTimeDate(qt.getString(QuickTimeMetadataDirectory.TAG_CREATION_DATE))
					.ifPresent(d -> found.add(new Found("dateCreated", d, Kind.DATE, source + "creationdate")));
			iso6709(qt.getString(QuickTimeMetadataDirectory.TAG_LOCATION_ISO6709))
					.ifPresent(p -> found.add(new Found("contentLocation", p, Kind.PLACE, source + "location")));
		}
		for (Mp4Directory mp4 : metadata.getDirectoriesOfType(Mp4Directory.class)) {
			text(mp4.getString(Mp4Directory.TAG_TITLE))
					.ifPresent(t -> found.add(new Found("name", t, Kind.TEXT, "MP4 title")));
			movieHeader(mp4.getDate(Mp4Directory.TAG_CREATION_TIME), mp4.getRational(Mp4Directory.TAG_DURATION_SECONDS),
					"MP4 ", found);
		}
		for (QuickTimeDirectory qt : metadata.getDirectoriesOfType(QuickTimeDirectory.class)) {
			movieHeader(qt.getDate(QuickTimeDirectory.TAG_CREATION_TIME),
					qt.getRational(QuickTimeDirectory.TAG_DURATION_SECONDS), "QuickTime ", found);
		}
	}

	private static void movieHeader(Date created, Rational seconds, String source, List<Found> found) {
		if (created != null && created.toInstant().isAfter(UNSET)) {
			found.add(new Found("dateCreated", created.toInstant().truncatedTo(ChronoUnit.SECONDS).toString(),
					Kind.DATE, source + "creation time"));
		}
		duration(seconds).ifPresent(d -> found.add(new Found("duration", d, Kind.TEXT, source + "duration")));
	}

	/**
	 * A duration as ISO 8601, as schema.org's duration is, to the
	 * millisecond, e.g. <code>PT1M2.5S</code>
	 */
	static Optional<String> duration(Rational seconds) {
		if (seconds == null || seconds.getDenominator() == 0 || seconds.getNumerator() <= 0) {
			return Optional.empty();
		}
		long ms = Math.round(seconds.doubleValue() * 1000);
		if (ms <= 0) {
			return Optional.empty();
		}
		long hours = ms / 3_600_000;
		long minutes = ms / 60_000 % 60;
		long rest = ms % 60_000;
		return Optional.of("PT" + (hours > 0 ? hours + "H" : "") + (minutes > 0 ? minutes + "M" : "")
				+ (rest > 0 ? BigDecimal.valueOf(rest, 3).stripTrailingZeros().toPlainString() + "S" : ""));
	}

	/**
	 * QuickTime's creation date, e.g. <code>2026-04-20T15:36:54+0100</code>,
	 * as ISO 8601 with its offset as <code>+01:00</code>
	 */
	static Optional<String> quickTimeDate(String text) {
		return text(text).map(t -> t.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2")).flatMap(EmbeddedMetadata::date);
	}

	/**
	 * A point in ISO 6709, as QuickTime has it, e.g.
	 * <code>+53.4668-002.2339+040.000/</code>, as
	 * <code>latitude, longitude</code>; also with minutes and seconds
	 * (<code>±DDMM.M</code>, <code>±DDMMSS.S</code>)
	 */
	static Optional<String> iso6709(String text) {
		if (text == null) {
			return Optional.empty();
		}
		Matcher m = ISO_6709.matcher(text.strip());
		if (!m.lookingAt()) {
			return Optional.empty();
		}
		OptionalDouble latitude = degrees(m.group(1), m.group(2), m.group(3), 2);
		OptionalDouble longitude = degrees(m.group(4), m.group(5), m.group(6), 3);
		if (latitude.isEmpty() || longitude.isEmpty() || Math.abs(latitude.getAsDouble()) > 90
				|| Math.abs(longitude.getAsDouble()) > 180
				|| latitude.getAsDouble() == 0 && longitude.getAsDouble() == 0) {
			return Optional.empty();
		}
		return Optional.of(String.format(Locale.ROOT, "%.6f, %.6f", latitude.getAsDouble(),
				longitude.getAsDouble()));
	}

	/** Degrees from ISO 6709's DD(D), DD(D)MM or DD(D)MMSS, with a fraction of the last */
	private static OptionalDouble degrees(String sign, String digits, String fraction, int degreeDigits) {
		double last = Double.parseDouble(digits.substring(Math.max(0, digits.length() - 2))
				+ (fraction == null ? "" : fraction));
		double value;
		if (digits.length() == degreeDigits) {
			value = Double.parseDouble(digits + (fraction == null ? "" : fraction));
		} else if (digits.length() == degreeDigits + 2) {
			value = Integer.parseInt(digits.substring(0, degreeDigits)) + last / 60;
		} else if (digits.length() == degreeDigits + 4) {
			value = Integer.parseInt(digits.substring(0, degreeDigits))
					+ Integer.parseInt(digits.substring(degreeDigits, degreeDigits + 2)) / 60.0 + last / 3600;
		} else {
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(sign.equals("-") ? -value : value);
	}

	/**
	 * The image's or video's size in pixels, as schema.org's Distance (e.g.
	 * <code>605 px</code>), from its format's own header: JPEG's frame,
	 * PNG's IHDR, WebP's, GIF's, BMP's, PSD's, the video track's, EPS's,
	 * else EXIF's: the image's size (as raw formats, whose first image may
	 * be a preview, and HEIC and AVIF have it: HEIF's own is that of the
	 * coded image, before it is cropped and turned), else TIFF's (IFD0)
	 */
	private static void size(Metadata metadata, List<Found> found) {
		List<Map.Entry<Class<? extends Directory>, int[]>> sources = List.of(
				Map.entry(JpegDirectory.class, new int[] { JpegDirectory.TAG_IMAGE_WIDTH, JpegDirectory.TAG_IMAGE_HEIGHT }),
				Map.entry(PngDirectory.class, new int[] { PngDirectory.TAG_IMAGE_WIDTH, PngDirectory.TAG_IMAGE_HEIGHT }),
				Map.entry(WebpDirectory.class, new int[] { WebpDirectory.TAG_IMAGE_WIDTH, WebpDirectory.TAG_IMAGE_HEIGHT }),
				Map.entry(GifHeaderDirectory.class,
						new int[] { GifHeaderDirectory.TAG_IMAGE_WIDTH, GifHeaderDirectory.TAG_IMAGE_HEIGHT }),
				Map.entry(BmpHeaderDirectory.class,
						new int[] { BmpHeaderDirectory.TAG_IMAGE_WIDTH, BmpHeaderDirectory.TAG_IMAGE_HEIGHT }),
				Map.entry(PsdHeaderDirectory.class,
						new int[] { PsdHeaderDirectory.TAG_IMAGE_WIDTH, PsdHeaderDirectory.TAG_IMAGE_HEIGHT }),
				Map.entry(Mp4VideoDirectory.class,
						new int[] { Mp4VideoDirectory.TAG_WIDTH, Mp4VideoDirectory.TAG_HEIGHT }),
				Map.entry(QuickTimeVideoDirectory.class,
						new int[] { QuickTimeVideoDirectory.TAG_WIDTH, QuickTimeVideoDirectory.TAG_HEIGHT }),
				Map.entry(EpsDirectory.class, new int[] { EpsDirectory.TAG_IMAGE_WIDTH, EpsDirectory.TAG_IMAGE_HEIGHT }),
				Map.entry(ExifSubIFDDirectory.class,
						new int[] { ExifDirectoryBase.TAG_EXIF_IMAGE_WIDTH, ExifDirectoryBase.TAG_EXIF_IMAGE_HEIGHT }),
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
		} else {
			// A video's, as phones write it
			QuickTimeMetadataDirectory qt = metadata.getFirstDirectoryOfType(QuickTimeMetadataDirectory.class);
			if (qt != null) {
				String make = text(qt.getString(QuickTimeMetadataDirectory.TAG_MAKE)).orElse("");
				String model = text(qt.getString(QuickTimeMetadataDirectory.TAG_MODEL)).orElse("");
				text(model.toLowerCase(Locale.ROOT).startsWith(make.toLowerCase(Locale.ROOT)) ? model
						: (make + " " + model).strip()).ifPresent(parts::add);
			}
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
		Matcher m = Pattern.compile("(\\d{4}):(\\d{2}):(\\d{2}) (\\d{2}:\\d{2}:\\d{2})")
				.matcher(text.strip());
		if (!m.matches() || m.group(1).compareTo("1900") < 0) {
			return Optional.empty();
		}
		String zone = offset != null && offset.strip().matches("[+-]\\d{2}:\\d{2}") ? offset.strip() : "";
		return Optional.of(m.group(1) + "-" + m.group(2) + "-" + m.group(3) + "T" + m.group(4) + zone);
	}
}
