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
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import no.s11.wpsld.lookup.PRONOMFormats;
import no.s11.wpsld.lookup.PRONOMFormats.Format;

/**
 * Choose a file format from PRONOM, bundled with wpsld, found by name,
 * extension, media type or PUID while typing. Gives the format chosen on OK.
 */
class FormatFinder extends Dialog<Format> {
	private final PRONOMFormats pronom;
	private final TextField query = new TextField();
	private final ListView<Format> results = new ListView<>();
	private final Label count = new Label();
	private final VBox details = new VBox(6);
	private final Consumer<String> browse;

	/**
	 * @param initial What to search for at first, e.g. the file's media type
	 *                or extension
	 */
	FormatFinder(Window owner, String initial, PRONOMFormats pronom, Consumer<String> browse) {
		this.pronom = Objects.requireNonNull(pronom);
		this.browse = Objects.requireNonNull(browse);
		initOwner(owner);
		setTitle("Choose a file format");
		setHeaderText("Choose a file format from PRONOM");
		setResizable(true);

		query.setId("formatQuery");
		query.setPromptText("Name, extension, media type or PUID, e.g. PDF 1.7, .csv or image/png");
		query.setText(initial);
		query.textProperty().addListener((obs, old, now) -> filter());
		query.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.DOWN && !results.getItems().isEmpty()) {
				results.requestFocus();
				e.consume();
			}
		});
		HBox searchBar = new HBox(12, query);
		searchBar.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(query, Priority.ALWAYS);

		results.setId("formatResults");
		results.setPlaceholder(new Label("No format matches"));
		results.setCellFactory(view -> new ListCell<>() {
			{
				// Long names wrap within the list, rather than scrolling it sideways
				setWrapText(true);
				setPrefWidth(0);
			}

			@Override
			protected void updateItem(Format format, boolean empty) {
				super.updateItem(format, empty);
				setText(empty || format == null ? null : format.name() + "\n" + summary(format));
			}
		});
		results.getSelectionModel().selectedItemProperty().addListener((obs, old, format) -> showDetails(format));
		results.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2
					&& results.getSelectionModel().getSelectedItem() != null) {
				setResult(results.getSelectionModel().getSelectedItem());
				close();
			}
		});
		details.setId("formatDetails");
		details.setPadding(new Insets(8));
		SplitPane split = new SplitPane(results, details);
		split.setDividerPositions(0.6);
		VBox.setVgrow(split, Priority.ALWAYS);
		count.setId("formatCount");
		Label note = new Label("Formats with several versions, such as PDF, are listed once for each: "
				+ "choose the version the file has, if you know it.");
		note.setWrapText(true);
		note.getStyleClass().add("dim-text");
		Hyperlink about = new Hyperlink("PRONOM, The National Archives, signature file V" + pronom.getVersion()
				+ " (Open Government Licence)");
		about.setOnAction(e -> browse.accept("https://pronom.nationalarchives.gov.uk/"));
		VBox content = new VBox(8, searchBar, split, count, note, about);
		content.setPadding(new Insets(4));
		content.setPrefSize(820, 500);
		getDialogPane().setContent(content);
		getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		Node ok = getDialogPane().lookupButton(ButtonType.OK);
		ok.setId("formatOk");
		ok.disableProperty().bind(results.getSelectionModel().selectedItemProperty().isNull());
		setResultConverter(button -> button == ButtonType.OK ? results.getSelectionModel().getSelectedItem() : null);
		filter();
		setOnShown(e -> query.requestFocus());
	}

	/** e.g. "fmt/19 · .pdf · application/pdf" */
	private static String summary(Format format) {
		List<String> parts = new ArrayList<>();
		parts.add(format.puid());
		format.extensions().stream().map(e -> "." + e).forEach(parts::add);
		parts.addAll(format.mediaTypes());
		return String.join(" · ", parts);
	}

	private void filter() {
		List<Format> found = pronom.search(query.getText());
		results.getItems().setAll(found);
		count.setText(found.size() == 1 ? "1 format" : found.size() + " formats");
		if (!found.isEmpty()) {
			results.getSelectionModel().selectFirst();
		}
	}

	private void showDetails(Format format) {
		details.getChildren().clear();
		if (format == null) {
			return;
		}
		Label name = new Label(format.name());
		name.setWrapText(true);
		name.setStyle("-fx-font-weight: bold;");
		details.getChildren().add(name);
		List<String> lines = new ArrayList<>();
		lines.add("PUID: " + format.puid());
		format.version().ifPresent(v -> lines.add("Version: " + v));
		lines.add("Extensions: " + (format.extensions().isEmpty() ? "none"
				: String.join(", ", format.extensions().stream().map(e -> "." + e).toList())));
		lines.add("Media types: " + (format.mediaTypes().isEmpty() ? "none in PRONOM"
				: String.join(", ", format.mediaTypes())));
		for (String line : lines) {
			Label label = new Label(line);
			label.setWrapText(true);
			details.getChildren().add(label);
		}
		Hyperlink page = new Hyperlink("PRONOM definition " + format.puid());
		page.setOnAction(e -> browse.accept(format.page()));
		details.getChildren().add(page);
	}
}
