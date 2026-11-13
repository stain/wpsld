# Bugs and requests to work through

Reported while trying the entity tree and icons (November 2026), kept here
to do one at a time. Longer-term plans are in [PLAN.md](PLAN.md).

## Bugs

- **Compact IRIs as `@id` are not expanded to open them.** The ↗ beside
  `@id` and the preview's links use the `@id` as written. An `@id` such as
  `s:ActiveActionStatus` should be expanded with the crate's `@context` to
  `http://schema.org/ActiveActionStatus` before opening, in the form and in
  the preview. (`#local` and `_:blank` ids still can't be opened.)
- **Text values of URL properties should be links in the preview.**
  Properties whose range is [URL](https://schema.org/URL), e.g. `url`,
  `contentUrl`, `sameAs`, `isBasedOn`, should show a text value
  (`"url": "http://example.com/"`) as a link. It mostly works already, maybe
  only because the text starts with `http`/`https`: check, and decide by
  the property's range, from schema.org or the profile.
- **Remove disabled for the first entity of a type**: reported with a type
  chosen in the type selector. Not reproduced: clicking the first entity
  of a type and choosing Remove works in tests, also on the ROHub crate
  below. There, the root dataset is also typed File, so it is listed first
  under Dataset, File and CreativeWork, where Remove is rightly disabled.
  Needs the steps that show it.
- **The "Entities" heading in OS/2 Warp dark** is dark grey on the dark
  sidebar, barely readable.
- **The "changed by another application" dialog cuts off its text in
  OS/2 Warp.** With unsaved changes, when the file changed on disk (e.g.
  `workshop8\ro-crate-metadata.json`), the text under the header ends in
  "or keep them and ..." instead of wrapping onto more lines. Reload and
  Keep My Changes work. The alert is in `CrateEditor.checkDisk()`; Warp's
  font is larger, so the dialog may need to grow, or the content label to
  wrap (check the Save Anyway dialog in `askBeforeOverwriting` too).
- **Parts that are not File get no link to open them in the preview**,
  e.g. an `ImageObject` in `hasPart`. Not reproduced: `CratePreview`
  (the saved `ro-crate-preview.html`) links every data entity reached by
  `hasPart` with a relative `@id` to its file, whatever its type, and the
  Preview tab (`EntityPreview.identifier`) links any relative `@id` whose
  file exists beside the crate. A test with an ImageObject `photo.jpg` in
  the root's `hasPart` gets `<a href="photo.jpg">`. Needs the crate, and
  which preview (saved HTML or Preview tab), that shows it.

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
- **Say what Paste left out and merged**: pasting entities whose `@id`
  the crate already has, e.g. files or absolute URIs copied within the
  same crate, now does nothing silently when they are the same
  (`Crate.paste` leaves them out) and asks once per entity when they
  differ (Merge or Keep Existing). Afterwards, show one dialog for the
  whole paste, as some of the entities may be new and others not:
  how many were pasted, which were left out as already in the crate,
  which were merged into existing ones and which kept as they were, with
  the dangling and ambiguous references `pasteEntity` already reports in
  the same dialog. Nothing to show when all were simply added. A start
  on PLAN.md's "A notice after Paste" (Copy and paste entities, step 2).
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

