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

import java.io.IOException;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import no.s11.wpsld.Settings;
import no.s11.wpsld.lookup.GeoNames;
import no.s11.wpsld.masp.Profile;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.masp.ProfileRegistry;

/**
 * The user's preferences: which profile registries to use, the profiles
 * kept from earlier loads, which can be removed (at once, not on OK), the
 * GeoNames account, and how much to log, with the logs cleared at once.
 * Gives the new {@link Settings} on OK.
 */
class PreferencesDialog extends Dialog<Settings> {
	/** What each log level offered logs */
	private static final Map<String, String> LOG_LEVELS = Map.of("SEVERE", "Errors only", "WARNING",
			"Errors and warnings", "INFO", "Also what it does (the default)", "FINE", "Also details, to find faults",
			"ALL", "Everything");
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
			.withZone(ZoneId.systemDefault());

	/**
	 * @param forgotten Told about each kept profile removed, if it was read,
	 *                  to stop offering it
	 */
	PreferencesDialog(Window owner, Settings settings, ProfileCache profiles, Consumer<Profile> forgotten,
			Consumer<Window> profileHelp, Consumer<String> browse) {
		initOwner(owner);
		setTitle("Preferences");
		setResizable(true);

		VBox registries = new VBox(8);
		registries.setPadding(new Insets(12));
		registries.getChildren().add(new Label("Profile registries to list profiles from in Load profile…, "
				+ "and to find profiles by identifier"));
		Map<ProfileRegistry, CheckBox> enabled = new LinkedHashMap<>();
		int i = 0;
		for (ProfileRegistry registry : profiles.registries()) {
			CheckBox use = new CheckBox(registry.name());
			use.setId("registry" + i++);
			use.setSelected(settings.isRegistryEnabled(registry.name()));
			registry.address().ifPresent(a -> use.setTooltip(new Tooltip(a.toString())));
			Label address = new Label(registry.address().map(Object::toString).orElse(""));
			address.getStyleClass().add("dim-text");
			address.setPadding(new Insets(0, 0, 0, 24));
			registries.getChildren().addAll(use, address);
			enabled.put(registry, use);
		}
		Tab registryTab = new Tab("Profile registries", registries);

		ListView<ProfileCache.Kept> kept = new ListView<>();
		kept.setId("keptProfiles");
		kept.setPlaceholder(new Label("No profiles kept: those you load are kept here for next time"));
		kept.setCellFactory(view -> new ListCell<>() {
			@Override
			protected void updateItem(ProfileCache.Kept item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setText(null);
					setTooltip(null);
				} else {
					setText(item.name().orElse(item.file()) + item.version().map(v -> " " + v).orElse("")
							+ item.retrieved().map(r -> " — " + WHEN.format(r)).orElse(""));
					setTooltip(new Tooltip("Loaded from " + item.location()
							+ (item.source().equals(item.location()) ? "" : "\nvia " + item.source())));
				}
			}
		});
		kept.getItems().setAll(profiles.kept());
		Button remove = new Button("Remove");
		remove.setId("removeKept");
		remove.disableProperty().bind(kept.getSelectionModel().selectedItemProperty().isNull());
		remove.setOnAction(e -> {
			ProfileCache.Kept item = kept.getSelectionModel().getSelectedItem();
			try {
				profiles.forget(item).ifPresent(forgotten);
				kept.getItems().remove(item);
			} catch (IOException ex) {
				Alert alert = new Alert(AlertType.ERROR, ex.toString(), ButtonType.OK);
				alert.initOwner(getDialogPane().getScene().getWindow());
				alert.setHeaderText("Can't remove " + item.file());
				alert.show();
			}
		});
		Label folder = new Label(profiles.store().map(s -> "Kept in " + s).orElse("Not kept"));
		folder.getStyleClass().add("dim-text");
		folder.setWrapText(true);
		VBox keptBox = new VBox(8, new Label("Profiles loaded before, kept for next time"), kept,
				new HBox(8, remove), folder);
		keptBox.setPadding(new Insets(12));
		Tab keptTab = new Tab("Kept profiles", keptBox);

