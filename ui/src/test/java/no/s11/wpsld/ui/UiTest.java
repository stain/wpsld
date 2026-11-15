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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestTimedOutException;
import org.testfx.framework.junit.ApplicationTest;

import javafx.application.Platform;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * The GUI tests: each fails after a time rather than hanging the build,
 * e.g. on a dialog nobody answers (a test's <code>interact</code> waits for
 * the code it runs, which waits in <code>showAndWait</code>), saying which
 * dialogs were open; and dialogs a test left open are closed after it, so
 * that the next tests of the class don't hang on them too.
 */
public abstract class UiTest extends ApplicationTest {
	/** How long a test may take; most take a few seconds */
	protected long timeoutSeconds() {
		return 120;
	}

	@Rule
	public final TestRule guard = (base, description) -> new Statement() {
		@Override
		public void evaluate() throws Throwable {
			try {
				Timeout.builder().withTimeout(timeoutSeconds(), TimeUnit.SECONDS).build().apply(base, description)
						.evaluate();
			} catch (TestTimedOutException e) {
				AssertionError error = new AssertionError(description.getMethodName() + " took over "
						+ timeoutSeconds() + " s" + openDialogs() + fxThread(), e);
				throw error;
			}
		}
	};

	/** Where the JavaFX thread is, as it may be stuck or busy */
	private static String fxThread() {
		return Thread.getAllStackTraces().entrySet().stream()
				.filter(e -> e.getKey().getName().equals("JavaFX Application Thread")).findFirst()
				.map(e -> ", with the JavaFX thread at:\n" + java.util.Arrays.stream(e.getValue()).limit(60)
						.map(f -> "\tat " + f).collect(Collectors.joining("\n")))
				.orElse("");
	}

	/** The dialogs open now, by their headers and texts; read off the FX thread, as it may be stuck */
	private static String openDialogs() {
		List<String> dialogs = List.copyOf(Window.getWindows()).stream()
				.filter(w -> w.getScene() != null && w.getScene().getRoot() != null)
				.map(w -> w.getScene().getRoot().lookup(".dialog-pane")).filter(DialogPane.class::isInstance)
				.map(DialogPane.class::cast)
				.map(d -> "“" + d.getHeaderText() + "”" + (d.getContentText() == null ? "" : ": " + d.getContentText()))
				.toList();
		return dialogs.isEmpty() ? "" : ", with a dialog open (unanswered?): " + String.join("; ", dialogs);
	}

	@After
	public void closeDialogs() {
		// Through the event queue, which a dialog's showAndWait still runs
		Platform.runLater(() -> List.copyOf(Window.getWindows()).stream()
				.filter(w -> w instanceof Stage && w.getScene() != null && w.getScene().getRoot() != null
						&& w.getScene().getRoot().lookup(".dialog-pane") != null)
				.collect(Collectors.toList()).forEach(w -> ((Stage) w).close()));
	}
}
