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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import javafx.application.Platform;
import javafx.stage.Stage;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * The RO-Crate context is loaded in the background, in parallel with the
 * schema, so building the editor on the JavaFX thread doesn't load it; the
 * editor appears once both are ready.
 */
public class TestCrateEditorLoading extends ApplicationTest {
	private static final SchemaFromJsonLd SCHEMA = new SchemaFromJsonLd();
	/** Threads that loaded the context, and when */
	private final List<Boolean> contextLoadedOnFxThread = new CopyOnWriteArrayList<>();
	private volatile long contextDone;

	@Override
	public void start(Stage stage) throws Exception {
		CrateEditor app = new CrateEditor(() -> SCHEMA, () -> {
			contextLoadedOnFxThread.add(Platform.isFxApplicationThread());
			try {
				// A slow context: the editor must wait for it, not load it itself
				Thread.sleep(1500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			contextDone = System.nanoTime();
			return CrateTerms.prewarm();
		});
		app.init();
		app.start(stage);
	}

	@Test
	public void contextLoadedInBackground() throws Exception {
		WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> lookup("#entities").tryQuery().isPresent());
		long editorShown = System.nanoTime();
		assertEquals("context loaded once, not on the JavaFX thread", List.of(false), contextLoadedOnFxThread);
		assertTrue("editor shown after the context was loaded", contextDone > 0 && editorShown >= contextDone);
	}
}
