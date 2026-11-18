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

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;

import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.soss.SchemaFromJsonLd;

final class Viewer {
	private final Screen screen;
	private final CrateSession session;
	private final CrateDocument document;
	private ReferencePicker picker;
	private final BasicWindow window = new BasicWindow("wpsld-tui");
	private final ActionListBox entities = new ActionListBox(new TerminalSize(50, 10));
	private final Deque<Place> history = new ArrayDeque<>();
	private boolean showingEntity;
	private Entity shown;
	private Table<String> properties;
	private List<Row> rows;
	private Label status;
	private String actions;

	private record Place(Entity entity, int row, String property, int index) {
	}

	private record Row(String property, int index, JsonNode value) {
	}

	Viewer(Screen screen, CrateSession session) {
		this.screen = screen;
		this.session = session;
		this.document = session.document();
		window.setHints(List.of(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));
		window.addWindowListener(new WindowListenerAdapter() {
			@Override
			public void onInput(Window window, KeyStroke key, AtomicBoolean deliver) {
				status.setText(actions);
				if (key.getKeyType() == KeyType.Escape) {
					deliver.set(false);
					if (showingEntity) {
						if (history.isEmpty()) {
							showEntities();
						} else {
							Place previous = history.pop();
							showEntity(previous.entity(), previous.row());
						}
					} else {
						if (!session.dirty() || message("Unsaved changes",
								"Discard unsaved changes?", MessageDialogButton.Yes, MessageDialogButton.No)
								== MessageDialogButton.Yes) {
							window.close();
						}
					}
				} else if (key.getKeyType() == KeyType.Character && !key.isCtrlDown()
						&& !key.isAltDown() && key.getCharacter() == 's') {
					deliver.set(false);
					save();
				} else if (showingEntity && key.getKeyType() == KeyType.Character
						&& !key.isCtrlDown() && !key.isAltDown() && key.getCharacter() == 'e') {
					deliver.set(false);
					edit();
				} else if (showingEntity && (key.getKeyType() == KeyType.PageUp
						|| key.getKeyType() == KeyType.PageDown)) {
					deliver.set(false);
					navigate(key.getKeyType() == KeyType.PageUp ? -1 : 1);
				} else if (showingEntity && !history.isEmpty() && key.getKeyType() == KeyType.Character
						&& !key.isCtrlDown() && !key.isAltDown() && key.getCharacter() == 'v') {
					deliver.set(false);
					int selected = allEntities().indexOf(shown);
					if (selected >= 0) {
						history.clear();
						entities.setSelectedIndex(selected);
						showEntity(shown, properties.getSelectedRow());
					}
				}
			}
		});
		showEntities();
	}

	private void populateEntities() {
		int selected = entities.getSelectedIndex();
		entities.clearItems();
		List<Entity> all = allEntities();
		for (Entity entity : all) {
			entities.addItem(oneLine(entity.getLabel()) + " [" + oneLine(entity.getId()) + "]",
					() -> showEntity(entity, 0));
		}
		entities.setSelectedIndex(Math.max(0, Math.min(selected, all.size() - 1)));
	}

	private List<Entity> allEntities() {
		List<Entity> all = new ArrayList<>(document.crate().orElseThrow().getEntities());
		document.descriptor().ifPresent(all::add);
		return all;
	}

	private void navigate(int direction) {
		if (history.isEmpty()) {
			List<Entity> all = allEntities();
			int index = all.indexOf(shown);
			int next = index + direction;
			if (index >= 0 && next >= 0 && next < all.size()) {
				entities.setSelectedIndex(next);
				showEntity(all.get(next), 0);
			}
			return;
		}
		Place previous = history.peek();
		List<JsonNode> values = previous.entity().getAll(previous.property());
		if (previous.index() >= values.size()
				|| target(values.get(previous.index()), document).filter(shown::equals).isEmpty()) return;
		for (int index = previous.index() + direction; index >= 0 && index < values.size(); index += direction) {
			Optional<Entity> entity = target(values.get(index), document);
			if (entity.isPresent()) {
				int row = 2;
				for (String property : previous.entity().getValues().keySet()) {
					if (property.equals(previous.property())) break;
					row += previous.entity().getAll(property).size();
				}
				history.pop();
				history.push(new Place(previous.entity(), row + index, previous.property(), index));
				showEntity(entity.get(), 0);
				return;
			}
		}
	}

	void run() {
		new MultiWindowTextGUI(screen, new DefaultWindowManager(), (graphics, gui, rendered) -> {
			if (gui.getTheme().getWindowPostRenderer() != null) {
				gui.getTheme().getWindowPostRenderer().postRender(graphics, gui, rendered);
			}
			TerminalSize size = graphics.getSize();
			if (size.getRows() > 0 && size.getColumns() > 0) {
				graphics.setForegroundColor(TextColor.ANSI.WHITE);
				graphics.setBackgroundColor(TextColor.ANSI.BLUE);
				graphics.fillRectangle(new TerminalPosition(0, size.getRows() - 1),
						new TerminalSize(size.getColumns(), 1), ' ');
				graphics.putString(0, size.getRows() - 1, status.getText());
			}
		}, null).addWindowAndWait(window);
	}

