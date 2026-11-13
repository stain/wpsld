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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * The operating system's file manager, to show a crate's folder or one of
 * its files: Windows' Explorer, macOS' Finder, or what xdg-open opens
 * folders with elsewhere. JavaFX's HostServices opens web pages, not
 * folders, so their own commands are run.
 */
final class FileManager {
	enum System {
		WINDOWS, MAC, OTHER;

		static System current() {
			String os = java.lang.System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			return os.startsWith("windows") ? WINDOWS : os.startsWith("mac") ? MAC : OTHER;
		}
	}

	private FileManager() {
	}

	/** What the menu item says, by the file manager's name */
	static String label(System system) {
		return switch (system) {
		case WINDOWS -> "Show in Explo_rer";
		case MAC -> "Show in F_inder";
		case OTHER -> "Show in File _Manager";
		};
	}

	/**
	 * The command that shows a folder, or a file selected in its folder
	 * (Explorer's /select, Finder's -R); xdg-open can only open the folder
	 */
	static List<String> command(System system, Path path, boolean folder) {
		String absolute = path.toAbsolutePath().toString();
		return switch (system) {
		// As two arguments, as Explorer reads /select,"…" itself
		case WINDOWS -> folder ? List.of("explorer.exe", absolute) : List.of("explorer.exe", "/select,", absolute);
		case MAC -> folder ? List.of("open", absolute) : List.of("open", "-R", absolute);
		case OTHER -> List.of("xdg-open", folder ? absolute : path.toAbsolutePath().getParent().toString());
		};
	}

	/** Show a folder, or a file in its folder, in the file manager */
	static void show(Path path) throws IOException {
		new ProcessBuilder(command(System.current(), path, Files.isDirectory(path)))
				.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
	}
}
