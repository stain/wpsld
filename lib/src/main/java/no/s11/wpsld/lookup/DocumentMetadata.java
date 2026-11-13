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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * The metadata a document carries in it, to offer for its File entity:
 * Office Open XML files' (Word, Excel, PowerPoint: <code>.docx</code>,
 * <code>.xlsx</code>, <code>.pptx</code>) document properties, and
 * OpenDocument files' (LibreOffice and OpenOffice: <code>.odt</code>,
 * <code>.ods</code>, <code>.odp</code>, …, and the older <code>.sxw</code>
 * and friends). Both are ZIP files, read with the JDK's zip file system;
 * what they say is untrusted, so only small parts are read, as XML without
 * DTDs or external entities.
 */
public final class DocumentMetadata {
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
		LICENSE
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

	private DocumentMetadata() {
	}

	/**
	 * The metadata of an Office or OpenDocument file, in the order found;
	 * none for other files, or files that can't be read.
	 */
	public static List<Found> read(Path file) {
		if (!isZip(file)) {
			return List.of();
		}
		List<Found> found = new ArrayList<>();
		try (FileSystem zip = FileSystems.newFileSystem(file)) {
			part(zip, OFFICE_CORE).ifPresent(core -> officeCore(core, found));
			part(zip, OFFICE_CUSTOM).ifPresent(custom -> officeCustom(custom, found));
			part(zip, OPEN_DOCUMENT_META).ifPresent(meta -> openDocument(meta, found));
		} catch (IOException | RuntimeException e) {
			// Not a ZIP after all, or broken: nothing to tell
			return List.of();
		}
		return List.copyOf(found);
	}

	private static boolean isZip(Path file) {
		try (InputStream in = Files.newInputStream(file)) {
			return Arrays.equals(in.readNBytes(ZIP.length), ZIP);
		} catch (IOException e) {
			return false;
		}
	}

	/** A part of the ZIP as XML, if it has it and it is small enough */
	private static Optional<Document> part(FileSystem zip, String name) throws IOException {
		Path path = zip.getPath(name);
		if (!Files.isRegularFile(path)) {
			return Optional.empty();
		}
		byte[] bytes;
		try (InputStream in = Files.newInputStream(path)) {
			bytes = in.readNBytes(MAX_PART + 1);
		}
		if (bytes.length > MAX_PART) {
			return Optional.empty();
		}
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setExpandEntityReferences(false);
			factory.setXIncludeAware(false);
			return Optional.of(factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)));
		} catch (ParserConfigurationException | SAXException e) {
			return Optional.empty();
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
				case "keywords" -> children(e).stream().map(DocumentMetadata::text).filter(k -> !k.isEmpty())
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
