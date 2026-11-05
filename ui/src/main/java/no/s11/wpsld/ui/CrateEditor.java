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

import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.SchemaOrgFormFields;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Edits the entities of an RO-Crate, in the style of Crate-O: a list of the
 * crate's entities, an {@link EntityEditor} for the selected one, and a
 * preview of the resulting <code>ro-crate-metadata.json</code>.
 */
public class CrateEditor extends Application {
	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	private CompletableFuture<SchemaFromJsonLd> schemaLoading;
	private final Crate crate = new Crate();
	private final ListView<Entity> entities = new ListView<>();
	private final TextArea preview = new TextArea();

	@Override
	public void init() {
		// Load the schema while the JavaFX toolkit and window start up
		schemaLoading = CompletableFuture.supplyAsync(SchemaFromJsonLd::new);
	}

	@Override
	public void start(Stage stage) {
		BorderPane root = new BorderPane(new Label("Loading schema.org " + SchemaFromJsonLd.SCHEMAORG_VERSION + "…"));
		stage.setScene(new Scene(root, 1400, 800));
		stage.setTitle("wpsld");
		stage.show();

		// The one load started in init(): if it is still running, the editor
		// replaces the label when it completes; if done, this runs at once
		schemaLoading.whenComplete((schema, failure) -> Platform.runLater(() -> {
			if (failure != null) {
				root.setCenter(new Label("Can't load schema: " + failure));
				return;
			}
			root.setCenter(editor(schema));
		}));
	}

	private Node editor(SchemaFromJsonLd schema) {
		EntityEditor entityEditor = new EntityEditor(schema, new SchemaOrgFormFields(schema),
				() -> crate.getEntities().stream().map(Entity::getId).toList(), this::changed, type -> {
					// Created from a reference field, which then links to it
					Entity entity = crate.add(type);
					entities.getItems().add(entity);
					return entity;
				});

		entities.getItems().setAll(crate.getEntities());
		entities.setCellFactory(view -> new ListCell<>() {
			@Override
			protected void updateItem(Entity entity, boolean empty) {
				super.updateItem(entity, empty);
				setText(empty || entity == null ? null
						: entity.getLabel() + "\n" + String.join(", ", entity.getTypes()) + "  " + entity.getId());
			}
		});
		entities.getSelectionModel().selectedItemProperty().addListener((obs, old, entity) -> {
			if (entity != null) {
				entityEditor.edit(entity);
			}
		});

		Button add = new Button("Add entity");
		add.setOnAction(e -> {
			// A Thing to start with; its type can then be chosen
			Entity entity = crate.add("Thing");
			entities.getItems().add(entity);
			entities.getSelectionModel().select(entity);
			changed();
		});
		Button remove = new Button("Remove");
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

		preview.setEditable(false);
		preview.setStyle("-fx-font-family: monospace;");

		SplitPane split = new SplitPane(entityList, entityEditor, preview);
		split.setDividerPositions(0.2, 0.66);
		entities.getSelectionModel().select(crate.getRoot());
		changed();
		return split;
	}

	/**
	 * Refresh the entity list and the preview after a change.
	 */
	private void changed() {
		entities.refresh();
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
