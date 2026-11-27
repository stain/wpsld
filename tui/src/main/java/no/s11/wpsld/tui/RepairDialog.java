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

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;

/** What Repair Crate would do, to read and then apply or cancel */
final class RepairDialog {
	private RepairDialog() {
	}

	/** The text with its lines broken at spaces to fit the width, as the text box doesn't wrap */
	static String wrap(String text, int width) {
		StringBuilder wrapped = new StringBuilder();
		for (String line : text.split("\n", -1)) {
			String indent = line.startsWith("• ") ? "  " : "";
			StringBuilder current = new StringBuilder();
			boolean fresh = true;
			for (String word : line.split(" ", -1)) {
				if (!fresh && current.length() + 1 + word.length() > width) {
					wrapped.append(current).append('\n');
					current = new StringBuilder(indent);
					fresh = true;
				}
				if (!fresh) {
					current.append(' ');
				}
				current.append(word);
				fresh = false;
			}
			wrapped.append(current).append('\n');
		}
		return wrapped.toString().stripTrailing();
	}

	/** Whether the user chose Apply */
	static boolean show(WindowBasedTextGUI gui, Status status, String repairs) {
		TerminalSize screen = gui.getScreen().getTerminalSize();
		BasicWindow dialog = new BasicWindow("Repair Crate");
		dialog.setCloseWindowWithEscape(true);
		Panel panel = new Panel();
		panel.addComponent(new Label("Repair Crate would do this; Tab to Apply or Cancel"));
		int width = Math.max(30, Math.min(90, screen.getColumns() - 8));
		TextBox text = new TextBox(new TerminalSize(width, Math.max(3, Math.min(14, screen.getRows() - 12))),
				wrap(repairs, width - 2), TextBox.Style.MULTI_LINE);
		text.setReadOnly(true);
		panel.addComponent(text);
		boolean[] applied = { false };
		panel.addComponent(new Button("Apply", () -> {
			applied[0] = true;
			dialog.close();
		}));
		panel.addComponent(new Button("Cancel", dialog::close));
		dialog.setComponent(panel);
		dialog.setFocusedInteractable(text);
		status.say("[Up/Down] Scroll  [Tab] Buttons  [Esc] Cancel");
		try {
			gui.addWindowAndWait(dialog);
		} finally {
			status.restore();
		}
		return applied[0];
	}
}
