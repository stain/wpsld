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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
import javafx.scene.text.TextFlow;
import javafx.stage.Window;
import no.s11.wpsld.lookup.GeoNames;
import no.s11.wpsld.lookup.GeoNames.Place;
import no.s11.wpsld.crate.Places.Area;

/**
 * Find a place by name in GeoNames: the matches in a list, the one selected
 * on a map. Gives the place chosen on OK.
 */
class PlaceFinder extends Dialog<Place> {
	/** Searches GeoNames, e.g. {@link GeoNames#search}, off the JavaFX thread */
	@FunctionalInterface
	interface Search {
		List<Place> search(String query, String username) throws Exception;
	}

	private final TextField query = new TextField();
	private final ListView<Place> results = new ListView<>();
	private final Label status = new Label();
	private final Search search;
	private final Supplier<String> username;
	private final VBox content = new VBox(8);
	private final Node finder;
	private final Node noAccount;

	/**
	 * @param username        The user's GeoNames username, read again after
	 *                        the preferences are shown
	 * @param showPreferences Shows Edit > Preferences, to set the username
	 */
	PlaceFinder(Window owner, String initial, Supplier<String> username, Search search,
			Consumer<Window> showPreferences, MapTiles tiles, Consumer<String> browse) {
		this.search = Objects.requireNonNull(search);
		this.username = Objects.requireNonNull(username);
		initOwner(owner);
		setTitle("Find a place");
		setHeaderText("Find a place in GeoNames");
		setResizable(true);

		query.setId("placeQuery");
		query.setPromptText("Name of a place, e.g. Catalina Park");
		query.setText(initial);
		HBox.setHgrow(query, Priority.ALWAYS);
		Button find = new Button("Search");
		find.setId("placeSearch");
		find.setOnAction(e -> runSearch());
		query.setOnAction(e -> runSearch());
		HBox searchBar = new HBox(8, query, find);

		results.setId("placeResults");
		results.setPlaceholder(new Label("Search for a place by its name"));
		results.setCellFactory(view -> new ListCell<>() {
			@Override
			protected void updateItem(Place place, boolean empty) {
				super.updateItem(place, empty);
				setText(empty || place == null ? null
						: place.name() + (place.details().isEmpty() ? "" : "\n" + place.details()));
			}
		});
		MapView map = new MapView(tiles);
		map.setBrowse(browse);
		map.setPrefSize(360, 300);
		results.getSelectionModel().selectedItemProperty().addListener((obs, old, place) -> {
			if (place != null) {
				map.show(Optional.of(Area.point(place.latitude(), place.longitude())));
			}
		});
		results.setOnMouseClicked(e -> {
			if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2
					&& results.getSelectionModel().getSelectedItem() != null) {
				setResult(results.getSelectionModel().getSelectedItem());
				close();
			}
		});
		SplitPane split = new SplitPane(results, map);
		split.setDividerPositions(0.45);
		VBox.setVgrow(split, Priority.ALWAYS);
		status.setId("placeStatus");
		status.setWrapText(true);
		Hyperlink about = new Hyperlink("Place names from GeoNames (CC BY 4.0)");
		about.setOnAction(e -> browse.accept("https://www.geonames.org/about.html"));
		VBox finderBox = new VBox(8, searchBar, split, status, about);
		VBox.setVgrow(finderBox, Priority.ALWAYS);
		finder = finderBox;

		// Without a username, how to get one
		Hyperlink signUp = new Hyperlink("make a free GeoNames account");
		signUp.setOnAction(e -> browse.accept(GeoNames.SIGN_UP));
		Hyperlink account = new Hyperlink("enable its free web services");
		account.setOnAction(e -> browse.accept(GeoNames.ACCOUNT));
		TextFlow explain = new TextFlow(new Label("Looking up places needs a GeoNames username. To get one, "),
				signUp, new Label(", then "), account,
				new Label(" on your account page, and enter the username in Edit > Preferences > Places."));
		Button preferences = new Button("Preferences…");
		preferences.setId("placePreferences");
		preferences.setOnAction(e -> {
			showPreferences.accept(getDialogPane().getScene().getWindow());
			showContent();
		});
		noAccount = new VBox(12, explain, preferences);
		noAccount.setId("placeNoAccount");

		content.setPadding(new Insets(4));
		content.setPrefSize(720, 440);
		getDialogPane().setContent(content);
		getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		Node ok = getDialogPane().lookupButton(ButtonType.OK);
		ok.setId("placeOk");
		ok.disableProperty().bind(results.getSelectionModel().selectedItemProperty().isNull());
		setResultConverter(button -> button == ButtonType.OK ? results.getSelectionModel().getSelectedItem() : null);
		showContent();
		setOnShown(e -> {
			query.requestFocus();
			if (!query.getText().isBlank() && !username.get().isBlank()) {
				runSearch();
			}
		});
	}

	private void showContent() {
		content.getChildren().setAll(username.get().isBlank() ? noAccount : finder);
		VBox.setVgrow(content.getChildren().get(0), Priority.ALWAYS);
	}

	private void runSearch() {
		String text = query.getText().strip();
		if (text.isEmpty()) {
			return;
		}
		String user = username.get();
		status.setText("Searching for " + text + "…");
		results.getItems().clear();
		Task<List<Place>> task = new Task<>() {
			@Override
			protected List<Place> call() throws Exception {
				return search.search(text, user);
			}
		};
		task.setOnSucceeded(e -> {
			results.getItems().setAll(task.getValue());
			status.setText(task.getValue().isEmpty() ? "No places named " + text + " found"
					: task.getValue().size() == 1 ? "1 place found; choose it and OK"
							: task.getValue().size() + " places found; choose one and OK");
			if (!task.getValue().isEmpty()) {
				results.getSelectionModel().selectFirst();
			}
		});
		task.setOnFailed(e -> {
			Throwable problem = task.getException();
			status.setText("Can't search GeoNames: "
					+ (problem.getMessage() == null ? problem.toString() : problem.getMessage()));
		});
		Thread thread = new Thread(task, "GeoNames search");
		thread.setDaemon(true);
		thread.start();
	}
}
