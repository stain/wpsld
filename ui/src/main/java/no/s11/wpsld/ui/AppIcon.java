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
import java.util.stream.IntStream;

import javafx.collections.ListChangeListener;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * wpsld's icon (made from ui/src/icons/wpsld.svg by scripts/update-icons.sh)
 * for its windows, from 16 to 256 pixels, so that each platform takes the
 * size it shows; the launchers' icons are the app images' (see
 * ui/build.gradle).
 */
final class AppIcon {
	private static final int[] SIZES = { 16, 24, 32, 48, 64, 128, 256 };
	private static List<Image> images;
	private static boolean watching;

	private AppIcon() {
	}

	/** The icon in all its sizes, loaded once */
	static synchronized List<Image> images() {
		if (images == null) {
			images = IntStream.of(SIZES).mapToObj(AppIcon::image).toList();
		}
		return images;
	}

	/** The icon at a size it is made in, e.g. 64 */
	static Image image(int size) {
		return new Image(AppIcon.class.getResource("icons/wpsld-" + size + ".png").toExternalForm());
	}

	/**
	 * Give every window that opens from now on the icon, unless it has its
	 * own: the editor's, its dialogs' and its other windows', wherever they
	 * are made. On the JavaFX thread; once is enough, for every editor window.
	 */
	static void forAllWindows() {
		if (watching) {
			return;
		}
		watching = true;
		Window.getWindows().forEach(AppIcon::give);
		Window.getWindows().addListener((ListChangeListener<Window>) change -> {
			while (change.next()) {
				change.getAddedSubList().forEach(AppIcon::give);
			}
		});
	}

	private static void give(Window window) {
		if (window instanceof Stage stage && stage.getIcons().isEmpty()) {
			stage.getIcons().setAll(images());
		}
	}
}
