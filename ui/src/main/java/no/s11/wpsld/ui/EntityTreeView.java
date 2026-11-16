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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;


import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
 * <p>
 * An entity may be in the tree more than once, e.g. in the folder of each
 * of its types: selecting it selects each, also in folders opened later.
 */
class EntityTreeView extends TreeView<Node> {
	private final EntityListing listing;
	/** Whether each folder, by its key, was open */
	private final Map<String, Boolean> expanded = new HashMap<>();
	/** The tree as last made, to make it again only if it changed */
	private List<Object> made = List.of();
	/** Entities with a problem, e.g. a file not found, and what it is */
	private Map<Entity, String> problems = Map.of();
	/** While selecting the other places of the entities selected */
	private boolean selectingAlso;

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
			private final EntityIcons.Icon icon = EntityIcons.forCell(this);
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
				getStyleClass().removeAll("not-data", "problem");
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
				Optional<String> problem = node.entity().map(problems::get);
				icon.set(problem.isPresent() ? EntityIcons.problem(node.entity().get(), listing)
						: EntityIcons.of(node, listing));
				setGraphic(icon);
				if (problem.isPresent()) {
					getStyleClass().add("problem");
				} else if ((node.kind() == Kind.FILE || node.kind() == Kind.FOLDER) && !node.data()) {
					getStyleClass().add("not-data");
				}
				Optional<String> tip = tip(node).map(t -> problem.map(p -> p + "\n" + t).orElse(t))
						.or(() -> problem);
				tooltip.setText(tip.orElse(""));
				setTooltip(tip.isPresent() ? tooltip : null);
			}
		});
		// The other places of an entity selected, or deselected: after the
		// change, as the selection can't change while it notifies
		getSelectionModel().getSelectedItems().addListener((ListChangeListener<TreeItem<Node>>) change -> {
			if (selectingAlso) {
				return;
			}
			Set<Entity> added = new HashSet<>();
			Set<Entity> removed = new HashSet<>();
			while (change.next()) {
				change.getAddedSubList().stream().filter(Objects::nonNull)
						.forEach(item -> item.getValue().entity().ifPresent(added::add));
				change.getRemoved().stream().filter(Objects::nonNull)
						.forEach(item -> item.getValue().entity().ifPresent(removed::add));
			}
			removed.removeAll(added);
			if (!added.isEmpty() || !removed.isEmpty()) {
				Platform.runLater(() -> selectAlso(added, removed));
			}
		});
		// + - * / open and close the folders selected, all of them (JavaFX's
		// own keys act on the one focused), and * and / all within them too
		addEventFilter(KeyEvent.KEY_PRESSED, e -> {
			if (e.getCode() == KeyCode.ADD || e.getCode() == KeyCode.SUBTRACT || e.getCode() == KeyCode.MULTIPLY
					|| e.getCode() == KeyCode.DIVIDE) {
				// Acted on as typed, below
				e.consume();
			}
		});
		addEventFilter(KeyEvent.KEY_TYPED, e -> {
			switch (e.getCharacter()) {
			case "+" -> expand(false);
			case "*" -> expand(true);
			case "-" -> collapse(false);
			case "/" -> collapse(true);
			default -> {
				return;
			}
			}
			e.consume();
		});
		// Places of the entities selected in a folder opened
		expandedItemCountProperty().addListener((obs, old, count) -> {
			if (!selectingAlso && count.intValue() > old.intValue()) {
				Platform.runLater(() -> selectAlso(Set.copyOf(selectedEntities()), Set.of()));
			}
		});
	}

	/** Whether a row is shown now, not to scroll to it (e.g. after adding what was right-clicked) */
	private boolean visible(int row) {
		if (lookup(".virtual-flow") instanceof javafx.scene.control.skin.VirtualFlow<?> flow
				&& flow.getFirstVisibleCell() != null && flow.getLastVisibleCell() != null) {
			return row > flow.getFirstVisibleCell().getIndex() && row < flow.getLastVisibleCell().getIndex();
		}
		return false;
	}

	/**
	 * Open the folders selected (all the top ones if none are), and with
	 * all, every folder within them too (Expand All, *)
	 */
	void expand(boolean all) {
		chosen().forEach(item -> open(item, true, all));
	}

	/**
	 * Close the folders selected (all the top ones if none are), and with
	 * all, every folder within them too, so that they open closed (Collapse
	 * All, /)
	 */
	void collapse(boolean all) {
		chosen().forEach(item -> open(item, false, all));
	}

	private static void open(TreeItem<Node> item, boolean open, boolean all) {
		if (!item.isLeaf()) {
			item.setExpanded(open);
		}
		if (all) {
			item.getChildren().forEach(child -> open(child, open, true));
		}
	}

	/**
	 * The items selected, each row asked (the selection model's list counts
	 * from its start for each item), else the top ones
	 */
	private List<TreeItem<Node>> chosen() {
		List<TreeItem<Node>> selected = new ArrayList<>();
		for (int row = 0; row < getExpandedItemCount(); row++) {
			if (getSelectionModel().isSelected(row)) {
				selected.add(getTreeItem(row));
			}
		}
		return selected.isEmpty() ? List.copyOf(getRoot().getChildren()) : selected;
	}

	/**
	 * Select each place of these entities shown, and deselect each of
	 * those, keeping the one selected last, and focused, as it was
	 */
	private void selectAlso(Set<Entity> select, Set<Entity> deselect) {
		selectingAlso = true;
		try {
			TreeItem<Node> last = getSelectionModel().getSelectedItem();
			int focused = getFocusModel().getFocusedIndex();
			rows(deselect).stream().filter(getSelectionModel()::isSelected)
					.forEach(getSelectionModel()::clearSelection);
			selectRows(rows(select).stream().filter(row -> !getSelectionModel().isSelected(row)).toList());
			if (last != null && getSelectionModel().getSelectedItems().contains(last)) {
				// Selected last again, as the one edited
				getSelectionModel().select(last);
			}
			getFocusModel().focus(focused);
		} finally {
			selectingAlso = false;
		}
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
		case FOLDER, FILE -> node.entity().isEmpty()
				? node.kind() == Kind.FILE ? "In the crate's folder, but not in the crate: Add File(s) to Has Part to add it"
						: "A folder with no Dataset entity"
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
				.map(item -> mark(item.getValue())).toList();
		getRoot().getChildren().setAll(nodes.stream().map(node -> item(node, true)).toList());
		selectingAlso = true;
		try {
			getSelectionModel().clearSelection();
			selectRows(rows(new HashSet<>(selected)));
			if (edited != null && selected.contains(edited)) {
				TreeItem<Node> first = firsts(Set.of(edited)).get(edited);
				if (first != null) {
					getSelectionModel().select(first);
				}
			}
		} finally {
			selectingAlso = false;
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

	/** What an item is selected again by: its entity, or its key (a folder without one) */
	private static Object mark(Node node) {
		return node.entity().<Object>map(e -> e).orElse(node.key());
	}

	/**
	 * The rows of the places shown (in open folders) of these entities or
	 * keys, in one walk, as there may be thousands
	 */
	private List<Integer> rows(Set<?> marks) {
		List<Integer> rows = new ArrayList<>();
		int[] row = { 0 };
		getRoot().getChildren().forEach(child -> rows(child, marks, row, rows));
		return rows;
	}

	private static void rows(TreeItem<Node> item, Set<?> marks, int[] row, List<Integer> rows) {
		if (marks.contains(mark(item.getValue()))) {
			rows.add(row[0]);
		}
		row[0]++;
		if (item.isExpanded()) {
			item.getChildren().forEach(child -> rows(child, marks, row, rows));
		}
	}

	/** The first item of each of these entities or keys, open or not */
	private Map<Object, TreeItem<Node>> firsts(Set<?> marks) {
		Map<Object, TreeItem<Node>> first = new HashMap<>();
		Deque<TreeItem<Node>> stack = new ArrayDeque<>(getRoot().getChildren().reversed());
		while (!stack.isEmpty() && first.size() < marks.size()) {
			TreeItem<Node> item = stack.pop();
			Object mark = mark(item.getValue());
			if (marks.contains(mark)) {
				first.putIfAbsent(mark, item);
			}
			item.getChildren().reversed().forEach(stack::push);
		}
		return first;
	}

	/** Select these rows at once, rather than one change for each */
	private void selectRows(List<Integer> rows) {
		if (!rows.isEmpty()) {
			getSelectionModel().selectIndices(rows.get(0),
					rows.subList(1, rows.size()).stream().mapToInt(Integer::intValue).toArray());
		}
	}

	/**
	 * Select these entities, opening the folders they are in, the last one
	 * scrolled to. False if any is not in the tree.
	 */
	boolean select(List<Entity> entities) {
		Set<Object> marks = new HashSet<>(entities);
		Map<Object, TreeItem<Node>> first = firsts(marks);
		selectingAlso = true;
		try {
			getSelectionModel().clearSelection();
			// Open the folder of each one's first place, and select each place shown
			for (TreeItem<Node> found : first.values()) {
				for (TreeItem<Node> parent = found.getParent(); parent != null; parent = parent.getParent()) {
					parent.setExpanded(true);
				}
			}
			selectRows(rows(marks));
			// The last one's first place selected last, as the one edited
			if (!entities.isEmpty() && first.containsKey(entities.getLast())) {
				getSelectionModel().select(first.get(entities.getLast()));
			}
		} finally {
			selectingAlso = false;
		}
		int last = getSelectionModel().getSelectedIndex();
		if (last >= 0 && !visible(last)) {
			scrollTo(Math.max(0, last - 3));
		}
		return first.size() == marks.size();
	}

	/**
	 * Mark these entities as having a problem, e.g. a file not found, saying
	 * what it is
	 */
	void setProblems(Map<Entity, String> problems) {
		if (!problems.equals(this.problems)) {
			this.problems = Map.copyOf(problems);
			refresh();
		}
	}

	/** The files selected that have no entity, by their @ids */
	List<String> selectedFileIds() {
		return getSelectionModel().getSelectedItems().stream().filter(Objects::nonNull).map(TreeItem::getValue)
				.map(EntityTree::fileId).flatMap(Optional::stream).distinct().toList();
	}

	/** The folders selected that have no Dataset, by their @ids, e.g. raw/ */
	List<String> selectedFolderIds() {
		return getSelectionModel().getSelectedItems().stream().filter(Objects::nonNull).map(TreeItem::getValue)
				.filter(node -> node.entity().isEmpty()).map(EntityTree::folderId).flatMap(Optional::stream)
				.distinct().toList();
	}

	/** The entities selected, each once, the one selected last at the end */
	List<Entity> selectedEntities() {
		List<Entity> entities = new ArrayList<>(getSelectionModel().getSelectedItems().stream()
				.filter(Objects::nonNull).map(TreeItem::getValue).map(Node::entity).flatMap(Optional::stream)
				.distinct().toList());
		Optional.ofNullable(getSelectionModel().getSelectedItem()).flatMap(item -> item.getValue().entity())
				.ifPresent(last -> {
					entities.remove(last);
					entities.add(last);
				});
		return entities;
	}
}
