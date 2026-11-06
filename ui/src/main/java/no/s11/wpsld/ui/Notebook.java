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
import java.util.function.Supplier;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * A notebook in the style of the OS/2 Workplace Shell's Properties notebook
 * (the Presentation Manager <code>WC_NOTEBOOK</code> control, Warp 4's
 * tabbed style): overlapping pastel tabs along the top with the selected one
 * in front, a page header with the page's title, its status ("Page 1 of 3")
 * and a dog-ear corner to turn the pages of a tab, and optional buttons along
 * the bottom (like Undo, Default and Help).
 * <p>
 * Each {@link Tab} has one or more {@link Page}s, built when first shown.
 * Keyboard: Ctrl+PageDown/PageUp change tab, PageDown/PageUp turn pages.
 * Styled by <code>notebook.css</code>.
 */
public class Notebook extends BorderPane {

	/**
	 * A page of a tab, its content built when first shown.
	 */
	public static final class Page {
		private final String title;
		private final Supplier<Node> content;
		private Node built;

		public Page(String title, Supplier<Node> content) {
			this.title = Objects.requireNonNull(title);
			this.content = Objects.requireNonNull(content);
		}

		public String getTitle() {
			return title;
		}

		public boolean isBuilt() {
			return built != null;
		}

		Node content() {
			if (built == null) {
				built = content.get();
			}
			return built;
		}
	}

	/**
	 * A major tab, with its pages.
	 */
	public static final class Tab {
		private final String text;
		private final List<Page> pages;

		public Tab(String text, List<Page> pages) {
			if (pages.isEmpty()) {
				throw new IllegalArgumentException("A tab needs at least one page");
			}
			this.text = Objects.requireNonNull(text);
			this.pages = List.copyOf(pages);
		}

		public Tab(String text, Page page) {
			this(text, List.of(page));
		}

		public String getText() {
			return text;
		}

		public List<Page> getPages() {
			return pages;
		}
	}

	/** Tab colours, as Warp 4's: cyan, green, blue, pink, yellow, mauve, orange */
	public static final int COLOURS = 7;
	/** How much neighbouring tabs overlap */
	private static final double OVERLAP = 10;

	private final ObservableList<Tab> tabs = FXCollections.observableArrayList();
	private final ReadOnlyIntegerWrapper selectedTab = new ReadOnlyIntegerWrapper(-1);
	private final ReadOnlyIntegerWrapper pageIndex = new ReadOnlyIntegerWrapper(0);
	/** The first tab shown in the tab row, when they don't all fit */
	private final IntegerProperty firstTab = new SimpleIntegerProperty(0);

	private final TabRow tabRow = new TabRow();
	private final Button scrollLeft = arrow("notebook-scroll-left", "Previous tabs");
	private final Button scrollRight = arrow("notebook-scroll-right", "More tabs");
	private final Label title = new Label();
	private final Label status = new Label();
	private final StackPane content = new StackPane();
	private final HBox buttons = new HBox(8);

	public Notebook() {
		getStyleClass().add("notebook");
		getStylesheets().add(Notebook.class.getResource("notebook.css").toExternalForm());

		HBox tabArea = new HBox(tabRow, scrollLeft, scrollRight);
		tabArea.getStyleClass().add("notebook-tab-area");
		tabArea.setAlignment(Pos.BOTTOM_LEFT);
		HBox.setHgrow(tabRow, Priority.ALWAYS);
		scrollLeft.setOnAction(e -> firstTab.set(Math.max(0, firstTab.get() - 1)));
		scrollRight.setOnAction(e -> firstTab.set(Math.min(tabs.size() - 1, firstTab.get() + 1)));
		firstTab.addListener((obs, old, first) -> tabRow.requestLayout());
		setTop(tabArea);

		title.getStyleClass().add("notebook-page-title");
		status.getStyleClass().add("notebook-status");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox header = new HBox(8, title, spacer, status, dogEar());
		header.getStyleClass().add("notebook-page-header");
		header.setAlignment(Pos.CENTER_LEFT);
		content.getStyleClass().add("notebook-page-content");
		VBox.setVgrow(content, Priority.ALWAYS);
		VBox page = new VBox(header, content);
		page.getStyleClass().add("notebook-page");
		setCenter(page);

		buttons.getStyleClass().add("notebook-buttons");
		buttons.setAlignment(Pos.CENTER);
		buttons.managedProperty().bind(buttons.visibleProperty());
		buttons.setVisible(false);
		setBottom(buttons);

		tabs.addListener((ListChangeListener<Tab>) change -> {
			tabRow.rebuild();
			select(tabs.isEmpty() ? -1 : Math.min(Math.max(selectedTab.get(), 0), tabs.size() - 1), 0);
		});
		setFocusTraversable(true);
		setOnKeyPressed(e -> {
			if (e.isControlDown() && e.getCode() == KeyCode.PAGE_DOWN) {
				selectTab(selectedTab.get() + 1);
			} else if (e.isControlDown() && e.getCode() == KeyCode.PAGE_UP) {
				selectTab(selectedTab.get() - 1);
			} else if (e.getCode() == KeyCode.PAGE_DOWN) {
				turnPage(1);
			} else if (e.getCode() == KeyCode.PAGE_UP) {
				turnPage(-1);
			} else {
				return;
			}
			e.consume();
		});
	}

	public ObservableList<Tab> getTabs() {
		return tabs;
	}

