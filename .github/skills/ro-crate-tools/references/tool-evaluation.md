# Tool evaluation: what was tested, strengths, weaknesses, scope

Use this to decide whether an existing tool is in scope for a task, instead of writing a new implementation. Evidence is from runs on 2026-10-16 (versions given); "tested" means the command was run, "help only" means only `--help`/README was read, "untested" means taken from the RO-Crate tools list (https://www.researchobject.org/ro-crate/tools) only. Statuses (alpha/beta/production) are the tools list's labels; how recently each tool was released, which RO-Crate versions it writes and reads, and which are unmaintained is in the next section. Tools change quickly: re-check versions and flags.

## Rules that apply to every tool
- **One virtual environment (or npm prefix) per tool.** `rocrate-action-recorder` pins `rocrate==0.15.0`; installing it into an environment with ro-crate-py 0.16.0 downgraded it and made `runcrate report` fail on a 1.3 crate (`ValueError: version '1.3' not supported`). The same crate worked in a clean environment.
- **Always review generated crates.** Most tools write a crate that lacks root `name`, `description`, `license`, authors, or declares an older RO-Crate version. Complete the root, then validate (`ro-crate-validator`).
- **Never guess** licences, authors, organisations, versions or identifiers to satisfy a tool.
- **Uploads and replays are outward actions**: confirm first (`ro-crate-deposit`, `ro-crate-runcrate`).

## Currency and RO-Crate version support (as of 2026-10-16)
Maintenance: **active** = a release or a commit on the default branch within 6 months, **slow** = 6-24 months, **stale** = over 24 months (release dates from PyPI and npm, last commit on the default branch from `git`; `python tests/tool_currency.py` in the repository regenerates them). "Tools list" = the label on https://www.researchobject.org/ro-crate/tools . Versions: **writes** = what a new crate declares, **reads** = tested with the repository's 1.0, 1.1, 1.2 and 1.3 fixtures (`tests/fixtures`; the 1.0 crate has the legacy `ro-crate-metadata.jsonld` name).

| Tool | Last release / last commit (default branch) | Maintenance | Writes | Reads (tested) |
|---|---|---|---|---|
| ro-crate-py 0.16.0 | 2026-10-06 / 2026-10-14 (master) | active | **1.3 by default**; `rocrate init --crate-version` accepts 1.0-1.3 (1.0 writes `.jsonld`); the code also accepts `-DRAFT` versions, never use them | 1.0, 1.1, 1.2, 1.3 |
| rocrate-validator 0.12.2 | 2026-10-05 / 2026-10-14 (develop) | active | n/a | **profiles for 1.1, 1.2, 1.3 only** (a 1.0 crate fails the 1.1 profile on the `.jsonld` name); run-crate profiles 0.5 (current 0.6); workflow profiles built on 1.1 |
| lint_crate.py (this repo) | n/a | maintained here | n/a | 1.0, 1.1, 1.2, 1.3, detached, zip |
| signposting 0.9.9 | 2024-03-23 / 2024-03-23 (main) | **stale** (works, but no release for over two years) | n/a (HTTP, not RO-Crate versions) | n/a |
| ro-crate-html 0.1.6 (`rochtml`) | 2025-05-02 / 2025-05-02 (master) | slow (the older `ro-crate-html-js` 1.4.19 was last published 2021-2022) | n/a | produced a preview for 1.0-1.3 (page content not inspected) |
| ro-crate-js 3.7.2 | 2026-05-01 / 2026-05-01 (master) | active | **1.2** (context + `@vocab`) | 1.0, 1.1, 1.2, 1.3 (keeps the declared context); `roccheck` expects the `.jsonld` name |
| ro-crate-excel 1.2.1 | 2026-06-18 / 2026-06-18 (master) | active | **1.2** | untested (it reads its own spreadsheet or JSON) |
| rocrate-action-recorder 0.7.1 | 2026-08-04 / 2026-08-04 (main) | active | **1.1** + Process Run Crate 0.5 only | its own crate (1.1); needs Python 3.13+ |
| runcrate 0.6.2 | 2026-02-24 / 2026-07-02 (main) | active | CWLProv conversion output version untested | 1.0, 1.1, 1.2, 1.3 with rocrate 0.16.0 (fails on 1.3 with rocrate 0.15.0) |
| rocrate-tabular 0.2.0 | no release on PyPI / 2026-06-24 (main) | active (install from GitHub) | n/a | 1.1, 1.2, 1.3; **fails on a 1.0 `.jsonld` crate** (needs `ro-crate-metadata.json`) |
| rocrate-inveniordm 2.1.0 | 2025-12-03 / 2025-12-03 (main) | slow | n/a | 1.1, 1.2, 1.3 (`--no-upload`); **needs `ro-crate-metadata.json`**, not `.jsonld` |
| rocrate-zenodo 0.1.1 | 2024-08-23 / 2024-08-23 (main) | **stale**; uses the legacy Zenodo API and is incompatible with InvenioRDM per its README | n/a | untested |
| bagit-python 1.9.0 | 2025-06-13 / 2026-06-18 (master) | active | n/a (BagIt 0.97 tags) | n/a |
| RO-Crate-RAG-Validator | unreleased / 2026-06-14 (main) | prototype (0 stars) | n/a | bundled profile is the 1.2 quick reference; assumes root `@id` `./` |
| CheckMyCrate | - / 2021-06-30 (master) | **unmaintained** (tools list: deprecated) | n/a | Workflow RO-Crate profile of that era |
| ro-crate-validator-py | - / 2024-10-10 (main) | **deprecated** (tools list), use rocrate-validator | n/a | untested |
| ro-crates-deposit | - / 2023-07-09 (main) | **inactive** (tools list), use rocrate-inveniordm | n/a | untested |
| Crate-O, nf-prov | - / 2026-04-14, 2026-10-12 (main) | active | untested | untested |

Practical consequences: new crates should come from ro-crate-py (1.3) or be upgraded from the 1.2 output of ro-crate-js / ro-crate-excel and the 1.1 output of the recorder (`ro-crate-compatibility`); legacy 1.0 crates must be upgraded or renamed to `ro-crate-metadata.json` before tabulator, inveniordm or the validator will take them; prefer an active tool over a stale one, and say so when only a stale tool fits (signposting still works, rocrate-zenodo needs extra care and a sandbox test).

## Summary table

| Tool (version tested) | Task | Verdict | Use when | Avoid when |
|---|---|---|---|---|
| ro-crate-py `rocrate` 0.16.0 | create, load, modify, zip crates (Python) | **Use** | any Python crate scripting; `rocrate init`, library, `write-zip`; default RO-Crate 1.3 | you need the root fully described automatically (it does not set `name`, `description`, `license`) |
| roc-validator `rocrate-validator` 0.12.2 | validate against spec and profiles | **Use first** for profile/SHACL checks | any final or incoming crate; JSON output; exit code 0/1; passed a minimal crate with HTTP(S) proxies pointed at a dead address, so no context fetch was needed there | judging 1.0 crates (no 1.0 profile), run-crate profiles newer than 0.5, workflow profiles on 1.3 crates (built on 1.1), Profile Crates with absolute root `@id` (over-strict); never treat a pass as proof of quality |
| `lint_crate.py` (this repo, ro-crate-validator skill) | offline structural checks | **Use** as a fast first pass | no network, missing `hasPart` files, unreachable entities, `-DRAFT` URIs, descriptor/root properties | profile rules, semantics, SHACL |
| signposting 0.9.9 | resolve a PID to crate links | **Use** (stale: no release since 2024-03) | DOI/URL to crate (`CiteAs`, `DescribedBy`, `Item`) | you need the `profile` attribute of the link (the CLI output omits it: read the `Link` header with `curl -I`); the first call on a slow DOI timed out at 60 s, allow 120 s |
| `rochtml` (`ro-crate-html` 0.1.6, beta) | write `ro-crate-preview.html` | **Use** | HTML preview next to `ro-crate-metadata.json` | you need a styled site (see `rocstatic`/`rocsite`, untested) |
| `ro-crate-js` 3.7.2 (beta) | create/modify crates (Node) | **Use with care** | JavaScript projects | assuming it targets 1.3: a new `ROCrate()` writes the **1.2** context plus `@vocab` and ignores constructor options; its `rocval` CLI crashed (`program.version is not a function`), `roccheck` is "not a full validation" and looks for `ro-crate-metadata.jsonld` (ENOENT on `.json`) |
| `rocrate-action-recorder` 0.7.1 (beta) | record Python CLI runs as provenance | **Use for CLIs you author**; not general | argparse/click/cyclopts tools you maintain (agent runs: worked after `scripts/link_actions.py` fix-up) | wrapping arbitrary shell commands; its output was RO-Crate 1.1 + Process Run Crate 0.5, failed `ro-crate-1.3` validation (software `url`, descriptor/`conformsTo`), recorded the OS user as agent, actions not linked from the root until fixed, needs Python 3.13+ and pins `rocrate==0.15.0` |
| `runcrate` 0.6.2 | report on / replay / convert run crates | **Use** `report`; `run`/`convert` help only | "what was run here?" on a run crate; CWLProv conversion | executing untrusted crates; shared environments (see pin conflict) |
| `bagit-python` `python -m bagit` 1.9.0 | make and validate BagIt bags | **Use** (preferred to hand-made bags) | `--sha512 <dir>` converts a copy in place, `--validate`; correct tag spelling and `Payload-Oxum` | running it on the original crate (it moves files into `data/`); setting organisation/contact flags the user did not give |
| `rocrate-tabular` `tabulator` 0.2.0 (alpha) | crate to SQLite/CSV | **Use** for counting/filtering | many crates or SQL questions | per-type tables without editing `config.json` (default is one `property` table, 37 rows for a 9-entity crate; CSV dir empty); it writes `config.json` into the working directory; not on PyPI under that name (install from GitHub) |
| `ro-crate-excel` (`rocxl`/`xlro` 1.2.1, beta) | folder + spreadsheet to crate | **Use with Siegfried** | collaborators supply metadata in Excel; folder to first-draft crate | Siegfried (`sf`) missing (fails with "Have you installed Siegfried?"); you cannot tolerate in-place rewrites (use a copy); output is RO-Crate 1.2, `license` as a plain string, and one round trip lost `contentSize`; not validated in the tests |
| `rocrate-zenodo` 0.1.1 (alpha) | deposit to Zenodo (legacy API) | **Use with confirmation and a sandbox test**; help only; stale (last release 2024-08) | draft uploads to Zenodo sandbox, then publish on instruction | InvenioRDM repositories; unattended publishing (irreversible DOI) |
| `rocrate-inveniordm` 2.1.0 (alpha) | deposit to InvenioRDM / Zenodo | **Use with confirmation** | `--no-upload` writes `datacite-out.json` for review (tested, works with dummy credentials); then draft upload | publishing without review |
| RO-Crate-RAG-Validator (alexhambley, unreleased, BSD-3) | per-entity LLM + RAG validation | **Optional / not recommended as a dependency** | extra usefulness review with your own LLM keys | structural validation (per entity only, root assumed `./`, non-deterministic, needs embeddings/chat API keys, bundled profile is the 1.2 quick reference) |
| nf-core RO-Crate, `nf-prov`, `galaxy2cwl`, PyCOMPSs `inspect`, Describo, Crate-O, NovaCrate, RO-Crate Playground, `rocrateR`, ro-crate-java/-ruby/-rs | various | **Untested**: listed by the tools page | follow their docs; verify output with `ro-crate-validator` | claiming results you have not checked |

## Evidence from agent evals (with vs without skill)
- ro-crate-excel: both agents completed the round trip once Siegfried was installed; the skill supplied the install steps and the `-r` flag.
- rocrate-action-recorder: the skill-guided agent used the library and fixed its output; the baseline wrote its own recorder (parameters, sha256, failure status) and did not validate it. That is the re-implementation these skills are meant to prevent, but note the library's output needed fix-ups too.
- Run-crate examples: the official Process Run Crate 0.6 example (profile entity typed only `CreativeWork`, `softwareVersion`) fails `rocrate-validator -p ro-crate-1.3` 0.12.2; the fixture and skill example use `["CreativeWork", "Profile"]` and `version`.
Details in `tests/eval-results/` of the repository. Summary relevant to tools:
- ro-crate-js: both runs first lost root fields (options ignored), both ended with a valid **1.2** crate; the skill avoided the detour and stated the version.
- rocrate-tabular and runcrate report: a no-skill agent solved the same small questions by reading the JSON, so these tools pay off for many crates or large crates, not for one tiny crate.
- Deposit: the skill-guided agent kept tokens out of chat and used the sandbox-first, review-first flow; the no-skill agent asked the user to send the token and described raw API calls.
- ro-crate-py: with the skill the agent filled `name`/`description`/`license` after `rocrate init`; both configurations produced valid 1.3 crates.

## Scope guide
1. A task a listed tool already does (preview, tables, validation, zip, deposit, run report): use the tool.
2. The tool only partly fits (Python-only recorder, Excel needing Siegfried): use it if you control the inputs, otherwise write the metadata following `ro-crate-create` and say why.
3. The tool is unreleased, unmaintained or a prototype (see the tools list status; RAG validator): mention it, do not depend on it.
4. Tool output is always a draft for review, never a finished deliverable.
