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
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
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
import javafx.scene.text.TextFlow;
import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.crate.Places;
import no.s11.wpsld.form.Markdown;

/**
 * A read-only preview of one entity of the crate, like an RO-Crate's
 * <code>ro-crate-preview.html</code>: its name, types and identifier, a table
 * of its properties, and the entities referring to it. References to other
 * entities of the crate are links that open them; absolute URLs open in the
 * browser, as does where a place is, on OpenStreetMap.
 */
public class EntityPreview extends ScrollPane {
	private final Supplier<Crate> crate;
	/** Opens an entity of the crate, e.g. selecting it for editing */
	private final Consumer<Entity> open;
	/** Opens a web address, e.g. in the browser */
	private final Consumer<String> browse;
	/** Human readable label of a property */
	private final Function<String, String> label;
	/** The folder of the crate, if it has one (opened from or saved to it) */
	private Supplier<Optional<Path>> folder = Optional::empty;
	/** Whether a property takes URLs, if known */
	private Function<String, Optional<Boolean>> takesUrl = property -> Optional.empty();
	private final VBox content = new VBox(8);
	private Entity entity;

	public EntityPreview(Supplier<Crate> crate, Consumer<Entity> open, Consumer<String> browse,
			Function<String, String> label) {
		this.crate = Objects.requireNonNull(crate);
		this.open = Objects.requireNonNull(open);
		this.browse = Objects.requireNonNull(browse);
		this.label = Objects.requireNonNull(label);
		content.setPadding(new Insets(12));
		content.setId("entityPreview");
		setContent(content);
		setFitToWidth(true);
	}

	/**
	 * Where the crate is, to open the files its relative <code>@id</code>s
	 * name; none for a new crate.
	 */
	public void setFolder(Supplier<Optional<Path>> folder) {
		this.folder = Objects.requireNonNull(folder);
	}

	/**
	 * How to tell whether a property takes URLs, by its range, to show its
	 * text values as links.
	 */
	public void setTakesUrl(Function<String, Optional<Boolean>> takesUrl) {
		this.takesUrl = Objects.requireNonNull(takesUrl);
	}

	/** How many values of a property are shown at first */
	static final int SHOWN = 100;

	/**
	 * Show an entity.
	 */
	public void show(Entity entity) {
		this.entity = entity;
		refresh();
		onShow.run();
	}

	/** Run after another entity (or none) is shown */
	private Runnable onShow = () -> {
	};

	public void setOnShow(Runnable onShow) {
		this.onShow = Objects.requireNonNull(onShow);
	}

	/** The entity shown, if any */
	public Entity getEntity() {
		return entity;
	}

	/**
	 * A part of the preview: what it shows (compared to tell whether it
	 * changed) and how to make its node
	 */
	private record Part(String key, Object shows, Supplier<Node> make) {
	}

	/** The entity whose parts are shown, and what each shows, by key */
	private Entity shown;
	private final Map<String, Object> shows = new HashMap<>();
	private final Map<String, Node> nodes = new HashMap<>();
	private List<String> topKeys = List.of();
	private List<String> rowKeys = List.of();
	private GridPane properties;

