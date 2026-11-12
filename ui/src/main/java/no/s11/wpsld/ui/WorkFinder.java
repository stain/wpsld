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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import no.s11.wpsld.crate.Dois;
import no.s11.wpsld.crate.Dois.Work;

/**
 * Find a publication, dataset or software by DOI, or by its title and
 * authors in Crossref and DataCite. Gives the work chosen on OK.
 */
class WorkFinder extends Dialog<Work> {
	/** Searches, e.g. {@link Dois#search}, off the JavaFX thread */
	@FunctionalInterface
	interface Search {
		List<Work> search(String query) throws Exception;
	}

	private final TextField query = new TextField();
	private final ListView<Work> results = new ListView<>();
	private final Label status = new Label();
	private final VBox details = new VBox(6);
	private final Search search;
	private final Consumer<String> browse;

	WorkFinder(Window owner, String initial, Search search, Consumer<String> browse) {
		this.search = Objects.requireNonNull(search);
		this.browse = Objects.requireNonNull(browse);
		initOwner(owner);
		setTitle("Find a publication");
		setHeaderText("Find a publication, dataset or software by DOI");
		setResizable(true);

		query.setId("workQuery");
		query.setPromptText("A DOI, e.g. 10.5281/zenodo.3541888, or words of its title and authors");
		query.setText(initial);
		HBox.setHgrow(query, Priority.ALWAYS);
		Button find = new Button("Search");
		find.setId("workSearch");
		find.setOnAction(e -> runSearch());
		query.setOnAction(e -> runSearch());
		HBox searchBar = new HBox(8, query, find);

		results.setId("workResults");
		results.setPlaceholder(new Label("Type a DOI, or search Crossref and DataCite by title and authors"));
		results.setCellFactory(view -> new ListCell<>() {
			{
				setWrapText(true);
				setPrefWidth(0);
			}

			@Override
			protected void updateItem(Work work, boolean empty) {
				super.updateItem(work, empty);
				setText(empty || work == null ? null : work.title() + "\n" + byline(work));
			}
		});
		results.getSelectionModel().selectedItemProperty().addListener((obs, old, work) -> showDetails(work));
		results.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2
					&& results.getSelectionModel().getSelectedItem() != null) {
				setResult(results.getSelectionModel().getSelectedItem());
				close();
			}
		});
		details.setId("workDetails");
		details.setPadding(new Insets(8));
		ScrollPane detailsPane = new ScrollPane(details);
		detailsPane.setFitToWidth(true);
		SplitPane split = new SplitPane(results, detailsPane);
		split.setDividerPositions(0.55);
		VBox.setVgrow(split, Priority.ALWAYS);
		status.setId("workStatus");
		status.setWrapText(true);
		Hyperlink about = new Hyperlink("From Crossref and DataCite, which register most research DOIs");
		about.setOnAction(e -> browse.accept("https://www.doi.org/"));
		VBox content = new VBox(8, searchBar, split, status, about);
		content.setPadding(new Insets(4));
		content.setPrefSize(860, 520);
		getDialogPane().setContent(content);
		getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		Node ok = getDialogPane().lookupButton(ButtonType.OK);
		ok.setId("workOk");
		ok.disableProperty().bind(results.getSelectionModel().selectedItemProperty().isNull());
		setResultConverter(button -> button == ButtonType.OK ? results.getSelectionModel().getSelectedItem() : null);
		setOnShown(e -> {
			query.requestFocus();
			if (!query.getText().isBlank()) {
				runSearch();
			}
		});
	}

	/** e.g. "Sefton, Soiland-Reyes … (2019) · Zenodo · 10.5281/… · DataCite" */
	private static String byline(Work work) {
		List<String> parts = new ArrayList<>();
		String authors = work.authors().stream().limit(3)
				.map(a -> a.familyName().orElse(a.name())).collect(Collectors.joining(", "))
				+ (work.authors().size() > 3 ? " et al." : "");
		parts.add((authors + work.year().map(y -> " (" + y + ")").orElse("")).strip());
		work.container().or(work::publisher).ifPresent(parts::add);
		parts.add(work.doi());
		parts.add(work.agency());
		return parts.stream().filter(p -> !p.isBlank()).collect(Collectors.joining(" · "));
	}

	private void runSearch() {
		String text = query.getText().strip();
		if (text.isEmpty()) {
			return;
		}
		boolean doi = Dois.doiOf(text).isPresent();
		status.setText(doi ? "Looking up " + text + "…" : "Searching Crossref and DataCite for " + text + "…");
		results.getItems().clear();
		Task<List<Work>> task = new Task<>() {
			@Override
			protected List<Work> call() throws Exception {
				return search.search(text);
			}
		};
		task.setOnSucceeded(e -> {
			results.getItems().setAll(task.getValue());
			int found = task.getValue().size();
			status.setText(found == 0 ? "Nothing found for " + text
					: found == 1 ? "1 found; choose it and OK" : found + " found, the best of each first; choose one and OK");
			if (found > 0) {
				results.getSelectionModel().selectFirst();
			}
		});
		task.setOnFailed(e -> {
			Throwable problem = task.getException();
			status.setText((doi ? "Can't look up the DOI: " : "Can't search: ")
					+ (problem.getMessage() == null ? problem.toString() : problem.getMessage())
					+ ". You can still give the DOI as https://doi.org/… by hand.");
		});
		Thread thread = new Thread(task, "DOI search");
		thread.setDaemon(true);
		thread.start();
	}

	private void showDetails(Work work) {
		details.getChildren().clear();
		if (work == null) {
			return;
		}
		Label title = new Label(work.title());
		title.setWrapText(true);
		title.setStyle("-fx-font-weight: bold;");
		details.getChildren().add(title);
		List<String> lines = new ArrayList<>();
		lines.add("Type: " + work.type() + ", registered with " + work.agency());
		if (!work.authors().isEmpty()) {
			lines.add("By: " + work.authors().stream()
					.map(a -> a.name() + a.orcid().map(o -> " (ORCID " + o + ")").orElse(""))
					.collect(Collectors.joining("; ")));
		}
		work.issued().ifPresent(i -> lines.add("Published: " + i));
		work.container().ifPresent(c -> lines.add("In: " + c));
		work.publisher().ifPresent(p -> lines.add("Publisher: " + p));
		for (String line : lines) {
			Label label = new Label(line);
			label.setWrapText(true);
			details.getChildren().add(label);
		}
		Optional<String> summary = work.summary();
		if (summary.isPresent()) {
			Label abstractText = new Label(summary.get());
			abstractText.setWrapText(true);
			abstractText.getStyleClass().add("dim-text");
			details.getChildren().add(abstractText);
		}
		Hyperlink link = new Hyperlink(work.iri());
		link.setOnAction(e -> browse.accept(work.iri()));
		details.getChildren().add(link);
	}
}
