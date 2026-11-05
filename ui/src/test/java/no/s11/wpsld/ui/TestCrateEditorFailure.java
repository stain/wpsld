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

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import javafx.scene.control.Label;
import javafx.stage.Stage;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * A failure while loading the schema is shown, not lost in the background
 * loading (which would leave "Loading…" forever).
 */
public class TestCrateEditorFailure extends ApplicationTest {

	@Override
	public void start(Stage stage) throws Exception {
		CrateEditor app = new CrateEditor(() -> {
			throw new IllegalStateException("Broken schema");
		});
		app.init();
		app.start(stage);
	}

	@Test
	public void loadingFailureShown() throws Exception {
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#failure").tryQuery().isPresent());
		String text = lookup("#failure").queryAs(Label.class).getText();
		assertTrue(text, text.startsWith("Can't load schema"));
		assertTrue(text, text.contains("Broken schema"));
		assertTrue(lookup("Loading schema.org " + SchemaFromJsonLd.SCHEMAORG_VERSION + "…").tryQuery().isEmpty());
	}
}
