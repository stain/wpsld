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

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Help > About wpsld: version, licence and third-party notices, as the
 * build copies them into the resources.
 */
public class TestAboutDialog extends ApplicationTest {
	private Stage stage;
	private final List<String> browsed = new ArrayList<>();

	@Override
	public void start(Stage stage) {
		this.stage = stage;
		stage.setScene(new Scene(new StackPane(), 200, 100));
		stage.show();
	}

	@Test
	public void about() {
		interact(() -> new AboutDialog(stage, browsed::add).show());
		// The build fills in the version, e.g. 0.1.0-SNAPSHOT
		assertFalse(AboutDialog.version().contains("development"));
		assertTrue(lookup("wpsld " + AboutDialog.version()).tryQuery().isPresent());
		assertTrue(lookup("#aboutRuntime").queryAs(Label.class).getText().startsWith("Java "));

		String licence = lookup("#aboutLicence").queryAs(TextArea.class).getText();
		assertTrue(licence.contains("Apache License"));
		String notices = lookup("#aboutNotices").queryAs(TextArea.class).getText();
		// Not about the source, and covers what the zips include
		assertTrue(notices.contains("It does NOT describe the wpsld source code or git repository"));
		for (String component : List.of("Jackson", "commonmark-java", "FastDoubleParser", "Schubfach", "Ikonli",
				"Fluent UI System Icons", "Copyright (c) 2020 Microsoft Corporation", "SPDX License List 3.29.0")) {
			assertTrue(component, notices.contains(component));
		}

		// The runtime's own notices, from its legal folder (a JDK has one too)
		String java = lookup("#aboutJava").queryAs(TextArea.class).getText();
		assertTrue(java, java.contains("java.base/LICENSE"));
		assertTrue(lookup("#aboutJavaFX").queryAs(TextArea.class).getText().contains("JavaFX"));

		clickOn("#aboutSource");
		assertEquals(List.of(AboutDialog.SOURCE), browsed);
		clickOn("Close");
	}
}
