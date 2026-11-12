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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Window;

/** Offline help, opened at the contents or at a topic from the editor. */
class HelpDialog extends Dialog<Void> {
	enum Topic {
		CONTENTS("Contents", "index.md"),
		GETTING_STARTED("Getting started", "getting-started.md"),
		FILES("Opening and saving crates", "files.md"),
		PROFILES("Profiles", "profiles.md"),
		TYPES("Types", "types.md"),
		ENTITIES("Entities and identifiers", "entities.md"),
		PROPERTIES("Properties and links", "properties.md"),
		CLIPBOARD("Copy and paste", "clipboard.md"),
		VIEWS("Views and styles", "views.md");

		final String title;
		final String resource;
		final String content;

		Topic(String title, String resource) {
			this.title = title;
			this.resource = resource;
			try (InputStream in = HelpDialog.class.getResourceAsStream("help/" + resource)) {
				if (in == null) {
					throw new IllegalStateException("Missing help resource: " + resource);
				}
				content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new UncheckedIOException("Can't read help resource: " + resource, e);
			}
		}

		@Override
		public String toString() {
			return title;
		}

		static Optional<Topic> fromResource(String resource) {
			return java.util.Arrays.stream(values()).filter(topic -> topic.resource.equals(resource)).findFirst();
		}
	}

	static boolean isHelpShortcut(KeyEvent event) {
		return (event.getCode() == KeyCode.F1 && !event.isAltDown() && !event.isControlDown()
				&& !event.isMetaDown() && !event.isShiftDown())
				|| (CrateFile.onMac() && new KeyCodeCombination(KeyCode.SLASH, KeyCombination.SHORTCUT_DOWN,
						KeyCombination.SHIFT_DOWN).match(event));
	}

	HelpDialog(Window owner, Topic initialTopic, Consumer<String> browse) {
		initOwner(owner);
		initModality(Modality.NONE);
		setTitle("wpsld Help");
		setResizable(true);

		TextField search = new TextField();
		search.setId("helpSearch");
		search.setPromptText("Search help");
		search.setAccessibleText("Search help");
		FilteredList<Topic> matches = new FilteredList<>(FXCollections.observableArrayList(List.of(Topic.values())));
		ListView<Topic> topics = new ListView<>(matches);
		topics.setId("helpTopics");
		topics.setAccessibleText("Help topics");
		topics.setPlaceholder(new javafx.scene.control.Label("No matching topics."));
		TextFlow content = new TextFlow();
		content.setId("helpContent");
		content.setFocusTraversable(true);
		content.setMinWidth(0);
		content.getStyleClass().add("help-content");
		ScrollPane page = new ScrollPane(content);
		page.setId("helpPage");
		page.setFitToWidth(true);
		DoubleBinding imageWidth = Bindings.createDoubleBinding(
				() -> Math.max(1, page.getViewportBounds().getWidth()
						- content.getPadding().getLeft() - content.getPadding().getRight()),
				page.viewportBoundsProperty(), content.paddingProperty());
		Consumer<String> openLink = target -> Topic.fromResource(target).ifPresentOrElse(topic -> {
			search.clear();
			topics.getSelectionModel().select(topic);
		}, () -> browse.accept(target));
		topics.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) ->
				HelpMarkdown.show(content, selected == null ? "" : selected.content, openLink, imageWidth));
		search.textProperty().addListener((obs, old, query) -> {
			String term = query.strip().toLowerCase(Locale.ROOT);
			Topic selected = topics.getSelectionModel().getSelectedItem();
			matches.setPredicate(topic -> (topic.title + "\n" + topic.content).toLowerCase(Locale.ROOT).contains(term));
			if (selected != null && matches.contains(selected)) {
				topics.getSelectionModel().select(selected);
			} else if (!matches.isEmpty()) {
				topics.getSelectionModel().selectFirst();
			}
		});
		topics.getSelectionModel().select(Objects.requireNonNull(initialTopic));

		Label title = new Label("wpsld Help");
		title.getStyleClass().add("help-title");
		title.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(title, Priority.ALWAYS);
		search.setPrefColumnCount(18);
		search.setMaxWidth(240);
		HBox toolbar = new HBox(10, title, search);
		toolbar.getStyleClass().add("help-toolbar");
		Label contents = new Label("Contents");
		contents.getStyleClass().add("help-sidebar-title");
		VBox sidebar = new VBox(4, contents, topics);
		sidebar.getStyleClass().add("help-sidebar");
		sidebar.setMinWidth(130);
		VBox.setVgrow(topics, Priority.ALWAYS);
		SplitPane pages = new SplitPane(sidebar, page);
		pages.getStyleClass().add("help-pages");
		pages.setDividerPositions(0.3);
		VBox.setVgrow(pages, Priority.ALWAYS);
		VBox root = new VBox(toolbar, pages);
		root.setPrefSize(720, 480);
		getDialogPane().getStyleClass().add("help-dialog");
		getDialogPane().setContent(root);
		getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
	}
}