	/**
	 * Show the entity again, e.g. after it or other entities changed: of
	 * the same entity, only the parts that changed are made again (typing
	 * in a large entity, the others' layout took most of the time)
	 */
	public void refresh() {
		if (entity == null || !crate.get().getEntities().contains(entity)) {
			content.getChildren().clear();
			shown = null;
			return;
		}
		Crate current = crate.get();
		List<Part> top = new ArrayList<>();
		top.add(new Part("name", entity.getLabel(), () -> {
			Label name = new Label(entity.getLabel());
			name.setId("previewName");
			name.setWrapText(true);
			name.setStyle("-fx-font-size: 1.6em; -fx-font-weight: bold;");
			return name;
		}));
		top.add(new Part("types", List.copyOf(entity.getTypes()), () -> {
			Label types = new Label(String.join(", ", entity.getTypes()));
			types.setId("previewTypes");
			types.setWrapText(true);
			types.getStyleClass().add("dim-text");
			return types;
		}));
		// On its own line, as several types may take the whole width
		top.add(new Part("id", entity.getId(), () -> {
			Node id = identifier(entity.getId());
			id.setId("previewId");
			return id;
		}));
		// The description as a paragraph under the name, not in the table
		List<String> descriptions = entity.getAll("description").stream().filter(JsonNode::isValueNode)
				.map(JsonNode::asText).toList();
		if (!descriptions.isEmpty()) {
			top.add(new Part("description", descriptions, () -> description(String.join("\n\n", descriptions))));
		}
		if (entity != current.getRoot()) {
			top.add(new Part("root", current.getRoot().getLabel(), () -> {
				Hyperlink root = link("Go to the crate: " + current.getRoot().getLabel(), current.getRoot());
				root.setId("previewRoot");
				return root;
			}));
		}
		// Where a place, its geometry or coordinates are
		Places.of(entity, current).ifPresent(area -> top.add(new Part("map", area, () -> {
			Hyperlink map = new Hyperlink(area.isPoint() ? "Show on OpenStreetMap"
					: "Show the area on OpenStreetMap");
			map.setId("previewMap");
			map.setTooltip(new Tooltip(String.format(Locale.ROOT, "Latitude %.6f, longitude %.6f",
					area.latitude(), area.longitude())));
			map.setOnAction(e -> browse.accept(area.openStreetMap()));
			return map;
		})));
		top.add(new Part("properties", "", () -> properties));

		List<Part> rows = new ArrayList<>();
		for (String property : entity.getValues().keySet()) {
			if (property.equals("name") || property.equals("description") && !descriptions.isEmpty()) {
				// Shown above
				continue;
			}
			List<JsonNode> all = entity.getAll(property);
			// Copies, as values may be changed in place
			rows.add(new Part(property, List.of(all.stream().map(JsonNode::deepCopy).toList(), targets(all, current)),
					() -> values(property, all, current)));
		}

		// The entities referring to this one, as the preview's reverse links
		List<List<Object>> referring = new ArrayList<>();
		for (Entity other : current.getEntities()) {
			for (String property : other.getValues().keySet()) {
				boolean refers = other.getAll(property).stream()
						.anyMatch(v -> v.isObject() && entity.getId().equals(v.path("@id").asText(null)));
				if (refers) {
					referring.add(List.of(other, other.getLabel(), property));
				}
			}
		}
		if (!referring.isEmpty()) {
			top.add(new Part("referencedBy", referring, () -> {
				Label heading = new Label("Referenced by");
				heading.setStyle("-fx-font-weight: bold;");
				VBox links = new VBox(2);
				links.setId("previewReferencedBy");
				for (List<Object> refer : referring) {
					Hyperlink link = link((String) refer.get(1), (Entity) refer.get(0));
					link.setText(refer.get(1) + " (" + refer.get(2) + ")");
					links.getChildren().add(link);
				}
				return new VBox(8, heading, links);
			}));
		}

		List<String> newTop = top.stream().map(Part::key).toList();
		List<String> newRows = rows.stream().map(Part::key).toList();
		if (entity != shown || !newTop.equals(topKeys) || !newRows.equals(rowKeys)) {
			// Made anew
			shown = entity;
			topKeys = newTop;
			rowKeys = newRows;
			shows.clear();
			nodes.clear();
			properties = table();
			properties.setId("previewProperties");
			int row = 0;
			for (Part part : rows) {
				Node values = part.make().get();
				properties.addRow(row++, propertyLabel(part.key()), values);
				shows.put("row:" + part.key(), part.shows());
				nodes.put("row:" + part.key(), values);
			}
			List<Node> made = new ArrayList<>();
			for (Part part : top) {
				Node node = part.make().get();
				made.add(node);
				shows.put(part.key(), part.shows());
				nodes.put(part.key(), node);
			}
			// All at once, as each child added is checked against all the others
			content.getChildren().setAll(made);
			return;
		}
		for (Part part : top) {
			if (!part.shows().equals(shows.get(part.key()))) {
				Node node = part.make().get();
				content.getChildren().set(content.getChildren().indexOf(nodes.get(part.key())), node);
				shows.put(part.key(), part.shows());
				nodes.put(part.key(), node);
			}
		}
		for (Part part : rows) {
			String key = "row:" + part.key();
			if (!part.shows().equals(shows.get(key))) {
				Node old = nodes.get(key);
				Node values = part.make().get();
				GridPane.setConstraints(values, 1, GridPane.getRowIndex(old));
				properties.getChildren().set(properties.getChildren().indexOf(old), values);
				shows.put(key, part.shows());
				nodes.put(key, values);
			}
		}
	}

	/** The description, formatted when written in Markdown, as the HTML preview does */
	private Node description(String text) {
		Node description;
		if (Markdown.looksLike(text)) {
			TextFlow flow = new TextFlow();
			flow.setMinWidth(0);
			HelpMarkdown.description(flow, text, browse);
			description = flow;
		} else {
			Label label = new Label(text);
			label.setWrapText(true);
			description = label;
		}
		description.setId("previewDescription");
		return description;
	}

	/**
	 * The labels of the entities the first values shown refer to, as their
	 * links show them
	 */
	private static List<String> targets(List<JsonNode> values, Crate current) {
		return values.stream().limit(SHOWN).filter(v -> v.isObject() && v.path("@id").isTextual())
				.map(v -> current.get(v.get("@id").asText()).map(Entity::getLabel).orElse("")).toList();
	}

