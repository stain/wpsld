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
import java.util.Optional;
import java.util.stream.Stream;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;

/**
 * The editor's look: JavaFX's own Modena, recoloured by a stylesheet.
 * <p>
 * The chosen theme applies to every window, dialogs included, as they open:
 * each scene gets <code>wpsld.css</code>, with the colours the editor names
 * itself (e.g. <code>-wpsld-dim-text</code>), then the theme's stylesheet.
 * The root of each scene also gets the style class
 * <code>theme-&lt;name&gt;</code>, so that a control with its own stylesheet,
 * such as {@link Notebook}, can recolour itself under
 * <code>.theme-dark</code>.
 */
enum Theme {
	LIGHT("light", "Light", null),
	/** In the colours of Visual Studio Code's Dark Modern theme */
	DARK("dark", "Dark", "dark.css"),
	/** Light or Dark, as the operating system's colour scheme */
	SYSTEM("system", "Same as System", null);

	private static final String BASE = Theme.class.getResource("wpsld.css").toExternalForm();
	private static final ObjectProperty<Theme> chosen = new SimpleObjectProperty<>(LIGHT);
	private static boolean watching;

	private final String id;
	private final String label;
	private final String stylesheet;

	Theme(String id, String label, String stylesheet) {
		this.id = id;
		this.label = label;
		this.stylesheet = stylesheet == null ? null : Theme.class.getResource(stylesheet).toExternalForm();
	}

	/**
	 * The name kept in the settings.
	 */
	String id() {
		return id;
	}

	String label() {
		return label;
	}

	/**
	 * The theme with this name; Light for a name not known, e.g. from a newer
	 * version's settings.
	 */
	static Theme named(String id) {
		return Stream.of(values()).filter(t -> t.id.equals(id)).findFirst().orElse(LIGHT);
	}

	/**
	 * The theme of every window from now on. Must be called on the JavaFX
	 * thread.
	 */
	static void choose(Theme theme) {
		watch();
		chosen.set(theme);
	}

	static Theme chosen() {
		return chosen.get();
	}

	/**
	 * The theme shown: for {@link #SYSTEM}, the one matching the operating
	 * system's colour scheme.
	 */
	static Theme shown() {
		if (chosen.get() != SYSTEM) {
			return chosen.get();
		}
		return Platform.getPreferences().getColorScheme() == ColorScheme.DARK ? DARK : LIGHT;
	}

	/**
	 * Apply the theme to each window, as it opens or changes scene or root,
	 * and again when another theme is chosen, or the system's colour scheme
	 * changes.
	 */
	private static void watch() {
		if (watching) {
			return;
		}
		watching = true;
		Window.getWindows().addListener((ListChangeListener<Window>) change -> {
			while (change.next()) {
				change.getAddedSubList().forEach(Theme::watch);
			}
		});
		List.copyOf(Window.getWindows()).forEach(Theme::watch);
		chosen.addListener(o -> applyToAll());
		Platform.getPreferences().colorSchemeProperty().addListener(o -> applyToAll());
	}

	private static void watch(Window window) {
		window.sceneProperty().addListener((obs, old, scene) -> watch(scene));
		watch(window.getScene());
	}

	private static void watch(Scene scene) {
		if (scene == null) {
			return;
		}
		// Once per scene, also if its window shows it again
		if (scene.getProperties().putIfAbsent(Theme.class, true) == null) {
			scene.rootProperty().addListener((obs, old, root) -> {
				removeStyleClasses(old);
				apply(scene);
			});
		}
		apply(scene);
	}

	private static void applyToAll() {
		List.copyOf(Window.getWindows()).forEach(w -> apply(w.getScene()));
	}

	private static void apply(Scene scene) {
		if (scene == null) {
			return;
		}
		Theme shown = shown();
		List<String> stylesheets = scene.getStylesheets();
		stylesheets.removeIf(s -> s.equals(BASE) || Stream.of(values()).anyMatch(t -> s.equals(t.stylesheet)));
		// The base first, as the theme recolours it
		stylesheets.add(0, BASE);
		Optional.ofNullable(shown.stylesheet).ifPresent(s -> stylesheets.add(1, s));
		removeStyleClasses(scene.getRoot());
		Optional.ofNullable(scene.getRoot()).ifPresent(r -> r.getStyleClass().add("theme-" + shown.id));
	}

	private static void removeStyleClasses(Parent root) {
		if (root != null) {
			root.getStyleClass().removeIf(c -> c.startsWith("theme-"));
		}
	}
}
