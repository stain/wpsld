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


import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateFolder;
import no.s11.wpsld.crate.RootProbe;
import no.s11.wpsld.crate.WebCrate;

/**
 * Crate &gt; Attach…: say what the crate's relative <code>@id</code>s are
 * relative to, so that its files are found: a folder on disk (maybe its files
 * unpacked by hand), a web address (with what the crate says its root could
 * be to choose from, and a check that its first file is there), or nothing,
 * detached. Which is chosen is the user's: nothing is attached that is not
 * confirmed here, and the crate is not changed.
 */
final class AttachDialog extends Dialog<Attachment> {
	private final Crate crate;
	private final RootProbe probe;
	private final int maxFiles;
	/** The first relative file the crate has, to look for, and to show where it would lead */
	private final Optional<String> firstFile;
	private final RadioButton folderChoice = new RadioButton("A folder on disk");
	private final RadioButton webChoice = new RadioButton("A web address");
	private final RadioButton noneChoice = new RadioButton("Nothing: detached");
	private final TextField folderField = new TextField();
	private final Label folderMatch = new Label();
	private final ComboBox<String> webField = new ComboBox<>();
	private final Label webWhy = new Label();
	private final Label webExample = new Label();
	private final Label webChecked = new Label();
	private final List<WebCrate.Root> suggestions;

	/**
	 * @param current       What it is attached to now
	 * @param suggestions   What the crate's root could be, to choose from
	 * @param defaultFolder A folder to start from, e.g. where the crate's file is
	 */
	AttachDialog(Window owner, Crate crate, Attachment current, List<WebCrate.Root> suggestions,
			Optional<Path> defaultFolder, RootProbe probe, int maxFiles) {
		this.crate = crate;
		this.probe = probe;
		this.maxFiles = maxFiles;
		this.suggestions = suggestions;
		this.firstFile = RootProbe.firstFile(crate);
		initOwner(owner);
		setTitle("Attach");
		setHeaderText("Where are the crate's files?");
		getDialogPane().setId("attachDialog");
		ToggleGroup group = new ToggleGroup();
		folderChoice.setToggleGroup(group);
		webChoice.setToggleGroup(group);
		noneChoice.setToggleGroup(group);
		folderChoice.setId("attachToFolder");
		webChoice.setId("attachToWeb");
		noneChoice.setId("attachToNone");

		Label intro = new Label("What the relative identifiers of the crate's files (such as data/table.csv) are"
				+ " relative to. The crate itself is not changed.");
		intro.setWrapText(true);
		intro.setMaxWidth(520);

		folderField.setId("attachFolder");
		folderField.setPromptText("The folder with the crate's files");
		HBox.setHgrow(folderField, Priority.ALWAYS);
		Button choose = new Button("Choose…");
		choose.setId("attachChooseFolder");
		choose.setOnAction(e -> {
			DirectoryChooser chooser = new DirectoryChooser();
			chooser.setTitle("Folder with the crate's files");
			currentFolder().filter(Files::isDirectory).or(() -> defaultFolder.filter(Files::isDirectory))
					.ifPresent(f -> chooser.setInitialDirectory(f.toFile()));
			File chosen = chooser.showDialog(getDialogPane().getScene().getWindow());
			if (chosen != null) {
				folderField.setText(chosen.getPath());
			}
		});
		folderMatch.setId("attachFolderMatch");
		folderMatch.setWrapText(true);
		folderMatch.setMaxWidth(500);

		webField.setId("attachWeb");
		webField.setEditable(true);
		webField.setPrefWidth(420);
		webField.getEditor().setPromptText("https://example.org/where/the/files/are/");
		webField.getItems().setAll(suggestions.stream().map(r -> r.address().toString()).toList());
		Button check = new Button("Check");
		check.setId("attachCheck");
		check.setOnAction(e -> check());
		webWhy.setId("attachWebWhy");
		webWhy.setWrapText(true);
		webWhy.setMaxWidth(500);
		webExample.setId("attachWebExample");
		webExample.setWrapText(true);
		webExample.setMaxWidth(500);
		webChecked.setId("attachWebChecked");
		webChecked.setWrapText(true);
		webChecked.setMaxWidth(500);

		VBox content = new VBox(8, intro, folderChoice,
				indented(new HBox(4, folderField, choose), folderMatch), webChoice,
				indented(new HBox(4, webField, check), webWhy, webExample, webChecked), noneChoice);
		content.setPadding(new Insets(8, 0, 0, 0));
		getDialogPane().setContent(content);
		ButtonType attach = new ButtonType("Attach", ButtonData.OK_DONE);
		getDialogPane().getButtonTypes().setAll(attach, ButtonType.CANCEL);

		// What the crate is attached to, else a web address if it has suggestions, else a folder
		current.folder().ifPresentOrElse(f -> {
			folderChoice.setSelected(true);
			folderField.setText(f.toString());
		}, () -> current.web().ifPresentOrElse(w -> {
			webChoice.setSelected(true);
			webField.getEditor().setText(w.toString());
		}, () -> {
			if (!suggestions.isEmpty()) {
				webChoice.setSelected(true);
				webField.getEditor().setText(suggestions.get(0).address().toString());
			} else {
				folderChoice.setSelected(true);
				defaultFolder.ifPresent(f -> folderField.setText(f.toString()));
			}
		}));

		folderField.textProperty().addListener((o, was, text) -> showFolderMatch());
		webField.getEditor().textProperty().addListener((o, was, text) -> showWeb());
		group.selectedToggleProperty().addListener((o, was, t) -> updateEnabled());
		showFolderMatch();
		showWeb();
		updateEnabled();
		Node ok = getDialogPane().lookupButton(attach);
		ok.setId("attachOk");
		Runnable valid = () -> ok.setDisable(attachment().isEmpty());
		valid.run();
		folderField.textProperty().addListener((o, was, text) -> valid.run());
		webField.getEditor().textProperty().addListener((o, was, text) -> valid.run());
		group.selectedToggleProperty().addListener((o, was, t) -> valid.run());
		setResultConverter(button -> button == attach ? attachment().orElse(null) : null);
	}

