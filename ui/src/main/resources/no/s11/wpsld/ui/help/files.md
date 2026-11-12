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
open. A metadata file of another name is *detached*: its relative
identifiers name no files on this computer. A crate opened from a folder
that has none of the files it lists, e.g. downloaded on its own, is
treated as detached too, and wpsld says so. **Crate > Attached to Folder**
changes this.

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
file's folder, for an attached crate. If you save in another folder, check that the described
files exist at the corresponding paths there; wpsld does not copy them.

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
