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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Help > About wpsld: the version, where the source is, the Java and JavaFX
 * it runs on, the licence (LICENSE.txt) and the third-party notices
 * (THIRD-PARTY-NOTICES.txt) of the software it includes; both copied into
 * the resources by the build.
 */
class AboutDialog extends Dialog<Void> {
	static final String SOURCE = "https://github.com/stain/wpsld";

	AboutDialog(Window owner, Consumer<String> browse) {
		initOwner(owner);
		setTitle("About wpsld");
		setHeaderText("wpsld " + version());
		Label about = new Label("A desktop editor for RO-Crate metadata.\n"
				+ "Copyright 2026 The University of Manchester, UK. Apache License 2.0.");
		about.setWrapText(true);
		Hyperlink source = new Hyperlink(SOURCE);
		source.setId("aboutSource");
		source.setOnAction(e -> browse.accept(SOURCE));
		Label runtime = new Label("Java " + System.getProperty("java.version") + ", JavaFX "
				+ System.getProperty("javafx.runtime.version", System.getProperty("javafx.version", "?")));
		runtime.setId("aboutRuntime");
		runtime.getStyleClass().add("dim-text");

		TabPane texts = new TabPane(new Tab("Licence", text("aboutLicence", "LICENSE.txt")),
				new Tab("Third-party notices", text("aboutNotices", "THIRD-PARTY-NOTICES.txt")));
		texts.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		VBox.setVgrow(texts, Priority.ALWAYS);
		VBox content = new VBox(6, about, source, runtime, texts);
		content.setPadding(new Insets(4, 0, 0, 0));
		content.setPrefSize(640, 480);
		getDialogPane().setContent(content);
		getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
		setResizable(true);
	}

	/**
	 * The version the build wrote in about.properties; "(development)" when
	 * run without it having been filled in.
	 */
	static String version() {
		Properties about = new Properties();
		try (InputStream in = AboutDialog.class.getResourceAsStream("about.properties")) {
			if (in != null) {
				about.load(in);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		String version = about.getProperty("version", "");
		return version.isBlank() || version.contains("${") ? "(development)" : version;
	}

	private static TextArea text(String id, String resource) {
		TextArea area = new TextArea(resource(resource));
		area.setId(id);
		area.setEditable(false);
		area.setStyle("-fx-font-family: monospace;");
		return area;
	}

	private static String resource(String name) {
		try (InputStream in = AboutDialog.class.getResourceAsStream(name)) {
			return in == null ? name + " is missing from this build." : new String(in.readAllBytes(),
					StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
