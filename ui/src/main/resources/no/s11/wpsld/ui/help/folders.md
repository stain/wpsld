# Folders and files

An RO-Crate is often a folder: its metadata file, `ro-crate-metadata.json`,
beside the data files it describes. wpsld works with such a folder: it
lists the files the crate doesn't describe yet, adds them, copies files in
from elsewhere, and keeps up with the folder as it changes. It never
deletes or moves your data files.

## Open a folder

Choose **File > Open Folder...** to open a folder as a crate: its
`ro-crate-metadata.json`, if it has one, else a new crate of the folder,
which is saved in it as `ro-crate-metadata.json` without asking where.
Opening a folder shows **View > Entities by Folder**. Its files are not
added by themselves: those the crate doesn't describe are listed greyed
out (see **View > Show Files Not in Crate** in [Views and
styles](views.md)), to add with **Crate > Add to Has Part**, or **Add
Dataset for Folder** for a folder, also several at once. Opening a
crate's `ro-crate-metadata.json` with **File > Open...** works the same.

## Attached and detached crates

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

## Add files

**Insert > Files...** adds files you choose to an attached crate, as
parts of the Dataset of their folder (or of the crate itself), with their
size, date modified and [format](formats.md). Files in the crate's folder
are added as they are.

Files from elsewhere are copied in first, into a folder of the crate you
choose: if it has files of the same names, choose **Replace**, **Keep
Both** (copied as e.g. `counts (2).csv`) or **Skip**. Copying happens in
a window of its own, with its progress and **Cancel**, so you can go on
editing. Each file is written in full under a hidden name before it gets
its own, so a full disk or a file that can't be read leaves no half
copies; what wasn't copied, and why, is told at the end, and the files
that were copied are added.

## Keep up with the folder

wpsld watches an attached crate's folder: files added, removed or changed
there, also by other programs, show in a moment, once the copying has
quietened down. **View > Refresh** (F5) looks again at once, e.g. where
the folder can't be watched. Files the crate describes that are not in
the folder are shown in red (see [Views and styles](views.md)).

**Crate > Update File Details...** brings the files an attached crate
describes up to date with its folder: their size (`contentSize`) and date
modified, where missing or changed since. Optionally, it also fills in
their [formats](formats.md) where they have none.

## Show in Explorer or Finder

**File > Show in Explorer** (**Show in Finder** on macOS, **Show in File
Manager** on Linux) shows the crate's metadata file in its folder. On the
right-click menu of the entities, it shows the file or folder of the one
selected.
