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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;

import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * GUI tests of the OS/2-style notebook, run headless.
 */
public class TestNotebook extends ApplicationTest {
	private Notebook notebook;
	/** How many pages have been built */
	private final AtomicInteger built = new AtomicInteger();

	private Notebook.Page page(String title, String text) {
		return new Notebook.Page(title, () -> {
			built.incrementAndGet();
			return new Label(text);
		});
	}

	@Override
	public void start(Stage stage) {
		notebook = new Notebook();
		notebook.getTabs().setAll(new Notebook.Tab("Screen", page("Screen", "screen page")),
				new Notebook.Tab("Window", List.of(page("Window", "window 1"), page("Window", "window 2"),
						page("Window", "window 3"))),
				new Notebook.Tab("Menu", page("Menu", "menu page")));
		notebook.getButtons().setAll(new Button("Undo"), new Button("Default"), new Button("Help"));
		// Not the scene's root, so its width can be narrowed without
		// resizing the window (which TestFX shares between test classes)
		StackPane root = new StackPane(notebook);
		StackPane.setAlignment(notebook, Pos.TOP_LEFT);
		stage.setScene(new Scene(root, 600, 400));
		stage.show();
	}

	private Label tab(String text) {
		return lookup(".notebook-tab").queryAllAs(Label.class).stream().filter(l -> l.getText().equals(text))
				.findFirst().orElseThrow();
	}

	private String status() {
		return lookup(".notebook-status").queryAs(Label.class).getText();
	}

	@Test
	public void firstTabShown() {
		assertEquals(0, notebook.selectedTabProperty().get());
		assertTrue(lookup("screen page").tryQuery().isPresent());
		assertEquals("Screen", lookup(".notebook-page-title").queryAs(Label.class).getText());
		// One page: no status
		assertEquals("", status());
		assertEquals(1, built.get());
		assertTrue(lookup("Help").tryQuery().isPresent());
	}

	@Test
	public void selectedTabInFront() {
		clickOn(tab("Window"));
		assertEquals(1, notebook.selectedTabProperty().get());
		assertTrue(tab("Window").getPseudoClassStates().stream().anyMatch(p -> p.getPseudoClassName().equals("selected")));
		// Drawn above the overlapping neighbours
		assertTrue(tab("Window").getViewOrder() < tab("Screen").getViewOrder());
		assertTrue(tab("Window").getViewOrder() < tab("Menu").getViewOrder());
		assertEquals("Page 1 of 3", status());
	}

	@Test
	public void dogEarTurnsPages() {
		clickOn(tab("Window"));
		Node dogEar = lookup("#dogEar").query();
		// The lower half turns forward
		clickOn(point(dogEar).atPosition(Pos.BOTTOM_LEFT).atOffset(new Point2D(4, -4)));
		assertEquals("Page 2 of 3", status());
		assertTrue(lookup("window 2").tryQuery().isPresent());
		clickOn(point(dogEar).atPosition(Pos.BOTTOM_LEFT).atOffset(new Point2D(4, -4)));
		assertEquals("Page 3 of 3", status());
		// Not past the last page
		clickOn(point(dogEar).atPosition(Pos.BOTTOM_LEFT).atOffset(new Point2D(4, -4)));
		assertEquals("Page 3 of 3", status());
		// The upper half turns back
		clickOn(point(dogEar).atPosition(Pos.TOP_RIGHT).atOffset(new Point2D(-4, 4)));
		assertEquals("Page 2 of 3", status());
	}

	@Test
	public void pagesBuiltWhenShown() {
		assertEquals(1, built.get());
		clickOn(tab("Window"));
		assertEquals(2, built.get());
		interact(() -> notebook.turnPage(1));
		assertEquals(3, built.get());
		// Built once
		interact(() -> notebook.turnPage(-1));
		assertEquals(3, built.get());
	}

	@Test
	public void keyboard() {
		clickOn(lookup(".notebook-page").queryAs(Node.class));
		interact(notebook::requestFocus);
		press(KeyCode.CONTROL).type(KeyCode.PAGE_DOWN).release(KeyCode.CONTROL);
		assertEquals(1, notebook.selectedTabProperty().get());
		type(KeyCode.PAGE_DOWN);
		assertEquals("Page 2 of 3", status());
		press(KeyCode.CONTROL).type(KeyCode.PAGE_UP).release(KeyCode.CONTROL);
		assertEquals(0, notebook.selectedTabProperty().get());
	}

	@Test
	public void scrollWhenTabsDontFit() {
		interact(() -> notebook.setMaxWidth(170));
		Node right = lookup(".notebook-scroll-right").query();
		assertTrue("more tabs than room", right.isVisible());
		assertFalse(lookup(".notebook-scroll-left").query().isVisible());
		clickOn(right);
		assertTrue(lookup(".notebook-scroll-left").query().isVisible());
		assertFalse("first tab scrolled away", tab("Screen").isVisible());
	}
}
