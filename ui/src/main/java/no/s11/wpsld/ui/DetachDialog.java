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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.crate.WebCrate;

/**
 * Crate &gt; Detach…: a detached crate has no folder, so RO-Crate requires the
 * <code>@id</code>s of its files to be absolute addresses. Asks what the
 * crate's relative <code>@id</code>s are to be relative to, to make them
 * absolute: a web address (filled in if it was found or chosen before), or a
 * fresh anonymous arcp address, <code>arcp://uuid,…/</code>, for a crate that
 * is not published anywhere. A <code>file:</code> address is warned about:
 * it only means anything on this computer. The base can also be kept as the
 * <code>@base</code> of the crate's context, which RO-Crate mentions only for
 * reading a crate as a graph.
 */
final class DetachDialog extends Dialog<Detachment> {
	private final List<Entity> relative;
	private final TextField baseField = new TextField();
	private final Label warning = new Label();
	private final Label example = new Label();
	private final CheckBox recordBase = new CheckBox("Also keep it as @base in the crate's @context");
	private final CheckBox useDownloadAddresses = new CheckBox();
	private final CheckBox checkAvailability = new CheckBox("Check Web Availability afterwards");
	/** Whether the user chose to check or not, so that the base's kind no longer chooses for them */
	private boolean checkChosen;

	/**
	 * @param relative The entities with relative <code>@id</code>s, other than the root
	 * @param prefill  The base, if it is known: where the crate is attached on the web, or what it suggests
	 */
	DetachDialog(Window owner, List<Entity> relative, Optional<URI> prefill) {
		this.relative = relative;
		initOwner(owner);
		setTitle("Detach");
		setHeaderText("Make the crate's identifiers absolute");
		getDialogPane().setId("detachDialog");
		Label intro = new Label(relative.isEmpty()
				? "The crate has no relative identifiers to make absolute."
				: "A detached crate has no folder, so RO-Crate requires the identifiers of its files to be absolute"
						+ " addresses. " + relative.size() + (relative.size() == 1 ? " identifier is" : " identifiers are")
						+ " relative, e.g. " + String.join(", ", relative.stream().limit(3).map(Entity::getId).toList())
						+ ". They are made absolute against this address, as one step you can undo:");
		intro.setId("detachIntro");
		intro.setWrapText(true);
		intro.setMaxWidth(520);
		baseField.setId("detachBase");
		baseField.setPromptText("https://example.org/where/the/files/are/");
		HBox.setHgrow(baseField, Priority.ALWAYS);
		Button anonymous = new Button("New Anonymous Base");
		anonymous.setId("detachAnonymous");
		anonymous.setOnAction(e -> {
			baseField.setText(newArcpBase().toString());
			recordBase.setSelected(true);
		});
		warning.setId("detachWarning");
		warning.setWrapText(true);
		warning.setMaxWidth(520);
		warning.setStyle("-fx-text-fill: -fx-accent; -fx-font-weight: bold;");
		example.setId("detachExample");
		example.setWrapText(true);
		example.setMaxWidth(520);
		recordBase.setId("detachRecordBase");
		long withAddress = relative.stream().filter(e -> Crate.downloadAddress(e).isPresent()).count();
		useDownloadAddresses.setId("detachUseContentUrl");
		useDownloadAddresses.setText("Use a file's own contentUrl (or url) as its identifier, where it has one"
				+ " (" + withAddress + " of " + relative.size() + ")");
		useDownloadAddresses.setDisable(withAddress == 0);
		useDownloadAddresses.setWrapText(true);
		useDownloadAddresses.setMaxWidth(520);
		checkAvailability.setId("detachCheck");
		checkAvailability.setOnAction(e -> checkChosen = true);
		Label help = new Label("With a web address, the crate's root (./) becomes that address too, as RO-Crate wants of"
				+ " a crate that is available online. An anonymous base, arcp://uuid,…/, is for a crate that is not"
				+ " published anywhere: its identifiers are unique to it, and the base can later be used to make them"
				+ " relative again.");
		help.setWrapText(true);
		help.setMaxWidth(520);
		VBox content = new VBox(8, intro);
		if (!relative.isEmpty()) {
			content.getChildren().addAll(new HBox(4, baseField, anonymous), warning, example, recordBase,
					useDownloadAddresses, checkAvailability, help);
		}
		content.setPadding(new Insets(8, 0, 0, 0));
		getDialogPane().setContent(content);
		ButtonType detach = new ButtonType("Detach", ButtonData.OK_DONE);
		getDialogPane().getButtonTypes().setAll(detach, ButtonType.CANCEL);
		getDialogPane().lookupButton(detach).setId("detachOk");
		prefill.ifPresent(b -> baseField.setText(b.toString()));
		baseField.textProperty().addListener((o, was, text) -> update());
		useDownloadAddresses.setOnAction(e -> update());
		update();
		setResultConverter(button -> button == detach ? detachment().orElse(null) : null);
	}

