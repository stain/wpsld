---
name: ro-crate-action-recorder
description: "Record runs of Python command-line tools into an RO-Crate (Process Run Crate provenance) with the rocrate-action-recorder library for argparse, click or cyclopts CLIs, instead of hand-writing CreateAction entities. Use when the user maintains a Python CLI and wants each invocation's inputs, outputs, software version and time captured as RO-Crate provenance, or asks how to automate provenance capture."
---

# rocrate-action-recorder

Source https://github.com/i-VRESSE/rocrate-action-recorder (PyPI `rocrate-action-recorder` 0.7.1 tested; Apache-2.0; beta). It is a library you add to **a Python CLI you control**; it does not wrap arbitrary shell commands. For other tools write the Process Run Crate yourself (`ro-crate-workflows`) or use an engine exporter (`ro-crate-tools`).

## Do not guess
Ask the user for facts about them and their project: authors, publisher, organisation, affiliation, ORCID/ROR/DOI, licence, funders, dates of events, and software or tool versions. Do not infer them from the user's email address, username, git config or machine, and never invent identifiers. You may derive what you can observe: file sizes and media types from the files, `tool --version` output, titles and descriptions from the content (say that you did).
If you cannot ask: leave optional properties out. For what the root data entity must have (`license`, `name`, `description`, `datePublished`) and for any other property the specification, a profile or the validator requires (for example `version` on a `SoftwareApplication` or `ComputerLanguage` you could not run), use an obviously temporary value that starts with `TODO:` (for example `"license": "TODO: choose a licence"`; today's date is fine for the `datePublished` of a draft), and list every `TODO:` value in your answer. The lint script (`ro-crate-validator`) warns about `TODO:` values. Never publish, deposit or hand over a crate that still contains one.

## Install (own environment, Python 3.13+)
```bash
python -m venv /tmp/rar && /tmp/rar/bin/pip install rocrate-action-recorder      # extras: [click], [cyclopts]
```
It pins `rocrate==0.15.0`: do not install it into an environment you share with other RO-Crate tools.

## Use (argparse, tested)
```python
import argparse, sys
from pathlib import Path
from rocrate_action_recorder import recorded_argparse

parser = argparse.ArgumentParser(prog="example-cli", description="Example CLI")
parser.add_argument("--version", action="version", version="%(prog)s 1.2.3")
parser.add_argument("input", type=Path); parser.add_argument("output", type=Path)

@recorded_argparse(parser=parser, input_files=["input"], output_files=["output"],
                   dataset_license="CC-BY-4.0")        # `enabled_argument=` adds an opt-out flag
def handler(args):
    args.output.write_text(args.input.read_text().upper())

handler(parser.parse_args())
```
Each run adds to `ro-crate-metadata.json` in the working directory: the input/output files, a `SoftwareApplication` (name, version from `--version`), and a `CreateAction` with `instrument`, `object`, `result`, `agent`, start/end times, under the Process Run Crate profile. Lower level: `rocrate_action_recorder.record(...)`, `record_click`, `record_argparse`.

## Check and fix its output
What it wrote in a test: RO-Crate **1.1** context and `conformsTo`, Process Run Crate **0.5**, agent `root` taken from the OS user, licence as a string. `rocrate-validator -p ro-crate-1.3` failed it (descriptor/`conformsTo` references, `SoftwareApplication` needs a `url`), and the offline lint script warned that the action, software and person are not reachable from the root. Fix-up: run `python scripts/link_actions.py <crate dir>` (standard library; path relative to this skill folder) to add the actions to the root's `mentions`; pass the software's real URL with `--software-url ID=URL` (it never invents one; without it the script warns). Then set a real person for `agent`, a real crate `name`/`description`, and change the declared version only deliberately (1.1 to 1.3 needs the steps in `ro-crate-compatibility`, not just a new `conformsTo`). Decide whether the recorded user name is acceptable to publish. Actions accumulate in the same crate (each action's `@id` is the command line); the recorder runs only for invocations that go through your handler, not when `main` logic is imported as a library.

Writing your own recorder (a context manager around the CLI that builds a crate with ro-crate-py) works too, but duplicates this tool; in an eval an agent without this skill did exactly that, with parameters and hashes, unvalidated.
