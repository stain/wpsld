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

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import no.s11.wpsld.crate.FileCopy;

/**
 * A window copying files into a crate's folder in the background (see
 * {@link FileCopy}), as a file manager's: the file being copied, how much
 * of all, and Cancel; not modal, so that editing goes on meanwhile.
 */
final class CopyWindow {
	private final Stage stage = new Stage();
	private final Label file = new Label();
	private final Label amount = new Label();
	private final ProgressBar bar = new ProgressBar(0);
	private final Task<FileCopy.Result> task;
	/** Cancel asked: the copy stops at the next block, telling what it copied */
	private final AtomicBoolean cancelled = new AtomicBoolean();

	/**
	 * @param done told on the JavaFX thread what was copied, also if cancelled
	 */
	CopyWindow(Window owner, List<Path> files, Path folder, FileCopy.Conflict conflict,
			Consumer<FileCopy.Result> done) {
		task = new Task<>() {
			@Override
			protected FileCopy.Result call() {
				return FileCopy.copy(files, folder, conflict, (current, copied, total) -> {
					updateMessage(current.getFileName().toString());
					updateTitle(size(copied) + " of " + size(total));
					updateProgress(copied, Math.max(total, 1));
				}, cancelled::get);
			}
		};
		file.textProperty().bind(task.messageProperty());
		amount.textProperty().bind(task.titleProperty());
		amount.getStyleClass().add("dim-text");
		bar.progressProperty().bind(task.progressProperty());
		bar.setMaxWidth(Double.MAX_VALUE);
		Button cancel = new Button("Cancel");
		cancel.setId("cancelCopy");
		cancel.setCancelButton(true);
		cancel.setOnAction(e -> {
			cancelled.set(true);
			cancel.setDisable(true);
		});
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox buttons = new HBox(spacer, cancel);
		buttons.setAlignment(Pos.CENTER_RIGHT);
		Label heading = new Label("Copying " + files.size() + (files.size() == 1 ? " file" : " files") + " to "
				+ folder.getFileName());
		heading.setStyle("-fx-font-weight: bold;");
		VBox content = new VBox(8, heading, file, bar, amount, buttons);
		content.setPadding(new Insets(12));
		content.setPrefWidth(420);
		stage.setScene(new Scene(content));
		stage.setTitle("Copying Files");
		stage.initOwner(owner);
		stage.setOnCloseRequest(e -> cancelled.set(true));
		// What was copied, also if cancelled
		task.setOnSucceeded(e -> {
			stage.close();
			done.accept(task.getValue());
		});
		task.setOnFailed(e -> {
			stage.close();
			done.accept(new FileCopy.Result(List.of(), List.of(), files.stream()
					.map(f -> new FileCopy.Failed(f, String.valueOf(task.getException()))).toList(), false));
		});
	}

	/** Start copying, showing the window */
	Task<FileCopy.Result> start() {
		stage.show();
		Thread thread = new Thread(task, "Copying files");
		thread.setDaemon(true);
		thread.start();
		return task;
	}

	/** A size for people, e.g. 12.3 MB */
	static String size(long bytes) {
		if (bytes < 1024) {
			return bytes + " bytes";
		}
		String[] units = { "KB", "MB", "GB", "TB" };
		double value = bytes;
		int unit = -1;
		while (value >= 1024 && unit < units.length - 1) {
			value /= 1024;
			unit++;
		}
		return String.format("%.1f %s", value, units[unit]);
	}
}
