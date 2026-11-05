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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.FormField;
import no.s11.wpsld.form.FormFields;
import no.s11.wpsld.form.ValueKind;
import no.s11.wpsld.soss.ClassDef;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Edits one {@link Entity} of a crate: its types, chosen with a
 * {@link TypeSelector}, its <code>@id</code> and a form of its properties.
 * <p>
 * Which fields the form shows is decided by {@link FormFields}; for now every
 * schema.org property of the types, later narrowed down by a profile. The
 * form reads from and writes to the entity, so changing the types keeps the
 * values; those the types don't have are listed separately, with suggestions
 * of types that have them.
 */
public class EntityEditor extends ScrollPane {
	private static final String S = "http://schema.org/";
	/** How many types to suggest for values without fields */
	private static final int SUGGESTIONS = 3;

	private final SchemaFromJsonLd schema;
	private final FormFields formFields;
	/** Identifiers of the other entities in the crate, to suggest as references */
	private final Supplier<List<String>> entityIds;
	/** Called after the entity is changed */
	private final Runnable onChange;
	/** Creates a new entity of the given type in the crate */
	private final Function<String, Entity> newEntity;

	private final VBox form = new VBox(8);
	private Entity entity;

	public EntityEditor(SchemaFromJsonLd schema, FormFields formFields, Supplier<List<String>> entityIds,
			Runnable onChange, Function<String, Entity> newEntity) {
		this.schema = schema;
		this.formFields = formFields;
		this.entityIds = entityIds;
		this.onChange = onChange;
		this.newEntity = newEntity;
		form.setPadding(new Insets(10));
		setContent(form);
		setFitToWidth(true);
	}

	/**
	 * Show and edit an entity.
	 */
	public void edit(Entity entity) {
		this.entity = Objects.requireNonNull(entity);
		showForm();
	}

	private Optional<ClassDef> classDef(String typeName) {
		return Optional.ofNullable(schema.getClasses().get(URI.create(S + typeName)));
	}

	/**
	 * Add a type to the entity. A superclass of a type the entity already
	 * has is not added, as it is implied; a subclass replaces its
	 * superclasses, e.g. Person replaces Thing. Unrelated types are kept
	 * side by side, making <code>@type</code> an array.
	 */
	private void addType(ClassDef added) {
		List<String> types = new ArrayList<>(entity.getTypes());
		if (types.contains(added.getName())) {
			return;
		}
		Optional<String> implying = types.stream()
				.filter(t -> classDef(t).map(c -> c.getSelfAndSuperClasses().contains(added)).orElse(false))
				.findFirst();
		if (implying.isPresent()) {
			Alert alert = new Alert(AlertType.INFORMATION,
					implying.get() + " is already a kind of " + added.getName() + ", so it is not added.");
			alert.initOwner(getScene().getWindow());
			alert.setHeaderText(null);
			alert.showAndWait();
			return;
		}
		types.removeIf(t -> classDef(t).map(added.getSelfAndSuperClasses()::contains).orElse(false));
		types.add(added.getName());
		changeTypes(types);
	}

	private void removeType(String type) {
		List<String> types = new ArrayList<>(entity.getTypes());
		types.remove(type);
		changeTypes(types);
	}

	private void changeTypes(List<String> types) {
		// Only the types change; values are kept on the entity
		entity.setTypes(types);
		showForm();
		onChange.run();
	}

	/**
	 * The entity's types as removable chips, and a button to add more.
	 */
	private FlowPane typeChips() {
		FlowPane chips = new FlowPane(6, 6);
		for (String type : entity.getTypes()) {
			Label name = new Label(type);
			name.setStyle("-fx-font-weight: bold;");
			classDef(type).flatMap(ClassDef::getComment).map(EntityEditor::tooltip).ifPresent(name::setTooltip);
			Button remove = new Button("×");
			remove.setStyle("-fx-padding: 0 4 0 4;");
			// An entity needs at least one type
			remove.setDisable(entity.getTypes().size() == 1);
			remove.setOnAction(e -> removeType(type));
			HBox chip = new HBox(4, name, remove);
			chip.setAlignment(Pos.CENTER_LEFT);
			chip.setStyle("-fx-background-color: -fx-control-inner-background; -fx-border-color: -fx-box-border;"
					+ " -fx-border-radius: 3; -fx-padding: 2 4 2 6;");
			chips.getChildren().add(chip);
		}
		Button add = new Button("Add type…");
		add.setOnAction(e -> new TypeSelector(schema, getScene().getWindow()).showAndWait().ifPresent(this::addType));
		chips.getChildren().add(add);
		return chips;
	}

	/**
	 * Types that have the given properties, those having the most first.
	 */
	private List<Map.Entry<ClassDef, Long>> suggestTypes(List<String> propertyNames) {
		return propertyNames.stream().map(name -> schema.getProperties().get(URI.create(S + name)))
				.filter(Objects::nonNull).flatMap(p -> p.getDomainIncludes().stream())
				.filter(c -> !schema.isSuperseded(c))
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
				.sorted(Map.Entry.<ClassDef, Long>comparingByValue().reversed()
						.thenComparing(e -> e.getKey().getName()))
				.limit(SUGGESTIONS).toList();
	}

