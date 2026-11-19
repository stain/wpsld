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
| `lib` (`no.s11.wpsld`), **free of JavaFX** | schema model (`soss`: `SchemaFromJsonLd`, `ClassDef`, `PropertyDef`), form fields (`form`: `FormFields`, `SchemaOrgFormFields`), crate model (`crate`: `Crate`, `Entity`, `Places`), lookups of identifiers (`lookup`: `GeoNames`, `ORCID`, `ROR`, `DOI`, `SPDXLicenses`, `PRONOMFormats`; depends on `crate`, never the reverse), xattr metadata (`impl.WrittenMetadata`), arcp file system (`arcp`). Dependencies: Jackson, Apache Tika (core), commonmark-java and JMustache. |
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

### Terminal interface

The experimental `tui` module uses Lanterna and `lib` to browse and edit
existing crate values. View-specific actions and temporary save messages appear
in a bottom status line, including in dialogs. PageUp and PageDown browse the
main list or the references behind a nested view; `v` promotes a nested entity
to the main list, and Escape closes nested views. The list is ordered as in
the JavaFX list and can be filtered by ID, name or a type chosen from a
searchable tree of the crate's types and their parents. See the README for
its current keys and limits.

The `ui` images and zips must not depend on `tui`, so that they carry neither
Lanterna nor its LGPL requirements (see README). The `tui` module has a jlink
setup of its own, for a smaller distribution with only `wpsld-tui` and
`wpsld-cli` (no JavaFX, so no jmods needed), which the Dockerfile builds for
Linux x64. To do: build and zip it in the release workflow for the other
platforms, as `wpsld-tui-<version>-<platform>.zip`.

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
- Short topics cover opening and saving, folders and files, file formats,
  profile loading and preferences, types, entities and identifiers,
  properties and links, with a page for each lookup (people,
  organisations, publications and DOIs, licences, places), copy and
  paste, and views. No page is over about 100 lines: split a page that
  grows past that, and link it from `index.md` under its parent. Links to
  sections (`page.md#section`, `#section`, named as GitHub names headings)
  open the page there. Paste Special is not implemented yet; document its
  actual options when it is added.
