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
| `lib` (`no.s11.wpsld`), **free of JavaFX** | schema model (`soss`: `SchemaFromJsonLd`, `ClassDef`, `PropertyDef`), form fields (`form`: `FormFields`, `SchemaOrgFormFields`), crate model (`crate`: `Crate`, `Entity`, `Places`), lookups of identifiers (`lookup`: `GeoNames`, `ORCID`, `ROR`, `DOI`, `SPDXLicenses`, `PRONOMFormats`; depends on `crate`, never the reverse), xattr metadata (`impl.WrittenMetadata`), arcp file system (`arcp`). Only dependency: Jackson. |
| `ui` (`no.s11.wpsld.ui`) | JavaFX 27: `CrateEditor` (main window: entities, editor, JSON-LD preview) and `EntityEditor` (type tree and form for one entity). |

Builds: Java 25, Gradle 9.8; `./gradlew build` in the conda env
(`environment.yml`), runtime images with the Temurin `Dockerfile`
(`dist/wpsld-linux-x64.zip`, cross-linked `dist/wpsld-windows-x64.zip`), and
releases from GitHub Actions (`.github/workflows/release.yml`, on a tag
`v<version>`): app images with native launchers for Linux x64 and arm64,
macOS x64 and arm64 and Windows x64, built on each platform, and a portable
zip without JavaFX (`./gradlew :ui:distZip`, see `ui/src/dist/README.txt`).
The apps are not signed, so SmartScreen and Gatekeeper block them at first
(see README.md). Next: sign `wpsld.exe` in the workflow (SignPath Foundation
is free for open source; Azure Trusted Signing), and sign and notarise the
macOS apps (needs an Apple Developer account).

Measured for schema.org 30.0: about 430 ms cold / 110 ms warm to load, the
model about 2.6 MB once the JSON is dropped; the editor is built in about
150 ms. Most of the time to the first window is JVM and JavaFX start-up.

## Next steps

### User help

The audience includes researchers and research data managers who may not
know RO-Crate, JSON-LD or schema.org. Write for tasks first, introduce terms
only when needed, and distinguish describing metadata from packaging or
depositing data.

- **Help > wpsld Help** *(started)*: an offline, searchable, non-modal
  JavaFX help window with linked Markdown topics, contents and screenshots.
  F1 opens the topic for the focused editor control (Command-? also works
  on macOS); the profile chooser's help button opens its topic directly.
  Existing field definitions remain available separately. The help viewer
  follows the chosen style and colours, including dark mode.
- The getting-started walkthrough covers creating and saving a description,
  linking a file and an author, and exploring types and properties with a
  photograph and a Wikidata person. It includes screenshots and further
  reading; the README links to the Markdown topics for reading outside the app.
- Short topics cover opening and saving, profile loading and preferences,
  types, entities and identifiers, properties and links, copy and paste,
  and views. Paste Special is not implemented yet; document its actual
  options when it is added.
- **Next:** add topics on required fields, people and organisations,
  files and troubleshooting; add a glossary. Check every instruction against
  the app's actual behaviour and
  the applicable RO-Crate specification.
- Extend contextual entry points as more topics and controls are added.
  Keep the help navigable by keyboard and screen reader,
  usable in all styles and offline, and test that packaged releases contain
  every topic.
