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

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.Label;

/**
 * The bottom line: the keys available in the view, replaced for a moment by
 * a message or by the keys of a dialog
 */
final class Status {
	private Label label = new Label("");
	private String actions = "";

	/** A new line for the view that is being set up, showing these keys */
	Label attach(String actions) {
		this.actions = actions;
		label = new Label(actions).setForegroundColor(TextColor.ANSI.WHITE).setBackgroundColor(TextColor.ANSI.BLUE);
		return label;
	}

	/** The keys of the view changed */
	void show(String actions) {
		this.actions = actions;
		label.setText(actions);
	}

	/** Say something, or the keys of a dialog, until the next {@link #restore()} */
	void say(String text) {
		label.setText(text);
	}

	/** The view's keys again */
	void restore() {
		label.setText(actions);
	}

	String text() {
		return label.getText();
	}
}
