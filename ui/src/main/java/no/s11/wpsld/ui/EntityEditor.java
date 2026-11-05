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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import no.s11.wpsld.form.FormField;
import no.s11.wpsld.form.FormFields;
import no.s11.wpsld.form.SchemaOrgFormFields;
import no.s11.wpsld.form.ValueKind;
import no.s11.wpsld.soss.ClassDef;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Editor for a single RO-Crate entity of any type. Which fields are shown is
 * decided by {@link FormFields}; for now every schema.org property of the
 * chosen type, later narrowed down by a profile.
 */
public class EntityEditor extends Application {
	private static final String S = "http://schema.org/";
	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	private SchemaFromJsonLd schema;
	private FormFields formFields;

	private final TextField idField = new TextField("./");
	private final VBox form = new VBox(8);
	private final TextArea preview = new TextArea();
	private ClassDef type;
	/** Fields of the shown form and the controls editing them */
	private final Map<FormField, Control> controls = new LinkedHashMap<>();

	@Override
	public void start(Stage stage) {
		BorderPane root = new BorderPane(new Label("Loading schema.org " + SchemaFromJsonLd.SCHEMAORG_VERSION + "…"));
		stage.setScene(new Scene(root, 1200, 750));
		stage.setTitle("wpsld entity editor");
		stage.show();

		Task<SchemaFromJsonLd> loading = new Task<>() {
			@Override
			protected SchemaFromJsonLd call() {
				return new SchemaFromJsonLd();
			}
		};
		loading.setOnSucceeded(e -> {
			schema = loading.getValue();
			formFields = new SchemaOrgFormFields(schema);
			root.setCenter(editor());
		});
		loading.setOnFailed(e -> root.setCenter(new Label("Can't load schema: " + loading.getException())));
		Thread thread = new Thread(loading, "schema loader");
		thread.setDaemon(true);
		thread.start();
	}

	private Node editor() {
		TreeView<ClassDef> types = typeTree();
		form.setPadding(new Insets(10));
		ScrollPane formScroll = new ScrollPane(form);
		formScroll.setFitToWidth(true);
		preview.setEditable(false);
		preview.setStyle("-fx-font-family: monospace;");
		idField.textProperty().addListener((obs, old, id) -> updatePreview());

		SplitPane split = new SplitPane(types, formScroll, preview);
		split.setDividerPositions(0.22, 0.65);
		// Root data entities of RO-Crates are Datasets
		select(types, "Thing", "CreativeWork", "Dataset");
		return split;
	}

	private TreeView<ClassDef> typeTree() {
		ClassDef thing = schema.getClasses().get(URI.create(S + "Thing"));
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
			if (item != null) {
				showForm(item.getValue());
			}
		});
		return tree;
	}

	/**
	 * Expand and select a path of class names from the root of the tree.
	 */
	private static void select(TreeView<ClassDef> tree, String... path) {
		TreeItem<ClassDef> item = tree.getRoot();
		for (int i = 1; i < path.length && item != null; i++) {
			item.setExpanded(true);
			String name = path[i];
			item = item.getChildren().stream().filter(c -> c.getValue().getName().equals(name)).findFirst()
					.orElse(null);
		}
		if (item != null) {
			tree.getSelectionModel().select(item);
			tree.scrollTo(tree.getRow(item));
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

	private void showForm(ClassDef classDef) {
		type = classDef;
		controls.clear();
		form.getChildren().clear();

		GridPane header = grid();
		Label typeLabel = new Label(classDef.getName());
		typeLabel.setStyle("-fx-font-weight: bold;");
		classDef.getComment().map(EntityEditor::tooltip).ifPresent(typeLabel::setTooltip);
		header.addRow(0, new Label("@type"), typeLabel);
		header.addRow(1, new Label("@id"), idField);
		form.getChildren().add(header);

		Map<String, List<FormField>> groups = formFields.fieldsFor(Set.of(classDef.getID())).stream()
				.collect(Collectors.groupingBy(FormField::group, LinkedHashMap::new, Collectors.toList()));
		boolean first = true;
		for (Map.Entry<String, List<FormField>> group : groups.entrySet()) {
			GridPane grid = grid();
			int row = 0;
			for (FormField field : group.getValue()) {
				Label label = new Label(field.label() + (field.isRequired() ? " *" : ""));
				field.comment().map(EntityEditor::tooltip).ifPresent(label::setTooltip);
				Control control = control(field);
				controls.put(field, control);
				grid.addRow(row++, label, control);
			}
			TitledPane pane = new TitledPane("From " + group.getKey() + " (" + group.getValue().size() + ")", grid);
			// Most specific properties first, the rest folded away
			pane.setExpanded(first);
			first = false;
			form.getChildren().add(pane);
		}
		updatePreview();
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

	private Control control(FormField field) {
		String rangeNames = field.range().stream().map(ClassDef::getName).collect(Collectors.joining(" or "));
		Control control = switch (field.kind()) {
		case BOOLEAN -> {
			ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList("", "true", "false"));
			combo.valueProperty().addListener((obs, old, v) -> updatePreview());
			yield combo;
		}
		case DATE -> {
			DatePicker picker = new DatePicker();
			picker.setPromptText("YYYY-MM-DD");
			picker.getEditor().textProperty().addListener((obs, old, v) -> updatePreview());
			picker.valueProperty().addListener((obs, old, v) -> updatePreview());
			yield picker;
		}
		default -> {
			TextField text = new TextField();
			text.setPromptText(switch (field.kind()) {
			case REFERENCE -> "@id of " + rangeNames;
			case TEXT_OR_REFERENCE -> rangeNames + " (text, or @id of an entity)";
			default -> rangeNames;
			});
			text.textProperty().addListener((obs, old, v) -> updatePreview());
			yield text;
		}
		};
		control.setMaxWidth(Double.MAX_VALUE);
		return control;
	}

	private void updatePreview() {
		if (type == null) {
			return;
		}
		ObjectNode entity = MAPPER.createObjectNode();
		entity.put("@id", idField.getText());
		entity.put("@type", type.getName());
		controls.forEach((field, control) -> value(field, control).ifPresent(v -> entity.set(field.name(), v)));
		try {
			preview.setText(MAPPER.writeValueAsString(entity));
		} catch (JsonProcessingException e) {
			preview.setText(e.toString());
		}
	}

	/**
	 * The JSON-LD value of a field, if filled in.
	 */
	private static Optional<JsonNode> value(FormField field, Control control) {
		JsonNodeFactory json = JsonNodeFactory.instance;
		String text = switch (control) {
		case TextField t -> t.getText();
		case DatePicker d -> d.getEditor().getText();
		case ComboBox<?> c -> c.getValue() == null ? "" : c.getValue().toString();
		default -> "";
		};
		text = text.strip();
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

	private static Tooltip tooltip(String text) {
		Tooltip tooltip = new Tooltip(text);
		tooltip.setWrapText(true);
		tooltip.setMaxWidth(500);
		return tooltip;
	}

	public static void main(String[] args) {
		launch(args);
	}
}
