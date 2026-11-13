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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;


import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import no.s11.wpsld.Settings;
import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateChecks;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateFolder;
import no.s11.wpsld.crate.SafeSave;
import no.s11.wpsld.crate.CrateDocument.Problem;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.lookup.DOI;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.lookup.GeoNames;
import no.s11.wpsld.lookup.ORCID;
import no.s11.wpsld.lookup.ROR;
import no.s11.wpsld.lookup.PRONOMFormats;
import no.s11.wpsld.lookup.WebAvailability;
import no.s11.wpsld.lookup.SPDXLicenses;
import no.s11.wpsld.crate.UndoHistory;
import no.s11.wpsld.crate.UndoHistory.Place;
import no.s11.wpsld.crate.UndoHistory.Step;
import no.s11.wpsld.form.EntityListing;
import no.s11.wpsld.form.EntityTree;
import no.s11.wpsld.form.EntityListing.TypeCount;
import no.s11.wpsld.form.EntityTypes;
import no.s11.wpsld.form.FormFields;
import no.s11.wpsld.form.ProfileFormFields;
import no.s11.wpsld.form.RequiredFields;
import no.s11.wpsld.form.SchemaOrgFormFields;
import no.s11.wpsld.jsonld.JsonLdContext;
import no.s11.wpsld.masp.Profile;
import no.s11.wpsld.masp.ProfileCache;
import no.s11.wpsld.masp.ProfileRegistry;
import no.s11.wpsld.soss.SchemaFromJsonLd;

/**
 * Edits the entities of an RO-Crate, in the style of Crate-O: a list of the
 * crate's entities, an {@link EntityEditor} for the selected one, and a
 * preview of the resulting <code>ro-crate-metadata.json</code>.
 */