		TextField geonames = new TextField(settings.geonamesUsername());
		geonames.setId("geonamesUsername");
		geonames.setPromptText("Your GeoNames username");
		Hyperlink signUp = new Hyperlink("Make a free GeoNames account");
		signUp.setOnAction(e -> browse.accept(GeoNames.SIGN_UP));
		Hyperlink account = new Hyperlink("Enable the free web services of your account");
		account.setOnAction(e -> browse.accept(GeoNames.ACCOUNT));
		Label geonamesAbout = new Label("Find place… looks places up by name in GeoNames, whose identifiers RO-Crate "
				+ "recommends for places. GeoNames needs a username of your own: make an account, enable its "
				+ "free web services on your account page, and enter the username here.");
		geonamesAbout.setWrapText(true);
		VBox placesBox = new VBox(8, geonamesAbout, new Label("GeoNames username"), geonames, signUp, account);
		placesBox.setPadding(new Insets(12));
		Tab placesTab = new Tab("Places", placesBox);
		placesTab.setId("placesTab");

		ChoiceBox<String> logLevel = new ChoiceBox<>();
		logLevel.setId("logLevel");
		logLevel.getItems().setAll(Settings.LOG_LEVELS);
		logLevel.setConverter(new StringConverter<>() {
			@Override
			public String toString(String level) {
				return level == null ? "" : LOG_LEVELS.getOrDefault(level, level);
			}

			@Override
			public String fromString(String label) {
				return null;
			}
		});
		logLevel.setValue(settings.logLevel());
		Label logsAbout = new Label("wpsld logs what goes wrong, e.g. a file it can't read or a web service that "
				+ "doesn't answer, for when you report a problem. The logs stay on this computer.");
		logsAbout.setWrapText(true);
		Label logsFolder = new Label();
		logsFolder.setId("logsFolder");
		logsFolder.getStyleClass().add("dim-text");
		logsFolder.setWrapText(true);
		Runnable showLogs = () -> logsFolder.setText(Logs.folder().map(dir -> {
			long size = Logs.files().stream().mapToLong(f -> {
				try {
					return Files.size(f);
				} catch (IOException e) {
					return 0;
				}
			}).sum();
			return "Kept in " + dir + " (" + (size < 1024 ? size + " bytes" : size < 1 << 20 ? size / 1024 + " KB"
					: String.format("%.1f MB", size / (double) (1 << 20))) + ")";
		}).orElse("Not logged to a file"));
		showLogs.run();
		Hyperlink openLogs = new Hyperlink("Open the logs' folder");
		openLogs.setId("openLogs");
		openLogs.setDisable(Logs.folder().isEmpty());
		openLogs.setOnAction(e -> Logs.folder().ifPresent(dir -> browse.accept(dir.toUri().toString())));
		Button clearLogs = new Button("Clear logs");
		clearLogs.setId("clearLogs");
		clearLogs.setDisable(Logs.folder().isEmpty());
		clearLogs.setOnAction(e -> {
			try {
				Logs.clear();
			} catch (IOException ex) {
				Alert alert = new Alert(AlertType.ERROR, ex.toString(), ButtonType.OK);
				alert.initOwner(getDialogPane().getScene().getWindow());
				alert.setHeaderText("Can't clear the logs");
				alert.show();
			}
			showLogs.run();
		});
		VBox logsBox = new VBox(8, logsAbout, new Label("What to log"), logLevel, new HBox(8, clearLogs), logsFolder,
				openLogs);
		logsBox.setPadding(new Insets(12));
		Tab logsTab = new Tab("Logs", logsBox);
		logsTab.setId("logsTab");

		TabPane tabs = new TabPane(registryTab, keptTab, placesTab, logsTab);
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		tabs.setPrefSize(640, 400);
		Hyperlink help = new Hyperlink("Help with profiles");
		help.setId("preferencesProfileHelp");
		help.setOnAction(e -> profileHelp.accept(getDialogPane().getScene().getWindow()));
		VBox.setVgrow(tabs, javafx.scene.layout.Priority.ALWAYS);
		getDialogPane().setContent(new VBox(tabs, help));
		getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
			if (HelpDialog.isHelpShortcut(event)) {
				event.consume();
				profileHelp.accept(getDialogPane().getScene().getWindow());
			}
		});
		getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		setResultConverter(button -> {
			if (button != ButtonType.OK) {
				return null;
			}
			Set<String> disabled = enabled.entrySet().stream().filter(e -> !e.getValue().isSelected())
					.map(e -> e.getKey().name()).collect(Collectors.toSet());
			return settings.withDisabledRegistries(disabled).withGeonamesUsername(geonames.getText())
					.withLogLevel(logLevel.getValue());
		});
	}
}