	/** A fresh anonymous base, for a crate with no place of its own */
	static URI newArcpBase() {
		return URI.create("arcp://uuid," + UUID.randomUUID() + "/");
	}

	/**
	 * The base as typed, as a folder: http, https and arcp addresses, and
	 * file: ones (warned about), with a / at the end when it has no query, so
	 * that paths lead inside it.
	 */
	static Optional<URI> baseOf(String text) {
		try {
			URI uri = new URI(text.strip());
			String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
			boolean web = scheme.equals("http") || scheme.equals("https");
			if (!(web && uri.getHost() != null) && !(scheme.equals("arcp") && uri.getRawAuthority() != null)
					&& !(scheme.equals("file") && uri.getRawPath() != null)) {
				return Optional.empty();
			}
			if (uri.getRawQuery() == null && uri.getRawFragment() == null
					&& (uri.getRawPath() == null || !uri.getRawPath().endsWith("/"))) {
				uri = new URI(uri.toString() + "/");
			}
			return Optional.of(uri);
		} catch (URISyntaxException e) {
			return Optional.empty();
		}
	}

	static boolean isFile(URI uri) {
		return "file".equalsIgnoreCase(uri.getScheme());
	}

	/** What the dialog says now, if it can be done */
	Optional<Detachment> detachment() {
		if (relative.isEmpty()) {
			return Optional.of(new Detachment(Optional.empty(), false));
		}
		return baseOf(baseField.getText()).map(b -> new Detachment(Optional.of(b), recordBase.isSelected(),
				useDownloadAddresses.isSelected(), checkAvailability.isSelected() && !checkAvailability.isDisabled()));
	}

	private void update() {
		String text = baseField.getText().strip();
		Optional<URI> base = baseOf(text);
		getDialogPane().lookupButton(getDialogPane().getButtonTypes().get(0)).setDisable(detachment().isEmpty());
		warning.setText(base.filter(DetachDialog::isFile).map(b -> "A file: address only means something on this"
				+ " computer, so the identifiers would lead nowhere for anyone else. Use a web address, or New"
				+ " Anonymous Base.").orElse(text.isEmpty() || base.isPresent() ? ""
						: "Not an https:, http: or arcp: address."));
		example.setText(base.isEmpty() || relative.isEmpty() ? ""
				: relative.get(0).getId() + " would be " + (useDownloadAddresses.isSelected()
						? Crate.downloadAddress(relative.get(0)).map(URI::toString) : Optional.<String>empty())
						.or(() -> WebCrate.baseOf(base.get()).resolve(relative.get(0).getId()).map(URI::toString))
						.orElse("(nothing)"));
		// Only a web address is somewhere the files can be: not an anonymous base, or one for this computer
		boolean web = base.filter(b -> b.getScheme().startsWith("http")).isPresent();
		checkAvailability.setDisable(!web);
		if (!checkChosen) {
			checkAvailability.setSelected(web);
		}
	}
}
