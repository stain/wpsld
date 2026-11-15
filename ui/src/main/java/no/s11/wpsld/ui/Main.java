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

import javafx.application.Application;

/**
 * Starts wpsld: its log first (see {@link Logs}), then the editor. Not the
 * {@link CrateEditor} itself, as Java's launcher starts JavaFX before the
 * main method of an {@link Application}, so that JavaFX failing to start
 * (no display, missing libraries) would not be logged.
 */
public final class Main {
	private Main() {
	}

	public static void main(String[] args) {
		Logs.setUp();
		Application.launch(CrateEditor.class, args);
	}
}