	private void addStatus(Panel panel, String available) {
		actions = available;
		status = new Label(actions).setForegroundColor(TextColor.ANSI.WHITE).setBackgroundColor(TextColor.ANSI.BLUE);
		panel.addComponent(status, BorderLayout.Location.BOTTOM);
	}

	private void popupActions(String text) {
		status.setText(text);
	}

	private MessageDialogButton message(String title, String text, MessageDialogButton... buttons) {
		MessageDialogBuilder builder = new MessageDialogBuilder().setTitle(title).setText(text);
		for (MessageDialogButton button : buttons) builder.addButton(button);
		MessageDialog dialog = builder.build();
		dialog.setCloseWindowWithEscape(true);
		popupActions("[Tab] Select  [Enter] Confirm  [Esc] Cancel");
		try {
			return dialog.showDialog(window.getTextGUI());
		} finally {
			status.setText(actions);
		}
	}

	private void showEntities() {
		showingEntity = false;
		populateEntities();
		Panel panel = new Panel(new BorderLayout());
		panel.addComponent(new Label("wpsld-tui | Entities" + (session.dirty() ? " *" : "")
				+ (document.problems().isEmpty() ? ""
						: " | Read problems: " + document.problems().size() + " (see terminal)")),
				BorderLayout.Location.TOP);
		panel.addComponent(entities, BorderLayout.Location.CENTER);
		addStatus(panel, "[Enter] Open  [s] Save  [Esc] Quit");
		window.setComponent(panel);
		window.setFocusedInteractable(entities);
	}

	private void showEntity(Entity entity, int selectedRow) {
		showingEntity = true;
		shown = entity;
		properties = new Table<>("Property", "Value preview");
		rows = new ArrayList<>();
		List<Optional<Entity>> targets = new ArrayList<>();
		properties.getTableModel().addRow("@id", preview(entity.getId(), 48));
		rows.add(new Row("@id", 0, TextNode.valueOf(entity.getId())));
		targets.add(Optional.empty());
		properties.getTableModel().addRow("@type",
				preview(entity.getTypes().stream().map(Viewer::shortType).collect(Collectors.joining(", ")), 48));
		targets.add(Optional.empty());
		rows.add(new Row("@type", 0, TextNode.valueOf(String.join(", ", entity.getTypes()))));
		entity.getValues().keySet().forEach(name -> {
			List<JsonNode> values = entity.getAll(name);
			for (int i = 0; i < values.size(); i++) {
				JsonNode item = values.get(i);
				properties.getTableModel().addRow(preview(name, 24), preview(valueText(item, document), 48));
				rows.add(new Row(name, i, item));
				targets.add(target(item, document));
			}
		});
		properties.setSelectedRow(Math.min(selectedRow, rows.size() - 1));
		properties.setSelectAction(() -> targets.get(properties.getSelectedRow()).ifPresent(target -> {
			int selected = properties.getSelectedRow();
			Row row = rows.get(selected);
			history.push(new Place(entity, selected, row.property(), row.index()));
			showEntity(target, 0);
		}));
		Panel panel = new Panel(new BorderLayout());
		String behind = history.isEmpty() ? "entity list" : title(history.peek().entity());
		panel.addComponent(new Label(title(entity) + (session.dirty() ? " *" : "")
				+ " | Behind: " + behind),
				BorderLayout.Location.TOP);
		panel.addComponent(properties, BorderLayout.Location.CENTER);
		addStatus(panel, "[Enter] Open  [e] Edit  [s] Save  [Esc] " + (history.isEmpty() ? "Back" : "Close")
				+ "  [PgUp/PgDn] Browse" + (history.isEmpty() ? "" : "  [v] Top"));
		window.setComponent(panel);
		window.setFocusedInteractable(properties);
	}

	private static String title(Entity entity) {
		return preview(entity.getLabel() + " [" + shortId(entity.getId()) + "]", 18);
	}

	static Optional<Entity> target(JsonNode value, CrateDocument document) {
		JsonNode id = value.path("@id");
		if (!value.isObject() || !id.isTextual()) {
			return Optional.empty();
		}
		String identifier = id.asText();
		return document.entity(identifier).or(() -> document.descriptor().filter(e -> document.expandId(e.getId())
				.equals(document.expandId(identifier))));
	}

	private void save() {
		if (!session.dirty()) {
			status.setText("No changes to save.");
			return;
		}
		try {
			session.save();
			if (showingEntity) {
				showEntity(shown, properties.getSelectedRow());
			} else {
				showEntities();
			}
			status.setText("Saved to " + oneLine(session.file().toString()));
		} catch (IOException e) {
			message("Can't save", e.getMessage(), MessageDialogButton.OK);
		}
	}

