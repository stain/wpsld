---
name: ro-crate-excel
description: "Create or update an RO-Crate from a folder of files via an Excel spreadsheet with the ro-crate-excel tools (rocxl / xlro): generate a spreadsheet of the files, let people fill in metadata, and merge it back into ro-crate-metadata.json. Use when metadata is gathered by non-programmers in spreadsheets, or the user asks to go from Excel or a folder listing to an RO-Crate. Requires Siegfried."
---

# ro-crate-excel (`rocxl`, `xlro`)

Source https://github.com/Language-Research-Technology/ro-crate-excel (npm `ro-crate-excel` 1.2.1; beta on the tools list). Tested end to end by two agents on a sample folder once Siegfried was installed.

## Do not guess
Ask the user for facts about them and their project: authors, publisher, organisation, affiliation, ORCID/ROR/DOI, licence, funders, dates of events, and software or tool versions. Do not infer them from the user's email address, username, git config or machine, and never invent identifiers. You may derive what you can observe: file sizes and media types from the files, `tool --version` output, titles and descriptions from the content (say that you did).
If you cannot ask: leave optional properties out. For what the root data entity must have (`license`, `name`, `description`, `datePublished`) and for any other property the specification, a profile or the validator requires (for example `version` on a `SoftwareApplication` or `ComputerLanguage` you could not run), use an obviously temporary value that starts with `TODO:` (for example `"license": "TODO: choose a licence"`; today's date is fine for the `datePublished` of a draft), and list every `TODO:` value in your answer. The lint script (`ro-crate-validator`) warns about `TODO:` values. Never publish, deposit or hand over a crate that still contains one.

## Install
```bash
npm install ro-crate-excel                 # provides rocxl and xlro (same tool)
```
It needs **Siegfried** (`sf`, file-format identification): download the binary from https://github.com/richardlehane/siegfried/releases, put `sf` on `PATH`, run `sf -update` once (the signature file `default.sig` lands in `~/siegfried/` or `~/.local/share/siegfried/`, depending on version and HOME). Without it the tool prints `File identification error ... Have you installed Siegfried?` and writes nothing.

## Use
```bash
cp -r folder folder-work && rocxl -r folder-work       # first run: spreadsheet + crate
# collaborators fill in ro-crate-metadata.xlsx
rocxl -r folder-work                                   # second run: merge the edited sheet into ro-crate-metadata.json
```
- Use `-r` (recurse): without it files in subfolders are skipped. First run writes `ro-crate-metadata.xlsx`, `ro-crate-metadata.json` and `ro-crate-preview.html` **in the folder**; sheets: `RootDataset` (Name/Value list for the crate), `@type=File`, `@type=Dataset` (one row per file/folder with `@id`, `name`, `description`, size, date, format; extra columns allowed), `@type=DigitalFormat` (PRONOM-identified formats).
- Whichever of the spreadsheet and the JSON is newer wins; the folder is re-scanned on every run and file information is merged in. Other options: `-b/--bag [dir]` BagIt bag(s), `-z/--zip` (with `--bag`), `-a/--add` merge `additional-ro-crate-metadata.xlsx`, `-j/--JSON` use the JSON rather than the spreadsheet, `-p/--partOf`, `-d/--depth`, `-m/--maxfiles`, `-u/--url`, `-c/--cratescript`.

## Checked output and cautions
- It declares **RO-Crate 1.2** (to move to 1.3 follow the 1.2 to 1.3 step in `ro-crate-compatibility`), writes `license` as a plain string (give a licence URL or a licence entity), and in one of two round trips dropped `contentSize` of an edited file: compare before and after.
- Folder entities come out as `data` and `docs`: add the trailing `/` to their `@id`s (and in `hasPart`) before validating; the lint script warns about it.
- It rewrites files in place: work on a copy or under version control.
- Multi-valued cells are JSON-like arrays (`["a","b"]`); brief the people filling the sheet.
- Review the generated crate, complete the root (`name`, `description`, `datePublished`, `license`), validate (`ro-crate-validator`; not done in the test runs).
- GUI alternatives without Excel: Crate-O, `chaos2crate` (see `ro-crate-tools`).