	/** A property's values, at most {@link #SHOWN} until asked for all */
	private VBox values(String property, List<JsonNode> all, Crate current) {
		VBox values = new VBox(2);
		// All at once, as each child added is checked against all the others
		values.getChildren().setAll(all.stream().limit(SHOWN).map(v -> value(property, v, current)).toList());
		if (all.size() > SHOWN) {
			// e.g. a root's thousands of parts: the others on asking
			Hyperlink more = new Hyperlink("Show all " + all.size());
			more.setId("previewMore-" + property);
			more.setOnAction(e -> values.getChildren().setAll(
					all.stream().map(v -> value(property, v, current)).toList()));
			values.getChildren().add(more);
		}
		return values;
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

	private Label propertyLabel(String property) {
		Label label = new Label(this.label.apply(property));
		label.setTooltip(new Tooltip(property));
		label.setStyle("-fx-font-weight: bold;");
		GridPane.setValignment(label, VPos.TOP);
		label.setPadding(new Insets(3, 0, 0, 0));
		return label;
	}

	/**
	 * A value: a link to an entity of the crate or a web address, or text;
	 * text of a property that takes URLs as a link to the web or the file it
	 * names, of other properties not, and of properties not known only if
	 * it is a web address.
	 */
	private Node value(String property, JsonNode value, Crate current) {
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
			if (isWebAddress(current.expandId(id))) {
				return webLink(id, current.expandId(id));
			}
			Label missing = new Label(id + " (not in the crate)");
			missing.getStyleClass().add("faint-text");
			return missing;
		}
		if (value.isTextual()) {
			String text = value.asText();
			Optional<Boolean> url = takesUrl.apply(property);
			Optional<String> to = url.orElse(false) ? address(text)
					: url.isEmpty() && isWebAddress(text) ? Optional.of(text) : Optional.empty();
			if (to.isPresent()) {
				return to.get().equals(text) ? webLink(text) : fileLink(text, to.get());
			}
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

	/**
	 * An entity's <code>@id</code>, to open where it leads: a web address in
	 * the browser, a relative path (also <code>./</code>) as the file or
	 * folder beside the crate if there is one; others, such as
	 * <code>#local</code>, <code>_:blank</code> and <code>urn:uuid:</code>,
	 * just as text.
	 */
	private Node identifier(String id) {
		Optional<String> to = address(id);
		if (to.isPresent()) {
			return isWebAddress(to.get()) ? webLink(id, to.get()) : fileLink(id, to.get());
		}
		Label text = new Label(id);
		text.setWrapText(true);
		text.getStyleClass().add("dim-text");
		return text;
	}

	/**
	 * Where an <code>@id</code> leads, as {@link #identifier} links it: a
	 * web address, or the address of the file or folder beside the crate;
	 * none for others.
	 */
	Optional<String> address(String id) {
		String iri = crate.get().expandId(id);
		return isWebAddress(iri) ? Optional.of(iri) : local(id).map(file -> file.toUri().toString());
	}

	/**
	 * The existing file or folder a relative <code>@id</code> names beside the
	 * crate, e.g. <code>data/table.csv</code> or <code>./</code>; none for
	 * <code>#local</code> ids, blank nodes and absolute URIs, or if the crate
	 * has no folder.
	 */
	private Optional<Path> local(String id) {
		if (id.isEmpty() || id.startsWith("#") || id.startsWith("_:")) {
			return Optional.empty();
		}
		try {
			URI uri = new URI(id);
			if (uri.isAbsolute() || uri.getPath() == null || uri.getPath().isEmpty()) {
				return Optional.empty();
			}
			Optional<Path> dir = folder.get().map(Path::normalize);
			// Not outside the crate's folder, e.g. ../other
			return dir.map(d -> d.resolve(uri.getPath()).normalize()).filter(file -> file.startsWith(dir.get()))
					.filter(Files::exists);
		} catch (URISyntaxException | InvalidPathException e) {
			return Optional.empty();
		}
	}

	private Hyperlink webLink(String address) {
		return webLink(address, address);
	}

	/**
	 * A link to a web address, shown as written, e.g. a compact IRI such as
	 * <code>s:ActiveActionStatus</code> for its expanded address.
	 */
	private Hyperlink webLink(String text, String address) {
		Hyperlink link = new Hyperlink(text);
		link.setTooltip(new Tooltip(text.equals(address) ? "Open in the browser" : "Open " + address + " in the browser"));
		link.setOnAction(e -> browse.accept(address));
		return link;
	}

	/** A link to a file or folder beside the crate, by its file: address */
	private Hyperlink fileLink(String text, String address) {
		Hyperlink link = new Hyperlink(text);
		link.setTooltip(new Tooltip(address.endsWith("/") ? "Open the folder" : "Open the file"));
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
