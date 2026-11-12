# RO-Crate version differences (1.0 → 1.1 → 1.2 → 1.3)

Not normative: the sources are the specifications, their changelogs and the project's migration guides. Specs: [1.0](https://w3id.org/ro/crate/1.0), [1.1](https://w3id.org/ro/crate/1.1), [1.2](https://w3id.org/ro/crate/1.2), [1.3](https://w3id.org/ro/crate/1.3).

## Versions and permalinks

| Version | Permalink (descriptor `conformsTo`) | Context | Metadata file | Zenodo | Published |
|---|---|---|---|---|---|
| 1.3 | <https://w3id.org/ro/crate/1.3> | <https://w3id.org/ro/crate/1.3/context> | `ro-crate-metadata.json` | <https://doi.org/10.5281/zenodo.20720080> | 2026-06-22 |
| 1.2 | <https://w3id.org/ro/crate/1.2> | <https://w3id.org/ro/crate/1.2/context> | `ro-crate-metadata.json` | <https://doi.org/10.5281/zenodo.13751027> | 2025-06-04 |
| 1.1 | <https://w3id.org/ro/crate/1.1> | <https://w3id.org/ro/crate/1.1/context> | `ro-crate-metadata.json` | <https://doi.org/10.5281/zenodo.7867028> (1.1.3 record; spec text 1.1.2) | 2022-01-19 |
| 1.0 | <https://w3id.org/ro/crate/1.0> | <https://w3id.org/ro/crate/1.0/context> | `ro-crate-metadata.jsonld` | <https://doi.org/10.5281/zenodo.3541888> | 2019-11-15 |

All versions: <https://doi.org/10.5281/zenodo.3406497>. The unversioned `https://w3id.org/ro/crate` identifies the *generic*
RO-Crate profile (used for `conformsTo` on referenced crates and `DataDownload`s, never on your own root).
Patch releases (1.0.1, 1.1.1-1.1.3, 1.2.x, 1.3.x) are errata/clarifications and keep the same permalink.
Version detection: read the descriptor's `conformsTo`, not `@context`.

## Sources
- What's changed in 1.2: https://www.researchobject.org/ro-crate/whats-changed-in-1-2
- What's changed in 1.3: https://www.researchobject.org/ro-crate/whats-changed-in-1-3
- Quick reference of all requirements (severities, "Changed in 1.2" column; valid for 1.3): https://www.researchobject.org/ro-crate/quick-reference
- Specifications: https://w3id.org/ro/crate/1.3 , /1.2 , /1.1 , /1.0 (changelog in each)

## Differences at a glance

