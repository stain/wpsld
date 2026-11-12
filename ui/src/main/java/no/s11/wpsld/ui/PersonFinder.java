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

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
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
import no.s11.wpsld.lookup.ORCID;
import no.s11.wpsld.lookup.ORCID.Person;

/**
 * Find a person in ORCID by name (or ORCID iD): the matches in a list, with
 * their institutions to tell people of the same name apart. Gives the person
 * chosen on OK.
 */
class PersonFinder extends Dialog<Person> {
	/** Searches ORCID, e.g. {@link ORCID#search}, off the JavaFX thread */
	@FunctionalInterface
	interface Search {
		List<Person> search(String query) throws Exception;
	}

	private final TextField query = new TextField();
	private final ListView<Person> results = new ListView<>();
	private final Label status = new Label();
	private final VBox details = new VBox(6);
	private final Search search;
	private final Consumer<String> browse;

	PersonFinder(Window owner, String initial, Search search, Consumer<String> browse) {
		this.search = Objects.requireNonNull(search);
		this.browse = Objects.requireNonNull(browse);
		initOwner(owner);
		setTitle("Find a person");
		setHeaderText("Find a person in ORCID");
		setResizable(true);

		query.setId("personQuery");
		query.setPromptText("Name, e.g. Josiah Carberry, or an ORCID iD");
		query.setText(initial);
		HBox.setHgrow(query, Priority.ALWAYS);
		Button find = new Button("Search");
		find.setId("personSearch");
		find.setOnAction(e -> runSearch());
		query.setOnAction(e -> runSearch());
		HBox searchBar = new HBox(8, query, find);

		results.setId("personResults");
		results.setPlaceholder(new Label("Search ORCID by name, then choose the person"));
		results.setCellFactory(view -> new ListCell<>() {
			{
				setWrapText(true);
				setPrefWidth(0);
			}

			@Override
			protected void updateItem(Person person, boolean empty) {
				super.updateItem(person, empty);
				setText(empty || person == null ? null
						: person.name() + "\n" + person.id()
								+ (person.institutions().isEmpty() ? "" : " · " + String.join(", ", person.institutions())));
			}
		});
		results.getSelectionModel().selectedItemProperty().addListener((obs, old, person) -> showDetails(person));
		results.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2
					&& results.getSelectionModel().getSelectedItem() != null) {
				setResult(results.getSelectionModel().getSelectedItem());
				close();
			}
		});
		details.setId("personDetails");
		details.setPadding(new Insets(8));
		SplitPane split = new SplitPane(results, details);
		split.setDividerPositions(0.55);
		VBox.setVgrow(split, Priority.ALWAYS);
		status.setId("personStatus");
		status.setWrapText(true);
		Label note = new Label("Several people may have the same name: check their institutions, or open their "
				+ "ORCID record, before choosing.");
		note.setWrapText(true);
		note.getStyleClass().add("dim-text");
		Hyperlink about = new Hyperlink("ORCID's public registry");
		about.setOnAction(e -> browse.accept("https://orcid.org/"));
		VBox content = new VBox(8, searchBar, split, status, note, about);
		content.setPadding(new Insets(4));
		content.setPrefSize(780, 480);
		getDialogPane().setContent(content);
		getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		Node ok = getDialogPane().lookupButton(ButtonType.OK);
		ok.setId("personOk");
		ok.disableProperty().bind(results.getSelectionModel().selectedItemProperty().isNull());
		setResultConverter(button -> button == ButtonType.OK ? results.getSelectionModel().getSelectedItem() : null);
		setOnShown(e -> {
			query.requestFocus();
			if (!query.getText().isBlank()) {
				runSearch();
			}
		});
	}

	private void runSearch() {
		String text = query.getText().strip();
		if (text.isEmpty()) {
			return;
		}
		status.setText("Searching ORCID for " + text + "…");
		results.getItems().clear();
		Task<List<Person>> task = new Task<>() {
			@Override
			protected List<Person> call() throws Exception {
				return search.search(text);
			}
		};
		task.setOnSucceeded(e -> {
			results.getItems().setAll(task.getValue());
			int found = task.getValue().size();
			status.setText(found == 0 ? "No one named " + text + " found"
					: found == 1 ? "1 person found; choose them and OK"
							: found + " people found, the most likely first; choose one and OK");
			if (found > 0) {
				results.getSelectionModel().selectFirst();
			}
		});
		task.setOnFailed(e -> {
			Throwable problem = task.getException();
			status.setText("Can't search ORCID: "
					+ (problem.getMessage() == null ? problem.toString() : problem.getMessage())
					+ ". You can still type the ORCID iD as the person's @id.");
		});
		Thread thread = new Thread(task, "ORCID search");
		thread.setDaemon(true);
		thread.start();
	}

	private void showDetails(Person person) {
		details.getChildren().clear();
		if (person == null) {
			return;
		}
		Label name = new Label(person.name());
		name.setWrapText(true);
		name.setStyle("-fx-font-weight: bold;");
		details.getChildren().addAll(name, new Label("ORCID iD: " + person.id()));
		if (!person.otherNames().isEmpty()) {
			Label others = new Label("Also known as: " + String.join("; ", person.otherNames()));
			others.setWrapText(true);
			details.getChildren().add(others);
		}
		if (!person.institutions().isEmpty()) {
			Label institutions = new Label("Institutions: " + String.join("; ", person.institutions()));
			institutions.setWrapText(true);
			details.getChildren().add(institutions);
		}
		Hyperlink record = new Hyperlink("Their ORCID record");
		record.setOnAction(e -> browse.accept(person.iri()));
		details.getChildren().add(record);
	}
}
