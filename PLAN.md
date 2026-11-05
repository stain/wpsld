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
(`dist/wpsld-linux-x64.zip`, cross-linked `dist/wpsld-windows-x64.zip`), and a
draft GitHub Actions workflow for a native Windows `wpsld.exe`.

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
  redundant supertypes dropped). Next: undo, and with the file system backend
  possibly renaming the file itself.
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
- **Save** *(done)*: "Save RO-Crate Metadata File…" (default
  `ro-crate-metadata.json`), after `CrateChecks` of the root's required
  properties and of references to local `@id`s not in the crate, with "Save
  anyway". Proper validation will come from MASP rules in Java (no Python).
  Next: open an existing `ro-crate-metadata.json`.
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
  of MASP.
- **More RO-Crate requirements in the save checks**, e.g. scripts and
  workflows (`SoftwareSourceCode`, `ComputationalWorkflow`) MUST have a
  `name`, found by rocrate-validator; better covered by validation from the
  MASP RO-Crate profile than by hand-written checks.

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
- Controls the tests use have ids: `entityId`, `propertySearch`, `addType`,
  `type-<Type>`, `removeType-<Type>`, `label-<property>`, `field-<property>`,
  `value-<property>-<n>`, `add-<property>`, `new-<property>`,
  `otherProperties`, `suggestType-<Type>`, `typeSearch`, `typeTree`,
  `entities`, `addEntity`, `removeEntity`, `save`, `preview`.

### Start-up

- Try AppCDS (`-XX:+AutoCreateSharedArchive`) or the Java 25 AOT cache in the
  launchers to cut JVM and JavaFX start-up time.
- Schema IRIs as plain strings and descriptions not kept in memory would bring
  the schema model under 2 MB, if needed.

### Profiles

- RO-Crate MASP profiles: see [MASP.md](MASP.md), on hold until the editor
  works well with plain schema.org.
