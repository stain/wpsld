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

import java.net.URI;
import java.util.function.Consumer;

import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;

import javafx.scene.control.Hyperlink;
import javafx.scene.text.TextFlow;

/** Renders the help's Markdown as native JavaFX nodes. */
final class HelpMarkdown {
	private static final Parser PARSER = Parser.builder().build();
	private static final TextContentRenderer PLAIN = TextContentRenderer.builder().build();

	private HelpMarkdown() {
	}

	static void show(TextFlow flow, String markdown, Consumer<String> browse) {
		flow.getChildren().clear();
		Node document = PARSER.parse(markdown);
		flow.setAccessibleText(PLAIN.render(document));
		blocks(flow, document, browse, 0);
	}

	private static void blocks(TextFlow flow, Node parent, Consumer<String> browse, int depth) {
		for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
			if (node instanceof Heading) {
				inline(flow, node, browse, "-fx-font-weight: bold; -fx-font-size: 1.25em;");
				text(flow, "\n\n", "");
			} else if (node instanceof Paragraph) {
				inline(flow, node, browse, "");
				text(flow, "\n\n", "");
			} else if (node instanceof BulletList || node instanceof OrderedList) {
				int number = node instanceof OrderedList list && list.getMarkerStartNumber() != null
						? list.getMarkerStartNumber() : 1;
				for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
					String marker = node instanceof OrderedList ? number++ + ". " : "• ";
					text(flow, "  ".repeat(depth) + marker, "");
					blocks(flow, item, browse, depth + 1);
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
				blocks(flow, node, browse, depth);
			}
		}
	}

	private static void inline(TextFlow flow, Node parent, Consumer<String> browse, String style) {
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
				inline(flow, node, browse, style + "-fx-font-weight: bold;");
			} else if (node instanceof Emphasis) {
				inline(flow, node, browse, style + "-fx-font-style: italic;");
			} else if (node instanceof Link link && isWebLink(link.getDestination())) {
				StringBuilder label = new StringBuilder();
				for (Node child = link.getFirstChild(); child != null; child = child.getNext()) {
					label.append(PLAIN.render(child));
				}
				Hyperlink hyperlink = new Hyperlink(label.toString().strip());
				hyperlink.setOnAction(e -> browse.accept(link.getDestination()));
				flow.getChildren().add(hyperlink);
			} else if (node instanceof HtmlInline html) {
				text(flow, html.getLiteral(), style);
			} else {
				inline(flow, node, browse, style);
			}
		}
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
		text.setStyle(style);
		flow.getChildren().add(text);
	}
}
