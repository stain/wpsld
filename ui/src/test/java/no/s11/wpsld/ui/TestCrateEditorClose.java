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

import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

/**
 * Closing the window with changes not saved, in a class of its own as the
 * window stays closed.
 */
public class TestCrateEditorClose extends ApplicationTest {
	private Stage stage;

	@Override
	public void start(Stage stage) throws Exception {
		this.stage = stage;
		CrateEditor app = new CrateEditor();
		app.init();
		app.start(stage);
	}

	@Test
	public void closeAsksToSaveChanges() throws Exception {
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#addEntity").tryQuery().isPresent());
		clickOn("#addEntity");
		clickOn("#typeSearch").write("person").type(KeyCode.ENTER);

		// File > Exit, with its shortcut, as closing the window
		push(KeyCode.SHORTCUT, KeyCode.Q);
		assertTrue(lookup("Do you want to save the changes to Untitled?").tryQuery().isPresent());
		clickOn("Cancel");
		assertTrue(stage.isShowing());

		push(KeyCode.SHORTCUT, KeyCode.Q);
		clickOn("Don't Save");
		WaitForAsyncUtils.waitForFxEvents();
		assertFalse(stage.isShowing());
	}
}