	/**
	 * Buttons along the bottom of the notebook, e.g. Undo, Default, Help.
	 */
	public ObservableList<Node> getButtons() {
		buttons.setVisible(true);
		return buttons.getChildren();
	}

	public ReadOnlyIntegerProperty selectedTabProperty() {
		return selectedTab.getReadOnlyProperty();
	}

	public ReadOnlyIntegerProperty pageIndexProperty() {
		return pageIndex.getReadOnlyProperty();
	}

	public void selectTab(int index) {
		if (index >= 0 && index < tabs.size() && index != selectedTab.get()) {
			select(index, 0);
		}
	}

	/**
	 * Turn the pages of the selected tab forward (positive) or back.
	 */
	public void turnPage(int by) {
		if (selectedTab.get() < 0) {
			return;
		}
		int pages = tabs.get(selectedTab.get()).getPages().size();
		int page = pageIndex.get() + by;
		if (page >= 0 && page < pages) {
			select(selectedTab.get(), page);
		}
	}

	private void select(int tab, int page) {
		selectedTab.set(tab);
		pageIndex.set(page);
		if (tab < 0) {
			title.setText("");
			status.setText("");
			content.getChildren().clear();
			return;
		}
		List<Page> pages = tabs.get(tab).getPages();
		Page shown = pages.get(page);
		title.setText(shown.getTitle());
		status.setText(pages.size() > 1 ? "Page " + (page + 1) + " of " + pages.size() : "");
		content.getChildren().setAll(shown.content());
		// Keep the selected tab in view
		if (tab < firstTab.get()) {
			firstTab.set(tab);
		}
		tabRow.update();
	}

	/**
	 * The folded page corner: its upper half ("+") turns to the next page, its
	 * lower half ("-") to the previous one.
	 */
	private Node dogEar() {
		Region fold = new Region();
		fold.getStyleClass().add("notebook-dog-ear");
		fold.setMinSize(26, 26);
		fold.setPrefSize(26, 26);
		Label plus = new Label("+");
		plus.getStyleClass().add("notebook-dog-ear-sign");
		Label minus = new Label("−");
		minus.getStyleClass().add("notebook-dog-ear-sign");
		StackPane.setAlignment(plus, Pos.TOP_RIGHT);
		StackPane.setAlignment(minus, Pos.BOTTOM_LEFT);
		StackPane ear = new StackPane(fold, plus, minus);
		ear.setId("dogEar");
		ear.getStyleClass().add("notebook-dog-ear-area");
		// Above the fold (the "+" corner) forward, below it (the "-") back
		ear.setOnMouseClicked(e -> turnPage(e.getY() < e.getX() ? 1 : -1));
		return ear;
	}

	private static Button arrow(String styleClass, String tooltip) {
		Button button = new Button();
		button.getStyleClass().addAll("notebook-scroll", styleClass);
		button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
		button.setFocusTraversable(false);
		button.managedProperty().bind(button.visibleProperty());
		return button;
	}

	/**
	 * The tabs, laid out overlapping from the first shown; the selected tab
	 * drawn in front of the others.
	 */
	private class TabRow extends Pane {
		private final List<Label> labels = new ArrayList<>();

		TabRow() {
			getStyleClass().add("notebook-tab-row");
			setMinWidth(0);
		}

		void rebuild() {
			labels.clear();
			for (int i = 0; i < tabs.size(); i++) {
				int index = i;
				Label tab = new Label(tabs.get(i).getText());
				tab.getStyleClass().addAll("notebook-tab", "notebook-tab-" + (i % COLOURS));
				// Selected when pressed, as in OS/2
				tab.setOnMousePressed(e -> selectTab(index));
				labels.add(tab);
			}
			getChildren().setAll(labels);
			update();
		}

		void update() {
			for (int i = 0; i < labels.size(); i++) {
				Label tab = labels.get(i);
				boolean selected = i == selectedTab.get();
				tab.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"), selected);
				// Selected in front, the others overlapping from the left
				tab.setViewOrder(selected ? -1 : i);
			}
			requestLayout();
		}

		@Override
		protected void layoutChildren() {
			double x = snappedLeftInset();
			double bottom = getHeight() - snappedBottomInset();
			double right = getWidth() - snappedRightInset();
			boolean overflow = false;
			for (int i = 0; i < labels.size(); i++) {
				Label tab = labels.get(i);
				if (i < firstTab.get()) {
					tab.setVisible(false);
					continue;
				}
				double width = tab.prefWidth(-1);
				double height = tab.prefHeight(width) + (i == selectedTab.get() ? 3 : 0);
				boolean fits = x + width <= right;
				tab.setVisible(fits);
				overflow |= !fits;
				if (fits) {
					tab.resizeRelocate(x, bottom - height, width, height);
				}
				x += width - OVERLAP;
			}
			scrollLeft.setVisible(firstTab.get() > 0);
			scrollRight.setVisible(overflow);
		}

		@Override
		protected double computePrefHeight(double width) {
			double height = labels.stream().mapToDouble(l -> l.prefHeight(-1)).max().orElse(20);
			return snappedTopInset() + height + 3 + snappedBottomInset();
		}

		@Override
		protected double computePrefWidth(double height) {
			return snappedLeftInset() + labels.stream().mapToDouble(l -> l.prefWidth(-1) - OVERLAP).sum() + OVERLAP
					+ snappedRightInset();
		}
	}
}