public class CrateEditor extends Application {
	private static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
	private static final KeyCombination REDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN,
			KeyCombination.SHIFT_DOWN);
	private static final KeyCombination REDO_WINDOWS = new KeyCodeCombination(KeyCode.Y,
			KeyCombination.SHORTCUT_DOWN);
	/** Incremented on changes, to update the labels of the entity list */
	private final LongProperty labels = new SimpleLongProperty();

	private final Supplier<SchemaFromJsonLd> loadSchema;
	private final Supplier<JsonLdContext> loadContext;
	private CompletableFuture<SchemaFromJsonLd> schemaLoading;
	private CompletableFuture<JsonLdContext> contextLoading;
	/** The crate being edited, replaced by File > New */
	private Crate crate = new Crate();
	/** Where the crate is saved, if it is, and whether it changed since */
	private final CrateFile crateFile = new CrateFile(crate.toJson());
	/** The window, once shown, whose title names the crate's file */
	private Stage stage;
	/** How wpsld names itself to web services, as their policies ask */
	static final String USER_AGENT = "wpsld/" + AboutDialog.version() + " (https://github.com/stain/wpsld)";
	private final MapTiles mapTiles = MapTiles.forApplication(USER_AGENT);
	private PlaceFinder.Search placeSearch = new GeoNames(ProfileCache.httpClient(Duration.ofSeconds(15)),
			Duration.ofSeconds(20), USER_AGENT)::search;
	/** Undo points of this crate: after leaving a field, adding, removing, changing types or @ids */
	private final UndoHistory history = new UndoHistory(crate.toJson());
	private final BooleanProperty canUndo = new SimpleBooleanProperty();
	private final BooleanProperty canRedo = new SimpleBooleanProperty();
	/** True while the profile is chosen to follow the crate's conformsTo, rather than by the user */
	private boolean followingCrate;
	private final ListView<Entity> entities = new ListView<>();
	/** Lists only entities of this type, and its subtypes; all if empty */
	private final ComboBox<TypeCount> entityType = new ComboBox<>();
	private EntityListing listing;
	/** True while the list is redone, keeping the selection, so as not to edit it again */
	private boolean relisting;
	/** The entities by their folders, shown instead of the list if chosen */
	private EntityTreeView tree;
	private boolean byFolder;
	/** Whether the tree shows the crate's parts (hasPart), not its folders */
	private boolean byPart;
	/** The entities selected in the list or tree shown, the one edited last */
	private final ObservableList<Entity> selection = FXCollections.observableArrayList();
	/** Whether none selected can be removed: none, or only the root data entity */
	private final BooleanBinding cannotRemove = Bindings
			.createBooleanBinding(() -> selection.stream().allMatch(e -> e == crate.getRoot()), selection);
	private final TextArea preview = new TextArea();
	private EntityPreview entityPreview;
	private EntityEditor entityEditor;
	private EntityTypes entityTypes;
	private CrateTerms terms;
	/** Which profile's fields the form shows */
	private final ComboBox<FormFields> profiles = new ComboBox<>();
	/** Bundled profiles, and those the user loaded before */
	private final ProfileCache profileCache;
	private final Button loadProfile = new Button("Load profile…");
	/** Loading a profile, or why it failed */
	private final Label profileStatus = new Label();
	private SchemaOrgFormFields schemaOrg;
	/** Where the user's preferences are kept, if they are */
	private final Optional<Path> settingsFile;
	private Settings settings = Settings.DEFAULT;
	/** The folder of the file last opened or saved, to start choosing there */
	private File lastFolder;
	/** The panes: entities (the sidebar), the form, the previews */
	private SplitPane split;
	private VBox sidebar;
	/** Where the dividers were when the sidebar was hidden */
	private double[] dividers;
	/** True while checking whether the file changed on disk, as its dialog moves the focus */
	private boolean checkingDisk;

	/** What to do when saving over a file that changed on disk */
	private enum Overwrite {
		SAVE_ANYWAY, RELOAD, SAVE_AS, CANCEL
	}

	public CrateEditor() {
		this(SchemaFromJsonLd::new, CrateTerms::prewarm, ProfileCache.forUser(), Optional.of(Settings.userFile()));
	}

	/**
	 * With another way to load the schema, e.g. failing in tests.
	 */
	CrateEditor(Supplier<SchemaFromJsonLd> loadSchema) {
		this(loadSchema, CrateTerms::prewarm);
	}

	/**
	 * With other ways to load the schema and the RO-Crate context, e.g. in
	 * tests.
	 */
	CrateEditor(Supplier<SchemaFromJsonLd> loadSchema, Supplier<JsonLdContext> loadContext) {
		this(loadSchema, loadContext, ProfileCache.bundled());
	}

	/**
	 * With other ways to load the schema and the RO-Crate context, and
	 * another store of profiles, e.g. in tests.
	 */
	CrateEditor(Supplier<SchemaFromJsonLd> loadSchema, Supplier<JsonLdContext> loadContext,
			ProfileCache profileCache) {
		this(loadSchema, loadContext, profileCache, Optional.empty());
	}

	/**
	 * With other ways to load the schema and the RO-Crate context, another
	 * store of profiles, and another settings file, e.g. in tests.
	 */
	CrateEditor(Supplier<SchemaFromJsonLd> loadSchema, Supplier<JsonLdContext> loadContext,
			ProfileCache profileCache, Optional<Path> settingsFile) {
		this.loadSchema = loadSchema;
		this.loadContext = loadContext;
		this.profileCache = profileCache;
		this.settingsFile = settingsFile;
	}

	@Override
	public void init() {
		// Load the schema while the JavaFX toolkit and window start up, and in
		// parallel the RO-Crate context that naming types and properties needs,
		// so that building the editor on the JavaFX thread doesn't load it
		schemaLoading = CompletableFuture.supplyAsync(loadSchema);
		contextLoading = CompletableFuture.supplyAsync(loadContext);
		settings = settingsFile.map(Settings::load).orElse(Settings.DEFAULT);
		profileCache.setDisabledRegistries(settings.disabledRegistries());
	}

	@Override
	public void start(Stage stage) {
		this.stage = stage;
		BorderPane root = new BorderPane(new Label("Loading schema.org " + SchemaFromJsonLd.SCHEMAORG_VERSION + "…"));
		stage.setScene(new Scene(root, 1400, 800));
		// For this window and every one opened after it
		Theme.choose(Theme.named(Theme.Style.class, settings.style()),
				Theme.named(Theme.Colours.class, settings.colours()));
		// Also if the window showed another scene before (as in tests)
		stage.sizeToScene();
		stage.setTitle("wpsld");
		// An undo point when leaving a field (or any control)
		stage.getScene().focusOwnerProperty().addListener((obs, old, now) -> checkpoint(placeOf(old)));
		// Redo also as on Windows and Linux; on macOS Command-Y is not Redo
		if (!CrateFile.onMac()) {
			stage.getScene().getAccelerators().put(REDO_WINDOWS, this::redo);
		}
		// In a text field, Ctrl+Z is first its own undo of typing; once it
		// has none, the crate's, e.g. in the field focused by an undo
		stage.getScene().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
			if (HelpDialog.isHelpShortcut(e)) {
				e.consume();
				showHelp(helpFor(stage.getScene().getFocusOwner()));
				return;
			}
			if (!(stage.getScene().getFocusOwner() instanceof TextInputControl field)) {
				return;
			}
			if (UNDO.match(e) && !field.isUndoable()) {
				e.consume();
				undo();
			} else if ((REDO.match(e) || (!CrateFile.onMac() && REDO_WINDOWS.match(e))) && !field.isRedoable()) {
				e.consume();
				redo();
			}
		});
		// Back from another application, e.g. git: has it changed the file?
		stage.focusedProperty().addListener((obs, was, focused) -> {
			if (focused) {
				Platform.runLater(this::checkDisk);
			}
		});
		// Closing the window, or File > Exit, asks to save changes
		stage.setOnCloseRequest(e -> {
			if (!mayClose(stage)) {
				e.consume();
			}
		});
		stage.show();

		// The loads started in init(): while still running, the editor
		// replaces the label when both complete; if done, this runs at once
		schemaLoading.thenCombine(contextLoading, (schema, context) -> schema)
				.whenComplete((schema, failure) -> Platform.runLater(() -> {
			if (failure != null) {
				boolean contextFailed = contextLoading.isCompletedExceptionally()
						&& !schemaLoading.isCompletedExceptionally();
				showFailure(root, contextFailed ? "Can't load the RO-Crate context" : "Can't load schema", failure);
				return;
			}
			try {
				root.setCenter(editor(schema));
			} catch (RuntimeException e) {
				// Otherwise lost in the CompletableFuture, leaving "Loading…"
				showFailure(root, "Can't show the editor", e);
				return;
			}
			Button save = new Button("Save");
			save.setId("save");
			save.setOnAction(e -> save(stage));
			profiles.setId("profile");
			profiles.setConverter(new StringConverter<>() {
				@Override
				public String toString(FormFields fields) {
					return fields == null ? "" : fields.getName();
				}

				@Override
				public FormFields fromString(String name) {
					return profiles.getItems().stream().filter(f -> f.getName().equals(name)).findFirst().orElse(null);
				}
			});
			profiles.valueProperty().addListener((obs, old, fields) -> {
				if (fields != null) {
					if (!followingCrate) {
						declareProfile(fields);
					}
					entityEditor.setFormFields(fields);
				}
				profiles.setTooltip(fields instanceof ProfileFormFields p
						? new Tooltip("From " + p.getProfile().document().source())
						: null);
			});
			loadProfile.setId("loadProfile");
			loadProfile.setOnAction(e -> askForProfile(stage));
			profileStatus.setId("profileStatus");
			Button profileHelp = new Button("?");
			profileHelp.setId("profileHelp");
			profileHelp.setAccessibleText("Help with profiles");
			profileHelp.setTooltip(new Tooltip("Help with profiles"));
			profileHelp.setOnAction(e -> showHelp(HelpDialog.Topic.PROFILES));
			root.setTop(new VBox(menus(stage), new ToolBar(save, new Separator(), new Label("Profile"), profiles,
					loadProfile, profileStatus, profileHelp)));
		}));
	}

	private Node editor(SchemaFromJsonLd schema) {
		entityTypes = new EntityTypes(schema);
		terms = new CrateTerms(schema);
		listing = new EntityListing(terms);
		tree = new EntityTreeView(listing, labels);
		tree.setId("entityTree");
		schemaOrg = new SchemaOrgFormFields(terms);
		// The core RO-Crate profile by default: the same schema.org fields,
		// with those RO-Crate requires first; then the profiles the user loaded
		// before, or schema.org without a profile
		profiles.getItems().setAll(profileCache.available().stream()
				.map(p -> (FormFields) new ProfileFormFields(p, schemaOrg)).toList());
		profiles.getItems().add(schemaOrg);
		profiles.getSelectionModel().selectFirst();
		entityEditor = new EntityEditor(schema, profiles.getValue(), () -> crate.getEntities(), () -> crate.getRoot(),
				this::changed, type -> {
					// Created from a reference field, which then links to it
					Entity entity = crate.add(type);
					relist();
					return entity;
				}, this::rename);
		entityEditor.setBrowse(address -> getHostServices().showDocument(address));
		// Places: a map, and looking them up in GeoNames
		entityEditor.setMapTiles(mapTiles);
		entityEditor.setFindPlace(name -> new PlaceFinder(stage, name, () -> settings.geonamesUsername(), placeSearch,
				owner -> showPreferences(stage), mapTiles, address -> getHostServices().showDocument(address))
				.showAndWait());
		entityEditor.setFindWork(query -> new WorkFinder(stage, query, workSearch,
				address -> getHostServices().showDocument(address)).showAndWait());
		entityEditor.setFindPerson(name -> new PersonFinder(stage, name, personSearch,
				address -> getHostServices().showDocument(address)).showAndWait());
		entityEditor.setFindOrganization(name -> new OrganizationFinder(stage, name, organizationSearch,
				address -> getHostServices().showDocument(address)).showAndWait());
		entityEditor.setFindFormat(query -> new FormatFinder(stage, query, PRONOMFormats.bundled(),
				address -> getHostServices().showDocument(address)).showAndWait());
		entityEditor.setFindLicense(() -> new LicenseFinder(stage, "", SPDXLicenses.bundled(),
				address -> getHostServices().showDocument(address)).showAndWait());
		entityEditor.setTypeHelp(owner -> showHelp(HelpDialog.Topic.TYPES, owner));
		// As the style lays it out, e.g. as a notebook in the OS/2 Warp style
		entityEditor.setFormLayout(Theme.style().get().form());
		Theme.style().addListener((obs, old, style) -> entityEditor.setFormLayout(style.form()));
		// Type changes, also those confirmed with Enter in a field
		entityEditor.setOnStep(this::checkpoint);

		entities.setId("entities");
		entityType.setId("entityType");
		entityType.setMaxWidth(Double.MAX_VALUE);
		entityType.setAccessibleText("Show entities of type");
		entityType.setTooltip(new Tooltip("Show only entities of this type, or of its subtypes"));
		entityType.setConverter(new StringConverter<>() {
			@Override
			public String toString(TypeCount type) {
				return type == null ? "" : type.type().isEmpty() ? "All types (" + type.count() + ")" : type.toString();
			}

			@Override
			public TypeCount fromString(String text) {
				return null;
			}
		});
		entityType.valueProperty().addListener((obs, old, type) -> {
			if (!relisting) {
				relist(false);
			}
		});
		relist();
		entities.setCellFactory(view -> new ListCell<>() {
			// Update the text in place on changes: refreshing the whole list
			// on each key typed made it flicker
			private final InvalidationListener relabel = obs -> label(getItem());
			private final EntityIcons.Icon icon = EntityIcons.forCell(this);
			{
				labels.addListener(new WeakInvalidationListener(relabel));
			}

			@Override
			protected void updateItem(Entity entity, boolean empty) {
				super.updateItem(entity, empty);
				label(empty ? null : entity);
				getStyleClass().remove("problem");
				if (empty || entity == null) {
					setGraphic(null);
					setTooltip(null);
				} else if (problems.containsKey(entity)) {
					icon.set(EntityIcons.problem(entity, listing));
					setGraphic(icon);
					getStyleClass().add("problem");
					setTooltip(new Tooltip(problems.get(entity)));
				} else {
					icon.set(EntityIcons.of(entity, listing));
					setGraphic(icon);
					setTooltip(null);
				}
			}

			private void label(Entity entity) {
				String text = entity == null ? null
						: entity.getLabel() + "\n"
								+ String.join(", ", entity.getTypes().stream().map(listing::typeName).toList()) + "  "
								+ entity.getId();
				if (!Objects.equals(text, getText())) {
					setText(text);
				}
			}
		});
		entities.getSelectionModel().selectedItemProperty().addListener((obs, old, entity) -> {
			if (entity != null && !relisting) {
				entityEditor.edit(entity);
				entityPreview.show(entity);
			}
		});
		// A folder without an entity is selected, but the form stays
		tree.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
			if (item != null && !relisting) {
				// Not again for another place of the entity edited
				item.getValue().entity().filter(entity -> entity != entityEditor.getEntity()).ifPresent(entity -> {
					entityEditor.edit(entity);
					entityPreview.show(entity);
				});
			}
		});
		// Several to copy or remove; the last selected is edited
		entities.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		entities.getSelectionModel().getSelectedItems().addListener((ListChangeListener<Entity>) c -> select());
		tree.getSelectionModel().getSelectedItems()
				.addListener((ListChangeListener<TreeItem<EntityTree.Node>>) c -> select());
		// Copy and paste entities, as Edit > Copy and Paste do in the list
		entities.setContextMenu(entityMenu(stage));
		tree.setContextMenu(entityMenu(stage));

		Button add = new Button("Add entity");
		add.setId("addEntity");
		add.setOnAction(e -> addEntity());
		Button remove = new Button("Remove");
		remove.setId("removeEntity");
		remove.disableProperty().bind(cannotRemove);
		remove.setOnAction(e -> removeEntities());
		HBox buttons = new HBox(6, add, remove);
		buttons.setPadding(new Insets(6));
		VBox.setVgrow(entities, Priority.ALWAYS);
		VBox.setMargin(entityType, new Insets(0, 0, 4, 0));
		VBox.setVgrow(tree, Priority.ALWAYS);
		sidebar = new VBox(new Label(" Entities"), entityType, entities, buttons);

		preview.setId("preview");
		preview.setEditable(false);
		preview.setStyle("-fx-font-family: monospace;");

		// On the right, a preview of the entity (with links to the entities it
		// refers to, which open them) and the crate's JSON-LD
		entityPreview = new EntityPreview(() -> crate, this::show,
				address -> getHostServices().showDocument(address), property -> entityEditor.label(property));
		// Only an attached crate's relative @ids are files
		entityPreview.setFolder(crateFile::folder);
		// Beside the form's references and @id: show the entity, or open the address
		entityEditor.setShow(this::show);
		entityEditor.setAddress(entityPreview::address);
		Tab previewTab = new Tab("Preview", entityPreview);
		previewTab.setId("previewTab");
		Tab jsonTab = new Tab("JSON-LD", preview);
		jsonTab.setId("jsonTab");
		TabPane views = new TabPane(previewTab, jsonTab);
		views.setId("views");
		views.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		split = new SplitPane(sidebar, entityEditor, views);
		split.setDividerPositions(0.2, 0.66);
		show(crate.getRoot());
		changed();
		// The list first, as before the type selector above it
		Platform.runLater(() -> listView().requestFocus());
		return split;
	}

	/** Copy and Paste for the entities selected, as Edit > Copy and Paste */
	/**
	 * Remove the entities selected from the crate, not the root data entity,
	 * which may be among them. Only from the crate's metadata: their files,
	 * or the people they are, stay as they are, hence "Remove", not "Delete".
	 */
	private void removeEntities() {
		List<Entity> removed = selection.stream().filter(entity -> entity != crate.getRoot()).toList();
		if (removed.isEmpty()) {
			return;
		}
		removed.forEach(crate::remove);
		// The entities only they linked to the root are now mentioned
		crate.mentionUnlinked();
		relist();
		changed();
	}

	private ContextMenu entityMenu(Stage stage) {
		MenuItem copyEntity = new MenuItem("Copy");
		copyEntity.setId("copyEntity");
		copyEntity.disableProperty().bind(Bindings.isEmpty(selection));
		copyEntity.setOnAction(e -> copyEntity());
		MenuItem pasteEntity = new MenuItem("Paste");
		pasteEntity.setId("pasteEntity");
		pasteEntity.setOnAction(e -> pasteEntity(stage));
		MenuItem removeEntity = new MenuItem("Remove");
		removeEntity.setId("removeEntityMenu");
		removeEntity.disableProperty().bind(cannotRemove);
		removeEntity.setOnAction(e -> removeEntities());
		List<MenuItem> parts = partItems("context");
		ContextMenu menu = new ContextMenu(copyEntity, pasteEntity, new SeparatorMenuItem());
		menu.getItems().addAll(parts);
		menu.getItems().addAll(new SeparatorMenuItem(), removeEntity);
		menu.setOnShowing(e -> enableParts(parts));
		return menu;
	}

	/** The list or the tree, whichever is shown */
	private Control listView() {
		return byFolder ? tree : entities;
	}

	/** Keep up with the entities selected in the list or tree shown */
	private void select() {
		List<Entity> selected = byFolder ? tree.selectedEntities()
				: new ArrayList<>(entities.getSelectionModel().getSelectedItems().stream()
						.filter(Objects::nonNull).toList());
		if (!byFolder) {
			Entity last = entities.getSelectionModel().getSelectedItem();
			if (last != null && selected.remove(last)) {
				selected.add(last);
			}
		}
		if (!selected.equals(selection)) {
			selection.setAll(selected);
		}
	}

	/**
	 * View > Entities by Type or by Folder: the list, sorted and by type,
	 * or the tree of folders; the same entities selected.
	 */
	private void listByFolder(boolean folders) {
		if (folders == byFolder) {
			return;
		}
		List<Entity> shown = List.copyOf(selection);
		boolean focused = listView().isFocused();
		byFolder = folders;
		int at = sidebar.getChildren().indexOf(folders ? entityType : tree);
		sidebar.getChildren().removeAll(entityType, entities, tree);
		if (folders) {
			sidebar.getChildren().add(at, tree);
		} else {
			sidebar.getChildren().addAll(at, List.of(entityType, entities));
		}
		relist();
		Entity edited = entityEditor.getEntity();
		showListed(!shown.isEmpty() || edited == null ? shown : List.of(edited));
		if (focused) {
			listView().requestFocus();
		}
	}

	/**
	 * Add an entity, choosing its type straight away.
	 */
	private void addEntity() {
		// A Thing to start with, and straight away choose its type, which
		// replaces Thing (not needed for entities added from a property)
		Entity entity = crate.add("Thing");
		// Mentioned by the root until something else links to it, as
		// no entity may be left dangling
		crate.mentionUnlinked();
		show(entity);
		// One undo point with the type chosen next (see EntityEditor's
		// step), or for the Thing if none is
		refresh();
		Platform.runLater(() -> {
			entityEditor.chooseType();
			checkpoint();
		});
	}

	/**
	 * Look something up, e.g. a place in GeoNames, and add it to the crate,
	 * mentioned by the root until something else links to it, then show it;
	 * if the crate has it already, just show it.
	 */
	private void insert(EntityEditor.Lookup lookup) {
		entityEditor.lookUp(lookup).ifPresent(entity -> {
			crate.mentionUnlinked();
			show(entity);
			changed();
		});
	}

	/**
	 * The Insert menu: a new entity of any type, as Add entity, then those
	 * looked up, under which more lookups may come.
	 */
	private Menu insertMenu() {
		MenuItem entity = new MenuItem("_Entity…");
		entity.setId("menuInsertEntity");
		entity.setOnAction(e -> addEntity());
		Menu insert = new Menu("_Insert", null, entity, new SeparatorMenuItem(),
				insertItem("_Person from ORCID…", "menuInsertPerson", EntityEditor.Lookup.PERSON),
				insertItem("_Organization from ROR…", "menuInsertOrganization", EntityEditor.Lookup.ORGANIZATION),
				insertItem("Publication by _DOI…", "menuInsertPublication", EntityEditor.Lookup.PUBLICATION),
				insertItem("_Licence from SPDX…", "menuInsertLicense", EntityEditor.Lookup.LICENSE),
				insertItem("File _format from PRONOM…", "menuInsertFormat", EntityEditor.Lookup.FORMAT),
				insertItem("Pl_ace from GeoNames…", "menuInsertPlace", EntityEditor.Lookup.PLACE));
		insert.setId("menuInsert");
		return insert;
	}

	private MenuItem insertItem(String text, String id, EntityEditor.Lookup lookup) {
		MenuItem item = new MenuItem(text);
		item.setId(id);
		item.setOnAction(e -> insert(lookup));
		return item;
	}

	/**
	 * The File, Edit, View, Insert and Help menus.
	 */
	private MenuBar menus(Stage stage) {
		MenuItem newCrate = new MenuItem("_New RO-Crate");
		newCrate.setId("menuNew");
		newCrate.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
		newCrate.setOnAction(e -> newCrate(stage));
		MenuItem open = new MenuItem("_Open…");
		open.setId("menuOpen");
		open.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
		open.setOnAction(e -> askToOpen(stage));
		MenuItem openFolder = new MenuItem("Open _Folder…");
		openFolder.setId("menuOpenFolder");
		openFolder.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN,
				KeyCombination.SHIFT_DOWN));
		openFolder.setOnAction(e -> askToOpenFolder(stage));
		MenuItem close = new MenuItem("_Close Window");
		close.setId("menuClose");
		close.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
		// As closing the window, rather than stage.close(), which doesn't ask
		close.setOnAction(e -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));
		MenuItem save = new MenuItem("_Save");
		save.setId("menuSave");
		save.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
		save.setOnAction(e -> save(stage));
		MenuItem saveAs = new MenuItem("Save _As…");
		saveAs.setId("menuSaveAs");
		saveAs.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN,
				KeyCombination.SHIFT_DOWN));
		saveAs.setOnAction(e -> saveAs(stage));
		MenuItem load = new MenuItem("_Load Profile…");
		load.setId("menuLoadProfile");
		load.disableProperty().bind(loadProfile.disabledProperty());
		load.setOnAction(e -> askForProfile(stage));
		MenuItem exit = new MenuItem("E_xit");
		exit.setId("menuExit");
		exit.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
		// As closing the window, rather than stage.close(), which doesn't ask
		exit.setOnAction(e -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));
		Menu file = new Menu("_File", null, newCrate, open, openFolder, close, new SeparatorMenuItem(), save, saveAs,
				new SeparatorMenuItem(), load, new SeparatorMenuItem(), exit);
		file.setId("menuFile");

		MenuItem undo = new MenuItem("_Undo");
		undo.setId("menuUndo");
		undo.setAccelerator(UNDO);
		undo.disableProperty().bind(canUndo.not());
		undo.setOnAction(e -> undo());
		MenuItem redo = new MenuItem("_Redo");
		redo.setId("menuRedo");
		redo.setAccelerator(REDO);
		redo.disableProperty().bind(canRedo.not());
		redo.setOnAction(e -> redo());
		MenuItem preferences = new MenuItem("Pr_eferences…");
		preferences.setId("menuPreferences");
		preferences.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN));
		preferences.setOnAction(e -> showPreferences(stage));
		Menu edit = new Menu("_Edit", null, undo, redo, new SeparatorMenuItem());
		// What they would undo and redo, e.g. Undo Add Person, as the menu opens
		edit.setOnShowing(e -> nameUndo(stage, undo, redo));
		edit.getItems().addAll(textEditing(stage));
		edit.getItems().addAll(new SeparatorMenuItem(), preferences);
		edit.setId("menuEdit");
		Menu view = new Menu("_View", null, sidebarItem(), new SeparatorMenuItem());
		view.getItems().addAll(listings());
		view.getItems().addAll(new SeparatorMenuItem(), styles(stage), colours(stage));
		view.setId("menuView");

		MenuItem manual = new MenuItem("_wpsld Help");
		manual.setId("menuManual");
		manual.setAccelerator(CrateFile.onMac()
				? new KeyCodeCombination(KeyCode.SLASH, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)
				: new KeyCodeCombination(KeyCode.F1));
		manual.setOnAction(e -> showHelp(HelpDialog.Topic.CONTENTS));
		MenuItem about = new MenuItem("_About wpsld…");
		about.setId("menuAbout");
		about.setOnAction(e -> new AboutDialog(stage, address -> getHostServices().showDocument(address)).show());
		Menu help = new Menu("_Help", null, manual, new SeparatorMenuItem(), about);
		help.setId("menuHelp");

		MenuBar bar = new MenuBar(file, edit, view, insertMenu(), crateMenu(stage), help);
		bar.setId("menuBar");
		// In the macOS style, on macOS the menus are in the screen's menu bar
		// (set, as JavaFX doesn't allow binding it)
		bar.setUseSystemMenuBar(Theme.style().get() == Theme.Style.MACOS);
		Theme.style().addListener((obs, old, style) -> bar.setUseSystemMenuBar(style == Theme.Style.MACOS));
		return bar;
	}

	/**
	 * Crate: what makes the crate's files its parts (its <code>hasPart</code>
	 * hierarchy), for the entities or folders selected; and repairing it.
	 */
	private Menu crateMenu(Stage stage) {
		List<MenuItem> parts = partItems("menu");
		MenuItem repair = new MenuItem("Repa_ir Crate");
		repair.setId("menuRepair");
		repair.setOnAction(e -> repair(stage));
		CheckMenuItem attached = new CheckMenuItem("A_ttached to Folder");
		attached.setId("menuAttached");
		attached.setOnAction(e -> {
			crateFile.setAttached(attached.isSelected());
			relist();
			refresh();
		});
		MenuItem web = new MenuItem("Check _Web Availability…");
		web.setId("menuCheckWeb");
		web.setOnAction(e -> checkWeb(stage));
		Menu menu = new Menu("_Crate", null, parts.toArray(MenuItem[]::new));
		menu.getItems().addAll(new SeparatorMenuItem(), attached, new SeparatorMenuItem(), web, repair);
		menu.setOnShowing(e -> {
			enableParts(parts);
			attached.setSelected(crateFile.isAttached());
			attached.setDisable(!crateFile.canAttach());
		});
		menu.setId("menuCrate");
		return menu;
	}

	/**
	 * Add Dataset for Folder and Add to Has Part, for the Crate menu and the
	 * entities' right-click menu, with ids starting with this prefix
	 */
	private List<MenuItem> partItems(String prefix) {
		MenuItem addDataset = new MenuItem("Add _Dataset for Folder");
		addDataset.setId(prefix + "AddDataset");
		addDataset.setOnAction(e -> addDatasets());
		MenuItem addPart = new MenuItem("Add to Has _Part");
		addPart.setId(prefix + "AddPart");
		addPart.setOnAction(e -> addParts());
		return List.of(addDataset, addPart);
	}

	/** As the menu opens, as what they act on changes with the crate */
	private void enableParts(List<MenuItem> items) {
		items.get(0).setDisable(selectedFolders().isEmpty());
		items.get(1).setDisable(partable().isEmpty() && (!byFolder || byPart || tree.selectedFileIds().isEmpty()));
	}

	/** The folders without a Dataset selected in the tree, by their @ids */
	private List<String> selectedFolders() {
		return byFolder && !byPart ? tree.selectedFolderIds() : List.of();
	}

	/**
	 * The entities selected that could be parts but are not: files and
	 * folders by their paths (not parts of files, e.g. page.html#table2),
	 * and Files and Datasets on the web
	 */
	private List<Entity> partable() {
		Set<Entity> data = Set.copyOf(crate.dataEntities());
		return selection.stream().filter(e -> e != crate.getRoot() && !data.contains(e))
				.filter(e -> EntityTree.isPath(e.getId()) && !e.getId().contains("#") || listing.isA(e, "File")
						|| listing.isA(e, "Dataset"))
				.toList();
	}

	/**
	 * The files and folders of an attached crate's folder, as relative
	 * <code>@id</code>s (see {@link CrateFolder#list}): listed again after a
	 * few seconds, not on each change, as a folder may have many.
	 */
	private List<String> filesOnDisk() {
		Optional<Path> folder = crateFile.folder();
		if (folder.isEmpty()) {
			return List.of();
		}
		long now = System.nanoTime();
		if (!folder.equals(listedFolder) || now - listedAt > 5_000_000_000L) {
			try {
				listed = CrateFolder.list(folder.get(), MAX_FILES);
			} catch (UncheckedIOException e) {
				listed = List.of();
			}
			listedFolder = folder;
			listedAt = now;
		}
		return listed;
	}

	/**
	 * Entities with a problem, saying what it is: in an attached crate, a
	 * relative path that is not in its folder (for a part of a file, e.g.
	 * page.html#t2, the file); and the web resources found unavailable by
	 * Crate > Check Web Availability
	 */
	private Map<Entity, String> problems() {
		Map<Entity, String> found = new HashMap<>();
		crateFile.folder().ifPresent(folder -> {
			for (Entity entity : crate.getEntities()) {
				String id = entity.getId();
				String file = id.contains("#") ? id.substring(0, id.indexOf('#')) : id;
				if (EntityTree.isPath(id) && !file.isEmpty()) {
					CrateFolder.problem(folder, file).ifPresent(problem -> found.put(entity, problem));
				}
			}
		});
		unavailable.forEach((id, problem) -> crate.get(id).ifPresent(entity -> found.put(entity, problem)));
		return found;
	}

	/** The entities with a problem shown, see {@link #problems()} */
	private Map<Entity, String> problems = Map.of();
	/** Web resources found unavailable, by @id, and why */
	private Map<String, String> unavailable = Map.of();

	/** Asks web resources whether they are available */
	WebAvailability webAvailability = new WebAvailability(ProfileCache.httpClient(Duration.ofSeconds(15)),
			Duration.ofSeconds(15), USER_AGENT);

	/**
	 * Crate > Check Web Availability: ask each part of the crate on the web
	 * (see {@link WebAvailability}), in the background, and mark those not
	 * available as entities with a problem, until checked again or another
	 * crate is opened; then tell how many are.
	 *
	 * @return The check, running, if there is anything to check
	 */
	Optional<Task<List<WebAvailability.Result>>> checkWeb(Stage stage) {
		List<String> addresses = WebAvailability.addresses(crate);
		if (addresses.isEmpty()) {
			Alert none = new Alert(AlertType.INFORMATION, "The crate has no parts on the web, under External"
					+ " resources in View > Entities by Folder, to check.", ButtonType.OK);
			none.initOwner(stage);
			none.setHeaderText("Nothing to check");
			none.show();
			return Optional.empty();
		}
		Crate checked = crate;
		Task<List<WebAvailability.Result>> task = new Task<>() {
			@Override
			protected List<WebAvailability.Result> call() throws InterruptedException {
				return webAvailability.check(addresses);
			}
		};
		Alert progress = new Alert(AlertType.INFORMATION, "Asking " + addresses.size()
				+ (addresses.size() == 1 ? " web address" : " web addresses") + " whether they are available…",
				ButtonType.CANCEL);
		progress.initOwner(stage);
		progress.initModality(javafx.stage.Modality.NONE);
		progress.setHeaderText("Checking web availability");
		progress.setOnHidden(e -> task.cancel(true));
		task.setOnSucceeded(e -> {
			progress.setOnHidden(null);
			progress.close();
			if (crate != checked) {
				return;
			}
			List<WebAvailability.Result> results = task.getValue();
			Map<String, String> found = new HashMap<>();
			results.stream().filter(r -> !r.available()).forEach(r -> found.put(r.address(), r.problem().get()));
			unavailable = found;
			relist();
			Alert done = new Alert(found.isEmpty() ? AlertType.INFORMATION : AlertType.WARNING,
					results.stream().filter(r -> !r.available()).map(r -> r.address() + ": " + r.problem().get())
							.collect(Collectors.joining("\n")),
					ButtonType.OK);
			done.initOwner(stage);
			done.setHeaderText(found.isEmpty() ? "All " + results.size() + " web resources are available"
					: found.size() + " of " + results.size() + " web resources are not available");
			done.setResizable(true);
			done.show();
		});
		task.setOnFailed(e -> {
			progress.setOnHidden(null);
			progress.close();
		});
		progress.show();
		Thread thread = new Thread(task, "Web availability");
		thread.setDaemon(true);
		thread.start();
		return Optional.of(task);
	}

	/** The most files of a folder to list */
	private static final int MAX_FILES = 10_000;
	private List<String> listed = List.of();
	private Optional<Path> listedFolder = Optional.empty();
	private long listedAt;

	/**
	 * The files selected that the crate doesn't describe yet, in its folder:
	 * as Files, described by the file on disk (size, date modified) and its
	 * name (format, see {@link PRONOMFormats#addByName})
	 */
	private List<Entity> addSelectedFiles() {
		if (!byFolder || byPart) {
			return List.of();
		}
		List<Entity> added = new ArrayList<>();
		for (String id : tree.selectedFileIds()) {
			if (crate.get(id).isPresent()) {
				continue;
			}
			Entity file = crate.add("File");
			crate.rename(file, id);
			crateFile.folder().ifPresent(folder -> CrateFolder.describe(file,
					folder.resolve(java.net.URLDecoder.decode(id.replace("+", "%2B"), StandardCharsets.UTF_8))));
			PRONOMFormats.bundled().addByName(crate, file);
			added.add(file);
		}
		return added;
	}

	/** A Dataset for each folder selected, as a part of the one above it */
	private void addDatasets() {
		List<Entity> added = crate.addFolders(selectedFolders());
		if (added.isEmpty()) {
			return;
		}
		crate.unmentionLinked();
		changed();
		showListed(added);
	}

	/** The entities selected as parts of the datasets of their folders */
	private void addParts() {
		List<Entity> files = addSelectedFiles();
		List<Entity> selected = new ArrayList<>(selection);
		selected.addAll(files);
		List<Entity> parts = new ArrayList<>(partable());
		parts.addAll(files);
		if (crate.addParts(parts).isEmpty()) {
			return;
		}
		crate.unmentionLinked();
		changed();
		showListed(selected);
	}

	private void showHelp(HelpDialog.Topic topic) {
		showHelp(topic, stage);
	}

	private void showHelp(HelpDialog.Topic topic, Window owner) {
		new HelpDialog(owner, topic, address -> getHostServices().showDocument(address)).show();
	}

	private HelpDialog.Topic helpFor(Node focused) {
		for (Node node = focused; node != null; node = node.getParent()) {
			String id = node.getId();
			if (id != null && (id.equals("profile") || id.equals("loadProfile") || id.equals("profileHelp")
					|| id.equals("profileStatus"))) {
				return HelpDialog.Topic.PROFILES;
			}
			if ("save".equals(id)) {
				return HelpDialog.Topic.FILES;
			}
			if (id != null && (id.equals("addType") || id.startsWith("removeType-")
					|| id.startsWith("type-"))) {
				return HelpDialog.Topic.TYPES;
			}
			if ((id != null && id.equals("entityId")) || node == sidebar) {
				return HelpDialog.Topic.ENTITIES;
			}
			if (id != null && id.equals("views")) {
				return HelpDialog.Topic.VIEWS;
			}
			if (node == entityEditor) {
				return HelpDialog.Topic.PROPERTIES;
			}
		}
		return HelpDialog.Topic.CONTENTS;
	}

	/**
	 * Name Edit > Undo and Redo after what they would undo and redo, e.g.
	 * "Undo Add Person"; "Undo Typing" in a text field with typing to undo,
	 * which Undo undoes first.
	 */
	void nameUndo(Stage stage, MenuItem undo, MenuItem redo) {
		Optional<String> undone = stage.getScene().getFocusOwner() instanceof TextInputControl field
				&& field.isUndoable() ? Optional.of("Typing") : history.undoName(crate.toJson());
		// _ marks the Alt key; one in a name, e.g. has_version, is doubled to stay
		undo.setText(undone.map(name -> "_Undo " + name.replace("_", "__")).orElse("_Undo"));
		redo.setText(history.redoName().map(name -> "_Redo " + name.replace("_", "__")).orElse("_Redo"));
	}

	/**
	 * Edit > Cut, Copy, Paste and Select All, for the text field being edited;
	 * in the entity list, Copy and Paste copy and paste entities, and Remove
	 * (Del) removes them; disabled
	 * elsewhere. A text field handles their shortcuts itself first, so these
	 * act when chosen from the menu.
	 */
	private List<MenuItem> textEditing(Stage stage) {
		List<MenuItem> items = new ArrayList<>();
		BooleanBinding noField = Bindings.createBooleanBinding(
				() -> !(stage.getScene().getFocusOwner() instanceof TextInputControl),
				stage.getScene().focusOwnerProperty());
		BooleanBinding inEntities = Bindings.createBooleanBinding(
				() -> stage.getScene().getFocusOwner() == entities || stage.getScene().getFocusOwner() == tree,
				stage.getScene().focusOwnerProperty());
		BooleanBinding never = Bindings.createBooleanBinding(() -> false);
		record Command(String label, String id, KeyCode key, Consumer<TextInputControl> action,
				BooleanBinding forEntities, Runnable entityAction) {
		}
		for (Command command : List.of(
				new Command("Cu_t", "menuCut", KeyCode.X, TextInputControl::cut, never, () -> {
				}),
				new Command("_Copy", "menuCopy", KeyCode.C, TextInputControl::copy,
						inEntities.and(Bindings.isNotEmpty(selection)),
						this::copyEntity),
				new Command("_Paste", "menuPaste", KeyCode.V, TextInputControl::paste, inEntities,
						() -> pasteEntity(stage)),
				new Command("Select _All", "menuSelectAll", KeyCode.A, TextInputControl::selectAll, never, () -> {
				}))) {
			MenuItem item = new MenuItem(command.label());
			item.setId(command.id());
			item.setAccelerator(new KeyCodeCombination(command.key(), KeyCombination.SHORTCUT_DOWN));
			item.disableProperty().bind(noField.and(command.forEntities().not()));
			item.setOnAction(e -> {
				if (stage.getScene().getFocusOwner() instanceof TextInputControl field) {
					command.action().accept(field);
				} else if (command.forEntities().get()) {
					command.entityAction().run();
				}
			});
			items.add(item);
		}
		// Del removes the entities selected, while the list or tree has focus:
		// a text field takes the key itself first
		MenuItem remove = new MenuItem("Re_move");
		remove.setId("menuRemove");
		remove.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
		remove.disableProperty().bind(inEntities.not().or(cannotRemove));
		remove.setOnAction(e -> removeEntities());
		// After Cut, Copy and Paste
		items.add(3, remove);
		return items;
	}

	/**
	 * Copy the entities selected in the list to the clipboard, in the
	 * crate's order, as their JSON-LD (see {@link EntityClipboard}), e.g. to
	 * paste them in another crate (see {@link #pasteEntity(Stage)}), a
	 * single one as a reference, or as text elsewhere. With them the
	 * entities they refer to, not copied, to tell when pasting whether
	 * references to those are right.
	 */
	void copyEntity() {
		List<Entity> selected = crate.getEntities().stream().filter(selection::contains).toList();
		if (selected.isEmpty()) {
			return;
		}
		Set<String> copied = selected.stream().map(Entity::getId).collect(Collectors.toSet());
		List<Entity> referenced = selected.stream()
				.flatMap(e -> e.getValues().values().stream())
				.flatMap(v -> v.isArray() ? StreamSupport.stream(v.spliterator(), false) : Stream.of(v))
				.filter(v -> v.isObject() && v.path("@id").isTextual()).map(v -> v.get("@id").asText())
				.filter(id -> !copied.contains(id)).distinct().map(crate::get).flatMap(Optional::stream).toList();
		EntityClipboard.copy(selected, referenced, crate.getContext(), crateFile.base());
	}

	/**
	 * Select only this entity in the list, and edit it.
	 */
	private void show(Entity entity) {
		showListed(List.of(entity));
	}

	/**
	 * Select these entities in the list, the last one edited, listing all
	 * types if any of them are not listed; or in the tree, opening their
	 * folders.
	 */
	private void showListed(List<Entity> shown) {
		relist();
		if (byFolder) {
			tree.select(shown);
			return;
		}
		if (!entities.getItems().containsAll(shown)) {
			entityType.setValue(null);
		}
		entities.getSelectionModel().clearSelection();
		shown.stream().map(entities.getItems()::indexOf).forEach(entities.getSelectionModel()::select);
	}

	/**
	 * List the crate's entities again, root first, then by type, name and
	 * @id (see {@link EntityListing}), only those of the type chosen and its
	 * subtypes; all types if there are none of it any more, or the entity
	 * edited and listed no longer is, e.g. its type changed. Those selected
	 * stay selected, without editing them again; none if the entity edited
	 * is no longer listed, as the form still shows it, and it is selected
	 * again when listed again. The tree too, when it is shown.
	 */
	private void relist() {
		relist(true);
	}

	/**
	 * @param followEdited whether to list all types if the entity edited is
	 *                     no longer of the type chosen, not when choosing it
	 */
	private void relist(boolean followEdited) {
		List<Entity> selected = List.copyOf(entities.getSelectionModel().getSelectedItems());
		Entity edited = entityEditor.getEntity();
		relisting = true;
		try {
			List<TypeCount> types = listing.types(crate.getEntities());
			String type = Optional.ofNullable(entityType.getValue()).map(TypeCount::type).orElse("");
			Optional<TypeCount> chosen = types.stream().filter(t -> t.type().equals(type)).findFirst();
			if (chosen.isEmpty() || (followEdited && edited != null && crate.getEntities().contains(edited)
					&& entities.getItems().contains(edited) && !type.isEmpty() && !listing.isA(edited, type))) {
				chosen = Optional.empty();
			}
			List<TypeCount> items = new ArrayList<>();
			items.add(new TypeCount("", crate.getEntities().size()));
			items.addAll(types);
			if (!items.equals(entityType.getItems())) {
				entityType.getItems().setAll(items);
			}
			entityType.setValue(chosen.orElse(items.get(0)));
			List<Entity> listed = listing.list(crate, entityType.getValue().type());
			if (!listed.equals(entities.getItems())) {
				entities.getItems().setAll(listed);
				entities.getSelectionModel().clearSelection();
				// Not others than the entity edited, unless it is listed too
				if (edited != null && listed.contains(edited)) {
					selected.stream().filter(e -> e != edited).map(listed::indexOf).filter(i -> i >= 0)
							.forEach(entities.getSelectionModel()::select);
					entities.getSelectionModel().select(edited);
				}
			}
			Map<Entity, String> found = problems();
			if (!found.equals(problems)) {
				problems = found;
				entities.refresh();
			}
			tree.setProblems(problems);
			if (byFolder) {
				tree.setNodes(byPart ? EntityTree.logical(crate, listing)
						: EntityTree.physical(crate, listing, filesOnDisk()), edited);
			}
		} finally {
			relisting = false;
		}
		select();
	}

	/**
	 * Paste entities from the clipboard, as copied by {@link #copyEntity()},
	 * or their JSON-LD as text (see {@link Crate#paste(List, Map, boolean)}):
	 * local <code>@id</code>s are kept unless taken; an absolute one (or a
	 * relative path) the crate has already offers to merge them; references
	 * among those pasted follow new <code>@id</code>s. Mentioned by the root
	 * until linked otherwise, and selected. References to entities not
	 * copied that may be wrong are reported.
	 */
	void pasteEntity(Stage stage) {
		Optional<EntityClipboard.Clip> clip = EntityClipboard.entities();
		if (clip.isEmpty()) {
			Alert alert = new Alert(AlertType.WARNING,
					"Copy an entity first: the clipboard should have its JSON-LD, an object with an @id.",
					ButtonType.OK);
			alert.initOwner(stage);
			alert.setHeaderText("No entity to paste");
			alert.showAndWait();
			return;
		}
		boolean sameCrate = clip.get().crate().filter(crateFile.base()::equals).isPresent();
		Crate.Pasted pasted = crate.paste(clip.get().entities(), clip.get().referenced(), sameCrate);
		List<Entity> shown = new ArrayList<>(pasted.added());
		pasted.existing().forEach((copy, existing) -> {
			ButtonType merge = new ButtonType("Merge", ButtonData.OK_DONE);
			ButtonType keep = new ButtonType("Keep Existing", ButtonData.CANCEL_CLOSE);
			Alert alert = new Alert(AlertType.CONFIRMATION, "This crate already has " + existing.getLabel() + " ("
					+ String.join(", ", existing.getTypes()) + ") with the @id " + existing.getId()
					+ ", which differs from the one pasted.\n\nMerge the one pasted into it? Their types and values"
					+ " are combined. Keep Existing leaves it as it is.", merge, keep);
			alert.initOwner(stage);
			alert.setHeaderText("Merge entities?");
			if (alert.showAndWait().orElse(keep) == merge) {
				crate.merge(existing, copy);
				existing.setTypes(entityTypes.simplify(existing.getTypes()));
			}
			shown.add(existing);
		});
		crate.mentionUnlinked();
		showListed(shown);
		refresh();
		checkpoint(placeOf(null), Optional.of("Paste"));
		reportPasted(stage, pasted);
	}

	/**
	 * Report references of entities pasted to entities not pasted that may
	 * be wrong: missing in this crate, or another entity of the same local
	 * @id than the one copied. Beside the crate, as the reader's warnings.
	 */
	private void reportPasted(Stage stage, Crate.Pasted pasted) {
		List<String> problems = new ArrayList<>();
		if (!pasted.dangling().isEmpty()) {
			problems.add("Not in this crate: " + String.join(", ", pasted.dangling())
					+ ". Paste them too, or Crate > Repair Crate to add them as Things.");
		}
		if (!pasted.ambiguous().isEmpty()) {
			problems.add("Another entity in this crate than the one copied may have the same @id: "
					+ String.join(", ", pasted.ambiguous()) + ". Check that these references are the ones meant.");
		}
		if (problems.isEmpty()) {
			return;
		}
		int count = pasted.dangling().size() + pasted.ambiguous().size();
		Alert alert = new Alert(AlertType.WARNING, String.join("\n\n", problems), ButtonType.OK);
		alert.initOwner(stage);
		alert.setHeaderText("Pasted " + pasted.added().size() + (pasted.added().size() == 1 ? " entity" : " entities")
				+ ", with " + count + (count == 1 ? " reference" : " references") + " to an entity not copied");
		alert.setResizable(true);
		alert.initModality(javafx.stage.Modality.NONE);
		alert.show();
	}

	/**
	 * View > Entities by Type (the list) or by Folder (the tree).
	 */
	private List<MenuItem> listings() {
		ToggleGroup group = new ToggleGroup();
		RadioMenuItem byType = new RadioMenuItem("Entities by _Type");
		byType.setId("menuListByType");
		byType.setToggleGroup(group);
		byType.setSelected(true);
		RadioMenuItem folders = new RadioMenuItem("Entities by _Folder");
		folders.setId("menuListByFolder");
		folders.setToggleGroup(group);
		RadioMenuItem parts = new RadioMenuItem("Entities by _Part");
		parts.setId("menuListByPart");
		parts.setToggleGroup(group);
		group.selectedToggleProperty().addListener((obs, old, item) -> {
			if (item == null) {
				return;
			}
			boolean logical = item == parts;
			if (byFolder && item != byType && logical != byPart) {
				// From one tree to the other
				List<Entity> shown = List.copyOf(selection);
				byPart = logical;
				Entity edited = entityEditor.getEntity();
				showListed(!shown.isEmpty() || edited == null ? shown : List.of(edited));
			} else {
				byPart = logical;
				listByFolder(item != byType);
			}
		});
		return List.of(byType, folders, parts);
	}

	/**
	 * View: show or hide the sidebar of entities. As the operating system
	 * has it: on macOS an item that says what it will do (Hide Sidebar, Show
	 * Sidebar, Control-Command-S), elsewhere a checked item.
	 */
	private MenuItem sidebarItem() {
		if (CrateFile.onMac()) {
			MenuItem item = new MenuItem("Hide Side_bar");
			item.setId("menuSidebar");
			item.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN,
					KeyCombination.CONTROL_DOWN));
			item.setOnAction(e -> {
				showSidebar(!split.getItems().contains(sidebar));
				item.setText(split.getItems().contains(sidebar) ? "Hide Side_bar" : "Show Side_bar");
			});
			return item;
		}
		CheckMenuItem item = new CheckMenuItem("Side_bar");
		item.setId("menuSidebar");
		item.setSelected(true);
		item.selectedProperty().addListener((obs, old, show) -> showSidebar(show));
		return item;
	}

	void showSidebar(boolean show) {
		boolean shown = split.getItems().contains(sidebar);
		if (show && !shown) {
			split.getItems().add(0, sidebar);
			split.setDividerPositions(dividers);
		} else if (!show && shown) {
			dividers = split.getDividerPositions();
			split.getItems().remove(sidebar);
			// The form keeps its share of the space beside the previews
			split.setDividerPositions((dividers[1] - dividers[0]) / (1 - dividers[0]));
		}
	}

	/**
	 * View > Style: the controls' look, and the form as a list or (OS/2
	 * Warp) a notebook; kept in the settings.
	 */
	private Menu styles(Stage stage) {
		return choices("Style", Theme.Style.values(), Theme.style().get(), style -> {
			Theme.choose(style, Theme.colours());
			settings = settings.withStyle(style.id());
			saveSettings(stage);
		});
	}

	/**
	 * View > Colours: light, dark or as the system; kept in the settings.
	 */
	private Menu colours(Stage stage) {
		return choices("Colours", Theme.Colours.values(), Theme.colours(), colours -> {
			Theme.choose(Theme.style().get(), colours);
			settings = settings.withColours(colours.id());
			saveSettings(stage);
		});
	}

	/**
	 * A submenu of radio items, ids <code>menu&lt;Name&gt;</code> and
	 * <code>menu&lt;Name&gt;-&lt;id&gt;</code>.
	 */
	private static <C extends Theme.Choice> Menu choices(String name, C[] choices, C selected, Consumer<C> chosen) {
		ToggleGroup group = new ToggleGroup();
		// With its first letter for the Alt key
		Menu menu = new Menu("_" + name);
		menu.setId("menu" + name);
		for (C choice : choices) {
			RadioMenuItem item = new RadioMenuItem(choice.label());
			item.setId("menu" + name + "-" + choice.id());
			item.setUserData(choice);
			item.setToggleGroup(group);
			item.setSelected(choice == selected);
			menu.getItems().add(item);
		}
		group.selectedToggleProperty().addListener((obs, old, item) -> {
			if (item != null) {
				@SuppressWarnings("unchecked")
				C choice = (C) item.getUserData();
				chosen.accept(choice);
			}
		});
		return menu;
	}

	/**
	 * Start a new crate with only its root data entity, after asking whether
	 * to save the changes to this one.
	 */
	void newCrate(Stage stage) {
		if (mayClose(stage)) {
			edit(new Crate(), Optional.empty());
		}
	}

	/**
	 * Whether the crate may be closed: it has no unsaved changes, or the user
	 * saves them or chooses not to.
	 */
	private boolean mayClose(Stage stage) {
		if (!crateFile.isModified(crate.toJson())) {
			return true;
		}
		ButtonType save = new ButtonType("Save", ButtonData.YES);
		ButtonType dontSave = new ButtonType("Don't Save", ButtonData.NO);
		Alert alert = new Alert(AlertType.CONFIRMATION, "Your changes will be lost if you don't save them.", save,
				dontSave, ButtonType.CANCEL);
		alert.initOwner(stage);
		alert.setHeaderText("Do you want to save the changes to " + crateFile.name() + "?");
		ButtonType chosen = alert.showAndWait().orElse(ButtonType.CANCEL);
		return chosen == dontSave || (chosen == save && save(stage));
	}

	/**
	 * Edit another crate, from its root data entity: one opened from or
	 * saved to a file, or a new one.
	 */
	private void edit(Crate other, Optional<Path> path) {
		crate = other;
		unavailable = Map.of();
		previewAsked = false;
		crateFile.open(path, crate.toJson());
		// All types of another crate
		entityType.setValue(null);
		show(crate.getRoot());
		// The undo history is for one crate
		history.reset(crate.toJson());
		changed();
	}

	/**
	 * Choose an RO-Crate Metadata File (or a folder's) and open it, after
	 * asking whether to discard the changes to this crate.
	 */
	private void askToOpen(Stage stage) {
		if (!mayClose(stage)) {
			return;
		}
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Open RO-Crate Metadata File");
		chooser.getExtensionFilters().setAll(
				new FileChooser.ExtensionFilter("RO-Crate Metadata File", "*.json", "*.jsonld"),
				new FileChooser.ExtensionFilter("All files", "*"));
		if (lastFolder != null) {
			chooser.setInitialDirectory(lastFolder);
		}
		File file = chooser.showOpenDialog(stage);
		if (file != null) {
			open(stage, file.toPath());
		}
	}

	/**
	 * File > Open Folder: choose a folder, and open its crate, after asking
	 * whether to discard the changes to this crate (see {@link #openFolder}).
	 */
	private void askToOpenFolder(Stage stage) {
		if (!mayClose(stage)) {
			return;
		}
		DirectoryChooser chooser = new DirectoryChooser();
		chooser.setTitle("Open Folder as RO-Crate");
		if (lastFolder != null) {
			chooser.setInitialDirectory(lastFolder);
		}
		File folder = chooser.showDialog(stage);
		if (folder != null) {
			openFolder(stage, folder.toPath());
		}
	}

	/**
	 * Open a folder's crate: its <code>ro-crate-metadata.json</code> (or
	 * RO-Crate 1.0's <code>.jsonld</code>), else a new crate attached to it,
	 * saved there when first saved. Its files are not added, but listed in
	 * Entities by Folder to add.
	 *
	 * @return Whether it was opened
	 */
	boolean openFolder(Stage stage, Path folder) {
		for (String name : List.of(Crate.RO_CRATE_METADATA_JSON, CrateFile.RO_CRATE_METADATA_JSONLD)) {
			if (Files.isRegularFile(folder.resolve(name))) {
				return open(stage, folder.resolve(name));
			}
		}
		Crate created = new Crate();
		String name = Optional.ofNullable(folder.toAbsolutePath().getFileName()).map(Path::toString).orElse("");
		if (!name.isEmpty()) {
			created.getRoot().set("name", Optional.of(TextNode.valueOf(name)));
		}
		edit(created, Optional.empty());
		crateFile.openIn(folder, crate.toJson());
		listedFolder = Optional.empty();
		lastFolder = folder.toFile();
		relist();
		refresh();
		return true;
	}

	/**
	 * Open an RO-Crate Metadata File, or the one in a folder, without asking.
	 * Its problems are shown: errors instead of opening it, warnings beside
	 * it. The profile it conforms to is chosen, or loaded if not offered yet.
	 *
	 * @return Whether it was opened
	 */
	boolean open(Stage stage, Path path) {
		CrateDocument document = new CrateReader().read(path);
		if (document.crate().isEmpty() || document.hasErrors()) {
			Alert error = new Alert(AlertType.ERROR, document.problems().stream().map(Problem::toString)
					.collect(Collectors.joining("\n")), ButtonType.OK);
			error.initOwner(stage);
			error.setHeaderText("Can't open " + path);
			error.show();
			return false;
		}
		Path file = Path.of(document.source());
		edit(document.crate().get(), Optional.of(file));
		lastFolder = file.toFile().getParentFile();
		chooseProfile(document);
		List<String> notes = new ArrayList<>(document.problems().stream().map(Problem::toString).toList());
		if (crateFile.isAttached() && CrateFolder.match(crate, crateFile.folder().get(), MAX_FILES).elsewhere()) {
			// e.g. downloaded on its own
			crateFile.setAttached(false);
			relist();
			refresh();
			notes.add("None of the crate's files are in " + crateFile.folder().orElse(file.getParent())
					+ ", so it is opened as a detached crate, whose files are elsewhere."
					+ " Choose Crate > Attached to Folder if they are in its folder.");
		}
		if (!notes.isEmpty()) {
			Alert warnings = new Alert(AlertType.WARNING, String.join("\n", notes), ButtonType.OK);
			warnings.initOwner(stage);
			warnings.setHeaderText(notes.size() + (notes.size() == 1 ? " warning" : " warnings") + " reading " + file);
			warnings.setResizable(true);
			// Beside the crate, not in the way of editing it
			warnings.initModality(javafx.stage.Modality.NONE);
			warnings.show();
		}
		return true;
	}

	/**
	 * Choose the profile a crate conforms to (see {@link #followCrate}); if
	 * not offered yet, load the first that is not an RO-Crate version.
	 */
	private void chooseProfile(CrateDocument document) {
		if (!followCrate(document.conformsTo(), false)) {
			profileIds(document.conformsTo()).stream().flatMap(id -> profileLocation(id).stream()).findFirst()
					.ifPresent(this::loadProfile);
		}
	}

	/**
	 * Choose the offered profile the crate conforms to, by its root's
	 * <code>conformsTo</code> (or, as in RO-Crate 1.1, its descriptor's),
	 * without changing the crate. Without one, the core RO-Crate profile.
	 *
	 * @param descriptorConformsTo The descriptor's conformsTo, when opening
	 * @param keepUndeclared       Whether a choice not written in the crate
	 *                             (the core RO-Crate profile or schema.org)
	 *                             stays, as after undo
	 * @return Whether an offered profile was found
	 */
	private boolean followCrate(List<String> descriptorConformsTo, boolean keepUndeclared) {
		followingCrate = true;
		try {
			for (String id : profileIds(descriptorConformsTo)) {
				Optional<FormFields> offered = profiles.getItems().stream()
						.filter(f -> f instanceof ProfileFormFields p && p.getProfile().identifiers().stream()
								.anyMatch(known -> sameIdentifier(known, id)))
						.findFirst();
				if (offered.isPresent()) {
					profiles.getSelectionModel().select(offered.get());
					return true;
				}
			}
			if (!keepUndeclared || declared(profiles.getValue()).isPresent()) {
				profiles.getSelectionModel().selectFirst();
			}
			return false;
		} finally {
			followingCrate = false;
		}
	}

	/**
	 * The profiles the crate says it conforms to, other than RO-Crate versions.
	 */
	private List<String> profileIds(List<String> descriptorConformsTo) {
		return Stream.concat(crate.getRoot().getAll("conformsTo").stream().map(v -> v.path("@id").asText("")),
				descriptorConformsTo.stream())
				.filter(id -> !id.isBlank() && !id.startsWith(Crate.RO_CRATE_SPEC)).distinct().toList();
	}

	/**
	 * The profile of a choice of fields, if it is one to write in the crate's
	 * conformsTo: not the bundled core RO-Crate profile, nor schema.org,
	 * which are RO-Crate itself.
	 */
	private Optional<Profile> declared(FormFields fields) {
		if (fields instanceof ProfileFormFields p && !p.getProfile().identifiers().isEmpty()
				&& p.getProfile().identifiers().stream()
						.noneMatch(id -> profileCache.bundled(id).orElse(null) == p.getProfile())) {
			return Optional.of(p.getProfile());
		}
		return Optional.empty();
	}

	/**
	 * Record the profile the user chose in the root's conformsTo, in place of
	 * the other offered ones, so that it is saved, and undone as a change.
	 */
	private void declareProfile(FormFields fields) {
		Entity root = crate.getRoot();
		Optional<Profile> chosen = declared(fields);
		List<JsonNode> conformsTo = new ArrayList<>(root.getAll("conformsTo"));
		if (chosen.isPresent() && conformsTo.stream().anyMatch(v -> chosen.get().identifiers().stream()
				.anyMatch(id -> sameIdentifier(id, v.path("@id").asText(""))))) {
			return;
		}
		List<String> others = profiles.getItems().stream().flatMap(f -> declared(f).stream())
				.flatMap(p -> p.identifiers().stream()).toList();
		boolean removed = conformsTo.removeIf(v -> others.stream()
				.anyMatch(id -> sameIdentifier(id, v.path("@id").asText(""))));
		chosen.ifPresent(p -> conformsTo.add(JsonNodeFactory.instance.objectNode().put("@id", p.identifiers().get(0))));
		if (removed || chosen.isPresent()) {
			root.setAll("conformsTo", conformsTo);
			changed();
		}
	}

	private static boolean sameIdentifier(String a, String b) {
		return a.replaceFirst("/$", "").equals(b.replaceFirst("/$", ""));
	}

	private PersonFinder.Search personSearch = new ORCID(ProfileCache.httpClient(Duration.ofSeconds(15)),
			Duration.ofSeconds(20), USER_AGENT)::search;

	private WorkFinder.Search workSearch = new DOI(ProfileCache.httpClient(Duration.ofSeconds(15)),
			Duration.ofSeconds(20), USER_AGENT)::search;

	/**
	 * Look works up with this instead of Crossref and DataCite, e.g. in tests.
	 */
	void setWorkSearch(WorkFinder.Search workSearch) {
		this.workSearch = Objects.requireNonNull(workSearch);
	}

	private OrganizationFinder.Search organizationSearch = new ROR(ProfileCache.httpClient(Duration.ofSeconds(15)),
			Duration.ofSeconds(20), USER_AGENT)::search;

	/**
	 * Look organisations up with this instead of ROR, e.g. in tests.
	 */
	void setOrganizationSearch(OrganizationFinder.Search organizationSearch) {
		this.organizationSearch = Objects.requireNonNull(organizationSearch);
	}

	/**
	 * Look people up with this instead of ORCID, e.g. in tests.
	 */
	void setPersonSearch(PersonFinder.Search personSearch) {
		this.personSearch = Objects.requireNonNull(personSearch);
	}

	/**
	 * Look places up with this instead of GeoNames, e.g. in tests.
	 */
	void setPlaceSearch(PlaceFinder.Search placeSearch) {
		this.placeSearch = Objects.requireNonNull(placeSearch);
	}

	/**
	 * Show the preferences, and keep them on OK.
	 */
	void showPreferences(Stage stage) {
		new PreferencesDialog(stage, settings, profileCache, this::forgotten,
				owner -> showHelp(HelpDialog.Topic.PROFILES, owner),
				address -> getHostServices().showDocument(address)).showAndWait().ifPresent(changed -> {
			settings = changed;
			profileCache.setDisabledRegistries(changed.disabledRegistries());
			saveSettings(stage);
		});
	}

	private void saveSettings(Stage stage) {
		if (settingsFile.isPresent()) {
			try {
				settings.save(settingsFile.get());
			} catch (IOException e) {
				Alert alert = new Alert(AlertType.ERROR, e.toString(), ButtonType.OK);
				alert.initOwner(stage);
				alert.setHeaderText("Can't save the preferences in " + settingsFile.get());
				alert.show();
			}
		}
	}

	/**
	 * A kept profile was removed: no longer offer it.
	 */
	private void forgotten(Profile profile) {
		boolean chosen = profiles.getValue() instanceof ProfileFormFields p && p.getProfile() == profile;
		profiles.getItems().removeIf(f -> f instanceof ProfileFormFields p && p.getProfile() == profile);
		if (chosen) {
			profiles.getSelectionModel().selectFirst();
		}
	}

	/**
	 * Ask for a profile's URL or file, and load it.
	 */
	private void askForProfile(Stage stage) {
		Dialog<String> dialog = new Dialog<>();
		dialog.initOwner(stage);
		dialog.setTitle("Load profile");
		dialog.setHeaderText("Load an RO-Crate MASP profile, kept for next time");
		TextField location = new TextField();
		location.setId("profileLocation");
		location.setPromptText("https://… or a file");
		location.setPrefColumnCount(40);
		Button file = new Button("File…");
		file.setId("profileFile");
		file.setOnAction(e -> {
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Profile crate");
			chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("JSON-LD", "*.json", "*.jsonld"));
			File chosen = chooser.showOpenDialog(dialog.getOwner());
			if (chosen != null) {
				location.setText(chosen.toURI().toString());
			}
		});
		HBox.setHgrow(location, Priority.ALWAYS);
		Label help = new Label("The profile's identifier, its web page or folder, or its ro-crate-metadata.json; "
				+ "or choose one listed by the profile registries");
		help.setWrapText(true);
		ListView<ProfileRegistry.Entry> listed = registryList(location);
		Label registryProblems = new Label();
		registryProblems.setId("registryProblems");
		registryProblems.setWrapText(true);
		registryProblems.getStyleClass().add("warning-text");
		VBox content = new VBox(8, new HBox(8, location, file), help, listed, registryProblems);
		content.setPadding(new Insets(4));
		dialog.getDialogPane().setContent(content);
		dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
			if (HelpDialog.isHelpShortcut(event)) {
				event.consume();
				showHelp(HelpDialog.Topic.PROFILES, dialog.getDialogPane().getScene().getWindow());
			}
		});
		ButtonType load = new ButtonType("Load", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().setAll(load, ButtonType.CANCEL);
		dialog.getDialogPane().lookupButton(load).disableProperty().bind(location.textProperty().isEmpty());
		dialog.setResultConverter(button -> button == load ? location.getText().strip() : null);
		listed.setOnMouseClicked(e -> {
			if (e.getClickCount() == 2 && listed.getSelectionModel().getSelectedItem() != null) {
				((Button) dialog.getDialogPane().lookupButton(load)).fire();
			}
		});
		CompletableFuture.supplyAsync(profileCache::registryEntries).thenAccept(listing -> Platform.runLater(() -> {
			listed.getItems().setAll(listing.entries());
			listed.setPlaceholder(new Label("No profiles listed"));
			registryProblems.setText(listing.problems().stream().map(Problem::message).collect(Collectors.joining("\n")));
		}));
		Platform.runLater(location::requestFocus);
		dialog.showAndWait().filter(text -> !text.isEmpty()).ifPresent(text -> {
			Optional<URI> uri = profileLocation(text);
			if (uri.isEmpty()) {
				profileStatus.setText("Not a URL or file: " + text);
				return;
			}
			loadProfile(uri.get()).thenAccept(profile -> Platform.runLater(() -> {
				if (profile.hasErrors()) {
					Alert alert = new Alert(AlertType.ERROR, problems(profile), ButtonType.OK);
					alert.initOwner(stage);
					alert.setHeaderText("Can't load the profile from " + uri.get());
					alert.show();
				}
			}));
		});
	}

	/**
	 * The profiles the registries list, filled in later; choosing one puts
	 * its location in the field.
	 */
	private static ListView<ProfileRegistry.Entry> registryList(TextField location) {
		ListView<ProfileRegistry.Entry> listed = new ListView<>();
		listed.setId("registryProfiles");
		listed.setPrefHeight(220);
		listed.setPlaceholder(new Label("Looking up profile registries…"));
		listed.setCellFactory(view -> new ListCell<>() {
			@Override
			protected void updateItem(ProfileRegistry.Entry entry, boolean empty) {
				super.updateItem(entry, empty);
				setText(empty || entry == null ? null : entry.label() + " \u2014 " + entry.registry());
				setTooltip(empty || entry == null ? null : new Tooltip(entry.location().toString()));
			}
		});
		listed.getSelectionModel().selectedItemProperty().addListener((obs, old, entry) -> {
			if (entry != null) {
				location.setText(entry.location().toString());
			}
		});
		return listed;
	}

	/**
	 * A URL, or a file path as typed or pasted.
	 */
	static Optional<URI> profileLocation(String text) {
		// file:, http: and https: URLs; C:\… and /… paths
		if (text.matches("(?i)(https?|file):.*")) {
			try {
				return Optional.of(new URI(text));
			} catch (URISyntaxException e) {
				return Optional.empty();
			}
		}
		try {
			return Optional.of(Path.of(text).toAbsolutePath().toUri());
		} catch (InvalidPathException e) {
			return Optional.empty();
		}
	}

	private static String problems(Profile profile) {
		return profile.allProblems().stream().map(p -> p.severity() + ": " + p.message())
				.collect(Collectors.joining("\n"));
	}

	/**
	 * Load a profile in the background, then offer it and choose it.
	 */
	CompletableFuture<Profile> loadProfile(URI location) {
		loadProfile.setDisable(true);
		profileStatus.setText("Loading profile from " + location + "…");
		profileStatus.setTooltip(null);
		return CompletableFuture.supplyAsync(() -> profileCache.load(location)).thenApply(profile -> {
			Platform.runLater(() -> {
				loadProfile.setDisable(false);
				if (profile.hasErrors()) {
					profileStatus.setText("Can't load the profile");
					profileStatus.setTooltip(new Tooltip(problems(profile)));
					return;
				}
				// A newer copy of a profile loaded before replaces it
				profiles.getItems().removeIf(f -> f instanceof ProfileFormFields p && isReplacedBy(p.getProfile(), profile));
				FormFields fields = new ProfileFormFields(profile, schemaOrg);
				profiles.getItems().add(profiles.getItems().size() - 1, fields);
				profiles.getSelectionModel().select(fields);
				long warnings = profile.allProblems().size();
				profileStatus.setText(warnings == 0 ? "" : warnings + " warnings loading the profile");
				profileStatus.setTooltip(warnings == 0 ? null : new Tooltip(problems(profile)));
			});
			return profile;
		});
	}

	/**
	 * Whether a profile offered is replaced by one just loaded: the same
	 * profile from the same place, or with one of its identifiers, unless
	 * bundled, as those stay.
	 */
	private boolean isReplacedBy(Profile old, Profile loaded) {
		boolean bundled = old.identifiers().stream().anyMatch(id -> profileCache.bundled(id).orElse(null) == old);
		return !bundled && (old.document().source().equals(loaded.document().source())
				|| old.identifiers().stream().anyMatch(loaded.identifiers()::contains));
	}

	private static void showFailure(BorderPane root, String what, Throwable failure) {
		Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause()
				: failure;
		System.getLogger(CrateEditor.class.getName()).log(System.Logger.Level.ERROR, what, cause);
		Label label = new Label(what + ": " + cause);
		label.setId("failure");
		label.setWrapText(true);
		root.setCenter(label);
	}

	/**
	 * Change an entity's @id, updating references to it. If another entity
	 * already has that identifier, offer to merge the two.
	 */
	private void rename(Entity entity, String newId) {
		Optional<Entity> existing = crate.get(newId);
		Entity edited = entity;
		// Named, as its difference looks like removing an entity
		Optional<String> change = Optional.empty();
		if (existing.isEmpty()) {
			crate.rename(entity, newId);
		} else {
			ButtonType merge = new ButtonType("Merge", ButtonData.OK_DONE);
			Alert alert = new Alert(AlertType.CONFIRMATION, "Another entity, " + existing.get().getLabel() + " ("
					+ String.join(", ", existing.get().getTypes()) + "), already has the @id " + newId
					+ ".\n\nMerge " + entity.getLabel() + " into it? Their types and values are combined and"
					+ " references to either point to the merged entity.", merge, ButtonType.CANCEL);
			alert.initOwner(entities.getScene().getWindow());
			alert.setHeaderText("Merge entities?");
			if (alert.showAndWait().orElse(ButtonType.CANCEL) != merge) {
				// Back to the current @id
				entityEditor.edit(entity);
				return;
			}
			if (entity == crate.getRoot()) {
				// The root data entity keeps its place, taking over the other
				crate.merge(entity, existing.get());
				crate.rename(entity, newId);
			} else {
				crate.merge(existing.get(), entity);
				edited = existing.get();
			}
			// e.g. DataDownload and Thing become DataDownload
			edited.setTypes(entityTypes.simplify(edited.getTypes()));
			change = Optional.of("Merge Entities");
		}
		show(edited);
		entityEditor.edit(edited);
		refresh();
		// Also when confirmed with Enter in the @id field
		checkpoint(placeOf(null), change);
	}

	/**
	 * File > Save: to the file it was opened from or saved to, else as for
	 * Save As.
	 *
	 * @return Whether it was saved
	 */
	boolean save(Stage stage) {
		Optional<Path> path = crateFile.path();
		if (path.isEmpty()) {
			// A new crate of a folder: in it, unless another was saved there since
			Optional<Path> target = crateFile.saveTarget().filter(file -> !Files.exists(file));
			if (target.isPresent()) {
				return meetsRequirements(stage) && saveTo(stage, target.get());
			}
			return saveAs(stage);
		}
		CrateFile.Disk disk = crateFile.disk();
		if (disk != CrateFile.Disk.SAME) {
			switch (askBeforeOverwriting(stage, disk)) {
				case SAVE_ANYWAY -> {
				}
				case SAVE_AS -> {
					return saveAs(stage);
				}
				case RELOAD -> {
					reload(stage);
					return false;
				}
				case CANCEL -> {
					return false;
				}
			}
		}
		return meetsRequirements(stage) && saveTo(stage, path.get());
	}

	/**
	 * Saving over a file that another application changed, moved or deleted
	 * since it was opened or saved: Save Anyway (overwriting the other
	 * changes), Reload (discarding these), Save As… (keeping both), or
	 * Cancel, which is the default.
	 */
	private Overwrite askBeforeOverwriting(Stage stage, CrateFile.Disk disk) {
		ButtonType saveAnyway = new ButtonType("Save Anyway", ButtonData.YES);
		ButtonType reload = new ButtonType("Reload", ButtonData.NO);
		ButtonType saveAs = new ButtonType("Save As…", ButtonData.OTHER);
		Alert alert;
		if (disk == CrateFile.Disk.MISSING) {
			alert = new Alert(AlertType.WARNING, "Save Anyway saves it there again. Save As… saves it somewhere else.",
					saveAnyway, saveAs, ButtonType.CANCEL);
			alert.setHeaderText("“" + crateFile.name() + "” has been moved or deleted since you opened or saved it.");
		} else {
			alert = new Alert(AlertType.WARNING, "Save Anyway overwrites the other application's changes. Reload"
					+ " shows the file as it is now, discarding your changes. Save As… keeps both, saving yours as"
					+ " another file.", saveAnyway, reload, saveAs, ButtonType.CANCEL);
			alert.setHeaderText("“" + crateFile.name() + "” has been changed by another application since you"
					+ " opened or saved it.");
		}
		alert.initOwner(stage);
		cancelByDefault(alert);
		ButtonType chosen = alert.showAndWait().orElse(ButtonType.CANCEL);
		return chosen == saveAnyway ? Overwrite.SAVE_ANYWAY
				: chosen == reload ? Overwrite.RELOAD : chosen == saveAs ? Overwrite.SAVE_AS : Overwrite.CANCEL;
	}

	/** The safe choice as the default button, not the one that overwrites */
	private static void cancelByDefault(Alert alert) {
		for (ButtonType type : alert.getButtonTypes()) {
			if (alert.getDialogPane().lookupButton(type) instanceof Button button) {
				button.setDefaultButton(type.getButtonData() == ButtonData.CANCEL_CLOSE);
			}
		}
	}

	/**
	 * Whether another application changed the file since it was opened or
	 * saved, e.g. git checking out another version, as when coming back to
	 * the window. If so: without unsaved changes, reload it; with them, ask
	 * once whether to reload it or keep them (and choose when saving).
	 * Moved or deleted files are left to saving.
	 */
	void checkDisk() {
		if (checkingDisk || entityEditor == null || crateFile.disk() != CrateFile.Disk.CHANGED) {
			return;
		}
		checkingDisk = true;
		try {
			if (!crateFile.isModified(crate.toJson())) {
				reload(stage);
			} else if (!crateFile.keptChangesOverDisk()) {
				ButtonType reload = new ButtonType("Reload", ButtonData.NO);
				ButtonType keep = new ButtonType("Keep My Changes", ButtonData.CANCEL_CLOSE);
				Alert alert = new Alert(AlertType.WARNING, "You have unsaved changes. Reload the file to see it as it"
						+ " is now, discarding your changes, or keep them and choose when you save.", reload, keep);
				alert.initOwner(stage);
				alert.setHeaderText("“" + crateFile.name() + "” has been changed by another application.");
				cancelByDefault(alert);
				if (alert.showAndWait().orElse(keep) == reload) {
					reload(stage);
				} else {
					crateFile.keepChangesOverDisk();
				}
			}
		} finally {
			checkingDisk = false;
		}
	}

	/**
	 * Read the file again, as it is on disk now, replacing the crate as an
	 * undoable step, so that Undo brings back the crate as it was. The same
	 * entity stays selected, if it still is in the crate. A file that can't be
	 * read leaves the crate as it is, and isn't asked about again.
	 */
	private void reload(Stage stage) {
		Path path = crateFile.path().orElseThrow();
		CrateDocument document = new CrateReader().read(path);
		if (document.crate().isEmpty() || document.hasErrors()) {
			crateFile.keepChangesOverDisk();
			Alert error = new Alert(AlertType.ERROR, document.problems().stream().map(Problem::toString)
					.collect(Collectors.joining("\n")), ButtonType.OK);
			error.initOwner(stage);
			error.setHeaderText("Can't reload " + crateFile.name());
			error.show();
			return;
		}
		Optional<String> selected = Optional.ofNullable(entityEditor.getEntity()).map(Entity::getId);
		crate = document.crate().get();
		crateFile.reset(Optional.of(path), crate.toJson());
		show(selected.flatMap(crate::get).orElse(crate.getRoot()));
		refresh();
		checkpoint(placeOf(null), Optional.of("Reload"));
	}

	/**
	 * File > Save As: choose where to save the crate as an RO-Crate Metadata
	 * File, and save it there.
	 *
	 * @return Whether it was saved
	 */
	boolean saveAs(Stage stage) {
		if (!meetsRequirements(stage)) {
			return false;
		}
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Save RO-Crate Metadata File");
		chooser.setInitialFileName(crateFile.saveTarget().map(p -> p.getFileName().toString())
				.orElse(Crate.RO_CRATE_METADATA_JSON));
		crateFile.saveTarget().map(p -> p.toAbsolutePath().getParent().toFile()).filter(File::isDirectory)
				.ifPresent(chooser::setInitialDirectory);
		chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("JSON-LD", "*.json", "*.jsonld"));
		if (lastFolder != null && chooser.getInitialDirectory() == null) {
			chooser.setInitialDirectory(lastFolder);
		}
		File file = chooser.showSaveDialog(stage);
		return file != null && saveAsChecked(stage, file.toPath());
	}

	/**
	 * Save As to a file, first asking if that attaches the crate to a folder
	 * (named ro-crate-metadata.json, in a folder it is not attached to) that
	 * lacks some of its files: telling how many of its files are there and
	 * not, and how many of the folder's it doesn't describe; to save there
	 * anyway, or under another name, detached.
	 *
	 * @return Whether it was saved
	 */
	boolean saveAsChecked(Stage stage, Path file) {
		Path folder = file.toAbsolutePath().getParent();
		if (!CrateFile.isMetadataFile(file) || crateFile.folder().equals(Optional.of(folder))) {
			return saveTo(stage, file);
		}
		CrateFolder.Match match = CrateFolder.match(crate, folder, MAX_FILES);
		if (match.absent().isEmpty()) {
			return saveTo(stage, file);
		}
		int parts = match.present().size() + match.absent().size();
		StringBuilder text = new StringBuilder();
		text.append(match.present().size()).append(" of the crate's ").append(parts)
				.append(parts == 1 ? " file is" : " files are").append(" in ").append(folder).append("; ")
				.append(match.absent().size()).append(match.absent().size() == 1 ? " is" : " are").append(" not, e.g. ")
				.append(String.join(", ", match.absent().stream().limit(3).toList())).append(".\n");
		if (!match.undescribed().isEmpty()) {
			text.append("The folder has ").append(match.undescribed().size())
					.append(match.undescribed().size() == 1 ? " file" : " files")
					.append(" the crate doesn't describe, e.g. ")
					.append(String.join(", ", match.undescribed().stream().limit(3).toList())).append(".\n");
		}
		text.append("\nSaved here as ").append(file.getFileName())
				.append(", the crate is attached to this folder: its paths are the files here. To keep it apart"
						+ " from this folder's files, save it detached, under another name, such as ")
				.append(detachedName(crate)).append(".");
		ButtonType here = new ButtonType("Save Here", ButtonData.YES);
		ButtonType detached = new ButtonType("Save Detached…", ButtonData.NO);
		Alert alert = new Alert(AlertType.WARNING, text.toString(), here, detached, ButtonType.CANCEL);
		alert.initOwner(stage);
		alert.setHeaderText("Not all of the crate's files are in this folder");
		alert.setResizable(true);
		ButtonType chosen = alert.showAndWait().orElse(ButtonType.CANCEL);
		if (chosen == here) {
			return saveTo(stage, file);
		}
		if (chosen == detached) {
			FileChooser chooser = new FileChooser();
			chooser.setTitle("Save Detached RO-Crate Metadata File");
			chooser.setInitialFileName(detachedName(crate));
			chooser.setInitialDirectory(folder.toFile());
			chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("JSON-LD", "*.json", "*.jsonld"));
			File other = chooser.showSaveDialog(stage);
			return other != null && saveTo(stage, other.toPath());
		}
		return false;
	}

	/** Whether the user was asked about this crate's stale HTML preview */
	private boolean previewAsked;

	/**
	 * After saving an attached crate, its folder's ro-crate-preview.html no
	 * longer shows it as it is, and wpsld can't make another yet: ask, once
	 * for each crate opened, whether to move it aside (into
	 * ro-crate-preview_files, named by its date), delete it, or keep it.
	 */
	private void stalePreview(Stage stage, Path folder) {
		Path preview = folder.resolve("ro-crate-preview.html");
		if (previewAsked || !Files.isRegularFile(preview)) {
			return;
		}
		previewAsked = true;
		ButtonType moveAside = new ButtonType("Move Aside", ButtonData.YES);
		ButtonType delete = new ButtonType("Delete", ButtonData.OTHER);
		ButtonType keep = new ButtonType("Keep", ButtonData.NO);
		Alert alert = new Alert(AlertType.CONFIRMATION, "ro-crate-preview.html shows the crate as it was when the"
				+ " preview was made, not as saved now, and wpsld can't make a new preview yet.\n\nMove it aside into"
				+ " ro-crate-preview_files, as old-ro-crate-preview with its date, delete it, or keep it as it is?",
				moveAside, delete, keep);
		alert.initOwner(stage);
		alert.setHeaderText("The crate's HTML preview is out of date");
		alert.setResizable(true);
		ButtonType chosen = alert.showAndWait().orElse(keep);
		try {
			if (chosen == moveAside) {
				Path aside = Files.createDirectories(folder.resolve("ro-crate-preview_files"));
				String date = java.time.LocalDate
						.ofInstant(Files.getLastModifiedTime(preview).toInstant(), java.time.ZoneId.systemDefault())
						.toString();
				Path old = aside.resolve("old-ro-crate-preview-" + date + ".html");
				for (int n = 2; Files.exists(old); n++) {
					old = aside.resolve("old-ro-crate-preview-" + date + "-" + n + ".html");
				}
				Files.move(preview, old);
			} else if (chosen == delete) {
				Files.delete(preview);
			}
		} catch (IOException e) {
			Alert error = new Alert(AlertType.ERROR, "Can't move or delete " + preview + ":\n" + e.getMessage());
			error.initOwner(stage);
			error.showAndWait();
		}
	}

	/**
	 * An RO-Crate 1.0 metadata file, ro-crate-metadata.jsonld, that saving
	 * a crate of a later version in this folder would leave beside it, or
	 * save as: not a symbolic link (e.g. to ro-crate-metadata.json, as on
	 * GitHub Pages), which is fine.
	 */
	private Optional<Path> legacyFile(Path file) {
		if (!CrateFile.isMetadataFile(file) || crate.specVersion().filter("1.0"::equals).isPresent()) {
			return Optional.empty();
		}
		Path legacy = file.toAbsolutePath().getParent().resolve(CrateFile.RO_CRATE_METADATA_JSONLD);
		return Files.isRegularFile(legacy, java.nio.file.LinkOption.NOFOLLOW_LINKS) ? Optional.of(legacy)
				: Optional.empty();
	}

	/**
	 * Ask whether to save as ro-crate-metadata.json, removing RO-Crate 1.0's
	 * ro-crate-metadata.jsonld, so that other tools don't read the old one
	 *
	 * @return Where to save: ro-crate-metadata.json, or the file as chosen to
	 *         keep it; none to cancel
	 */
	private Optional<Path> askAboutLegacy(Stage stage, Path file, Path legacy) {
		Path json = legacy.getParent().resolve(Crate.RO_CRATE_METADATA_JSON);
		boolean asLegacy = file.toAbsolutePath().equals(legacy);
		String version = crate.specVersion().map(v -> "RO-Crate " + v).orElse("a later RO-Crate");
		ButtonType useJson = new ButtonType(asLegacy ? "Save as .json" : "Remove .jsonld", ButtonData.YES);
		ButtonType keep = new ButtonType(asLegacy ? "Keep .jsonld" : "Keep Both", ButtonData.NO);
		Alert alert = new Alert(AlertType.CONFIRMATION, CrateFile.RO_CRATE_METADATA_JSONLD
				+ " is RO-Crate 1.0's name for the metadata file. This crate is " + version + ", whose metadata file is "
				+ Crate.RO_CRATE_METADATA_JSON + "; " + (asLegacy ? "saved as " + Crate.RO_CRATE_METADATA_JSON
						+ ", the old file is removed." : "with both in the folder, other tools may read the old one.")
				+ "\n\nA symbolic link " + CrateFile.RO_CRATE_METADATA_JSONLD + " to " + Crate.RO_CRATE_METADATA_JSON
				+ ", as some web sites need, can stay.", useJson, keep, ButtonType.CANCEL);
		alert.initOwner(stage);
		alert.setHeaderText(asLegacy ? "Save as " + Crate.RO_CRATE_METADATA_JSON + "?"
				: "Remove the old " + CrateFile.RO_CRATE_METADATA_JSONLD + "?");
		alert.setResizable(true);
		ButtonType chosen = alert.showAndWait().orElse(ButtonType.CANCEL);
		if (chosen == useJson) {
			return Optional.of(json);
		}
		return chosen == keep ? Optional.of(file) : Optional.empty();
	}

	/**
	 * A name for a detached crate's metadata file, as RO-Crate 1.2 suggests:
	 * <code>bird-survey-ro-crate-metadata.json</code> for a crate named
	 * "Bird survey".
	 */
	static String detachedName(Crate crate) {
		String prefix = crate.getRoot().get("name").filter(JsonNode::isTextual).map(JsonNode::asText).orElse("")
				.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-+|-+$", "");
		if (prefix.length() > 40) {
			prefix = prefix.substring(0, 40).replaceAll("-+$", "");
		}
		return (prefix.isEmpty() ? "detached" : prefix) + "-" + Crate.RO_CRATE_METADATA_JSON;
	}

	/**
	 * Whether the crate meets the RO-Crate requirements, or the user chooses
	 * to save it anyway; first the entities left dangling are mentioned.
	 */
	private boolean meetsRequirements(Stage stage) {
		mentionUnlinked();
		// The profile's required properties, then RO-Crate's own checks
		// (worded alike, so each problem only once)
		List<String> problems = Stream.concat(Stream.concat(RequiredFields.check(crate, profiles.getValue(), terms)
				.stream(), CrateChecks.check(crate).stream()),
				// Those of an attached crate's files and folder
				crateFile.folder().map(folder -> CrateFolder.problems(crate, folder)).orElse(List.of()).stream())
				.distinct().toList();
		if (problems.isEmpty()) {
			return true;
		}
		ButtonType saveAnyway = new ButtonType("Save Anyway", ButtonData.OK_DONE);
		Alert alert = new Alert(AlertType.WARNING, "• " + String.join("\n• ", problems), saveAnyway,
				ButtonType.CANCEL);
		alert.initOwner(stage);
		alert.setHeaderText("The crate does not yet meet the RO-Crate requirements");
		return alert.showAndWait().orElse(ButtonType.CANCEL) == saveAnyway;
	}

	/**
	 * Crate > Repair Crate: add a Thing for each reference to an entity not in
	 * the crate, to be named and typed (see {@link Crate#addReferenced()});
	 * no longer mention the entities linked otherwise from the root data
	 * entity (see {@link Crate#unmentionLinked()}), and mention those not
	 * linked (see {@link Crate#mentionUnlinked()}). First, SPDX licences
	 * written otherwise (e.g. <code>https://spdx.org/licenses/MIT.html</code>,
	 * or the text <code>CC-BY-4.0</code>) become the licence's SPDX IRI, and
	 * are described from the SPDX License List (see
	 * {@link SPDXLicenses#repair}), and PRONOM file formats likewise, also
	 * for a media type that only one format has (see
	 * {@link PRONOMFormats#repair}), and ORCID iDs, ROR identifiers and DOIs
	 * written otherwise (see {@link ORCID#repair}, {@link ROR#repair} and
	 * {@link DOI#repair}). One undoable step, then tells what was
	 * done.
	 */
	void repair(Stage stage) {
		List<String> licences = SPDXLicenses.bundled().repair(crate);
		List<String> formats = PRONOMFormats.bundled().repair(crate);
		List<String> people = ORCID.repair(crate);
		List<String> organizations = ROR.repair(crate);
		List<String> dois = DOI.repair(crate);
		List<Entity> added = crate.addReferenced();
		List<Entity> unmentioned = crate.unmentionLinked();
		List<Entity> mentioned = crate.mentionUnlinked();
		List<String> done = new ArrayList<>();
		if (!licences.isEmpty()) {
			done.add("Licences of the SPDX License List:\n" + licences.stream().limit(LISTED).map(l -> "• " + l)
					.collect(Collectors.joining("\n"))
					+ (licences.size() > LISTED ? "\n• and " + (licences.size() - LISTED) + " more" : ""));
		}
		if (!dois.isEmpty()) {
			done.add("DOIs:\n" + dois.stream().limit(LISTED).map(f -> "• " + f)
					.collect(Collectors.joining("\n"))
					+ (dois.size() > LISTED ? "\n• and " + (dois.size() - LISTED) + " more" : ""));
		}
		if (!people.isEmpty()) {
			done.add("ORCID iDs:\n" + people.stream().limit(LISTED).map(f -> "• " + f)
					.collect(Collectors.joining("\n"))
					+ (people.size() > LISTED ? "\n• and " + (people.size() - LISTED) + " more" : ""));
		}
		if (!organizations.isEmpty()) {
			done.add("ROR identifiers:\n" + organizations.stream().limit(LISTED).map(f -> "• " + f)
					.collect(Collectors.joining("\n"))
					+ (organizations.size() > LISTED ? "\n• and " + (organizations.size() - LISTED) + " more" : ""));
		}
		if (!formats.isEmpty()) {
			done.add("File formats of PRONOM:\n" + formats.stream().limit(LISTED).map(f -> "• " + f)
					.collect(Collectors.joining("\n"))
					+ (formats.size() > LISTED ? "\n• and " + (formats.size() - LISTED) + " more" : ""));
		}
		if (!added.isEmpty()) {
			done.add("Added as Thing, referred to but not in the crate:\n" + list(added)
					+ "\nGive them a name and type, or if an @id is mistyped, change it to the one meant"
					+ " to merge them.");
		}
		if (!unmentioned.isEmpty()) {
			done.add("No longer mentioned by the root, as linked otherwise:\n" + list(unmentioned));
		}
		if (!mentioned.isEmpty()) {
			done.add("Mentioned by the root, as not linked otherwise:\n" + list(mentioned));
		}
		if (!done.isEmpty()) {
			// Entities may have been added, renamed or merged
			relist();
			Entity edited = entityEditor.getEntity();
			if (edited != null) {
				entityEditor.edit(crate.getEntities().contains(edited) ? edited : crate.getRoot());
			}
			refresh();
			checkpoint(placeOf(null), Optional.of("Repair"));
		}
		Alert report = new Alert(AlertType.INFORMATION,
				done.isEmpty() ? "Nothing to repair." : String.join("\n\n", done), ButtonType.OK);
		report.initOwner(stage);
		report.setHeaderText("Repair Crate");
		report.showAndWait();
	}

	/**
	 * The entities, one per line, the first {@value #LISTED} of them.
	 */
	private static String list(List<Entity> listed) {
		String list = listed.stream().limit(LISTED)
				.map(e -> "• " + (e.getLabel().equals(e.getId()) ? e.getId() : e.getLabel() + " (" + e.getId() + ")"))
				.collect(Collectors.joining("\n"));
		return listed.size() > LISTED ? list + "\n… and " + (listed.size() - LISTED) + " more" : list;
	}

	/** How many entities {@link #repair} lists of each change */
	private static final int LISTED = 10;

	/**
	 * Add the entities not linked from the root data entity to its
	 * <code>mentions</code> (see {@link Crate#mentionUnlinked()}), e.g. after
	 * removing the only reference to one, as an undoable step.
	 */
	private void mentionUnlinked() {
		if (crate.mentionUnlinked().isEmpty()) {
			return;
		}
		if (entityEditor.getEntity() == crate.getRoot()) {
			entityEditor.edit(crate.getRoot());
		}
		refresh();
		checkpoint();
	}

	/**
	 * Write the crate to this file, which it is then saved to.
	 *
	 * @return Whether it was written
	 */
	boolean saveTo(Stage stage, Path file) {
		Optional<Path> legacy = legacyFile(file);
		if (legacy.isPresent()) {
			Optional<Path> chosen = askAboutLegacy(stage, file, legacy.get());
			if (chosen.isEmpty()) {
				return false;
			}
			if (!chosen.get().equals(file)) {
				// Saved as ro-crate-metadata.json, then the old one is removed
				file = chosen.get();
			} else {
				legacy = Optional.empty();
			}
		}
		try {
			JsonNode json = crate.toJson();
			// With the line ends the file had, else \n; never half written
			SafeSave.write(file, Crate.writer(crateFile.lineEnd()).writeValueAsBytes(json));
			if (legacy.isPresent()) {
				Files.deleteIfExists(legacy.get());
			}
			crateFile.reset(Optional.of(file), json);
			lastFolder = file.toAbsolutePath().getParent().toFile();
			refresh();
			crateFile.folder().ifPresent(folder -> stalePreview(stage, folder));
			return true;
		} catch (IOException e) {
			Alert error = new Alert(AlertType.ERROR, "Can't save " + file + ":\n" + e.getMessage());
			error.initOwner(stage);
			error.showAndWait();
			return false;
		}
	}

	/**
	 * Refresh the entity list and the preview after a change, and mark an
	 * undo point unless it comes from typing in a field, which gets one when
	 * leaving the field.
	 */
	private void changed() {
		refresh();
		Node focused = entities.getScene() == null ? null : entities.getScene().getFocusOwner();
		if (!(focused instanceof TextInputControl)) {
			checkpoint();
		}
	}

	/**
	 * Mark an undo point, if the crate changed since the last one, at the
	 * entity being edited.
	 */
	private void checkpoint() {
		checkpoint(placeOf(null));
	}

	private void checkpoint(Place place) {
		checkpoint(place, Optional.empty());
	}

	/**
	 * Mark an undo point, named when its difference doesn't tell what it was
	 * (see {@link no.s11.wpsld.crate.ChangeNames}).
	 */
	private void checkpoint(Place place, Optional<String> name) {
		history.checkpoint(crate.toJson(), place, name);
		// Sorted again by the names and types changed, not while typing
		relist();
		canUndo.set(history.canUndo());
		canRedo.set(history.canRedo());
	}

	/**
	 * Where a change is being made: the entity being edited, and the
	 * property of a node of its form, e.g. the field just left.
	 */
	private Place placeOf(Node edited) {
		Optional<String> entity = Optional.ofNullable(entityEditor).map(EntityEditor::getEntity).map(Entity::getId);
		return new Place(entity, Optional.ofNullable(edited).flatMap(EntityEditor::propertyOf));
	}

	/**
	 * Back to the previous undo point, first marking one for changes since,
	 * e.g. typing in the field still being edited.
	 */
	void undo() {
		checkpoint();
		ObjectNode before = crate.toJson();
		history.undo().ifPresent(step -> restore(before, step));
	}

	/**
	 * Forward to the undo point last undone.
	 */
	void redo() {
		ObjectNode before = crate.toJson();
		history.redo().ifPresent(step -> restore(before, step));
	}

	/**
	 * Edit the crate as at an undo point, showing where the change undone or
	 * redone was made: its entity, else the one that differs, else the one
	 * shown before if still there, else the root; and its property's field if
	 * the form shows it. The profile follows the crate's conformsTo.
	 */
	private void restore(ObjectNode before, Step step) {
		Optional<String> shown = Optional.ofNullable(entityEditor.getEntity()).map(Entity::getId);
		crate = new CrateReader().read(step.crate(), crateFile.path().map(Path::toString).orElse("undo")).crate()
				.orElseThrow();
		history.settle(crate.toJson());
		followCrate(List.of(), true);
		relist();
		Optional<Entity> changed = step.place().entity().flatMap(crate::get);
		Entity show = changed.or(() -> UndoHistory.changedEntity(before, step.crate()).flatMap(crate::get))
				.or(() -> shown.flatMap(crate::get)).orElse(crate.getRoot());
		show(show);
		entityEditor.edit(show);
		if (changed.isPresent()) {
			step.place().property().ifPresent(property -> Platform.runLater(() -> entityEditor.focusProperty(property)));
		}
		refresh();
		canUndo.set(history.canUndo());
		canRedo.set(history.canRedo());
	}

	/**
	 * Refresh the entity list and the preview.
	 */
	private void refresh() {
		labels.set(labels.get() + 1);
		if (entityPreview != null) {
			entityPreview.refresh();
		}
		JsonNode json = crate.toJson();
		try {
			preview.setText(Crate.WRITER.writeValueAsString(json));
		} catch (JsonProcessingException e) {
			preview.setText(e.toString());
		}
		if (stage != null) {
			stage.setTitle(crateFile.title(json, CrateFile.onMac()));
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}
