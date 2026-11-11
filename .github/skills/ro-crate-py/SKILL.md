---
name: ro-crate-py
description: "Use the ro-crate-py `rocrate` CLI and Python library to create, load, modify and zip RO-Crates instead of writing custom crate code: rocrate init, add, write-zip, ROCrate().add_file. Use whenever a task involves scripting crate creation or edits in Python, bootstrapping ro-crate-metadata.json from a folder, adding workflows or files, or producing a crate zip. Remember its output still needs name, description and licence on the root."
---

# ro-crate-py (`rocrate`)

Source https://github.com/ResearchObject/ro-crate-py, PyPI `rocrate` (status "beta" on the RO-Crate tools list). Commands below were run with rocrate 0.16.0; check `--help` for your version.

```bash
python -m venv /tmp/rc && /tmp/rc/bin/pip install rocrate
```

## Do not guess
Ask the user for facts about them and their project: authors, publisher, organisation, affiliation, ORCID/ROR/DOI, licence, funders, dates of events, and software or tool versions. Do not infer them from the user's email address, username, git config or machine, and never invent identifiers. You may derive what you can observe: file sizes and media types from the files, `tool --version` output, titles and descriptions from the content (say that you did).
If you cannot ask: leave optional properties out. For what the root data entity must have (`license`, `name`, `description`, `datePublished`) and for any other property the specification, a profile or the validator requires (for example `version` on a `SoftwareApplication` or `ComputerLanguage` you could not run), use an obviously temporary value that starts with `TODO:` (for example `"license": "TODO: choose a licence"`; today's date is fine for the `datePublished` of a draft), and list every `TODO:` value in your answer. The lint script (`ro-crate-validator`) warns about `TODO:` values. Never publish, deposit or hand over a crate that still contains one.

## CLI
```bash
rocrate init -c <crate-dir>                 # scan the directory, write ro-crate-metadata.json
rocrate init --gen-preview                  # also ro-crate-preview.html
rocrate init -e tmp,.git                    # exclude names
rocrate init --crate-version 1.2            # target another released version (default 1.3)
rocrate add file|dataset|workflow ...       # add entities (see `rocrate add <cmd> --help`)
rocrate write-zip -c <crate-dir> out.zip    # zip an attached crate
```
`rocrate init` makes every file and directory a data entity, sets `datePublished` to a timestamp and the 1.3 descriptor/context, but it does **not** set `name`, `description` or `license`, which the root must have. **Always complete the root afterwards** (by hand or via the library), then validate with the `ro-crate-validator` skill.

## Library
```python
from rocrate.rocrate import ROCrate
crate = ROCrate()                     # new crate; ROCrate("existing-dir") to load one
crate.name = "Title"; crate.description = "What it holds"
crate.license = "https://creativecommons.org/licenses/by/4.0/"
f = crate.add_file("local/results.csv", dest_path="data/results.csv",
                   properties={"name": "Results", "encodingFormat": "text/csv"})
crate.write("out-dir")                # or crate.write_zip("out.zip")
```
In a test with 0.16.0 `write` added `datePublished` automatically, wrote `ro-crate-metadata.json` and copied the file; `license` given as a URL string became a plain value (add a licence entity yourself when you want a described licence). Add people with `crate.add(Person(crate, "https://orcid.org/...", properties={"name": "..."}))` (import `Person` from `rocrate.model`).

## Guidance
- Do not leave a bare `rocrate init` metadata file in the folder next to a completed crate elsewhere (for example a finished zip): complete the root in the folder's own metadata (`ROCrate(folder, init=True)`, set the root properties, `write()`), then zip that.
- Use this instead of writing JSON serialisation; hand-edit only what the library does not cover, and keep `ro-crate-create` rules (reachability, contextual entities, no invented identifiers).
- Check what the tool wrote: `python -m json.tool ro-crate-metadata.json`, then validate. Don't trust defaults for descriptions, licences or authors.
- Loading a crate preserves its existing entities; confirm the declared version before saving over it.
