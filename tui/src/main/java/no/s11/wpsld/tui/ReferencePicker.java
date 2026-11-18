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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.CheckBoxList;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.soss.ClassDef;
import no.s11.wpsld.soss.SchemaFromJsonLd;

final class ReferencePicker {
	private final CrateDocument document;
	private final SchemaFromJsonLd schema;
	private final Map<String, ClassDef> classes;

	ReferencePicker(CrateDocument document, SchemaFromJsonLd schema) {
		this.document = document;
		this.schema = schema;
		this.classes = schema.getClasses().values().stream()
				.collect(Collectors.toMap(c -> c.getID().toString(), c -> c));
	}

	private record Range(boolean known, List<ClassDef> classes) {
	}

	private Range range(String property) {
		return document.expand(property).flatMap(iri -> schema.getProperties().values().stream()
				.filter(p -> p.getID().toString().equals(iri)).findFirst())
				.map(p -> new Range(!p.getRangeIncludes().isEmpty(),
						p.getRangeIncludes().stream().filter(c -> !schema.isDataType(c)).toList()))
				.orElseGet(() -> new Range(false, List.of()));
	}

	List<Entity> candidates(String property) {
		Range range = range(property);
		List<Entity> entities = new ArrayList<>(document.crate().orElseThrow().getEntities());
		document.descriptor().ifPresent(entities::add);
		return entities.stream().filter(e -> !range.known() || matches(e, range.classes())).toList();
	}

	private boolean matches(Entity entity, List<ClassDef> range) {
		Set<String> types = document.types(entity);
		return types.stream().anyMatch(type -> range.stream().anyMatch(r -> r.getID().toString().equals(type))
				|| Optional.ofNullable(classes.get(type))
						.map(c -> c.getSelfAndSuperClasses().stream().anyMatch(range::contains)).orElse(false));
	}

	private record Choice(Entity entity) {
		@Override
		public String toString() {
			return entity.getLabel() + " [" + entity.getId() + "]";
		}
	}

	Optional<List<JsonNode>> show(WindowBasedTextGUI gui, Entity entity, String property) {
		List<Entity> candidates = candidates(property);
		Set<Entity> chosen = new LinkedHashSet<>();
		List<JsonNode> original = entity.getAll(property);
		original.forEach(v -> Viewer.target(v, document).filter(candidates::contains).ifPresent(chosen::add));
		BasicWindow dialog = new BasicWindow("Choose " + property);
		dialog.setCloseWindowWithEscape(true);
		Panel panel = new Panel();
		if (!range(property).known()) {
			panel.addComponent(new Label("Unknown entity range: showing all entities."));
		}
		panel.addComponent(new Label("Search ID or name; Tab: list, Space: select"));
		TextBox search = new TextBox(new TerminalSize(50, 1));
		panel.addComponent(search);
		CheckBoxList<Choice> list = new CheckBoxList<>(new TerminalSize(60, 10));
		panel.addComponent(list);
		Runnable remember = () -> {
			for (Choice choice : list.getItems()) {
				if (Boolean.TRUE.equals(list.isChecked(choice))) chosen.add(choice.entity());
				else chosen.remove(choice.entity());
			}
		};
		Runnable refresh = () -> {
			remember.run();
			String query = search.getText().toLowerCase(Locale.ROOT);
			list.clearItems();
			candidates.stream().filter(e -> matchesQuery(e, query))
					.forEach(e -> list.addItem(new Choice(e), chosen.contains(e)));
		};
		search.setTextChangeListener((text, byUser) -> refresh.run());
		boolean[] accepted = { false };
		Runnable apply = () -> {
			remember.run();
			accepted[0] = true;
			dialog.close();
		};
		dialog.addWindowListener(new WindowListenerAdapter() {
			@Override
			public void onInput(Window window, KeyStroke key, AtomicBoolean deliver) {
				if (key.getKeyType() == KeyType.Enter
						&& (window.getFocusedInteractable() == search || window.getFocusedInteractable() == list)) {
					deliver.set(false);
					apply.run();
					return;
				}
				if (window.getFocusedInteractable() != list) return;
				if (key.getKeyType() == KeyType.Character && !key.isCtrlDown() && !key.isAltDown()
						&& key.getCharacter() != ' ') {
					search.setText(search.getText() + key.getCharacter());
					deliver.set(false);
				} else if (key.getKeyType() == KeyType.Backspace && !search.getText().isEmpty()) {
					String text = search.getText();
					search.setText(text.substring(0, text.offsetByCodePoints(text.length(), -1)));
					deliver.set(false);
				}
			}
		});
		refresh.run();
		panel.addComponent(new Label("Selections outside the range or missing from the crate are kept."));
		panel.addComponent(new Button("Apply", apply));
		panel.addComponent(new Button("Cancel", dialog::close));
		dialog.setComponent(panel);
		dialog.setFocusedInteractable(search);
		gui.addWindowAndWait(dialog);
		if (!accepted[0]) return Optional.empty();
		List<JsonNode> result = new ArrayList<>();
		Set<Entity> remaining = new LinkedHashSet<>(chosen);
		for (JsonNode value : original) {
			Optional<Entity> target = Viewer.target(value, document);
			if (target.isPresent() && candidates.contains(target.get())) {
				if (chosen.contains(target.get())) result.add(value);
				remaining.remove(target.get());
			} else {
				result.add(value);
			}
		}
		remaining.forEach(e -> result.add(JsonNodeFactory.instance.objectNode().put("@id", e.getId())));
		return Optional.of(result);
	}

	static boolean matchesQuery(Entity entity, String query) {
		String lower = query.toLowerCase(Locale.ROOT);
		return entity.getId().toLowerCase(Locale.ROOT).contains(lower)
				|| entity.getAll("name").stream()
						.map(v -> v.isTextual() ? v.asText() : v.path("@value").asText(""))
						.anyMatch(name -> name.toLowerCase(Locale.ROOT).contains(lower));
	}
}