	private void edit() {
		int rowIndex = properties.getSelectedRow();
		Row row = rows.get(rowIndex);
		if (row.property().startsWith("@")) {
			message("Can't edit", "@id and @type cannot be edited here.",
					MessageDialogButton.OK);
			return;
		}
		if (document.descriptor().filter(e -> e == shown).isPresent() && row.property().equals("about")) {
			message("Can't edit",
					"The descriptor's about identifies the crate root and cannot be changed here.",
					MessageDialogButton.OK);
			return;
		}
		if (row.value().isObject() && row.value().path("@id").isTextual()) {
			if (picker == null) {
				picker = new ReferencePicker(document, new SchemaFromJsonLd());
			}
			popupActions("[Tab] Focus  [Space] Select  [Enter] Apply  [Esc] Cancel");
			try {
				picker.show(window.getTextGUI(), shown, row.property()).ifPresent(values -> {
					shown.setAll(row.property(), values);
					showEntity(shown, rowIndex);
				});
			} finally {
				status.setText(actions);
			}
			return;
		}
		JsonNode text = row.value().isTextual() ? row.value() : row.value().path("@value");
		if (!text.isTextual()) {
			message("Can't edit",
					"Only text and entity references can be edited.", MessageDialogButton.OK);
			return;
		}
		BasicWindow dialog = new BasicWindow("Edit " + row.property());
		dialog.setCloseWindowWithEscape(true);
		Panel panel = new Panel();
		panel.addComponent(new Label("Edit text; Tab to Apply or Cancel"));
		TextBox input = new TextBox(new TerminalSize(60, 6), text.asText(), TextBox.Style.MULTI_LINE);
		panel.addComponent(input);
		panel.addComponent(new Button("Apply", () -> {
			List<JsonNode> values = new ArrayList<>(shown.getAll(row.property()));
			JsonNode changed;
			if (row.value().isTextual()) {
				changed = TextNode.valueOf(input.getText());
			} else {
				ObjectNode copy = row.value().deepCopy();
				copy.put("@value", input.getText());
				changed = copy;
			}
			values.set(row.index(), changed);
			shown.setAll(row.property(), values);
			dialog.close();
			showEntity(shown, rowIndex);
		}));
		panel.addComponent(new Button("Cancel", dialog::close));
		dialog.setComponent(panel);
		dialog.setFocusedInteractable(input);
		popupActions("[Tab] Buttons  [Enter] New line  [Esc] Cancel");
		try {
			window.getTextGUI().addWindowAndWait(dialog);
		} finally {
			status.setText(actions);
		}
	}

	static String valueText(JsonNode value, CrateDocument document) {
		if (value.isArray()) {
			return StreamSupport.stream(value.spliterator(), false).map(v -> valueText(v, document))
					.collect(Collectors.joining(", "));
		}
		if (value.isObject()) {
			JsonNode id = value.path("@id");
			if (id.isTextual()) {
				String identifier = id.asText();
				return target(value, document)
						.map(entity -> reference(identifier, entity)).orElseGet(() -> shortId(identifier));
			}
			if (value.has("@value")) {
				String literal = valueText(value.get("@value"), document);
				return value.path("@language").isTextual() ? literal + " (" + value.get("@language").asText() + ")"
						: literal;
			}
			return value.properties().stream().map(p -> p.getKey() + ": " + valueText(p.getValue(), document))
					.collect(Collectors.joining("; "));
		}
		return value.asText();
	}

	private static String reference(String id, Entity entity) {
		String type = entity.getTypes().stream().map(Viewer::shortType).collect(Collectors.joining(", "));
		String name = entity.get("name").filter(JsonNode::isTextual).map(JsonNode::asText)
				.filter(n -> !n.isBlank()).map(n -> ": " + n).orElse("");
		return shortId(id) + " (" + type + name + ")";
	}

	private static String shortType(String type) {
		String name = shortId(type);
		if (name.startsWith("#") && name.length() > 1) {
			return name.substring(1);
		}
		int colon = name.lastIndexOf(':');
		return colon >= 0 && colon < name.length() - 1 ? name.substring(colon + 1) : name;
	}

	private static String shortId(String id) {
		int hash = id.lastIndexOf('#');
		if (hash >= 0 && hash < id.length() - 1) {
			return id.substring(hash);
		}
		if (id.contains("://")) {
			int end = id.length();
			while (end > 0 && id.charAt(end - 1) == '/') {
				end--;
			}
			int slash = id.lastIndexOf('/', end - 1);
			if (slash > id.indexOf("://") + 2 && slash < end - 1) {
				return id.substring(slash + 1, end) + (end < id.length() ? "/" : "");
			}
		}
		return id;
	}

	static String preview(String text, int limit) {
		String line = oneLine(text);
		int length = line.codePointCount(0, line.length());
		return length <= limit ? line : line.substring(0, line.offsetByCodePoints(0, limit - 3)) + "...";
	}

	private static String oneLine(String text) {
		return text.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]+", " ");
	}
}
