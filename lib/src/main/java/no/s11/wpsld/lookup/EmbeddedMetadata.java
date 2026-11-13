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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.lang.KeyValuePair;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.png.PngDirectory;
import com.drew.metadata.xmp.XmpDirectory;

/**
 * The metadata a file carries in it, to offer for its File entity:
 * <ul>
 * <li>Office Open XML files' (Word, Excel, PowerPoint: <code>.docx</code>,
 * <code>.xlsx</code>, <code>.pptx</code>) document properties, and
 * OpenDocument files' (LibreOffice and OpenOffice: <code>.odt</code>,
 * <code>.ods</code>, <code>.odp</code>, …, and the older <code>.sxw</code>
 * and friends). Both are ZIP files, read with the JDK's zip file system.
 * <li>PDFs' XMP ({@link #xmp}), for ui to read their document information
 * with PDFBox, which needs java.desktop.
 * <li>Images' (JPEG, PNG, TIFF, WebP) EXIF, IPTC, XMP and PNG text, with
 * metadata-extractor, and where they were taken (GPS).
 * <li>SVG's RDF metadata, as Inkscape writes it, and its title and
 * description.
 * </ul>
 * XMP and SVG's metadata are RDF with Dublin Core, Creative Commons' and
 * Adobe's terms, read alike. What files say is untrusted: only small
 * parts are read, as XML without external entities or DTDs.
 */
