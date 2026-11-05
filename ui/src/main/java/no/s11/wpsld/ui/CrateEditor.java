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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import javafx.stage.Stage;
import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateChecks;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.EntityTypes;
import no.s11.wpsld.form.FormFields;
import no.s11.wpsld.form.ProfileFormFields;
import no.s11.wpsld.form.RequiredFields;
import no.s11.wpsld.form.SchemaOrgFormFields;
import no.s11.wpsld.jsonld.JsonLdContext;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Edits the entities of an RO-Crate, in the style of Crate-O: a list of the
 * crate's entities, an {@link EntityEditor} for the selected one, and a
 * preview of the resulting <code>ro-crate-metadata.json</code>.
 */
public class CrateEditor extends Application {
	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	/** Incremented on changes, to update the labels of the entity list */
	private final LongProperty labels = new SimpleLongProperty();

	private final Supplier<SchemaFromJsonLd> loadSchema;
	private final Supplier<JsonLdContext> loadContext;
	private CompletableFuture<SchemaFromJsonLd> schemaLoading;
	private CompletableFuture<JsonLdContext> contextLoading;
	private final Crate crate = new Crate();
	private final ListView<Entity> entities = new ListView<>();
	private final TextArea preview = new TextArea();
	private EntityPreview entityPreview;
	private EntityEditor entityEditor;
	private EntityTypes entityTypes;
	private CrateTerms terms;
	/** Which profile's fields the form shows */
	private final ComboBox<FormFields> profiles = new ComboBox<>();
	/** Where the crate was last saved, to start the next save there */
	private File lastSaved;

	public CrateEditor() {
		this(SchemaFromJsonLd::new);
	}

	/**
	 * With another way to load the schema, e.g. failing in tests.
	 */
	CrateEditor(Supplier<SchemaFromJsonLd> loadSchema) {
		this(loadSchema, CrateTerms::prewarm);
	}

	/**
	 * With other ways to load the schema and the RO-Crate context, e.g. in
	 * tests.
	 */
	CrateEditor(Supplier<SchemaFromJsonLd> loadSchema, Supplier<JsonLdContext> loadContext) {
		this.loadSchema = loadSchema;
		this.loadContext = loadContext;
	}

	@Override
	public void init() {
		// Load the schema while the JavaFX toolkit and window start up, and in
		// parallel the RO-Crate context that naming types and properties needs,
		// so that building the editor on the JavaFX thread doesn't load it
		schemaLoading = CompletableFuture.supplyAsync(loadSchema);
		contextLoading = CompletableFuture.supplyAsync(loadContext);
	}

	@Override
	public void start(Stage stage) {
		BorderPane root = new BorderPane(new Label("Loading schema.org " + SchemaFromJsonLd.SCHEMAORG_VERSION + "…"));
		stage.setScene(new Scene(root, 1400, 800));
		// Also if the window showed another scene before (as in tests)
		stage.sizeToScene();
		stage.setTitle("wpsld");
		stage.show();

		// The loads started in init(): while still running, the editor
		// replaces the label when both complete; if done, this runs at once
		schemaLoading.thenCombine(contextLoading, (schema, context) -> schema)
				.whenComplete((schema, failure) -> Platform.runLater(() -> {
			if (failure != null) {
				boolean contextFailed = contextLoading.isCompletedExceptionally()
						&& !schemaLoading.isCompletedExceptionally();
				showFailure(root, contextFailed ? "Can't load the RO-Crate context" : "Can't load schema", failure);
				return;
			}
			try {
				root.setCenter(editor(schema));
			} catch (RuntimeException e) {
				// Otherwise lost in the CompletableFuture, leaving "Loading…"
				showFailure(root, "Can't show the editor", e);
				return;
			}
			Button save = new Button("Save RO-Crate Metadata File…");
			save.setId("save");
			save.setOnAction(e -> save(stage));
			profiles.setId("profile");
			profiles.setConverter(new StringConverter<>() {
				@Override
				public String toString(FormFields fields) {
					return fields == null ? "" : fields.getName();
				}

				@Override
				public FormFields fromString(String name) {
					return profiles.getItems().stream().filter(f -> f.getName().equals(name)).findFirst().orElse(null);
				}
			});
			profiles.valueProperty().addListener((obs, old, fields) -> {
				if (fields != null) {
					entityEditor.setFormFields(fields);
				}
			});
			// The form as a list of folding groups, or as a notebook in the style
			// of the OS/2 Workplace Shell's Properties notebook
			ToggleGroup view = new ToggleGroup();
			ToggleButton list = new ToggleButton("List");
			list.setId("viewList");
			ToggleButton notebook = new ToggleButton("Notebook");
			notebook.setId("viewNotebook");
			list.setToggleGroup(view);
			notebook.setToggleGroup(view);
			list.setSelected(true);
			view.selectedToggleProperty().addListener((obs, old, selected) -> {
				if (selected == null) {
					// Keep one selected
					old.setSelected(true);
				} else {
					entityEditor.setNotebookMode(selected == notebook);
				}
			});
			root.setTop(new ToolBar(save, new Separator(), new Label("Profile"), profiles, new Separator(),
					new Label("View"), list, notebook));
		}));
	}

