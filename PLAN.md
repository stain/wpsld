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
  parents. Frees the space now taken by the tree.
- **Several values per property** (JSON arrays), multiple `@type`s, and
  renaming an `@id` updating references from other entities.
- **Linked entities**: reference fields suggest the crate's entities; add
  creating a new entity of the property's range from the field, like Crate-O.
- Open and save `ro-crate-metadata.json`.
- Remove the old tutorial classes (`HelloFX`, `SearchController`, `Person`).

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

### Start-up

- Try AppCDS (`-XX:+AutoCreateSharedArchive`) or the Java 25 AOT cache in the
  launchers to cut JVM and JavaFX start-up time.
- Schema IRIs as plain strings and descriptions not kept in memory would bring
  the schema model under 2 MB, if needed.

### Profiles

- RO-Crate MASP profiles: see [MASP.md](MASP.md), on hold until the editor
  works well with plain schema.org.
