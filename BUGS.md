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
  `PdfMetadata`). More formats with no new dependency (*done*, by
  metadata-extractor's `FileTypeDetector`: HEIC/HEIF, AVIF, GIF, BMP,
  PSD, EPS, raw formats, MP4 and MOV, with videos' title, author,
  creation date (QuickTime's with its offset, else the movie header's
  UTC), `duration`, size, phone and ISO 6709 place; test files made with
  ffmpeg, libheif, libavif, ImageMagick and exiftool). HEIF's own size
  is not used: it is the coded image's, before cropping and rotation.
  Found by the damaged-input tests: metadata-extractor 2.22.0 allocates
  for a movie's sample description's entry count, running out of memory
  on a damaged one; `MovieBoxes` refuses such movies first (to report
  upstream). MP4's own location (`©xyz`) is not read by
  metadata-extractor. Still to do: offering it when adding files (Insert >
  Files, dropping files in), never blocking the window: for one or a few
  files (up to about 5), read in the background and, if they say
  anything new, a non-modal notice ("photo.jpg says who made it, when and
  its licence: Review…") opening the dialog; for many (10 or more) or
  large ones, not read at once but offered ("Added 37 files. Read what
  they say…"), as Crate > Update File Details does: one background job
  with progress and Cancel (as copying files has), two files at a time
  each with its time limit, filling in only what entities lack (never
  where or the camera), a summary with the files that failed or took too
  long, one undo step. Results remembered by path, size and date
  modified, so nothing is read twice. Readers isolated: each on its own
  thread with a time limit, any Throwable (also LinkageError,
  StackOverflowError, OutOfMemoryError) caught and reported as a problem,
  never thrown at the window (*done*, `FileMetadata`, and reading in the
  background with progress and Cancel in both); damaged-input tests (cut
  short, garbage after the start bytes, huge lengths, deep nesting,
  oversized parts) as integration tests in their own GitHub workflow, as
  the online tests are (*done*, `./gradlew damagedTest`, a job of
  `integration-tests.yml` with the online tests). `exifData` as PropertyValues rather than one
  text; and JPEG 2000 is not planned (no library reads its metadata but
  jai-imageio-jpeg2000, whose JJ2000 licence is a problem).

- **A log for the app**: the Windows launcher has no console, and
  slf4j-nop dropped what Tika logged. *Done*: Java's own logging
  (`java.util.logging`) to the user's log folder (XDG state, Windows'
  `LOCALAPPDATA`, macOS `~/Library/Logs`), rotated at 20 MB, a set of
  logs for each wpsld running at once; SLF4J through slf4j-jdk14, PDFBox's
  Commons Logging and `System.Logger` (wpsld, JavaFX, the JDK's HTTP
  client errors) all to it; Preferences > Logs for the level, the folder
  and clearing (by truncating, as another wpsld may be writing); failures
  shown only as messages (readers, web searches, tiles) logged with their
  causes. Log4j 2 was considered: none of the dependencies needs it (only
  Commons Logging would use it if present), and it adds 2.4 MB to the
  image; `log4j-jpl` and `log4j-slf4j2-impl` could take over later without
  changes to wpsld's code. Not yet: a Help > Report a problem that
  attaches the log.

- **Metadata readers as modules (proto-plugins)**: *done* for images
  (`readers-image`) and PDF (`readers-pdf`): lib's `MetadataReader`
  service, `FileMetadata` asking them all, each on its own thread with
  a time limit. Next: scientific formats (below) as a module too, and
  plugins found in a folder (see PLAN.md) rather than ui requiring the
  modules so that jlink links them in.
- **PDF metadata without PDFBox in the app (option 3)**: *done*, lib
  scans PDF, PostScript/EPS (and old `.ai`), PSD and InDesign files for
  their XMP packets (`XmpPackets`; a PDF's document packet is the one
  with `pdf:` terms, else its only one), and PDFBox is a reader module
  for the document information dictionary and compressed XMP. Left:
  PDFBox as an optional plugin rather than linked in (see PLAN.md;
  tried 13 November 2026, it works as an automatic module in a module
  layer of its own with java.desktop in the boot layer), and packets in
  other formats (MP4/MOV's `uuid` box, by FileTypeDetector, above).
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
  a plugin if ever; Parquet's footer later. Also statistics packages'
  files, read for their dictionary only, by our own code (no maintained
  Java library; ReadStat, which R's haven and pyreadstat use, is C):
  - SPSS `.sav` and `.zsav` (only the data is compressed), as GNU PSPP's
    "System File Format" documents it: the header's product (e.g. "IBM
    SPSS Statistics 29"), creation date and time, file label and number
    of cases; each variable's name (also the long names), label, type,
    value labels (1 = "Male"…), missing values and measurement level
    (nominal, ordinal, scale); document records (notes, often what was
    done to the data); file and variable attributes (key-value); the
    encoding.
  - Stata `.dta` (format 117 and later, documented by StataCorp; older
    ones too if simple): dataset label, timestamp, variables' names,
    labels and value labels, notes (characteristics).
  - SAS `.sas7bdat`: undocumented, reverse-engineered; later, if asked.
- **Where a photo was taken, named**: now GPS gives a Place "Where
  photo.jpg was made" with its point (only on purpose). Also:
  - Name it by GeoNames' nearest place to the point (`findNearbyPlaceName`,
    with the GeoNames username Preferences already has; online, so shown
    to confirm), linked as GeoNames' place is now; or reuse a Place of
    the crate at (about) that point.
  - Read location written as text: XMP `photoshop:City`, `State`,
    `Country`, `Iptc4xmpCore:Location` (sub-location), IPTC
    Sub-location, City, Province-State, Country-PrimaryLocationName: a
    Place's name and address even without GPS, and a better GeoNames
    search.
  - GPS altitude (the Geometry's or GeoCoordinates' `elevation`), and its
    UTC date and time (GPSDateStamp, GPSTimeStamp): a better
    `dateCreated` when the camera's clock has no offset.

- **Variables of tabular files, `variableMeasured`**, compatible with
  Science-on-Schema's Dataset guide
  (https://github.com/ESIPFed/science-on-schema.org/blob/main/guides/Dataset.md),
  as RO-Crate says nothing of it. Their Dataset is our File (e.g.
  `data/counts.csv`), not the root. One PropertyValue per column or
  variable, an entity of its own (`#…`, as the crate is flattened), in
  their tiers: `name` (the column's label), `alternateName` and
  `description`; `propertyID` (an IRI defining it, e.g. a CF standard
  name, chosen by hand otherwise); for numbers `unitText`, `unitCode`
  (an IRI, e.g. QUDT), `minValue`, `maxValue`, `measurementTechnique`.
  - CSV and TSV: the header row's names, each column's kind (number,
    date, text) and for numbers their minimum and maximum, by reading the
    file (in the background, as files may be large); with CSVW metadata
    beside it (`csv-metadata.json`, `<file>-metadata.json`) its titles,
    descriptions, datatypes and units instead.
  - XLSX (with the zip file system, no POI) and ODS: each sheet's first
    row as names (`xl/sharedStrings.xml`, `xl/worksheets/sheet1.xml`),
    the same kinds and ranges; several sheets as several groups.
  - SPSS, Stata, NetCDF and HDF5 (above): their variables with labels,
    units and value labels.
  - Where they go: schema.org gives `variableMeasured` to Dataset, not
    MediaObject, but a File must not also be typed Dataset, which in
    RO-Crate is a folder (a path `@id`). Instead, as DCAT does, a
    Dataset of its own beside the files, e.g. `#count-days` with `name`,
    `description`, `variableMeasured` and `distribution` linking the
    Files (typed File and DataDownload, which `distribution` takes and is
    a MediaObject): one Dataset for the same table in several files, e.g.
    workshop8's `data/exports/csv/count_days.csv` and
    `data/exports/parquet/count_days.parquet`. Its `#` id keeps it out of
    the folder tree; the root mentions it. Offered when reading a
    tabular file's variables: a new such Dataset, or one the crate has.
    Check how RO-Crate 1.3 and its validators take a Dataset whose `@id`
    is not a folder, and say so in the help.
  - Spatial coverage: Science-on-Schema writes a GeoShape `box` ("south
    west north east") or GeoCoordinates, RO-Crate 1.3 a Geometry with
    WKT; write what RO-Crate says, perhaps both.
  - The form: variables as a table to edit (name, description, unit,
    min, max) rather than one form per PropertyValue.
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

