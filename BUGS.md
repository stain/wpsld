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
- **Image metadata from EXIF**: for PNG and JPEG images, read their
  embedded metadata (EXIF, and XMP or IPTC where present) to offer as an
  ImageObject's properties: `width` and `height`, `dateCreated`
  (DateTimeOriginal), `creator` (Artist), `copyrightNotice`, `caption` or
  `description`, the camera in `exifData` (PropertyValues), and where it
  was taken (GPS) as a Place in `contentLocation`. Offered to choose, not
  filled in silently, as photos can carry private details (location,
  camera serial numbers). Not JPEG 2000: no library reads its metadata
  but jai-imageio-jpeg2000, whose JJ2000 licence is a problem, and it
  would mean native-ish binary handling of our own.
  Library: **metadata-extractor** (2.22.0, Oct 2026, Apache-2.0; xmpcore
  BSD-3-Clause): JPEG (EXIF, XMP, IPTC) and PNG (text chunks, XMP, eXIf),
  about 1 MB with xmpcore, typed access (e.g.
  `GpsDirectory.getGeoLocation()`); it is what Tika's image parser uses.
  Compared with Apache Commons Imaging (1.0.0-alpha6, still alpha, about
  2 MB with commons-io and commons-lang3) and Tika's image module (more
  jars than needed).
  Tried (13 November 2026) on a JPEG and a PNG tagged with exiftool: it
  reads EXIF Artist, Copyright, ImageDescription, Make and Model,
  DateTimeOriginal with OffsetTimeOriginal, GPS as decimal degrees
  (`GpsDirectory.getGeoLocation()`), and XMP `cc:license`,
  `xmpRights:WebStatement`, `dc:creator`, `dc:title`, `xmp:CreateDate`;
  from PNG both its eXIf chunk and XMP. Take XMP through
  `XmpDirectory.getXmpProperties()` (a map of path to value, e.g.
  `cc:license`), not xmpcore's `XMPMeta`: xmpcore has no module name, so
  it is on the class path and our module can't read its types. Take dates
  as their text: `getDateOriginal()` takes UTC when the file has no zone.
  The jlink plugin merges both jars into `wpsld.merged.module`, as it does
  tika-core (linking itself not tried here: the container's JDK has no
  jmods).
- **SVG metadata**: SVG files can describe themselves in RDF in a
  `<metadata>` element; Inkscape writes a `cc:Work` with Dublin Core
  (`dc:title`, `dc:creator` as a `cc:Agent` with its `dc:title`,
  `dc:date`, `dc:rights`, `dc:description`, `dc:subject` keywords) and
  `cc:license` with an `rdf:resource` URL. Read with the JDK's XML parser
  (DTDs and external entities off), no library needed; also `<title>` and
  `<desc>`, and the size from `width`, `height` or `viewBox`.
- **Document metadata from PDF, Office and OpenDocument files**, the same
  way: `name`, `author`/`creator`, `dateCreated`, `dateModified`,
  `keywords`, `description`, `numberOfPages`, offered to choose. Tika can
  (its PDF and Microsoft parser modules), but they bring PDFBox and Apache
  POI with xmlbeans (many MB). Instead:
  - **PDF**: Apache PDFBox (Apache-2.0; a few MB with fontbox and
    pdfbox-io, to check): the document information dictionary (Title,
    Author, Subject, Keywords, Creator, Producer, CreationDate, ModDate),
    the page count, and the XMP metadata stream, which is RDF with Dublin
    Core (`dc:title`, `dc:creator`, `dc:description`, `dc:rights`), read
    with xmpcore as for images.
  - **DOCX, XLSX, PPTX**: no library needed: open the file as a file
    system (`FileSystems.newFileSystem(path)`, the JDK's zip file system
    in `jdk.zipfs`, which the runtime image must then include, e.g. by
    `requires jdk.zipfs` or jlink's `--add-modules`, as it is a service
    provider) and read `docProps/core.xml`
    (Dublin Core: title, creator, description, subject, keywords, created,
    modified), `docProps/app.xml` (application, pages, words) and
    `docProps/custom.xml` (custom properties, where a licence may be).
    LibreOffice and OpenOffice's OpenDocument files (`.odt`, `.ods`,
    `.odp`, `.odg`, and the older `.sxw` and friends) likewise: a ZIP with
    `meta.xml` (Dublin Core and `meta:` statistics such as page count).
- **Dates from embedded metadata**, as `dateCreated` and `dateModified`
  (and `datePublished` only if the file says so), rather than the file
  system's times, which copying changes:
  - EXIF: DateTimeOriginal (taken), DateTimeDigitized, DateTime
    (changed), as `2026:04:20 15:36:54` without a time zone, unless
    OffsetTimeOriginal (EXIF 2.31) gives it; GPS has its own UTC date.
  - XMP (images and PDF): `xmp:CreateDate`, `xmp:ModifyDate`,
    `photoshop:DateCreated`, `dc:date`, ISO 8601.
  - PDF information dictionary: CreationDate and ModDate as
    `D:20260420153654+01'00'`.
  - Office: `dcterms:created` and `dcterms:modified` in core.xml (W3CDTF,
    usually UTC); OpenDocument: `meta:creation-date` and `dc:date` in
    meta.xml (often without a zone).
  Written as ISO 8601, with the zone only when the file gives one; when
  sources disagree (e.g. XMP and EXIF), prefer the most precise and say
  which.
- **Licences from embedded metadata**, across images and documents:
  SVG's RDF has `cc:license`; XMP (in JPEG, PNG and PDF) has `cc:license` (Creative Commons' schema,
  a URL), `xmpRights:WebStatement` (a URL), `xmpRights:UsageTerms` and
  `dc:rights` (text); EXIF has Copyright and IPTC CopyrightNotice (text);
  Office files may name one in custom properties. Match URLs with
  `SPDXLicenses.byLink` (e.g. `https://creativecommons.org/licenses/by/4.0/`
  is CC-BY-4.0) and SPDX identifiers in text with `meant`; names such as
  "CC BY 4.0" or "Creative Commons Attribution 4.0 International" need
  matching by name too. Offer the match as the file's `license`, and keep
  rights text as `copyrightNotice` or `creditText`.

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

