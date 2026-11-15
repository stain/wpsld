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
of that name can stay).

## Metadata from the file

Some files say what they are, who made them, when and under which
licence. Right-click a file in the entity list and choose **Metadata from
the File…** to see what it says:

- Office files (`.docx`, `.xlsx`, `.pptx`) and OpenDocument files from
  LibreOffice or OpenOffice (`.odt`, `.ods`, `.odp`, and the older
  `.sxw`): title, description, author, keywords, language, when created
  and last changed, and a licence in their custom properties.
- PDFs: their document information (title, subject, author, keywords,
  dates) and their XMP metadata.
- EPS and PostScript (also older Illustrator `.ai`), Photoshop (`.psd`)
  and InDesign files: their XMP metadata.
- Photos and images (JPEG, PNG, TIFF, WebP, HEIC and HEIF, AVIF, GIF,
  BMP, and camera raw files: DNG, CR2, CR3, NEF, ARW, ORF, RW2, RAF):
  description, artist, copyright, keywords, when taken and changed, a
  licence in their XMP, their size in pixels, the camera and its
  settings, and where the photo was taken, if the camera recorded it.
- Videos (MP4, QuickTime `.mov`): title, description, author, when
  made, how long, their size in pixels, and from phones the phone and
  where the video was taken. A phone's own time includes its time zone;
  otherwise the time is in UTC.
- SVG drawings: their title, description and size, and the metadata
  Inkscape writes (creator, date, rights, keywords, licence).

A licence named in a copyright notice, e.g. "© 2026 Ada Lovelace, CC BY
4.0", is offered as the licence too.

Tick what to give the file's entity. Those it has no value for yet are
ticked to start with, except where a photo or video was taken and the
camera or phone: a photo can tell more than meant, such as where someone lives, so give
those only on purpose. Hover over a value to see where in the file it is, and the
entity's value now. Authors become links to a Person of that name, added
if the crate has none; a licence known to SPDX a link to it; and where a
photo was taken a new Place with its point on the map. **Edit > Undo**
takes it all back at once. To fill in many files at once, where they have
no value yet, use **Crate > Update File Details...**.

When you add files (**Insert > Files...**, or adding files of the crate's
folder as parts), up to five of them are read in the background while you
work; close the notice "Reading the files added..." to stop. If they say
something their entities lack, a notice at the foot of the window offers
it: **Review...** opens the dialog above for one file, and **Fill in**
gives several files what they say where they have no value yet (not
where or the camera). More files, or very large ones, are not read at
once: the notice offers **Read what they say...** instead.

Nothing a file says is given to its entity until you click: what you type
meanwhile is kept, and **Edit > Undo Metadata from Files** takes back
just what was filled in. If you stop reading, what was read so far is
offered. Opening another crate stops reading the files of this one,
asking first if you asked for it.

Files are read in the background; a large one shows its progress, with
**Cancel**. A file that is damaged, or that a reader takes too long over
(20 seconds), says what could be read, and which reader failed. A reader
still busy with two such files is skipped for others until it is done.

## HTML preview

Saving an attached crate also writes `ro-crate-preview.html` beside its
metadata file: the crate described as a web page, to read in a browser.
It has the crate's name and description, a list of its entities (files
and folders first, then the others by type) and a section for each, with
its properties; links go to the entities it refers to, to its files, and
to the web. Descriptions keep their paragraphs and line breaks, or are
shown formatted when they are written in Markdown (headings, lists,
**bold**, links, tables). The page also holds a copy of the metadata, for
programs that read it. The crate describes the page, as RO-Crate shows it: an entity
`ro-crate-preview.html`, a CreativeWork about the crate, not one of its
parts.

A preview made by another tool is not written over without asking: choose
**Replace** to move it aside, into `ro-crate-preview_files/` as
`old-ro-crate-preview-` with its date, and write wpsld's, or **Keep** to
leave it as it is, no longer showing the crate as saved.

## Changes on disk

If another application changed the metadata file, read the warning before
choosing how to proceed. **Reload** discards your unsaved changes in favour
of the file on disk; **Keep My Changes** keeps editing them. When saving a
changed file, **Save Anyway** overwrites the other changes, **Save As...**
saves yours elsewhere, and **Cancel** leaves them unsaved. If the file was
moved or deleted, you can save it again at the old location or choose
another location.
