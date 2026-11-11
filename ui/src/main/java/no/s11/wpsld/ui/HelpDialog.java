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
import java.util.function.Consumer;

import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.stage.Window;

/** Offline help, opened at the contents or at a topic from the editor. */
class HelpDialog extends Dialog<Void> {
	enum Topic {
		GETTING_STARTED("Getting started", "getting-started.md"),
		PROFILES("Profiles", "profiles.md");

		final String title;
		final String content;

		Topic(String title, String resource) {
			this.title = title;
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
	}

	HelpDialog(Window owner, Topic initialTopic, Consumer<String> browse) {
		initOwner(owner);
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
		content.getStyleClass().add("help-content");
		content.setPadding(new Insets(8));
		topics.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) ->
				HelpMarkdown.show(content, selected == null ? "" : selected.content, browse));
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

		ScrollPane page = new ScrollPane(content);
		page.setFitToWidth(true);
		SplitPane pages = new SplitPane(topics, page);
		pages.setDividerPositions(0.3);
		VBox.setVgrow(pages, Priority.ALWAYS);
		VBox root = new VBox(8, search, pages);
		root.setPadding(new Insets(4, 0, 0, 0));
		root.setPrefSize(720, 480);
		getDialogPane().setContent(root);
		getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
	}
}
