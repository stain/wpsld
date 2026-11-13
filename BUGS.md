# Bugs and requests to work through

Reported while trying the entity tree and icons (November 2026), kept here
to do one at a time. Longer-term plans are in [PLAN.md](PLAN.md).

## Bugs

- **Remove disabled for the first entity of a type**: reported with a type
  chosen in the type selector. Not reproduced: clicking the first entity
  of a type and choosing Remove works in tests, also on the ROHub crate
  below. There, the root dataset is also typed File, so it is listed first
  under Dataset, File and CreativeWork, where Remove is rightly disabled.
  Needs the steps that show it.
- **The "Entities" heading in OS/2 Warp dark** is dark grey on the dark
  sidebar, barely readable. Not reproduced on Linux: the label takes
  `.label`'s `-warp-text` (#e4e4e4), also after switching style and
  colours while open. Maybe Windows only (as the cut-off dialog text was);
  needs a screenshot, and whether the colours were Dark or Same as System.
- **Menus open away from the menu bar after a DPI change (Windows)**:
  after the window went to a display with a higher scale, File's menu
  opened about 240 px right of and 80 px below File, "hanging", and the
  mnemonic underlines were drawn too low (`debug/Screenshot 2026-11-13
  140920.png`, JavaFX 27.0.1). JavaFX's, not ours, as far as known: still
  open are JDK-8251912 (a Stage doesn't take up a change of its screen's
  scale, e.g. in Settings while running; its workaround: move the window
  to another screen and back) and JDK-8301386 (popups offset where two
  screens of different scales meet); JDK-8346281 and JDK-8371302, fixed
  in JavaFX 26, are in ours. Not reproducible headless (one screen, scale
  1). It happened when the external monitors were disconnected, so that
  Windows moved the window to the laptop's own screen. Left to JavaFX
  for now; if it needs a workaround of ours, perhaps re-showing the
  stage when its `outputScaleX` changes.

## Repair Crate

- **Types written as IRIs**, e.g. ROHub's `http://schema.org/Person` and
  `http://schema.org/DefinedTerm`, could be written by the names the
  context gives them (`Person`). The entity list already shows them by
  name; the type chips and the preview still show the IRIs.
- **Enumeration members written as text**, e.g. `"actionStatus":
  "PotentialActionStatus"`, should be references, `{"@id":
  "http://schema.org/PotentialActionStatus"}`, to be valid JSON-LD (see
  Enumerations below).

## Requests

- **Cut, copy and paste of files into a folder, within a crate**: in the
  entity tree, cutting a File with a relative `@id` and pasting it into a
  folder should move the file there on disk, if the folder is a real one
  in an attached crate, renaming its `@id` (and the references to it) and
  moving it to that folder's Dataset's `hasPart`. Copy and paste should
  copy the file there, adding a File entity for the copy. Now Edit > Cut
  does nothing in the entity list, and pasting a File into its own crate
  is skipped as the same entity. Shares the moving with "Drag and drop
  within the tree" and the copying with "Copy and paste of files" in
  PLAN.md (which says that within one crate there is nothing to copy:
  update it). Ask before changing files on disk, with Replace, Keep Both
  or Skip for names taken.
- **Unknown types and properties used in the crate**: types in `@type`
  that neither schema.org, the RO-Crate context nor the profile define
  should be offered in Add Type, in a list of "Unknown types" (now
  `EntityEditor.otherTypes()` offers only the profile's and the
  context's). Their definition may be in the crate itself (`rdfs:Class`
  entities, RO-Crate's ad hoc terms, with `rdfs:label`, `rdfs:comment`,
  `rdfs:subClassOf`), or in a profile crate the crate `conformsTo` other
  than the one chosen: use it for the label, description and place in
  the tree when there is one. The same for properties: those used
  elsewhere in the crate, but not known for the entity's types, found by
  the property search to add. With an `rdf:Property` definition
  (`domainIncludes`, `rangeIncludes`), offered for those classes with a
  field of that range; without one, for any entity, as text or a
  reference as they are used.
