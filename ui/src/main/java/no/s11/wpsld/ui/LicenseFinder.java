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
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
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
import no.s11.wpsld.lookup.SPDXLicenses;
import no.s11.wpsld.lookup.SPDXLicenses.License;

/**
 * Choose a licence from the SPDX License List bundled with wpsld, found by
 * its identifier or name while typing. Gives the licence chosen on OK.
 */
class LicenseFinder extends Dialog<License> {
	private final SPDXLicenses spdx;
	private final TextField query = new TextField();
	private final CheckBox deprecated = new CheckBox("Show deprecated identifiers");
	private final ListView<License> results = new ListView<>();
	private final Label count = new Label();
	private final VBox details = new VBox(6);
	private final Consumer<String> browse;

	LicenseFinder(Window owner, String initial, SPDXLicenses spdx, Consumer<String> browse) {
		this.spdx = Objects.requireNonNull(spdx);
		this.browse = Objects.requireNonNull(browse);
		initOwner(owner);
		setTitle("Choose a licence");
		setHeaderText("Choose a licence from the SPDX License List");
		setResizable(true);

		query.setId("licenseQuery");
		query.setPromptText("Identifier or name, e.g. CC-BY-4.0, Apache or MIT");
		query.setText(initial);
		query.textProperty().addListener((obs, old, now) -> filter());
		// Down from the search into the list
		query.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.DOWN && !results.getItems().isEmpty()) {
				results.requestFocus();
				e.consume();
			}
		});
		HBox.setHgrow(query, Priority.ALWAYS);
		deprecated.setId("licenseDeprecated");
		deprecated.selectedProperty().addListener((obs, old, now) -> filter());
		HBox searchBar = new HBox(12, query, deprecated);
		searchBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

		results.setId("licenseResults");
		results.setPlaceholder(new Label("No licence matches"));
		results.setCellFactory(view -> new ListCell<>() {
			{
				// Long names wrap within the list, rather than scrolling it sideways
				setWrapText(true);
				setPrefWidth(0);
			}

			@Override
			protected void updateItem(License license, boolean empty) {
				super.updateItem(license, empty);
				setText(empty || license == null ? null : license.name() + "\n" + license.id() + notes(license));
			}
		});
		results.getSelectionModel().selectedItemProperty().addListener((obs, old, license) -> showDetails(license));
		results.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2
					&& results.getSelectionModel().getSelectedItem() != null) {
				setResult(results.getSelectionModel().getSelectedItem());
				close();
			}
		});
		details.setId("licenseDetails");
		details.setPadding(new Insets(8));
		SplitPane split = new SplitPane(results, details);
		split.setDividerPositions(0.6);
		VBox.setVgrow(split, Priority.ALWAYS);
		count.setId("licenseCount");
		Hyperlink about = new Hyperlink("SPDX License List " + spdx.getVersion() + " (CC BY 3.0)");
		about.setOnAction(e -> browse.accept("https://spdx.org/licenses/"));
		VBox content = new VBox(8, searchBar, split, count, about);
		content.setPadding(new Insets(4));
		content.setPrefSize(820, 480);
		getDialogPane().setContent(content);
		getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		Node ok = getDialogPane().lookupButton(ButtonType.OK);
		ok.setId("licenseOk");
		ok.disableProperty().bind(results.getSelectionModel().selectedItemProperty().isNull());
		setResultConverter(button -> button == ButtonType.OK ? results.getSelectionModel().getSelectedItem() : null);
		filter();
		setOnShown(e -> query.requestFocus());
	}

	/** e.g. " · OSI approved · deprecated" */
	private static String notes(License license) {
		List<String> notes = new ArrayList<>();
		if (license.osiApproved()) {
			notes.add("OSI approved");
		}
		if (license.fsfLibre()) {
			notes.add("FSF free");
		}
		if (license.deprecated()) {
			notes.add("deprecated");
		}
		return notes.isEmpty() ? "" : " · " + String.join(" · ", notes);
	}

	private void filter() {
		List<License> found = spdx.search(query.getText()).stream()
				.filter(l -> deprecated.isSelected() || !l.deprecated()).toList();
		results.getItems().setAll(found);
		count.setText(found.size() == 1 ? "1 licence" : found.size() + " licences");
		if (!found.isEmpty()) {
			results.getSelectionModel().selectFirst();
		}
	}

	private void showDetails(License license) {
		details.getChildren().clear();
		if (license == null) {
			return;
		}
		Label name = new Label(license.name());
		name.setWrapText(true);
		name.setStyle("-fx-font-weight: bold;");
		Label id = new Label("SPDX identifier: " + license.id() + notes(license));
		id.setWrapText(true);
		details.getChildren().addAll(name, id);
		if (license.deprecated()) {
			Label note = new Label("SPDX has deprecated this identifier: choose its replacement, "
					+ "e.g. one ending in -only or -or-later.");
			note.setWrapText(true);
			note.getStyleClass().add("dim-text");
			details.getChildren().add(note);
		}
		details.getChildren().add(link("SPDX entry " + license.id(), license.page()));
		license.seeAlso().forEach(address -> details.getChildren().add(link(address, address)));
	}

	private Hyperlink link(String text, String address) {
		Hyperlink link = new Hyperlink(text);
		link.setWrapText(true);
		link.setOnAction(e -> browse.accept(address));
		return link;
	}
}
