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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import no.s11.wpsld.Settings;
import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateChecks;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateDocument.Problem;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
import no.s11.wpsld.crate.GeoNames;
import no.s11.wpsld.crate.UndoHistory;
import no.s11.wpsld.crate.UndoHistory.Place;
import no.s11.wpsld.crate.UndoHistory.Step;
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
	/** Names wpsld to the web services of places, as they ask */
	private static final String USER_AGENT = "wpsld/" + AboutDialog.version() + " (https://github.com/stain/wpsld)";
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
					entities.getItems().add(entity);
					return entity;
				}, this::rename);
		entityEditor.setBrowse(address -> getHostServices().showDocument(address));
		// Places: a map, and looking them up in GeoNames
		entityEditor.setMapTiles(mapTiles);
		entityEditor.setFindPlace(name -> new PlaceFinder(stage, name, () -> settings.geonamesUsername(), placeSearch,
				owner -> showPreferences(stage), mapTiles, address -> getHostServices().showDocument(address))
				.showAndWait());
		entityEditor.setTypeHelp(owner -> showHelp(HelpDialog.Topic.TYPES, owner));
		// As the style lays it out, e.g. as a notebook in the OS/2 Warp style
		entityEditor.setFormLayout(Theme.style().get().form());
		Theme.style().addListener((obs, old, style) -> entityEditor.setFormLayout(style.form()));
		// Type changes, also those confirmed with Enter in a field
		entityEditor.setOnStep(this::checkpoint);

		entities.setId("entities");
		entities.getItems().setAll(crate.getEntities());
		entities.setCellFactory(view -> new ListCell<>() {
			// Update the text in place on changes: refreshing the whole list
			// on each key typed made it flicker
			private final InvalidationListener relabel = obs -> label(getItem());
			{
				labels.addListener(new WeakInvalidationListener(relabel));
			}

			@Override
			protected void updateItem(Entity entity, boolean empty) {
				super.updateItem(entity, empty);
				label(empty ? null : entity);
			}

			private void label(Entity entity) {
				String text = entity == null ? null
						: entity.getLabel() + "\n" + String.join(", ", entity.getTypes()) + "  " + entity.getId();
				if (!Objects.equals(text, getText())) {
					setText(text);
				}
			}
		});
		entities.getSelectionModel().selectedItemProperty().addListener((obs, old, entity) -> {
			if (entity != null) {
				entityEditor.edit(entity);
				entityPreview.show(entity);
			}
		});
		// Several to copy or remove; the last selected is edited
		entities.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		// Copy and paste entities, as Edit > Copy and Paste do in the list
		MenuItem copyEntity = new MenuItem("Copy");
		copyEntity.setId("copyEntity");
		copyEntity.disableProperty().bind(entities.getSelectionModel().selectedItemProperty().isNull());
		copyEntity.setOnAction(e -> copyEntity());
		MenuItem pasteEntity = new MenuItem("Paste");
		pasteEntity.setId("pasteEntity");
		pasteEntity.setOnAction(e -> pasteEntity(stage));
		entities.setContextMenu(new ContextMenu(copyEntity, pasteEntity));

		Button add = new Button("Add entity");
		add.setId("addEntity");
		add.setOnAction(e -> {
			// A Thing to start with, and straight away choose its type, which
			// replaces Thing (not needed for entities added from a property)
			Entity entity = crate.add("Thing");
			// Mentioned by the root until something else links to it, as
			// no entity may be left dangling
			crate.mentionUnlinked();
			entities.getItems().add(entity);
			show(entity);
			// One undo point with the type chosen next (see EntityEditor's
			// step), or for the Thing if none is
			refresh();
			Platform.runLater(() -> {
				entityEditor.chooseType();
				checkpoint();
			});
		});
		Button remove = new Button("Remove");
		remove.setId("removeEntity");
		// Not the root data entity, which may be among those selected
		remove.disableProperty().bind(Bindings.createBooleanBinding(
				() -> entities.getSelectionModel().getSelectedItems().stream().allMatch(e -> e == crate.getRoot()),
				entities.getSelectionModel().getSelectedItems()));
		remove.setOnAction(e -> {
			List<Entity> removed = entities.getSelectionModel().getSelectedItems().stream()
					.filter(entity -> entity != crate.getRoot()).toList();
			removed.forEach(crate::remove);
			// The entities only they linked to the root are now mentioned
			crate.mentionUnlinked();
			entities.getItems().removeAll(removed);
			changed();
		});
		HBox buttons = new HBox(6, add, remove);
		buttons.setPadding(new Insets(6));
		VBox.setVgrow(entities, Priority.ALWAYS);
		sidebar = new VBox(new Label(" Entities"), entities, buttons);

		preview.setId("preview");
		preview.setEditable(false);
		preview.setStyle("-fx-font-family: monospace;");

		// On the right, a preview of the entity (with links to the entities it
		// refers to, which open them) and the crate's JSON-LD
		entityPreview = new EntityPreview(() -> crate, this::show,
				address -> getHostServices().showDocument(address), property -> entityEditor.label(property));
		entityPreview.setFolder(() -> crateFile.path().map(file -> file.toAbsolutePath().getParent()));
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
		return split;
	}

	/**
	 * The File, Edit, View and Help menus.
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
		Menu file = new Menu("_File", null, newCrate, open, close, new SeparatorMenuItem(), save, saveAs,
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
		MenuItem repair = new MenuItem("Repa_ir Crate");
		repair.setId("menuRepair");
		repair.setOnAction(e -> repair(stage));
		edit.getItems().addAll(new SeparatorMenuItem(), repair, new SeparatorMenuItem(), preferences);
		edit.setId("menuEdit");
		Menu view = new Menu("_View", null, sidebarItem(), new SeparatorMenuItem(), styles(stage), colours(stage));
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

		MenuBar bar = new MenuBar(file, edit, view, help);
		bar.setId("menuBar");
		// In the macOS style, on macOS the menus are in the screen's menu bar
		// (set, as JavaFX doesn't allow binding it)
		bar.setUseSystemMenuBar(Theme.style().get() == Theme.Style.MACOS);
		Theme.style().addListener((obs, old, style) -> bar.setUseSystemMenuBar(style == Theme.Style.MACOS));
		return bar;
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
	 * in the entity list, Copy and Paste copy and paste entities; disabled
	 * elsewhere. A text field handles their shortcuts itself first, so these
	 * act when chosen from the menu.
	 */
	private List<MenuItem> textEditing(Stage stage) {
		List<MenuItem> items = new ArrayList<>();
		BooleanBinding noField = Bindings.createBooleanBinding(
				() -> !(stage.getScene().getFocusOwner() instanceof TextInputControl),
				stage.getScene().focusOwnerProperty());
		BooleanBinding inEntities = Bindings.createBooleanBinding(() -> stage.getScene().getFocusOwner() == entities,
				stage.getScene().focusOwnerProperty());
		BooleanBinding never = Bindings.createBooleanBinding(() -> false);
		record Command(String label, String id, KeyCode key, Consumer<TextInputControl> action,
				BooleanBinding forEntities, Runnable entityAction) {
		}
		for (Command command : List.of(
				new Command("Cu_t", "menuCut", KeyCode.X, TextInputControl::cut, never, () -> {
				}),
				new Command("_Copy", "menuCopy", KeyCode.C, TextInputControl::copy,
						inEntities.and(entities.getSelectionModel().selectedItemProperty().isNotNull()),
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
		List<Entity> selected = crate.getEntities().stream()
				.filter(entities.getSelectionModel().getSelectedItems()::contains).toList();
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
		entities.getSelectionModel().clearAndSelect(entities.getItems().indexOf(entity));
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
		entities.getItems().setAll(crate.getEntities());
		entities.getSelectionModel().clearSelection();
		shown.stream().map(entities.getItems()::indexOf).forEach(entities.getSelectionModel()::select);
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
					+ ". Paste them too, or Edit > Repair Crate to add them as Things.");
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
		crateFile.open(path, crate.toJson());
		entities.getItems().setAll(crate.getEntities());
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
		if (!document.problems().isEmpty()) {
			Alert warnings = new Alert(AlertType.WARNING, document.problems().stream().map(Problem::toString)
					.collect(Collectors.joining("\n")), ButtonType.OK);
			warnings.initOwner(stage);
			warnings.setHeaderText(document.problems().size() + (document.problems().size() == 1 ? " warning" : " warnings")
					+ " reading " + file);
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
		entities.getItems().setAll(crate.getEntities());
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
		Optional<String> selected = Optional.ofNullable(entities.getSelectionModel().getSelectedItem())
				.map(Entity::getId);
		crate = document.crate().get();
		crateFile.reset(Optional.of(path), crate.toJson());
		entities.getItems().setAll(crate.getEntities());
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
		chooser.setInitialFileName(crateFile.path().map(p -> p.getFileName().toString())
				.orElse(Crate.RO_CRATE_METADATA_JSON));
		chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("JSON-LD", "*.json", "*.jsonld"));
		if (lastFolder != null) {
			chooser.setInitialDirectory(lastFolder);
		}
		File file = chooser.showSaveDialog(stage);
		return file != null && saveTo(stage, file.toPath());
	}

	/**
	 * Whether the crate meets the RO-Crate requirements, or the user chooses
	 * to save it anyway; first the entities left dangling are mentioned.
	 */
	private boolean meetsRequirements(Stage stage) {
		mentionUnlinked();
		// The profile's required properties, then RO-Crate's own checks
		// (worded alike, so each problem only once)
		List<String> problems = Stream.concat(RequiredFields.check(crate, profiles.getValue(), terms).stream(),
				CrateChecks.check(crate).stream()).distinct().toList();
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
	 * Edit > Repair Crate: add a Thing for each reference to an entity not in
	 * the crate, to be named and typed (see {@link Crate#addReferenced()});
	 * no longer mention the entities linked otherwise from the root data
	 * entity (see {@link Crate#unmentionLinked()}), and mention those not
	 * linked (see {@link Crate#mentionUnlinked()}). One undoable step, then
	 * tells what was done.
	 */
	void repair(Stage stage) {
		List<Entity> added = crate.addReferenced();
		List<Entity> unmentioned = crate.unmentionLinked();
		List<Entity> mentioned = crate.mentionUnlinked();
		List<String> done = new ArrayList<>();
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
			entities.getItems().addAll(added);
			if (entityEditor.getEntity() == crate.getRoot()) {
				entityEditor.edit(crate.getRoot());
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
		try {
			JsonNode json = crate.toJson();
			// With the line ends the file had, else \n
			Crate.writer(crateFile.lineEnd()).writeValue(file.toFile(), json);
			crateFile.reset(Optional.of(file), json);
			lastFolder = file.toAbsolutePath().getParent().toFile();
			refresh();
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
		Optional<String> shown = Optional.ofNullable(entities.getSelectionModel().getSelectedItem()).map(Entity::getId);
		crate = new CrateReader().read(step.crate(), crateFile.path().map(Path::toString).orElse("undo")).crate()
				.orElseThrow();
		history.settle(crate.toJson());
		followCrate(List.of(), true);
		entities.getItems().setAll(crate.getEntities());
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
