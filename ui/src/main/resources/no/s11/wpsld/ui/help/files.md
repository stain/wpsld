# Opening and saving crates

wpsld edits an RO-Crate's metadata; it does not collect or copy your data
files, create a ZIP, or deposit the crate.

## Start or open

Choose **File > New RO-Crate** to start a new description. Choose
**File > Open...** and select an existing RO-Crate metadata file, usually
`ro-crate-metadata.json`, to edit it. wpsld reports errors it cannot open
and may show warnings about a file it did open. It also tries to choose a
declared [profile](profiles.md).

Choose **File > Open Folder...** to open a folder as a crate: its
`ro-crate-metadata.json`, if it has one, else a new crate of the folder,
which is saved in it as `ro-crate-metadata.json`. Its files are not added
by themselves: **View > Entities by Folder** lists those the crate doesn't
describe greyed out, to add with **Crate > Add to Has Part** (or **Add
Dataset for Folder**), also several at once. A file added gets its size,
date modified and, by its extension, its format from PRONOM.

### Attached and detached crates

A crate whose metadata file is named `ro-crate-metadata.json` (or
`ro-crate-metadata.jsonld`) is *attached* to its folder: identifiers such
as `data/counts.csv` are its files there, which the preview and **↗**
open, and **Entities by Folder** also lists the folder's files that the
crate doesn't describe yet, to add.

A metadata file of another name, e.g. `bird-survey-ro-crate-metadata.json`,
is *detached*, as RO-Crate calls a crate kept apart from its files:

- Its relative identifiers name no files on this computer: the preview
  shows `data/counts.csv` as text, not a link, and **↗** is not
  available for it (web addresses still open).
- **Entities by Folder** still shows the crate's folders and files by
  their identifiers, but not the files of the folder it was opened from.
- **Crate > Attached to Folder** is not available, as the file is not
  named for a folder.
- **File > Save** writes it back under the same name. Saving it with
  **Save As...** as `ro-crate-metadata.json` attaches it to the folder
  saved in; under any other name it stays detached.
- **File > Open Folder...** only opens `ro-crate-metadata.json` (or
  `.jsonld`), not other metadata files in the folder.

A crate opened from a folder that has none of the files it lists, e.g.
downloaded on its own, is treated as detached too, and wpsld says so;
**Crate > Attached to Folder** attaches it after all, or detaches an
attached crate.

Before replacing a description with New or Open, or when using **File >
Close Window** or **File > Exit**, wpsld asks what to do with unsaved
changes. Choose **Save**, **Don't Save**, or **Cancel** to keep editing.

## Save

Choose **File > Save** to write to the current metadata file. For a new
description, choose a folder and file name, normally
`ro-crate-metadata.json`. Choose **File > Save As...** to save at another
location or under another name. Other tools may expect the standard
`ro-crate-metadata.json` name.

File identifiers such as `data.csv` are paths relative to the metadata
file's folder, for an attached crate. wpsld does not copy files. If **Save
As...** would attach the crate to a folder, as `ro-crate-metadata.json`,
that lacks some of its files, wpsld first says how many of the crate's
files are there and how many not, and how many of the folder's files the
crate doesn't describe. Choose **Save Here** to attach it to that folder
anyway, **Save Detached...** to save it under another name, such as
`bird-survey-ro-crate-metadata.json`, apart from that folder's files, or
**Cancel**.

wpsld saves by writing the whole file beside the old one first and then
putting it in its place, so that a crash or a full disk never leaves a
half-written metadata file. A symbolic link to the metadata file stays a
link: the file it links to is saved.

For an attached crate, the checks before saving also report files and
folders it describes that are not in its folder, or are there with other
upper and lower case (which Windows and macOS find, but other computers
don't), paths out of its folder (`../`), which an attached crate can't
have, and Datasets whose `@id` doesn't end with `/`. Saving a crate of
RO-Crate 1.1 or later beside RO-Crate 1.0's `ro-crate-metadata.jsonld`
offers to remove that old file (a symbolic link of that name can stay),
and saving an attached crate with an `ro-crate-preview.html` offers to
move it aside or delete it, as it no longer shows the crate as saved.

Before saving, wpsld reports missing requirements it detects. Choose
**Cancel** to correct them, or **Save Anyway** to save an incomplete
description. These checks are not a complete validator; validate the
finished crate and check its contents before sharing. The
[Getting started](getting-started.md) guide shows how to correct a warning.

## Changes on disk

If another application changed the metadata file, read the warning before
choosing how to proceed. **Reload** discards your unsaved changes in favour
of the file on disk; **Keep My Changes** keeps editing them. When saving a
changed file, **Save Anyway** overwrites the other changes, **Save As...**
saves yours elsewhere, and **Cancel** leaves them unsaved. If the file was
moved or deleted, you can save it again at the old location or choose
another location.