- Author topics in `ui/src/main/resources/no/s11/wpsld/ui/help/` as
  CommonMark. The native renderer supports headings, paragraphs, lists,
  emphasis, code, HTTP(S) links and bundled images under `help/images/`;
  use relative image paths with ASCII names and descriptive alt text. Add a
  topic to `HelpDialog.Topic` and link it from `index.md` to make it
  searchable and available to contextual help. Regenerate walkthrough
  screenshots with:

      HELP_SCREENSHOTS=$PWD/ui/src/main/resources/no/s11/wpsld/ui/help/images ./gradlew :ui:test --tests '*ReadmeScreenshots.helpScreenshots' --rerun

  The places' screenshots (`placeScreenshots`, same folder) show
  OpenStreetMap's tiles, so they need the internet; GeoNames is not asked.

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
- **Places** *(done)*: as RO-Crate 1.2 and later describe them, a `Place`
  (from `contentLocation` or `spatialCoverage`) whose `geo` is a GeoSPARQL
  `Geometry` with Well-Known Text in `asWKT`. Both terms are fields like
  schema.org's (`ro-crate-terms.jsonld`). Places are found by name in
  GeoNames (`GeoNames`, `PlaceFinder`; the user's own GeoNames username in
  Preferences > Places): "New ▾ Place from GeoNames…" on a field whose range
  includes Place, or "Find place…" on a place, sets its GeoNames `@id`,
  `name`, `identifier`, `url` and a Geometry at GeoNames' point. A place's
  form shows it on a map (`MapView`, OpenStreetMap tiles kept in memory;
  none in the tests), and a click moves it (`Places.setPoint`, keeping
  GeoCoordinates or latitude/longitude where a crate has them). The preview
  links to it on OpenStreetMap. Next: tiles kept on disk between runs,
  drawing lines and areas on the map, and `_:` blank node ids for new
  geometries, as the specification's example has.
  - *Map in the preview:* not shown there for now, as the form beside it
    already has the map, and the two would duplicate each other. If the
    preview is used on its own, e.g. a read-only viewer of crates, the
    preview of several entities, or an exported `ro-crate-preview.html`,
    show a read-only `MapView` (no `setOnPick`) of `Places.of(entity)` there,
    for places, geometries, GeoCoordinates, and entities whose
    `contentLocation` or `spatialCoverage` lead to a place.
- **Required types** *(done)*: a notice beside "Add type…" when a profile
  requires a type the entity lacks (subtypes count), with a button to add it.
