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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Locale;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import no.s11.wpsld.crate.Entity;

final class TypePicker {
	private final TypeFilter filter;

	TypePicker(TypeFilter filter) {
		this.filter = filter;
	}

	// Outer Optional is empty on cancellation; inner empty means all types.
	Optional<Optional<TypeFilter.Type>> show(WindowBasedTextGUI gui, List<Entity> entities) {
		BasicWindow dialog = new BasicWindow("Filter by type");
		dialog.setCloseWindowWithEscape(true);
		Panel panel = new Panel();
		panel.addComponent(new Label("Search types; Tab to list"));
		TextBox search = new TextBox(new TerminalSize(50, 1));
		panel.addComponent(search);
		ActionListBox list = new ActionListBox(new TerminalSize(60, 12));
		panel.addComponent(list);
		AtomicReference<Optional<Optional<TypeFilter.Type>>> result = new AtomicReference<>(Optional.empty());
		Runnable refresh = () -> {
			list.clearItems();
			if (search.getText().isBlank()) {
				list.addItem("All types", () -> {
					result.set(Optional.of(Optional.empty()));
					dialog.close();
				});
			}
			int firstMatch = -1;
			for (TypeFilter.Choice choice : filter.choices(entities, search.getText())) {
				if (firstMatch < 0 && choice.type().name().toLowerCase(Locale.ROOT)
						.contains(search.getText().toLowerCase(Locale.ROOT))) firstMatch = list.getItemCount();
				list.addItem("  ".repeat(choice.depth()) + choice.type().name(), () -> {
					result.set(Optional.of(Optional.of(choice.type())));
					dialog.close();
				});
			}
			list.setSelectedIndex(search.getText().isBlank() ? 0 : Math.max(0, firstMatch));
		};
		search.setTextChangeListener((text, byUser) -> refresh.run());
		dialog.addWindowListener(new WindowListenerAdapter() {
			@Override
			public void onInput(Window window, KeyStroke key, AtomicBoolean deliver) {
				if (window.getFocusedInteractable() == search && key.getKeyType() == KeyType.Enter) {
					deliver.set(false);
					if (!list.isEmpty()) list.runSelectedItem();
				} else if (window.getFocusedInteractable() == list && key.getKeyType() == KeyType.Character
						&& !key.isCtrlDown() && !key.isAltDown()) {
					search.setText(search.getText() + key.getCharacter());
					deliver.set(false);
				} else if (window.getFocusedInteractable() == list && key.getKeyType() == KeyType.Backspace
						&& !search.getText().isEmpty()) {
					String text = search.getText();
					search.setText(text.substring(0, text.offsetByCodePoints(text.length(), -1)));
					deliver.set(false);
				}
			}
		});
		refresh.run();
		dialog.setComponent(panel);
		dialog.setFocusedInteractable(search);
		gui.addWindowAndWait(dialog);
		return result.get();
	}
}
