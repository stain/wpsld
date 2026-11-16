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
import java.util.function.Consumer;

import javafx.animation.PauseTransition;
import javafx.geometry.Side;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.chart.NumberAxis;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * The background jobs (see {@link Jobs}) as a strip at the foot of the
 * window, while any run: the latest's progress and message, how many more,
 * and Show… to bring up their window. That window, not modal, has each
 * job's progress, message, what it is at and status lines, with Cancel;
 * Run in Background hides it, as closing it does, and Help tells about
 * them. A job with a window of its own (copying files) is brought up
 * instead, if it is the only one. In the OS/2 Warp style, as Warp 4's
 * installers had it: each job in a sunken panel, its message in navy,
 * its progress a ribbon in a slot over a scale from 0 to 100, and the
 * buttons from the left (see warp.css).
 */
final class JobsView {
	/** How long a job may take before its window is shown, if asked to be */
	private static final Duration MOMENT = Duration.millis(400);

	private final Jobs jobs;
	private final Window owner;
	private final Consumer<Window> help;
	private final HBox strip = new HBox(8);
	private final ProgressBar bar = new ProgressBar();
	private final Label latest = new Label();
	private final Label more = new Label();
	private final VBox rows = new VBox(12);
	private Stage window;

	/** @param help Shows help on jobs, over a window */
	JobsView(Jobs jobs, Window owner, Consumer<Window> help) {
		this.jobs = jobs;
		this.owner = owner;
		this.help = help;
		strip.setId("jobsStrip");
		strip.setAlignment(Pos.CENTER_LEFT);
		strip.setPadding(new Insets(6, 12, 6, 12));
		bar.setPrefWidth(120);
		latest.setId("jobsLatest");
		more.setId("jobsMore");
		more.getStyleClass().add("dim-text");
		Button show = new Button("Show…");
		show.setId("jobsShow");
		show.setOnAction(e -> show());
		strip.getChildren().setAll(bar, latest, more, show);
		jobs.running().addListener((ListChangeListener<Jobs.Job>) c -> update());
		update();
	}

	/** The strip, at the foot of the window; not visible while no job runs */
	Node strip() {
		return strip;
	}

	/** Bring up the jobs' window, or the only job's own */
	void show() {
		if (jobs.running().isEmpty()) {
			return;
		}
		if (jobs.running().size() == 1 && jobs.running().get(0).show().isPresent()) {
			jobs.running().get(0).show().get().run();
			return;
		}
		if (window == null) {
			window = new Stage();
			window.initOwner(owner);
			window.setTitle("Background Jobs");
			Button hide = new Button("Run in Background");
			hide.setId("jobsHide");
			hide.setDefaultButton(true);
			hide.setOnAction(e -> window.hide());
			Button helpButton = new Button("Help");
			helpButton.setId("jobsHelp");
			helpButton.setOnAction(e -> help.accept(window));
			HBox buttons = new HBox(8);
			// Help last, from the left, as Presentation Manager had it; else
			// first, apart from the others
			Runnable order = () -> {
				Region spacer = new Region();
				HBox.setHgrow(spacer, Priority.ALWAYS);
				buttons.getChildren().setAll(Theme.style().get() == Theme.Style.WARP ? List.of(hide, helpButton)
						: List.of(helpButton, spacer, hide));
			};
			order.run();
			Theme.style().addListener((o, was, style) -> order.run());
			ScrollPane scroll = new ScrollPane(rows);
			scroll.setFitToWidth(true);
			scroll.setPrefViewportHeight(260);
			VBox.setVgrow(scroll, Priority.ALWAYS);
			VBox content = new VBox(12, scroll, buttons);
			content.getStyleClass().add("jobs-window");
			content.setPadding(new Insets(12));
			content.setPrefWidth(480);
			window.setScene(new Scene(content));
		}
		rebuild();
		window.show();
		window.toFront();
	}

	/** Bring up the jobs' window if a job is still running after a moment */
	void showSoon(Jobs.Job job) {
		PauseTransition moment = new PauseTransition(MOMENT);
		moment.setOnFinished(e -> {
			if (jobs.running().contains(job)) {
				show();
			}
		});
		moment.play();
	}

	/** Whether the jobs' window is showing, e.g. in tests */
	boolean showing() {
		return window != null && window.isShowing();
	}

	private void update() {
		boolean any = !jobs.running().isEmpty();
		strip.setVisible(any);
		strip.setManaged(any);
		bar.progressProperty().unbind();
		latest.textProperty().unbind();
		if (!any) {
			if (window != null) {
				window.hide();
			}
			return;
		}
		Jobs.Job job = jobs.running().get(jobs.running().size() - 1);
		bar.progressProperty().bind(job.task().progressProperty());
		latest.textProperty().bind(Bindings.createStringBinding(() -> job.title()
				+ (job.task().getMessage().isBlank() ? "" : ": " + job.task().getMessage()),
				job.task().messageProperty()));
		int others = jobs.running().size() - 1;
		more.setText(others == 0 ? "" : "and " + others + " more");
		if (showing()) {
			rebuild();
		}
	}

	/** A row for each job */
	private void rebuild() {
		rows.getChildren().clear();
		for (Jobs.Job job : jobs.running()) {
			if (!rows.getChildren().isEmpty()) {
				rows.getChildren().add(new Separator());
			}
			Label title = new Label(job.title());
			title.setStyle("-fx-font-weight: bold;");
			title.setWrapText(true);
			Label message = new Label();
			message.textProperty().bind(job.task().messageProperty());
			message.setWrapText(true);
			message.getStyleClass().add("job-message");
			// What it is at, on a line of its own
			Label item = new Label();
			item.textProperty().bind(job.item());
			item.getStyleClass().add("job-message");
			item.visibleProperty().bind(job.item().isNotEmpty());
			item.managedProperty().bind(item.visibleProperty());
			ProgressBar progress = new ProgressBar();
			progress.progressProperty().bind(job.task().progressProperty());
			progress.setMaxWidth(Double.MAX_VALUE);
			NumberAxis scale = new NumberAxis(0, 100, 50);
			scale.setMinorTickCount(5);
			scale.setAutoRanging(false);
			scale.setAnimated(false);
			scale.setSide(Side.BOTTOM);
			scale.setPrefHeight(28);
			scale.setFocusTraversable(false);
			scale.getStyleClass().add("progress-scale");
			// Shown only where a style shows it (OS/2 Warp)
			scale.managedProperty().bind(scale.visibleProperty());
			ListView<String> lines = new ListView<>(job.lines());
			lines.setPrefHeight(90);
			lines.visibleProperty().bind(Bindings.isNotEmpty(job.lines()));
			lines.managedProperty().bind(lines.visibleProperty());
			Button cancel = new Button("Cancel");
			cancel.setId("jobCancel");
			cancel.setOnAction(e -> {
				cancel.setDisable(true);
				job.cancel();
			});
			// On the right, unless a style says otherwise (wpsld.css)
			HBox buttons = new HBox(8);
			buttons.getStyleClass().add("job-buttons");
			job.show().ifPresent(own -> {
				Button show = new Button("Show");
				show.setOnAction(e -> own.run());
				buttons.getChildren().add(show);
			});
			buttons.getChildren().add(cancel);
			// Room for the scale's first and last labels
			VBox meter = new VBox(progress, scale);
			meter.setPadding(new Insets(0, 10, 0, 4));
			VBox row = new VBox(6, title, message, item, meter, lines, buttons);
			row.getStyleClass().add("job");
			rows.getChildren().add(row);
		}
	}
}