	private static VBox indented(Node... nodes) {
		VBox box = new VBox(4, nodes);
		box.setPadding(new Insets(0, 0, 0, 24));
		return box;
	}

	private Optional<Path> currentFolder() {
		try {
			return Optional.of(Path.of(folderField.getText().strip())).filter(p -> !folderField.getText().isBlank());
		} catch (java.nio.file.InvalidPathException e) {
			return Optional.empty();
		}
	}

	/** What the dialog says now, if it is something that can be attached to */
	Optional<Attachment> attachment() {
		if (noneChoice.isSelected()) {
			return Optional.of(Attachment.none());
		}
		if (folderChoice.isSelected()) {
			return currentFolder().filter(Files::isDirectory).map(Attachment::toFolder);
		}
		return CrateEditor.webAddress(webField.getEditor().getText().strip()).map(Attachment::toWeb);
	}

	private void updateEnabled() {
		folderField.setDisable(!folderChoice.isSelected());
		getDialogPane().lookupAll("#attachChooseFolder").forEach(n -> n.setDisable(!folderChoice.isSelected()));
		webField.setDisable(!webChoice.isSelected());
		getDialogPane().lookupAll("#attachCheck").forEach(n -> n.setDisable(!webChoice.isSelected()));
	}

	/** How many of the crate's files are in the folder, as for Save As */
	private void showFolderMatch() {
		Optional<Path> folder = currentFolder().filter(Files::isDirectory);
		if (folder.isEmpty()) {
			folderMatch.setText(folderField.getText().isBlank() ? "" : "Not a folder.");
			return;
		}
		CrateFolder.Match match = CrateFolder.match(crate, folder.get(), maxFiles);
		int parts = match.present().size() + match.absent().size();
		folderMatch.setText(parts == 0 ? "The crate describes no files in a folder."
				: match.present().size() + " of the crate's " + parts + (parts == 1 ? " file is" : " files are")
						+ " in this folder" + (match.absent().isEmpty() ? "."
								: "; e.g. " + String.join(", ", match.absent().stream().limit(3).toList())
										+ " are not.")
						+ (match.undescribed().isEmpty() ? ""
								: " The folder has " + match.undescribed().size() + " it doesn't describe."));
	}

	/** Why a suggestion, and where the first file would be there */
	private void showWeb() {
		String text = webField.getEditor().getText().strip();
		webChecked.setText("");
		webWhy.setText(suggestions.stream().filter(r -> r.address().toString().equals(text)).findFirst()
				.map(r -> "Suggested: " + r.why() + ".").orElse(""));
		Optional<URI> address = CrateEditor.webAddress(text);
		webExample.setText(address.isEmpty() ? (text.isEmpty() ? "" : "Not an https: or http: address.")
				: firstFile.map(file -> file + " would be at " + WebCrate.attachedTo(address.get()).resolve(file)
						.map(URI::toString).orElse("(nowhere)")).orElse(""));
	}

	/** Ask whether the first file is there, in the background (see {@link RootProbe}) */
	private void check() {
		Optional<URI> address = CrateEditor.webAddress(webField.getEditor().getText().strip());
		if (address.isEmpty() || firstFile.isEmpty()) {
			webChecked.setText(firstFile.isEmpty() ? "The crate has no relative files to look for." : "");
			return;
		}
		String file = firstFile.get();
		webChecked.setText("Looking for " + file + "…");
		Task<Optional<WebCrate.Root>> task = new Task<>() {
			@Override
			protected Optional<WebCrate.Root> call() throws InterruptedException {
				return probe.find(file, List.of(new WebCrate.Root(address.get(), "typed")));
			}
		};
		task.setOnSucceeded(e -> webChecked.setText(task.getValue().isPresent() ? file + " is there."
				: file + " was not found there: the files may be elsewhere, inside a zip, or the server may not say."));
		task.setOnFailed(e -> webChecked.setText("Could not look: " + task.getException().getMessage()));
		Thread.ofPlatform().daemon().name("Look for " + file).start(task);
	}
}
