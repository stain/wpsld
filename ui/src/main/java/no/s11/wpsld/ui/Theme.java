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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Window;

/**
 * The editor's look: a {@link Style} in {@link Colours}, both on top of
 * JavaFX's own Modena.
 * <p>
 * The look applies to every window, dialogs included, as they open: each
 * scene gets <code>wpsld.css</code>, with the colours the editor names itself
 * (e.g. <code>-wpsld-dim-text</code>), then the style's stylesheet, then its
 * dark stylesheet if dark. The root of each scene also gets the style
 * classes <code>style-&lt;name&gt;</code> and <code>colours-light</code> or
 * <code>colours-dark</code>, so that a control with its own stylesheet, such
 * as {@link Notebook}, can recolour itself under <code>.colours-dark</code>.
 */
final class Theme {
	/**
	 * A choice in the View menu, kept in the settings by its id.
	 */
	interface Choice {
		String id();

		String label();
	}

	enum Style implements Choice {
		/** Modena, and in Dark the colours of Visual Studio Code */
		CLASSIC("classic", "Classic", null, "dark.css", false),
		/** As Windows 11's Fluent design: rounded, light controls on cards */
		WIN11("win11", "Windows 11", "win11.css", "win11-dark.css", false),
		/** As OS/2 Warp 4: grey, bevelled, and the form as a notebook */
		WARP("warp", "OS/2 Warp", "warp.css", "warp-dark.css", true);

		private final String id;
		private final String label;
		private final String stylesheet;
		private final String darkStylesheet;
		private final boolean notebook;

		/**
		 * @param stylesheet     The file name of its stylesheet, if any, beside
		 *                       Theme; only a name, so that the styles don't
		 *                       depend on Theme being set up first
		 * @param darkStylesheet The one it adds for dark colours
		 */
		Style(String id, String label, String stylesheet, String darkStylesheet, boolean notebook) {
			this.id = id;
			this.label = label;
			this.stylesheet = stylesheet;
			this.darkStylesheet = darkStylesheet;
			this.notebook = notebook;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public String label() {
			return label;
		}

		/**
		 * Whether the form is a notebook, rather than a list of folding groups.
		 */
		boolean notebook() {
			return notebook;
		}
	}

	enum Colours implements Choice {
		LIGHT("light", "Light"),
		DARK("dark", "Dark"),
		/** Light or Dark, as the operating system's colour scheme */
		SYSTEM("system", "Same as System");

		private final String id;
		private final String label;

		Colours(String id, String label) {
			this.id = id;
			this.label = label;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private static final String BASE = url("wpsld.css");
	private static final ObjectProperty<Style> style = new SimpleObjectProperty<>(Style.CLASSIC);
	private static final ObjectProperty<Colours> colours = new SimpleObjectProperty<>(Colours.LIGHT);
	private static boolean watching;

	private Theme() {
	}

	private static String url(String stylesheet) {
		return stylesheet == null ? null : Theme.class.getResource(stylesheet).toExternalForm();
	}

	/**
	 * The choice with this id; the first for an id not known, e.g. from a
	 * newer version's settings.
	 */
	static <C extends Enum<C> & Choice> C named(Class<C> choices, String id) {
		C[] all = choices.getEnumConstants();
		return Stream.of(all).filter(c -> c.id().equals(id)).findFirst().orElse(all[0]);
	}

	/**
	 * The look of every window from now on. Must be called on the JavaFX
	 * thread.
	 */
	static void choose(Style style, Colours colours) {
		watch();
		Theme.style.set(style);
		Theme.colours.set(colours);
	}

	static ReadOnlyObjectProperty<Style> style() {
		return style;
	}

	static Colours colours() {
		return colours.get();
	}

	/**
	 * Whether dark colours show: for {@link Colours#SYSTEM}, if the operating
	 * system's colour scheme is dark.
	 */
	static boolean dark() {
		return switch (colours.get()) {
			case LIGHT -> false;
			case DARK -> true;
			case SYSTEM -> Platform.getPreferences().getColorScheme() == ColorScheme.DARK;
		};
	}

	/**
	 * Apply the look to each window, as it opens or changes scene or root,
	 * and again when another is chosen, or the system's colour scheme
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
		style.addListener(o -> applyToAll());
		colours.addListener(o -> applyToAll());
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
		Style shown = style.get();
		boolean dark = dark();
		List<String> ours = new ArrayList<>(List.of(BASE));
		Stream.of(Style.values()).flatMap(s -> Stream.of(s.stylesheet, s.darkStylesheet)).filter(Objects::nonNull)
				.map(Theme::url).forEach(ours::add);
		List<String> stylesheets = scene.getStylesheets();
		stylesheets.removeIf(ours::contains);
		// In this order, each recolouring the one before
		List<String> look = new ArrayList<>(List.of(BASE));
		Stream.of(shown.stylesheet, dark ? shown.darkStylesheet : null).filter(Objects::nonNull).map(Theme::url)
				.forEach(look::add);
		stylesheets.addAll(0, look);
		removeStyleClasses(scene.getRoot());
		if (scene.getRoot() != null) {
			scene.getRoot().getStyleClass().addAll("style-" + shown.id, dark ? "colours-dark" : "colours-light");
		}
	}

	private static void removeStyleClasses(Parent root) {
		if (root != null) {
			root.getStyleClass().removeIf(c -> c.startsWith("style-") || c.startsWith("colours-"));
		}
	}
}