public final class EmbeddedMetadata {
	/** Office's core properties: Dublin Core and a few of its own */
	public static final String OFFICE_CORE = "docProps/core.xml";
	/** Office's custom properties, where a licence may be */
	public static final String OFFICE_CUSTOM = "docProps/custom.xml";
	/** OpenDocument's metadata, also of OpenOffice 1's formats */
	public static final String OPEN_DOCUMENT_META = "meta.xml";
	/** The most read of a part, against ZIP bombs */
	private static final int MAX_PART = 1 << 20;
	/** Custom properties that name a licence */
	private static final Pattern LICENSE_NAME = Pattern.compile("(?i)licen[cs]e|rights|usage ?terms");
	private static final byte[] ZIP = { 'P', 'K', 3, 4 };
	private static final byte[] RIFF = { 'R', 'I', 'F', 'F' };
	/** JPEG, PNG, and TIFF both ways round */
	private static final List<byte[]> IMAGES = List.of(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF },
			new byte[] { (byte) 0x89, 'P', 'N', 'G' }, new byte[] { 'I', 'I', 42, 0 }, new byte[] { 'M', 'M', 0, 42 });
	private static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	private static final String SVG = "http://www.w3.org/2000/svg";
	private static final String DC = "http://purl.org/dc/elements/1.1/";
	private static final String CC = "http://creativecommons.org/ns#";
	private static final String XMP = "http://ns.adobe.com/xap/1.0/";
	private static final String XMP_RIGHTS = "http://ns.adobe.com/xap/1.0/rights/";
	private static final String PHOTOSHOP = "http://ns.adobe.com/photoshop/1.0/";
	/** XMP's usual prefixes, as metadata-extractor gives its properties */
	private static final Map<String, String> XMP_PREFIXES = Map.of("dc", DC, "cc", CC, "xmp", XMP, "xmpRights",
			XMP_RIGHTS, "photoshop", PHOTOSHOP);
	/** The prefixes, to say where a value is */
	private static final Map<String, String> TERM_NAMES = Map.of(DC, "dc:", CC, "cc:", XMP, "xmp:", XMP_RIGHTS,
			"xmpRights:", PHOTOSHOP, "photoshop:");

	/** What the value is, to write it as the property needs */
	public enum Kind {
		/** Text as it is */
		TEXT,
		/** An ISO 8601 date or date and time */
		DATE,
		/** A person's name, to link to a Person of that name */
		PERSON,
		/** Keywords, separated by commas */
		KEYWORDS,
		/** A licence: a URL, an SPDX identifier, or its name */
		LICENSE,
		/** Where, as <code>latitude, longitude</code> in decimal degrees */
		PLACE
	}

	/**
	 * A value found in a file
	 *
	 * @param property The property it is for, e.g. <code>dateCreated</code>
	 * @param value    The value as found, trimmed
	 * @param kind     What it is
	 * @param source   Where in the file, e.g.
	 *                 <code>docProps/core.xml dcterms:created</code>
	 */
	public record Found(String property, String value, Kind kind, String source) {
	}

	private EmbeddedMetadata() {
	}

	/**
	 * The metadata of an Office or OpenDocument file, an image or an SVG,
	 * by what it starts with, in the order found, each value once; none
	 * for other files, or files that can't be read.
	 */
	public static List<Found> read(Path file) {
		byte[] head;
		try (InputStream in = Files.newInputStream(file)) {
			head = in.readNBytes(1024);
		} catch (IOException e) {
			return List.of();
		}
		List<Found> found = new ArrayList<>();
		try {
			if (startsWith(head, ZIP)) {
				try (FileSystem zip = FileSystems.newFileSystem(file)) {
					part(zip, OFFICE_CORE).ifPresent(core -> officeCore(core, found));
					part(zip, OFFICE_CUSTOM).ifPresent(custom -> officeCustom(custom, found));
					part(zip, OPEN_DOCUMENT_META).ifPresent(meta -> openDocument(meta, found));
				}
			} else if (IMAGES.stream().anyMatch(image -> startsWith(head, image))
					|| startsWith(head, RIFF) && new String(head, 8, 4, StandardCharsets.ISO_8859_1).equals("WEBP")) {
				image(file, found);
			} else if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".svg")
					|| new String(head, StandardCharsets.UTF_8).contains("<svg")) {
				svg(file, found);
			}
		} catch (IOException | RuntimeException e) {
			// Not what it started like after all, or broken: nothing to tell
			return List.of();
		}
		return found.stream().filter(distinct()).toList();
	}

	/** Each property's value once, the first found */
	private static java.util.function.Predicate<Found> distinct() {
		Set<List<String>> seen = new HashSet<>();
		return f -> seen.add(List.of(f.property(), f.value()));
	}

	private static boolean startsWith(byte[] head, byte[] start) {
		return head.length >= start.length && Arrays.equals(head, 0, start.length, start, 0, start.length);
	}

	/** A part of the ZIP as XML, if it has it and it is small enough */
	private static Optional<Document> part(FileSystem zip, String name) throws IOException {
		Path path = zip.getPath(name);
		if (!Files.isRegularFile(path)) {
			return Optional.empty();
		}
		try (InputStream in = Files.newInputStream(path)) {
			return xml(in, false);
		}
	}

	/**
	 * XML, if it is small enough (at most {@link #MAX_PART}): without a
	 * DOCTYPE, or for SVG with one but no external DTD or entities, and
	 * internal entities limited by secure processing
	 */
	private static Optional<Document> xml(InputStream in, boolean doctype) throws IOException {
		byte[] bytes = in.readNBytes(MAX_PART + 1);
		if (bytes.length > MAX_PART) {
			return Optional.empty();
		}
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", !doctype);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setExpandEntityReferences(false);
			factory.setXIncludeAware(false);
			return Optional.of(factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)));
		} catch (ParserConfigurationException | SAXException e) {
			return Optional.empty();
		}
	}

	/**
	 * What XMP says (e.g. a PDF's, read in ui with PDFBox, which needs
	 * java.desktop), as RDF/XML without a DOCTYPE
	 *
	 * @param source Where it is, e.g. "PDF XMP "
	 */
	public static List<Found> xmp(InputStream in, String source) throws IOException {
		List<Found> found = new ArrayList<>();
		xml(in, false).ifPresent(xmp -> rdf(xmp, source, found));
		return found;
	}

	/** Several authors in one, e.g. "Ada Lovelace; Charles Babbage" */
	public static List<String> people(String names) {
		return Arrays.stream(names.split("\\s*;\\s*")).map(String::strip).filter(n -> !n.isEmpty()).toList();
	}

	private static Optional<String> text(String text) {
		return Optional.ofNullable(text).map(String::strip).filter(t -> !t.isEmpty());
	}

	/**
	 * An image's EXIF (description, artist, copyright, when taken and
	 * changed, where taken), IPTC (caption, by-line, copyright, keywords),
	 * PNG text and XMP. Not the camera and its settings, which say little
	 * about the image and may identify the camera.
	 */
	private static void image(Path file, List<Found> found) throws IOException {
		Metadata metadata;
		try {
			metadata = ImageMetadataReader.readMetadata(file.toFile());
		} catch (ImageProcessingException e) {
			return;
		}
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
			Map<String, List<String>> values = new LinkedHashMap<>();
			// e.g. dc:creator[1], not dc:title[1]/xml:lang; in the order of
			// their items, as the map has none
			Pattern item = Pattern.compile("(\\w+):(\\w+)(?:\\[(\\d+)\\])?");
			xmp.getXmpProperties().entrySet().stream().map(e -> Map.entry(item.matcher(e.getKey()), e.getValue()))
					.filter(e -> e.getKey().matches() && XMP_PREFIXES.containsKey(e.getKey().group(1))
							&& !e.getValue().isBlank())
					.sorted(java.util.Comparator.comparing((Map.Entry<java.util.regex.Matcher, String> e) -> e.getKey()
							.group(1) + ":" + e.getKey().group(2))
							.thenComparingInt(e -> e.getKey().group(3) == null ? 0 : Integer.parseInt(e.getKey().group(3))))
					.forEach(e -> values.computeIfAbsent(XMP_PREFIXES.get(e.getKey().group(1)) + e.getKey().group(2),
							k -> new ArrayList<>()).add(e.getValue().strip()));
			values.forEach((term, list) -> term(term, list, "XMP ", found));
		}
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
	static Optional<String> exifDate(String text, String offset) {
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

	/**
	 * An SVG's RDF metadata (as Inkscape writes it: a cc:Work with Dublin
	 * Core and cc:license), and its own title and description
	 */
	private static void svg(Path file, List<Found> found) throws IOException {
		Optional<Document> svg;
		try (InputStream in = Files.newInputStream(file)) {
			svg = xml(in, true);
		}
		if (svg.isEmpty()) {
			return;
		}
		Element root = svg.get().getDocumentElement();
		for (Element e : children(root)) {
			if (SVG.equals(e.getNamespaceURI()) && !text(e).isEmpty()) {
				switch (e.getLocalName()) {
				case "title" -> found.add(new Found("name", text(e), Kind.TEXT, "SVG title"));
				case "desc" -> found.add(new Found("description", text(e), Kind.TEXT, "SVG desc"));
				default -> {
					// Drawing
				}
				}
			}
		}
		rdf(svg.get(), "SVG metadata ", found);
	}

	/**
	 * RDF/XML, of XMP or SVG metadata: each node's properties (as
	 * attributes or elements), their values text, a resource, a container's
	 * items (an Alt's first), or a node's title (e.g. Inkscape's
	 * <code>&lt;dc:creator&gt;&lt;cc:Agent&gt;&lt;dc:title&gt;</code>)
	 */
	private static void rdf(Document document, String source, List<Found> found) {
		NodeList rdfs = document.getElementsByTagNameNS(RDF, "RDF");
		Map<String, List<String>> values = new LinkedHashMap<>();
		for (int i = 0; i < rdfs.getLength(); i++) {
			for (Element node : children((Element) rdfs.item(i))) {
				org.w3c.dom.NamedNodeMap attributes = node.getAttributes();
				for (int a = 0; a < attributes.getLength(); a++) {
					Node attribute = attributes.item(a);
					String ns = attribute.getNamespaceURI();
					if (ns != null && !ns.equals(RDF) && !ns.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)
							&& !ns.equals(XMLConstants.XML_NS_URI)) {
						values.computeIfAbsent(ns + attribute.getLocalName(), k -> new ArrayList<>())
								.add(attribute.getNodeValue().strip());
					}
				}
				for (Element property : children(node)) {
					List<String> list = values.computeIfAbsent(
							property.getNamespaceURI() + property.getLocalName(), k -> new ArrayList<>());
					String resource = property.getAttributeNS(RDF, "resource");
					List<Element> inner = children(property);
					if (!resource.isEmpty()) {
						list.add(resource.strip());
					} else if (inner.isEmpty()) {
						text(property.getTextContent()).ifPresent(list::add);
					} else {
						for (Element value : inner) {
							List<Element> items = children(value).stream()
									.filter(li -> RDF.equals(li.getNamespaceURI()) && li.getLocalName().equals("li"))
									.toList();
							if (!items.isEmpty()) {
								boolean alt = RDF.equals(value.getNamespaceURI()) && value.getLocalName().equals("Alt");
								items.stream().limit(alt ? 1 : Long.MAX_VALUE)
										.flatMap(li -> text(li.getTextContent()).stream()).forEach(list::add);
							} else {
								children(value).stream()
										.filter(t -> DC.equals(t.getNamespaceURI()) && t.getLocalName().equals("title"))
										.findFirst().flatMap(t -> text(t.getTextContent())).ifPresent(list::add);
							}
						}
					}
				}
			}
		}
		values.forEach((term, list) -> {
			if (!list.isEmpty()) {
				term(term, list, source, found);
			}
		});
	}

	/**
	 * What a term of Dublin Core, Creative Commons, XMP or Photoshop says,
	 * with its values
	 */
	private static void term(String term, List<String> values, String source, List<Found> found) {
		String first = values.get(0);
		String name = source + TERM_NAMES.getOrDefault(term.substring(0, term.lastIndexOf(
				term.contains("#") ? '#' : '/') + 1), "") + term.substring(Math.max(term.lastIndexOf('/'),
						term.lastIndexOf('#')) + 1);
		switch (term) {
		case DC + "title" -> found.add(new Found("name", first, Kind.TEXT, name));
		case DC + "description" -> found.add(new Found("description", first, Kind.TEXT, name));
		case DC + "creator" -> values.forEach(v -> found.add(new Found("author", v, Kind.PERSON, name)));
		case DC + "subject" -> found.add(new Found("keywords", String.join(", ", values), Kind.KEYWORDS, name));
		case DC + "rights", XMP_RIGHTS + "UsageTerms" -> found.add(new Found("copyrightNotice", first, Kind.TEXT,
				name));
		case DC + "date", XMP + "CreateDate", PHOTOSHOP + "DateCreated" -> date(first)
				.ifPresent(d -> found.add(new Found("dateCreated", d, Kind.DATE, name)));
		case XMP + "ModifyDate" -> date(first).ifPresent(d -> found.add(new Found("dateModified", d, Kind.DATE, name)));
		case CC + "license", XMP_RIGHTS + "WebStatement" -> found.add(new Found("license", first, Kind.LICENSE, name));
		default -> {
			// Others not offered
		}
		}
	}

	/**
	 * Office's core properties: title, description (else subject), the
	 * creator, keywords, language, and when created and last modified. Not
	 * who last modified it, which says little about the document.
	 */
	private static void officeCore(Document core, List<Found> found) {
		String source = OFFICE_CORE + " ";
		for (Element e : children(core.getDocumentElement())) {
			String text = text(e);
			if (text.isEmpty()) {
				continue;
			}
			switch (e.getLocalName()) {
			case "title" -> found.add(new Found("name", text, Kind.TEXT, source + "dc:title"));
			case "description" -> found.add(new Found("description", text, Kind.TEXT, source + "dc:description"));
			case "subject" -> found.add(new Found("description", text, Kind.TEXT, source + "dc:subject"));
			case "creator" -> found.add(new Found("author", text, Kind.PERSON, source + "dc:creator"));
			case "keywords" -> found.add(new Found("keywords", text, Kind.KEYWORDS, source + "cp:keywords"));
			case "language" -> found.add(new Found("inLanguage", text, Kind.TEXT, source + "dc:language"));
			case "created" -> date(text).ifPresent(d -> found.add(new Found("dateCreated", d, Kind.DATE,
					source + "dcterms:created")));
			case "modified" -> date(text).ifPresent(d -> found.add(new Found("dateModified", d, Kind.DATE,
					source + "dcterms:modified")));
			default -> {
				// Others, e.g. lastModifiedBy and revision, not offered
			}
			}
		}
	}

	/** Office's custom properties: one that names a licence */
	private static void officeCustom(Document custom, List<Found> found) {
		for (Element property : children(custom.getDocumentElement())) {
			String name = property.getAttribute("name");
			String text = text(property);
			if (LICENSE_NAME.matcher(name).find() && !text.isEmpty()) {
				found.add(new Found("license", text, Kind.LICENSE, OFFICE_CUSTOM + " " + name));
			}
		}
	}

	/**
	 * OpenDocument's metadata: title, description (else subject), the
	 * initial creator (dc:creator is who last changed it), keywords,
	 * language, when created and last changed (dc:date), and a
	 * user-defined property that names a licence.
	 */
	private static void openDocument(Document meta, List<Found> found) {
		String source = OPEN_DOCUMENT_META + " ";
		List<String> keywords = new ArrayList<>();
		for (Element office : children(meta.getDocumentElement())) {
			if (!office.getLocalName().equals("meta")) {
				continue;
			}
			for (Element e : children(office)) {
				String text = text(e);
				if (text.isEmpty()) {
					continue;
				}
				switch (e.getLocalName()) {
				case "title" -> found.add(new Found("name", text, Kind.TEXT, source + "dc:title"));
				case "description" -> found.add(new Found("description", text, Kind.TEXT, source + "dc:description"));
				case "subject" -> found.add(new Found("description", text, Kind.TEXT, source + "dc:subject"));
				case "initial-creator" -> found.add(new Found("author", text, Kind.PERSON,
						source + "meta:initial-creator"));
				case "keyword" -> keywords.add(text);
				// OpenOffice 1 wrote them in one, e.g. <meta:keywords><meta:keyword>…
				case "keywords" -> children(e).stream().map(EmbeddedMetadata::text).filter(k -> !k.isEmpty())
						.forEach(keywords::add);
				case "language" -> found.add(new Found("inLanguage", text, Kind.TEXT, source + "dc:language"));
				case "creation-date" -> date(text).ifPresent(d -> found.add(new Found("dateCreated", d, Kind.DATE,
						source + "meta:creation-date")));
				case "date" -> date(text).ifPresent(d -> found.add(new Found("dateModified", d, Kind.DATE,
						source + "dc:date")));
				case "user-defined" -> {
					String name = e.getAttributeNS(e.getNamespaceURI(), "name");
					if (LICENSE_NAME.matcher(name).find()) {
						found.add(new Found("license", text, Kind.LICENSE, source + name));
					}
				}
				default -> {
					// Others, e.g. the generator and statistics, not offered
				}
				}
			}
		}
		if (!keywords.isEmpty()) {
			found.add(new Found("keywords", String.join(", ", keywords), Kind.KEYWORDS, source + "meta:keyword"));
		}
	}

	/**
	 * A date as written in the crate: ISO 8601 to the second, keeping the
	 * time zone if it has one (Office's are UTC, OpenDocument's often have
	 * none), e.g. <code>2026-04-20T15:36:54Z</code>; none if it isn't one.
	 */
	static Optional<String> date(String text) {
		java.util.regex.Matcher m = Pattern
				.compile("(\\d{4}-\\d{2}-\\d{2})(T\\d{2}:\\d{2}(:\\d{2})?)?(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?")
				.matcher(text.strip());
		if (!m.matches()) {
			return Optional.empty();
		}
		// Office writes 1601-01-01 or 0001 for unknown; not dates to offer
		if (m.group(1).compareTo("1900") < 0) {
			return Optional.empty();
		}
		return Optional.of(m.group(1) + (m.group(2) == null ? "" : m.group(2) + (m.group(5) == null ? "" : m.group(5))));
	}

	private static List<Element> children(Element parent) {
		List<Element> elements = new ArrayList<>();
		for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n instanceof Element e && e.getLocalName() != null) {
				elements.add(e);
			}
		}
		return elements;
	}

	private static String text(Element e) {
		return e.getTextContent().strip();
	}
}
