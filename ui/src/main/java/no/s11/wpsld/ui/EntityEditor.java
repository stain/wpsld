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
package no.s11.wpsld.ui;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.FormField;
import no.s11.wpsld.form.FormFields;
import no.s11.wpsld.form.ValueKind;
import no.s11.wpsld.soss.ClassDef;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Edits one {@link Entity} of a crate: its type, chosen from a tree of
 * schema.org types, and a form of its properties.
 * <p>
 * Which fields the form shows is decided by {@link FormFields}; for now every
 * schema.org property of the type, later narrowed down by a profile. The
 * form reads from and writes to the entity, so changing the type keeps the
 * values; those the new type doesn't have are listed separately.
 */
public class EntityEditor extends SplitPane {
	private static final String S = "http://schema.org/";

	private final SchemaFromJsonLd schema;
	private final FormFields formFields;
	/** Identifiers of the other entities in the crate, to suggest as references */
	private final Supplier<List<String>> entityIds;
	/** Called after the entity is changed */
	private final Runnable onChange;

	private final TreeView<ClassDef> types;
	private final VBox form = new VBox(8);
	private Entity entity;
	/** True while selecting the entity's type in the tree, rather than the user changing it */
	private boolean selectingType;

	public EntityEditor(SchemaFromJsonLd schema, FormFields formFields, Supplier<List<String>> entityIds,
			Runnable onChange) {
		this.schema = schema;
		this.formFields = formFields;
		this.entityIds = entityIds;
		this.onChange = onChange;
		this.types = typeTree();
		form.setPadding(new Insets(10));
		ScrollPane formScroll = new ScrollPane(form);
		formScroll.setFitToWidth(true);
		getItems().setAll(types, formScroll);
		setDividerPositions(0.3);
	}

	/**
	 * Show and edit an entity.
	 */
	public void edit(Entity entity) {
		this.entity = Objects.requireNonNull(entity);
		selectType();
		showForm();
	}

	private Optional<ClassDef> classDef(String typeName) {
		return Optional.ofNullable(schema.getClasses().get(URI.create(S + typeName)));
	}

