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
Dataset for Folder**), also several at once. Opening a folder shows
**View > Entities by Folder**.

**Insert > Files...** adds files you choose to an attached crate, as
parts of the Dataset of their folder, with their size, date and format.
Files from elsewhere are copied in first, into a folder of the crate you
choose: if it has files of the same names, choose **Replace**, **Keep
Both** (copied as e.g. `counts (2).csv`) or **Skip**. Copying happens in
a window of its own, with its progress and **Cancel**, so you can go on
editing. Each file is written in full under a hidden name before it gets
its own, so a full disk or a file that can't be read leaves no half
copies; what wasn't copied, and why, is told at the end.

wpsld watches an attached crate's folder: files added, removed or changed
there, also by other programs, show in a moment, once the copying has
quietened down. **View > Refresh** (F5) looks again at once, e.g. where
the folder can't be watched.

**Crate > Update File Details...** brings the files an attached crate
describes up to date with its folder: their size (`contentSize`) and date
modified, where missing or changed since. Optionally, it also fills in
their formats where they have none.

A file's format is found from its content, and then its name: see
[File formats](#file-formats) below.

**File > Show in Explorer** (**Show in Finder** on macOS, **Show in File
Manager** on Linux) shows the crate's metadata file in its folder; on the
right-click menu, it shows the file or folder of the entity selected. A file added gets its size,
date modified and its format (see below).

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

## File formats

When wpsld adds a file from the crate's folder (**Add to Has Part**,
**Insert > Files...**, or **Crate > Update File Details...**), it fills in
the file's `encodingFormat`, if it has none, with its media type, and a
link to its [PRONOM](https://www.nationalarchives.gov.uk/PRONOM/) format
where it can tell which. It looks at the file's content first, the
"magic" of its first bytes, using [Apache Tika](https://tika.apache.org/),
and then at its name. So a PDF named `scan.bin` is still a PDF.

Formats common in research data, and what wpsld writes for them:

- **Tables**: CSV `text/csv` (PRONOM x-fmt/18), tab-separated
  `text/tab-separated-values` (x-fmt/13), Parquet
  `application/vnd.apache.parquet`, Arrow and Feather
  `application/vnd.apache.arrow.file`, SQLite `application/x-sqlite3`
  (fmt/729); SPSS, Stata and SAS data files.
- **HDF5** `application/x-hdf5`, found by its signature also after a user
  block of 512, 1024, 2048... bytes, as some instruments write. NetCDF-4
  and MATLAB 7.3 `.mat` files are HDF5 inside, so they show as HDF5.
  HDF4 is `application/x-hdf`.
- **NetCDF-3** (classic, 64-bit offset and CDF-5) `application/x-netcdf`.
  NASA's **CDF**, which also uses `.cdf`, is `application/x-cdf`, told
  apart by its content.
- **Weather and climate**: GRIB `application/grib`, BUFR
  `application/bufr`.
- **Geography**: GeoJSON `application/geo+json` (fmt/1367), KML
  (fmt/244), shapefiles, LAS point clouds.
- **FITS** `application/fits` (x-fmt/383), **DICOM** `application/dicom`
  (fmt/574), MATLAB 5 `.mat` `application/x-matlab-data`.
- **Life sciences**: FASTA, FASTQ, SAM, VCF and PDB files are text,
  `text/plain`, as they have no media types of their own; BAM files are
  `application/gzip`.
- **Images, sound and video**: PNG `image/png`, TIFF `image/tiff`, JPEG
  2000 `image/jp2`, SVG, WAV, FLAC, MP4.
- **Documents**: PDF `application/pdf`, Markdown `text/markdown`
  (fmt/1149), Word `.docx` and other Office files by their names.
- **Code and notebooks**: Python `text/x-python`, R `text/x-rsrc`,
  Jupyter notebooks `application/x-ipynb+json`.
- **Structured text**: JSON `application/json`, JSON-LD
  `application/ld+json`, YAML `application/yaml`.
- **Archives**: ZIP `application/zip` (x-fmt/263), gzip
  `application/gzip` (x-fmt/266), tar, bzip2, xz, Zstandard, 7-Zip.

Some formats share an extension, and wpsld goes by the content: a
`.vcf` of variant calls isn't taken for a vCard, nor an electron
microscopy `.mrc` map for a MARC library record. Formats without a known
signature or media type, e.g. NumPy `.npy`, Avro, NIfTI or ROOT files,
are `application/octet-stream`.

PRONOM describes each version of a format apart, e.g. PDF 1.4, PDF/A-1b
and so on, and many formats share a media type. wpsld links a PRONOM
format only when exactly one has both the file's media type and its
extension: so PDF, PNG and TIFF files get their media type but no PRONOM
format. If you know the version, choose **New** beside
**Encoding format**, then **Format from PRONOM...** (see [Properties and
links](properties.md)).

A file whose content wpsld doesn't recognise, as many instruments' `.dat`
and `.bin` files are, gets `application/octet-stream` (any bytes), or
`text/plain` if it is text, rather than a guess from an extension that
many formats share. Describe what it is in its **Description**, and,
where the format has a specification on the web, link it in **Encoding
format** too.

## Changes on disk

If another application changed the metadata file, read the warning before
choosing how to proceed. **Reload** discards your unsaved changes in favour
of the file on disk; **Keep My Changes** keeps editing them. When saving a
changed file, **Save Anyway** overwrites the other changes, **Save As...**
saves yours elsewhere, and **Cancel** leaves them unsaved. If the file was
moved or deleted, you can save it again at the old location or choose
another location.
