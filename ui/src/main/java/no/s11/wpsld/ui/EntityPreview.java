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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;

import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;

/**
 * A read-only preview of one entity of the crate, like an RO-Crate's
 * <code>ro-crate-preview.html</code>: its name, types and identifier, a table
 * of its properties, and the entities referring to it. References to other
 * entities of the crate are links that open them; absolute URLs open in the
 * browser.
 */
public class EntityPreview extends ScrollPane {
	private final Supplier<Crate> crate;
	/** Opens an entity of the crate, e.g. selecting it for editing */
	private final Consumer<Entity> open;
	/** Opens a web address, e.g. in the browser */
	private final Consumer<String> browse;
	private final VBox content = new VBox(8);
	private Entity entity;

	public EntityPreview(Supplier<Crate> crate, Consumer<Entity> open, Consumer<String> browse) {
		this.crate = Objects.requireNonNull(crate);
		this.open = Objects.requireNonNull(open);
		this.browse = Objects.requireNonNull(browse);
		content.setPadding(new Insets(12));
		content.setId("entityPreview");
		setContent(content);
		setFitToWidth(true);
	}

	/**
	 * Show an entity.
	 */
	public void show(Entity entity) {
		this.entity = entity;
		refresh();
	}

	/**
	 * Show the entity again, e.g. after it or other entities changed.
	 */
	public void refresh() {
		content.getChildren().clear();
		if (entity == null || !crate.get().getEntities().contains(entity)) {
			return;
		}
		Crate current = crate.get();
		Label name = new Label(entity.getLabel());
		name.setId("previewName");
		name.setWrapText(true);
		name.setStyle("-fx-font-size: 1.6em; -fx-font-weight: bold;");
		Label about = new Label(String.join(", ", entity.getTypes()) + "    " + entity.getId());
		about.setStyle("-fx-text-fill: #505050;");
		content.getChildren().addAll(name, about);
		// The description as a paragraph under the name, not in the table
		List<String> descriptions = entity.getAll("description").stream().filter(JsonNode::isValueNode)
				.map(JsonNode::asText).toList();
		if (!descriptions.isEmpty()) {
			Label description = new Label(String.join("\n\n", descriptions));
			description.setId("previewDescription");
			description.setWrapText(true);
			content.getChildren().add(description);
		}
		if (entity != current.getRoot()) {
			Hyperlink root = link("Go to the crate: " + current.getRoot().getLabel(), current.getRoot());
			root.setId("previewRoot");
			content.getChildren().add(root);
		}

		GridPane properties = table();
		properties.setId("previewProperties");
		int row = 0;
		for (String property : entity.getValues().keySet()) {
			if (property.equals("name") || property.equals("description") && !descriptions.isEmpty()) {
				// Shown above
				continue;
			}
			VBox values = new VBox(2);
			entity.getAll(property).forEach(v -> values.getChildren().add(value(v, current)));
			properties.addRow(row++, propertyLabel(property), values);
		}
		content.getChildren().add(properties);

		// The entities referring to this one, as the preview's reverse links
		List<Node> referredBy = new ArrayList<>();
		for (Entity other : current.getEntities()) {
			for (String property : other.getValues().keySet()) {
				boolean refers = other.getAll(property).stream()
						.anyMatch(v -> v.isObject() && entity.getId().equals(v.path("@id").asText(null)));
				if (refers) {
					Hyperlink link = link(other.getLabel(), other);
					link.setText(other.getLabel() + " (" + property + ")");
					referredBy.add(link);
				}
			}
		}
		if (!referredBy.isEmpty()) {
			Label heading = new Label("Referenced by");
			heading.setStyle("-fx-font-weight: bold;");
			VBox links = new VBox(2);
			links.setId("previewReferencedBy");
			links.getChildren().addAll(referredBy);
			content.getChildren().addAll(heading, links);
		}
	}

	private static GridPane table() {
		GridPane table = new GridPane();
		table.setHgap(12);
		table.setVgap(6);
		ColumnConstraints names = new ColumnConstraints();
		names.setMinWidth(120);
		ColumnConstraints values = new ColumnConstraints();
		values.setHgrow(Priority.ALWAYS);
		table.getColumnConstraints().setAll(names, values);
		return table;
	}

	private static Label propertyLabel(String property) {
		Label label = new Label(property);
		label.setStyle("-fx-font-weight: bold;");
		GridPane.setValignment(label, VPos.TOP);
		label.setPadding(new Insets(3, 0, 0, 0));
		return label;
	}

	/**
	 * A value: a link to an entity of the crate or a web address, or text.
	 */
	private Node value(JsonNode value, Crate current) {
		if (value.isObject() && value.path("@id").isTextual()) {
			String id = value.get("@id").asText();
			Optional<Entity> target = current.get(id);
			if (target.isPresent()) {
				Hyperlink link = link(target.get().getLabel(), target.get());
				if (!target.get().getLabel().equals(id)) {
					link.setTooltip(new Tooltip(id));
				}
				return link;
			}
			if (isWebAddress(id)) {
				return webLink(id);
			}
			Label missing = new Label(id + " (not in the crate)");
			missing.setStyle("-fx-text-fill: #808080;");
			return missing;
		}
		if (value.isTextual() && isWebAddress(value.asText())) {
			return webLink(value.asText());
		}
		Label text = new Label(value.isValueNode() ? value.asText() : value.toString());
		text.setWrapText(true);
		return text;
	}

	private Hyperlink link(String text, Entity target) {
		Hyperlink link = new Hyperlink(text);
		link.setOnAction(e -> open.accept(target));
		return link;
	}

	private Hyperlink webLink(String address) {
		Hyperlink link = new Hyperlink(address);
		link.setTooltip(new Tooltip("Open in the browser"));
		link.setOnAction(e -> browse.accept(address));
		return link;
	}

	private static boolean isWebAddress(String text) {
		try {
			URI uri = new URI(text);
			return uri.isAbsolute() && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()));
		} catch (URISyntaxException e) {
			return false;
		}
	}
}
