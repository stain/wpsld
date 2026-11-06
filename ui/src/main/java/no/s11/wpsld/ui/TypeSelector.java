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
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.form.Labels;
import no.s11.wpsld.soss.ClassDef;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Pop-up to choose a schema.org type: a tree of types under Thing, and a
 * search of type names and descriptions that trims the tree to the matches
 * and their superclasses.
 */
public class TypeSelector extends Dialog<ClassDef> {
	private static final String S = "http://schema.org/";

	private final SchemaFromJsonLd schema;
	private final CrateTerms terms;
	private final ClassDef thing;
	private final TreeView<ClassDef> tree = new TreeView<>();
	private final Label status = new Label();
	/** Types matching the search, shown in bold */
	private Set<ClassDef> matches = Set.of();

	public TypeSelector(SchemaFromJsonLd schema, Window owner) {
		this.schema = schema;
		this.terms = new CrateTerms(schema);
		this.thing = schema.getClasses().get(URI.create(S + "Thing"));
		initOwner(owner);
		setTitle("Add type");
		setResizable(true);

		TextField search = new TextField();
		search.setId("typeSearch");
		search.setPromptText("Search type names and descriptions");
		search.textProperty().addListener((obs, old, query) -> filter(query));

		tree.setId("typeTree");
		tree.setCellFactory(view -> new TreeCell<>() {
			@Override
			protected void updateItem(ClassDef classDef, boolean empty) {
				super.updateItem(classDef, empty);
				setText(empty || classDef == null ? null : label(classDef));
				setStyle(!empty && matches.contains(classDef) ? "-fx-font-weight: bold;" : "");
				setTooltip(empty || classDef == null ? null
						: classDef.getComment().map(Labels::plainText).map(EntityEditor::tooltip).orElse(null));
			}
		});
		tree.setOnMouseClicked(e -> {
			if (e.getClickCount() == 2 && tree.getSelectionModel().getSelectedItem() != null) {
				setResult(tree.getSelectionModel().getSelectedItem().getValue());
				close();
			}
		});
		VBox.setVgrow(tree, Priority.ALWAYS);
		VBox content = new VBox(8, search, tree, status);
		content.setPrefSize(480, 600);
		getDialogPane().setContent(content);
		getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		Node ok = getDialogPane().lookupButton(ButtonType.OK);
		ok.disableProperty().bind(tree.getSelectionModel().selectedItemProperty().isNull());
		setResultConverter(button -> button == ButtonType.OK && tree.getSelectionModel().getSelectedItem() != null
				? tree.getSelectionModel().getSelectedItem().getValue()
				: null);

		filter("");
		Platform.runLater(search::requestFocus);
	}

	private void filter(String query) {
		String q = query.strip().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			matches = Set.of();
			TreeItem<ClassDef> root = new LazyClassTreeItem(thing);
			root.setExpanded(true);
			tree.setRoot(root);
			status.setText("");
			return;
		}
		matches = schema.getClasses().values().stream()
				.filter(c -> !schema.isSuperseded(c))
				.filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(q)
						|| terms.name(c).toLowerCase(Locale.ROOT).contains(q)
						|| c.getComment().map(t -> t.toLowerCase(Locale.ROOT).contains(q)).orElse(false))
				.collect(Collectors.toSet());
		// The matches and the paths to them
		Set<ClassDef> shown = matches.stream().flatMap(c -> c.getSelfAndSuperClasses().stream())
				.collect(Collectors.toSet());
		tree.setRoot(shown.contains(thing) ? filteredItem(thing, shown) : null);
		status.setText(matches.size() + " matching types");
		// Select the best match: name starting with the query, else any match
		matches.stream()
				.sorted(Comparator.comparing((ClassDef c) -> !terms.name(c).toLowerCase(Locale.ROOT).startsWith(q)
						&& !c.getName().toLowerCase(Locale.ROOT).startsWith(q)).thenComparing(terms::name))
				.findFirst().ifPresent(this::select);
	}

	/**
	 * The name as written in RO-Crates, with the schema.org name if
	 * different, e.g. "File (MediaObject)".
	 */
	private String label(ClassDef classDef) {
		String name = terms.name(classDef);
		return name.equals(classDef.getName()) ? name : name + " (" + classDef.getName() + ")";
	}

	private TreeItem<ClassDef> filteredItem(ClassDef classDef, Set<ClassDef> shown) {
		TreeItem<ClassDef> item = new TreeItem<>(classDef);
		item.setExpanded(true);
		schema.subclasses(classDef).filter(shown::contains).map(c -> filteredItem(c, shown))
				.forEach(item.getChildren()::add);
		return item;
	}

	private void select(ClassDef classDef) {
		TreeItem<ClassDef> item = find(tree.getRoot(), classDef);
		if (item != null) {
			tree.getSelectionModel().select(item);
			tree.scrollTo(Math.max(0, tree.getRow(item) - 3));
		}
	}

	private static TreeItem<ClassDef> find(TreeItem<ClassDef> item, ClassDef classDef) {
		if (item == null || item.getValue() == classDef) {
			return item;
		}
		for (TreeItem<ClassDef> child : item.getChildren()) {
			TreeItem<ClassDef> found = find(child, classDef);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/**
	 * Tree item creating the subclasses (except superseded ones) when first expanded.
	 */
	private class LazyClassTreeItem extends TreeItem<ClassDef> {
		private boolean childrenLoaded;

		LazyClassTreeItem(ClassDef classDef) {
			super(classDef);
		}

		@Override
		public ObservableList<TreeItem<ClassDef>> getChildren() {
			if (!childrenLoaded) {
				childrenLoaded = true;
				super.getChildren().setAll(schema.subclasses(getValue()).filter(c -> !schema.isSuperseded(c))
						.map(LazyClassTreeItem::new).toList());
			}
			return super.getChildren();
		}

		@Override
		public boolean isLeaf() {
			return childrenLoaded ? super.getChildren().isEmpty() : schema.subclasses(getValue()).findAny().isEmpty();
		}
	}
}