- **CodeMeta**: read `codemeta.json` files (JSON-LD, context
  `https://w3id.org/codemeta/3.0`, mostly schema.org terms) to describe a
  SoftwareSourceCode or SoftwareApplication: name, description, version,
  author (with ORCIDs), license, codeRepository, programmingLanguage,
  softwareRequirements, dates, keywords, funding. A `codemeta.json` in
  the crate (or a folder of it) offers to describe its folder's entity;
  its terms mapped through the CodeMeta context, keeping what schema.org
  doesn't define (e.g. `codemeta:continuousIntegration`) as known
  properties. Also Insert > Software from a GitHub or GitLab URL:
  fetch the repository's `codemeta.json` if it has one, else build from
  its API (name, description, licence's SPDX id, homepage, topics as
  keywords, languages) and `CITATION.cff` (authors with ORCIDs, version,
  DOI), as the DOI and ROR lookups do: rate-limited, untrusted input,
  shown to confirm before adding. Perhaps write `codemeta.json` back
  from the crate's SoftwareSourceCode.
- **An entity from a web page's schema.org** (not now: a can of worms):
  Insert > From Web Page… reads the schema.org a page embeds, e.g. a
  `<script type="application/ld+json">`, also microdata (`itemscope`,
  `itemprop`) and RDFa, and offers its entities to add. Pages use
  schema.org loosely and in several ways at once, and their identifiers
  are all over the place (none, blank nodes, `#` ids relative to the
  page, the page's own URL for several things), so each would need
  checking and renaming before it is added; and the page is untrusted.
- **File > Recent**: the last 6 crates opened or saved, kept in the
  settings. A preference turns it off (and forgets the list), as the
  names and places of crates can be private.
- **Recover unsaved changes**, as Word and vim do: if wpsld is closed
  without saving, or crashes, the changes are offered again when it next
  starts.
  - While a crate has unsaved changes, write a copy of its JSON-LD in the
    background to wpsld's data folder (XDG data directory on Linux, the
    equivalents on Windows and macOS), at most every 10–15 seconds, as a
    crate may be large; delete it once saved, or closed without saving on
    purpose.
  - Several windows of wpsld, or several wpsld processes, may be open, so
    there may be several to recover: one file each, named so they don't
    clash, with which crate (if saved before) and when. At start, list
    those whose wpsld is no longer running, to recover, open beside the
    original, or discard.
  - Privacy: these copies hold the crate's metadata, e.g. names and
    e-mail addresses of people, outside the crate's own folder. A
    preference turns recovery off (and deletes the copies); say in the
    help where they are kept.
- **JSON-LD tab follows the selection**: when an entity is selected,
  highlight its object in the JSON-LD and scroll to it.
- **Icons in Add Entity's type chooser**: show each type's icon there too,
  at least in the OS/2 Warp style, if it doesn't get too busy.
- **Enumerations as drop-downs**: a property whose range is an Enumeration
  subtype, from schema.org or a profile, should offer its members as if
  they were entities of the crate, e.g. `actionStatus` (ActionStatusType)
  offers ActiveActionStatus, CompletedActionStatus, FailedActionStatus and
  PotentialActionStatus. Other values must still be allowed, as users may
  extend an enumeration.
- **Find entities (Edit > Find…, Ctrl+F)**: a dialog to search the
  crate's entities by their values and `@id`s, with tick-boxes for what
  to search (names, other values, `@id`s, nested objects; match case,
  whole words), listing the matches to go to. Perhaps Replace too, for
  values other than `@id`s: not `@id`s by default, neither of entities
  nor of nested objects, as renaming can merge entities and change
  references; if offered, check first and say what would happen (e.g.
  "15 entities would be merged"), and rewrite `#local` identifiers but
  never URLs. Matches hidden by the type chosen above the entity list
  are said to be, as for the filter below.
- **Filter the entity list**: a search field in the toolbar after Load
  Profile… and ?, filtering the entities shown (list or tree) by name,
  `@id` and maybe values as you type; with a type chosen, say at the end
  of the list what else matches, e.g. "Hidden: 15 entities of other
  types", to show them all.
- **Files' own metadata, what is left**: Metadata from the File… and
  Crate > Update File Details… read Office, OpenDocument, PDF, JPEG, PNG,
  TIFF, WebP and SVG files: names, descriptions, authors, keywords, dates,
  copyright, licences (also named in text, `SPDXLicenses.named`), sizes,
  the camera and where a photo was taken (`EmbeddedMetadata`,
  `PdfMetadata`). Next: more formats with no new dependency, as
  metadata-extractor reads them already: decide by its
  `FileTypeDetector` rather than our own start bytes, to read HEIC/HEIF,
  AVIF, DNG and other raw formats (CR2, CR3, NEF, ARW, ORF, RW2, RAF),
  PSD, EPS and old PostScript `.ai` (newer `.ai` are PDFs, read already),
  GIF, and videos (MP4, MOV); map videos' own metadata: QuickTime's and
  MP4's creation date, `duration` (ISO 8601), the phone's make and model
  and GPS (ISO 6709). Test files from temporary tools (ffmpeg, libheif,
  libavif). Still to do: offering it when adding files (Insert >
  Files, dropping files in); `exifData` as PropertyValues rather than one
  text; and JPEG 2000 is not planned (no library reads its metadata but
  jai-imageio-jpeg2000, whose JJ2000 licence is a problem).