	private void showForm() {
		form.getChildren().clear();

		GridPane header = grid();
		TextField idField = new TextField(entity.getId());
		// TODO: Renaming should also update references from other entities, see PLAN.md
		idField.setEditable(!entity.getId().equals("./"));
		idField.textProperty().addListener((obs, old, id) -> {
			if (!id.isBlank()) {
				entity.setId(id);
				onChange.run();
			}
		});
		header.addRow(0, new Label("@type"), typeChips());
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
			List<Map.Entry<ClassDef, Long>> suggestions = suggestTypes(others);
			if (!suggestions.isEmpty()) {
				FlowPane suggest = new FlowPane(6, 6, new Label("Suggest type:"));
				for (Map.Entry<ClassDef, Long> suggestion : suggestions) {
					Button addSuggested = new Button("Add type " + suggestion.getKey().getName() + " ("
							+ suggestion.getValue() + " of " + others.size() + ")");
					suggestion.getKey().getComment().map(EntityEditor::tooltip).ifPresent(addSuggested::setTooltip);
					addSuggested.setOnAction(e -> addType(suggestion.getKey()));
					suggest.getChildren().add(addSuggested);
				}
				grid.add(suggest, 0, row, 3, 1);
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
			GridPane.setValignment(label, VPos.TOP);
			label.setPadding(new Insets(4, 0, 0, 0));
			grid.addRow(row++, label, new FieldEditor(field).node());
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
	 * Edits the values of one field: a row per value, a button to add
	 * another value (up to the field's maximum), and for references, a menu
	 * to create a new entity of the field's range and link it.
	 */
	private class FieldEditor {
		private final FormField field;
		private final List<Row> rows = new ArrayList<>();
		private final VBox rowsBox = new VBox(4);
		private final Button addValue = new Button("+");
		private final VBox node;

		/** A value row: its control and how to get the text typed in it */
		private record Row(HBox node, Supplier<String> text) {
		}

		FieldEditor(FormField field) {
			this.field = field;
			List<JsonNode> values = entity.getAll(field.name());
			if (values.isEmpty()) {
				addRow("");
			} else {
				values.forEach(v -> addRow(text(v)));
			}
			addValue.setTooltip(new Tooltip("Add another value"));
			addValue.setOnAction(e -> addRow(""));
			HBox actions = new HBox(4, addValue);
			List<ClassDef> entityTypes = field.range().stream().filter(c -> !schema.isDataType(c)).toList();
			if (!entityTypes.isEmpty()) {
				MenuButton create = new MenuButton("New");
				create.setTooltip(new Tooltip("Create a new entity and link it"));
				for (ClassDef type : entityTypes) {
					MenuItem item = new MenuItem(type.getName());
					item.setOnAction(e -> link(newEntity.apply(type.getName()).getId()));
					create.getItems().add(item);
				}
				actions.getChildren().add(create);
			}
			node = new VBox(4, rowsBox, actions);
		}

		Node node() {
			return node;
		}

		private boolean full() {
			return field.maxCount().isPresent() && rows.size() >= field.maxCount().getAsInt();
		}

		/**
		 * Link an entity: in the first empty row, or a new row.
		 */
		private void link(String id) {
			Optional<Row> empty = rows.stream().filter(r -> r.text().get().isBlank()).findFirst();
			if (empty.isPresent()) {
				((ComboBox<?>) empty.get().node().getChildren().get(0)).getEditor().setText(id);
			} else if (!full()) {
				addRow(id);
				update();
			}
		}

		private void addRow(String current) {
			String rangeNames = field.range().stream().map(ClassDef::getName).collect(Collectors.joining(" or "));
			Control control;
			Supplier<String> text;
			switch (field.kind()) {
			case BOOLEAN -> {
				ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList("", "true", "false"));
				combo.setValue(current);
				combo.valueProperty().addListener((obs, old, v) -> update());
				control = combo;
				text = combo::getValue;
			}
			case DATE -> {
				DatePicker picker = new DatePicker();
				picker.setPromptText("YYYY-MM-DD");
				picker.getEditor().setText(current);
				picker.getEditor().textProperty().addListener((obs, old, v) -> update());
				control = picker;
				text = picker.getEditor()::getText;
			}
			case REFERENCE, TEXT_OR_REFERENCE -> {
				// Suggest the other entities of the crate, like Crate-O
				ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(
						entityIds.get().stream().filter(id -> !id.equals(entity.getId())).toList()));
				combo.setEditable(true);
				combo.setPromptText(field.kind() == ValueKind.REFERENCE ? "@id of " + rangeNames
						: rangeNames + " (text, or @id of an entity)");
				combo.getEditor().setText(current);
				combo.getEditor().textProperty().addListener((obs, old, v) -> update());
				control = combo;
				text = combo.getEditor()::getText;
			}
			default -> {
				TextField textField = new TextField(current);
				textField.setPromptText(rangeNames);
				textField.textProperty().addListener((obs, old, v) -> update());
				control = textField;
				text = textField::getText;
			}
			}
			control.setMaxWidth(Double.MAX_VALUE);
			HBox.setHgrow(control, Priority.ALWAYS);
			Button remove = new Button("×");
			remove.setTooltip(new Tooltip("Remove this value"));
			HBox rowNode = new HBox(4, control, remove);
			Row row = new Row(rowNode, text);
			remove.setOnAction(e -> {
				rows.remove(row);
				rowsBox.getChildren().remove(rowNode);
				if (rows.isEmpty()) {
					addRow("");
				}
				update();
			});
			rows.add(row);
			rowsBox.getChildren().add(rowNode);
			addValue.setDisable(full());
			// A single value field needs no button to add more
			addValue.setVisible(field.maxCount().orElse(Integer.MAX_VALUE) > 1);
			addValue.setManaged(addValue.isVisible());
		}

		/**
		 * Set all the field's values on the entity, skipping empty rows.
		 */
		private void update() {
			entity.setAll(field.name(), rows.stream().map(r -> value(field, r.text().get()))
					.flatMap(Optional::stream).toList());
			addValue.setDisable(full());
			onChange.run();
		}
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
