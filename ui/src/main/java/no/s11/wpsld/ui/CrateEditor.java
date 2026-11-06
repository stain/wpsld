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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import no.s11.wpsld.Settings;
import no.s11.wpsld.crate.Crate;
import no.s11.wpsld.crate.CrateChecks;
import no.s11.wpsld.crate.CrateDocument;
import no.s11.wpsld.crate.CrateDocument.Problem;
import no.s11.wpsld.crate.CrateReader;
import no.s11.wpsld.crate.CrateTerms;
import no.s11.wpsld.crate.Entity;
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
	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	/** Incremented on changes, to update the labels of the entity list */
	private final LongProperty labels = new SimpleLongProperty();

	private final Supplier<SchemaFromJsonLd> loadSchema;
	private final Supplier<JsonLdContext> loadContext;
	private CompletableFuture<SchemaFromJsonLd> schemaLoading;
	private CompletableFuture<JsonLdContext> contextLoading;
	/** The crate being edited, replaced by File > New */
	private Crate crate = new Crate();
	/** The crate as started or last saved, to tell whether it changed since */
	private JsonNode unchanged = crate.toJson();
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
	/** Where the crate was last saved, to start the next save there */
	private File lastSaved;

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
		BorderPane root = new BorderPane(new Label("Loading schema.org " + SchemaFromJsonLd.SCHEMAORG_VERSION + "…"));
		stage.setScene(new Scene(root, 1400, 800));
		// Also if the window showed another scene before (as in tests)
		stage.sizeToScene();
		stage.setTitle("wpsld");
		// Closing the window, or File > Exit, asks before discarding changes
		stage.setOnCloseRequest(e -> {
			if (!mayDiscard(stage)) {
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
			Button save = new Button("Save RO-Crate Metadata File…");
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
					entityEditor.setFormFields(fields);
				}
				profiles.setTooltip(fields instanceof ProfileFormFields p
						? new Tooltip("From " + p.getProfile().document().source())
						: null);
			});
			loadProfile.setId("loadProfile");
			loadProfile.setOnAction(e -> askForProfile(stage));
			profileStatus.setId("profileStatus");
			root.setTop(new VBox(menus(stage), new ToolBar(save, new Separator(), new Label("Profile"), profiles,
					loadProfile, profileStatus)));
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

		Button add = new Button("Add entity");
		add.setId("addEntity");
		add.setOnAction(e -> {
			// A Thing to start with, and straight away choose its type, which
			// replaces Thing (not needed for entities added from a property)
			Entity entity = crate.add("Thing");
			entities.getItems().add(entity);
			entities.getSelectionModel().select(entity);
			changed();
			Platform.runLater(entityEditor::chooseType);
		});
		Button remove = new Button("Remove");
		remove.setId("removeEntity");
		remove.disableProperty().bind(entities.getSelectionModel().selectedIndexProperty().lessThanOrEqualTo(0));
		remove.setOnAction(e -> {
			Entity entity = entities.getSelectionModel().getSelectedItem();
			crate.remove(entity);
			entities.getItems().remove(entity);
			changed();
		});
		HBox buttons = new HBox(6, add, remove);
		buttons.setPadding(new Insets(6));
		VBox.setVgrow(entities, Priority.ALWAYS);
		VBox entityList = new VBox(new Label(" Entities"), entities, buttons);

		preview.setId("preview");
		preview.setEditable(false);
		preview.setStyle("-fx-font-family: monospace;");

		// On the right, a preview of the entity (with links to the entities it
		// refers to, which open them) and the crate's JSON-LD
		entityPreview = new EntityPreview(() -> crate, entities.getSelectionModel()::select,
				address -> getHostServices().showDocument(address), property -> entityEditor.label(property));
		Tab previewTab = new Tab("Preview", entityPreview);
		previewTab.setId("previewTab");
		Tab jsonTab = new Tab("JSON-LD", preview);
		jsonTab.setId("jsonTab");
		TabPane views = new TabPane(previewTab, jsonTab);
		views.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		SplitPane split = new SplitPane(entityList, entityEditor, views);
		split.setDividerPositions(0.2, 0.66);
		entities.getSelectionModel().select(crate.getRoot());
		changed();
		return split;
	}

	/**
	 * The File, Edit and View menus.
	 */
	private MenuBar menus(Stage stage) {
		MenuItem newCrate = new MenuItem("New RO-Crate");
		newCrate.setId("menuNew");
		newCrate.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
		newCrate.setOnAction(e -> newCrate(stage));
		MenuItem open = new MenuItem("Open RO-Crate Metadata File…");
		open.setId("menuOpen");
		open.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
		open.setOnAction(e -> askToOpen(stage));
		MenuItem save = new MenuItem("Save RO-Crate Metadata File…");
		save.setId("menuSave");
		save.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
		save.setOnAction(e -> save(stage));
		MenuItem load = new MenuItem("Load Profile…");
		load.setId("menuLoadProfile");
		load.disableProperty().bind(loadProfile.disabledProperty());
		load.setOnAction(e -> askForProfile(stage));
		MenuItem exit = new MenuItem("Exit");
		exit.setId("menuExit");
		exit.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
		// As closing the window, rather than stage.close(), which doesn't ask
		exit.setOnAction(e -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));
		Menu file = new Menu("File", null, newCrate, open, save, load, new SeparatorMenuItem(), exit);
		file.setId("menuFile");

		MenuItem preferences = new MenuItem("Preferences…");
		preferences.setId("menuPreferences");
		preferences.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN));
		preferences.setOnAction(e -> showPreferences(stage));
		Menu edit = new Menu("Edit", null, preferences);
		edit.setId("menuEdit");
		// The form as a list of folding groups, or as a notebook in the style
		// of the OS/2 Workplace Shell's Properties notebook
		ToggleGroup formView = new ToggleGroup();
		RadioMenuItem list = new RadioMenuItem("List");
		list.setId("menuViewList");
		list.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.SHORTCUT_DOWN));
		RadioMenuItem notebook = new RadioMenuItem("Notebook");
		notebook.setId("menuViewNotebook");
		notebook.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.SHORTCUT_DOWN));
		list.setToggleGroup(formView);
		notebook.setToggleGroup(formView);
		list.setSelected(true);
		formView.selectedToggleProperty().addListener((obs, old, selected) -> {
			if (selected != null) {
				entityEditor.setNotebookMode(selected == notebook);
			}
		});
		Menu view = new Menu("View", null, list, notebook);
		view.setId("menuView");

		MenuBar bar = new MenuBar(file, edit, view);
		bar.setId("menuBar");
		return bar;
	}

	/**
	 * Start a new crate with only its root data entity, after asking whether
	 * to discard the changes to this one.
	 */
	void newCrate(Stage stage) {
		if (mayDiscard(stage)) {
			edit(new Crate());
			stage.setTitle("wpsld");
		}
	}

	/**
	 * Whether the crate has no changes since it was started, opened or saved,
	 * or the user agrees to discard them.
	 */
	private boolean mayDiscard(Stage stage) {
		if (crate.toJson().equals(unchanged)) {
			return true;
		}
		ButtonType discard = new ButtonType("Discard", ButtonData.OK_DONE);
		Alert alert = new Alert(AlertType.CONFIRMATION, "The changes since it was last saved will be lost.",
				discard, ButtonType.CANCEL);
		alert.initOwner(stage);
		alert.setHeaderText("Discard the changes to this crate?");
		return alert.showAndWait().orElse(ButtonType.CANCEL) == discard;
	}

	/**
	 * Edit another crate, from its root data entity.
	 */
	private void edit(Crate other) {
		crate = other;
		entities.getItems().setAll(crate.getEntities());
		entities.getSelectionModel().select(crate.getRoot());
		unchanged = crate.toJson();
		changed();
	}

	/**
	 * Choose an RO-Crate Metadata File (or a folder's) and open it, after
	 * asking whether to discard the changes to this crate.
	 */
	private void askToOpen(Stage stage) {
		if (!mayDiscard(stage)) {
			return;
		}
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Open RO-Crate Metadata File");
		chooser.getExtensionFilters().setAll(
				new FileChooser.ExtensionFilter("RO-Crate Metadata File", "*.json", "*.jsonld"),
				new FileChooser.ExtensionFilter("All files", "*"));
		if (lastSaved != null) {
			chooser.setInitialDirectory(lastSaved.getParentFile());
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
		edit(document.crate().get());
		Path file = Path.of(document.source());
		lastSaved = file.toFile();
		stage.setTitle("wpsld – " + file);
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
	 * Choose the profile a crate conforms to: from its root's
	 * <code>conformsTo</code> (or, as in RO-Crate 1.1, its descriptor's), the
	 * first offered; else load the first that is not an RO-Crate version.
	 * Without any, the core RO-Crate profile.
	 */
	private void chooseProfile(CrateDocument document) {
		profiles.getSelectionModel().selectFirst();
		List<String> conformsTo = Stream.concat(
				crate.getRoot().getAll("conformsTo").stream().map(v -> v.path("@id").asText("")),
				document.conformsTo().stream())
				.filter(id -> !id.isBlank() && !id.startsWith(Crate.RO_CRATE_SPEC)).distinct().toList();
		for (String id : conformsTo) {
			Optional<FormFields> offered = profiles.getItems().stream()
					.filter(f -> f instanceof ProfileFormFields p && p.getProfile().identifiers().stream()
							.anyMatch(known -> sameIdentifier(known, id)))
					.findFirst();
			if (offered.isPresent()) {
				profiles.getSelectionModel().select(offered.get());
				return;
			}
		}
		conformsTo.stream().flatMap(id -> profileLocation(id).stream()).findFirst().ifPresent(this::loadProfile);
	}

	private static boolean sameIdentifier(String a, String b) {
		return a.replaceFirst("/$", "").equals(b.replaceFirst("/$", ""));
	}

	/**
	 * Show the preferences, and keep them on OK.
	 */
	void showPreferences(Stage stage) {
		new PreferencesDialog(stage, settings, profileCache, this::forgotten).showAndWait().ifPresent(changed -> {
			settings = changed;
			profileCache.setDisabledRegistries(changed.disabledRegistries());
			if (settingsFile.isPresent()) {
				try {
					changed.save(settingsFile.get());
				} catch (IOException e) {
					Alert alert = new Alert(AlertType.ERROR, e.toString(), ButtonType.OK);
					alert.initOwner(stage);
					alert.setHeaderText("Can't save the preferences in " + settingsFile.get());
					alert.show();
				}
			}
		});
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
		registryProblems.setStyle("-fx-text-fill: #8a5a00;");
		VBox content = new VBox(8, new HBox(8, location, file), help, listed, registryProblems);
		content.setPadding(new Insets(4));
		dialog.getDialogPane().setContent(content);
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
		if (existing.isEmpty()) {
			crate.rename(entity, newId);
		} else {
			Alert alert = new Alert(AlertType.CONFIRMATION, "Another entity, " + existing.get().getLabel() + " ("
					+ String.join(", ", existing.get().getTypes()) + "), already has the @id " + newId
					+ ".\n\nMerge " + entity.getLabel() + " into it? Their types and values are combined and"
					+ " references to either point to the merged entity.", ButtonType.OK, ButtonType.CANCEL);
			alert.initOwner(entities.getScene().getWindow());
			alert.setHeaderText("Merge entities?");
			if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
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
		}
		entities.getItems().setAll(crate.getEntities());
		entities.getSelectionModel().select(edited);
		entityEditor.edit(edited);
		changed();
	}

	/**
	 * Save the crate as an RO-Crate Metadata File, after checking the RO-Crate
	 * requirements and asking whether to save anyway if any are not met.
	 */
	private void save(Stage stage) {
		// The profile's required properties, then RO-Crate's own checks
		// (worded alike, so each problem only once)
		List<String> problems = Stream.concat(RequiredFields.check(crate, profiles.getValue(), terms).stream(),
				CrateChecks.check(crate).stream()).distinct().toList();
		if (!problems.isEmpty()) {
			ButtonType saveAnyway = new ButtonType("Save anyway", ButtonData.OK_DONE);
			Alert alert = new Alert(AlertType.WARNING,
					"• " + String.join("\n• ", problems), saveAnyway, ButtonType.CANCEL);
			alert.initOwner(stage);
			alert.setHeaderText("The crate does not yet meet the RO-Crate requirements");
			if (alert.showAndWait().orElse(ButtonType.CANCEL) != saveAnyway) {
				return;
			}
		}
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Save RO-Crate Metadata File");
		chooser.setInitialFileName(Crate.RO_CRATE_METADATA_JSON);
		chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("JSON-LD", "*.json", "*.jsonld"));
		if (lastSaved != null) {
			chooser.setInitialDirectory(lastSaved.getParentFile());
		}
		File file = chooser.showSaveDialog(stage);
		if (file == null) {
			return;
		}
		try {
			JsonNode json = crate.toJson();
			MAPPER.writeValue(file, json);
			lastSaved = file;
			unchanged = json;
			stage.setTitle("wpsld – " + file);
		} catch (IOException e) {
			Alert error = new Alert(AlertType.ERROR, "Can't save " + file + ":\n" + e.getMessage());
			error.initOwner(stage);
			error.showAndWait();
		}
	}

	/**
	 * Refresh the entity list and the preview after a change.
	 */
	private void changed() {
		labels.set(labels.get() + 1);
		if (entityPreview != null) {
			entityPreview.refresh();
		}
		try {
			preview.setText(MAPPER.writeValueAsString(crate.toJson()));
		} catch (JsonProcessingException e) {
			preview.setText(e.toString());
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}