	private Node editor(SchemaFromJsonLd schema) {
		entityTypes = new EntityTypes(schema);
		terms = new CrateTerms(schema);
		SchemaOrgFormFields schemaOrg = new SchemaOrgFormFields(terms);
		// The core RO-Crate profile by default: the same schema.org fields,
		// with those RO-Crate requires first; or schema.org without a profile
		profiles.getItems().setAll(ProfileCache.bundled().bundledIdentifiers().stream()
				.map(ProfileCache.bundled()::bundled).flatMap(Optional::stream).distinct()
				.map(p -> (FormFields) new ProfileFormFields(p, schemaOrg)).toList());
		profiles.getItems().add(schemaOrg);
		profiles.getSelectionModel().selectFirst();
		entityEditor = new EntityEditor(schema, profiles.getValue(), crate::getEntities, crate::getRoot,
				this::changed, type -> {
					// Created from a reference field, which then links to it
					Entity entity = crate.add(type);
					entities.getItems().add(entity);
					return entity;
				}, this::rename);

		entities.setId("entities");
		entities.getItems().setAll(crate.getEntities());
		entities.setCellFactory(view -> new ListCell<>() {
			// Update the text in place on changes: refreshing the whole list
			// on each key typed made it flicker
			private final InvalidationListener relabel = obs -> label(getItem());
			{
				labels.addListener(new WeakInvalidationListener(relabel));
			}

			@Override
			protected void updateItem(Entity entity, boolean empty) {
				super.updateItem(entity, empty);
				label(empty ? null : entity);
			}

			private void label(Entity entity) {
				String text = entity == null ? null
						: entity.getLabel() + "\n" + String.join(", ", entity.getTypes()) + "  " + entity.getId();
				if (!Objects.equals(text, getText())) {
					setText(text);
				}
			}
		});
		entities.getSelectionModel().selectedItemProperty().addListener((obs, old, entity) -> {
			if (entity != null) {
				entityEditor.edit(entity);
				entityPreview.show(entity);
			}
		});

		Button add = new Button("Add entity");
		add.setId("addEntity");
		add.setOnAction(e -> {
			// A Thing to start with, and straight away choose its type, which
			// replaces Thing (not needed for entities added from a property)
			Entity entity = crate.add("Thing");
			entities.getItems().add(entity);
			entities.getSelectionModel().select(entity);
			changed();
			Platform.runLater(entityEditor::chooseType);
		});
		Button remove = new Button("Remove");
		remove.setId("removeEntity");
		remove.disableProperty().bind(entities.getSelectionModel().selectedIndexProperty().lessThanOrEqualTo(0));
		remove.setOnAction(e -> {
			Entity entity = entities.getSelectionModel().getSelectedItem();
			crate.remove(entity);
			entities.getItems().remove(entity);
			changed();
		});
		HBox buttons = new HBox(6, add, remove);
		buttons.setPadding(new Insets(6));
		VBox.setVgrow(entities, Priority.ALWAYS);
		VBox entityList = new VBox(new Label(" Entities"), entities, buttons);

		preview.setId("preview");
		preview.setEditable(false);
		preview.setStyle("-fx-font-family: monospace;");

		// On the right, a preview of the entity (with links to the entities it
		// refers to, which open them) and the crate's JSON-LD
		entityPreview = new EntityPreview(() -> crate, entities.getSelectionModel()::select,
				address -> getHostServices().showDocument(address));
		Tab previewTab = new Tab("Preview", entityPreview);
		previewTab.setId("previewTab");
		Tab jsonTab = new Tab("JSON-LD", preview);
		jsonTab.setId("jsonTab");
		TabPane views = new TabPane(previewTab, jsonTab);
		views.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		SplitPane split = new SplitPane(entityList, entityEditor, views);
		split.setDividerPositions(0.2, 0.66);
		entities.getSelectionModel().select(crate.getRoot());
		changed();
		return split;
	}

