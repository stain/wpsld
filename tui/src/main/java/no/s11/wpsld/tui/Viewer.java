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
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
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
import no.s11.wpsld.crate.Issue;
import no.s11.wpsld.form.CrateCheck;
import no.s11.wpsld.lookup.CrateRepair.Repaired;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * The viewer's window: the entity list, searched and filtered by type, and
 * the properties of one entity, with the keys of each. Dialogs and wording
 * are in their own classes.
 */
final class Viewer {
	private static final String LIST_ACTIONS = "[Enter] Open  [s] Save  [Esc] Quit  [/] Search  [t] Type"
			+ "  [c] Check  [r] Repair";
	private static final String SEARCH_ACTIONS = "[Enter] Open  [Tab] List  [Esc] List";

	private final Screen screen;
	private final CrateSession session;
	private final CrateDocument document;
	private final SchemaFromJsonLd schema = new SchemaFromJsonLd();
	private final TypeFilter typeFilter;
	private final Maintenance maintenance;
	private final Status status = new Status();
	private ReferencePicker picker;
	private final BasicWindow window = new BasicWindow("wpsld-tui");
	private final ActionListBox entities = new ActionListBox(new TerminalSize(50, 10));
	private final TextBox search = new TextBox(new TerminalSize(40, 1));
	private final Deque<Place> history = new ArrayDeque<>();
	private List<Entity> listed = List.of();
	private Optional<TypeFilter.Type> selectedType = Optional.empty();
	private Label typeLabel;
	private boolean showingEntity;
	private Entity shown;
	private Table<String> properties;
	private List<Row> rows;

	private record Place(Entity entity, int row, String property, int index) {
	}

	private record Row(String property, int index, JsonNode value) {
	}

