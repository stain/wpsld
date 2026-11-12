---
name: ro-crate-compatibility
description: "Understand differences between RO-Crate versions 1.0, 1.1, 1.2 and 1.3 and migrate or check crates. Use when a crate declares an older or newer version, has ro-crate-metadata.jsonld or profiles in the descriptor's conformsTo, when upgrading a crate to 1.3, when a validator reports a version mismatch, or when asked what changed between versions."
---

# RO-Crate version compatibility

Detect the version from the metadata descriptor's `conformsTo` (not `@context`). Released versions only: never write or accept `-DRAFT` URIs as valid.

| Version | Permalink / context | Descriptor `@id` | Key facts |
|---|---|---|---|
| 1.3 (2026-06) | https://w3id.org/ro/crate/1.3 (+ `/context`) | `ro-crate-metadata.json` | Bioschemas term IRIs moved to `https://bioschemas.org/terms/` |
| 1.2 (2025-06) | https://w3id.org/ro/crate/1.2 | `ro-crate-metadata.json` | Attached/Detached crates, profiles on the root + Profile Crates, `contentUrl`, `localPath`, referencing other crates, `mainEntity`/`mentions`, context MUST be by reference |
| 1.1 (2022-01) | https://w3id.org/ro/crate/1.1 | `ro-crate-metadata.json` | web-based data entities, root `@id` may be absolute, `ComputationalWorkflow` |
| 1.0 (2019-11) | https://w3id.org/ro/crate/1.0 | `ro-crate-metadata.jsonld` | root `@id` MUST be `./`, `Workflow`/`Script` types, local data entities only |

Root must have `name`, `description`, `datePublished` (single ISO 8601 string), `license` in every version.

## Upgrade steps
- **1.0 to 1.1:** rename file to `ro-crate-metadata.json`; descriptor `@id`/`conformsTo`/`@context` to 1.1; `Workflow` to `ComputationalWorkflow` (check the context before keeping `Script`).
- **1.1 to 1.2:** attached crate already; use the 1.2 context by reference; preview file named `ro-crate-preview.html` and not in `hasPart`; every entity has `@id`/`@type`, reachable from the root, references as `{"@id"}`; single-valued `datePublished`; check `identifier`/`cite-as`; **move profile declarations from descriptor `conformsTo` to root `conformsTo`** with a `Profile` entity each; relative `@id`s match files (and vice versa); web files use `contentUrl` for downloads; extra terms mapped in `@context`.
- **1.2 to 1.3:** change descriptor `conformsTo` and `@context` to 1.3; nothing else. RDF consumers should also accept the old Bioschemas IRIs from 1.1/1.2 crates (`ComputationalWorkflow`, `FormalParameter`, `input`, `output`).
- From 1.1: apply 1.1 to 1.2 first, then 1.2 to 1.3.

## Reading and validating older crates
- Accept `.jsonld` descriptors, string `identifier`/`license`, descriptor `conformsTo` arrays containing profiles. Find the root through the descriptor's `about` (or the `conformsTo` prefix for 1.0/1.1).
- Validate against the version the crate declares (`rocrate-validator profiles list`, `-p ro-crate-1.1`), not 1.3, or expect mismatches.
- Do not silently upgrade. Report the declared version, differences found, and upgrade only on request, keeping a copy or recording the change as a `CreateAction`/`UpdateAction`.

Details: [references/version-differences.md](references/version-differences.md) (comparison table, per-version summaries); project guides https://www.researchobject.org/ro-crate/whats-changed-in-1-2 and `-1-3`; requirement tables https://www.researchobject.org/ro-crate/quick-reference
