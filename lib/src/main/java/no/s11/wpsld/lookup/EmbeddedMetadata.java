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

/**
 * The metadata a file carries in it, to offer for its File entity:
 * <ul>
 * <li>Office Open XML files' (Word, Excel, PowerPoint: <code>.docx</code>,
 * <code>.xlsx</code>, <code>.pptx</code>) document properties, and
 * OpenDocument files' (LibreOffice and OpenOffice: <code>.odt</code>,
 * <code>.ods</code>, <code>.odp</code>, …, and the older <code>.sxw</code>
 * and friends). Both are ZIP files, read with the JDK's zip file system.
 * <li>SVG's RDF metadata, as Inkscape writes it, and its title and
 * description.
 * </ul>
 * These are the built-in {@link MetadataReader}s ({@link #builtIn()}),
 * needing only the JDK; readers with libraries of their own are modules of
 * their own: images' (no.s11.wpsld.readers.image) and PDFs'
 * (no.s11.wpsld.readers.pdf), which use the helpers here, e.g.
 * {@link #xmp} and {@link #people}. {@link FileMetadata} asks them all.
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
		/**
		 * Whether to give it only on purpose, not chosen to start with nor
		 * filled in for several files: where a photo was taken, and the
		 * camera, which can tell more than meant
		 */
		public boolean onPurpose() {
			return kind == Kind.PLACE || property.equals("exifData");
		}
	}

	private EmbeddedMetadata() {
	}

	/** A reader of what a file starts with */
	@FunctionalInterface
	private interface Read {
		void read(Path file, List<Found> found) throws IOException;
	}

	/** A built-in reader: its name, which files it reads, how */
	private record BuiltIn(String name, java.util.function.BiPredicate<Path, byte[]> test, Read reader)
			implements MetadataReader {
		@Override
		public boolean reads(Path file, byte[] head) {
			return test.test(file, head);
		}

		@Override
		public List<Found> read(Path file) throws IOException {
			List<Found> found = new ArrayList<>();
			reader.read(file, found);
			return found;
		}
	}

	/**
	 * The readers needing only the JDK: Office and OpenDocument files (ZIP
	 * files with their parts), and SVG (by its name, or its start)
	 */
	public static List<MetadataReader> builtIn() {
		return List.of(new BuiltIn("Office and OpenDocument", (file, head) -> startsWith(head, ZIP),
				EmbeddedMetadata::zip),
				new BuiltIn("SVG", (file, head) -> file.getFileName().toString().toLowerCase(Locale.ROOT)
						.endsWith(".svg") || new String(head, StandardCharsets.UTF_8).contains("<svg"),
						EmbeddedMetadata::svg));
	}

	/**
	 * The metadata of an Office or OpenDocument file or an SVG, by the
	 * built-in readers (see {@link #builtIn()}), on this thread, in the
	 * order found, each value once; none for other files, or files that
	 * can't be read. {@link FileMetadata} asks all readers, safely.
	 */
	public static List<Found> read(Path file) {
		byte[] head;
		try (InputStream in = Files.newInputStream(file)) {
			head = in.readNBytes(MetadataReader.HEAD);
		} catch (IOException e) {
			return List.of();
		}
		List<Found> found = new ArrayList<>();
		for (MetadataReader reader : builtIn()) {
			if (reader.reads(file, head)) {
				try {
					found.addAll(reader.read(file));
				} catch (IOException | RuntimeException e) {
					// Not what it started like after all, or broken: nothing to tell
				}
			}
		}
		return withNamedLicences(found).stream().filter(distinct()).toList();
	}

	/** An Office or OpenDocument file's parts with metadata */
	private static void zip(Path file, List<Found> found) throws IOException {
		try (FileSystem zip = FileSystems.newFileSystem(file)) {
			part(zip, OFFICE_CORE).ifPresent(core -> officeCore(core, found));
			part(zip, OFFICE_CUSTOM).ifPresent(custom -> officeCustom(custom, found));
			part(zip, OPEN_DOCUMENT_META).ifPresent(meta -> openDocument(meta, found));
		}
	}

	/**
	 * The values found, and a licence where a text names one (see
	 * {@link SPDXLicenses#named}), e.g. "© 2026 Ada Lovelace, CC BY 4.0" in
	 * a photo's copyright, if no licence found already is that one
	 */
	public static List<Found> withNamedLicences(List<Found> found) {
		SPDXLicenses spdx = SPDXLicenses.bundled();
		Set<String> licences = new HashSet<>();
		found.stream().filter(f -> f.kind() == Kind.LICENSE)
				.forEach(f -> spdx.meant(f.value(), true).or(() -> spdx.byLink(f.value()))
						.or(() -> spdx.named(f.value())).ifPresent(l -> licences.add(l.id())));
		List<Found> all = new ArrayList<>(found);
		for (Found f : found) {
			if (f.property().equals("copyrightNotice")) {
				spdx.named(f.value()).filter(l -> licences.add(l.id())).ifPresent(l -> all.add(
						new Found("license", l.id(), Kind.LICENSE, f.source() + ", which names " + l.id())));
			}
		}
		return all;
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
	 * What XMP says (e.g. a PDF's, as PDFBox gives it), as RDF/XML without
	 * a DOCTYPE
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

	/** Text, trimmed, if there is any */
	public static Optional<String> text(String text) {
		return Optional.ofNullable(text).map(String::strip).filter(t -> !t.isEmpty());
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
		// Its size as drawn, e.g. 210mm, or 800 (pixels); not a percentage
		Pattern length = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(px|mm|cm|in|pt|pc)?");
		for (String side : List.of("width", "height")) {
			java.util.regex.Matcher m = length.matcher(root.getAttribute(side).strip());
			if (m.matches()) {
				found.add(new Found(side, m.group(1) + " " + (m.group(2) == null ? "px" : m.group(2)), Kind.TEXT,
						"SVG " + side));
			}
		}
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
	 * What XMP says, as a flat map of its properties by their usual
	 * prefixes (as metadata-extractor gives them), e.g.
	 * <code>dc:creator[1]</code> (not <code>dc:title[1]/xml:lang</code>),
	 * each list's items in their order
	 *
	 * @param source Where it is, e.g. "XMP "
	 */
	public static List<Found> xmpProperties(Map<String, String> properties, String source) {
		Map<String, List<String>> values = new LinkedHashMap<>();
		Pattern item = Pattern.compile("(\\w+):(\\w+)(?:\\[(\\d+)\\])?");
		properties.entrySet().stream().map(e -> Map.entry(item.matcher(e.getKey()), e.getValue()))
				.filter(e -> e.getKey().matches() && XMP_PREFIXES.containsKey(e.getKey().group(1))
						&& e.getValue() != null && !e.getValue().isBlank())
				.sorted(java.util.Comparator.comparing((Map.Entry<java.util.regex.Matcher, String> e) -> e.getKey()
						.group(1) + ":" + e.getKey().group(2))
						// By number, of any length (untrusted)
						.thenComparing(e -> e.getKey().group(3) == null ? "" : e.getKey().group(3).replaceFirst("^0+", ""),
								java.util.Comparator.comparingInt(String::length).thenComparing(
										java.util.Comparator.naturalOrder())))
				.forEach(e -> values.computeIfAbsent(XMP_PREFIXES.get(e.getKey().group(1)) + e.getKey().group(2),
						k -> new ArrayList<>()).add(e.getValue().strip()));
		List<Found> found = new ArrayList<>();
		values.forEach((term, list) -> term(term, list, source, found));
		return found;
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
	public static Optional<String> date(String text) {
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
