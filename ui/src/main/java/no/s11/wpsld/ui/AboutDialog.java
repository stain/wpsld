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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
 * (THIRD-PARTY-NOTICES.txt) of the software it includes, both copied into
 * the resources by the build; and the notices of the Java runtime and
 * JavaFX, from the runtime's legal folder.
 */
class AboutDialog extends Dialog<Void> {
	static final String SOURCE = "https://github.com/stain/wpsld";
	static final String ISSUES = SOURCE + "/issues";

	AboutDialog(Window owner, Consumer<String> browse) {
		initOwner(owner);
		setTitle("About wpsld");
		setHeaderText("wpsld " + version());
		Label about = new Label("A desktop editor for RO-Crate metadata.\n"
				+ "Copyright 2026 The University of Manchester, UK. Apache License 2.0.");
		about.setWrapText(true);
		Hyperlink source = new Hyperlink("Source code and releases on GitHub: " + SOURCE);
		source.setId("aboutSource");
		source.setOnAction(e -> browse.accept(SOURCE));
		Hyperlink issues = new Hyperlink("Report a problem or suggest an improvement");
		issues.setId("aboutIssues");
		issues.setOnAction(e -> browse.accept(ISSUES));
		Label runtime = new Label("Java " + System.getProperty("java.version") + ", JavaFX "
				+ System.getProperty("javafx.runtime.version", System.getProperty("javafx.version", "?")));
		runtime.setId("aboutRuntime");
		runtime.getStyleClass().add("dim-text");

		TabPane texts = new TabPane(new Tab("Licence", text("aboutLicence", resource("LICENSE.txt"))),
				new Tab("Third-party notices", text("aboutNotices", resource("THIRD-PARTY-NOTICES.txt"))),
				new Tab("Java", text("aboutJava", runtimeNotices(module -> !module.startsWith("javafx."),
						"Java runtime"))),
				new Tab("JavaFX", text("aboutJavaFX", runtimeNotices(module -> module.startsWith("javafx."),
						"JavaFX"))));
		texts.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		VBox.setVgrow(texts, Priority.ALWAYS);
		VBox content = new VBox(6, about, source, issues, runtime, texts);
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

	/**
	 * The licences and notices of the runtime's modules, from its
	 * <code>legal</code> folder, one folder per module (java.base first):
	 * as the platform zips include them, with Java and JavaFX linked in. A
	 * file the same as one already shown (e.g. each module's LICENSE) is only
	 * named.
	 */
	static String runtimeNotices(Predicate<String> modules, String what) {
		Path legal = Path.of(System.getProperty("java.home"), "legal");
		List<Path> folders;
		try (Stream<Path> list = Files.list(legal)) {
			folders = list.filter(Files::isDirectory).filter(f -> modules.test(f.getFileName().toString()))
					.sorted(Comparator.comparing((Path f) -> !f.getFileName().toString().equals("java.base"))
							.thenComparing(f -> f.getFileName().toString()))
					.toList();
		} catch (IOException e) {
			folders = List.of();
		}
		if (folders.isEmpty()) {
			return "This runtime has no notices for " + what + " in " + legal + "."
					+ (what.equals("JavaFX") ? " JavaFX is then on the module path (as with the portable zip):"
							+ " its notices are in the legal folder of the JavaFX SDK, or of the JDK that includes it."
							: "");
		}
		StringBuilder text = new StringBuilder("The licences and notices of the " + what + " in "
				+ System.getProperty("java.home") + ", from its legal folder.\n");
		Map<String, String> shown = new HashMap<>();
		for (Path folder : folders) {
			List<Path> files;
			try (Stream<Path> list = Files.list(folder)) {
				files = list.filter(Files::isRegularFile).sorted().toList();
			} catch (IOException e) {
				continue;
			}
			for (Path file : files) {
				String name = folder.getFileName() + "/" + file.getFileName();
				String content;
				try {
					content = Files.readString(file, StandardCharsets.UTF_8);
				} catch (IOException e) {
					content = "(can't be read: " + e.getMessage() + ")";
				}
				text.append("\n").append("=".repeat(76)).append("\n").append(name).append("\n");
				String same = shown.putIfAbsent(content, name);
				text.append(same == null ? "\n" + content.strip() + "\n" : "The same as " + same + "\n");
			}
		}
		return text.toString();
	}

	private static TextArea text(String id, String content) {
		TextArea area = new TextArea(content);
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
