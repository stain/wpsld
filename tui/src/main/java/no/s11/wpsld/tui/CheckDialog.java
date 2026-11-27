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
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;

import no.s11.wpsld.crate.Issue;
import no.s11.wpsld.form.CrateCheck;
import no.s11.wpsld.masp.Profile;

/**
 * The result of a check, as wpsld-cli check words it: the profiles checked
 * against, the problems (required) and warnings, to open the entity of
 */
final class CheckDialog {
	private CheckDialog() {
	}

	/** The one-line result, e.g. "Passed." or "2 problems. 1 warning." */
	static String summary(CrateCheck check) {
		long required = check.issues().stream().filter(Issue::isRequired).count();
		long warnings = check.issues().size() - required;
		return (!check.read() ? "Couldn't be read."
				: check.passed() ? "Passed."
				: required + (required == 1 ? " problem." : " problems."))
				+ (warnings == 0 ? "" : " " + warnings + (warnings == 1 ? " warning." : " warnings."));
	}

	static String profiles(CrateCheck check) {
		return "Profiles: " + (check.profiles().matched().isEmpty() ? "none"
				: String.join(", ", check.profiles().matched().stream().map(CheckDialog::describe).toList()))
				+ (check.profiles().declared().isEmpty() ? " (none declared in conformsTo)" : "");
	}

	private static String describe(Profile profile) {
		return profile.name().orElse(profile.id()) + profile.version().map(v -> " " + v).orElse("");
	}

	static String line(Issue issue) {
		return (issue.isRequired() ? "• " : "Warning: ") + issue.message();
	}

	/** The issue chosen to go to, if any */
	static Optional<Issue> show(WindowBasedTextGUI gui, Status status, CrateCheck check) {
		TerminalSize screen = gui.getScreen().getTerminalSize();
		int width = Math.max(30, Math.min(100, screen.getColumns() - 8));
		int height = Math.max(3, Math.min(14, screen.getRows() - 12));
		BasicWindow dialog = new BasicWindow("Check");
		dialog.setCloseWindowWithEscape(true);
		Panel panel = new Panel();
		panel.addComponent(new Label(Values.preview(profiles(check), width)));
		check.profiles().unknown().forEach((id, why) ->
				panel.addComponent(new Label(Values.preview("Profile not found: " + id + " (" + why + ")", width))));
		panel.addComponent(new Label(summary(check)));
		Issue[] chosen = { null };
		if (!check.issues().isEmpty()) {
			ActionListBox list = new ActionListBox(new TerminalSize(width, Math.min(height, check.issues().size())));
			for (Issue issue : check.issues()) {
				list.addItem(Values.preview(line(issue), width - 2), () -> {
					chosen[0] = issue;
					dialog.close();
				});
			}
			panel.addComponent(list);
			dialog.setFocusedInteractable(list);
		}
		dialog.setComponent(panel);
		status.say("[Enter] Open the entity  [Esc] Close");
		try {
			gui.addWindowAndWait(dialog);
		} finally {
			status.restore();
		}
		return Optional.ofNullable(chosen[0]);
	}
}