- **Multi-line text.** Only the header's Description is a text area now;
  every other text value is a one-line field, which drops line ends when
  pasted into and can't show them. Give text fields a button (e.g. ⤢ at
  their end, also Ctrl+Enter or a context menu item) that turns the field
  into a text area of a few rows, wrapping, where Enter adds a line and
  Tab still moves on (as Description does); and back, if it has one line.
  A value that already has line ends opens as a text area. Useful for
  `description`s outside the header, `text`, `abstract`, `citation` and
  values pasted from elsewhere. Keep `EntityClipboard.guard` on it.
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
- Line ends: saved files have \n on every platform (`Crate.WRITER`,
  rather than Jackson's system line separator), so the same crate is the
  same file on each, e.g. for git; except a file opened with \r\n on
  every line, which keeps them, as Vim does (`CrateFile.lineEnd`).
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

- **Copy and paste entities.** In the entity list, Edit > Copy (and the
  list's own menu) puts the selected entities on the clipboard as their
  JSON-LD (`EntityClipboard`): as text for text editors (an object, or
  several separated by commas, as in a `@graph`); as `application/ld+json`,
  a document of the crate's `@context` and a `@graph` of the entities; and
  as wpsld's own `application/vnd.s11.wpsld.entities+json`, so wpsld's own
  fields know they are entities: reference fields paste a single entity's
  `@id`, other fields nothing. Paste in the list adds them (also from such
  a document, or the text), mentioned by the root.
  1. **Several entities** *(done)*. Select several in the entity list (multiple
     selection); Copy puts them all in one `@graph`; Paste adds them all
     and selects them. A shallow copy: references to entities outside the
     copied set stay as they are. Pasting a reference field still takes a
     single entity's `@id`; with several on the clipboard, nothing (or one
     row each, if wanted).
     - **Which crate it came from.** Each crate has an `arcp://uuid,<uuid>/`
       base (RO-Crate's scheme for crates without a location). The
       `application/ld+json` document gets it as `@base` in its
       `@context` (`[<crate context>, {"@base": "arcp://uuid,…/"}]`), so
       other JSON-LD tools expanding it keep `#uom` of one crate apart from
       `#uom` of another. The wpsld format wraps the same document:
       `{"version": 1, "crate": "arcp://uuid,…/", "referenced":
       [entities], "document": {…}}`. A document without `@base`, or text,
       counts as from another crate (the safe default). The version is
       raised only for changes that would mislead an older wpsld, which
       then ignores it and pastes from `application/ld+json`; added keys
       keep it.
     - **Assigning the UUID**: random per opened crate, kept in
       `CrateFile` and not in the crate. File > New and Open: a new one.
       Save As…: a new one, as the copy is a different crate from then on
       (entities copied before still count as from the original: harmless,
       as the rules below are safe either way). Reload after a change on
       disk keeps it. Each window is its own process now, so two windows
       with the same file have different UUIDs, and pasting between them
       is as between crates (renaming `#` ids that are taken). Later, when
       one wpsld process has several crates open: windows of the same file
       share it, and perhaps a UUID derived from the file (e.g. a
       name-based UUID of its path, as `arcp://name,…`; moving or copying
       the folder changes it). Relates to watching the file for changes
       (under Menus). A crate whose root `@id` is absolute (published)
       could use that as its base instead.
     - **`@id`s when pasting**, into the same crate or another: absolute
       ones (`https:`, `mailto:`, `urn:`, `arcp:`) name the same entity in
       any crate, so are kept; if the crate has it already, offer to merge
       (the dialogue of renaming to an existing `@id`) rather than add a
       copy. `#local` ones are kept if free and get a new one if taken (a
       copy within one crate; another crate's `#uom` may be another
       organisation). `_:` blank nodes always get a new `#` id. Relative
       paths (data entities) are kept if free; if taken, they name this
       crate's file, so offer to merge as for absolute ones, never a `#`
       id. References among the pasted entities follow a new `@id` (not
       those of the crate's own entities).
     - **Referenced entities that weren't selected**: the wpsld format
       carries them one level deep (`referenced`), but not the
       `application/ld+json` document, which would be an incomplete deep
       copy (they may refer to others again). Paste keeps the references
       and asks nothing; using the carried copies, it reports those that
       may be wrong. Same `@id` in the crate: linked; in another crate a
       local `#` one whose entity differs from the carried copy is
       ambiguous (it may be another organisation of the same `#uom`), so
       linked and reported (an absolute one is the same entity, a question
       of merging, not ambiguity). Not in the crate: dangling, kept and
       reported (Edit > Repair Crate would make it a Thing). Within one
       crate, the usual case, nothing is reported.
  2. **Paste options and Paste Special…** Powerful even without a deep
     copy.
     - **A notice after Paste**, when it reported anything, as the
       reader's warnings beside the list: "Pasted 2 entities. 1 reference
       to an entity not copied: `#uom` (missing here). [Add it]
       [Paste Special…]", like Office's paste options. A button undoes the
       paste and pastes again with that option, one undo step in all. A
       plain warning to start with.
     - **Edit > Paste Special…** (Ctrl+Alt+V, as in Excel; ⌥⇧⌘V on macOS)
       opens a dialogue that lists what is on the clipboard (copied, and
       only referenced) and offers:
       - References to entities not copied: *keep as they are* (default),
         *add them* (the carried copies, one level, `#` ids renamed if
         taken, their own references kept as they are), *remove these
         references*; later *add all* (deep copy, step 5).
       - Pasted entities whose `@id` the crate has: *ask for each* (the
         merge dialogue), *merge all*, *keep the existing ones*, *paste as
         copies with new `@id`s* (which Edit > Duplicate is, step 6).
       - Later, how to treat the `@context` (step 4).
     - Enabled whenever Paste is, in the entity list.
  3. **Merge Entities**: select two or more entities in the list and merge
     them with the dialogue of renaming to an existing `@id` (`Crate.merge`,
     one undo step), choosing which `@id` stays. For duplicates found
     later, e.g. a pasted person who was already there under another `@id`.
  4. **Context merging.** When the pasted document's `@context` differs
     from the crate's (another RO-Crate version, e.g. Bioschemas IRIs of
     1.2 against 1.3, or extra terms such as `ldac:` or a custom `@vocab`),
     expand its terms with its own context (see `JsonLdContext`, which
     reads contexts without fetching) and compact them with the crate's,
     adding to the crate's context the terms it lacks. On the whole
     `@graph` at once, so it builds on several entities.
  5. **Deep copy**: optionally the entities the copied ones refer to as
     well, all the way, matching those the other crate already has (same
     `@id`, or same identifier) rather than duplicating them; offering to
     merge where they differ.
  6. Edit > Duplicate within a crate, which is easy on top of these: Paste
     Special's *paste as copies with new `@id`s*, so unlike Paste every copy
     gets a new `#` id, absolute ones too (a template for "another person
     like this").
  7. Check on Windows and macOS that the entity formats reach another
     wpsld window.
- **An SPDX licence selector** *(done)*: "New ▾ Licence from SPDX…" on
  `license` (`LicenseFinder`, `SPDXLicenses`) searches the bundled SPDX
  License List by identifier or name while typing, deprecated identifiers
  hidden unless asked for, and adds the licence as a CreativeWork with its
  `http://spdx.org/licenses/<id>` IRI (as SPDX's own JSON-LD has it), its
  SPDX name, and its identifier as a PropertyValue (`#spdx-<id>`, `name`
  SPDX, `propertyID` https://registry.identifiers.org/registry/spdx,
  `value` the identifier, `url` its SPDX page), as RO-Crate 1.3's Root Data
  Entity identifier section describes; a licence the crate has is linked.
  Edit > Repair Crate turns other forms of SPDX addresses
  (`https://spdx.org/licenses/MIT.html`, another case) and `license` text
  that is an SPDX address or identifier into the IRI, merging duplicates,
  and completes the licences; also licences known by an address the list
  links them with (`rdfs:seeAlso`, compared without http/https, `www.`,
  trailing slash, `legalcode` or `deed.xx`), e.g. Creative Commons'
  readable `https://creativecommons.org/licenses/by/4.0/` (SPDX lists only
  its `…/legalcode`), keeping that address as the licence's `url`; links
  that several current licences share (46 of 875, e.g. GNU's GPL page for
  `-only` and `-or-later`) are left alone. Bundled from the latest release, v3.29.0, by
  `scripts/update-spdx-licenses.sh <tag>`: its `jsonld/licenses.jsonld`
  (21 MB, every licence's text) trimmed to the licences' `@id`, name,
  OSI/FSF/deprecation flags and `rdfs:seeAlso` (415 kB), credited under
  CC-BY-3.0 in the third-party notices. Findings (2026-11-09): the
  `http://spdx.org/licenses/` IRIs can't be resolved as Linked Data (they
  redirect to the HTML page; the JSON-LD is at `….jsonld`, served as
  `binary/octet-stream`), so the list is bundled rather than fetched.
- **File formats from PRONOM**, as RO-Crate recommends for
  `encodingFormat`: a contextual entity `["WebPage", "Standard"]` with
  `@id` `https://www.nationalarchives.gov.uk/PRONOM/<PUID>` (still
  resolving, via redirect to the new site), beside the media type string.
  Findings (2026-11-12): The National Archives replaced PRONOM with
  https://pronom.nationalarchives.gov.uk/ (beta); its data is under the
  Open Government Licence v3.0 (and the GitHub copy,
  `nationalarchives/pronom`, under MIT). No search API (its `/search` is
  HTML only), but `/<PUID>.json` and `.xml` give one format, and the DROID
  signature file (`/binary-signature.xml`, V125 of 2026-09, 3.7 MB, 2,571
  formats: PUID, name, version, MIME types, extensions; `signatures.json`
  names the latest) can be bundled and searched offline, as the SPDX list
  is. Credit "Contains public sector information licensed under the Open
  Government Licence v3.0" and the signature version.
  - *Done:* "New ▾ Format from PRONOM…" on `encodingFormat`
    (`FormatFinder`, `PRONOMFormats`) searches the bundled formats by name,
    version, PUID, extension or media type while typing, starting from the
    file's media type or extension, and links the format (a `WebPage`,
    `Standard` with its PRONOM address and name) beside the media type,
    adding that if the file has none. `encodingFormat` takes links as well
    as text (`WebPage` added to its range in `ro-crate-terms.jsonld`), so
    such links are no longer turned into text when edited. Many formats
    share a media type or extension (`application/pdf` and `.pdf` match 35
    PUIDs: PDF 1.0 to 1.7, PDF/A 1a to 4f…), so each version is listed.
    Bundled by `scripts/update-pronom-formats.sh <version>|latest` from
    signature file V125, trimmed to PUID, name, version, media types and
    extensions (271 kB), credited under the Open Government Licence v3.0.
  - *Done:* Repair Crate links the format where a file's media type
    belongs to one format only (209 of PRONOM's 310 media types, e.g.
    `text/csv`, `text/markdown`, `application/zip`), and turns other forms
    of PRONOM addresses (`http:`, the new site's, `.json`) into
    `https://www.nationalarchives.gov.uk/PRONOM/<PUID>`. No identifiers.org
    prefix exists for PRONOM, so no PropertyValue as for SPDX.
  - With proper file support (a crate with its root folder, see Opening a
    folder below), identify a file's format from its content with the same
    signature file (and `container-signature.xml` for ZIP and OLE2
    formats), as DROID and Siegfried do, as the extension doesn't tell the
    version; and fill in `contentSize`.
- **Organisations from ROR** *(done)*: "New ▾ Organization from ROR…" on
  fields that take an Organization, and "Find in ROR…" in a ROR row on an
  Organization's form (`OrganizationFinder`, `ROR`), searching
  `api.ror.org/v2/organizations?query=` on Enter, or the record of a ROR
  identifier (checked by its ISO 7064 97-10 check digits). The name to
  write is chosen from all its names (display name first, then labels,
  aliases, acronyms, with language); it gets `url` (web site) and
  `location`, the GeoNames Place of its city, from the record. Repair Crate
  writes ROR addresses given otherwise as `https://ror.org/<id>`, merging
  duplicates. Next: a `Client-Id` setting if the limit bites; ROR's
  parent/child `relationships` as `parentOrganization`; the ROR dump (CC0)
  as an offline data module. Notes from the plan:
  for `affiliation`, `publisher` and `funder`, as
  RO-Crate recommends ROR identifiers: search its REST API v2
  (https://ror.readme.io/docs/rest-api), on Enter rather than on each key.
  Without a client ID (https://ror.readme.io/docs/client-id, checked
  2026-11-12) the limit is 50 requests per 5 minutes, 2,000 with one in a
  `Client-Id` header; client registration is paused and no limits are
  enforced until it reopens. Organisations have many names, in many
  languages (`ror_display`, `label`, `alias`, `acronym`, with `lang`):
  write the `ror_display` name as a plain `name`, without `@language`,
  which is not yet common in RO-Crates, and let the user choose another of
  its names instead, e.g. its local name.
- **Publications and datasets by DOI** *(done)*: "New ▾ Work by DOI…" on
  `citation`, `isBasedOn` and `subjectOf`, and a DOI row on a work whose
  `@id` is a DOI ("Fill in from DOI", keeping its values) or on an article,
  book, chapter, report or thesis without one ("Find DOI…") (`WorkFinder`,
  `DOI`). A DOI typed or pasted into those fields becomes its
  `https://doi.org/` link when confirmed, and is looked up once to describe
  the work, as RO-Crate asks citations to be URLs. A DOI in any form (bare `10.…`, `doi:`, `https://doi.org/`,
  `dx.doi.org`, percent-encoded) is looked up at its agency
  (`https://doi.org/ra/<doi>`): Crossref's REST API (`api.crossref.org/works/`)
  or DataCite's (`api.datacite.org/dois/`), whose records have more than
  content negotiation gives (DataCite's CSL has no ORCIDs; Crossref answers
  no schema.org), else CSL-JSON at doi.org for other agencies. Words are
  searched at both, alternately: Crossref's `query.bibliographic`, and
  DataCite's with all words required (`a AND b`), as its ranking of loose
  words is poor. The work is described as RO-Crate's citation example has
  it: DOI as `@id` and `identifier`, `name`, `datePublished`, `creditText`
  (a short citation, see below), `abstract` as plain text (Crossref's JATS or DataCite's HTML;
  Crossref has abstracts only where the publisher deposited one), the first
  10 authors as Persons (by ORCID iD, or a Person of the crate with the same
  name), and the journal by `isPartOf` a Periodical with `issn`, `@id`
  `https://portal.issn.org/resource/ISSN/<ISSN>`. RO-Crate's example has
  `"journal": "…"`, but the RO-Crate context defines no `journal` property
  (only `Journal` for `Periodical`), so wpsld uses `isPartOf`.
  **TODO:** raise an issue at https://github.com/ResearchObject/ro-crate
  that the citation example of 1.3's Contextual Entities (Publications via
  citation property) uses `journal`, which the RO-Crate context doesn't
  define: suggest `isPartOf` a Periodical (`Journal`) as schema.org has it,
  or adding the term. Repair Crate
  writes DOIs as `https://doi.org/…`, merges works whose DOIs differ only in
  case, and turns `identifier` and `citation` text that is a DOI into that
  (a link for `citation`); a DOI alone as `@id` is left, being a path.
  Crossref's public pool allowed 1 request per second (2026-11-12).
  - *Short forms* such as "Xuan et al. (2015): Topic Model for Graph
    Mining" are the `creditText`, which RO-Crate gives any CreativeWork as
    "a textual citation for the entity" (Root Data Entity: Resolvable
    persistent identifiers and citation text): the first author's family
    name ("et al." for three or more, "A & B" for two), the year, the
    title, and the journal if any. Next: show it as the work's label in references and the entity
    list, where the title alone can be ambiguous.
  - *Next:* explore the kinds of objects DataCite registers besides
    articles (its `resourceTypeGeneral`: Dataset, Software,
    ComputationalNotebook, Workflow, Image, Audiovisual, PhysicalObject,
    Collection, Model, Instrument, …), and describe each as RO-Crate would:
    a Dataset, possibly an RO-Crate itself (`conformsTo`
    `https://w3id.org/ro/crate`, e.g. from WorkflowHub or Zenodo, see
    Referencing other RO-Crates), software as `SoftwareSourceCode` or
    `SoftwareApplication`, a workflow as `ComputationalWorkflow` (Workflow
    RO-Crate), an instrument as in Provenance's equipment; and their
    licences (DataCite's `rightsList`, often SPDX ids) and keywords.
    Also the publisher as an Organization (from ROR, now that it is there).
- **People from ORCID** *(done)*: "New ▾ Person from ORCID…" on fields
  that take a Person, and "Find in ORCID…" in an ORCID row on a Person's
  form (`PersonFinder`, `ORCID`), search ORCID's public API
  (`pub.orcid.org/v3.0/expanded-search`, no credentials needed, tried
  2026-11-12) by name or iD, on Enter, listing ORCID iD, other names and
  institutions (as text: no ROR ids), and add the Person with
  `https://orcid.org/<iD>` as `@id`, `name`, `givenName` and
  `familyName`; e-mail addresses ORCID may give are ignored. Offline, the
  search fails saying the iD can still be typed. Repair Crate writes ORCID
  addresses given otherwise (`http:`, no scheme, trailing slash, small
  `x`) as `https://orcid.org/<iD>`, if the ISO 7064 check digit is right,
  merging duplicates; sandbox iDs are left. Next: affiliations as ROR
  Organizations, from ORCID's employment records (`/v3.0/<iD>/employments`),
  once ROR lookups exist: their organisations are disambiguated by ROR,
  Ringgold or GRID (Josiah Carberry's by Ringgold only), so those without
  a ROR id need a ROR search by name and country (`ROR.search`); tests and help use
  ORCID's fictional Josiah Carberry, 0000-0002-1825-0097.
- **Insert menu** *(next, after ROR)*: Insert > Place…, Person…,
  Organization…, Licence…, Publication by DOI… (and File format…), opening
  the same finders as the New ▾ items, adding the entity to the crate (and
  showing it) without first having to find the field that links it; later
  datasets and other kinds of entities.
- **Plugins for other lookup services** *(TODO, consider)*: let others add
  lookups (e.g. Wikidata, ISNI, RRID, a discipline's own registry, an
  institution's internal staff or project list inside a TRE) without
  changing wpsld, and let the offline data modules (above) plug in the same
  way. The Java module system's way is still the SPI with
  `java.util.ServiceLoader`: `lib` declares an interface and `uses` it in
  its `module-info.java`; a plugin module says `provides
  no.s11.wpsld.lookup.LookupService with org.example.MyLookup;` (on the
  class path, a `META-INF/services/` file instead, so ship both).
  `ServiceLoader.load(LookupService.class)` finds what is on the module
  path or linked into the jlink/jpackage image. To pick up plugin JARs
  dropped into a folder at run time (e.g. the user data folder) rather than
  linked in: `ModuleFinder.of(folder)`, then
  `Configuration.resolveAndBind(…)` on the boot layer's configuration,
  `ModuleLayer.defineModulesWithOneLoader(…)`, and
  `ServiceLoader.load(layer, LookupService.class)`; a plugin then only
  `requires no.s11.wpsld` (and Jackson). Considerations:
  - The interface, roughly what the six built-in lookups share: an id and
    name; which types and properties it serves (Place on `contentLocation`,
    Organization on `publisher`…), for the New ▾ and Insert menus; `search(query)`
    giving results with `@id` IRI, label and details (for a generic finder
    dialog, with a hook for a richer one like the map); `describe(entity,
    result, lookup, newEntity)`; an optional `repair(crate)`; whether it
    works offline; the settings it needs (e.g. GeoNames' username) shown in
    Preferences. Move the built-in ones behind it first, to prove it.
  - Plugins run with the user's full rights (the Security Manager is gone
    since Java 24), so only install trusted ones; say where each came from
    and let users turn them off. No downloading plugins from within wpsld at
    first.
  - Versioning of the interface (a `@since`/API version the plugin
    declares), as plugins outlive releases.
  - Licences: a plugin of its own module and JAR can carry data under
    licences incompatible with wpsld's Apache-2.0, which is why the data
    modules may suit this.
  - Alternatives: OSGi or PF4J add class-loader isolation and lifecycles,
    but ServiceLoader with module layers needs no dependency.
- **More lookups for RO-Crate's recommended identifiers** (Recommended
  Identifiers in RO-Crate 1.3's metadata page). Smaller helpers: `keywords` edited as tags but
  written as one comma-separated string, and `temporalCoverage` as a
  from/to date range (`1950/1975`).
- **Offline use, e.g. inside a Trusted Research Environment (TRE)** whose
  firewall blocks GeoNames, OpenStreetMap, ROR, ORCID, DOI and the profile
  registries. Every online lookup must fail gracefully and say so, never
  block typing an identifier by hand, and never send crate content outside
  (lookups send only the search text; tell the user what goes where). A
  preference to turn all online services off, and per service. Bundled or
  downloadable offline data, chosen by size:
  - Places: GeoNames' dumps (https://download.geonames.org/export/dump/,
    CC BY 4.0, daily): `cities15000.zip` (3.2 MB, about 25,000 places with
    population over 15,000 or capitals), `cities5000` (5.5 MB), `cities1000`
    (11 MB), `cities500` (13 MB), with `countryInfo.txt` and
    `admin1CodesASCII.txt` for countries and regions; `allCountries.zip`
    (403 MB) is too big to bundle but could be imported by a TRE's admin.
    Coarse: towns, not parks or buildings, so clicking the map stays the way
    to a precise point.
  - Maps: OpenStreetMap has no coarse street-level download, but
    https://osmdata.openstreetmap.de/data/ has land and water polygons and
    coastlines (ODbL), enough for an offline outline map to place a point
    on; or a tile server inside the TRE, set as the map's tile address.
  - Licences (SPDX) and file formats (PRONOM) are bundled already, small
    enough (415 and 271 kB) not to bloat the zips.
  - ROR's full data dump (https://doi.org/10.5281/zenodo.6347574, CC0;
    v2.15 of 2026-10-20 is a 39.3 MB zip, 330 MB of JSON unpacked) stays
    about 39 MB compressed inside a jar: too big for the main zips, but
    fine as an optional data module. The registry changes daily; the dumps
    are released as versions on Zenodo (check how often).
  - ORCID can't be offline as a whole: its yearly public data file is
    about 270 GB (919 GB unpacked), and even only the public names
    (`scripts/orcid/names.sql` on ORCID's data in BigQuery, tried
    2026-11) are over 1 GB of CSV, besides being personal data about
    millions of people. ORCID and DOI lookups stay online, with typing the
    identifier by hand as the fallback. An institution may make its own
    offline subset: its researchers' names and affiliations, by its ROR ids
    (`scripts/orcid/`), megabytes rather than gigabytes, as a data module
    for its TRE, never shipped with wpsld.
  - **Data modules:** a third Gradle module beside `lib` and `ui` for the
    offline data, or one module (and jar) per dataset, as they update at
    different rates: SPDX a few times a year, PRONOM 2-4 times a year, ROR
    with each Zenodo release, GeoNames daily. Each jar carries its data,
    source, version and licence (shown in Help > About), so the application
    and the data can be released separately, e.g. by GitHub Actions
    packaging each data release, and a TRE can take the jars it needs.
    Small ones (SPDX, PRONOM) stay in the main zips; large ones (ROR,
    GeoNames' bigger dumps) are added as below.
  - **Data bundles added by the user** (not yet): data too big to bundle,
    or under a licence that doesn't fit with Apache-2.0, dropped into a
    folder that wpsld reads at start (e.g. `data/` beside its settings, or
    beside the application for a whole TRE), each with its source, version
    and licence shown in Help > About. For example GeoNames'
    `allCountries.zip` (403 MB), a ROR dump, or map data from
    https://download.geofabrik.de/ (OpenStreetMap extracts per continent,
    country and region, ODbL, up to tens of GB): too large to bundle, but
    a region's extract could give offline street-level maps and place
    search. A "pre-populated wpsld" could be prepared outside a TRE with the
    data its users need, then moved inside the firewall as one folder or
    zip. Needs: the folder's layout and a manifest per bundle, checking
    what is there at start, and preferring the bundles over online services
    when offline.
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
- **`conformsTo` with several values** is shown as one text field holding
  them all in brackets (`[…]`), rather than one row per value as other
  properties with several values have; probably a value form the field
  doesn't recognise (e.g. an array of `{"@id"}` read as text).
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