- **Next:** the topics list as a tree, the pages split off under their
  parent (Folders and files, File formats under Opening and saving; the
  lookups under Properties and links), and each page's sections under it
  to jump to; F1 on more controls (the entity tree to Folders and files,
  Encoding format to File formats, each lookup's button to its page);
  add topics on required fields and troubleshooting; add a glossary. Check every instruction against
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

### Command-line tools (a `bin` module)

*Done, first version (November 2026)*: `wpsld-cli` (the module `bin`,
`no.s11.wpsld.cli`, with [picocli](https://picocli.info/) for its commands,
options and `--help`; only `lib` besides, no JavaFX), a launcher of its own
beside `wpsld` in the jlink and app images (`secondaryLauncher`, with a
console on Windows) and the portable zip (`cliStartScripts`):
- `check [--json] [--offline] CRATE`: what the editor checks before saving
  (`CrateCheck`, a list of `Issue`s, each with its severity as RFC 2119's
  REQUIRED/RECOMMENDED/OPTIONAL, check, requirement's source, entity,
  property and value): reading the crate, the properties its profiles require,
  the profiles found by its `conformsTo` as the editor finds them
  (`CrateProfiles`: bundled, kept by the editor, else loaded from the web,
  unless `--offline`; without any, the core RO-Crate profile), RO-Crate's
  own requirements, and an attached crate's files and folders; files in its
  folder that it doesn't describe are warnings (RECOMMENDED), as is a
  Dataset's @id without its / (SHOULD). Exit status 0 if it passed
  (warnings allowed), 1 for problems, 2 if it can't be read. `--json` writes
  the report as rocrate-validator does (`validate -f json`: `meta`,
  `validation_settings`, `passed`, `issues` with `severity`, `message`,
  `violatingEntity`, `violatingProperty` as an IRI, `violatingPropertyValue`
  and the `check` with its requirement and profile, and `statistics`), so
  that what reads its reports reads wpsld's; plus `unknown_profiles`.
- `repair [--dry-run] [-o FILE] [--json] CRATE`: Crate > Repair Crate
  (`CrateRepair`, which the editor's dialog uses too), written back in place
  safely (`SafeSave`) with the file's line ends (`Crate.lineEnd`), or to a
  file; says what was done, as the editor's dialog.
- `preview [-o FILE] [--replace-other] [--describe] [--offline] CRATE`:
  `ro-crate-preview.html` beside an attached crate's metadata, or the file
  given (as a detached crate needs), with the labels of the crate's profile;
  another tool's preview only with `--replace-other`, moved aside into
  `ro-crate-preview_files/` (`CratePreview.moveAside`, as the editor);
  `--describe` describes it in the crate, as the editor does on saving.

*To do*, next commands and options, each from what the editor already does
in `lib` (moving what is still in `ui` there first):
- **Make a crate detached or attached** (see Attached and detached crates):
  `detach --base URI` (checking each file is at the base URI first, as a
  job does in the editor) and `attach FOLDER` (downloading the files there,
  checked against their sizes and checksums).
- **Update file details**: what files say about themselves
  (`FileMetadata`, the `readers-*`: EXIF, PDF, Office documents' own
  metadata), as Crate > Update File Details… offers it, chosen by options
  (e.g. `--only name,dateCreated`) rather than a dialog; with the readers'
  modules in the image, as the editor has them.
- **Check files and web availability**: an attached crate's files on disk
  (sizes, checksums where given), and web-based data entities, as Check Web
  Availability does (HEAD, sizes); as part of `check` with `--web`, or a
  command of its own, as it asks the network.
- **Reports in other formats** for CI, besides the text and
  rocrate-validator's JSON (*done*, `--json`): GitHub Actions' workflow
  commands (`::error file=…,title=…::…`, annotations in a pull request
  without any set-up); SARIF 2.1.0 (OASIS; GitHub code scanning,
  `upload-sarif`); EARL (W3C, RDF: `earl:Assertion`s of the crate against
  each requirement, as JSON-LD) or a SHACL validation report
  (`sh:ValidationReport`, the vocabulary rocrate-validator's checks are
  written in). Each `Issue` has what they need.
- Others to weigh: describe a folder as a new crate; identify files'
  formats (PRONOM); a crate given as a URL or a zip, as the editor opens
  them.

### Package managers *(TODO, investigate)*

Find out how to get wpsld into the package systems people install software
with, instead of only zips from the GitHub releases: a Debian package (`.deb`,
and what Debian or Ubuntu themselves require to include it, given the
bundled Java runtime and JavaFX), WinGet on Windows, and Homebrew on macOS
(a cask for `wpsld.app`; the apps are not signed or notarised yet, see
"Builds"). Also look at Flatpak and Scoop. For each: the packaging
format, how an update is submitted and automated from the release workflow,
and what its rules say about bundled runtimes, third-party notices and
unsigned apps. The separate terminal viewer zip (`wpsld-tui`, see
"Terminal interface") might be packaged separately, as it is small and has
no JavaFX.

Guix, for the reWIRE project, is probably extensive: Guix builds everything
from source, so Java 25, JavaFX and the Gradle dependencies would all need
packaging or porting first. The terminal viewer and `wpsld-cli`, without
JavaFX, are the likelier first step.

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
- **How long the GUI tests take in CI** (measured November 2026, PR #24 and
  #26, from each OS's `class-times.tsv` in its `test-reports-<os>`
  artifact): the classes took 590 s in all on Linux, 830 s on Windows and
  1 640 s on macOS. Each test, not some, is about 4 times as slow on
  GitHub's macOS runner (3 cores, 7 GB) as on Linux, 1.4 times on Windows;
  classes of one test take the same everywhere, so it is the JavaFX work
  (software rendering, `prism.order=sw`), not starting JVMs. Done: test
  classes in parallel, a JVM a core, at most three (`maxParallelForks`;
  macOS's three cores gave no slower tests than two), and `TestCrateEditor`
  split in four (it alone took 645 s on macOS): the job's Build and test
  step went from over 20 minutes (timed out) to 15½, then 14 on macOS, and
  5 minutes on Linux, 7 on Windows. Yet only about 2 of the 3 JVMs are busy
  on average (the classes' time over the test task's: 2.1 on macOS, 2.2 on
  Linux and Windows), where 3 would take about 9 minutes on macOS:
  probably as Gradle starts the classes by name, so a long one starting
  late (e.g. `TestEntityEditor`, 286 s on macOS) runs on alone at the end.
  Left there for now; next, if wanted: record when each class starts and
  ends in `class-times.tsv` (seconds from the task's start) to see it, and
  split the longest classes (`TestCrateEditorEntityList` 297 s and
  `TestEntityEditor` 286 s on macOS) so that none takes more than about
  150 s; and the slowest tests themselves (e.g. the Getting started ones,
  40 s on macOS) could share their set-up.

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
- Insert > Entity… (as Add entity), then items that look an entity up
  and add it, mentioned by the root and shown, without linking it from a
  property, under a separator: Person from ORCID…, Organization from ROR…,
  Publication by DOI…, Licence from SPDX…, File format from PRONOM…, Place
  from GeoNames…
  (`EntityEditor.lookUp`, the same finders and descriptions as New ▾).
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
and recent files (in the state folder); see "Crates as ZIP, BagIt and on
the web" below.

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

### Following the OS, and a web-like style

wpsld aims at a native desktop experience: regular menus, the clipboard,
keyboard shortcuts, editing many entities at once. There are several
web-based RO-Crate editors already. Looking shiny is not the aim, and the
OS/2 Warp style is where wpsld started rather than what it is about.

Following the OS where JavaFX can:
- **The style follows the OS by default**: Windows 11 on Windows, macOS on
  macOS, and on Linux Classic or a Linux style (below). Today Classic is
  the default everywhere; View > Style still overrides it.
- The **accent colour** and **increased contrast** from
  `Platform.Preferences` (see "Platform guidelines and styles"), and the
  OS's font where a style doesn't name one.
- The screen's menu bar on macOS whatever the style, not only in the macOS
  style (today `setUseSystemMenuBar` only for `Style.MACOS`).

**Linux: its own style, or a more modern Classic?** To investigate. Classic
is Modena, JavaFX's look from 2014, which looks dated beside GNOME's
libadwaita (flat, rounded, header bars, Adwaita Sans) and KDE's Breeze.
Either:
- an **Adwaita** style (and perhaps Breeze, chosen by `XDG_CURRENT_DESKTOP`),
  as Windows 11 and macOS are their platforms' styles; or
- **Classic made modern**: flatter, rounded, the system font and accent
  colour, fewer gradients, so it serves Linux and anyone who wants a
  neutral style. Classic's dark colours (VS Code's Dark Modern) are
  already of today.

Look at AtlantaFX (MIT; Primer, Nord and Cupertino themes for JavaFX) for
either, and check it is still maintained before depending on it.

**A web-like style** ("Modern"?), as people expect from web applications
made with Bootstrap or React, for those who find native styles old-fashioned.
As with the Warp style, it needs its own components and form layout
(`FormLayout.WEB`), not only a stylesheet:
- the form as cards, labels above the fields, help and validation messages
  under each field rather than in popovers or dialogs;
- several values as chips or tags with × inside, added from an input at
  their end;
- primary and secondary buttons (one filled, the others outlined), larger
  click targets, more space, rounded corners, subtle shadows;
- notices as toasts at a corner, and empty states with a sentence and a
  button rather than empty lists;
- the sidebar as a navigation list, perhaps a search field at its top.

It keeps the menus, shortcuts, clipboard and multiple selection: it
changes how wpsld looks and lays out its form, not how it behaves.

### The GUI toolkit: staying with JavaFX

Considered in 2026-11 as thought experiments: rewriting wpsld in Rust, or
in .NET (C#). For now wpsld stays with JavaFX.

- **Rust**: `lib` and `bin` would port well (serde_json keeping key order,
  pulldown-cmark or comrak, a Mustache crate with the same whitespace rules,
  reqwest, clap) and gain a small native CLI and WebAssembly (a check or
  preview page on a homepage). Reading files' metadata has no Tika: EXIF,
  PDF, OOXML/ODF and MP4 crates cover the common formats only. The GUI is
  the hard part: Slint, egui and iced draw their own widgets (menus inside
  the window unless with `muda`, basic tables, varying IME and screen
  reader support); gtk4-rs is native only on Linux; Tauri and Dioxus are a
  web page in a window, which is what wpsld is not. They would start
  faster and use less memory, but editing would be no faster than now:
  0.7.0's speed-ups were algorithms, not drawing.
- **.NET**: Avalonia is much like JavaFX (its own widgets, all three OSes,
  also mobile and web); MAUI has no Linux and is mobile first; Eto.Forms
  wraps the native widgets (WPF or WinForms, AppKit, GTK), the most native
  option, but mainly kept by one company. Markdig, System.Text.Json,
  dotNetRDF, Stubble, PdfPig and a .NET metadata-extractor exist; no Tika,
  and no TestFX for Eto. Moving means rewriting `lib` as well, which works.
- **SWT** (Eclipse's toolkit) gives the native widgets of Eto while keeping
  Java: only `ui` would be rewritten, keeping `lib`, `bin`, Tika and their
  tests; SWTBot for the GUI tests. Check jpackage with SWT's per-platform
  jars first.
- **Java in the browser** without a rewrite: TeaVM or GraalVM's Web Image
  could compile `lib` (Jackson's reflection and Tika the likely obstacles),
  or CheerpJ run it as a jar.

If following the OS from JavaFX isn't native enough (native text fields
and tables with the OS's spell checking and text services), prototype an
SWT `ui`: the entity list, one form and pasting 2,000 entities, timed on
all three OSes against the performance crates.

### Entities

- **Choosing a type that hides the entity edited** *(to investigate,
  undecided)*: now the form keeps showing it, and it is selected again
  when listed again. Perhaps the form should empty instead ("No entity
  selected"), as after removing it: why would the user choose another
  type unless to go to another entity? But they may be looking for the
  entity to link from the one edited (e.g. which Person to give as its
  author), and lose their place. Watch what users do; perhaps keep the
  form while the type is chosen from a reference field, or offer
  "Back to …".
- **Copy and paste entities.** In the entity list, Edit > Copy (and the
  list's own menu) puts the selected entities on the clipboard as their
  JSON-LD (`EntityClipboard`): as text for text editors (an object, or
  several separated by commas, as in a `@graph`); as `application/ld+json`,
  a document of the crate's `@context` and a `@graph` of the entities; and
  as wpsld's own `application/vnd.s11.wpsld.entities+json`, so wpsld's own
  fields know they are entities: reference fields paste a single entity's
  `@id`, other fields nothing. Paste in the list adds them (also from such
  a document, or the text), mentioned by the root.
  0. **Paste a whole RO-Crate** *(TODO)*. When Paste finds a *full RO-Crate
     metadata document* on the clipboard (text or `application/ld+json`: a
     `@graph` with a metadata descriptor `about` a root data entity, as
     the prober of "Retrieving" decides, `CrateProbe`; not a few entities
     copied from a crate, whose document has no descriptor), it does not
     just add its entities but **asks**, in a dialog:
     - **Open as a new RO-Crate**: as if it had been opened from a URL with
       no base, a detached crate in no file (Save is Save As), checked by
       `CrateReader` as any crate is, with its problems shown, and the
       current crate asked about first if it has unsaved changes. No web
       base and no landing page, so a crate with relative `@id`s is
       semi-detached and has the "Attach…" button (see "Semi-detached");
       its `@context` is its own.
     - **Add all its entities to this crate**: every entity but the pasted
       crate's descriptor, and its root data entity as an entity of its own
       (an ordinary Dataset, not this crate's root: its `hasPart` and the
       rest kept; mentioned from this crate's root by `hasPart` only if the
       user says so, or as `mentions`, to decide), under the rules for
       pasted `@id`s below (a `#` id that is taken is renamed, absolute ones
       merged). This needs **`@context` merging**: the pasted crate's terms
       must mean the same in this one. Terms both contexts define the same
       are as they are; a term the pasted context defines that this one
       does not is **added to this crate's context** as an object (as
       `{"term": "iri"}`, from the pasted context if it is inline, or, if
       it is a context by address, that address added to the array and the
       terms it defines kept if they clash with none); a term both define
       *differently* is a clash, shown in the dialog (the entities using it
       listed), to resolve by renaming it in the pasted entities (to a
       prefixed or absolute IRI that means what the pasted context meant, so
       never silently the wrong meaning) or by cancelling; `@vocab` and
       `@base` that differ are clashes too (a pasted `@base` makes relative
       `@id`s absolute first, as Detach does, `Crate.absolutize`). The
       RO-Crate versions' own contexts (1.1, 1.2, 1.3 and the drafts) are
       the same for this: terms a later one added just come with it, and
       `conformsTo` of the pasted descriptor is not copied. The same
       merging serves "Add the entities of a crate opened from a file or a
       URL to this one" and entity paste from a document with another
       context, now guarded by `EntityClipboard.guard`.
     - Which is asked as the first thing Paste does in the list, with
       **Cancel**, and the choice (and "don't ask again": always add, always
       open) in Preferences. Pasting into a text field is unchanged.
     - Tests: a crate on the clipboard (`application/ld+json` and text),
       the dialog's three buttons, opening (no base, notice), adding with
       the same context, with a context that adds a term, with a clash
       (renamed, cancelled), undo of the add as one step.
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
       reported (Crate > Repair Crate would make it a Thing). Within one
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
       - **Files between attached crates**: pasting File entities copied
         from another attached crate (another window) now leaves them
         missing here, as their files stay in the other crate's folder
         (reported). Offer to copy their files too, into a folder of this
         crate (as Insert > Files does, in the background with progress,
         Replace, Keep Both or Skip for names taken), with their `@id`s
         following the folder chosen; and Datasets with their folders.
     - Enabled whenever Paste is, in the entity list.
  3. **Merge Entities** (*done*: Edit > Merge Entities… and Merge… on the
     right-click menu): select
     two or more entities in the list or a tree and merge them with the
     dialogue of renaming to an existing `@id` (`Crate.merge`, one undo
     step), choosing which `@id` stays. For duplicates found later, e.g. a
     pasted person who was already there under another `@id`. Two of
     different types may be merged (their types combined); not offered
     for more than two whose types differ, as one of them is likely
     selected by accident.
  3b. **Pasting into a type's folder** (Entities by Folder or by Part):
     pasting one entity with an absolute `@id` (e.g. `https://…`) copied
     from this crate onto a type's folder, e.g. Person, adds that type to
     it, rather than pasting a copy; several only if they all have that
     type as an explicit shared type already (else it is likely a
     mistake). After Cut rather than Copy, the type of the folder it was
     cut from is replaced by this one: a change of type rather than an
     added one. Consider whether this belongs in Paste Special instead,
     and what to do for `#` and `_:` entities, which a plain paste copies
     with new identifiers.
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
  Crate > Repair Crate turns other forms of SPDX addresses
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
- **Insert menu** *(done, see Menus)*: next, datasets and other kinds of
  entities, e.g. a DataCite dataset or software by DOI as a Dataset or
  SoftwareSourceCode (see the DataCite types TODO), and a file or folder.
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
  - **Moving the six built-in lookups behind it** (looked at 2026-11-12):
    not a quick change, as each is a lib class plus hand-written UI wiring,
    and they differ where an interface would need them alike:
    - lib (`no.s11.wpsld.lookup`): the service (online search, or the
      bundled list filtered as you type), a result record of its own
      (`GeoNames.Place`, `ORCID.Person`, `ROR.Organization`, `DOI.Work`,
      `SPDXLicenses.License`, `PRONOMFormats.Format`), `idOf`, `describe`
      and `repair`. `search` signatures differ (GeoNames takes the username)
      and `describe` takes four kinds of helpers (lookup, `newEntity` by
      type, by type and id, a `newGeometry` supplier).
    - ui: a finder dialog each, with its own extras (a map for places,
      the name to write for ROR, abstracts for DOIs, licence flags);
      `EntityEditor` with an `add…` method each, the New ▾ items and when
      they show (by range: Place, Person, Organization; by field name:
      `license`, `encodingFormat`, `citation`…), header rows (Location,
      ORCID, ROR, DOI), the DOI typed into Citation, the PRONOM media type
      added to the file; `CrateEditor` with the wiring, settings
      (GeoNames' username, Preferences tab), test stubs, and Repair Crate's
      report sections; the Insert menu (`EntityEditor.Lookup`).
    Steps that each stand alone, in order:
    1. In lib, a `CrateAccess` (find by `@id`, add of a type with an id or
       its own) passed to every `describe`, and a common `Found` view of a
       result (`@id` IRI, label, details, optionally a point), so results
       can be listed alike.
    2. A `LookupService<R>` interface in lib: id and name ("Organization
       from ROR"), the type it adds, which fields it serves (by range or
       name), `idOf`, `search`, `describe(Entity, R, CrateAccess)`,
       `repair(Crate)` (default none), whether it works offline, settings
       it needs. The six implement it; `module-info` says `uses` and
       `provides` (a module may do both), and the Insert menu, New ▾ items,
       header rows ("Find in …" plus a link when `@id` matches `idOf`) and
       Repair Crate loop over `ServiceLoader.load(LookupService.class)`.
    3. A second SPI in ui, `LookupDialog`, for a richer finder of a
       service by its id (the map, ROR's names), else a generic finder of
       a search box, the `Found` list and details. A plugin with no
       JavaFX of its own gets the generic one; one with a dialog needs a
       ui plugin module too (`requires javafx.controls`).
    4. The special cases stay hand-written, behind small hooks: the map,
       the DOI typed into Citation, PRONOM's media type on the file.
    Only then plugin JARs from a folder (module layer), as above.
- **Plugins for files' metadata readers** *(TODO, consider)*: the same
  mechanism for what reads files' own metadata (`EmbeddedMetadata`, and
  `PdfMetadata` in ui as PDFBox needs java.desktop): an interface in lib
  (which files it reads, by start bytes or media type; `read(path)` giving
  `EmbeddedMetadata.Found`s), the built-in readers behind it first, and
  plugin JARs for formats whose library has a licence wpsld shouldn't
  carry or is large: JPEG 2000 with jai-imageio-jpeg2000 (its JJ2000
  licence), perhaps audio tags, scientific formats (NetCDF, HDF5 and FITS
  attributes), or Microsoft's old binary Office files with Apache POI. A
  plugin that is installed separately keeps its licence out of wpsld's
  release.
  The jlink image is only the boot layer: modules not linked into it can
  be loaded at run time into a layer above it, also wpsld's own, shipped
  in a `plugins/` folder of the release zip beside the runtime (jpackage
  `--app-content`), not linked. So keep the linked image minimal (the
  JDK modules, JavaFX, wpsld's lib and ui with the interfaces, Jackson)
  and let plugins bring the rest: the metadata readers (metadata-extractor
  and xmpcore, PDFBox, jHDF), perhaps Tika. A library without
  module-info, e.g. PDFBox, then stays an automatic module there rather
  than being merged into wpsld.merged.module, whose generated module-info
  missed java.xml and java.desktop (tried 13 November 2026: PDFBox in
  such a layer reads a PDF; see BUGS.md). The boot layer must have the
  JDK modules plugins need (they can't add any; e.g. java.desktop, there
  for JavaFX, and java.sql and java.xml), and plugins mustn't bring
  packages the boot layer has (e.g. commons-logging goes with PDFBox).
  JDK modules to link for the plugins planned (metadata readers, lookups,
  offline data; not visualisation, so no javafx.web, javafx.media or
  javafx.swing): as now java.base, java.desktop (PDFBox, FontBox,
  commons-lang3, image I/O), java.xml, jdk.zipfs, java.net.http,
  java.logging, java.prefs, java.datatransfer; and, by jlink's
  `--add-modules` as nothing in the image requires them, jdk.unsupported
  (`sun.misc.Unsafe` of lz4-java and compress-lzf, which jHDF uses, and
  of pdfbox-io), java.sql (Tika's `TikaInputStream`, libraries' dates)
  and jdk.charsets (SPSS's, Stata's and IPTC's encodings beyond the
  common ones). Not java.compiler, java.scripting, java.management,
  java.naming or jdk.jfr until a plugin needs one. JavaFX's 3D classes
  are in javafx.graphics, so they stay. Check each plugin's needs with
  `jdeps --print-module-deps`.
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
- **A type selector above the entity list** *(done, as a drop-down in all
  styles)*, showing only entities of the chosen type, its subtypes included
  (e.g. CreativeWork also shows Dataset and File), with how many there are
  of each. Offered are the types the crate's entities have, and those two
  or more of them are kinds of, except Thing. Choosing it again lists all
  types when an entity is added or shown that is not of it, or the entity
  edited stops being of it. Still to do: tabs in the OS/2 Warp style.
- **The entity list sorted** *(done)*: the root data entity first, then by
  types (as listed), name (else `@id`, as shown) and `@id`, ignoring case
  and in the user's language's order (`EntityListing` in lib). Sorted again
  at each undo point (leaving a field, adding, renaming), not while typing,
  so the entity edited doesn't move under the cursor; the selection is
  kept.
- **Logical and physical listings** (a tree, next to the flat list by
  type), proposed after sorting the list. The physical one is *done*, as
  View > Entities by Folder (`EntityTree` in lib, `EntityTreeView`), with
  icons by type (`EntityIcons`, Fluent UI, for the major schema.org types,
  coloured by family) also in the list; entities with a path that are not
  data entities are greyed out at their path; an entity of several types
  is in the folder of each; parts of files (`page.html#table2`) are under
  their file. *Done*: Crate > Add Dataset for Folder and Add to Has Part
  (also on the right-click menu) bring greyed out folders and files into
  `hasPart`. The logical one is *done* too, as View > Entities by Part
  (`EntityTree.logical`), and Open Folder, attached and detached crates,
  Insert > Files with copying, the folder watched, Update File Details,
  files not found and web availability marked, and safe saving. Next:
  pasting file paths and drag and drop (see Adding files from the
  operating system); relinking files that were moved (a missing file's
  entity to a file of the same name and size elsewhere in the folder);
  the
  Workplace Shell's look in the Warp style (lines joining the tree,
  hatched icon when selected); a header with the selected folder's path
  and counts.
  - *Logical*: the root's `hasPart` hierarchy of Datasets and their parts,
    which may include entities with absolute URIs or in other folders; an
    entity can appear under more than one Dataset. Contextual entities
    below, in virtual folders by class ("Persons", "Organizations"…).
  - *Physical*: the relative `@id`s broken down by path segment, with
    folders even where there is no Dataset entity for them (shown as
    such, to add one); data entities with absolute URIs in `hasPart`
    under "External resources", sorted by URL without a breakdown.
  - Open questions: where contextual entities go in the physical view;
    entities neither in `hasPart` nor contextual (e.g. unlinked data);
    cycles in `hasPart`; files on disk not described (with Open Folder).

- **Opening a folder for annotation**, with or without an
  `ro-crate-metadata.json`: the whole folder opened as a crate, which
  needs a way to fill `hasPart` with its existing folders and files (and
  later to notice new ones). Related to opening zipped crates.
- **Previewing files** of data entities, e.g. images, text and tables:
  only for an attached crate (opened from or saved to a folder), whose
  relative `@id`s are files beside it.

### Done: labels of properties outside the profile

Loading the crate of
https://www.rohub.org/3b9d1622-2ff1-4a08-8e4a-9fe002f47180?activetab=overview,
a property outside the profile, `https://w3id.org/ro-id/rohub/model#has_version`,
got the label "Https://w 3d.org…"; an IRI is now named by its local name
after `#` or `/`, with `_` between words ("Has version"). The same crate
nests entities in `hasPart` (`{"@id": …, "@type": "Dataset"}`), which are
now flattened as it is read, writes types as IRIs (`http://schema.org/Person`),
now named as the context names them in the entity list, and has
properties outside the types with several values, now one row each.
Still to do: Repair Crate could write such types by their names.

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

### Adding files from the operating system

Insert > Files copies files from elsewhere into an attached crate's
folder (`FileCopy`, `CopyWindow`) and adds them. Next ways in:
- **Paste file paths**: Edit > Paste in the entity list or tree with files
  copied in Explorer or Finder (the clipboard's file list, or text that is
  paths), adding those in the folder and copying the others, as Insert >
  Files does.
- **Drag and drop** files and folders from Explorer or Finder onto the
  list or tree; onto a folder of the tree to copy them into it. Folders
  copied with their contents, which FileCopy doesn't do yet.
- Moving rather than copying (with Shift, as file managers do), and
  copying folders.
- **Copy and paste of files**: copying a File entity from one crate and
  pasting it into another (Edit > Copy and Paste, as entities are copied
  now) should also copy the file itself into the other crate's folder,
  when both are attached: into the folder pasted in (or chosen), with
  FileCopy's window, conflicts and checks, the pasted entity's `@id` its
  new path. For a Dataset it is unclear: copy the whole folder with its
  files (possibly big), only the Dataset and those of its parts also
  selected, or ask; decide when there is a use for it. Within one crate,
  pasting a File is a second description of the same file, so nothing to
  copy.
- **Drag and drop within the tree, into a folder**: in Entities by
  Folder, drop files or folders (several selected) onto a folder to move
  them there: on disk in an attached crate (asking first, as it changes
  the user's files; Replace, Keep Both or Skip for names taken), and their
  `@id`s renamed with the links to them, and the parts moved to that
  folder's Dataset (`hasPart`). For files not yet in the crate, only on
  disk. In a detached crate, only the `@id`s. In Entities by Part, drop
  onto a Dataset to make them its parts instead (no files moved).
  Undoable as one step, though moving files back on disk would need its
  own undo.

### File formats beyond media types

`FileFormats` finds a file's media type by content (Apache Tika, plus
wpsld's own signatures for HDF5 after a user block, NASA CDF, GRIB, BUFR
and Arrow) and links PRONOM where one format fits both media type and
extension. Next:
- **EDAM formats** for life sciences, which have no media types (FASTA,
  FASTQ, SAM/BAM, VCF, PDB, mmCIF): Bioschemas and Workflow RO-Crate use
  EDAM's format IRIs (e.g. `http://edamontology.org/format_1929` for
  FASTA) in `encodingFormat`; link those by content or extension.
- **PRONOM identifiers by content**, which tell versions apart (PDF 1.4
  from PDF/A): a small matcher of DROID's signature file (V125 has 2,273
  internal signatures, byte sequences at offsets from the start or end,
  with gaps and byte ranges; no dependencies needed), container
  signatures for Office and ZIP-based formats later. DROID itself as a
  library brings about 48 MB of dependencies and no modules.
- **Choosing a media type by hand from IANA's registry**: bundle IANA's
  media types (https://www.iana.org/assignments/media-types/, as XML or
  the CSV per top-level type; about 2,350 current types, each with its
  template and references) and offer them in the `encodingFormat` field
  as suggestions, with Tika's readable names where it has them (about
  500 of its 1,700). Warn about a media type that is neither registered
  at IANA nor an `x-`, `vnd.` or `prs.` one (e.g. a typo, or one made
  up). Note PRONOM has only 191 of IANA's types.
- **Biomedical and microscopy formats**: check detection of OME-TIFF
  (TIFF with OME-XML in its description, `image/tiff` now), OME-Zarr
  (a folder), Zeiss CZI (`ZISRAWFILE`), Leica LIF, Nikon ND2, Olympus
  OIR/VSI, Imaris `.ims` (HDF5), NIfTI and Analyze, MRC, DICOM series,
  EDF, BrainVision; add signatures where they are simple, and find their
  PRONOM and EDAM entries. Bio-Formats (Java, from the OME project) knows
  most of these, but check its licence (GPL for most readers) and size
  before considering it; its format list is a good reference either way.
- **EDAM formats chosen by hand** too, like PRONOM's Format from PRONOM…:
  a finder of EDAM's formats (about 600 under `format_1915`), with their
  names, synonyms and definitions. EDAM is published as OWL (RDF/XML),
  and its releases also have CSV and TSV tables (check:
  https://github.com/edamontology/edamontology/releases), which can be
  read without Jena; else the OWL's few elements wpsld needs (classes,
  labels, synonyms, subClassOf, deprecation) can be read with the JDK's
  XML parser. Bundle a subset (formats only) as JSON, as for PRONOM.
- **Format identifiers besides PRONOM**: Tika's media types carry links
  for 64 types, some to the Library of Congress's format descriptions
  (e.g. FITS, `https://www.digitalpreservation.gov/formats/fdd/fdd000317.shtml`),
  which could be `encodingFormat` links where PRONOM has none; and
  Tika's readable names (493 of 1,693 types) for the format's entity.
- **More signatures** where Tika has none and a media type exists or is
  common: NumPy `.npy` (`\x93NUMPY`), Avro (`Obj\x01`), NIfTI (`n+1` at
  344), ROOT (`root`), MRC maps (`MAP ` at 208).

### Crates as ZIP, BagIt and on the web

- **Open from the web, by dereferencing**: File > Open URL… (done: a
  window modal to the editor with Cancel while it is fetched, so that the
  crate being edited can't change and is never swapped out unasked, and
  that closes before the replacement, which can't be cancelled; a notice;
  no recent files yet, and the failures shown
  as the one message of the exception, not yet as every step tried), for
  a crate's URL, DOI (`https://doi.org/…`) or other persistent
  identifier. `CrateRetriever` already follows
  FAIR Signposting (`Link: <…>; rel="describedby"`), content negotiation
  (`Accept: application/ld+json;profile="https://w3id.org/ro/crate"`),
  `ro-crate-metadata.json` beside a landing page, and HTML previews
  linking their metadata; it limits sizes and requests, as all it fetches
  is untrusted. Opened read-only, with its relative `@id`s resolved
  against its web base (next item), to save locally with Save As. Also an
  RO-Crate zip at a URL (below), downloaded to a temporary folder.
- **A crate on the web: attached or detached, and its web base**
  *(done for a crate opened by File > Open URL…: `WebCrate` in lib,
  `CrateFile.web()`, `EntityPreview.setWeb`, the notice, and the Save As
  warning with `Crate.absolutize`, tested in `TestWebCrate` and
  `TestCrateEditorWebCrate`; still to do: the checks, the TUI and CLI,
  Export as ZIP and copying entities, `Content-Location`, and choosing
  among a record's items, see the list below this one)*. What RO-Crate 1.2 says
  (`structure.md`, "Attached RO-Crate Package", "Detached RO-Crate
  Package"; `appendix/relative-uris.md`):
  - An Attached RO-Crate Package "can be stored and published in multiple
    ways", among them "exposed as a Web resource within a folder
    structure"; its metadata document "MUST be present in the RO-Crate
    Root and it MUST be named `ro-crate-metadata.json`", and "the use of
    relative URI references in the RO-Crate Metadata File ensures
    identifiers of data entities work as they should" once its root
    folder is on a web server. So a crate fetched from
    `http://example.com/crate1/ro-crate-metadata.json` is an attached
    crate whose RO-Crate Root is `http://example.com/crate1/`, and its
    `hasPart` `file.txt` is `http://example.com/crate1/file.txt`.
  - A Detached RO-Crate Package has no root folder; its metadata "is
    accessed independently (e.g. as part of a programmatic API)", should
    be named `${prefix}-ro-crate-metadata.json` if it is a file, its root
    "SHOULD have an `@id` which is an absolute URL if it is available
    online", and "any data entities ... MUST be Web-based Data Entities"
    (absolute URIs); `#` local identifiers are still allowed for
    contextual entities.

  **Deciding**, in lib (e.g. `CrateRetriever.Retrieved#packaging()` or a
  small `WebCrate` record beside it, so the TUI and CLI can use it too),
  from `Retrieved.source()`, the URL the metadata document finally came
  from *after all redirects* (not the URL the user typed, which may be a
  DOI or a w3id.org redirect):
  - the last path segment is `ro-crate-metadata.json` (or RO-Crate 1.0's
    `ro-crate-metadata.jsonld`, as `CrateFile.isMetadataFile`): attached;
    web base = the folder, `source.resolve("./")`, with query and
    fragment dropped (a query means an API: detached, below);
  - the path ends with `/` and the server answered with the crate itself
    (content negotiation on a folder, as some servers do for
    `index.html`/`ro-crate-metadata.json`): attached, base = that URL;
  - anything else, e.g. `my-crate-ro-crate-metadata.json`, LDaCA's
    `/api/object/…?meta=original`, ROHub's API, a DOI that negotiated
    straight to JSON-LD at a record URL: detached, and **with no base**:
    the address of the document says nothing of where files are (KU
    Leuven's export has `Calibration.Trial26.enf`, which is not at
    `https://rdr.kuleuven.be/api/datasets/Calibration.Trial26.enf`: the
    DOI has gone from the path), so JSON-LD's way of resolving against the
    document's address is *not* used, whatever the address; services
    mangle relative paths, which is why attached or detached is decided
    first. Only the context's `@base` gives such a crate a base;
  - a `file:` URL is not a web crate: as now (`CrateFile`, by name).
  - An `@base` in the document's own `@context`
    (`JsonLdContext.getBase()`) wins over the URL for resolving, as
    JSON-LD says; note it in the steps shown.
  - `Content-Location` headers are ignored (rarely sent, and JSON-LD
    1.1 uses the document URL); revisit if a repository needs it.
  - Record the decision and why in `Retrieved.steps()`, e.g. "attached:
    named ro-crate-metadata.json at http://example.com/crate1/", so the
    window (and `wpsld-tui`'s status line) can say where relative paths
    lead.

  **Semi-detached** *(done: the notice; to do: the rest)*. A detached
  crate with no `@base` that still has relative data entities (File or
  Dataset with a relative `@id`: `Crate.unanchoredDataEntities`) is neither
  fully detached (all absolute, as the specification wants) nor attached
  (a folder to be relative to): the paths are most likely those of other
  items of the same record, or of files inside an archive that is one of
  its items, but there is no telling which. Such crates come from
  repositories' exports, such as Dataverse's, and must open. Done: the
  notice warns that it is neither, how many files and one example, that
  they are not linked, and what its RO-Crate Root could be
  (`WebCrate.suggestedRoots`), with the reason for each: the **landing
  page** the metadata was found from (its address after the redirections,
  `Retrieved.landing()`); the root's own `@id`, `identifier` and `cite-as`
  when they are web addresses; and the folder beside the document, when
  its address has no query. **Never an address earlier in the
  redirections** that led there: not the DOI or Handle asked for
  (`Retrieved.asked()`), nor any persistent identifier resolver's address
  (`doi.org`, `hdl.handle.net`, `w3id.org`, `purl.org`, `identifiers.org`,
  …), as they lead to the landing page and are not where files are.
  Choosing among them is a heuristic, so it is the user's to do, *unless*
  it can be checked: `RootProbe` (done) does one HTTP `HEAD` for the first
  file of the root's `hasPart` (a `File` before a folder) beside each
  candidate (four at most), and a `HEAD` of a made-up path that must not be
  answered 200 (a server that has a page for any path says nothing); only if
  the file is there is that candidate taken, in the background after
  opening, saying so (`CrateEditor.findRoot`). Otherwise the user is left to
  choose. **Not finding the files is not finding that there is no root**:
  they may be inside a zip file (or other archive) among the record's items
  that has not been looked into.
  - **A root inside an archive** *(TODO)*: each `item` of the record that is
    a zip (from the `Link` headers and the linkset, see Signposting) is
    another candidate, "inside <name>", with the arcp base of the zip (see
    "A zip, from disk or from the web"), and it is not taken without the
    user. It can be checked without downloading the whole zip: a zip lists
    its entries at its end, so an HTTP `Range` request for the last 64 kB
    (`bytes=-65536`, and the next range if the central directory is
    longer) gives the names of the entries (`206`; servers without ranges
    answer `200` with the whole file, which is then not read), and the
    first file of the crate, as a path in it (also under the zip's single
    top folder, as the specification allows), is there or not. The zip
    can be big, so a zip's size is shown and a download is the user's
    choice.
  - **Choosing a root where it can't be determined is attaching the
    crate** *(done for a folder and a web address, 2026-11-19: `AttachDialog`,
    `Attachment`, `CrateFile.attach`, Crate > Attach… and Detach, the notice's
    Attach… button, and Check Web Availability asking the relative files
    at the web base through `WebAvailability.targets`; the zip is still to
    do; not yet remembered with the crate in the state folder)*: **Crate > Attach…**, also as the button of the notice that
    warns of a semi-detached crate (`showNotice` takes an action), says
    what the relative `@id`s are relative to. The target is one of:
    1. **a folder** on disk (a directory chooser), e.g. the crate's files
       downloaded and unzipped by hand, or kept beside it by another tool;
       the crate is then attached to it as one opened from a folder is
       (`CrateFile.folder()`: the files listed in Entities by Folder, the
       links, adding files, the checks), after showing how many of its files
       are there and how many are not, as Save As to a folder does
       (`CrateFolder.match`), and not accepted if none are, unless the user
       insists;
    2. **a web address**: a field, with the suggestions of
       `WebCrate.suggestedRoots` to pick from, each with why, and a Check
       that runs `RootProbe` on it and says if the first file is there (not
       there is allowed: the user may know better, with the warning that
       links may then lead nowhere); the crate keeps it as its web base
       (`WebCrate.withBase`), the JSON unchanged, and the Save As warning
       applies to it;
    3. **a zip** *(once zip support is there, see "A zip, from disk or from
       the web")*: a zip file (chosen on disk, or one of the record's
       `item`s that is one), and then **the folder inside it** that is the
       root, from the zip's entries (the top of the zip, or its single top
       folder, or another folder the user picks in a tree of the entries),
       with the arcp base of the zip; the crate is attached to that folder
       of the zip file system, read-only.
    **None** (Crate > Detach) puts it back to detached. Attaching is how
    the crate is *looked at*, not an edit of it: it is not an undo step and
    changes no `@id` (so it differs from Crate > Make Attached…/Make
    Detached… below, which rewrite `@id`s between relative and absolute,
    and move or fetch files); it is remembered with the crate (the state
    folder, as recent files are), not written into it. The existing
    **Attached to Folder** toggle (for a crate opened from a file whose
    files are in its folder or not, see "Attached and detached crates") is
    the folder case of this, so it becomes this: the menu has Attach… and
    Detach, and says in the window's notice and the Crate menu what the
    crate is attached to, rather than a checkbox that can only be a folder.
    Opening a crate from the web ends semi-detached, as it is not known
    where its files are, until the user attaches it, or `RootProbe` finds
    them (above).
  - **use what the crate says about its files**: each File of such an
    export has its own `url` or `contentUrl` (KU Leuven's: one or two
    addresses, `https://rdr.kuleuven.be/api/access/datafile/129354`, for
    two versions of the file), so the → beside a relative `@id` should open
    that, as in an attached crate whose files are also on the web
    ("Data entities in an attached RO-Crate that are also on the web"),
    rather than nothing; several are listed in the preview;
  - **match the paths to the record's items**: with Signposting's `item`
    links (and the linkset's), a relative `@id` may be the name of an
    item (`Calibration.Trial26.enf` at `…/files/Calibration.Trial26.enf`),
    or a path inside the one archive item (see the zip item below, with the
    arcp base): offer these as suggestions for the root as well, not
    taken without the user's choice;
  - relative `@id`s that are not paths at all (Dataverse's contact names,
    `van der Have, Tuur`, as the `@id` of a Person) are not files: they
    don't count, as only File and Dataset entities do, and a repair could
    offer to make them local (`#…`) identifiers;
  - **the array `@id`** of an entity in the same export (see Dataverse).

  **Keeping it**: `CrateFile` gets `Optional<URI> webBase` (and the URL
  asked for, `Optional<URI> openedFrom`, for the title, messages, recent
  files and the persistent identifier below), set by `openWeb(Retrieved,
  JsonNode)`, cleared by `open`/`openIn` and by a Save As to a local file
  (after the warning below). A web crate has no `folder()`, `path` is
  empty (Save becomes Save As), and `isAttached()` stays about a *local*
  folder; add `isAttachedOnWeb()` for the decision above. The JSON is
  not changed: no `@base` is added, the `@id`s stay relative as written,
  so an unchanged crate saved is the crate as published.

  **Using it** (the → beside a reference, the ↗ beside `@id`, and the
  preview's links, all through `EntityPreview.address(String)` which
  `EntityEditor.setAddress` already uses):
  - a relative `@id` (not `#…`, not `_:…`; `./` included) resolves as
    `webBase.resolve(id)` and opens in the browser, e.g. `file.txt` →
    `http://example.com/crate1/file.txt`, `data/` → the folder's URL;
    percent-encoded ids (`my%20file.txt`) stay encoded;
  - for an attached web crate, not outside the root: `../other` resolves
    to no link, as `local(...)` refuses paths outside the folder now, and
    `CrateFolder`'s `rocrate:attached-outside` check applies to the URL
    paths the same way;
  - for a detached web crate, no link for a relative `@id`, unless the
    context has an `@base` (done: `WebCrate.base()` is empty);
  - absolute `@id`s and `#`/`_:` ids as now. No HEAD requests just to
    show a link; checking that files are there is Check Web Availability
    (see "Attached and detached crates").
  - `wpsld-tui` shows the resolved URL beside a relative `@id` (it
    cannot open a browser everywhere; copying is enough), and
    `wpsld preview` of a web crate writes links the same way.

  **Checks**: a detached web crate with relative `File`/`Dataset` `@id`s
  gets a REQUIRED issue ("Detached crate: data entities must be
  web-based", spec quote, the resolved URL as the suggested fix), with a
  repair that absolutizes them (below). An attached web crate gets
  `CrateFolder`'s checks except "missing": files can't be listed over
  HTTP, so missing files are for Check Web Availability.

  **Saving it locally** (Save As, the only way to save a web crate):
  before writing, if the crate has a web base and any relative `@id` of a
  data entity (or one referenced from `hasPart`, or the root `./`), warn
  in a dialog of the usual style, listing up to five ids with where they
  lead now, e.g. "`file.txt` → http://example.com/crate1/file.txt": *"N
  identifiers are relative to http://example.com/crate1/. Saved as
  `<target>`, they will name files beside it instead, which wpsld does not
  download, and the links to the web copies are lost."* Buttons:
  - **Make Absolute** (default): one undo step (`UndoHistory`, as
    renames are), rewriting each relative `@id` of a data entity (and
    every reference to it, as `Crate` renames do) to `webBase.resolve(id)`,
    leaving `#` ids alone (they stay local, as the appendix allows); the
    root `./` stays `./` when saved as `ro-crate-metadata.json` (attached
    locally, with web-based data entities, which RO-Crate allows) and
    becomes the base URL when saved under another name (detached);
    where the root has no `identifier`, the base (or the persistent
    identifier it was opened by) is offered as `identifier`/`cite-as`, as
    "Keep the persistent identifier" says, and, as the appendix suggests
    for a fork, `isBasedOn` if the user goes on to change it;
  - **Keep Relative**: save as is, e.g. to download the files by hand
    next to it; the crate then becomes attached to the local folder (when
    named `ro-crate-metadata.json`), its `@id`s naming files there;
  - **Cancel**.
  After either save the web base is dropped: the crate is now the local
  file's. The same warning for Export as ZIP, and for copying entities
  to another crate (`EntityClipboard` already writes `@base`, which
  should be the web base for a web crate, so pasted relative ids become
  absolute there).

  **Tests**: lib, with the JDK's `HttpServer` as `TestCrateRetriever`
  does: `…/crate1/ro-crate-metadata.json` → attached, base
  `…/crate1/`; a redirect from `/doi/x` to `/crate1/ro-crate-metadata.json`
  → base after the redirect; `/crate1/` answering JSON → attached;
  `/api/object?id=…` and `x-ro-crate-metadata.json` → detached; an
  `@base` in the context; `../escape` → no address. ui: TestFX for → on a
  relative `hasPart` opening `http://…/crate1/file.txt` (a recording
  `browse`), and the Save As warning's three choices (undo restores the
  relative ids). Help: a paragraph in the tutorial on opening a crate from
  the web, with its screenshot (see AGENTS.md).
- **Has the file diverged from what we edited? A checksum of the crate's
  metadata that ignores how it is written, and a lineage of the files we
  have seen** *(TODO, investigated 2026-11-19; to be built)*. The problem:
  Save must not overwrite a `ro-crate-metadata.json` that is not ours without
  saying so, and must not ask when it is. It arises when a crate that is
  open (with unsaved changes) is saved over a file changed on disk by
  another program (as now, `CrateFile.disk()` compares the file's time,
  size and SHA-256 with what it was when opened or saved), and newly
  when **a crate fetched from the web is attached to a folder that already
  has a `ro-crate-metadata.json`** (Attach…, above): is that the crate we
  opened, saved by another tool, or another crate altogether? The idea:
  keep the checksum of every state of the file we have read or written, as
  marks on the undo history (`UndoHistory`: the metadata document as fetched
  or opened, and as saved by us at each Save), and at Save compare the
  file on disk with them: **one we know is not diverged** (even if we have
  since undone, as the file is then ahead of the crate, not different), so
  Save goes on without asking; **an unknown one has diverged**, and the user
  chooses (as `askBeforeOverwriting` does): save over it, Save As, reload
  from it (losing changes), or Cancel, saying whose it is (its name and
  `dateModified`, and how many entities it has, from reading it) and
  not only that it differs.
  What to checksum: the bytes' SHA-256 are exact but a different tool writing
  the same crate (indented differently, entities in another order) differs,
  so a second checksum of the *content*. What was found:
  - **RFC 8785 JSON Canonicalization Scheme (JCS)**: a deterministic JSON text
    (object keys sorted by UTF-16 code units, no whitespace, strings and
    numbers as ECMAScript writes them), made for hashing and signing; it
    keeps the order of arrays and the entities in `@graph`, so it alone
    does not ignore their order.
  - **RDF Dataset Canonicalization (RDFC-1.0, W3C Recommendation 2024, from
    URDNA2015)**: for JSON-LD, converts to RDF quads and gives canonical
    N-Quads that a hash is taken of, whatever the JSON's shape (compact or
    expanded, prefixes, keys, `@graph` order, single or array, blank node
    labels): semantic equality. Java: `com.apicatalog:titanium-rdfc`
    2.0.0 (Apache-2.0, small: titanium-rdf-api and the N-Quads reader; it
    takes quads, and has a ticker to stop a "poison graph"), with
    `titanium-json-ld` 1.7.0 (`2.0.0-M2` is a milestone) and a Jakarta JSON
    implementation to make the quads; Jena 6.2.0 has no RDFC. Data
    Integrity's `eddsa-jcs-2022` and `eddsa-rdfc-2022` are these two.
  - **Measured** (a Python prototype, `pyld` for URDNA2015, on the example
    crate, WorkflowHub's Workflow RO-Crate 1.0 (126 entities) and KU Leuven's
    (3,989 entities, 1.4 MB)): bytes: every change in the writing is a
    difference. **A JCS of the content with the `@graph` entities sorted by
    `@id` and a one-element array taken as its value**: the same for
    reformatting, keys in another order, `@graph` shuffled and singletons
    wrapped as arrays, different for any real change (a value edited, or the
    **order of a value's array**, which is kept as authors' and parts' order
    mean something to people though JSON-LD calls arrays unordered); 0 ms
    for the small crates and 23-48 ms for KU Leuven's, with no network, and
    **it works on an invalid crate** (KU Leuven's has an `@id` that is an
    array). RDFC: the same for all of these *and* for value arrays
    shuffled; 0.3-0.6 s for the small crates and 1.8 s for 28,451 quads of
    KU Leuven's (Python), but **it cannot be taken of an invalid
    crate** (`Could not convert input to RDF dataset`, that `@id` again),
    and it fetches the `@context` (the RO-Crate contexts are bundled in
    wpsld, so a loader for them).
  - **So**: the lineage uses the JCS-of-content checksum (call it
    `CrateChecksum`, in lib, over the `JsonNode` as `Crate.toJson` and the
    file's parse give it; the bytes' SHA-256 as the quick first check, as now),
    not RDFC, which is for a later "same data, written differently"
    (compare two crates as graphs: Detect when a crate and a published one
    are the same, or what changed) where its cost and its need for valid
    JSON-LD are worth it. JCS needs ES number formatting for numbers that
    are not integers (rare in a crate; `Double.toString` differs, so write
    them as ECMAScript does or leave a note that such a crate is "diverged"
    when in doubt: a false difference only asks, a false equality would
    overwrite). The checksums are not written into the crate.
  - Marks on the undo history rather than a set beside it, so that Undo and
    Redo past a Save keep knowing which state the file is: the step of
    each Save and of opening has the file's two checksums. A new
    `CrateFile.disk()` (SAME, CHANGED, MISSING) becomes: SAME if the file's
    bytes or content checksum is one of the marks, else CHANGED, and Save
    for a crate with no file of its own (fetched from the web, attached
    to a folder with a metadata file in it) asks as for CHANGED, rather than
    sending the user to the file chooser's overwrite question. Opening a
    crate from the web marks the checksums of the document as fetched
    (the bytes in `Retrieved`), so that a copy of the same crate unpacked by
    hand into the folder is recognised as the same starting point.
- **A zip, from disk or from the web: its arcp base and a viewer of its
  files** *(TODO, designed in outline; one mechanism for a zip chosen in
  File > Open, a zip found by Signposting, and `.eln`)*. A crate in a zip
  is an attached crate whose folder is inside the archive, so its relative
  `@id`s need a base that maps to the inside of the zip, as RO-Crate's
  "Establishing a base URI inside a zip file" has it: the arcp scheme,
  `arcp://uuid,<uuid>/`, with the UUID **name-based (version 5, SHA-1) from
  where the zip was loaded from**: `file:///home/…/crate.zip` for a file,
  the address it was downloaded from (`https://…`, after redirects, with
  its query: `…/ro_crate?version=5`) for the web, so that opening the same
  zip again, from the same place, gives the same base, and a copy
  elsewhere another. (`java.util.UUID.nameUUIDFromBytes` is version 3,
  MD5, so write version 5: the namespace and the exact string to hash are
  to be taken from the arcp specification, https://w3id.org/arcp, and
  tested against its examples; `no.s11.wpsld.arcp` has `arcp:` paths and a
  file system, mostly stubs, see Storage backends.) The base is kept in
  `CrateFile` as the web base is (not written into the JSON, or only as the
  `@base` of the context when copying entities, as `EntityClipboard`
  does), `WebCrate` is not used for it: a zip's `@id`s lead into the
  archive, and a zip fetched from the web leads nowhere there.

  There is no "Open Folder" for an archive, so the zip is opened with the
  JDK's zip file system (`jdk.zipfs`, `FileSystems.newFileSystem`) and wpsld
  gets a viewer of its own for what is in it, in Java: iterating the
  entries as `Path`s of the zip file system, which `CrateFolder` (what the
  crate describes and what the folder has, Entities by Folder), the checks
  and the file metadata readers already take (they take a `Path`, so
  check each that reads only; those that need a real file, such as
  external readers, get a copy extracted to a temporary folder, as does
  opening a file in the browser or another program, since a `jar:` or
  zip-file-system URI means nothing outside the process). Whether it can be
  saved back depends on where the zip is: see "What Save does" below. Checks against zip slip, entry
  sizes and counts (zip bombs), names with `..` and absolute paths, and
  duplicate entries; a single top-level folder is the root of the crate, as
  the specification allows. A zip from the web is downloaded to a
  temporary file as a job within size limits (the same Cancel window as
  Open URL while it comes, no swapping out of the crate being edited
  until it is here), then opened as a local zip with the web address as
  its location.
- **What Save does, by where the crate is** *(TODO; the first row is
  done)*. The crate's place decides, and **a crate is never saved to a place
  it can't be written back to, or to a copy that is lost**:
  | The crate is | Save | Save As |
  |---|---|---|
  | a file on disk, or a folder | the file, as now | as now |
  | **fetched from the web** (Open URL, a web base) | **can't write back**: there is no general authentication, so no PUT or POST to a server. Save is never offered as writing there: it is **Save As, as for a new crate** (done: no path, so Save asks where; consider showing the menu item as "Save As…" and saying why in its tooltip, rather than "Save" that opens a chooser), and the web-ids warning applies (done) | a file or folder on disk, the web base dropped |
  | **in a local zip** (opened from disk, e.g. `.crate.zip`, `.eln`) | **in place**, through the zip file system, which can write (`FileSystems.newFileSystem(zip)` and changes written when it is closed; to check: it rewrites the whole zip, so a big zip is a job with progress and Cancel, written to a temporary file beside it and then moved over it, as `SafeSave` does, never half written, and the file changed on disk by another program since it was opened asked about, as for a metadata file) | a zip elsewhere (a copy), or Extract to Folder… |
  | **in a zip that was downloaded** (from the web, in a temporary file) | **never modifies the temporary zip** (which would be lost, in `/tmp` or the state folder): Save first asks where to save *the zip* (a zip file on disk), copies it there and goes on in that copy as a local zip, or extracts it to a folder (below) | the same |
  | a folder inside a zip (Attach… to a folder of a zip, see above) | as its zip | as its zip |
  The window's title and notice say where the crate is (a web address, a
  zip and the folder in it) so that what Save will do is not a surprise,
  and a crate with unsaved changes opened from the web asks before it is
  closed or replaced as any other does (done).
- **Zip handling goes both ways** *(TODO, with the zip item above, for zips
  from disk as much as from the web)*:
  - **Unpack to a folder**: Crate > Extract to Folder… (and the choice when
    a zip is opened or downloaded) writes the zip's files into a folder the
    user chooses (an empty or new one, never into one with other files
    without asking, never outside it: zip slip), as a job with progress and
    Cancel, checking entry sizes and counts as above, and then attaches the
    crate to that folder (as Attach… does) and saves it there as a folder
    crate; the zip itself is not changed or deleted. A single top-level
    folder of the zip is the root: its contents, not the folder, go into
    the chosen folder, saying so.
  - **Turn an open folder into a zip**: File > Export as ZIP… (see "Save as
    ZIP" below, there for an attached crate's folder; here also for a crate
    attached to a folder with Attach…, and for one fetched from the web
    whose files were attached to a folder): the folder's files and
    `ro-crate-metadata.json` into a zip file the user names (`.crate.zip`
    is suggested, as it is what repositories such as WorkflowHub use), at
    the top or inside a single folder as chosen, skipping the hidden files
    and, as it is for sharing, the crate's own `ro-crate-preview.html`
    only if it is the crate's preview and wanted; through a temporary file
    and then moved, never half written. The crate stays open on its folder
    (a copy was made), with the option to open the zip (as a local zip, in
    place).
  - A **BagIt** zip and a BagIt folder in the same two directions (see
    BagIt below).
- **Save as ZIP and open ZIP**: File > Export as ZIP… writes an attached
  crate's folder as a zip, `ro-crate-metadata.json` at its top (or in a
  single folder, as RO-Crate allows), with the preview, skipping hidden
  files, through a temporary file as Save does; and File > Open on a
  `.zip` (or `.crate.zip`, `.eln`) unpacks it to a folder of the user's
  choice (or a temporary one, read-only), then opens it as an attached
  crate. Big zips in a window of their own, as copying files is. Check
  zip entries' names for `..` and absolute paths (zip slip), and sizes
  (zip bombs). With the JDK's zip file system (`jdk.zipfs`,
  `FileSystems.newFileSystem(path)`), which reading Office documents'
  metadata would use too (see BUGS.md); the runtime image must include
  it, as it is a service provider.
- **BagIt**: a crate packaged as a BagIt bag (RFC 8493), as RO-Crate
  describes in its implementation notes: `bagit.txt`, the payload in
  `data/` with `ro-crate-metadata.json` there (or at the top, as some
  profiles do), and checksum manifests (`manifest-sha512.txt`,
  `tagmanifest-…`). Recognise a bag when opening a folder, open its
  crate, and check its manifests (in the background), reporting files
  missing or changed. Convert a folder to a bag (Crate > Make BagIt Bag…):
  move the payload into `data/` (asking first, as it moves the user's
  files, and only within the folder), keep `@id`s relative to the crate
  (so the metadata moves with it), write `bagit.txt`, `bag-info.txt`
  (from the crate's name, description, dates, creator) and the
  manifests; and update the manifests on save. Check
  https://github.com/LibraryOfCongress/bagit-java (archived) or write the
  little BagIt needs (it is simple: text files and checksums). A bag
  whose payload is listed in `fetch.txt` (a "holey" bag) is opened as a
  detached crate, fetching nothing. Manifests checked: SHA-512 (as
  RFC 8493 recommends) and SHA-256 (common); others reported as not
  checked.
- **Open URL/PID defensively** *(TODO: the editor shows only the
  exception's message so far)* (many ways to fail): repositories such as
  Zenodo, a common home of crates, link their files with
  `rel="item"` Signposting but without the RO-Crate profile, and a record
  may have several items (zips, JSON, other files): offer them for the
  user to pick, those most likely a crate first (`.crate.zip`, a zip, a
  `ro-crate-metadata.json`), saying what each is (name, type, size). A
  JSON item is hopefully `ro-crate-metadata.json`, but crates are also
  deposited under other names: read it (within limits) and check it is a
  crate (its `@graph` has a metadata descriptor `about` a root dataset)
  before opening it, else say what it is. Every failure says what was
  tried, in the order of "Retrieving an RO-Crate" (`Retrieved.steps`):
  no network, not found, HTTP errors, redirect loops, too large, not
  JSON, JSON but not a crate, a zip without a crate, rate limits, login
  pages.
- **Repositories without Signposting**: LDaCA's collection pages are a
  JavaScript application whose server answers HTTP 404 with an empty page,
  whatever is asked for, and no `Link` headers; the crate is in its API:
  `https://data.ldaca.edu.au/api/object/<arcp id, URL-encoded>?meta=original`
  (an object's own crate; `/api/object/meta?id=` redirects there). Know
  such repositories by their addresses (a small plugin each, as for
  Publish): LDaCA and other Oni repositories, and others found (ROHub,
  WorkflowHub's `/ro_crate` zips), each turning a page's address into its
  crate's.
- **FAIR Signposting: what `CrateRetriever` does, and what is missing**
  *(explored 2026-11-19 against RO-Crate 1.2's "Retrieving an RO-Crate"
  and https://signposting.org/FAIR/, and by trying real repositories)*.
  Done and tested only in its parts (`linkHeader`, `htmlLink`, `folder`;
  `retrieve()` itself has no test in lib, only the editor's, for a
  redirect and a plain document):
  - the order of the specification: redirects without content negotiation
    (a browser's `Accept`, as some PIDs only redirect for HTML), then
    Signposting from the `Link` header (RFC 8288, several headers and
    values, parameters) or `<link>` in HTML, then content negotiation for
    the RO-Crate media type, then `ro-crate-metadata.json` beside the
    resolved address; the first `describedby` link with the RO-Crate
    `profile` wins over a JSON(-LD) one;
  - size and request limits, a loop guard, and `Retrieved.steps()`.

  **`rel="linkset"` started** (2026-11-19: `Linkset`, tested by
  `TestLinksetRetrieval`): when the `Link` header and the HTML give no
  `describedby`, a link to a link set (`rel="linkset"` in either, JSON first
  and `application/linkset` text second, two at most) is fetched once, and the
  `describedby` links of the entries that are about the page are ranked as any
  are; the step is "linkset". **Which entries are the page's**: those whose
  `anchor` is its address as asked, after redirects (also with or without a
  final `/` or a fragment), or (for a link set whose links come with no
  anchor, as in the text format) all; else, as ROHub's is anchored at the
  API address of what the page describes, the only entry that has any
  `describedby` or `item`; never the links of other anchors (Zenodo's files'
  own `collection` links back, another page's metadata). Not yet: the
  `item`s (the zip of a crate, a deposited `ro-crate-metadata.json`), the
  `cite-as` and `license`, a link set merged with the header's links rather
  than tried only when they give none, `Content-Location`, and the
  prober; see below.

  Missing, found by running it on the real thing (the probe is in the
  session notes: `java -cp lib/build/classes/java/main:… Probe <url>`):
  1. **`rel="item"` is ignored**, so WorkflowHub's DOI
     (`https://doi.org/10.48546/workflowhub.workflow.120.5`) fails: its
     only crate is `<…/workflows/120/ro_crate?version=5>; rel="item";
     type="application/zip"; profile="https://w3id.org/ro/crate"`, and its
     `describedby` links go to the landing page itself (a bioschemas
     JSON-LD of the workflow, not a crate). The error then lists part of
     the `Accept` header as an address: `get()` cuts each request key at
     its first space, and the header has spaces (cosmetic bug, fix with
     the failure messages).
  2. **`rel="linkset"` is not followed.** Zenodo's landing page (e.g.
     `https://zenodo.org/records/24059872`) has only `rel="linkset";
     type="application/linkset+json"` in its `Link` header (the record's
     files are in the linkset, RFC 9264, `{"linkset": [{"anchor": …,
     "item": [{"href", "type"}], "describedby": […], "cite-as": […],
     "license": […]}]}`, with other anchors for the files' own
     `collection` links, which are not the record's: use the entry whose
     `anchor` is the resolved landing page); plain `application/linkset`
     (the `Link` header's own syntax) too, and a linkset linked from HTML.
  3. **A `describedby` link is taken without checking what it leads
     to.** Zenodo's `describedby` links (`application/ld+json`, also
     `application/json`, DCAT) go to the record's own metadata at
     `https://zenodo.org/api/records/<id>`, which `retrieve()` returns as
     "the crate" (`steps: [Link header]`), and the editor then rejects as
     not an RO-Crate. As the specification's last step says, a candidate
     is only the crate if it is valid JSON-LD with a root data entity (a
     metadata descriptor `about` a root dataset): check each, and if not,
     go on to the next candidate instead of stopping, noting why in the
     steps. DataCite's content negotiation giving its own JSON-LD (the
     specification's tip) is the same case.
  4. **Archives and the choice among items** (the two items above and
     below): the result of retrieving is no longer always a document, but
     one of a document (`source`, `content`, `steps`), an archive to
     download (zip or BagIt, from an `item` with the RO-Crate profile, or
     the only zip, or a `ro-crate-metadata.json` item), or a list of
     choices (`Item(href, type, profile, name, size)`, ranked: the RO-Crate
     profile, then a JSON that passed the probe, then `.crate.zip`/`.eln`,
     then other zips, with a clearer label than the URL) for the editor to offer
     and the user to pick; never opening a file the user has not chosen out
     of several. An `item` of another type (PDF, CSV) is not a crate; one
     that may be JSON is probed (see Decisions), as Zenodo lists the
     crate's `ro-crate-metadata.json` among the files, flagged as data.
  5. **`cite-as` and `license` are not read** (they are what "Keep the
     persistent identifier" below needs): return them with the result, from
     the same response as the document came from, or the linkset's entry.
  6. **Tests of `retrieve()` itself**, against the JDK's `HttpServer`, one
     canned repository for each case (as headers captured from the real
     ones, no network in tests): WorkflowHub's (zip item with the profile,
     `describedby` to the landing page), Zenodo's (linkset only, record
     JSON-LD as a decoy, several items), w3id.org's (content negotiation
     redirect), a folder with no Signposting, a loop, a 404, a page
     that is HTML without links, a JSON that is not a crate. Also the
     `Link` parsing of quoted commas, `anchor`s and relative `href`s.

  **Decisions** (2026-11-19):
  - **Prefer JSON-LD online to an archive**, as it is the faster: so, after
    the links with the RO-Crate `profile` (the specification's own
    preference, either `describedby` or `item`) are put first among their
    own kind, a JSON(-LD) link that the prober (below) shows to be a crate
    comes before an `item` zip, even a zip with the profile. A
    `describedby` that fails the probe (WorkflowHub's, Zenodo's) is
    dropped, and the zip is next. **The JSON-LD may be an `item`, not a
    `describedby`**: a repository such as Zenodo does not know that a
    deposited `ro-crate-metadata.json` is metadata, not data, so it lists it
    with the other files, as `rel="item"; type="application/json"` (or
    `application/ld+json`, or `text/plain`, or no type at all), and only the
    prober can tell. So the JSON candidates are the `describedby` links to
    JSON(-LD) *and* the `item`s that may be JSON (by type, or by a name
    ending in `.json` or `.jsonld`, when the type is missing or generic),
    in one tier, ranked: the profile first, then by name
    (`ro-crate-metadata.json`, `ro-crate-metadata.jsonld`,
    `<prefix>-ro-crate-metadata.json`, `*.crate.json`), then others. A
    record with many JSON data files must not be fetched whole: the named
    candidates are probed first, with the full size limit; if none passes,
    the others, at most five, each read only up to a smaller limit (about
    1 MB, saying "not read to the end" if it was cut), as data files
    seldom start as a crate's `@graph`. Candidates are listed
    from the `Link` header, the HTML, *and the linkset*, and the same
    `href` is tried once however many types it is listed with (Zenodo
    lists its record five times).
  - **Level 2 is needed: traverse the linkset**, not only the headers. A
    `rel="linkset"` link (the `Link` header or `<link>`; Zenodo has only
    that) is fetched with `Accept: application/linkset+json` (RFC 9264;
    also `application/linkset`, the `Link` header's syntax, when a
    server has only that), within the same limits, once (a linkset does
    not lead to another). Its entries are taken for the page when their
    `anchor` is the address of the landing page (as resolved after
    redirects), the address asked for, or its `cite-as`; other anchors,
    such as a file's own `collection` link back, are not the page's. The
    links of the headers and of the linkset are merged and listed once.
  - **Probe each candidate; pick when there is more than one.** A prober
    (`CrateProbe` in lib, with `CrateReader` sharing its list of
    descriptor `@id`s, `DESCRIPTOR_IDS`) answers whether a fetched
    document is an RO-Crate metadata document, cheaply: it is JSON (a
    `Content-Type` of `text/plain` or `application/octet-stream` is
    fine, and HTML is not), an object with an `@graph` array, and the
    array has the metadata descriptor of the specification's "Finding the
    Root Data Entity": an entity whose `@id` is `ro-crate-metadata.json`,
    or RO-Crate 0.2 and 1.0's `ro-crate-metadata.jsonld` (even when the
    file itself is named `<prefix>-ro-crate-metadata.json`, as a detached
    crate's may be), with an `about` that has an `@id`. Its
    `conformsTo` is returned (`https://w3id.org/ro/crate/<version>`,
    any version) but not required, and whether the root it is about
    exists is for `CrateReader` to say, so that the probe stays one pass
    over the `@graph` (streamed with Jackson, stopping at the
    descriptor, within the size limit given for the candidate) and says
    why when it fails ("HTML
    page", "JSON, but not an RO-Crate: no metadata descriptor in its
    @graph", "DataCite's JSON-LD"). The JSON candidates are fetched
    together (a few at a time, and the same limits for each) and
    probed. **One that passes is opened. Several that pass are a choice**
    for the user (two versions of the crate, or its own and a copy's, or
    a `describedby` and a JSON `item` that are the same file at two
    addresses: those with the same content are one, by their checksum).
    **None that pass, and zip (or BagIt) items:** one with the RO-Crate
    profile is the candidate; several are a choice, and a single one
    without the profile is asked about too, as it may be data and big
    (shown with its type and size). The choice window lists each with
    what it is (name, type, size where known, why it is a candidate)
    and never opens one unasked out of several.

  Order to work in: the prober and the tests of `retrieve()` first, as
  they make today's failures say the truth and cost little; then the
  linkset (JSON) and `cite-as`/`license`; then the choice among several;
  then the archives, with the zip handling below.

- **Dataverse** *(checked 2026-11-19)*: Dataverse installations give each
  dataset's metadata in several export formats, one of which can be an
  RO-Crate (`rocrate_json`, in the installation's list of exporters at
  `/api/info/exportFormats`), but it is an optional exporter and very few
  have it on. Of the 151 installations in
  https://github.com/IQSS/dataverse-installations (`data/data.json`),
  108 could be asked (the list of exporters, or else a search for a
  dataset and a try of the exporters' names on it) and **one has RO-Crate
  enabled: KU Leuven RDR, `rdr.kuleuven.be` (Dataverse 6.7.1)**; **DataverseNO
  (`dataverse.no`, 6.11, the only Norwegian one in the list) does not**: it
  lists OAI_ORE, DataCite, Dublin Core, schema.org, DDI, Croissant and
  Dataverse's own JSON, and answers any other exporter's name, an
  RO-Crate's too, as it does a made-up one, with HTTP 403. The other 43
  (older versions, or an API that is not open) could not be checked. Try
  again later: `scripts/` has no tool for it yet (the check is a few lines
  of Python over `data.json`), add one if installations are to be
  followed. What it means for retrieval:
  - Dataverse's landing pages have FAIR Signposting of both levels: a
    `Link` header with `cite-as`, `license`, `type`, a `describedby` for
    each exporter, *and* a `rel="linkset"` to
    `/api/datasets/:persistentId/versions/<version>/linkset?persistentId=…`
    (`application/linkset+json`). The RO-Crate is a `describedby` like the
    others: `…/api/datasets/export?exporter=rocrate_json&persistentId=doi:…`
    with `type="application/json"`, no `profile`, nothing to tell it from
    `OAI_ORE` and `dataverse_json` that have the same type. Today
    `CrateRetriever` takes the first, so DataverseNO's DOI
    (`10.18710/CVQPYG`) gives the OAI_ORE export, not a crate. This is why
    the prober and the ranking above are needed: for Dataverse, a link
    whose address names an exporter containing `crate` (`rocrate_json`) is
    a named candidate and probed first; where there is none (DataverseNO),
    the other JSON `describedby`s are probed only within the limits above
    (five, about 1 MB each, `OAI_ORE` of a big dataset is not), and the
    answer is a clear failure: "no RO-Crate among the 12 metadata formats
    this repository gives; it has: …", rather than opening one that isn't.
  - A crate exported by Dataverse is a *detached* crate with no files
    beside it (RO-Crate 1.1, `./` the dataset, `identifier` the DOI,
    files as `File` entities with Dataverse's download addresses), so
    `WebCrate` takes it as detached whatever its address
    (`…/export?exporter=rocrate_json&…` has a query); its data entities
    should be absolute web addresses already, which the detached checks
    will say.
  - **The trust store, not the server**: in this container (conda-forge
    OpenJDK 25.0.2) Java refused `rdr.kuleuven.be` (`PKIX path building
    failed`) while `curl` and Firefox were fine. The server sends its whole
    chain (leaf, GEANT TLS RSA 1, signed by *HARICA TLS RSA Root CA
    2021*, which `openssl` verifies); the JDK's `cacerts` has only
    HARICA's 2015 roots, and the system's has the 2021 one. So an
    application built on a JDK without the root fails for every site under
    it (many European academic repositories: GEANT TLS is common), and the
    runtime image `jlink` makes carries the JDK's store: check which JDK
    the release workflow builds with and that its `cacerts` has
    `HARICA TLS RSA Root CA 2021` and `HARICA TLS ECC Root CA 2021`
    (`keytool -list -cacerts | grep -i harica`). The failure should say
    the certificate is not trusted by this Java and by whom it was signed,
    not "not found"; whether to use the operating system's store
    (`Windows-ROOT`, `KeychainStore`) is for Preferences, not a quiet
    change.
  - **KU Leuven's crate is not valid JSON-LD**: 3,989 entities, of which
    one, a folder named with spaces, has `"@id": ["New%20Patient%20Classification%201/", "New Patient Classification 1/"]`,
    an array (its name encoded and not), where an `@id` is one string.
    `CrateReader` drops that entity as an error and the editor refuses to
    open the crate (`hasErrors`), and says "has no @id", which is not what
    is wrong. To decide: (a) say what is wrong (`@id` is an array of 2
    strings) in the message; (b) for Dataverse's export, and any crate
    whose only errors are entities that can be dropped, open it with the
    errors shown as warnings, listing what was dropped, rather than not at
    all; (c) take the first string as the `@id` and the others as
    `sameAs`, written back as one (as Repair Crate does for other
    shapes). Tell Dataverse's exporter project when decided (see
    `TestCrateRetrieverOnline`, which expects this one fault).
  - **Integration test**: `TestCrateRetrieverOnline` (`./gradlew
    onlineTest`, not in the build): DOI `10.48804/XES6PY`, as
    `https://doi.org/…` and as `https://hdl.handle.net/…`, through the
    redirects to the landing page, its Signposting (`cite-as`, the
    `rocrate_json` `describedby`, the other exports, the linkset), then the
    crate (RO-Crate 1.1, detached by `WebCrate`). It passes today because
    the `rocrate_json` link comes before the other JSON exports in the
    header and the first of equal rank is taken, not because it is known
    to be a crate: the test then needs the prober to keep passing if
    the order changes.
- **Repositories tried** *(2026-11-19; the first column is how
  `CrateRetriever` fares today, from the DOI, Handle or address given)*:

  | Repository (identifier) | Today | What it has, and what is needed |
  |---|---|---|
  | KU Leuven RDR (Dataverse), `doi:10.48804/XES6PY` | opens, by luck of order | `Link` header: `describedby` for each export, `rocrate_json` first; a linkset; crate is detached, with an array `@id` error (see Dataverse above). Tested: `TestCrateRetrieverOnline`. |
  | DataverseNO, `doi:10.18710/CVQPYG` | wrong document (OAI_ORE) | no RO-Crate exporter; needs the prober to say so. |
  | **OneStop4WildLIVE**, `https://wildlive.senckenberg.de/captureevent/wildlive/963d905b946c6119ed5f` | **opens** | `Link` header `describedby` to `/api/call?…method=getAsROCrate`: a detached crate with absolute `@id`s, `contentUrl`s on its files, RO-Crate **1.2-DRAFT**, whose context is not one we know: its terms warn. Tested. To do: treat `https://w3id.org/ro/crate/1.2-DRAFT/context` (and other drafts and release candidates) as the bundled 1.2 context. |
  | **MLentory**, `https://mlentory.zbmed.de/mlentory_graph/bb23…582a` | **opens** | an HTML `<link rel="describedby">` to `…/ro-crate-metadata.json` in a folder: an attached crate on the web. Its own faults: `@dcterms` (not a keyword) in the context, `@vocab` set to the context's address, an entity nested in a value. Tested. To do: `CrateReader` warns "More than one metadata descriptor" because the root's `@id` is an absolute address that *ends* with `/ro-crate-metadata.json`: that is the root (the descriptor's `about`), not a descriptor, so it should not count. |
  | **UK CEH** (EIDC), `doi:10.5285/3970a138-c035-40ac-bf1d-a2f8a464644a` | "opens" a **non-crate** | no Signposting, no `Link` headers; content negotiation answers `application/ld+json;profile="https://w3id.org/ro/crate"` with CEH's own metadata JSON (`accessLimitation`, `authors`, …), which has no `@graph`: the case the specification's tip warns of. Its page offers `format=ttl`, `schema.org`, `bib`, `ris`, `gemini`, `json`, no crate. Tested as is: the prober must say "no RO-Crate here: it claims the profile but is not one". |
  | **RADAR** (KIT), `https://dx.doi.org/10.35097/1054` | fails, HTTP 403 | the landing page's `Link` headers have `describedby … type="application/ld+json"; profile="https://w3id.org/ro/crate/1.1/context"` (`…/exportrocrate`), a tar `item` and others; the crate export itself answers **403 Forbidden** to non-browser clients, whatever the `User-Agent`. **Not to be got round** (no pretending to be a browser): the failure should say it was refused, by which address, and suggest downloading the crate from the page in a browser and opening the file. The same 403 for a second RADAR dataset (`…/radar/de/export/7j69dwbftunf1egw/exportrocrate`), so it is the export and not one dataset. Note the profile is the *context's* address, not `https://w3id.org/ro/crate`: it already ranks as the profile (`contains`); a tar item is not a zip. |
  | **HUN-REN ARP** (Dataverse 6.9), `hdl:21.15109/ARP/2DHQF1` | fails, "Invalid redirection" | the landing page is behind **Anubis**, a proof-of-work bot wall: a `302` with no `Location`, and its own cookies. Not to be solved or got round; the failure should say that the site's bot protection stopped a program, not "invalid redirection". Its API works (version 6.9, exporters: Datacite, OAI_ORE, dataverse_json, dcterms, ddi, html, oai_datacite, oai_dc, oai_ddi, schema.org): **no RO-Crate export**, so there is no crate either way. |
  | **ROHub**, `https://w3id.org/ro-id/2176fd9b-bef8-486a-b414-fef7edb37732` | fails, timeout | the landing page (www.rohub.org) has only `rel="linkset"` (`application/linkset` and `application/linkset+json`, at `api.rohub.org/api/ros/<id>/json/`), whose one `describedby` is `…/crate/download/` (`application/ld+json`) and which has 114 `item`s (`…/resources/<id>/download`, no type). Today `crate/download/` answers **504 Gateway Time-out** after 30 s (the crate of 114 resources is slow to make), and content negotiation on the identifier answers 503, which is what timed out the retriever (20 s). Linkset traversal is done (it now goes on to `crate/download/` and fails there with the 504, naming the address); still needs a timeout that says which address did not answer, and to look again for the zip. The zip you said it has was not in the linkset today: look again (the item `type`s are missing, so only their addresses say). |
  | Zenodo, `doi:10.5281/zenodo.24059872` | "opens" a **non-crate** | a `rel="linkset"` only; its `describedby` are Zenodo's own metadata. Needs linkset and prober. |
  | WorkflowHub, `doi:10.48546/workflowhub.workflow.120.5` | fails | the crate only as `rel="item"` zip with the profile. Needs items and zips. |

  From these: **three kinds of failure are not the same and should not read
  the same**: *not a crate* (CEH, Zenodo's metadata, DataverseNO's exports),
  *refused* (RADAR's 403, HUN-REN's bot wall: say who refused, and offer the
  way a person can do it themselves, never a way round), and *did not
  answer* (ROHub: which address, after how long). A refused or blocked
  response must not be retried with other headers. With `Retrieved.steps`,
  each failure lists what was tried in order. The online tests are in
  `TestCrateRetrieverRepositoriesOnline` (WildLIVE, MLentory, CEH); RADAR,
  HUN-REN and ROHub are not tested as they fail by the repositories' choice.
- **Check Web Availability: manners, and what it says of the content**
  *(in progress, 2026-11-19: `WebAvailability` rewritten and tested
  (`TestWebAvailabilityManners`, 11 tests) and used by the editor)*.

  Measured first (18 real addresses from the crates tried, HEAD against a GET
  for one byte): both agree on every 404 and on success; **the one real
  difference is KU Leuven's file addresses, HEAD 403 and GET 206**; the rest
  of the differences are 200 against 206 (the range honoured). So **HEAD first,
  and a `GET` with `Range: bytes=0-0` (body closed unread, so that no file is
  downloaded) where HEAD says a 4xx that may only be a server that does not do
  HEAD right** (400, 403, 404, 405, 406, 501; not 401, which needs a login, nor
  410, which is final, nor 429 or 503, which say to wait); 206 and 416 (a range
  of an empty file) count as found. **`https://w3id.org/ro/crate/1.1` answers
  404 to `Accept: */*`** (as to Java's, which sends none) and 303 to a
  browser's: the check now sends `text/html,application/xhtml+xml;q=0.9,*/*;q=0.8`,
  or every crate's `conformsTo` address would be reported as not there.

  Done in the rewrite: **`Retry-After`** (RFC 9110, 10.2.3: seconds or an HTTP
  date) on a 429 or 503 pauses **all** requests to that host, from every
  thread, until then; longer than `longestWait` (2 min) and the host is given
  up on, saying what it asked; **retries** of a 429, 5xx or failed connection,
  3 tries with 1 s then 2 s between (`Manners`), a timeout tried once more;
  **a host that does not answer** (3 timeouts or refused connections running,
  from any address) is not asked any more, so 40 addresses on a server that is
  down cost a few waits, not 40 ("Could not check: host is not asked any
  more, as …"); at most 4 at once, 2 to a host.

  **Connections** (measured, JDK 25.0.2): the JDK's client keeps them and
  negotiates **HTTP/2 by itself** (ORCID, schema.org, GitHub, doi.org, SPDX,
  creativecommons.org and WildLIVE answered `HTTP_2`; Zenodo and KU Leuven only
  speak HTTP/1.1): 20 sequential requests of one client are 1 connection, a
  check of 40 addresses 4, and **a new client for each request is one
  connection each**, so the editor must keep its one client (it does).
  **HTTP/3 is not in JDK 25's client** (JEP 517 is JDK 26's, and opt-in; only
  schema.org and doi.org advertise it of those tried): leave it, the gain is a
  connection set-up per host.

  **The content** (`WebAvailability.mismatches`, `expectations`): for an
  `@id` or `contentUrl` of an entity that says `contentSize` (bytes) or
  `encodingFormat` (a media type), **warn** (yellow) if the server's length is
  not the size (a HEAD that says 0 or the page's is confirmed by a GET for a
  byte, whose `Content-Range` has the whole length) or the answer is a web
  page (`text/html`) where another kind is said: "it is 32,790 bytes, but
  contentSize says 1,234" / "it is a web page (text/html), not application/pdf:
  a landing page, not the file?", as RO-Crate's note on `File` `@id`s asks (a
  DOI or repository page is not the file). Not for `url`, which may be a page
  about it, nor a size like "1.2 MB".

  **Done 2026-11-19**: the three tests that were ignored are fixed and none is
  (a skipped address keeps its own failure, "Not available: no answer within
  1.0 s, and not asked again, as …"; "tried N times" is the tries made; the
  closed port is a `ServerSocket`'s; and a host given up on is asked at most by
  those already on their way, as the permit is let go before the reply is
  classified), and `TestWebAvailability.check` has fast pauses.

  **To do, in this order:**
  1. **Use it in the editor** *(done 2026-11-19 for the check, the warnings and
     the summary: `CrateEditor.checkWeb` passes `expectations`, keeps `mismatches`
     as `suspicious` by `@id`, shows them as yellow warnings whatever the type
     and says how many "do not look like what the crate says"; the help in
     `views.md`; `contentThatIsNotWhatTheCrateSaysIsAWarning`)*. Still to do:
     the job should say what it is at (the host, "waiting 30 s as example.org
     asked") and have **Cancel** that stops at once; the summary should count
     separately the addresses "could not be checked" (a host given up on) from
     those not available, as it now counts both as not available.
  2. **`RootProbe`** (one HEAD per candidate, and one for a made-up path) and
     `CrateRetriever` should share these manners: the `Accept`, and, for the
     retriever, `Retry-After` on a 429 or 503 (waiting up to a few seconds,
     not a host given up on), the message naming the address that did not
     answer, and the three kinds of failure of "Repositories tried".
  3. **Smaller**: send `Accept-Encoding: identity` (RFC 9110, 12.5.3: the JDK
     client does not decompress); decode an HTML page by its `charset`, not
     always UTF-8; a check of thousands of addresses (KU Leuven's has about
     8,000) needs a progress count and a way to skip a host; `Linkset` uses
     Jackson's deprecated `JsonNode.fields()` (the build says so): use
     `properties()`.

- **A zip by FAIR Signposting**: `CrateRetriever` also follows
  `rel="item"` links to an RO-Crate zip (`type="application/zip"`, with
  the RO-Crate profile), as "Retrieving an RO-Crate" allows: downloaded
  in the background within the same limits, then opened as a zip (above).
- **Keep the persistent identifier**: a crate opened by its DOI or other
  PID gets it as the root's `identifier` and `cite-as`, where it has none,
  from Signposting's `cite-as` (and its `license` link where the crate has
  no licence), so that a local copy still says where it came from.
- **Open a zip without unpacking**: browse a zip read-only through the zip
  file system, with Crate > Extract to Folder… to edit it, rather than
  always unpacking; so that large `.eln` exports open at once, and files'
  own metadata is read from zip paths as they are.
- **Background jobs**: downloading, unpacking and checking manifests are
  slow and cancellable, so they are jobs (`Jobs`, shown by `JobsView` at
  the foot of the window, with a window of progress, status lines, Cancel
  and Run in Background; done).
- **Order**: Open URL/PID (read-only, Save As); open and
  export zips; open and check a bag; make a bag (last, as it moves the
  user's files into `data/`).

### Attached and detached crates

- **Attached to detached** (Crate > **Detach…**) *(done 2026-11-19 for the
  rewriting, the base and the dialog: `DetachDialog`, `Detachment`,
  `Crate.absolutize`, `Crate.setBase`; still to do: checking that the files
  are there, below)*. What RO-Crate 1.2 says (checked): in a Detached RO-Crate
  Package a File's `@id` "MUST be an Absolute URI" and all its File data
  entities are web-based data entities (`data-entities.md`); the root
  "SHOULD have an `@id` which is an absolute URL if it is available online;
  if it is not yet, or will never be, `@id` MAY be any valid URI - including
  `./`" and `#` ids of contextual entities remain allowed (`structure.md`);
  the descriptor's `@id` stays `ro-crate-metadata.json` (`root-data-entity.md`).
  **`@base` is not a substitute for absolute `@id`s**: the specification
  mentions it only for reading a crate as a graph (the appendix on relative
  URIs: a known web address of the root as the base for RDF tools, a fresh
  `arcp://uuid,…/` as an anonymous one, and `{"@base": null}`, which a saved
  crate SHOULD NOT have), and in its conversion from attached to detached
  by JSON-LD flattening against a fresh arcp `@base` "the resulting `@base`
  is harmless, but can be removed". So: rewrite the `@id`s, and keep the base
  as `@base` only as an option (it lets the identifiers be made relative
  again by search and replace, as the arcp section says). The dialog asks
  for the base, filled in from where the crate is attached on the web or
  what it says its root is (`WebCrate.rootsOf`), warns of a `file:` address
  (and asks again) and has **New Anonymous Base**, a fresh
  `arcp://uuid,<random UUID>/`, with `@base` kept by default for it; the
  base is made a folder (a `/` is added when it has no query). Rewritten: every
  entity but the root with a relative `@id`, files and folders (the
  `hasPart` hierarchy) and other entities written with a path or a name,
  not `#local`, `_:blank` or absolute ones, and every reference follows
  (`Crate.absolutize`); the root becomes the base if it is a web address
  (and stays `./` for arcp, which is not where the crate is online); one
  undo step ("Detach"), with the context's `@base` in it. After it the crate
  is attached to nothing, and the notice suggests saving it as
  `<prefix>-ro-crate-metadata.json` (RO-Crate 1.2: the name that tells that
  its file's presence does not make a folder an attached crate). Done
  2026-11-19: **Check Web Availability afterwards**, a tick-box (on for a
  web base, unavailable for an anonymous or `file:` one, as no files are
  there), and **use a file's own `contentUrl` (or `url`)** as its `@id`
  (`Crate.downloadAddress`: exactly one web address, `contentUrl` first;
  several, as KU Leuven's two versions, say nothing, so the path is used).
  **Check Web Availability asks across all entities**
  (`WebAvailability.targetsOf`): every `contentUrl` and `url` (relative ones
  against the web base), and every `@id` that is a web address, of any
  entity, but not the descriptor or schema.org terms; plus, with a web base,
  the relative `@id`s of files and folders at the address they lead to (not
  other entities' names). A failure says which property it was in.
  **Not the root's own `@id`**: RO-Crate only says it SHOULD be `./` or an
  absolute URI (`root-data-entity.md`, "Root Data Entity identifier"), and
  RECOMMENDS that a persistent identifier resolves to the crate (by
  content negotiation or Signposting, with `cite-as`), but no representation
  of a folder is required, and a folder may have no page; what must work for
  an attached crate on the web is its `ro-crate-metadata.json` beside it,
  which is asked instead (as of the root) when the crate is attached to a web
  address. **Severity**: the specification requires no `@id` to resolve (a
  DefinedTerm's `@id` in an outdated namespace, with a `url` as a fall-back for
  people), so an address of a work (a CreativeWork or any kind of one: files,
  datasets, software) that does not answer is a red problem, and of anything
  else (people, organisations, terms, places) only a yellow warning (the row
  and its icon; `warnings` in `CrateEditor`, `EntityTreeView`). To
  do: the root's `identifier`/`cite-as` as a persistent identifier if the
  user has one (the root's `@id`, "or a persistent identifier"); progress for
  the availability job, which asks four addresses at a time, so a crate of
  thousands (KU Leuven's has about 8,000) takes long, and a way to skip
  those on one host.
  This also is what **Make Attached…** below undoes; the view-only
  "detached" (nothing attached, `@id`s as they are) is in Crate > Attach….
- **Detached to attached** (Crate > Make Attached…): choose a folder; the
  `@id`s under a base URI (the root's, or one the user gives) become
  relative paths in it, and files there are offered to download (each
  checked against `contentSize` and any checksum, as a job). Files not
  downloaded keep their absolute `@id`s, as RO-Crate allows web-based
  data entities in an attached crate.
- **Attach…** (Crate menu) says what a crate's relative `@id`s are
  relative to: a folder, a web address, nothing (detached, `@id`s as they
  are) or a folder inside a zip, with no change to the crate: see
  "Semi-detached" under "Crates as ZIP, BagIt and on the web". It replaces
  the toggle below. **Detach…** is the other, which *does* change the
  crate: it makes the relative `@id`s absolute (above).
- **Attached to Folder** (the Crate menu's toggle) stays for crates
  merely opened from elsewhere, e.g. downloaded on their own.

### Publishing

- **Crate > Publish To…**: deposit the crate in a repository or on the
  web, as plugins (see "Plugins" above, a module layer of their own), one
  for each service: ROHub, Zenodo (and InvenioRDM), WorkflowHub, GitHub
  Pages (a repository's Pages branch, with the HTML preview as its
  index), Dataverse. Each says what crates it takes, checked before
  anything is uploaded: WorkflowHub a Workflow RO-Crate (a main workflow
  and its language), ROHub a research object, Zenodo and Dataverse any
  crate (as a zip, with their own metadata from the crate's: title,
  creators with ORCIDs, licence, keywords), GitHub Pages an attached crate
  of files small enough for Pages. Each shows what it would send and its
  metadata to confirm, uploads as a job (sandbox first where the service
  has one, e.g. Zenodo's), and leaves publishing (minting a DOI) to the
  user on the service's own page unless asked. The identifier given back
  (DOI, URL) is offered as the root's `identifier` and `cite-as`, and for
  GitHub Pages as the base URI of a detached crate (above).
- Accounts and tokens per service, kept in the platform's credential
  store where there is one (not in settings.json), and never logged.

### Nested RO-Crates

A crate may contain other crates: a folder of an attached crate with its
own `ro-crate-metadata.json`, which RO-Crate describes as a Dataset whose
`conformsTo` is the RO-Crate specification (see "Referencing other
RO-Crates" in the spec's data entities). Recognise them:
- When listing an attached crate's folder, a subfolder with its own
  metadata file is a crate of its own: show it with a crate icon, its
  files not as the outer crate's (they are the inner crate's to
  describe), and offer to add it as such a Dataset (`@id` its folder,
  `conformsTo` RO-Crate, its name and description from its metadata).
- Open the inner crate from there in another window (File > Open on its
  metadata file), and go back to the outer one.
- Also crates referenced by URL or DOI (a Dataset with `conformsTo`
  RO-Crate), opened by retrieving them (see the ro-crate-retrieve notes:
  Signposting, content negotiation).
- Checks: files of a nested crate shouldn't be the outer crate's parts
  directly; the nested crate's own metadata file and preview are not
  data of the outer one.
- **Collections of crates in a repository**, as LDaCA's (an Oni
  repository): a collection is a crate of its own (a
  `RepositoryCollection`, with no `hasPart` or `hasMember`), and each of
  its members another crate (`RepositoryObject`, with its files), linking
  up with `memberOf`. Mitchell and Delbridge has 7733 members, each about
  13 entities and 1 s to fetch. Open the collection's crate, list its
  members from the repository's API as they are asked for (paged:
  `/api/objects?memberOf=<id>`, 100 in 2.4 s), and open a member as a
  crate of its own; never put them all together (LDaCA's `?meta=all`
  times out). Their files are on the web (`/api/stream?id=…&path=…`),
  so members open as detached crates.

### Licences, defaults and the metadata descriptor

- **Recommended licences first** in the licence finder (Licence from
  SPDX…): a "Recommended" group at the very top, before the search
  results, of `Apache-2.0`, `BSD-2-Clause`, `BSD-3-Clause`, `MIT`,
  `CC0-1.0`, `CC-BY-4.0`, `CC-BY-NC-4.0` and `CC-BY-ND-4.0` (SPDX's
  identifiers; "CC-NC-4.0" isn't one, the non-commercial licence is
  CC-BY-NC-4.0). Software licences and data/content licences could be
  told apart in the group.
- **Preferences for defaults**: a default licence, author (e.g. the
  user's own ORCID, looked up once) and publisher (e.g. their
  organisation's ROR), filled in on a new crate's root, and offered first
  in those fields. Kept in the settings, so the privacy note of File >
  Recent applies.
- **The metadata descriptor's own licence**: the `ro-crate-metadata.json`
  entity may have a `license` (RO-Crate suggests e.g. CC0 for the
  metadata, separate from the data's), but the editor never shows the
  descriptor. Perhaps View > Show Metadata Descriptor, listing it as an
  entity (with only the properties that make sense: license, and not its
  @id, conformsTo or about, which wpsld manages), or a "Metadata licence"
  field on the root's form.

### HTML preview (ro-crate-preview.html)

*Done, basic*: saving an attached crate writes `ro-crate-preview.html`
(`CratePreview`): plain HTML and CSS, light and dark, a few colours, no
scripts but the metadata's JSON-LD in `<script type="application/ld+json">`;
descriptions as Markdown when they look like it (`Markdown.looksLike`),
else paragraphs with their line breaks; files linked by their paths
wherever they are linked from; a Top link in each section; the
crate's name and description, a contents list, a section per entity with
its properties, `#entity-n` anchors between them, links to files and the
web; marked with `<meta name="generator" content="wpsld …">` so that
only wpsld's own is written over; another tool's is replaced (moved
aside into `ro-crate-preview_files/`) or kept, asked once. The crate
describes it as RO-Crate's example does (a CreativeWork about the root,
not in hasPart). Next: a preference to not write it; the generator as a
SoftwareApplication with a CreateAction (object the metadata, result the
preview), as the spec's example; images and maps for files and places;
"Referenced by" as in the editor's preview; anchors that stay the same
across saves (by @id rather than position).

*Next*: **File > Save RO-Crate Preview…**, which writes the preview on its
own, to a file the user chooses, for a detached crate (which has no folder
for saving to write it in, e.g. to put beside its metadata on a web server)
and for an attached crate whose preview is to go elsewhere; links to files
as their `@id`s, which in a detached crate are absolute. The same
`CratePreview`, the same check for another tool's preview where the file
is there already, and not described in the crate, which does not hold it.

*Done (November 2026)*: the page is a Mustache template,
`ro-crate-preview.html.mustache` with the partial
`ro-crate-preview-value.mustache` for one value (beside
`ro-crate-preview.css` in `lib`'s resources, all three CC0 1.0, as they
make every preview), filled in by `CratePreview` with
[JMustache](https://github.com/samskivert/jmustache) from plain maps and
lists, so that its markup can be read and changed as HTML. The pages are
as before but for their indentation (checked on nine crates). Standard Mustache only
(none of JMustache's `-first`, `-index` and the like), so that other tools
could fill in the same template (mustache.js, chevron in Python); every
key of a value is given, false where not used, as Mustache looks for a
missing one in the enclosing sections. `CratePreview` still works out
what to show (anchors, groups, links, Markdown), as a logic-less template
can't. Chosen over the others, in November 2026:
- *The JDK*: no template engine of its own; String Templates (JEP
  430/459) were withdrawn in JDK 23, and XSLT (`java.xml`) would mean the
  crate as XML first.
- *JMustache 1.16* (chosen): one 79 KB jar, no dependencies, a Java
  module of its own (`com.samskivert.jmustache`), BSD 3-Clause. Stable
  (1.x since 2010, 1.16 from November 2023, releases years apart, but
  maintained: fixes merged in 2026, not yet released); Spring Boot's
  Mustache support uses it. Escaping is CratePreview's own, as before
  (`withEscaper`).
- *Jinjava 2.8.5* (HubSpot's Jinja, Apache 2.0): Jinja is better known in
  the RO-Crate community (ro-crate-py's preview), but 28 jars, 9.8 MB
  with its dependencies (Guava, javassist, commons-lang3, commons-net,
  SnakeYAML, …), about 7.5 MB more than `lib` has, and an older Jackson.
  Its Jinja is HubSpot's dialect, so ro-crate-py's template may not run
  as it is. If Jinja templates are wanted, as an optional module outside
  `lib` (as the `readers-*` are), e.g. for the command-line tools.
- Not compared further: Mustache.java (larger), jte (typed templates
  compiled at build time, a Gradle plugin), Pebble.
- Next: File > Save RO-Crate Preview… and a command-line preview writer
  (see Command-line tools) with the same template; perhaps a template of
  the user's own (a preference), as Mustache's logic-less templates can't
  do harm beyond the page they make.

The Preview tab renders
Markdown descriptions too, by the same heuristics, as JavaFX nodes
(`HelpMarkdown.description`, as the help viewer's pages). For a richer
preview, investigate which existing previewer
is easiest to reuse without running JavaScript or Python inside the Java
application (no GraalJS, Jython or a bundled Node):
- Previewers whose template could run in Java as it is: e.g. ro-crate-py's
  preview is a Jinja template, which Jinjava (a Java implementation of
  Jinja) might render; check the template's filters and its licence.
- Previewers that render in the browser: a static HTML page with its
  script, and the crate's JSON-LD embedded or beside it, which wpsld would
  only copy and fill in (e.g. what ro-crate-html-js / rochtml can make;
  check whether a page works offline, from `file:`).
- Others to compare: ro-crate-html-lite, and Crate-O's and Describo's
  previews; what each shows (files, people, licences, provenance), its
  size, how it is maintained, and its licence.
- Or wpsld's own: the preview pane already renders entities; writing that
  as HTML is the fallback, with the most control and the most to maintain.

### Performance on larger crates

Measure before optimising, on crates of growing size: e.g. the COMPSs run
crate above, a ROHub crate, and generated ones with 1 000, 10 000 and
100 000 entities and as many files in an attached crate's folder.
- What to time: opening (reading, the profile, the first form), selecting
  an entity (form, preview, references across the crate), typing in a
  field (each change checks the crate for undo and relists), saving and
  Repair Crate; switching to Entities by Folder and by Part; listing the
  folder's files (now at most 10 000, again after 5 seconds).
- How (*done*): `./gradlew performanceTest`, the JUnit category
  `Performance`, left out of `build` (`TestEditorPerformance`). It times
  each step (opening, selecting, typing, undo, the trees, saving, adding
  3 030 files as parts, copying and pasting 1 000 entities) on crates made
  in advance by `PerformanceCrates` (`./gradlew :ui:performanceCrates`
  makes them again into `ui/src/test/resources/performance/`): `large`
  (3 200 entities of many types referring to each other), `wide` (a root
  with 4 000 parts, all by one person), `deep` (folders ten deep) and
  `variables` (1 000 PropertyValues); and on a folder of 3 000 files made
  when run. Each step's time is in `ui/build/test-results/performance.tsv`,
  and a step fails when over its limit (some three times its time when
  written, `-PperformanceSlack=2` on a slower machine; lower a limit when
  its step is made faster). `-PperformanceProfile` records each test with
  the JDK's Flight Recorder (`ui/build/performance/<test>.jfr`, e.g. for
  `jfr print --events jdk.ExecutionSample` or JDK Mission Control). All
  of it took 35 s here, in November 2026; GitHub Actions runs it monthly
  on Linux (integration-tests.yml). Next: crates of 10 000 and 100 000
  entities, and the typing steps with the preview and JSON-LD tabs shown.
- Likely costs to look at: `crate.toJson()` compared on every change
  (undo, the title's "modified"), `dataEntities()` and `byId()` built
  again in loops, the tree made again from scratch, and the preview's
  "Referenced by" scanning all entities. Next: the form finding
  references by `Crate.get` (the index) rather than going through the
  entities, and an index of references (who refers to each `@id`) for the
  preview's "Referenced by", kept as the `@id` index is; time both on
  ICE-AUS.
- *Considered*: keeping each set of types' form and reusing it for the
  next entity of those types, its values cleared and filled again. Left
  for now, as selecting takes 0.2 to 0.3 s since the groups show only the
  properties in use: the form's handlers hold on to the entity they were
  made for (lookups, Fill in, Find…, the map), so reusing them risks
  writing to the previous entity, and a value left over is shown as the
  new one's. If done: one cache per window's editor (nodes can't be in two
  scenes, and several windows may come), cleared on opening another crate
  and on changing style or profile, and each handler reading the entity
  edited now.
- *Measured and done (November 2026)*, by a test timing each step and
  sampling the JavaFX thread's stack (kept outside the repository, as it
  read downloaded crates): the COMPSs crate (WorkflowHub workflow 780,
  1835 entities) opened in 174 s, now 2.2 s; see BUGS.md for what
  changed. LDaCA's ICE-AUS collection (2374 entities, 1.3 MB): opening
  3.9 s, selecting an entity 0.9 to 1.4 s, undo 1.2 s, mostly JavaFX
  laying out the form, next to look at (fewer controls made at once,
  e.g. groups built when opened, as folded ones already are).
- A larger real one: LDaCA's Mitchell and Delbridge collection, 7733
  member crates of about 13 entities each (about 43 MB of JSON-LD if put
  together, some 35 000 to 50 000 entities); LDaCA's own API times out
  (after 2 minutes) putting it together (`?meta=all`). As a stress test,
  generate a crate of that size from a sample of its members rather than
  fetching them all (an hour or two, one by one).
- These crates' licences (CC BY, CC BY-NC) allow using them but not
  bundling them: as online integration tests, downloading them when run.
- **Pasting many small entities**: time copying and pasting 500 fairly
  simple PropertyValue objects (e.g. a table's variables, as
  `variableMeasured` would make), into an empty crate and into a large
  one: the paste itself (`Crate.paste`, renaming clashing `#` ids, the
  references checked), selecting them (now at once, with no form), the
  undo step, relisting and the preview; and Paste's report, which lists
  what was merged or kept. Reported: pasting thousands freezes the window
  for seconds, without even a busy (hourglass) pointer. Work that may
  take long on the JavaFX thread should at least show the busy pointer
  (`Cursor.WAIT` on the scene, set before and cleared after, letting a
  pulse show it first), and the slow parts move to a job (see `Jobs`)
  where they don't touch the scene. *Measured*: pasting 2000 took 7.3 s
  into a large crate, now 1 s (0.2 s into a new one), as they are
  selected at once with no form. Pasting or adding as parts more than
  200 now shows the busy pointer first (`CrateEditor.busy`); moving the
  slow parts to a job is left. Timed by `performanceTest`
  (`copyAndPaste1000Entities`): 1 000 into their own crate, a large one
  and a new one, 0.1 to 0.3 s each.

### Internationalisation (i18n)

wpsld is in English only. Translating it means more than the UI's labels:
- **The UI**: menus, buttons, dialogs, messages and help pages in resource
  bundles, chosen by the system's language with a preference to change
  it; dates and numbers formatted for the language.
- **The vocabulary**: property and type labels and their descriptions come
  from schema.org's definitions (`rdfs:label`, `rdfs:comment`), which are
  in English; translations would need another source, e.g. community
  translations of schema.org, or labels in a profile. Labels made from
  terms (`datePublished` → "Date published", see `Labels`) are English
  word rules too.
- **Profiles**: some MASP profiles may already give labels and
  descriptions with `@language`: check which do, and show those in the
  user's language, falling back to English.
- **The crate's own values**: `name` and `description` may be given in
  several languages (`{"@value": …, "@language": "nb"}`), which the form
  shows read-only now; editing values per language would follow.
- **Right-to-left languages** (Arabic, Hebrew): the UI mirrored (JavaFX's
  node orientation), and text fields that take mixed directions; the
  Warp notebook's tabs and the tree's lines mirrored too.
- **Keyboard layouts**: shortcuts and mnemonics that work on layouts
  without the letters (e.g. Cyrillic, Greek) or where the keys move
  (AZERTY, QWERTZ; `Ctrl+/` for help on macOS), and input methods for
  Chinese, Japanese and Korean in the text fields.

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
- **Infer a profile from an example crate**: Crate > Make Profile from
  This Crate… drafts a MASP profile crate from one (or later several)
  example crates, as a start to edit rather than a finished profile:
  - A class rule (`rdfs:Class`, `prov:specializationOf` the type's IRI)
    for each type used, with how many entities of it the crate has as a
    suggested `sh:minCount`/`sh:maxCount` (e.g. exactly one root, one
    license). Several `@type`s on one entity: a rule for the combination,
    or for the most specific type (see the open question below).
  - A property rule per property per class: `rdfs:label` as written in
    the crate, `domainIncludes` that class rule, `rangeIncludes` from the
    values seen (class rules of the entities referred to, or Text,
    Number, Boolean, Date, URL by the values' form), `sh:minCount` 1 when
    every entity of the class has it, `sh:maxCount` 1 when none has more
    than one value. Comments from schema.org or the crate's own
    definitions (ad hoc `rdfs:Class`/`rdf:Property` entities, or another
    profile it conforms to).
  - The root and descriptor rules MASP finds the root by; types and
    properties the crate defines itself carried into the profile.
  - From several crates: the union of types and properties, required
    only when all have it, counts as ranges seen across them; a value
    the same in all (e.g. a fixed `conformsTo` or `license`) as a
    `PropertyValue` or `ItemList` to choose from.
  - Shown in a dialog to choose which types and properties to keep and
    to loosen what was inferred (one example says little about what is
    required), then written as a new profile crate with its own
    `profile-documentation.md`, opened in the editor like any crate and
    usable at once as a profile for other crates. One example is not
    enough to infer value patterns (regexes); leave those to editing.
- **Load vocabularies into a crate** *(TODO, designed in outline)*: for
  the terms a crate (often a profile crate) uses or defines, read a
  published vocabulary, e.g. Insert > Vocabulary from URL or File…, for an
  address (content negotiation or `Link` headers as `CrateRetriever` does,
  for JSON-LD, Turtle, RDF/XML) or a file in:
  - RDFS (`rdfs:Class`, `rdf:Property`, `rdfs:label`, `rdfs:comment`,
    `rdfs:subClassOf`, `rdfs:subPropertyOf`, `rdfs:domain`, `rdfs:range`,
    `rdfs:seeAlso`, `rdfs:isDefinedBy`), and schema.org's way of saying
    the same (`domainIncludes`, `rangeIncludes`, `supersededBy`);
  - OWL (`owl:Class`, `owl:ObjectProperty`, `owl:DatatypeProperty`,
    `owl:AnnotationProperty`, `owl:equivalentClass`, `owl:deprecated`,
    `owl:Ontology` for the vocabulary's own name, version and licence),
    taking the few elements needed, not reasoning (restrictions are shown
    as text or left out, with a note);
  - SKOS (`skos:ConceptScheme`, `skos:Concept`, `skos:prefLabel`,
    `altLabel`, `definition`, `broader`/`narrower`, `inScheme`,
    `hasTopConcept`, `notation`), for controlled vocabularies, as EDAM's
    and many thesauri are.

  What it becomes, chosen in a dialog like the profile inference's (which
  terms to keep, a search and a filter by type, how many there are, as the
  files' finders show):
  - as **contextual entities** for use in an ordinary crate: a SKOS scheme
    or a vocabulary's list of terms as a `DefinedTermSet` (`name`,
    `description`, `url`, the `@id` the vocabulary's own), each concept as
    a `DefinedTerm` (`name` from `prefLabel`, `description` from
    `definition`, `termCode` from `notation`, `inDefinedTermSet`, `url`
    its IRI, `alternateName` from `altLabel`), and, where the crate says
    what a term is, classes as `rdfs:Class` and properties as `rdf:Property`
    entities as RO-Crate's "Extending RO-Crate" describes, with `@id` the
    term's IRI or a prefixed `@id` and the vocabulary's prefix added to the
    crate's `@context` (kept, not renamed; check it for clashes with the
    context's own terms, and the vocabulary's namespace for `@vocab`);
  - as **rules of a profile crate** (MASP): a class becomes a class rule
    (`rdfs:Class`, `prov:specializationOf` the class's IRI), a property a
    property rule (`domainIncludes` the classes it is the domain of,
    `rangeIncludes` its range, `rdfs:label`/`rdfs:comment` from the
    vocabulary), `owl:Restriction` cardinalities as `sh:minCount` and
    `sh:maxCount` where simple (`owl:minCardinality`, `owl:cardinality`),
    and a SKOS scheme as a `DefinedTermSet` rule or an `ItemList` of the
    values a property may take (see MASP.md), so a profile can be made
    from a vocabulary rather than by hand;
  - as **choices of a form** (the profile in use): a concept scheme
    becomes the values to choose from for a property, as `ItemList` rules
    do now, searchable by label and synonym, as the format finders are.
  Keep where each term came from (`isDefinedBy`/`sameAs` the vocabulary's
  IRI, its version or `dateModified`, and `retrieved`) so that a loaded
  vocabulary can be checked for updates and its terms refreshed, merging
  rather than duplicating (as pasting entities does); big vocabularies
  (EDAM, NCIt) are loaded as a job, with a limit and a subset chosen
  before anything is added, as the XML or Turtle parsing is untrusted
  (sizes, entity expansion, no network from inside the parser; for Turtle
  and RDF/XML check what the JDK can do alone, else a small reader, or
  Jena only if it is accepted, as for file formats above).
- **Editing profile crates**: more is probably needed to edit a profile
  crate well, beyond the vocabularies, to be worked out as profile crates
  are written. For example: a Profile mode of the editor choosing what the
  forms show for a crate that *is* a profile (the MASP profile's own
  classes and properties, as `rdfs:Class`, `rdf:Property`, `ItemList`,
  `DefinedTermSet` and `PropertyValue` entities, with their cardinalities
  and ranges); adding a class rule and its properties in one step; picking
  a range from the classes of the profile and of schema.org; checking the
  profile as a MASP profile (every property has a domain and a range, no
  class without a label, every `domainIncludes` found, rules that clash)
  and previewing the form it gives, as one using it would see it; creating
  the profile's `profile-documentation.md`, `index.html` and
  `crate-o-mode.json` with the profile; versioning (`version`,
  `isBasedOn` the profile it extends, `supersededBy`), and publishing it
  with a persistent identifier that its `conformsTo` users can load (see
  Publishing).