	private TreeView<ClassDef> typeTree() {
		ClassDef thing = classDef("Thing").orElseThrow();
		TreeView<ClassDef> tree = new TreeView<>(new ClassTreeItem(thing));
		tree.setCellFactory(view -> new TreeCell<>() {
			@Override
			protected void updateItem(ClassDef classDef, boolean empty) {
				super.updateItem(classDef, empty);
				setText(empty || classDef == null ? null : classDef.getName());
				setTooltip(empty || classDef == null ? null
						: classDef.getComment().map(EntityEditor::tooltip).orElse(null));
			}
		});
		tree.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
			if (item != null && entity != null && !selectingType) {
				// Only the type changes; values are kept on the entity
				entity.setTypes(List.of(item.getValue().getName()));
				showForm();
				onChange.run();
			}
		});
		return tree;
	}

	/**
	 * Select the entity's (first) type in the tree, expanding the path to it.
	 */
	private void selectType() {
		selectingType = true;
		try {
			Optional<ClassDef> type = classDef(entity.getTypes().get(0));
			if (type.isEmpty()) {
				types.getSelectionModel().clearSelection();
				return;
			}
			// Path from Thing, following the first superclass of each class
			List<String> path = new ArrayList<>();
			for (ClassDef c = type.get(); c != null; c = c.getSubClassOf().stream().findFirst().orElse(null)) {
				path.add(0, c.getName());
			}
			TreeItem<ClassDef> item = types.getRoot();
			for (String name : path.subList(1, path.size())) {
				item.setExpanded(true);
				item = item.getChildren().stream().filter(c -> c.getValue().getName().equals(name)).findFirst()
						.orElse(null);
				if (item == null) {
					types.getSelectionModel().clearSelection();
					return;
				}
			}
			types.getSelectionModel().select(item);
			types.scrollTo(Math.max(0, types.getRow(item) - 5));
		} finally {
			selectingType = false;
		}
	}

	/**
	 * Tree item creating the subclasses (except superseded ones) when first expanded.
	 */
	private class ClassTreeItem extends TreeItem<ClassDef> {
		private boolean childrenLoaded;

		ClassTreeItem(ClassDef classDef) {
			super(classDef);
		}

		@Override
		public ObservableList<TreeItem<ClassDef>> getChildren() {
			if (!childrenLoaded) {
				childrenLoaded = true;
				super.getChildren().setAll(schema.subclasses(getValue()).filter(c -> !schema.isSuperseded(c))
						.map(ClassTreeItem::new).toList());
			}
			return super.getChildren();
		}

		@Override
		public boolean isLeaf() {
			return childrenLoaded ? super.getChildren().isEmpty() : schema.subclasses(getValue()).findAny().isEmpty();
		}
	}

	private void showForm() {
		form.getChildren().clear();

		GridPane header = grid();
		Label typeLabel = new Label(String.join(", ", entity.getTypes()));
		typeLabel.setStyle("-fx-font-weight: bold;");
		classDef(entity.getTypes().get(0)).flatMap(ClassDef::getComment).map(EntityEditor::tooltip)
				.ifPresent(typeLabel::setTooltip);
		TextField idField = new TextField(entity.getId());
		// TODO: Renaming should also update references from other entities
		idField.setEditable(!entity.getId().equals("./"));
		idField.textProperty().addListener((obs, old, id) -> {
			if (!id.isBlank()) {
				entity.setId(id);
				onChange.run();
			}
		});
		header.addRow(0, new Label("@type"), typeLabel);
		header.addRow(1, new Label("@id"), idField);
		form.getChildren().add(header);

		List<FormField> fields = formFields.fieldsFor(entity.getTypes().stream().map(this::classDef)
				.flatMap(Optional::stream).map(ClassDef::getID).collect(Collectors.toSet()));
		Set<String> fieldNames = fields.stream().map(FormField::name).collect(Collectors.toSet());

		// Values the current type has no field for, e.g. after changing type
		List<String> others = entity.getValues().keySet().stream().filter(name -> !fieldNames.contains(name))
				.toList();
		if (!others.isEmpty()) {
			GridPane grid = grid();
			int row = 0;
			for (String name : others) {
				TextField value = new TextField(text(entity.get(name).orElseThrow()));
				value.setEditable(false);
				Button remove = new Button("Remove");
				remove.setOnAction(e -> {
					entity.set(name, Optional.empty());
					showForm();
					onChange.run();
				});
				grid.addRow(row++, new Label(name), value, remove);
			}
			TitledPane pane = new TitledPane("Not properties of " + String.join(", ", entity.getTypes()) + " ("
					+ others.size() + ")", grid);
			form.getChildren().add(pane);
		}

		Map<String, List<FormField>> groups = fields.stream()
				.collect(Collectors.groupingBy(FormField::group, LinkedHashMap::new, Collectors.toList()));
		boolean first = true;
		for (Map.Entry<String, List<FormField>> group : groups.entrySet()) {
			List<FormField> groupFields = group.getValue();
			boolean hasValues = groupFields.stream().anyMatch(f -> entity.get(f.name()).isPresent());
			TitledPane pane = new TitledPane("From " + group.getKey() + " (" + groupFields.size() + ")", null);
			// Most specific properties and those with values open, the rest
			// folded away, and only built when first opened
			if (first || hasValues) {
				pane.setContent(fieldsGrid(groupFields));
			} else {
				pane.setExpanded(false);
				pane.expandedProperty().addListener((obs, was, expanded) -> {
					if (expanded && pane.getContent() == null) {
						pane.setContent(fieldsGrid(groupFields));
					}
				});
			}
			first = false;
			form.getChildren().add(pane);
		}
	}

	private GridPane fieldsGrid(List<FormField> fields) {
		GridPane grid = grid();
		int row = 0;
		for (FormField field : fields) {
			Label label = new Label(field.label() + (field.isRequired() ? " *" : ""));
			field.comment().map(EntityEditor::tooltip).ifPresent(label::setTooltip);
			grid.addRow(row++, label, control(field));
		}
		return grid;
	}

	private static GridPane grid() {
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(6);
		ColumnConstraints labels = new ColumnConstraints();
		labels.setMinWidth(160);
		ColumnConstraints controls = new ColumnConstraints();
		controls.setHgrow(Priority.ALWAYS);
		grid.getColumnConstraints().setAll(labels, controls);
		return grid;
	}

	/**
	 * A control for a field, showing the entity's current value and
	 * updating the entity when edited.
	 */
	private Control control(FormField field) {
		String rangeNames = field.range().stream().map(ClassDef::getName).collect(Collectors.joining(" or "));
		String current = entity.get(field.name()).map(EntityEditor::text).orElse("");
		Control control = switch (field.kind()) {
		case BOOLEAN -> {
			ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList("", "true", "false"));
			combo.setValue(current);
			combo.valueProperty().addListener((obs, old, v) -> update(field, v));
			yield combo;
		}
		case DATE -> {
			DatePicker picker = new DatePicker();
			picker.setPromptText("YYYY-MM-DD");
			picker.getEditor().setText(current);
			picker.getEditor().textProperty().addListener((obs, old, v) -> update(field, v));
			yield picker;
		}
		case REFERENCE, TEXT_OR_REFERENCE -> {
			// Suggest the other entities of the crate, like Crate-O
			ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(
					entityIds.get().stream().filter(id -> !id.equals(entity.getId())).toList()));
			combo.setEditable(true);
			combo.setPromptText(field.kind() == ValueKind.REFERENCE ? "@id of " + rangeNames
					: rangeNames + " (text, or @id of an entity)");
			combo.getEditor().setText(current);
			combo.getEditor().textProperty().addListener((obs, old, v) -> update(field, v));
			yield combo;
		}
		default -> {
			TextField text = new TextField(current);
			text.setPromptText(rangeNames);
			text.textProperty().addListener((obs, old, v) -> update(field, v));
			yield text;
		}
		};
		control.setMaxWidth(Double.MAX_VALUE);
		return control;
	}

	private void update(FormField field, String text) {
		entity.set(field.name(), value(field, text));
		onChange.run();
	}

	/**
	 * Text to show for a JSON-LD value.
	 */
	private static String text(JsonNode value) {
		if (value.isValueNode()) {
			return value.asText();
		}
		if (value.isObject() && value.size() == 1 && value.has("@id")) {
			return value.get("@id").asText();
		}
		return value.toString();
	}

	/**
	 * The JSON-LD value of a field from the text typed, if any.
	 */
	private static Optional<JsonNode> value(FormField field, String text) {
		JsonNodeFactory json = JsonNodeFactory.instance;
		text = text == null ? "" : text.strip();
		if (text.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(switch (field.kind()) {
		case BOOLEAN -> json.booleanNode(Boolean.parseBoolean(text));
		case NUMBER -> number(text);
		case REFERENCE -> reference(text);
		case TEXT_OR_REFERENCE -> looksLikeId(text) ? reference(text) : json.textNode(text);
		default -> json.textNode(text);
		});
	}

	private static JsonNode number(String text) {
		try {
			return JsonNodeFactory.instance.numberNode(Long.parseLong(text));
		} catch (NumberFormatException notLong) {
			try {
				return JsonNodeFactory.instance.numberNode(Double.parseDouble(text));
			} catch (NumberFormatException notNumber) {
				// Keep as text rather than losing it
				return JsonNodeFactory.instance.textNode(text);
			}
		}
	}

	private static JsonNode reference(String id) {
		return JsonNodeFactory.instance.objectNode().put("@id", id);
	}

	/**
	 * Whether text in a field that takes either text or entities is an
	 * identifier: an absolute URI, or a local reference like <code>#person</code>
	 * or <code>./data/</code>.
	 */
	private static boolean looksLikeId(String text) {
		if (text.startsWith("#") || text.startsWith("./") || text.startsWith("../")) {
			return true;
		}
		try {
			return new URI(text).isAbsolute();
		} catch (URISyntaxException e) {
			return false;
		}
	}

	static Tooltip tooltip(String text) {
		Tooltip tooltip = new Tooltip(text);
		tooltip.setWrapText(true);
		tooltip.setMaxWidth(500);
		return tooltip;
	}
}