| Aspect | 1.0 | 1.1 | 1.2 | 1.3 |
|---|---|---|---|---|
| Metadata file / descriptor `@id` | `ro-crate-metadata.jsonld` | `ro-crate-metadata.json` (read `.jsonld` from 1.0 crates; rename on update) | `ro-crate-metadata.json` MUST (legacy `.jsonld` readable) | same as 1.2 |
| Context by reference | SHOULD | SHOULD | **MUST** (1.3: `1.3/context`) | MUST |
| Package types | one (directory) | one (directory) | **Attached** (1.1-style) and **Detached** (no directory; all data entities absolute URIs; file may be `<prefix>-ro-crate-metadata.json`) | same |
| Finding the root | entity whose `conformsTo` starts with `https://w3id.org/ro/crate/`; its `about` | same | `ro-crate-metadata.json` entity's `about` (legacy `.jsonld` fallback) | same |
| Descriptor `conformsTo` | single versioned URI | MAY be an array incl. profiles | SHOULD be a single versioned URI; profiles go on the root (arrays discouraged, still tolerated) | same |
| Root `@id` | MUST be `./` | `./` or absolute URI; MUST end `/` per text | `./` or absolute URI (no trailing `/` requirement) | same |
| Root required properties | `@type` Dataset, `name`, `description`, `datePublished`, `license` (MUST be present; content SHOULD) | same | same; `datePublished` a **single** ISO 8601 string; `license` may point to a data entity | same |
| Profiles / Profile Crates | none | none (profiles loosely in `conformsTo`) | **New**: `conformsTo` on the root, `Profile` contextual entities, Profile Crates (`isProfileOf`, `hasResource`, `ResourceDescriptor`) | same |
| Data entity definition | local files/dirs only | + web resources (absolute `@id`), `sdDatePublished` | explicit: `@type` has `File`/`Dataset` **and** `@id` is absolute/relative URI; `#id` ones are not data entities; relative `@id` must exist in the root (and vice versa) | same |
| Direct download for web files | n/a | `url` | **`contentUrl`** (follow redirects → file); `url` for landing pages; `localPath` for local destination | same |
| Referencing other crates | n/a | n/a | **New**: `Dataset` with version-less `conformsTo https://w3id.org/ro/crate`, retrieval algorithm (Signposting, content negotiation), `distribution`/`DataDownload` | same |
| Root identifier | n/a | `identifier` string | `identifier` as `PropertyValue`; `cite-as` and Signposting recommendations (strings still tolerated) | same |
| Focus of a crate | n/a | n/a | **New**: `mainEntity`, `mentions` | same |
| File extras | `encodingFormat` | same | media-type string and/or format entity (PRONOM, `Standard`); `conformsTo` file-format profiles; `contentSize` SHOULD | same |
| Places | `latitude`/`longitude` (`GeoCoordinates`) | same | WKT `Geometry`/`asWKT`, `contentLocation` or `spatialCoverage` | same |
| Workflows | `@type` `File` + `Workflow`/`Script` (+ `SoftwareSourceCode`) | `File`, `SoftwareSourceCode`, `ComputationalWorkflow`; pre-release Bioschemas profiles (ComputationalWorkflow 0.5, FormalParameter 0.1; do not use for new crates) | same types; Bioschemas profiles 1.0-RELEASE / FormalParameter 1.0-RELEASE | same; new Bioschemas IRIs (below) |
| Bioschemas term IRIs | n/a | `bioschemas.org/ComputationalWorkflow`, `/FormalParameter`, `…/ComputationalWorkflow#input`, `#output` | `bioschemas.org/ComputationalWorkflow`, `/FormalParameter`, `bioschemas.org/properties/input`, `/output` | `bioschemas.org/terms/ComputationalWorkflow`, `FormalParameter`, `input`, `output` |
| `@base: null` in the context | present | removed (see relative-URI appendix) | not present | not present |
| schema.org snapshot | 5.0 | 10.0 | 22.0 | 30.0 (adds terms, removes none) |
| Preview (`ro-crate-preview.html`) | MAY be present | MAY; no longer needs to repeat metadata | If present MUST be that name; extras in `ro-crate-preview_files/`; not in `hasPart` (except as a Profile Crate's description); no "cite-as" text | same |
| Extending the context | extension guidance (appendix); strength not checked | SHOULD map extra terms | **MUST** map extra terms; ad hoc `DefinedTerm`/`rdf:Property`/`rdfs:Class` with `@id`, `name`, `description`; extensions can be grouped in a profile | same |
| Appendices | JSON-LD, BagIt notes | + relative-URI handling, repository identifiers | + Attached⇄Detached conversion, ELN note, profile grouping | same |

## Migration checklists

### 1.0 → 1.1
1. Rename `ro-crate-metadata.jsonld` to `ro-crate-metadata.json`; change the descriptor `@id` and `conformsTo` to
   `https://w3id.org/ro/crate/1.1`; change `@context` to `https://w3id.org/ro/crate/1.1/context`.
2. Workflows: `Workflow` → `ComputationalWorkflow`; scripts stay `SoftwareSourceCode` (check the 1.1 context before keeping a `Script` type).
3. Optional: describe web resources as data entities; use absolute root `@id`; add `sdDatePublished` to web files.
4. If you relied on `@base: null` from the 1.0 context, handle relative URIs as described in the relative-URI appendix.

### 1.1 → 1.2 (from the project's guide; see https://www.researchobject.org/ro-crate/whats-changed-in-1-2)
1. Crates stay valid as **Attached** crates; no structural change needed. Use the 1.2 context by reference; update `conformsTo`.
2. Preview: file named `ro-crate-preview.html`, resources in `ro-crate-preview_files/`; remove them from any `hasPart`.
3. Every entity has `@id`, `@type`, ideally `name`; all entities reachable from the root; references use `{"@id": ...}`.
4. Root: single-valued `datePublished`; check `identifier`/`cite-as`; move profile `conformsTo` from the descriptor to the
   root; add a `Profile` contextual entity for each declared profile.
5. Data entities: relative `@id` ⇄ file/dir present in the crate root; web files' `@id` not a landing page; use `contentUrl` for download links.
6. Optional: `name`/`description` on data entities, new SHOULDs for `File`/`Dataset`, guidance on referencing other crates, place descriptions.
7. Update Bioschemas profile versions if workflows are described; remap `input`/`output` IRIs to `bioschemas.org/properties/…` (changes again in 1.3).

### 1.2 → 1.3
Update `conformsTo` (descriptor) to `https://w3id.org/ro/crate/1.3` and `@context` to `https://w3id.org/ro/crate/1.3/context`. No other JSON changes.
RDF consumers must also recognise the old Bioschemas IRIs when reading 1.1/1.2 crates (table above). To upgrade from 1.1, apply
1.1 → 1.2 first, then this step.

### Reading older crates (consumers)
- Accept `.jsonld` descriptors; find the root by `about` (or by `conformsTo` prefix for 1.0/1.1 if the `@id` lookup fails).
- Accept string `identifier`, string `license`, descriptor `conformsTo` arrays containing profiles (treat entries other than the base spec as profile declarations).
- Treat a missing `@context` version as unreliable; use `conformsTo`.
- Do not "fix" a crate's version silently; report the declared version, then apply the checklist if the user asks to upgrade.
