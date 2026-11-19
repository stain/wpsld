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

### Crates on the web

Choose **File > Open URL...** and give the address of a crate: of its
`ro-crate-metadata.json`, of the folder it is in, of a page that leads to
it, or a DOI (`10.5281/zenodo.1234`). A window says it is being fetched,
and Cancel keeps the crate you are editing, which stays as it is until the
other is here; then it is opened in its place, in no file, so Save asks
where to save it.

A crate whose metadata file is named `ro-crate-metadata.json` is an
attached crate published on the web: its relative identifiers, such as
`data/table.csv`, are paths in that folder, so with the crate at
`http://example.com/crate1/ro-crate-metadata.json` the arrow beside it
opens `http://example.com/crate1/data/table.csv` in the browser. This is
worked out from where the crate was found in the end, after any redirects,
and the notice at the foot of the window says where identifiers lead.
Another name (`my-ro-crate-metadata.json`), or a service's address with a
query, is a detached crate, which has no folder: its files should have
web addresses of their own. wpsld does not guess where a detached crate's
relative identifiers lead from the address it came from, as a service's
address says nothing of where the files are. If it still has relative
identifiers for files (neither detached nor attached, as repositories'
exports often are), the notice says so and gives what its root could be.

The identifiers stay as written in the crate. Saving it to a file would
make them name files beside the saved one instead, so wpsld asks first
whether to **Make Absolute** (each becomes the web address it leads to,
one step you can undo), **Keep Relative** or Cancel.

### Attach: where the crate's files are

Choose **Crate > Attach...** to say what the crate's relative identifiers
(`data/table.csv`) are relative to, if wpsld doesn't know or has it wrong:

- **A folder on disk**, such as the crate's files downloaded and unpacked
  by hand. wpsld says how many of the crate's files are in it. The crate
  is then the folder's: its files are listed, its links open them, and a
  crate fetched from the web is saved in that folder.
- **A web address**, which can be one the crate suggests (its landing
  page, or its own identifier) or one you type. **Check** asks the server
  whether the crate's first file is there, which it can't always say (a
  server may not allow it, or the files may be inside a zip). Check Web
  Availability then asks for the crate's relative files at that address
  too.
- **Nothing**: detached, so relative identifiers lead nowhere.

Attaching does not change the crate, only where wpsld looks for its files:
the identifiers stay as written.

### Detach: a crate with no folder

A detached crate has no folder, so RO-Crate requires the identifiers of its
files to be absolute addresses. Choose **Crate > Detach...** to make them so:
wpsld asks what the crate's relative identifiers (`data/table.csv`, and
other entities written with a path or a name, but not `#local` ones) are
relative to, and makes each absolute against that address, as one step you
can undo. The address is filled in if it is known, from where the crate is
attached on the web or what it says its root is. **New Anonymous Base**
makes a fresh `arcp://uuid,...` address for a crate that is not published
anywhere, and the address can also be kept as the `@base` of the crate's
`@context`, so that the identifiers can later be made relative again. With a
web address the crate's root (`./`) becomes that address too. A `file:`
address only means something on this computer, so wpsld warns about it.
Two options: **Use a file's own contentUrl (or url)** gives a file that says
where it can be downloaded from that address as its identifier, instead of
the base and its path (not if it says several, as there is no telling
which); and **Check Web Availability afterwards**, on by default for a web
address, asks whether every web address of the crate is there once it is
detached (see [Views](views.md)). Save the detached crate under another name, such as
`survey-ro-crate-metadata.json`, as RO-Crate suggests.

### Crates written in other ways

RO-Crate's JSON-LD is *flattened*: each entity is written once in the
`@graph`, and values link to others by `{"@id": "..."}` alone. Crates made
by other tools don't always follow this. When opening one, wpsld keeps
what it can and says what it did in the warnings:

- An entity written out in full inside a value, with an `@id`, becomes a
  link and an entity of its own (merged with the crate's of that `@id`).
- An object written inside a value without an `@id` is kept as it is,
  with a warning: **Crate > Repair Crate** makes it an entity of its own
  with a new `@id` (see [Entities and identifiers](entities.md)).
- A link written as text, `"{\"@id\": \"...\"}"` in quotes, stays text:
  **Crate > Repair Crate** makes it the link it says.
- Links written with JSON-LD's `@reverse` become links of the entities
  they come from.
- Two entities with the same `@id` are merged.
- Text in a language, or of a datatype, is kept and can be edited (see
  [Properties and links](properties.md)).

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
the File…** to see what it says. With several files selected, **Metadata
from the Files…** reads them all in the background, then offers to fill
in what their entities lack, at once (see below), rather than a dialog
for each. These kinds of files say something:

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
work. If they say
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

Files are read in the background. While they are, the foot of the
window shows the progress; **Show...** brings up the window of the jobs
running, with **Cancel**, **Run in Background** and **Help**, which also
opens by itself for reading you asked for that takes more than a moment.
It shows what each job is at, and the files that couldn't be read. A file that is damaged, or that a reader takes too long over
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