	Viewer(Screen screen, CrateSession session) {
		this.screen = screen;
		this.session = session;
		this.document = session.document();
		this.typeFilter = new TypeFilter(document, schema);
		this.maintenance = new Maintenance(session, schema);
		search.setTextChangeListener((text, byUser) -> populateEntities());
		window.setHints(List.of(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));
		window.addWindowListener(new WindowListenerAdapter() {
			@Override
			public void onInput(Window window, KeyStroke key, AtomicBoolean deliver) {
				Viewer.this.onInput(key, deliver);
			}
		});
		showEntities();
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
				graphics.putString(0, size.getRows() - 1, status.text());
			}
		}, null).addWindowAndWait(window);
	}

	// Keys

	private static boolean isPlain(KeyStroke key) {
		return key.getKeyType() == KeyType.Character && !key.isCtrlDown() && !key.isAltDown();
	}

	private static boolean isChar(KeyStroke key, char c) {
		return isPlain(key) && key.getCharacter() == c;
	}

	private void onInput(KeyStroke key, AtomicBoolean deliver) {
		status.restore();
		if (!showingEntity && window.getFocusedInteractable() == search) {
			onSearchKey(key, deliver);
			return;
		}
		if (key.getKeyType() == KeyType.Escape) {
			deliver.set(false);
			escape();
		} else if (isChar(key, 's')) {
			deliver.set(false);
			save();
		} else if (isChar(key, 'c')) {
			deliver.set(false);
			check();
		} else if (isChar(key, 'r')) {
			deliver.set(false);
			repair();
		} else if (showingEntity) {
			onEntityKey(key, deliver);
		} else {
			onListKey(key, deliver);
		}
	}

	/** In the search field, which also takes the keys of the list it filters */
	private void onSearchKey(KeyStroke key, AtomicBoolean deliver) {
		if (key.getKeyType() == KeyType.Enter) {
			deliver.set(false);
			if (!listed.isEmpty()) entities.runSelectedItem();
		} else if (key.getKeyType() == KeyType.Tab && !listed.isEmpty()) {
			deliver.set(false);
			focusEntities();
		} else if (key.getKeyType() == KeyType.Escape) {
			deliver.set(false);
			if (!listed.isEmpty()) focusEntities();
			else {
				search.setText("");
				if (!listed.isEmpty()) focusEntities();
			}
		}
	}

	/** In the entity list, where other keys search */
	private void onListKey(KeyStroke key, AtomicBoolean deliver) {
		if (isChar(key, '/')) {
			deliver.set(false);
			focusSearch();
		} else if (isChar(key, 't')) {
			deliver.set(false);
			chooseType();
		} else if (isPlain(key)) {
			deliver.set(false);
			search.setText(search.getText() + key.getCharacter());
			search.setCaretPosition(search.getText().length());
			focusSearch();
		} else if (key.getKeyType() == KeyType.Tab) {
			deliver.set(false);
			focusSearch();
		} else if (key.getKeyType() == KeyType.Backspace && !search.getText().isEmpty()) {
			deliver.set(false);
			String text = search.getText();
			search.setText(text.substring(0, text.offsetByCodePoints(text.length(), -1)));
		}
	}

	/** In an entity's properties */
	private void onEntityKey(KeyStroke key, AtomicBoolean deliver) {
		if (isChar(key, 'e')) {
			deliver.set(false);
			edit();
		} else if (key.getKeyType() == KeyType.PageUp || key.getKeyType() == KeyType.PageDown) {
			deliver.set(false);
			navigate(key.getKeyType() == KeyType.PageUp ? -1 : 1);
		} else if (!history.isEmpty() && isChar(key, 'v')) {
			deliver.set(false);
			promote();
		}
	}

	private void escape() {
		if (showingEntity) {
			if (history.isEmpty()) {
				showEntities();
			} else {
				Place previous = history.pop();
				showEntity(previous.entity(), previous.row());
			}
		} else if (!session.dirty() || message("Unsaved changes", "Discard unsaved changes?",
				MessageDialogButton.Yes, MessageDialogButton.No) == MessageDialogButton.Yes) {
			window.close();
		}
	}

	/** Show the entity of a nested view in the main list */
	private void promote() {
		if (allEntities().contains(shown)) {
			history.clear();
			if (!filteredEntities().contains(shown)) {
				selectedType = Optional.empty();
				search.setText("");
			}
			populateEntities();
			entities.setSelectedIndex(listed.indexOf(shown));
			showEntity(shown, properties.getSelectedRow());
		}
	}

	private void chooseType() {
		popup("[Tab] List  [Enter] Choose  [Esc] Cancel",
				() -> new TypePicker(typeFilter).show(window.getTextGUI(), allEntities())).ifPresent(chosen -> {
					selectedType = chosen;
					typeLabel.setText(typeText());
					populateEntities();
					if (listed.isEmpty()) focusSearch();
				});
	}

	// The entity list

	private void populateEntities() {
		int selected = entities.getSelectedIndex();
		Entity previous = selected >= 0 && selected < listed.size() ? listed.get(selected) : null;
		entities.clearItems();
		listed = filteredEntities();
		for (Entity entity : listed) {
			entities.addItem(Values.oneLine(entity.getLabel()) + " [" + Values.oneLine(entity.getId()) + "]",
					() -> showEntity(entity, 0));
		}
		if (!listed.isEmpty()) {
			int index = listed.indexOf(previous);
			entities.setSelectedIndex(index >= 0 ? index : 0);
		}
	}

	private List<Entity> allEntities() {
		List<Entity> all = new ArrayList<>(document.crate().orElseThrow().getEntities());
		document.descriptor().ifPresent(all::add);
		return typeFilter.ordered(all);
	}

	private List<Entity> filteredEntities() {
		return allEntities().stream().filter(e -> selectedType.isEmpty() || typeFilter.matches(e, selectedType.get()))
				.filter(e -> ReferencePicker.matchesQuery(e, search.getText())).toList();
	}

	private String typeText() {
		return "Type: " + selectedType.map(TypeFilter.Type::name).orElse("All");
	}

	private void focusSearch() {
		status.show(SEARCH_ACTIONS);
		window.setFocusedInteractable(search);
	}

	private void focusEntities() {
		status.show(LIST_ACTIONS);
		window.setFocusedInteractable(entities);
	}

	private void showEntities() {
		showingEntity = false;
		populateEntities();
		Panel panel = new Panel(new BorderLayout());
		Panel heading = new Panel();
		heading.addComponent(new Label("wpsld-tui | Entities" + (session.dirty() ? " *" : "")
				+ (document.problems().isEmpty() ? ""
						: " | Read problems: " + document.problems().size() + " (see terminal)")));
		typeLabel = new Label(typeText());
		heading.addComponent(typeLabel);
		heading.addComponent(new Label("Search ID or name:"));
		heading.addComponent(search);
		panel.addComponent(heading, BorderLayout.Location.TOP);
		panel.addComponent(entities, BorderLayout.Location.CENTER);
		panel.addComponent(status.attach(LIST_ACTIONS), BorderLayout.Location.BOTTOM);
		window.setComponent(panel);
		if (listed.isEmpty()) focusSearch();
		else focusEntities();
	}

	// One entity

	private void navigate(int direction) {
		if (history.isEmpty()) {
			List<Entity> all = filteredEntities();
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
				|| Values.target(values.get(previous.index()), document).filter(shown::equals).isEmpty()) return;
		for (int index = previous.index() + direction; index >= 0 && index < values.size(); index += direction) {
			Optional<Entity> entity = Values.target(values.get(index), document);
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

	private void showEntity(Entity entity, int selectedRow) {
		showingEntity = true;
		shown = entity;
		properties = new Table<>("Property", "Value preview");
		rows = new ArrayList<>();
		List<Optional<Entity>> targets = new ArrayList<>();
		properties.getTableModel().addRow("@id", Values.preview(entity.getId(), 48));
		rows.add(new Row("@id", 0, TextNode.valueOf(entity.getId())));
		targets.add(Optional.empty());
		properties.getTableModel().addRow("@type", Values.preview(Values.types(entity), 48));
		targets.add(Optional.empty());
		rows.add(new Row("@type", 0, TextNode.valueOf(String.join(", ", entity.getTypes()))));
		entity.getValues().keySet().forEach(name -> {
			List<JsonNode> values = entity.getAll(name);
			for (int i = 0; i < values.size(); i++) {
				JsonNode item = values.get(i);
				properties.getTableModel().addRow(Values.preview(name, 24),
						Values.preview(Values.valueText(item, document), 48));
				rows.add(new Row(name, i, item));
				targets.add(Values.target(item, document));
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
		String behind = history.isEmpty() ? "entity list" : Values.title(history.peek().entity());
		panel.addComponent(new Label(Values.title(entity) + (session.dirty() ? " *" : "")
				+ " | Behind: " + behind),
				BorderLayout.Location.TOP);
		panel.addComponent(properties, BorderLayout.Location.CENTER);
		panel.addComponent(status.attach("[Enter] Open  [e] Edit  [s] Save  [Esc] "
				+ (history.isEmpty() ? "Back" : "Close") + "  [PgUp/PgDn] Browse"
				+ (history.isEmpty() ? "" : "  [v] Top") + "  [c] Check  [r] Repair"),
				BorderLayout.Location.BOTTOM);
		window.setComponent(panel);
		window.setFocusedInteractable(properties);
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
				picker = new ReferencePicker(document, schema);
			}
			popup("[Tab] Focus  [Space] Select  [Enter] Apply  [Esc] Cancel",
					() -> picker.show(window.getTextGUI(), shown, row.property())).ifPresent(values -> {
						shown.setAll(row.property(), values);
						showEntity(shown, rowIndex);
					});
			return;
		}
		JsonNode text = row.value().isTextual() ? row.value() : row.value().path("@value");
		if (!text.isTextual()) {
			message("Can't edit",
					"Only text and entity references can be edited.", MessageDialogButton.OK);
			return;
		}
		TextDialog.show(window.getTextGUI(), status, row.property(), text.asText()).ifPresent(edited -> {
			List<JsonNode> values = new ArrayList<>(shown.getAll(row.property()));
			JsonNode changed;
			if (row.value().isTextual()) {
				changed = TextNode.valueOf(edited);
			} else {
				ObjectNode copy = row.value().deepCopy();
				copy.put("@value", edited);
				changed = copy;
			}
			values.set(row.index(), changed);
			shown.setAll(row.property(), values);
			showEntity(shown, rowIndex);
		});
	}

	// Saving, checking and repairing

	private void save() {
		if (!session.dirty()) {
			status.say("No changes to save.");
			return;
		}
		try {
			session.save();
			if (showingEntity) {
				showEntity(shown, properties.getSelectedRow());
			} else {
				showEntities();
			}
			status.say("Saved to " + Values.oneLine(session.file().toString()));
		} catch (IOException e) {
			message("Can't save", e.getMessage(), MessageDialogButton.OK);
		}
	}

	/** Check the crate as it is now, and go to the entity of a problem chosen */
	private void check() {
		CrateCheck result = maintenance.check();
		if (result.issues().isEmpty()) {
			message("Check", CheckDialog.profiles(result) + "\n" + CheckDialog.summary(result),
					MessageDialogButton.OK);
			return;
		}
		CheckDialog.show(window.getTextGUI(), status, result).ifPresent(this::goTo);
	}

	private void goTo(Issue issue) {
		Optional<Entity> entity = issue.entity().flatMap(id -> Values.find(id, document));
		if (entity.isEmpty()) {
			status.say("The crate has no entity " + issue.entity().orElse(""));
			return;
		}
		history.clear();
		showEntity(entity.get(), 0);
		issue.property().ifPresent(property -> {
			for (int i = 0; i < rows.size(); i++) {
				if (rows.get(i).property().equals(property)) {
					properties.setSelectedRow(i);
					return;
				}
			}
		});
	}

	/** Show what Repair Crate would do, and do it if the user agrees; then back to the entity list */
	private void repair() {
		List<Repaired> would = maintenance.preview();
		if (would.isEmpty()) {
			message("Repair Crate", "Nothing to repair.", MessageDialogButton.OK);
			return;
		}
		if (!RepairDialog.show(window.getTextGUI(), status,
				would.stream().map(Repaired::toString).collect(Collectors.joining("\n\n")))) {
			return;
		}
		maintenance.repair();
		history.clear();
		showEntities();
		status.say("Repaired; [s] to save, [c] to check");
	}

	// Dialogs

	/** Show a dialog with its keys in the bottom line */
	private <T> T popup(String actions, Supplier<T> dialog) {
		status.say(actions);
		try {
			return dialog.get();
		} finally {
			status.restore();
		}
	}

	private MessageDialogButton message(String title, String text, MessageDialogButton... buttons) {
		MessageDialogBuilder builder = new MessageDialogBuilder().setTitle(title).setText(text);
		for (MessageDialogButton button : buttons) builder.addButton(button);
		MessageDialog dialog = builder.build();
		dialog.setCloseWindowWithEscape(true);
		return popup("[Tab] Select  [Enter] Confirm  [Esc] Cancel", () -> dialog.showDialog(window.getTextGUI()));
	}
}
