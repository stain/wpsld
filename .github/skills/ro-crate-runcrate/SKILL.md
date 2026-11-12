---
name: ro-crate-runcrate
description: "Use the `runcrate` CLI to report on, replay or convert Workflow Run RO-Crates (Process, Workflow and Provenance Run Crates): list the actions, tools, inputs and outputs of a crate, re-run its workflow, or convert a CWLProv research object. Use whenever the user has a run crate and asks what was run, wants to re-execute it, or has a cwltool --provenance bundle to turn into a crate. Do not hand-parse run crates when this tool answers the question."
---

# runcrate

Source: https://github.com/ResearchObject/runcrate (PyPI `runcrate`, 0.6.2 tested). Works on run crates for the Workflow Run Crate profiles (see `ro-crate-workflows`).

## Do not guess
Ask the user for facts about them and their project: authors, publisher, organisation, affiliation, ORCID/ROR/DOI, licence, funders, dates of events, and software or tool versions. Do not infer them from the user's email address, username, git config or machine, and never invent identifiers. You may derive what you can observe: file sizes and media types from the files, `tool --version` output, titles and descriptions from the content (say that you did).
If you cannot ask: leave optional properties out. For what the root data entity must have (`license`, `name`, `description`, `datePublished`) and for any other property the specification, a profile or the validator requires (for example `version` on a `SoftwareApplication` or `ComputerLanguage` you could not run), use an obviously temporary value that starts with `TODO:` (for example `"license": "TODO: choose a licence"`; today's date is fine for the `datePublished` of a draft), and list every `TODO:` value in your answer. The lint script (`ro-crate-validator`) warns about `TODO:` values. Never publish, deposit or hand over a crate that still contains one.

## Install in its own environment
```bash
python -m venv /tmp/runcrate && /tmp/runcrate/bin/pip install runcrate
```
Use a separate virtualenv per RO-Crate tool: tools pin different `rocrate` versions. In a shared environment where `rocrate-action-recorder` had downgraded `rocrate` to 0.15.0, `runcrate report` failed on a 1.3 crate with `ValueError: version '1.3' not supported`; in a clean environment (rocrate 0.16.0) the same crate worked.

## Commands
```bash
runcrate report <crate dir>        # actions, instruments, inputs and outputs (tested)
runcrate run [--dry-run] [--keep-wd] [--executable CMD] <crate dir|zip>   # re-run the workflow (not tested here)
runcrate convert -o <out crate> [-l LICENSE-URL] [-w NAME] [--readme README.md] <CWLProv RO dir>   # CWLProv to Workflow Run RO-Crate (not tested here)
```
`report` output for a Process Run Crate fixture:
```
action: #run1
  instrument: https://www.imagemagick.org/ (SoftwareApplication)
  ended: 2025-01-31T17:01:07+10:00
  inputs: in.jpg
  outputs: out.jpg
```

## Guidance
- `runcrate run` executes the crate's workflow on this machine: only with the user's consent, preferably with `--dry-run` first, on crates you trust (see the trust rules in `ro-crate-consume`).
- For `convert`, give the licence yourself (`-l`); do not guess one. Validate the result (`ro-crate-validator`); note the validator's run-crate profiles lag the current 0.6 profiles.
- To record new runs rather than report on them see `ro-crate-action-recorder` and `ro-crate-workflows`.
