---
name: ro-crate-create
description: "Create or edit an RO-Crate: write or repair ro-crate-metadata.json for a directory of data, code or a workflow; add files, people, licences, funders, publications, profiles or provenance to a crate. Use whenever the user wants to package research data with metadata, make a folder an RO-Crate, describe a dataset for FAIR sharing, or fix a crate's metadata. Defaults to RO-Crate 1.3 for new crates and keeps the declared version when editing. Not for validating alone (ro-crate-validator) or reading crates (ro-crate-consume)."
---

# Creating or editing an RO-Crate

Spec 1.3: https://www.researchobject.org/ro-crate/specification/1.3/ (permalink https://w3id.org/ro/crate/1.3).
Requirement tables with severities: https://www.researchobject.org/ro-crate/quick-reference

## Do not guess
Ask the user for facts about them and their project: authors, publisher, organisation, affiliation, ORCID/ROR/DOI, licence, funders, dates of events, and software or tool versions. Do not infer them from the user's email address, username, git config or machine, and never invent identifiers. You may derive what you can observe: file sizes and media types from the files, `tool --version` output, titles and descriptions from the content (say that you did).
If you cannot ask: leave optional properties out. For what the root data entity must have (`license`, `name`, `description`, `datePublished`) and for any other property the specification, a profile or the validator requires (for example `version` on a `SoftwareApplication` or `ComputerLanguage` you could not run), use an obviously temporary value that starts with `TODO:` (for example `"license": "TODO: choose a licence"`; today's date is fine for the `datePublished` of a draft), and list every `TODO:` value in your answer. The lint script (`ro-crate-validator`) warns about `TODO:` values. Never publish, deposit or hand over a crate that still contains one.

## Before writing
- **New crate:** target 1.3. **Existing crate:** read its declared version (descriptor `conformsTo`) and keep it; upgrade only if asked (changes: https://www.researchobject.org/ro-crate/whats-changed-in-1-2 and `-1-3`).
- **Only released versions.** Never write `-DRAFT` specs, contexts, `conformsTo` or profile URIs. One exception: a Bioschemas profile for a type that has no released profile may be declared in its draft version, though it is not recommended (see `ro-crate-bioschemas`).
- If the Python tool is available, prefer `pip install rocrate` and `rocrate init` to bootstrap from a directory, then edit by hand; do not write RO-Crate libraries yourself.
- Example identifier: the ORCID `https://orcid.org/0000-0002-1825-0097` (fictional Josiah Carberry) is a deliberate example record, fine for examples and tests only.

## Minimal crate
`ro-crate-metadata.json` in the root directory of the data:

```json
{
  "@context": "https://w3id.org/ro/crate/1.3/context",
  "@graph": [
    {
      "@id": "ro-crate-metadata.json",
      "@type": "CreativeWork",
      "about": {"@id": "./"},
      "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"}
    },
    {
      "@id": "./",
      "@type": "Dataset",
      "name": "Title of the crate",
      "description": "What the crate contains and why",
      "datePublished": "2025-01-31",
      "license": {"@id": "https://creativecommons.org/licenses/by/4.0/"},
      "author": {"@id": "https://orcid.org/0000-0002-1825-0097"},
      "hasPart": [{"@id": "data/results.csv"}]
    },
    {
      "@id": "data/results.csv",
      "@type": "File",
      "name": "Results",
      "encodingFormat": "text/csv",
      "contentSize": "1234"
    },
    {
      "@id": "https://orcid.org/0000-0002-1825-0097",
      "@type": "Person",
      "name": "Josiah Carberry"
    },
    {
      "@id": "https://creativecommons.org/licenses/by/4.0/",
      "@type": "CreativeWork",
      "name": "CC BY 4.0",
      "description": "Creative Commons Attribution 4.0 International"
    }
  ]
}
```

## Rules (RO-Crate 1.2/1.3)
**Graph and JSON-LD**
- Flat `@graph`; every entity has `@id` and `@type` (string or array); unique `@id`s; order is irrelevant.
- References are objects: `{"@id": "..."}`, never nested entities or bare strings. Unpack single-element arrays.
- `@context` is the versioned context URL by reference. Extra terms must be added as keys in an `@context` array (`["https://w3id.org/ro/crate/1.3/context", {"term": "https://..."}]`) and defined as `DefinedTerm`/`rdf:Property`/`rdfs:Class` entities.
- Entity ids: absolute URI (ORCID, ROR, DOI, GeoNames, PRONOM) when real; `#name` for named local entities; `_:x` for unnamed (e.g. `Geometry`).

**Metadata descriptor:** `@id` `ro-crate-metadata.json`, `CreativeWork`, `about` the root, `conformsTo` a single versioned `https://w3id.org/ro/crate/1.x`. Do not put profiles here (1.2+).

**Root data entity:** `@type` includes `Dataset`; `@id` `./` (or an absolute URI that is the crate's main identifier); MUST have `name`, `description`, `datePublished` (one ISO 8601 string, at least a day) and `license` (a licence entity with name, or text). Add `author`, `publisher`, `keywords`, `identifier`/`cite-as` (only real PIDs) as known. Declare profiles with `conformsTo` on the root, each with a contextual entity of `@type` `["CreativeWork", "Profile"]` (see https://www.researchobject.org/ro-crate/specification/1.3/profiles).

**Data entities** (`File`, `Dataset`; `@id` relative path or absolute URI)
- Link each from the root via `hasPart`, directly or through a parent `Dataset`. There is no need to list every file; summarise bulk files in a `Dataset` with a `description`.
- A relative `@id` must exist in the crate at that path (and the reverse); use `/` separators, percent-encode spaces and `%` (`Results%20and%20Diagrams/a%2050%25.png`); directory ids end `/`; never use `../`.
- Give `name`, `description`, `encodingFormat` (IANA media type, optionally plus a PRONOM entity), `contentSize` in bytes where known.
- `#local` `File`/`Dataset` entities are *not* data entities (for example files not yet present).
- Web files: absolute `@id` that downloads directly (not a landing page), `sdDatePublished`, optional `localPath`. For a local file that is also online use `contentUrl` (download), `url`/`identifier`/`subjectOf` (landing pages).
- Do not list `ro-crate-preview.html` or `ro-crate-preview_files/` in `hasPart`.
- Software/workflows: `["File", "SoftwareSourceCode"]`, workflows also `ComputationalWorkflow`, with `programmingLanguage`; run provenance via `CreateAction` (`instrument`, `object`, `result`, `agent`, `endTime`).

**Contextual entities:** describe everything referenced (people, organisations, licences, places, funders, publications) with at least `name`; persons/organisations prefer ORCID/ROR; licences are `CreativeWork` with a URL `@id`; every contextual entity should be referenced from another entity.

## Detail files (read the one you need)
- [references/data-entities.md](references/data-entities.md): directories, web-based files, `contentUrl`/`localPath`, distributions, file formats, path encoding.
- [references/contextual-entities.md](references/contextual-entities.md): people, organisations, licences, funding, citations, places, keywords, identifiers.
- [references/jsonld.md](references/jsonld.md): `@context`, extending with new terms, relative URIs and `@base`.
- [references/web-records.md](references/web-records.md): a detached crate per record of a web application or catalogue (root = the record, described thing = `mainEntity`), generated from existing schema.org/Bioschemas JSON-LD.
- [references/provenance.md](references/provenance.md): software, equipment, change history actions, recording an AI agent's own work.
- [references/from-other-metadata.md](references/from-other-metadata.md): starting from CITATION.cff, codemeta.json or other project metadata (cffconvert + JSON-LD flattening).

## Workflow
1. Inspect the directory (or the existing crate) and list files.
2. Ask for or derive title, description, authors, licence, dates; mark unknowns as questions, not guesses.
3. Write or edit the metadata; keep existing content you were not asked to change.
4. Validate with the `ro-crate-validator` skill (or `pip install roc-validator` and `rocrate-validator validate <dir>`), fix failures against the spec, then do its semantic review (usefulness for the intended audience).
5. Report what was added, assumptions made, and anything left for the user to supply.
