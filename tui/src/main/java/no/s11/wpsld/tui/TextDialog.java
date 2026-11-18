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

import java.util.Optional;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;

/** A dialog to edit a text, over several lines */
final class TextDialog {
	private TextDialog() {
	}

	/** The text as edited, if the user chose Apply */
	static Optional<String> show(WindowBasedTextGUI gui, Status status, String property, String text) {
		BasicWindow dialog = new BasicWindow("Edit " + property);
		dialog.setCloseWindowWithEscape(true);
		Panel panel = new Panel();
		panel.addComponent(new Label("Edit text; Tab to Apply or Cancel"));
		TextBox input = new TextBox(new TerminalSize(60, 6), text, TextBox.Style.MULTI_LINE);
		panel.addComponent(input);
		boolean[] applied = { false };
		panel.addComponent(new Button("Apply", () -> {
			applied[0] = true;
			dialog.close();
		}));
		panel.addComponent(new Button("Cancel", dialog::close));
		dialog.setComponent(panel);
		dialog.setFocusedInteractable(input);
		status.say("[Tab] Buttons  [Enter] New line  [Esc] Cancel");
		try {
			gui.addWindowAndWait(dialog);
		} finally {
			status.restore();
		}
		return applied[0] ? Optional.of(input.getText()) : Optional.empty();
	}
}