- **Metadata readers as modules (proto-plugins)**: lib declares a
  `MetadataReader` service (`ServiceLoader`, `uses` in its module-info)
  and keeps the readers needing only java.xml (Office, OpenDocument, SVG,
  XMP/RDF); one Gradle subproject and module per group of readers with
  their own dependencies, providing it: images and video
  (metadata-extractor, xmpcore), PDF (PDFBox, and so java.desktop, out of
  ui), scientific formats (below). ui asks the service for all readers
  and, until plugins are found in a folder (see PLAN.md), requires them
  so that jlink links them in.
- **Metadata of scientific formats**: global attributes by the CF and
  ACDD conventions map onto the file's or dataset's properties: `title`
  (name), `summary` (description), `creator_name`/`creator_email`
  (author), `institution` (publisher), `license`, `keywords`,
  `date_created`, `time_coverage_start`/`_end` (temporalCoverage),
  `geospatial_lat_min`… (spatialCoverage, a Place with a box). Formats:
  HDF5 and NetCDF-4 with jHDF (0.14.0, MIT, pure Java, about 1 MB with
  commons-lang3, lz4-java and compress-lzf; not the HDF Group's, which
  needs native libraries); NetCDF-3 classic, FITS (80-character header
  cards: OBJECT, TELESCOP, INSTRUME, DATE-OBS, AUTHOR) and Zarr (JSON
  attributes) read by our own code; netcdf-java (GRIB and more) too big,
  a plugin if ever; Parquet's footer later.
- **Key-value metadata as PropertyValues**: what has no schema.org
  property, as PropertyValue entities (`name`, `value`, `unitText`,
  `propertyID` where a convention defines it): a scientific file's other
  global attributes on `additionalProperty`; its variables on
  `variableMeasured` (`long_name` as description, `units`, CF
  `standard_name` as propertyID); a photo's camera and settings on
  `exifData` (now one text). Offered as one choice per group in the
  dialog (e.g. "12 attributes"), not one per value.
- **Upgrade dependencies, one at a time**, e.g. for security fixes:
  PDFBox, metadata-extractor and Tika parse files from anywhere, so keep
  them current. One dependency per commit: bump it, run all the tests,
  check the app image (`podman build`) and its THIRD-PARTY-NOTICES.txt
  (version, and NOTICE or licence changes), and watch for one pulling in
  another's newer version (as Tika 4.2.0 did commonmark 0.31.0 over our
  0.24.0, now aligned). Newer than ours (13 November 2026): Jackson
  2.23.0 (2.22.4), commonmark 0.32.0 (0.31.0, but Tika's choice), JUnit
  4.13.2 (4.13; or JUnit 5); not yet: SLF4J 2.1.0-alpha1, JavaFX 28 (early
  access). Current: Tika 4.2.0, PDFBox 3.0.8, metadata-extractor 2.22.0,
  Ikonli 12.4.0, TestFX 4.0.18. Perhaps Dependabot or Renovate to say so,
  still merged one at a time.

## To check on Windows and macOS

Built and tested on Linux only so far; the tests that need another system
skip or take the Linux path. Try in the Windows and macOS builds:
- **Saving**: SafeSave's atomic move over the metadata file (Windows has
  no hard links on FAT, and NTFS replaces by MoveFileEx); saving with
  the file open in another program; a folder without write access.
- **Upper and lower case**: an `@id` whose case differs from the file's
  (`Data.csv` for `data.csv`) is marked and reported; the test of it only
  runs where names are found ignoring case.
- **Show in Explorer / Finder**: `explorer.exe /select,` on a file, a
  folder, and paths with spaces and non-ASCII letters; `open -R` on macOS.
- **Watching the folder**: on macOS Java polls (every few seconds), so
  check that changes show and Refresh works; on Windows, network drives
  and OneDrive folders.
- **Copying files in**: from another drive, a full disk (e.g. a small USB
  stick), Cancel midway; file dates kept.
- **Tika and the runtime image**: formats found by content in the
  packaged app (the image includes Tika, checked in the Linux build).

