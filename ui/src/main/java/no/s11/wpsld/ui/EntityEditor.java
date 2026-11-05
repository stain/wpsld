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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
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
import javafx.util.StringConverter;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.EntityTypes;
import no.s11.wpsld.form.FieldTier;
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
	/** How many types to suggest for values without fields */
	private static final int SUGGESTIONS = 3;

	private final SchemaFromJsonLd schema;
	private FormFields formFields;
	/** The crate's root data entity, whose form may differ (profile rules for the root) */
	private final Supplier<Entity> root;
	private final EntityTypes entityTypes;
	/** Type and property names as written in RO-Crates, e.g. File */
	private final CrateTerms terms;
	/** The crate's entities, to suggest as references */
	private final Supplier<List<Entity>> crateEntities;
	/** Called after the entity is changed */
	private final Runnable onChange;
	/** Creates a new entity of the given type in the crate */
	private final Function<String, Entity> newEntity;
	/** Changes an entity's @id in the crate, updating references (or merging) */
	private final BiConsumer<Entity, String> renameEntity;

	private final VBox form = new VBox(8);
	private Entity entity;
	/** True while an @id change is applied */
	private boolean renaming;
	/** Show the groups as a notebook rather than a list */
	private boolean notebookMode;
	/** The tabs of the notebook while building it */
	private final List<Notebook.Tab> notebookTabs = new ArrayList<>();
	/** Fields on each page of the notebook */
	static final int FIELDS_PER_PAGE = 8;
	/** The form's groups of fields */
	private final List<FieldGroup> groups = new ArrayList<>();
	/** Text of the property search, kept while the same entity is shown */
	private String propertyQuery = "";
	private final Label noMatches = new Label("No matching properties");

	public EntityEditor(SchemaFromJsonLd schema, FormFields formFields, Supplier<List<Entity>> crateEntities,
			Supplier<Entity> root, Runnable onChange, Function<String, Entity> newEntity,
			BiConsumer<Entity, String> renameEntity) {
		this.schema = schema;
		this.formFields = formFields;
		this.terms = new CrateTerms(schema);
		this.entityTypes = new EntityTypes(terms);
		this.crateEntities = crateEntities;
		this.root = root;
		this.onChange = onChange;
		this.newEntity = newEntity;
		this.renameEntity = renameEntity;
		form.setPadding(new Insets(10));
		setContent(form);
		setFitToWidth(true);
	}

	/**
	 * Use other form fields, e.g. another profile, for the entity shown and
	 * those edited after.
	 */
	public void setFormFields(FormFields formFields) {
		this.formFields = Objects.requireNonNull(formFields);
		if (entity != null) {
			showForm();
		}
	}

	/**
	 * Show and edit an entity.
	 */
	public void edit(Entity entity) {
		if (entity != this.entity) {
			propertyQuery = "";
		}
		this.entity = Objects.requireNonNull(entity);
		showForm();
	}

	/**
	 * Let the user choose a type to add to the entity, e.g. for a new entity.
	 */
	public void chooseType() {
		new TypeSelector(schema, getScene().getWindow()).showAndWait().ifPresent(this::addType);
	}

	private Optional<ClassDef> classDef(String typeName) {
		return terms.classDef(typeName);
	}

	/**
	 * Add a type to the entity, following {@link EntityTypes}: a subclass
	 * replaces its superclasses, unrelated types are kept side by side. If
	 * existing types already imply the added type (it is a superclass of
	 * them), ask whether to add it anyway or replace those types with it.
	 */
	private void addType(ClassDef added) {
		List<String> types = entity.getTypes();
		List<String> implying = entityTypes.implying(types, added);
		if (implying.isEmpty()) {
			changeTypes(entityTypes.add(types, added));
			return;
		}
		ButtonType addAnyway = new ButtonType("Add anyway");
		ButtonType replace = new ButtonType("Replace");
		String existing = String.join(" and ", implying);
		Alert alert = new Alert(AlertType.CONFIRMATION,
				existing + (implying.size() == 1 ? " is" : " are") + " already a kind of " + terms.name(added)
						+ ". Add " + terms.name(added) + " anyway, or replace " + existing + " with it?",
				addAnyway, replace, ButtonType.CANCEL);
		alert.initOwner(getScene().getWindow());
		alert.setHeaderText("Add " + terms.name(added));
		alert.showAndWait().ifPresent(choice -> {
			if (choice == addAnyway) {
				changeTypes(entityTypes.add(types, added));
			} else if (choice == replace) {
				changeTypes(entityTypes.replaceImplying(types, added));
			}
		});
	}

	private void removeType(String type) {
		// Removing the last type falls back to Thing
		changeTypes(entityTypes.remove(entity.getTypes(), type));
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
			remove.setId(id("removeType", type));
			remove.setStyle("-fx-padding: 0 4 0 4;");
			// Removing the last type falls back to Thing, so a lone Thing stays
			remove.setDisable(!entityTypes.canRemove(entity.getTypes(), type));
			remove.setOnAction(e -> removeType(type));
			HBox chip = new HBox(4, name, remove);
			chip.setId(id("type", type));
			chip.setAlignment(Pos.CENTER_LEFT);
			chip.setStyle("-fx-background-color: -fx-control-inner-background; -fx-border-color: -fx-box-border;"
					+ " -fx-border-radius: 3; -fx-padding: 2 4 2 6;");
			chips.getChildren().add(chip);
		}
		Button add = new Button("Add type…");
		add.setId("addType");
		add.setOnAction(e -> chooseType());
		chips.getChildren().add(add);
		return chips;
	}

	/**
	 * Types that have the given properties, those having the most first.
	 */
	private List<Map.Entry<ClassDef, Long>> suggestTypes(List<String> propertyNames) {
		return propertyNames.stream().map(terms::propertyDef).flatMap(Optional::stream).flatMap(p -> p.getDomainIncludes().stream())
				.filter(c -> !schema.isSuperseded(c))
				.collect(Collectors.groupingBy(Function.identity(), Collectors.counting())).entrySet().stream()
				.sorted(Map.Entry.<ClassDef, Long>comparingByValue().reversed()
						.thenComparing(e -> terms.name(e.getKey())))
				.limit(SUGGESTIONS).toList();
	}

	private void showForm() {
		form.getChildren().clear();

		GridPane header = grid();
		TextField idField = new TextField(entity.getId());
		idField.setId("entityId");
		// Applied when done (Enter or leaving the field), not on every key,
		// as intermediate identifiers could clash with other entities
		// The entity of this form, even if focus leaves the field because
		// another entity was selected
		Entity edited = entity;
		Runnable applyId = () -> {
			// Skip if another entity is shown now (e.g. after merging), or
			// while already renaming: a confirmation dialog takes the focus
			if (edited != entity || renaming) {
				return;
			}
			String id = idField.getText().strip();
			if (id.isEmpty() || id.equals(edited.getId())) {
				idField.setText(edited.getId());
				return;
			}
			renaming = true;
			try {
				renameEntity.accept(edited, id);
			} finally {
				renaming = false;
			}
		};
		idField.setOnAction(e -> applyId.run());
		idField.focusedProperty().addListener((obs, was, focused) -> {
			if (!focused) {
				applyId.run();
			}
		});
		header.addRow(0, new Label("@type"), typeChips());
		header.addRow(1, new Label("@id"), idField);
		TextField search = new TextField(propertyQuery);
		search.setId("propertySearch");
		search.setPromptText("Search properties, e.g. hasPart");
		search.textProperty().addListener((obs, old, query) -> {
			boolean starting = old.isBlank() && !query.isBlank();
			propertyQuery = query;
			applySearch(starting);
		});
		if (!notebookMode) {
			header.addRow(2, new Label("Properties"), search);
		}
		form.getChildren().add(header);
		noMatches.setVisible(false);
		noMatches.setManaged(false);
		form.getChildren().add(noMatches);
		groups.clear();

		notebookTabs.clear();
		List<FormField> fields = formFields.fieldsFor(entity.getTypes().stream().map(this::classDef)
				.flatMap(Optional::stream).map(ClassDef::getID).collect(Collectors.toSet()), entity == root.get());
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
					Button addSuggested = new Button("Add type " + terms.name(suggestion.getKey()) + " ("
							+ suggestion.getValue() + " of " + others.size() + ")");
					addSuggested.setId(id("suggestType", terms.name(suggestion.getKey())));
					suggestion.getKey().getComment().map(EntityEditor::tooltip).ifPresent(addSuggested::setTooltip);
					addSuggested.setOnAction(e -> addType(suggestion.getKey()));
					suggest.getChildren().add(addSuggested);
				}
				grid.add(suggest, 0, row, 3, 1);
			}
			String title = "Not properties of " + String.join(", ", entity.getTypes()) + " (" + others.size() + ")";
			if (notebookMode) {
				grid.setId("otherProperties");
				notebookTabs.add(new Notebook.Tab("Not of type", new Notebook.Page(title, () -> grid)));
			} else {
				TitledPane pane = new TitledPane(title, grid);
				pane.setId("otherProperties");
				pane.setAnimated(false);
				form.getChildren().add(pane);
			}
		}

		Map<String, List<FormField>> byGroup = fields.stream()
				.collect(Collectors.groupingBy(FormField::group, LinkedHashMap::new, Collectors.toList()));
		if (notebookMode) {
			showNotebook(byGroup);
			return;
		}
		// Groups of a profile's required and suggested properties first and
		// open; then the vocabulary's, of which only the most specific opens
		// when there are none of the profile's
		boolean profileGroups = fields.stream().anyMatch(f -> f.tier() != FieldTier.OTHER);
		boolean first = true;
		for (Map.Entry<String, List<FormField>> group : byGroup.entrySet()) {
			List<FormField> groupFields = group.getValue();
			boolean hasValues = groupFields.stream().anyMatch(f -> entity.get(f.name()).isPresent());
			boolean other = groupFields.get(0).tier() == FieldTier.OTHER;
			String title = (other ? "From " + group.getKey() : group.getKey() + " by " + formFields.getName())
					+ " (" + groupFields.size() + ")";
			FieldGroup fieldGroup = new FieldGroup(title, groupFields,
					!other || hasValues || (first && !profileGroups));
			if (other) {
				first = false;
			}
			groups.add(fieldGroup);
			form.getChildren().add(fieldGroup.pane);
		}
		if (!propertyQuery.isBlank()) {
			// Still searching, e.g. after adding a type
			applySearch(true);
		}
	}

	/**
	 * The groups as tabs of a notebook in the style of OS/2's Properties
	 * notebook, each with pages of {@link #FIELDS_PER_PAGE} fields.
	 */
	private void showNotebook(Map<String, List<FormField>> byGroup) {
		for (Map.Entry<String, List<FormField>> group : byGroup.entrySet()) {
			List<FormField> groupFields = group.getValue();
			boolean other = groupFields.get(0).tier() == FieldTier.OTHER;
			String title = other ? "From " + group.getKey() : group.getKey() + " by " + formFields.getName();
			List<Notebook.Page> pages = new ArrayList<>();
			for (int from = 0; from < groupFields.size(); from += FIELDS_PER_PAGE) {
				List<FormField> page = groupFields.subList(from, Math.min(from + FIELDS_PER_PAGE, groupFields.size()));
				pages.add(new Notebook.Page(title, () -> fieldsGrid(page, new LinkedHashMap<>())));
			}
			notebookTabs.add(new Notebook.Tab(group.getKey(), pages));
		}
		Notebook notebook = new Notebook();
		notebook.setId("notebook");
		notebook.getTabs().setAll(notebookTabs);
		VBox.setVgrow(notebook, Priority.ALWAYS);
		form.getChildren().add(notebook);
	}

	/**
	 * Show the form as a notebook of tabs and pages (true) or as a list of
	 * folding groups (false).
	 */
	public void setNotebookMode(boolean notebookMode) {
		this.notebookMode = notebookMode;
		setFitToHeight(notebookMode);
		if (entity != null) {
			showForm();
		}
	}

	/**
	 * Show only the properties whose name contains the search text, opening
	 * the groups that have such properties and folding the others; or,
	 * when the search is cleared, everything as before the search.
	 */
	private void applySearch(boolean starting) {
		String query = propertyQuery.strip().toLowerCase(Locale.ROOT);
		groups.forEach(g -> g.filter(query.isEmpty() ? null : query, starting));
		noMatches.setVisible(!query.isEmpty() && groups.stream().noneMatch(g -> g.hasMatches(query)));
		noMatches.setManaged(noMatches.isVisible());
	}

	private static boolean matches(FormField field, String query) {
		return field.name().toLowerCase(Locale.ROOT).contains(query)
				|| field.label().toLowerCase(Locale.ROOT).contains(query);
	}

	/**
	 * A titled group of fields, built when first opened, whose rows can be
	 * filtered by the property search.
	 */
	private class FieldGroup {
		private final TitledPane pane;
		private final List<FormField> fields;
		/** Label and editor of each field, once built */
		private final Map<FormField, List<Node>> rows = new LinkedHashMap<>();
		/** Whether the group was open before the search started, to restore it */
		private boolean expandedBeforeSearch;

		FieldGroup(String title, List<FormField> fields, boolean expanded) {
			this.fields = fields;
			pane = new TitledPane(title, null);
			// Meant to be fast, not pretty
			pane.setAnimated(false);
			pane.setExpanded(expanded);
			if (expanded) {
				build();
			}
			pane.expandedProperty().addListener((obs, was, now) -> {
				if (now) {
					build();
				}
			});
		}

		private void build() {
			if (pane.getContent() == null) {
				pane.setContent(fieldsGrid(fields, rows));
			}
		}

		boolean hasMatches(String query) {
			return fields.stream().anyMatch(f -> matches(f, query));
		}

		/**
		 * Show only the fields matching the query (in lower case), or all
		 * of them as before the search if the query is null.
		 */
		void filter(String query, boolean starting) {
			if (query == null) {
				rows.values().forEach(nodes -> show(nodes, true));
				pane.setExpanded(expandedBeforeSearch);
				return;
			}
			if (starting) {
				expandedBeforeSearch = pane.isExpanded();
			}
			if (!hasMatches(query)) {
				pane.setExpanded(false);
				return;
			}
			build();
			rows.forEach((field, nodes) -> show(nodes, matches(field, query)));
			pane.setExpanded(true);
		}

		private static void show(List<Node> nodes, boolean visible) {
			nodes.forEach(node -> {
				node.setVisible(visible);
				node.setManaged(visible);
			});
		}
	}

	/**
	 * A grid with a row of label and editor for each field.
	 *
	 * @param rows Filled with each field's label and editor
	 */
	private GridPane fieldsGrid(List<FormField> fields, Map<FormField, List<Node>> rows) {
		GridPane grid = grid();
		int row = 0;
		for (FormField field : fields) {
			Label label = new Label(field.label() + (field.isRequired() ? " *" : ""));
			label.setId(id("label", field.name()));
			field.comment().map(EntityEditor::tooltip).ifPresent(label::setTooltip);
			GridPane.setValignment(label, VPos.TOP);
			label.setPadding(new Insets(4, 0, 0, 0));
			Node editor = new FieldEditor(field).node();
			grid.addRow(row++, label, editor);
			rows.put(field, List.of(label, editor));
		}
		return grid;
	}

	private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

	/**
	 * The text as an ISO 8601 date (YYYY-MM-DD), if it is one.
	 */
	static Optional<String> isoDate(String text) {
		String date = text == null ? "" : text.strip();
		try {
			return Optional.of(LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE).toString());
		} catch (DateTimeParseException e) {
			return Optional.empty();
		}
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
			addValue.setId(id("add", field.name()));
			addValue.setTooltip(new Tooltip("Add another value"));
			addValue.setOnAction(e -> addRow(""));
			HBox actions = new HBox(4, addValue);
			List<ClassDef> entityTypes = field.range().stream().filter(c -> !schema.isDataType(c)).toList();
			if (!entityTypes.isEmpty()) {
				MenuButton create = new MenuButton("New");
				create.setId(id("new", field.name()));
				create.setTooltip(new Tooltip("Create a new entity and link it"));
				for (ClassDef type : entityTypes) {
					MenuItem item = new MenuItem(terms.name(type));
					item.setOnAction(e -> link(newEntity.apply(terms.name(type)).getId()));
					create.getItems().add(item);
				}
				actions.getChildren().add(create);
			}
			node = new VBox(4, rowsBox, actions);
			node.setId(id("field", field.name()));
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
			String rangeNames = field.range().stream().map(terms::name).collect(Collectors.joining(" or "));
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
				DatePicker picker = isoDatePicker(current);
				control = picker;
				// Only valid ISO 8601 dates are stored
				text = () -> isoDate(picker.getEditor().getText()).orElse("");
			}
			case REFERENCE, TEXT_OR_REFERENCE -> {
				control = referenceCombo(field, rangeNames, current);
				text = ((ComboBox<?>) control).getEditor()::getText;
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
			control.setId(id("value", field.name()) + "-" + rows.size());
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
		 * A date picker showing and reading ISO 8601 dates (YYYY-MM-DD),
		 * whatever the locale's date format; invalid text is marked and not
		 * stored.
		 */
		private DatePicker isoDatePicker(String current) {
			DatePicker picker = new DatePicker();
			picker.setPromptText("YYYY-MM-DD");
			picker.setConverter(new StringConverter<>() {
				@Override
				public String toString(LocalDate date) {
					return date == null ? "" : date.toString();
				}

				@Override
				public LocalDate fromString(String text) {
					return isoDate(text).map(LocalDate::parse).orElse(null);
				}
			});
			picker.getEditor().setText(current);
			isoDate(current).map(LocalDate::parse).ifPresent(picker::setValue);
			Tooltip invalid = new Tooltip("Not an ISO 8601 date: use YYYY-MM-DD, e.g. " + LocalDate.now());
			picker.getEditor().textProperty().addListener((obs, old, typed) -> {
				boolean valid = typed.isBlank() || isoDate(typed).isPresent();
				picker.getEditor().pseudoClassStateChanged(INVALID, !valid);
				picker.getEditor().setStyle(valid ? "" : "-fx-border-color: red;");
				// The editor's tooltip is bound to the picker's
				picker.setTooltip(valid ? null : invalid);
				update();
			});
			// A date chosen in the calendar, as ISO 8601 text
			picker.valueProperty().addListener((obs, old, date) -> {
				if (date != null && !date.toString().equals(picker.getEditor().getText())) {
					picker.getEditor().setText(date.toString());
				}
			});
			return picker;
		}

		/**
		 * An editable drop-down of the crate's entities fitting the field's
		 * range (like Crate-O), filtered by @id and name while typing.
		 */
		private ComboBox<String> referenceCombo(FormField field, String rangeNames, String current) {
			List<ClassDef> entityRange = field.range().stream().filter(c -> !schema.isDataType(c)).toList();
			Map<String, Entity> candidates = new LinkedHashMap<>();
			crateEntities.get().stream().filter(e -> e != entity)
					.filter(e -> entityRange.stream().anyMatch(range -> entityTypes.isA(e.getTypes(), range)))
					.forEach(e -> candidates.put(e.getId(), e));
			FilteredList<String> suggestions = new FilteredList<>(
					FXCollections.observableArrayList(candidates.keySet()));
			ComboBox<String> combo = new ComboBox<>(suggestions);
			combo.setEditable(true);
			combo.setPromptText(field.kind() == ValueKind.REFERENCE ? "@id of " + rangeNames
					: rangeNames + " (text, or @id of an entity)");
			// Show the entity's name and type, not just its @id
			combo.setCellFactory(view -> new ListCell<>() {
				@Override
				protected void updateItem(String id, boolean empty) {
					super.updateItem(id, empty);
					Entity e = empty || id == null ? null : candidates.get(id);
					setText(e == null ? id
							: (e.getLabel().equals(id) ? id : e.getLabel() + "  " + id) + "  ("
									+ String.join(", ", e.getTypes()) + ")");
				}
			});
			combo.getEditor().setText(current);
			combo.getEditor().textProperty().addListener((obs, old, typed) -> {
				update();
				if (!combo.getEditor().isFocused()) {
					// Set by choosing from the list or linking, not typed
					return;
				}
				String query = typed.strip().toLowerCase(Locale.ROOT);
				// Changing the list while the editor is being updated would reset it
				Platform.runLater(() -> {
					suggestions.setPredicate(id -> query.isEmpty() || id.toLowerCase(Locale.ROOT).contains(query)
							|| candidates.get(id).getLabel().toLowerCase(Locale.ROOT).contains(query));
					if (suggestions.isEmpty()) {
						combo.hide();
					} else if (!combo.isShowing()) {
						combo.show();
					}
				});
			});
			return combo;
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

	/**
	 * A node id from a property or type name, for tests and styling; CSS ids
	 * can't contain e.g. the colon of <code>ldac:Speaker</code>.
	 */
	static String id(String prefix, String name) {
		return prefix + "-" + name.replaceAll("[^A-Za-z0-9_-]", "_");
	}

	static Tooltip tooltip(String text) {
		Tooltip tooltip = new Tooltip(text);
		tooltip.setWrapText(true);
		tooltip.setMaxWidth(500);
		return tooltip;
	}
}
