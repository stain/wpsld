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

import static org.junit.Assert.*;

import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

public class TestHelpDialog extends ApplicationTest {
	private Stage stage;

	@Override
	public void start(Stage stage) {
		this.stage = stage;
		stage.setScene(new Scene(new StackPane(), 200, 100));
		stage.show();
	}

	@Test
	public void opensAtContextAndSearchesContent() {
		interact(() -> new HelpDialog(stage, HelpDialog.Topic.PROFILES, address -> {}).show());
		ListView<?> topics = lookup("#helpTopics").queryListView();
		TextFlow content = lookup("#helpContent").queryAs(TextFlow.class);
		assertEquals("Profiles", topics.getSelectionModel().getSelectedItem().toString());
		assertTrue(text(content).contains("profile"));
		assertTrue(content.getAccessibleText().contains("profile"));

		TextField search = lookup("#helpSearch").queryAs(TextField.class);
		interact(() -> search.setText("organisations"));
		assertEquals(1, topics.getItems().size());
		assertEquals("Getting started", topics.getSelectionModel().getSelectedItem().toString());
		assertTrue(text(content).contains("organisations"));

		interact(() -> search.setText("no such help topic"));
		assertTrue(topics.getItems().isEmpty());
		assertEquals("", text(content));
		interact(search::clear);
		assertEquals(2, topics.getItems().size());
		assertNotNull(topics.getSelectionModel().getSelectedItem());
		clickOn("Close");
	}

	@Test
	public void rendersMarkdownAndOpensWebLinks() {
		TextFlow flow = new TextFlow();
		List<String> browsed = new ArrayList<>();
		interact(() -> HelpMarkdown.show(flow, "# Heading\n\nA **bold** word and "
				+ "[a *long* link](https://example.org).\n\n- One\n- Two", browsed::add));
		assertTrue(text(flow).contains("Heading"));
		assertTrue(text(flow).contains("• One"));
		assertTrue(flow.getChildren().stream().filter(Text.class::isInstance).map(Text.class::cast)
				.anyMatch(t -> t.getText().equals("bold") && t.getStyle().contains("bold")));
		Hyperlink link = flow.getChildren().stream().filter(Hyperlink.class::isInstance)
				.map(Hyperlink.class::cast).findFirst().orElseThrow();
		assertEquals("a long link", link.getText());
		interact(link::fire);
		assertEquals(List.of("https://example.org"), browsed);
	}

	static String text(TextFlow flow) {
		return flow.getChildren().stream().filter(Text.class::isInstance).map(Text.class::cast)
				.map(Text::getText).reduce("", String::concat);
	}
}
