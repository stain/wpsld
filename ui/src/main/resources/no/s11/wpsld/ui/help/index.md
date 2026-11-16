# Help contents

Choose a topic in the list or search the help. Press F1 while working in
the editor for help about the focused control (on macOS, Command-? also
works). Help stays open while you work.

- [Getting started](getting-started.md): create and save a first crate.
- [Opening and saving crates](files.md): use the File menu and handle warnings.
  - [Folders and files](folders.md): open a folder, attached and detached
    crates, add and copy in files.
  - [File formats](formats.md): how files' formats are found, and chosen
    from PRONOM.
- [Profiles](profiles.md): choose guidance for a particular kind of crate.
- [Types](types.md): choose, add and remove types.
- [Entities and identifiers](entities.md): add, remove and merge descriptions.
- [Properties and links](properties.md): find fields and link entities.
  - [People](people.md): authors and others, by their ORCID iD.
  - [Organisations](organisations.md): from the Research Organization
    Registry (ROR).
  - [Publications and DOIs](publications.md): works by their DOI.
  - [Licences](licences.md): from the SPDX License List.
  - [Places](places.md): from GeoNames, on a map.
- [Copy and paste](clipboard.md): copy descriptions or link a copied entity.
- [Views and styles](views.md): change the layout and inspect the preview.

wpsld is developed on GitHub, at
[github.com/stain/wpsld](https://github.com/stain/wpsld): its source code,
releases and these help pages. Report a problem or suggest an improvement
in its [issues](https://github.com/stain/wpsld/issues).

wpsld logs what goes wrong, such as a file it can't read or a web service
that doesn't answer, on your computer only. Attach the log when you report
a problem. Find it in **Edit > Preferences... > Logs**, which opens its
folder, sets how much to log, and clears the logs. The folder is
`~/.local/state/wpsld/logs` on Linux, `%LOCALAPPDATA%\wpsld\Logs` on
Windows and `~/Library/Logs/wpsld` on macOS. The latest log is
`wpsld.0.log`; each log is started anew at 20 MB, and the last three are
kept.
