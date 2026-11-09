# wpsld plan

wpsld is a desktop application for describing files and folders where they
are, using schema.org-based **Linked Data** (the "LD"), and packaging that
description as an [RO-Crate](https://w3id.org/ro/crate/1.3) — a lightweight
desktop take on [Crate-O](https://github.com/Language-Research-Technology/crate-o)
and Describo, where the metadata travels with the data.

See also [MASP.md](MASP.md) for profile-aware editing with RO-Crate MASP.

## Ideas behind it

- **Metadata stored on the files.** User-defined extended attributes
  (`wpsld.*` xattrs) act as a temporary store of each file's metadata, from
  which the RO-Crate JSON-LD (`ro-crate-metadata.json`) is built
  (`WrittenMetadata`). Metadata can be added by any tool and stays with the
  file until written into a crate. Contextual entities (people,
  organisations, licences…) are not files, so they need "pretend files" or
  another store.
- **schema.org-driven forms.** The editor builds its forms from schema.org's
  own definitions (classes, properties, `domainIncludes`/`rangeIncludes`),
  pinned to schema.org 30.0 as used by RO-Crate 1.3, with `http://schema.org/`
  IRIs like the RO-Crate context. Later narrowed by MASP profiles.
- **arcp identifiers.** A Java NIO file system for the
  [arcp URI scheme](https://w3id.org/arcp) (`no.s11.wpsld.arcp`) gives files
  inside a crate, folder or zip stable identifiers.

## Architecture

| Module | Contents |
|--------|----------|
| `lib` (`no.s11.wpsld`), **free of JavaFX** | schema model (`soss`: `SchemaFromJsonLd`, `ClassDef`, `PropertyDef`), form fields (`form`: `FormFields`, `SchemaOrgFormFields`), crate model (`crate`: `Crate`, `Entity`), xattr metadata (`impl.WrittenMetadata`), arcp file system (`arcp`). Only dependency: Jackson. |
| `ui` (`no.s11.wpsld.ui`) | JavaFX 27: `CrateEditor` (main window: entities, editor, JSON-LD preview) and `EntityEditor` (type tree and form for one entity). |

Builds: Java 25, Gradle 9.8; `./gradlew build` in the conda env
(`environment.yml`), runtime images with the Temurin `Dockerfile`
(`dist/wpsld-linux-x64.zip`, cross-linked `dist/wpsld-windows-x64.zip`), and
releases from GitHub Actions (`.github/workflows/release.yml`, on a tag
`v<version>`): app images with native launchers for Linux x64 and arm64,
macOS x64 and arm64 and Windows x64, built on each platform, and a portable
zip without JavaFX (`./gradlew :ui:distZip`, see `ui/src/dist/README.txt`).

Measured for schema.org 30.0: about 430 ms cold / 110 ms warm to load, the
model about 2.6 MB once the JSON is dropped; the editor is built in about
150 ms. Most of the time to the first window is JVM and JavaFX start-up.

## Next steps

### Editor (Crate-O style)

- **JavaFX model-view binding.** Keep `lib`'s model free of JavaFX; in `ui`,
  wrap `Entity` in JavaFX properties (`StringProperty` for `@id`,
  `ObservableList` for `@type`s, `ObservableMap` for values) so controls bind
  to the model instead of copying values in and out.
- **Cache forms per type.** Build a type's form when first selected and keep
  it, re-binding it to whichever entity is edited (needs the binding above).
- **Type selector as a pop-up**, with a search box beside the tree: search
  labels and descriptions, and trim the tree down to the matches and their
  parents. Frees the space now taken by the tree. Allows selecting several
  types (see below).
- **Changing an `@id`** *(done)*: applied on Enter or leaving the field,
  updating every reference to it (`Crate.rename`); the root's `@id` can be
  changed too (e.g. to an absolute URI), with the descriptor's `about`
  following. Changing to an `@id` that already exists asks to merge the two
  entities (`Crate.merge`: types and values combined, references repointed,
  redundant supertypes dropped), and undone with Edit > Undo. Next: with the
  file system backend, possibly renaming the file itself.
- **Several `@type`s.** The type selector should allow choosing more than one
  type, making `@type` an array. A type's superclass needn't be added as well
  (e.g. not both `CreativeWork` and `Dataset`); the selector can warn about
  such redundant parents. Required combinations from RO-Crate still apply,
  and are not parent and child: files MUST include `File` (e.g.
  `["File", "SoftwareSourceCode"]`), directories `Dataset`, and a profile
  crate's root is `["Dataset", "Profile"]`.
  *Done:* types are chips with × and "Add type…" opens the pop-up
  selector (`EntityTypes`): a subclass replaces its superclasses
  (Thing → Person), adding a superclass of existing types asks to add anyway
  or replace them, unrelated types are kept side by side, and removing the
  last type falls back to Thing.
- **Types from outside the RO-Crate context** (e.g. a profile's own
  vocabulary): an entity SHOULD still have a schema.org type from the
  RO-Crate context, so warn when only non-schema.org types are selected and
  suggest a default such as `CreativeWork` or `Thing` to add alongside.
- **Warn about RO-Crate's required types**, e.g. the root data entity
  MUST be a `Dataset`; today removing `Dataset` from `./` is allowed.
- **Several values per property** (JSON arrays). *Done:* each value is a
  row with ×, "+" adds another unless the field's `maxCount` is reached
  (schema.org gives no cardinality, so 0..*; MASP profiles will give
  `sh:minCount`/`sh:maxCount`), and one value is written as a plain value,
  several as an array.
- **Linked entities from reference fields.** *Done:* "New ▾" lists the
  entity types of the field's range and creates one in the crate, linked
  from the field. Suggestions only list entities fitting the field's range
  (or subclasses), with name and type, filtered while typing. Next: open the
  new entity for editing, show the linked entity's name in the field.
- **Save** *(done)*: File > Save, and Save As… (default
  `ro-crate-metadata.json`), after `CrateChecks` of the root's required
  properties and of references to local `@id`s not in the crate, with "Save
  anyway". Proper validation will come from MASP rules in Java (no Python).
  Opening is File > Open (see Menus).
- **Opening an existing `ro-crate-metadata.json`.** Crates come from any
  tool, so loading needs many sanity checks so that unexpected input never
  breaks the UI: not JSON, or JSON-LD that is not flattened (nested objects,
  no `@graph`), `@context` arrays and extra terms (resolve against bundled
  RO-Crate 1.0–1.3 contexts), a missing or different-version metadata
  descriptor, no root data entity, duplicate or missing `@id`, missing or
  unknown `@type`s, values the form can't edit (`{"@value", "@language"}`,
  nested objects, numbers where text is expected), references to entities
  or files that aren't described, and very large crates. Load what can be
  loaded, keep unsupported values untouched (shown read-only, written back
  as they were), and report each problem rather than throwing. MASP profile
  and schema crates are RO-Crates too, so this reader is also the first step
  of MASP. *Reader done* (`CrateReader` → `CrateDocument`, on the `masp`
  branch): never throws, ERROR/WARNING problems (errors first), unknown terms
  and undefined prefixes reported once each, duplicates merged, values kept as
  written; reads our own output back unchanged. *Opening done:* File > Open
  shows the problems beside the crate, and saving keeps the crate's own
  `@context` and descriptor (see Menus). Still to do: show values the form
  can't edit read-only.
- **More RO-Crate requirements in the save checks**, e.g. scripts and
  workflows (`SoftwareSourceCode`, `ComputationalWorkflow`) MUST have a
  `name`, found by rocrate-validator; better covered by validation from the
  MASP RO-Crate profile than by hand-written checks.

- **Preview, names and descriptions** *(done on `masp`)*: a Preview tab
  beside JSON-LD, like `ro-crate-preview.html`, with links between entities
  and to web addresses. Property labels are readable names ("Date
  published"): the profile's `name` if it differs from the term, else
  `Labels.fromTerm` (abbreviations in capitals, "Number of …", explicit names
  for odd terms such as the CDC `cvd…` ones; all 1,530 schema.org properties
  checked). A [?] beside each label shows the description as plain text
  (`Labels.plainText` drops schema.org's `[[Term]]`, HTML and Markdown) and a
  link to the term's schema.org page. Name and Description sit on top as
  plain text fields for the first value, and in their group in full. The
  property search matches the term and the name, not descriptions.
- **Preview header** *(done)*: the name, the types, and the `@id` on its
  own line (several types may take the width), which opens where it leads:
  a web address in the browser, a relative path (also `./`) as the file
  or folder beside the crate, only if the crate has a folder (opened from
  or saved to one) and the file exists; `#local`, `_:` blank nodes,
  `urn:uuid:` and other absolute ids are plain text.
- **Adding any property by its IRI**: a URL, or a prefixed name the
  crate's `@context` maps to one, pasted into the property search and
  confirmed with Enter, adds it, shown under "Not properties of …". An
  `https://schema.org/` URL becomes its `http://schema.org/` term, as the
  RO-Crate context has it; only a term newer than the context needs the
  explicit `http://schema.org/…` IRI.
- **Retired schema.org terms** *(done)*: superseded and attic terms are not
  fields (pending ones are). In use, they are editable in "Not properties
  of …" with a ⚑ note naming the replacement; searching a full term or name
  (e.g. `fileFormat`) reveals one there to add. A profile naming one keeps
  it in the profile's group. Next: offer to move the value to the
  replacement (e.g. `fileFormat` → `encodingFormat`); search reveal in the
  notebook view (which has no search box).
- **Required types** *(done)*: a notice beside "Add type…" when a profile
  requires a type the entity lacks (subtypes count), with a button to add it.
- **Later:** an "External types" section in the type dialog; rows per page
  of the notebook view fitted to its height; report upstream that the core
  MASP profile's `datePublished` rule points to `schema:datePubished`.

### Look: the OS/2 Workplace Shell notebook

"WPS" is named after the OS/2 Workplace Shell, whose objects each had a
Settings (Warp 4: Properties) notebook, the Presentation Manager notebook
control (`WC_NOTEBOOK`). `Notebook` (in `ui`, styled by `notebook.css`) is a
prototype of Warp 4's tabbed style: overlapping pastel tabs with the selected
one in front (`viewOrder`), selected when pressed; scroll arrows when the tabs
don't fit; a page header with title, "Page n of m" and a dog-ear corner to
turn the pages of a tab; optional buttons along the bottom (Undo, Default,
Help); pages built when first shown; Ctrl+PageUp/PageDown and
PageUp/PageDown. The entity form is shown as a notebook in the OS/2 Warp style
(View > Style): groups as tabs, 8 properties per page; no property search
there yet. Next (see also "Platform guidelines and styles"): the scroll
wheel over the dog-ear's −/+ turning the pages; Undo/Default/Help,
mnemonics on tabs, the Warp 3 style
(tabs on the right, minor tabs), and whether the notebook becomes the default.

### Storage backends

- `Entity` and `Crate` should become interfaces over a backend: **RO-Crate
  JSON-LD** (today's classes) or **the file system**, where a file entity's
  values are its `wpsld.*` xattrs.
- A separate, simpler **entity editor for xattr-style editing** of a single
  file (e.g. opened from a file manager) may be needed.
- **xattrs on Windows**: Java maps user-defined attributes to NTFS alternate
  data streams; probe what works (sizes, names, copying, FAT/exFAT, network
  shares, OneDrive) with a small program run on Windows.
- Decide how contextual entities are stored when crates live as xattrs.
- **Evaluate the `arcp://` file system** (`no.s11.wpsld.arcp`) when mapping
  crates to the file system with Java NIO: it gives files in a crate (folder,
  zip, BagIt) stable `arcp://` identifiers. Today most of
  `ARCPFileSystemProvider` is unimplemented stubs, it has no tests, and it is
  not registered as a provider (no `provides` in `module-info`), so decide
  its role before testing or finishing it.

### Testing

- `lib`: JUnit 4 unit tests (`./gradlew :lib:test`), including that loading
  is complete (JSON dropped, no lazy value left to compute later where an
  exception could be lost), that `Value.lazy` reports a failure and retries
  and computes once across threads, that schema.org loads without QA
  warnings (`getWarnings()`, also logged), and a golden file of the JSON
  written for a crate, checked with rocrate-validator.
- `ui`: GUI tests with [TestFX](https://github.com/TestFX/TestFX) 4.0.18
  (`TestEntityEditor`, `TestCrateEditor`), run **headless** with JavaFX's own
  headless platform (JavaFX 26+; `-Dglass.platform=headless
  -Dtestfx.robot=glass -Dprism.order=sw`, see `ui/build.gradle`), so they run
  in the conda env, the container and CI without a display or Monocle. On
  Linux they need Pango (`libpangoft2`) for text layout, as on any desktop.
- `TestCrateEditorFailure`: a failure while loading the schema (or building
  the editor) is shown in the window, not lost in the background loading.
- TestFX keeps each test's application in a static map, so every scene stays
  in memory until the JVM ends (~25 MB per test): `forkEvery = 1` and a
  1.5 GB test heap in `ui/build.gradle`.
- Controls the tests use have ids: `entityId`, `propertySearch`, `addType`,
  `header-<property>`, `help-<property>`, `description-<property>`,
  `definition-<property>`, `retired-<property>`, `requiredType-<Type>`,
  `previewName`, `previewProperties`,
  `type-<Type>`, `removeType-<Type>`, `label-<property>`, `field-<property>`,
  `value-<property>-<n>`, `add-<property>`, `new-<property>`,
  `otherProperties`, `suggestType-<Type>`, `typeSearch`, `typeTree`,
  `entities`, `addEntity`, `removeEntity`, `save`, `preview`.
- The release workflow also runs `./gradlew build` on each platform's
  runner; it must pass on linux-x64, and is reported elsewhere. The tests
  should not assume `/` in paths or POSIX permissions, and `.gitattributes`
  checks text out with LF on Windows too, as tests check the SHA-256 of
  bundled files.

### User folders

`AppDirectories` (in `lib`) gives the folders for cache, data, settings and
state, and where to look for data installed for all users: XDG on Linux and
other Unix (`$XDG_CACHE_HOME`… with their defaults, invalid values ignored),
`%LOCALAPPDATA%`/`%APPDATA%`/`%PROGRAMDATA%` on Windows, and `~/Library/Caches`
and `~/Library/Application Support` on macOS, each with a `wpsld` folder.
Profiles the user loads are kept in `profiles/` of the data folder, and
preferences (`Settings`, now which profile registries to use) in
`settings.json` of the config folder; see MASP.md, "Bundled and dynamic
profiles".

### Menus

A File menu and an Edit menu above the toolbar, which keeps its buttons:

- File > New RO-Crate (Ctrl+N), and File > Open… (Ctrl+O) for an
  `ro-crate-metadata.json`. Both, and closing the window, ask whether to
  save unsaved changes: Save, Don't Save or Cancel.
- File > Save (Ctrl+S) writes to the file opened or last saved, asking
  where only for a new crate; Save As… (Shift+Ctrl+S) asks. Both first
  check the RO-Crate requirements, offering Save Anyway. File > Close
  Window (Ctrl+W).
- Changes on disk by another application, e.g. git (`CrateFile.disk()`):
  the file's modification time and size are remembered when it is opened
  or saved, with its SHA-256, which tells a real change from the same
  content written again (git checking out, touching). Checked when the
  window gets the focus back and before saving. Without unsaved changes the
  file is reloaded, as an undoable step; with them, asked once: Reload or
  Keep My Changes. Saving over a changed file asks Save Anyway, Reload,
  Save As… or Cancel (the default); a moved or deleted one, Save Anyway,
  Save As… or Cancel.
  Next (not yet done):
  - **Anyone may change the file**, not only the same person in another
    window: a background job, another user, or another machine or VM on a
    shared file system. So also check while the window has the focus, not
    only when it gets it back: a file watcher (Java's `WatchService`)
    where the file system reports changes, and polling the time and size
    every few seconds where it doesn't (network shares, VM shared folders).
  - **Show the silent reload**: a notice such as "Reloaded: changed by
    another application. Undo to go back.", as it otherwise looks as if
    nothing happened. Make an undo point just before reloading, so that
    typing still in progress in a field comes back with Undo too.
  - **Merge by replaying the unsaved edits** onto the file as it is now.
    The form edits one entity at a time, so each undo point can keep the
    entities it changed, each as its JSON-LD before and after (before null
    for an added entity, after null for a removed one); a point may change
    several, e.g. renaming an @id changes the references to it, removing
    one removes references, merging changes two. When the file changed
    with unsaved edits, read it again, then replay the points since it was
    opened or last saved, in order: for each entity changed, if the file's
    version equals the recorded before, apply the after (add, replace or
    remove); if it already equals the after, nothing to do; otherwise both
    the file and the user changed the same entity: a conflict, so stop and
    ask (mine, theirs, or Save As… to keep both), showing the differences.
    Without conflicts the merge needs no question beyond a notice, and is
    one undo step ("Undo Merge" back to the user's own version). Later,
    finer: per property within an entity, so only the same property
    changed on both sides conflicts.
- The window's title names the file (`CrateFile`): its folder and name, or
  Untitled, as the operating system has it: "name — Edited" while unsaved
  on macOS, "*name – wpsld" elsewhere.
- Opening a crate shows the reader's warnings beside it. A crate it can't
  read gives an error, and the current crate stays.
- An opened crate is saved with its own `@context` and metadata descriptor,
  so it keeps its RO-Crate version and extra terms.
- The root's `conformsTo` (or, as in 1.1, the descriptor's) chooses the
  profile. A profile that isn't offered yet is loaded. Without one, the core
  RO-Crate profile is used.
- File > Load Profile…, Exit (Ctrl+Q).
- Edit > Cut, Copy, Paste and Select All, for the text field being edited
  (disabled elsewhere; the field handles their shortcuts itself).
- Edit > Undo (Ctrl+Z) and Redo (Ctrl+Shift+Z, and Ctrl+Y but not on
  macOS, where Command-Y is not Redo), through undo points
  (`UndoHistory`):
  - A point is the crate's JSON-LD as a string, made after leaving a field,
    after adding (with the type chosen) or removing an entity or value, after
    type changes and merges, after `@id` changes and merges, and after
    choosing a profile.
  - No changes since the last point means no new point.
  - Each point remembers where its change was made: undo and redo show that
    entity again, with the cursor in the property's field.
  - In a text field, Ctrl+Z first undoes its own typing, then continues with
    the crate's points.
  - The history is per crate: File > New and Open clear it.
  - The items say what they would undo or redo (`ChangeNames`, from the
    difference between the two snapshots): Undo Add Person, Remove
    Person, Change @id, Change Type, Edit “Date published”, Choose Profile,
    Edit Person (several properties), Changes (several entities); merging
    and reloading pass their names (Merge Entities, Reload). In a text field
    with typing to undo: Undo Typing. Named as the Edit menu opens.
- Choosing a profile writes it in the root's `conformsTo`, replacing other
  offered profiles. The core RO-Crate profile and schema.org aren't written,
  as they are RO-Crate itself. Undo and redo choose the profile the crate
  then conforms to.
- Edit > Preferences… (Ctrl+,).
- Every menu and item has a letter for the Alt key (Alt+F opens File, as
  on Windows and Linux; `_` in its text), unique within its menu; Undo's
  and Redo's names have their own `_` doubled. macOS has no such letters.
- Help > About wpsld…: version (from the build, `about.properties`), the
  source's address, the Java and JavaFX versions, the licence, the
  runtime's Java and JavaFX notices (from its `legal` folder), and the
  third-party notices (`ui/src/notices/THIRD-PARTY-NOTICES.txt`, for the
  built application: the build copies it and LICENSE.txt into the resources
  and into every zip). Update the notices when a dependency changes.
- View > Sidebar shows or hides the entity list: a checked item, or on
  macOS Hide Sidebar / Show Sidebar (Control-Command-S), as Apple's
  guidelines have it.
- View > Style and View > Colours, kept in `settings.json` (`Theme`):
  - Styles: Classic (JavaFX's Modena), Windows 11 (Modena reshaped after
    Fluent, `win11.css`: Mica window, cards, 4 px corners, accent line under
    a focused input, a pill on the selected item, focus rings only for the
    keyboard, Segoe UI), macOS (`macos.css`, after macOS 14-15 before
    Liquid Glass: white push buttons with a soft shadow, the blue focus ring,
    pop-up buttons with a blue chevron box, System Settings' rounded groups,
    a grey sidebar with rounded selection, segmented tabs, the round help
    button, the system font, and on macOS the screen's menu bar), and OS/2
    Warp (`warp.css`, after Warp 4's
    Presentation Manager: #cccccc grey, square corners, black-outlined 3D
    bevels, raised buttons that sink when pressed, sunken white entry
    fields and lists, grey list selection and title-blue menu selection;
    colours from the warp-legacy KDE theme), which also shows the form as a
    notebook (in place of the earlier View > List/Notebook).
  - Colours: Light, Dark or Same as System. Each style has its dark
    stylesheet: VS Code's Dark Modern for Classic (`dark.css`), Fluent's
    dark tokens for Windows 11 (`win11-dark.css`), AppKit's dark colours for
    macOS (`macos-dark.css`), and the same bevels on dark grey for Warp
    (`warp-dark.css`).
  - Every window and dialog gets `wpsld.css`, which names the editor's own
    colours (`-wpsld-dim-text`…, used through style classes such as
    `dim-text` instead of inline colours), then the style's stylesheet, then
    its dark one. The scene's root gets `style-<name>` and `colours-dark` or
    `colours-light`, so `notebook.css` has dark tabs under `.colours-dark`.
  - Small actions (remove ×, add another +, help ?) are icon buttons
    (`EntityEditor.iconButton`): Classic and Warp show their character,
    Windows 11 only their icon from Microsoft's Fluent System Icons (Ikonli
    12.4.0, `ikonli-fluentui-pack`), as subtle buttons.
  - How the form is laid out follows the style (`FormLayout`): a list of
    folding groups (Classic, Windows 11) with × on each value, + below and
    ? showing the description below; a notebook (OS/2 Warp); or as macOS
    forms: − and + at the end of each value row (+ adds one below it, as in
    Mail's rules), ⓘ opening the description in a popover (on macOS ? is
    for help), thin lines between rows, and a rounded search field with a
    magnifier. Required fields keep their * (Apple's guidelines say nothing
    of marking them; "Required" as placeholder would hide the type hint).

Next: opening zipped crates and crates from a URL (with `CrateRetriever`),
and recent files (in the state folder).

### Platform guidelines and styles: still to do

Checked against Apple's Human Interface Guidelines (macOS 26); Windows'
and GNOME's guidelines are still to check the same way. Done so far: Save
and Save As, Save / Don't Save / Cancel, the file named in the title with
unsaved changes marked, files changed on disk, Close Window, the text
editing commands, named Undo and Redo, View > Sidebar, Merge instead of
OK, no Command-Y on macOS, and the macOS-style form.

For macOS (when running on it, whatever the style):
- **No native bindings** (such as NSMenuFX) for now: the app menu stays as
  JavaFX makes it, so About, Settings and Quit stay in Help, Edit and File;
  duplicate them where macOS expects them if that helps.
- A **Window menu** (Minimize, Zoom, the window), which macOS apps have
  even with one window.
- A **Settings window** with toolbar panes, titled after the pane shown,
  applying changes as they are made, instead of the modal Preferences.
- **Help > wpsld Help**, for ? (Command-?) to open.
- The **close button's dot** and real **sheets** are out of JavaFX's reach.

For every platform:
- **A toolbar** with more than Save, and with icons:
  - The commands people use most, in a few groups: New, Open, Save; Undo,
    Redo (with their names as tooltips, see Named undo); Add Entity,
    Remove; and the sidebar's View > Sidebar. Each also a menu command,
    as Apple's guidelines ask, with its shortcut in the tooltip.
  - Icons, as each style has them: Fluent icons for Windows 11 (Ikonli,
    already bundled), borderless symbols for macOS (at most three groups,
    no backgrounds; SF Symbols can't be bundled, so Fluent's or another
    open set drawn plainly), Warp 4-like icons for OS/2 Warp, and for
    Classic icons with text. A button's text stays as its tooltip and for
    screen readers, as for the icon buttons today.
  - A narrow window: Windows 11 moves what doesn't fit to a "…" overflow
    menu; JavaFX's ToolBar already shows an overflow arrow.
  - The **profile** chooser moved out of the way and compacted: today a
    wide labelled combo box and a Load profile… button take half the
    toolbar. Instead a compact pop-up button at the toolbar's end (or above
    the form, as it chooses the form's fields), showing a short name of the
    profile, e.g. "RO-Crate" rather than "RO-Crate Machine Actionable
    Profile 0.1.0", with the full name and source as tooltip, and Load
    Profile… as the last item of its list; and **View > Profile** with the
    same choices, as every toolbar item should also be a command.
- The system's **accent colour** (JavaFX's `Platform.Preferences`) in the
  Windows 11 and macOS styles, instead of a fixed blue; **increased
  contrast** colours.
- A **dark window title bar** where the OS allows it under dark colours.

Styles:
- **macOS family**, revised after the macOS style (macOS 14-15) and its
  form (`FormLayout.MAC`) were built:
  - First a refactoring: a style as a chain of stylesheets (e.g. macOS 26 =
    `macos.css` + `macos-glass.css`, Yosemite = `macos.css` +
    `yosemite.css`), each with its dark ones, rather than one stylesheet
    and one dark; and what a family shares as flags of the style rather
    than tests for MACOS: the screen's menu bar (today only for MACOS), and
    whether it has dark colours at all.
  - **macOS 26 (Liquid Glass)** as `macos-glass.css` on the macOS style,
    only on controls and navigation (toolbar, sidebar, menus, buttons),
    never on content: translucent fills, highlights, capsule buttons, the
    sidebar floating inset with rounded corners. Needs style classes on the
    sidebar and toolbar (and padding around the sidebar), not only CSS.
    JavaFX can't blur or refract what is behind a control, so judge from
    snapshots whether the imitation convinces.
  - **Yosemite (10.10)** shares most of macOS (− and + rows, popovers, the
    blue pop-up box, segmented tabs, the source-list sidebar), but not
    System Settings' rounded groups, which came later: forms then had
    labels aligned to the right with a colon, in plain rows. That is a
    second Mac form layout (`FormLayout.MAC_CLASSIC`), and the label's colon
    is text, so code. Yosemite had no dark windows (only a dark menu bar and
    Dock): Dark either keeps it light or borrows macOS's dark.
  - **Aqua (10.0-10.4)** as its own nostalgic style on `MAC_CLASSIC`:
    pinstripes need no image, as JavaFX gradients can repeat
    (`linear-gradient(from 0px 0px to 0px 4px, repeat, …)`); gel buttons
    and the blue scroll thumb from layered radial and linear gradients;
    Lucida Grande only exists on Macs. Other components (drawers, Aqua's
    centred tabs) only if they matter.
- **OS/2 Warp**: IBM Plex Sans Condensed (open font licence) bundled in
  place of WarpSans; Warp 4-style icons; the scroll wheel over the
  notebook's −/+ turning pages; the entity list as a Workplace Shell folder
  (see the explorer under Entities).
- **Windows 11**: Fluent icons in the tool bar and menus; Mica-like
  backgrounds where JavaFX allows.
- Check the colours of each style on the real systems, side by side.

### Entities

- **Copy and paste entities.** Edit > Duplicate within a crate is easy.
  Across open crates it is trickier, as an entity refers to others: a
  shallow copy (references left as they are, possibly dangling) or a deep
  copy (the entities it refers to too), matching those the other crate
  already has (same `@id`, or same identifier) rather than duplicating
  them.
- **An SPDX licence selector** for `license`: search the SPDX License
  List by identifier or name (e.g. "Apache", "CC-BY"), adding the licence
  as a contextual entity `{"@id": "http://spdx.org/licenses/Apache-2.0",
  "@type": "CreativeWork", "name": "Apache License 2.0"}`. Use the
  `http://spdx.org/licenses/` IRIs: they are the `@id`s in SPDX's own
  JSON-LD (license-list-data's `jsonld/`), although the web server
  redirects them to `https://`. Findings (2026-11-09):
  - They can't be resolved as Linked Data: whatever the Accept header,
    `http://spdx.org/licenses/Apache-2.0` redirects (301) to the HTML page,
    which has the licence as RDFa (`typeof="spdx:License"`, `spdx:name`,
    `spdx:licenseId`) but no `<link>` to the JSON-LD. That is at a separate
    address, `https://spdx.org/licenses/Apache-2.0.jsonld`, served as
    `binary/octet-stream`. `CrateRetriever` gets neither, so bundle.
  - Bundle `json/licenses.json` of license-list-data's release v3.29.0
    (https://github.com/spdx/license-list-data/releases/tag/v3.29.0, 740
    licences, 340 kB, SHA-256
    47d1cc681abe31166b342b6cc4aab13a6ba8ea5c48794697fe3bf8b1dbaf509a):
    identifier, name, OSI approval, deprecation and `seeAlso` for each,
    which is all a selector needs. Not `jsonld/licenses.jsonld`, which has
    every licence's full text (21 MB); a licence's JSON-LD (5-40 kB) could
    be fetched from `….jsonld` when its text is wanted. Pin it to a tag
    with a script, as `scripts/update-masp-profiles.sh` pins profiles.
  - Its licence: license-list-data's
    [accessingLicenses.md](https://github.com/spdx/license-list-data/blob/main/accessingLicenses.md)
    gives Creative Commons Attribution 3.0 (CC-BY-3.0), strictly under
    "Tech Report License", i.e. for that document; neither repository has a
    licence file (GitHub says NOASSERTION). Take the list as CC-BY-3.0,
    which only asks for attribution: credit the SPDX License List v3.29.0,
    link the licence, and say it is unchanged (or what was left out) in
    THIRD-PARTY-NOTICES.txt.
- **An explorer for entities**, like Explorer, the Finder or a Workplace
  Shell folder: a window (or view) of the crate with virtual folders for
  each kind of contextual entity on top, e.g. "Persons", "Organizations"
  and "Events", each with an icon for its kind, as the Workplace Shell had
  specialised folders; data entities by their folders (`hasPart`). Related
  to the type selector below and to browsing large crates.
- **A type selector above the entity list**, showing only entities of the
  chosen type, its subtypes included (e.g. Organization also shows
  ResearchOrganization). Only types the crate's entities have are offered.
  Tabs in the OS/2 Warp style, a drop-down in the others.

- **Opening a folder for annotation**, with or without an
  `ro-crate-metadata.json`: the whole folder opened as a crate, which
  needs a way to fill `hasPart` with its existing folders and files (and
  later to notice new ones). Related to opening zipped crates.
- **Previewing files** of data entities, e.g. images, text and tables:
  only for an attached crate (opened from or saved to a folder), whose
  relative `@id`s are files beside it.

### To debug: labels of properties outside the profile

Loading the crate of
https://www.rohub.org/3b9d1622-2ff1-4a08-8e4a-9fe002f47180?activetab=overview,
a property outside the profile, `https://w3id.org/ro-id/rohub/model#has_version`,
gets a strange label in the editor and the preview, "Https://w 3d.org…":
the whole IRI is split into words, rather than its local name after `#`
or `/` (here "Has version").

### To debug: a large workflow run crate

The crate of https://doi.org/10.5281/zenodo.18934348 (a COMPSs workflow
run) loads, but shows what to work on:
- **Markdown**: its descriptions are Markdown, shown as plain text. Detect
  it (e.g. `encodingFormat` `text/markdown`, or the text's own syntax) and
  render it in the preview.
- **Browsing its files**: with many data entities, the flat entity list is
  not enough; needs other ways to navigate, e.g. by folder (`hasPart`), by
  type (see the type selector above), or by search.
- **A hang or slowness** when following Orchestration of `#Task_1636` to
  the Run of "COMPSs runtime version 3.4": find out where the time goes
  (preview, form building, or reference lookups across many entities).

### Start-up

- Try AppCDS (`-XX:+AutoCreateSharedArchive`) or the Java 25 AOT cache in the
  launchers to cut JVM and JavaFX start-up time.
- Schema IRIs as plain strings and descriptions not kept in memory would bring
  the schema model under 2 MB, if needed.

### Profiles

- RO-Crate MASP profiles drive the forms: bundled, loaded from a URL, a
  file or a registry, and chosen by the crate's `conformsTo`. See
  [MASP.md](MASP.md) for how, and for what's next (SHACL shapes for profiles
  such as FAIRSCAPE, checking kept profiles for updates).
