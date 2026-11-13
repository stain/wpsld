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
package no.s11.wpsld.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.control.Hyperlink;
import javafx.scene.image.ImageView;
import javafx.scene.text.TextFlow;

/** Renders the help's Markdown as native JavaFX nodes. */
final class HelpMarkdown {
	private static final Parser PARSER = Parser.builder().build();
	private static final TextContentRenderer PLAIN = TextContentRenderer.builder().build();

	private HelpMarkdown() {
	}

	/**
	 * Show a help page's Markdown in a text flow
	 *
	 * @return Its headings, by their names in links to them (see
	 *         {@link #anchor}), to scroll to
	 */
	static Map<String, javafx.scene.Node> show(TextFlow flow, String markdown, Consumer<String> browse,
			ObservableDoubleValue imageWidth) {
		flow.getChildren().clear();
		Node document = PARSER.parse(markdown);
		flow.setAccessibleText(PLAIN.render(document));
		Map<String, javafx.scene.Node> headings = new LinkedHashMap<>();
		blocks(flow, document, browse, imageWidth, 0, headings);
		return headings;
	}

	/**
	 * A heading's name in links to it, as GitHub makes it: in lower case,
	 * without punctuation, spaces as hyphens, e.g. attached-and-detached-crates
	 */
	static String anchor(String heading) {
		return heading.strip().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} _-]", "").replace(' ', '-');
	}

	/** Whether a link leads to a help page, or a section of one (#…) */
	static boolean isHelpLink(String destination) {
		String page = destination.contains("#") ? destination.substring(0, destination.indexOf('#')) : destination;
		return page.isEmpty() && destination.length() > 1 || HelpDialog.Topic.fromResource(page).isPresent();
	}

	private static void blocks(TextFlow flow, Node parent, Consumer<String> browse,
			ObservableDoubleValue imageWidth, int depth, Map<String, javafx.scene.Node> headings) {
		for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
			if (node instanceof Heading) {
				int first = flow.getChildren().size();
				inline(flow, node, browse, imageWidth, "-fx-font-weight: bold; -fx-font-size: 1.25em;");
				if (flow.getChildren().size() > first) {
					headings.putIfAbsent(anchor(PLAIN.render(node)), flow.getChildren().get(first));
				}
				text(flow, "\n\n", "");
			} else if (node instanceof Paragraph) {
				inline(flow, node, browse, imageWidth, "");
				text(flow, "\n\n", "");
			} else if (node instanceof BulletList || node instanceof OrderedList) {
				int number = node instanceof OrderedList list && list.getMarkerStartNumber() != null
						? list.getMarkerStartNumber() : 1;
				for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
					String marker = node instanceof OrderedList ? number++ + ". " : "• ";
					text(flow, "  ".repeat(depth) + marker, "");
					blocks(flow, item, browse, imageWidth, depth + 1, headings);
				}
			} else if (node instanceof FencedCodeBlock code) {
				text(flow, code.getLiteral() + "\n", "-fx-font-family: monospace;");
			} else if (node instanceof IndentedCodeBlock code) {
				text(flow, code.getLiteral() + "\n", "-fx-font-family: monospace;");
			} else if (node instanceof ThematicBreak) {
				text(flow, "────────\n\n", "");
			} else if (node instanceof HtmlBlock html) {
				text(flow, html.getLiteral() + "\n", "");
			} else {
				blocks(flow, node, browse, imageWidth, depth, headings);
			}
		}
	}

	private static void inline(TextFlow flow, Node parent, Consumer<String> browse,
			ObservableDoubleValue imageWidth, String style) {
		for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
			if (node instanceof org.commonmark.node.Text value) {
				text(flow, value.getLiteral(), style);
			} else if (node instanceof Code code) {
				text(flow, code.getLiteral(), style + "-fx-font-family: monospace;");
			} else if (node instanceof SoftLineBreak) {
				text(flow, " ", style);
			} else if (node instanceof HardLineBreak) {
				text(flow, "\n", style);
			} else if (node instanceof StrongEmphasis) {
				inline(flow, node, browse, imageWidth, style + "-fx-font-weight: bold;");
			} else if (node instanceof Emphasis) {
				inline(flow, node, browse, imageWidth, style + "-fx-font-style: italic;");
			} else if (node instanceof org.commonmark.node.Image image) {
				image(flow, image, imageWidth);
			} else if (node instanceof Link link && (isWebLink(link.getDestination())
					|| isHelpLink(link.getDestination()))) {
				Hyperlink hyperlink = new Hyperlink(plainChildren(link));
				hyperlink.setOnAction(e -> browse.accept(link.getDestination()));
				flow.getChildren().add(hyperlink);
			} else if (node instanceof HtmlInline html) {
				text(flow, html.getLiteral(), style);
			} else {
				inline(flow, node, browse, imageWidth, style);
			}
		}
	}

	/** Images are relative to the help folder and never loaded from the network. */
	static boolean imagePath(String path) {
		return path.startsWith("images/") && path.matches("images/[A-Za-z0-9._/-]+")
				&& Arrays.stream(path.split("/", -1)).noneMatch(s -> s.isEmpty() || s.equals(".") || s.equals(".."));
	}

	private static void image(TextFlow flow, org.commonmark.node.Image node, ObservableDoubleValue imageWidth) {
		text(flow, "\n", "");
		String path = node.getDestination();
		String alt = plainChildren(node);
		if (!imagePath(path)) {
			unavailable(flow, alt, "invalid path");
			return;
		}
		try (InputStream in = HelpMarkdown.class.getResourceAsStream("help/" + path)) {
			if (in == null) {
				unavailable(flow, alt, "missing " + path);
				return;
			}
			javafx.scene.image.Image image = new javafx.scene.image.Image(in);
			if (image.isError()) {
				unavailable(flow, alt, "cannot read " + path);
				return;
			}
			ImageView view = new ImageView(image);
			view.setAccessibleText(alt);
			view.setPreserveRatio(true);
			view.fitWidthProperty().bind(Bindings.createDoubleBinding(
					() -> Math.max(1, Math.min(image.getWidth(), imageWidth.get())), imageWidth));
			flow.getChildren().add(view);
		} catch (IOException e) {
			unavailable(flow, alt, e.toString());
		}
	}

	private static void unavailable(TextFlow flow, String alt, String reason) {
		String message = "[Image unavailable: " + alt + " (" + reason + ")]";
		text(flow, message, "");
		flow.setAccessibleText(flow.getAccessibleText() + "\n" + message);
	}

	private static String plainChildren(Node node) {
		StringBuilder text = new StringBuilder();
		for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
			text.append(PLAIN.render(child));
		}
		return text.toString().strip();
	}

	private static boolean isWebLink(String destination) {
		try {
			URI uri = URI.create(destination);
			return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
					&& uri.getHost() != null;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static void text(TextFlow flow, String value, String style) {
		javafx.scene.text.Text text = new javafx.scene.text.Text(value);
		text.getStyleClass().add("help-text");
		text.setStyle(style);
		flow.getChildren().add(text);
	}
}
