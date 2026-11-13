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
package no.s11.wpsld.form;

import java.util.List;
import java.util.regex.Pattern;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Text values such as descriptions, which crates often write in Markdown
 * (e.g. ROHub's, COMPSs' and a README copied in), as HTML: as Markdown when
 * the text looks like it, else as paragraphs with their line breaks.
 */
public final class Markdown {
	/** Tells of Markdown, each enough on its own */
	private static final List<Pattern> TELLS = List.of(
			// # Heading
			Pattern.compile("(?m)^#{1,6} \\S"),
			// ``` fenced code
			Pattern.compile("(?m)^(```|~~~)"),
			// [text](http://...)
			Pattern.compile("\\[[^\\]\\n]+\\]\\([^)\\s]+\\)"),
			// **bold** or __bold__
			Pattern.compile("(\\*\\*|__)\\S[^\\n]*?\\S\\1"),
			// `code`
			Pattern.compile("`[^`\\n]+`"),
			// |---|---| under a table's header
			Pattern.compile("(?m)^\\|?\\s*:?-{3,}:?\\s*\\|"));
	/** Lines of a list: two or more make it one */
	private static final Pattern LIST_ITEM = Pattern.compile("(?m)^\\s{0,3}([-*+]|\\d+[.)]) \\S");
	/** _italics_ or *italics*: as snake_case words also have underscores, only with another tell or a list */
	private static final Pattern EMPHASIS = Pattern.compile("(?<![\\w*_])([*_])[^\\s*_][^\\n*_]*?(?<=\\S)\\1(?![\\w*_])");

	private static final Parser PARSER = Parser.builder().extensions(List.of(TablesExtension.create())).build();
	/** HTML written in the text is shown as text, and links are only to the web, mail or relative */
	private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(List.of(TablesExtension.create()))
			.escapeHtml(true).sanitizeUrls(true).build();

	private Markdown() {
	}

	/**
	 * Whether a text is likely to be Markdown: a heading, fenced code, a
	 * link, bold, inline code or a table; or several list items, or
	 * emphasis.
	 */
	public static boolean looksLike(String text) {
		if (TELLS.stream().anyMatch(p -> p.matcher(text).find())) {
			return true;
		}
		long items = LIST_ITEM.matcher(text).results().count();
		return items >= 2 || items >= 1 && EMPHASIS.matcher(text).find()
				|| EMPHASIS.matcher(text).results().count() >= 2;
	}

	/**
	 * The text as HTML: rendered as Markdown if it looks like it, else each
	 * paragraph (after a blank line) a <code>&lt;p&gt;</code>, and other line
	 * breaks kept as <code>&lt;br&gt;</code>.
	 */
	public static String toHtml(String text) {
		if (looksLike(text)) {
			return RENDERER.render(PARSER.parse(text));
		}
		StringBuilder html = new StringBuilder();
		for (String paragraph : text.strip().split("\\R\\s*\\R")) {
			html.append("<p>").append(String.join("<br>\n", CratePreview.escape(paragraph.strip()).split("\\R")))
					.append("</p>\n");
		}
		return html.toString();
	}
}
