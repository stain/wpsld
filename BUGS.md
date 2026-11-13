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
- **The HTML preview (`ro-crate-preview.html`)**, seen on
  `debug/workshop8`:
  - **Files only mentioned get no link to the file**, e.g.
    `data/exports/csv/dim_date.csv` and the ImageObject `Picture1.png`,
    in the root's `mentions` rather than reached by `hasPart`:
    `CratePreview` links only data entities' paths. Link every entity
    whose `@id` is a relative path (not `#local` or `_:blank`).
  - **Descriptions lose their line breaks**: written as one `<p>`, so
    they look different than in the app. Each blank-line-separated part
    a `<p>`, a lone newline a `<br>`; or render as Markdown when it looks
    like Markdown (see "Markdown descriptions in the preview" below:
    tells are `` `code` ``, `_italics_`, `**bold**`, `[link](http…)`,
    lines starting `- ` or `#`).
  - **A link back to the top** (the crate) from each entity's section.
  - **The JSON-LD in a `<script type="application/ld+json">`** in the
    `<head>`, as the RO-Crate specification asks of previews (with `</`
    escaped so that it can't end the script).

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
- **Markdown descriptions in the preview**: render `description` as
  Markdown when it is likely to be Markdown, by heuristics (headings,
  lists, emphasis, links such as `[text](url)`), or when the crate says so
  (`encodingFormat` `text/markdown`). ROHub's and COMPSs' crates write
  Markdown descriptions; see also PLAN.md.

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

