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
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import no.s11.wpsld.Settings;
import no.s11.wpsld.masp.Profile;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.masp.ProfileRegistry;

/**
 * The user's preferences: which profile registries to use, and the profiles
 * kept from earlier loads, which can be removed (at once, not on OK). Gives
 * the new {@link Settings} on OK.
 */
class PreferencesDialog extends Dialog<Settings> {
	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
			.withZone(ZoneId.systemDefault());

	/**
	 * @param forgotten Told about each kept profile removed, if it was read,
	 *                  to stop offering it
	 */
	PreferencesDialog(Window owner, Settings settings, ProfileCache profiles, Consumer<Profile> forgotten,
			Consumer<Window> profileHelp) {
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

		TabPane tabs = new TabPane(registryTab, keptTab);
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
			return settings.withDisabledRegistries(disabled);
		});
	}
}
