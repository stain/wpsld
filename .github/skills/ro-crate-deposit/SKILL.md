---
name: ro-crate-deposit
description: "Deposit an RO-Crate to Zenodo or an InvenioRDM repository with the rocrate-zenodo and rocrate-inveniordm command-line tools, which fill the repository metadata from the crate, with sandbox-first and review-before-publish steps. Use when the user wants to upload, archive, get a DOI for or publish a crate in Zenodo or InvenioRDM. Always confirm before uploading or publishing. For packaging, BagIt, hosting and identifiers see ro-crate-publish."
---

# Depositing a crate to Zenodo / InvenioRDM

Uploading and publishing are outward-facing and hard to undo (a published DOI cannot be removed). Confirm the destination, the crate content, licence and authors with the user first; use the sandbox first; do not publish unless asked. Tokens are secrets: never print them, put them in the crate, or commit them.

## Do not guess
Ask the user for facts about them and their project: authors, publisher, organisation, affiliation, ORCID/ROR/DOI, licence, funders, dates of events, and software or tool versions. Do not infer them from the user's email address, username, git config or machine, and never invent identifiers. You may derive what you can observe: file sizes and media types from the files, `tool --version` output, titles and descriptions from the content (say that you did).
If you cannot ask: leave optional properties out. For what the root data entity must have (`license`, `name`, `description`, `datePublished`) and for any other property the specification, a profile or the validator requires (for example `version` on a `SoftwareApplication` or `ComputerLanguage` you could not run), use an obviously temporary value that starts with `TODO:` (for example `"license": "TODO: choose a licence"`; today's date is fine for the `datePublished` of a draft), and list every `TODO:` value in your answer. The lint script (`ro-crate-validator`) warns about `TODO:` values. Never publish, deposit or hand over a crate that still contains one.

## Zenodo (legacy API): `rocrate-zenodo`
PyPI `rocrate-zenodo` 0.1.1 (Apache-2.0, alpha; command `rocrate_zenodo`). Token in `~/.config/zenodo.ini` (`[zenodo:sandbox] sandbox_api_token = ...`, `[zenodo] api_token = ...`; scopes `deposit:write`, `deposit:actions`).
```bash
python -m venv /tmp/rz && /tmp/rz/bin/pip install rocrate-zenodo
rocrate_zenodo -s <crate dir>        # upload to the Zenodo SANDBOX (draft)
rocrate_zenodo <crate dir>           # production draft;  add -p to publish
```
## InvenioRDM: `rocrate-inveniordm`
PyPI `rocrate-inveniordm` 2.1.0 (MIT, alpha; command `rocrate_inveniordm`; replaces the inactive `ro-crates-deposit`). Env: `INVENIORDM_BASE_URL` (e.g. `https://sandbox.zenodo.org/`) and `INVENIORDM_API_KEY`.
```bash
rocrate_inveniordm --no-upload <crate dir>   # writes datacite-out.json for review, uploads nothing (tested)
rocrate_inveniordm <crate dir>               # uploads a draft record
rocrate_inveniordm -d datacite.json ...      # use a reviewed/edited DataCite file
rocrate_inveniordm -z <crate dir>            # upload the whole crate as one zip (keeps directories)
rocrate_inveniordm -p <crate dir>            # also publish
```
`--omit-roc-files` leaves out `ro-crate-metadata.json` and the preview (not recommended).

## Procedure
1. Validate the crate (`ro-crate-validator`; stop while the lint script reports errors or `TODO:` placeholders), check real author, licence, `datePublished`, no secrets or private data in files.
2. For InvenioRDM run `--no-upload` and review `datacite-out.json` with the user (the metadata is *derived*: creators, title, licence, dates).
3. Upload a **draft** to the sandbox; let the user inspect it in the web interface.
4. Only on an explicit instruction publish to production (`-p` or the web UI). Afterwards put the minted DOI into the crate (`identifier`/`cite-as`) only if it is real, and re-publish a version if needed; see `ro-crate-publish`.