	private static void showFailure(BorderPane root, String what, Throwable failure) {
		Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause()
				: failure;
		System.getLogger(CrateEditor.class.getName()).log(System.Logger.Level.ERROR, what, cause);
		Label label = new Label(what + ": " + cause);
		label.setId("failure");
		label.setWrapText(true);
		root.setCenter(label);
	}

	/**
	 * Change an entity's @id, updating references to it. If another entity
	 * already has that identifier, offer to merge the two.
	 */
	private void rename(Entity entity, String newId) {
		Optional<Entity> existing = crate.get(newId);
		Entity edited = entity;
		if (existing.isEmpty()) {
			crate.rename(entity, newId);
		} else {
			Alert alert = new Alert(AlertType.CONFIRMATION, "Another entity, " + existing.get().getLabel() + " ("
					+ String.join(", ", existing.get().getTypes()) + "), already has the @id " + newId
					+ ".\n\nMerge " + entity.getLabel() + " into it? Their types and values are combined and"
					+ " references to either point to the merged entity.", ButtonType.OK, ButtonType.CANCEL);
			alert.initOwner(entities.getScene().getWindow());
			alert.setHeaderText("Merge entities?");
			if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
				// Back to the current @id
				entityEditor.edit(entity);
				return;
			}
			if (entity == crate.getRoot()) {
				// The root data entity keeps its place, taking over the other
				crate.merge(entity, existing.get());
				crate.rename(entity, newId);
			} else {
				crate.merge(existing.get(), entity);
				edited = existing.get();
			}
			// e.g. DataDownload and Thing become DataDownload
			edited.setTypes(entityTypes.simplify(edited.getTypes()));
		}
		entities.getItems().setAll(crate.getEntities());
		entities.getSelectionModel().select(edited);
		entityEditor.edit(edited);
		changed();
	}

	/**
	 * Save the crate as an RO-Crate Metadata File, after checking the RO-Crate
	 * requirements and asking whether to save anyway if any are not met.
	 */
	private void save(Stage stage) {
		// The profile's required properties, then RO-Crate's own checks
		// (worded alike, so each problem only once)
		List<String> problems = Stream.concat(RequiredFields.check(crate, profiles.getValue(), terms).stream(),
				CrateChecks.check(crate).stream()).distinct().toList();
		if (!problems.isEmpty()) {
			ButtonType saveAnyway = new ButtonType("Save anyway", ButtonData.OK_DONE);
			Alert alert = new Alert(AlertType.WARNING,
					"• " + String.join("\n• ", problems), saveAnyway, ButtonType.CANCEL);
			alert.initOwner(stage);
			alert.setHeaderText("The crate does not yet meet the RO-Crate requirements");
			if (alert.showAndWait().orElse(ButtonType.CANCEL) != saveAnyway) {
				return;
			}
		}
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Save RO-Crate Metadata File");
		chooser.setInitialFileName(Crate.RO_CRATE_METADATA_JSON);
		chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("JSON-LD", "*.json", "*.jsonld"));
		if (lastSaved != null) {
			chooser.setInitialDirectory(lastSaved.getParentFile());
		}
		File file = chooser.showSaveDialog(stage);
		if (file == null) {
			return;
		}
		try {
			MAPPER.writeValue(file, crate.toJson());
			lastSaved = file;
			stage.setTitle("wpsld – " + file);
		} catch (IOException e) {
			Alert error = new Alert(AlertType.ERROR, "Can't save " + file + ":\n" + e.getMessage());
			error.initOwner(stage);
			error.showAndWait();
		}
	}

	/**
	 * Refresh the entity list and the preview after a change.
	 */
	private void changed() {
		labels.set(labels.get() + 1);
		if (entityPreview != null) {
			entityPreview.refresh();
		}
		try {
			preview.setText(MAPPER.writeValueAsString(crate.toJson()));
		} catch (JsonProcessingException e) {
			preview.setText(e.toString());
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}
