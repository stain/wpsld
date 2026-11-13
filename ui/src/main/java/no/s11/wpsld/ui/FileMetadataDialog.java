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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.lookup.EmbeddedMetadata.Found;

/**
 * Choose which of the values a file says about itself (see
 * {@link no.s11.wpsld.lookup.EmbeddedMetadata}) to give its entity: each
 * with where in the file it is, and the entity's values now. Those the
 * entity has no value for yet are chosen to start with, one of each.
 */
class FileMetadataDialog extends Dialog<List<Found>> {
	/**
	 * @param found Values the entity doesn't have yet
	 * @param shown A value as shown, e.g. the name of the entity it refers to
	 */
	FileMetadataDialog(Window owner, Entity entity, List<Found> found, Function<String, String> label,
			Function<JsonNode, String> shown) {
		initOwner(owner);
		setTitle("Metadata from the File");
		setHeaderText("“" + entity.getLabel() + "” says this about itself");
		setResizable(true);
		Map<CheckBox, Found> boxes = new LinkedHashMap<>();
		Set<String> chosen = new HashSet<>();
		VBox list = new VBox(6);
		list.setId("fileMetadata");
		for (Found f : found) {
			List<String> now = entity.getAll(f.property()).stream().map(shown).toList();
			CheckBox box = new CheckBox(label.apply(f.property()) + ": " + oneLine(f.value()));
			box.setId("fileMetadata-" + boxes.size());
			box.setMnemonicParsing(false);
			// One value of each property to start with, where it has none
			// Where a photo was taken, and the camera, only on purpose
			box.setSelected(!f.onPurpose() && now.isEmpty() && chosen.add(f.property()));
			box.setTooltip(new Tooltip("From " + f.source() + (now.isEmpty() ? ""
					: "\nNow: " + now.stream().map(FileMetadataDialog::oneLine).collect(Collectors.joining(", ")))));
			boxes.put(box, f);
			list.getChildren().add(box);
		}
		Label note = new Label(boxes.isEmpty() ? "The entity has all of it already."
				: "Chosen are those it has no value for yet. A name, description, date or language replaces the"
						+ " entity's; authors, keywords and licences are added. Authors are linked by name, to a"
						+ " Person added if the crate has none of that name; SPDX licences by their SPDX address; where"
						+ " a photo was taken by a new Place. Photos can tell more than meant, e.g. where someone"
						+ " lives: choose with care.");
		note.setWrapText(true);
		note.getStyleClass().add("dim-text");
		ScrollPane scroll = new ScrollPane(list);
		scroll.setFitToWidth(true);
		scroll.setPrefViewportHeight(Math.min(360, 30 * Math.max(1, boxes.size())));
		getDialogPane().setContent(new VBox(10, scroll, note));
		getDialogPane().setPrefWidth(520);
		getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		getDialogPane().lookupButton(ButtonType.OK).setDisable(boxes.isEmpty());
		setResultConverter(button -> {
			if (button != ButtonType.OK) {
				return null;
			}
			List<Found> result = new ArrayList<>();
			boxes.forEach((box, f) -> {
				if (box.isSelected()) {
					result.add(f);
				}
			});
			return result;
		});
	}

	private static String oneLine(String text) {
		String line = text.strip().replaceAll("\\s+", " ");
		return line.length() > 120 ? line.substring(0, 119) + "…" : line;
	}
}
