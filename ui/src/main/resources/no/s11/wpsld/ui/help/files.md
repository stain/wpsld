# Opening and saving crates

wpsld edits an RO-Crate's metadata; it does not create a ZIP or deposit
the crate. How it works with a crate's folder and its files, including
copying files in, is described in [Folders and files](folders.md).

## Start or open

Choose **File > New RO-Crate** to start a new description. Choose
**File > Open...** and select an existing RO-Crate metadata file, usually
`ro-crate-metadata.json`, to edit it. wpsld reports errors it cannot open
and may show warnings about a file it did open. It also tries to choose a
declared [profile](profiles.md). Choose **File > Open Folder...** to open
a folder as a crate (see [Folders and files](folders.md)).

Before replacing a description with New or Open, or when using **File >
Close Window** or **File > Exit**, wpsld asks what to do with unsaved
changes. Choose **Save**, **Don't Save**, or **Cancel** to keep editing.

## Save

Choose **File > Save** to write to the current metadata file. For a new
description, choose a folder and file name, normally
`ro-crate-metadata.json`. Choose **File > Save As...** to save at another
location or under another name. Other tools may expect the standard
`ro-crate-metadata.json` name, and a file of another name is a detached
crate (see [Attached and detached crates](folders.md#attached-and-detached-crates)).

File identifiers such as `data.csv` are paths relative to the metadata
file's folder, for an attached crate. wpsld does not copy files when
saving. If **Save As...** would attach the crate to a folder, as
`ro-crate-metadata.json`, that lacks some of its files, wpsld first says
how many of the crate's files are there and how many not, and how many of
the folder's files the crate doesn't describe. Choose **Save Here** to
attach it to that folder anyway, **Save Detached...** to save it under
another name, such as `bird-survey-ro-crate-metadata.json`, apart from
that folder's files, or **Cancel**.

wpsld saves by writing the whole file beside the old one first and then
putting it in its place, so that a crash or a full disk never leaves a
half-written metadata file. A symbolic link to the metadata file stays a
link: the file it links to is saved.

Before saving, wpsld reports missing requirements it detects. Choose
**Cancel** to correct them, or **Save Anyway** to save an incomplete
description. For an attached crate, the checks also report files and
folders it describes that are not in its folder, or are there with other
upper and lower case (which Windows and macOS find, but other computers
don't), paths out of its folder (`../`), which an attached crate can't
have, and Datasets whose `@id` doesn't end with `/`. These checks are not
a complete validator; validate the finished crate and check its contents
before sharing. The [Getting started](getting-started.md) guide shows how
to correct a warning.

Saving a crate of RO-Crate 1.1 or later beside RO-Crate 1.0's
`ro-crate-metadata.jsonld` offers to remove that old file (a symbolic link
of that name can stay), and saving an attached crate with an
`ro-crate-preview.html` offers to move it aside or delete it, as it no
longer shows the crate as saved.

## Changes on disk

If another application changed the metadata file, read the warning before
choosing how to proceed. **Reload** discards your unsaved changes in favour
of the file on disk; **Keep My Changes** keeps editing them. When saving a
changed file, **Save Anyway** overwrites the other changes, **Save As...**
saves yours elsewhere, and **Cancel** leaves them unsaved. If the file was
moved or deleted, you can save it again at the old location or choose
another location.
