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
- **Open Folder with files not in the crate**: show them in Entities by
  Folder greyed out, as folders without a Dataset are now. Right-click
  adds them as a Dataset (a folder) or a File, also several selected at
  once. For a file, detect its format from its contents (file magic and
  PRONOM), and fill in its size, media type and other details.
