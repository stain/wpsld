/*
 * Copyright 2026 The University of Manchester, UK
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package no.s11.wpsld.tui;

import static org.junit.Assume.assumeNotNull;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.junit.Test;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;

/**
 * Takes the terminal viewer's screenshots for README.md, from the example
 * crate of the ui module, into the folder named by the environment variable
 * SCREENSHOTS; skipped without it:
 *
 * SCREENSHOTS=$PWD/docs/screenshots ./gradlew :tui:test --tests '*ReadmeScreenshots' --rerun
 *
 * The viewer runs on a virtual terminal; its cells are drawn with Java 2D
 * in a monospaced font, in the colours of a light terminal.
 */
public class ReadmeScreenshots {
	private static final Path EXAMPLE_CRATE = Path.of("..", "ui", "src", "test", "resources", "example-crate");
	private static final Color BACKGROUND = new Color(0xe5, 0xe5, 0xe5);
	private static final Color FOREGROUND = Color.BLACK;

	@Test
	public void screenshots() throws Exception {
		String folder = System.getenv("SCREENSHOTS");
		assumeNotNull(folder);
		Path out = Path.of(folder);
		Files.createDirectories(out);
		CrateSession session = CrateSession.open(EXAMPLE_CRATE);
		DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(100, 24));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (Screen screen = new TerminalScreen(terminal)) {
			screen.startScreen();
			Future<?> task = executor.submit(() -> new Viewer(screen, session).run());
			try {
				await(terminal, "[Enter] Open");
				save(terminal, out.resolve("tui-entities.png"));
				// Alice, the first person
				for (int i = 0; i < 20 && !selectedRow(terminal).contains("#alice"); i++) {
					key(terminal, KeyType.ArrowDown);
				}
				key(terminal, KeyType.Enter);
				await(terminal, "Property");
				save(terminal, out.resolve("tui-properties.png"));
				// Edit the job title
				press(terminal, KeyType.ArrowDown, 3);
				terminal.addInput(new KeyStroke('e', false, false));
				await(terminal, "Apply");
				save(terminal, out.resolve("tui-edit-text.png"));
				key(terminal, KeyType.Escape);
				await(terminal, "Property");
				// Choose another affiliation
				press(terminal, KeyType.ArrowDown, 2);
				terminal.addInput(new KeyStroke('e', false, false));
				await(terminal, "Apply");
				save(terminal, out.resolve("tui-choose-reference.png"));
				key(terminal, KeyType.Escape);
				await(terminal, "Property");
				// Follow the affiliation
				key(terminal, KeyType.Enter);
				await(terminal, "Behind: Alice");
				save(terminal, out.resolve("tui-reference.png"));
				key(terminal, KeyType.Escape);
				key(terminal, KeyType.Escape);
				await(terminal, "[Esc] Quit");
				key(terminal, KeyType.Escape);
				task.get(5, TimeUnit.SECONDS);
			} finally {
				task.cancel(true);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	private static void key(DefaultVirtualTerminal terminal, KeyType key) throws InterruptedException {
		terminal.addInput(new KeyStroke(key));
		Thread.sleep(150);
	}

	private static void press(DefaultVirtualTerminal terminal, KeyType key, int times) throws InterruptedException {
		for (int i = 0; i < times; i++) {
			key(terminal, key);
		}
	}

	private static void await(DefaultVirtualTerminal terminal, String text) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		do {
			if (contents(terminal).contains(text)) {
				Thread.sleep(150);
				return;
			}
			Thread.sleep(20);
		} while (System.nanoTime() < deadline);
		throw new AssertionError("Did not see " + text + " in terminal:\n" + contents(terminal));
	}

	private static String contents(DefaultVirtualTerminal terminal) {
		StringBuilder text = new StringBuilder();
		TerminalSize size = terminal.getTerminalSize();
		for (int row = 0; row < size.getRows(); row++) {
			for (int col = 0; col < size.getColumns(); col++) {
				text.append(terminal.getCharacter(col, row).getCharacterString());
			}
			text.append('\n');
		}
		return text.toString();
	}

	/** The row drawn on a blue background, other than the last (the key bar). */
	private static String selectedRow(DefaultVirtualTerminal terminal) {
		TerminalSize size = terminal.getTerminalSize();
		for (int row = 1; row < size.getRows() - 1; row++) {
			TextCharacter first = terminal.getCharacter(0, row);
			if (first.getBackgroundColor() == TextColor.ANSI.BLUE) {
				StringBuilder text = new StringBuilder();
				for (int col = 0; col < size.getColumns(); col++) {
					text.append(terminal.getCharacter(col, row).getCharacterString());
				}
				return text.toString();
			}
		}
		return "";
	}

	private static Color color(TextColor color, Color byDefault) {
		return color == TextColor.ANSI.DEFAULT ? byDefault : color.toColor();
	}

	private static void save(DefaultVirtualTerminal terminal, Path file) throws Exception {
		Font font = new Font(Font.MONOSPACED, Font.PLAIN, 15);
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		Graphics2D measure = probe.createGraphics();
		FontMetrics metrics = measure.getFontMetrics(font);
		int width = metrics.charWidth('M');
		int height = metrics.getHeight();
		measure.dispose();
		TerminalSize size = terminal.getTerminalSize();
		BufferedImage image = new BufferedImage(size.getColumns() * width, size.getRows() * height,
				BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(BACKGROUND);
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		for (int row = 0; row < size.getRows(); row++) {
			for (int col = 0; col < size.getColumns(); col++) {
				TextCharacter c = terminal.getCharacter(col, row);
				Color fg = color(c.getForegroundColor(), FOREGROUND);
				// Terminals show bold text in the bright variant of a basic colour
				if (c.getModifiers().contains(SGR.BOLD) && c.getForegroundColor() instanceof TextColor.ANSI
						&& c.getForegroundColor() != TextColor.ANSI.DEFAULT) {
					fg = new Color(Math.min(255, fg.getRed() + 85), Math.min(255, fg.getGreen() + 85),
							Math.min(255, fg.getBlue() + 85));
				}
				Color bg = color(c.getBackgroundColor(), BACKGROUND);
				if (c.isReversed()) {
					Color swap = fg;
					fg = bg;
					bg = swap;
				}
				int x = col * width;
				int y = row * height;
				g.setColor(bg);
				g.fillRect(x, y, width, height);
				g.setColor(fg);
				g.setFont(c.getModifiers().contains(SGR.BOLD) ? font.deriveFont(Font.BOLD) : font);
				String s = c.getCharacterString();
				if (!s.isBlank()) {
					g.drawString(s, x, y + metrics.getAscent());
				}
				if (c.getModifiers().contains(SGR.UNDERLINE)) {
					g.drawLine(x, y + height - 2, x + width - 1, y + height - 2);
				}
			}
		}
		g.dispose();
		ImageIO.write(image, "png", file.toFile());
	}
}
