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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.kordamp.ikonli.javafx.FontIcon;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.form.EntityListing;
import no.s11.wpsld.form.EntityTree;
import no.s11.wpsld.form.EntityTree.Kind;
import no.s11.wpsld.form.EntityTree.Node;

/**
 * The entities of a crate as a tree of folders (see {@link EntityTree}),
 * each with its icon. Folders of the path without an entity, and entities
 * with a path that are not data entities, are greyed out. Made again when
 * the crate changes, with the same folders open and the same selected.
 */
class EntityTreeView extends TreeView<Node> {
	private final EntityListing listing;
	/** Whether each folder, by its key, was open */
	private final Map<String, Boolean> expanded = new HashMap<>();
	/** The tree as last made, to make it again only if it changed */
	private List<Object> made = List.of();

	/**
	 * @param labels changes when entities may have been renamed, to show
	 *               their names as they are typed
	 */
	EntityTreeView(EntityListing listing, ObservableValue<?> labels) {
		this.listing = listing;
		setShowRoot(false);
		setRoot(new TreeItem<>());
		getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		setCellFactory(view -> new TreeCell<>() {
			private final FontIcon icon = EntityIcons.forCell(this);
			private final Tooltip tooltip = new Tooltip();
			private final InvalidationListener relabel = obs -> show(getItem(), isEmpty());
			{
				labels.addListener(new WeakInvalidationListener(relabel));
			}

			@Override
			protected void updateItem(Node node, boolean empty) {
				super.updateItem(node, empty);
				show(node, empty);
			}

			private void show(Node node, boolean empty) {
				getStyleClass().remove("not-data");
				if (empty || node == null) {
					setText(null);
					setGraphic(null);
					setTooltip(null);
					return;
				}
				String text = text(node);
				if (!Objects.equals(text, getText())) {
					setText(text);
				}
				icon.setIconCode(EntityIcons.of(node, listing));
				setGraphic(icon);
				if ((node.kind() == Kind.FILE || node.kind() == Kind.FOLDER) && !node.data()) {
					getStyleClass().add("not-data");
				}
				Optional<String> tip = tip(node);
				tooltip.setText(tip.orElse(""));
				setTooltip(tip.isPresent() ? tooltip : null);
			}
		});
	}

	/**
	 * What a node says: its name in the path, and the entity's name if it
	 * has another; the entity's name as typed; or a folder's name and how
	 * many it holds.
	 */
	private static String text(Node node) {
		Optional<Entity> entity = node.entity();
		return switch (node.kind()) {
		case ROOT, CONTEXTUAL -> entity.map(Entity::getLabel).orElse(node.name());
		case FILE, FOLDER, RESOURCE -> node.name() + entity.map(Entity::getLabel)
				.filter(label -> !label.equals(entity.get().getId()) && !label.equals(node.name()))
				.map(label -> " — " + label).orElse("");
		case EXTERNAL, TYPE -> node.name() + " (" + node.children().size() + ")";
		};
	}

	/** The entity's types and @id; or what a greyed out node is */
	private static Optional<String> tip(Node node) {
		String state = switch (node.kind()) {
		case FOLDER, FILE -> node.entity().isEmpty() ? "A folder with no Dataset entity"
				: node.data() ? "" : "Not a data entity: not a part (hasPart) of the crate or its datasets";
		default -> "";
		};
		return node.entity().map(e -> String.join(", ", e.getTypes()) + "  " + e.getId())
				.map(about -> state.isEmpty() ? about : about + "\n" + state)
				.or(() -> Optional.of(state).filter(s -> !s.isEmpty()));
	}

	/**
	 * Show these nodes: made again if they changed, with the folders open
	 * that were (at first the top ones), and selected again what was, the
	 * entity edited last. Selecting may edit, so the caller guards it.
	 */
	void setNodes(List<Node> nodes, Entity edited) {
		List<Object> shape = new ArrayList<>();
		shape(nodes, shape);
		if (shape.equals(made)) {
			return;
		}
		made = shape;
		List<Object> selected = getSelectionModel().getSelectedItems().stream().filter(Objects::nonNull)
				.map(item -> item.getValue().entity().<Object>map(e -> e).orElse(item.getValue().key())).toList();
		getRoot().getChildren().setAll(nodes.stream().map(node -> item(node, true)).toList());
		getSelectionModel().clearSelection();
		for (Object mark : selected) {
			if (mark != edited) {
				find(getRoot(), mark).ifPresent(getSelectionModel()::select);
			}
		}
		if (edited != null && selected.contains(edited)) {
			find(getRoot(), edited).ifPresent(getSelectionModel()::select);
		}
	}

	/** What tells the tree has changed: keys, names, entities and their names */
	private static void shape(List<Node> nodes, List<Object> shape) {
		for (Node node : nodes) {
			shape.add(node.key());
			shape.add(node.name());
			shape.add(node.data());
			node.entity().ifPresent(shape::add);
			shape(node.children(), shape);
			shape.add("/");
		}
	}

	private TreeItem<Node> item(Node node, boolean top) {
		TreeItem<Node> item = new TreeItem<>(node);
		node.children().stream().map(child -> item(child, false)).forEach(item.getChildren()::add);
		item.setExpanded(expanded.getOrDefault(node.key(), top));
		item.expandedProperty().addListener((obs, old, open) -> expanded.put(node.key(), open));
		return item;
	}

	/** The item of an entity, or of a key (a folder without one) */
	private static Optional<TreeItem<Node>> find(TreeItem<Node> item, Object mark) {
		Node node = item.getValue();
		if (node != null && (node.entity().<Object>map(e -> e).orElse(node.key()).equals(mark))) {
			return Optional.of(item);
		}
		return item.getChildren().stream().map(child -> find(child, mark)).flatMap(Optional::stream).findFirst();
	}

	/**
	 * Select these entities, opening the folders they are in, the last one
	 * scrolled to. False if any is not in the tree.
	 */
	boolean select(List<Entity> entities) {
		getSelectionModel().clearSelection();
		boolean all = true;
		for (Entity entity : entities) {
			Optional<TreeItem<Node>> item = find(getRoot(), entity);
			all &= item.isPresent();
			item.ifPresent(found -> {
				for (TreeItem<Node> parent = found.getParent(); parent != null; parent = parent.getParent()) {
					parent.setExpanded(true);
				}
				getSelectionModel().select(found);
			});
		}
		int last = getSelectionModel().getSelectedIndex();
		if (last >= 0) {
			scrollTo(Math.max(0, last - 3));
		}
		return all;
	}

	/** The entities selected, the one selected last at the end */
	List<Entity> selectedEntities() {
		List<Entity> entities = new ArrayList<>(getSelectionModel().getSelectedItems().stream()
				.filter(Objects::nonNull).map(TreeItem::getValue).map(Node::entity).flatMap(Optional::stream)
				.toList());
		Optional.ofNullable(getSelectionModel().getSelectedItem()).flatMap(item -> item.getValue().entity())
				.ifPresent(last -> {
					entities.remove(last);
					entities.add(last);
				});
		return entities;
	}
}
