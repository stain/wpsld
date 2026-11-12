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

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import no.s11.wpsld.lookup.ROR;
import no.s11.wpsld.lookup.ROR.Name;
import no.s11.wpsld.lookup.ROR.Organization;

/**
 * Find an organisation in ROR by name (or ROR identifier): the matches in a
 * list, with where they are, and which of its names to write, ROR's display
 * name unless another is chosen. Gives the organisation and name chosen on OK.
 */
class OrganizationFinder extends Dialog<OrganizationFinder.Chosen> {
	/** Searches ROR, e.g. {@link ROR#search}, off the JavaFX thread */
	@FunctionalInterface
	interface Search {
		List<Organization> search(String query) throws Exception;
	}

	/** The organisation chosen, and the one of its names to write */
	record Chosen(Organization organization, String name) {
	}

	private final TextField query = new TextField();
	private final ListView<Organization> results = new ListView<>();
	private final Label status = new Label();
	private final VBox details = new VBox(6);
	private final ComboBox<Name> name = new ComboBox<>();
	private final Search search;
	private final Consumer<String> browse;

	OrganizationFinder(Window owner, String initial, Search search, Consumer<String> browse) {
		this.search = Objects.requireNonNull(search);
		this.browse = Objects.requireNonNull(browse);
		initOwner(owner);
		setTitle("Find an organisation");
		setHeaderText("Find an organisation in ROR");
		setResizable(true);

		query.setId("organizationQuery");
		query.setPromptText("Name, e.g. University of Oslo, or a ROR identifier");
		query.setText(initial);
		HBox.setHgrow(query, Priority.ALWAYS);
		Button find = new Button("Search");
		find.setId("organizationSearch");
		find.setOnAction(e -> runSearch());
		query.setOnAction(e -> runSearch());
		HBox searchBar = new HBox(8, query, find);

		results.setId("organizationResults");
		results.setPlaceholder(new Label("Search ROR by name, then choose the organisation"));
		results.setCellFactory(view -> new ListCell<>() {
			{
				setWrapText(true);
				setPrefWidth(0);
			}

			@Override
			protected void updateItem(Organization organization, boolean empty) {
				super.updateItem(organization, empty);
				setText(empty || organization == null ? null
						: organization.name() + "\n" + Stream.of(organization.where(),
								String.join(", ", organization.types())).filter(s -> !s.isEmpty())
								.collect(Collectors.joining(" · ")));
			}
		});
		results.getSelectionModel().selectedItemProperty()
				.addListener((obs, old, organization) -> showDetails(organization));
		results.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2
					&& results.getSelectionModel().getSelectedItem() != null) {
				setResult(chosen());
				close();
			}
		});
		name.setId("organizationName");
		name.setMaxWidth(Double.MAX_VALUE);
		details.setId("organizationDetails");
		details.setPadding(new Insets(8));
		SplitPane split = new SplitPane(results, details);
		split.setDividerPositions(0.5);
		VBox.setVgrow(split, Priority.ALWAYS);
		status.setId("organizationStatus");
		status.setWrapText(true);
		Hyperlink about = new Hyperlink("The Research Organization Registry (ROR)");
		about.setOnAction(e -> browse.accept("https://ror.org/"));
		VBox content = new VBox(8, searchBar, split, status, about);
		content.setPadding(new Insets(4));
		content.setPrefSize(820, 480);
		getDialogPane().setContent(content);
		getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		Node ok = getDialogPane().lookupButton(ButtonType.OK);
		ok.setId("organizationOk");
		ok.disableProperty().bind(results.getSelectionModel().selectedItemProperty().isNull());
		setResultConverter(button -> button == ButtonType.OK ? chosen() : null);
		setOnShown(e -> {
			query.requestFocus();
			if (!query.getText().isBlank()) {
				runSearch();
			}
		});
	}

	private Chosen chosen() {
		Organization organization = results.getSelectionModel().getSelectedItem();
		if (organization == null) {
			return null;
		}
		Name written = name.getValue();
		return new Chosen(organization, written == null ? organization.name() : written.value());
	}

	private void runSearch() {
		String text = query.getText().strip();
		if (text.isEmpty()) {
			return;
		}
		status.setText("Searching ROR for " + text + "…");
		results.getItems().clear();
		Task<List<Organization>> task = new Task<>() {
			@Override
			protected List<Organization> call() throws Exception {
				return search.search(text);
			}
		};
		task.setOnSucceeded(e -> {
			results.getItems().setAll(task.getValue());
			int found = task.getValue().size();
			status.setText(found == 0 ? "No organisation named " + text + " found"
					: found == 1 ? "1 organisation found; choose it and OK"
							: found + " organisations found, the most likely first; choose one and OK");
			if (found > 0) {
				results.getSelectionModel().selectFirst();
			}
		});
		task.setOnFailed(e -> {
			Throwable problem = task.getException();
			status.setText("Can't search ROR: "
					+ (problem.getMessage() == null ? problem.toString() : problem.getMessage())
					+ ". You can still type the ROR identifier as the organisation's @id.");
		});
		Thread thread = new Thread(task, "ROR search");
		thread.setDaemon(true);
		thread.start();
	}

	private void showDetails(Organization organization) {
		details.getChildren().clear();
		name.getItems().clear();
		if (organization == null) {
			return;
		}
		Label title = new Label(organization.name());
		title.setWrapText(true);
		title.setStyle("-fx-font-weight: bold;");
		details.getChildren().addAll(title, new Label("ROR: " + organization.id()));
		if (!organization.where().isEmpty()) {
			details.getChildren().add(new Label("In: " + organization.where()));
		}
		if (!organization.types().isEmpty()) {
			details.getChildren().add(new Label("Kind: " + String.join(", ", organization.types())));
		}
		if (!organization.status().equals("active")) {
			Label inactive = new Label("Status: " + organization.status());
			inactive.setId("organizationInactive");
			details.getChildren().add(inactive);
		}
		name.getItems().setAll(organization.names());
		name.getSelectionModel().selectFirst();
		Label nameLabel = new Label("Name to write:");
		nameLabel.setLabelFor(name);
		Label note = new Label("ROR knows it by these names, in several languages; one is written as its name.");
		note.setWrapText(true);
		note.getStyleClass().add("dim-text");
		details.getChildren().addAll(nameLabel, name, note);
		organization.website().ifPresent(website -> {
			Hyperlink site = new Hyperlink(website);
			site.setOnAction(e -> browse.accept(website));
			details.getChildren().add(site);
		});
		Hyperlink record = new Hyperlink("Its ROR record");
		record.setOnAction(e -> browse.accept(organization.iri()));
		details.getChildren().add(record);
	}
}
