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

import java.io.InputStream;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.parser.Parser;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class TestHelpDialog extends ApplicationTest {
	private Stage stage;

	@Override
	public void start(Stage stage) {
		Theme.choose(Theme.Style.CLASSIC, Theme.Colours.LIGHT);
		this.stage = stage;
		stage.setScene(new Scene(new StackPane(), 200, 100));
		stage.show();
	}

	@Test
	public void helpFollowsStylesAndColoursWhileOpen() {
		HelpDialog[] dialog = new HelpDialog[1];
		interact(() -> {
			dialog[0] = new HelpDialog(stage, HelpDialog.Topic.CONTENTS, address -> {});
			dialog[0].show();
		});
		Label title = lookup(".help-title").queryAs(Label.class);
		Region sidebar = lookup(".help-sidebar").queryAs(Region.class);
		TextFlow content = lookup("#helpContent").queryAs(TextFlow.class);
		Text text = content.getChildren().stream().filter(Text.class::isInstance)
				.map(Text.class::cast).findFirst().orElseThrow();
		Hyperlink link = content.getChildren().stream().filter(Hyperlink.class::isInstance)
				.map(Hyperlink.class::cast).findFirst().orElseThrow();
		ListView<?> topics = lookup("#helpTopics").queryListView();

		interact(() -> {
			Theme.choose(Theme.Style.MACOS, Theme.Colours.LIGHT);
			dialog[0].getDialogPane().applyCss();
		});
		assertEquals(Color.web("#e3e3e3"), sidebar.getBackground().getFills().get(0).getFill());
		assertEquals(Color.web("#262626"), text.getFill());
		assertEquals(Color.web("#0068da"), link.getTextFill());
		assertEquals(15, title.getFont().getSize(), 0.1);

		interact(() -> {
			Theme.choose(Theme.Style.MACOS, Theme.Colours.DARK);
			dialog[0].getDialogPane().applyCss();
		});
		assertEquals(Color.web("#1e1e1e"), content.getBackground().getFills().get(0).getFill());
		assertEquals(Color.web("#dfdfdf"), text.getFill());
		assertEquals(Color.web("#419cff"), link.getTextFill());

		interact(() -> {
			Theme.choose(Theme.Style.WARP, Theme.Colours.DARK);
			dialog[0].getDialogPane().applyCss();
		});
		assertEquals(Color.web("#3d2bd0"), title.getBackground().getFills().get(0).getFill());
		assertEquals(Color.WHITE, title.getTextFill());
		assertEquals(Color.web("#262626"), content.getBackground().getFills().get(0).getFill());
		assertEquals(Color.web("#e4e4e4"), text.getFill());
		assertEquals(Color.web("#9d93ff"), link.getTextFill());
		assertEquals("Contents", topics.getSelectionModel().getSelectedItem().toString());

		interact(() -> {
			Theme.choose(Theme.Style.WIN11, Theme.Colours.LIGHT);
			dialog[0].getDialogPane().applyCss();
		});
		assertEquals(Color.web("#fbfbfb"), content.getBackground().getFills().get(0).getFill());
		assertEquals(Color.web("#1b1b1b"), text.getFill());

		interact(() -> {
			Theme.choose(Theme.Style.WIN11, Theme.Colours.DARK);
			dialog[0].getDialogPane().applyCss();
		});
		assertEquals(Color.web("#2b2b2b"), content.getBackground().getFills().get(0).getFill());
		assertEquals(Color.WHITE, text.getFill());

		interact(() -> {
			Theme.choose(Theme.Style.CLASSIC, Theme.Colours.DARK);
			dialog[0].getDialogPane().applyCss();
		});
		assertEquals(Color.web("#1f1f1f"), content.getBackground().getFills().get(0).getFill());
		assertEquals(Color.web("#cccccc"), text.getFill());
		clickOn("Close");
	}

	@Test
	public void opensAtContextAndSearchesContent() {
		HelpDialog[] dialog = new HelpDialog[1];
		interact(() -> {
			dialog[0] = new HelpDialog(stage, HelpDialog.Topic.PROFILES, address -> {});
			dialog[0].show();
		});
		assertEquals(Modality.NONE, dialog[0].getModality());
		ListView<?> topics = lookup("#helpTopics").queryListView();
		TextFlow content = lookup("#helpContent").queryAs(TextFlow.class);
		assertEquals("Profiles", topics.getSelectionModel().getSelectedItem().toString());
		assertTrue(text(content).contains("profile"));
		assertTrue(content.getAccessibleText().contains("profile"));

		TextField search = lookup("#helpSearch").queryAs(TextField.class);
		interact(() -> search.setText("sensitive data"));
		assertEquals(1, topics.getItems().size());
		assertEquals("Getting started", topics.getSelectionModel().getSelectedItem().toString());
		assertTrue(text(content).contains("sensitive data"));

		interact(() -> search.setText("no such help topic"));
		assertTrue(topics.getItems().isEmpty());
		assertEquals("", text(content));
		interact(search::clear);
		assertEquals(HelpDialog.Topic.values().length, topics.getItems().size());
		assertNotNull(topics.getSelectionModel().getSelectedItem());
		clickOn("Close");
	}

	@Test
	public void contentsLinksOpenBundledTopics() {
		HelpDialog[] dialog = new HelpDialog[1];
		interact(() -> {
			dialog[0] = new HelpDialog(stage, HelpDialog.Topic.CONTENTS, address -> fail("Must stay offline"));
			dialog[0].show();
		});
		ListView<?> topics = lookup("#helpTopics").queryListView();
		TextFlow content = lookup("#helpContent").queryAs(TextFlow.class);
		List<String> links = new ArrayList<>();
		Parser.builder().build().parse(HelpDialog.Topic.CONTENTS.content).accept(new AbstractVisitor() {
			@Override
			public void visit(org.commonmark.node.Link link) {
				assertTrue("Unknown topic: " + link.getDestination(),
						HelpDialog.Topic.fromResource(link.getDestination()).isPresent());
				links.add(link.getDestination());
			}
		});
		assertEquals(HelpDialog.Topic.values().length - 1, links.size());
		assertEquals(links.size(), links.stream().distinct().count());
		TextField search = lookup("#helpSearch").queryAs(TextField.class);
		interact(() -> search.setText("Choose a topic in the list"));
		assertEquals(1, topics.getItems().size());
		Hyperlink types = content.getChildren().stream().filter(Hyperlink.class::isInstance)
				.map(Hyperlink.class::cast).filter(link -> link.getText().equals("Types")).findFirst().orElseThrow();
		interact(types::fire);
		assertEquals("Types", topics.getSelectionModel().getSelectedItem().toString());
		assertEquals("", search.getText());
		assertTrue(text(content).startsWith("Types"));
		clickOn("Close");
	}

	/** The names of a page's headings, as links to them have them */
	private static List<String> anchors(HelpDialog.Topic topic) {
		List<String> anchors = new ArrayList<>();
		Parser.builder().build().parse(topic.content).accept(new AbstractVisitor() {
			@Override
			public void visit(org.commonmark.node.Heading heading) {
				StringBuilder text = new StringBuilder();
				heading.accept(new AbstractVisitor() {
					@Override
					public void visit(org.commonmark.node.Text value) {
						text.append(value.getLiteral());
					}

					@Override
					public void visit(org.commonmark.node.Code code) {
						text.append(code.getLiteral());
					}
				});
				anchors.add(HelpMarkdown.anchor(text.toString()));
			}
		});
		return anchors;
	}

	/** Links to pages and their sections (page.md#section, #section) lead somewhere */
	@Test
	public void localTopicLinksAreRegistered() {
		for (HelpDialog.Topic topic : HelpDialog.Topic.values()) {
			Parser.builder().build().parse(topic.content).accept(new AbstractVisitor() {
				@Override
				public void visit(org.commonmark.node.Link link) {
					String destination = link.getDestination();
					if (destination.startsWith("http:") || destination.startsWith("https:")) {
						return;
					}
					assertTrue("Not a help link in " + topic + ": " + destination, HelpMarkdown.isHelpLink(destination));
					int hash = destination.indexOf('#');
					HelpDialog.Topic target = hash == 0 ? topic
							: HelpDialog.Topic.fromResource(hash < 0 ? destination : destination.substring(0, hash))
									.orElseThrow();
					if (hash >= 0) {
						assertTrue("No such section in " + topic + ": " + destination + " of " + anchors(target),
								anchors(target).contains(destination.substring(hash + 1)));
					}
				}
			});
		}
	}

	@Test
	public void anchors() {
		assertEquals("attached-and-detached-crates", HelpMarkdown.anchor("Attached and detached crates"));
		assertEquals("show-in-explorer-or-finder", HelpMarkdown.anchor("Show in Explorer or Finder"));
		assertEquals("1-prepare-a-folder", HelpMarkdown.anchor("1. Prepare a folder"));
	}

	/** A link to a section of another page opens it there */
	@Test
	public void sectionLinksScroll() {
		HelpDialog[] dialog = new HelpDialog[1];
		interact(() -> {
			dialog[0] = new HelpDialog(stage, HelpDialog.Topic.FILES, address -> fail("Must stay offline"));
			dialog[0].show();
		});
		ListView<?> topics = lookup("#helpTopics").queryListView();
		TextFlow content = lookup("#helpContent").queryAs(TextFlow.class);
		Hyperlink attached = content.getChildren().stream().filter(Hyperlink.class::isInstance)
				.map(Hyperlink.class::cast).filter(link -> link.getText().equals("Attached and detached crates"))
				.findFirst().orElseThrow();
		interact(attached::fire);
		WaitForAsyncUtils.waitForFxEvents();
		assertEquals("Folders and files", topics.getSelectionModel().getSelectedItem().toString());
		assertTrue(lookup("#helpPage").queryAs(ScrollPane.class).getVvalue() > 0);
		clickOn("Close");
	}

	@Test
	public void rendersMarkdownAndOpensWebLinks() {
		TextFlow flow = new TextFlow();
		List<String> browsed = new ArrayList<>();
		interact(() -> HelpMarkdown.show(flow, "# Heading\n\nA **bold** word and "
				+ "[a *long* link](https://example.org).\n\n- One\n- Two", browsed::add,
				new SimpleDoubleProperty(400)));
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

	@Test
	public void imagesResizeAndMissingOrUnsafePathsAreVisible() {
		TextFlow flow = new TextFlow();
		SimpleDoubleProperty width = new SimpleDoubleProperty(300);
		interact(() -> HelpMarkdown.show(flow, "![Dataset form](images/dataset-form.png)\n\n"
				+ "![Missing](images/missing.png)\n\n![Remote](https://example.org/image.png)\n\n"
				+ "![Traversal](images/../outside.png)", address -> fail("Must not open a URL"), width));
		ImageView image = flow.getChildren().stream().filter(ImageView.class::isInstance)
				.map(ImageView.class::cast).findFirst().orElseThrow();
		assertEquals("Dataset form", image.getAccessibleText());
		assertTrue(flow.getAccessibleText().contains("Dataset form"));
		assertTrue(image.isPreserveRatio());
		assertEquals(300, image.getFitWidth(), 0.1);
		interact(() -> width.set(150));
		assertEquals(150, image.getFitWidth(), 0.1);
		interact(() -> width.set(10000));
		assertEquals(image.getImage().getWidth(), image.getFitWidth(), 0.1);
		assertTrue(text(flow).contains("Image unavailable: Missing"));
		assertTrue(text(flow).contains("Image unavailable: Remote"));
		assertTrue(text(flow).contains("Image unavailable: Traversal"));
		assertTrue(flow.getAccessibleText().contains("Image unavailable: Missing"));
	}

	@Test
	public void helpScreenshotsFitTheResizedViewport() {
		HelpDialog[] dialog = new HelpDialog[1];
		interact(() -> {
			dialog[0] = new HelpDialog(stage, HelpDialog.Topic.GETTING_STARTED, address -> {});
			dialog[0].show();
		});
		ScrollPane page = lookup("#helpPage").queryAs(ScrollPane.class);
		TextFlow flow = lookup("#helpContent").queryAs(TextFlow.class);
		ImageView image = flow.getChildren().stream().filter(ImageView.class::isInstance)
				.map(ImageView.class::cast).findFirst().orElseThrow();
		WaitForAsyncUtils.waitForFxEvents();
		double original = image.getFitWidth();
		assertTrue(original > 1 && original <= page.getViewportBounds().getWidth());
		interact(() -> ((Stage) dialog[0].getDialogPane().getScene().getWindow()).setWidth(500));
		WaitForAsyncUtils.waitForFxEvents();
		assertTrue(image.getFitWidth() < original);
		assertTrue(image.getFitWidth() <= page.getViewportBounds().getWidth());
		clickOn("Close");
	}

	@Test
	public void allHelpImagesAreBundledAndReadable() {
		List<String> paths = new ArrayList<>();
		for (HelpDialog.Topic topic : HelpDialog.Topic.values()) {
			int previousCount = paths.size();
			Parser.builder().build().parse(topic.content).accept(new AbstractVisitor() {
				@Override
				public void visit(org.commonmark.node.Image image) {
					assertTrue("Invalid image path: " + image.getDestination(), HelpMarkdown.imagePath(image.getDestination()));
					assertNotNull("Image needs alt text", image.getFirstChild());
					paths.add(image.getDestination());
				}
			});
			TextFlow flow = new TextFlow();
			interact(() -> HelpMarkdown.show(flow, topic.content, address -> fail("Must not open a URL"),
					new SimpleDoubleProperty(400)));
			List<ImageView> images = flow.getChildren().stream().filter(ImageView.class::isInstance)
					.map(ImageView.class::cast).toList();
			assertEquals("Rendered images in " + topic, paths.size() - previousCount, images.size());
			for (ImageView image : images) {
				assertNotNull(image.getImage());
				assertFalse(image.getImage().isError());
				assertTrue(image.getImage().getWidth() > 0 && image.getImage().getHeight() > 0);
				assertNotNull(image.getAccessibleText());
				assertFalse(image.getAccessibleText().isBlank());
			}
		}
		assertFalse("Expected screenshots in the help", paths.isEmpty());
		for (String path : paths) {
			try (InputStream in = HelpMarkdown.class.getResourceAsStream("help/" + path)) {
				assertNotNull("Missing help image: " + path, in);
				javafx.scene.image.Image image = new javafx.scene.image.Image(in);
				assertFalse("Unreadable help image: " + path, image.isError());
				assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
			} catch (java.io.IOException e) {
				throw new AssertionError("Can't read help image: " + path, e);
			}
		}
	}

	static String text(TextFlow flow) {
		return flow.getChildren().stream().filter(Text.class::isInstance).map(Text.class::cast)
				.map(Text::getText).reduce("", String::concat);
	}
}
