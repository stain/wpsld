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

import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import no.s11.wpsld.ui.FileManager.System;

/** The commands only: running them would open windows */
public class TestFileManager {
	@Test
	public void commands() {
		Path folder = Path.of("my crate").toAbsolutePath();
		Path file = folder.resolve("ro-crate-metadata.json");
		assertEquals(List.of("explorer.exe", folder.toString()), FileManager.command(System.WINDOWS, folder, true));
		assertEquals(List.of("explorer.exe", "/select,", file.toString()),
				FileManager.command(System.WINDOWS, file, false));
		assertEquals(List.of("open", "-R", file.toString()), FileManager.command(System.MAC, file, false));
		assertEquals(List.of("xdg-open", folder.toString()), FileManager.command(System.OTHER, file, false));
		assertEquals("Show in F_inder", FileManager.label(System.MAC));
	}
}
