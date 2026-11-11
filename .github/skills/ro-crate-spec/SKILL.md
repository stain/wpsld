---
name: ro-crate-spec
description: "Answer questions about the RO-Crate specification itself: what a term, requirement or version rule means, whether something is allowed or required, which property or type to use, where in the spec something is described, how profiles relate. Use for 'what does RO-Crate say about ...' and 'is X required', before answering from memory. For version differences see ro-crate-compatibility."
---

# RO-Crate specification: principles and where to look

Default target RO-Crate 1.3 (https://w3id.org/ro/crate/1.3); older versions at `/1.2`, `/1.1`, `/1.0`. Quote the spec text (or the requirement tables) when answering, and say which version. If unsure, look it up rather than answering from memory; the spec pages are listed in [references/where-to-look.md](references/where-to-look.md), and every requirement with its severity is in https://www.researchobject.org/ro-crate/quick-reference .

## Principles that settle most questions
1. A crate is a `Dataset` described by one JSON-LD file, `ro-crate-metadata.json`. JSON-LD tooling is optional.
2. The JSON-LD is **flattened and compacted**: a flat `@graph` of entities, links as `{"@id": "..."}` objects, no nesting, single-element arrays unpacked, unique `@id`s.
3. Every entity has `@id` and `@type`; it SHOULD have a `name` and use a schema.org type; it SHOULD be reachable from the root.
4. Required members: the metadata descriptor (`@id` `ro-crate-metadata.json`, `about` the root, single versioned `conformsTo`) and the root data entity (a `Dataset` that has `name`, `description`, `datePublished`, `license`; `@id` `./` or an absolute URI).
5. **Data entities** are the payload: a "directory, file, or other Web resource" contained by the crate (terminology), linked via `hasPart`. Entities representing files MUST include `File`, directories `Dataset`; another web resource is typically a `CreativeWork` or, better, its most specific schema.org subtype (`WebPage`, `LearningResource`, `SoftwareSourceCode`, `ScholarlyArticle`, ...; the 176 in schema.org 30.0, which the 1.3 context uses, are in [references/creativework-types.md](references/creativework-types.md)), and may add those subtypes to `File` too. **Contextual entities** (people, organisations, licences, places, events, actions, profiles) describe them. `#local` entities are not data entities. The metadata is not an exhaustive manifest.
6. schema.org first (liberal readings are fine, e.g. `IndividualProduct` for instruments). `File` = `MediaObject`. Other terms must be mapped in an `@context` array and defined. Each RO-Crate context is fixed to one schema.org release (its `schemaVersion`: 1.0 → 5.0, 1.1 → 10.0, 1.2 → 22.0, 1.3 → 30.0); schema.org terms added after that are not in the context and must be mapped too. The context also keeps terms schema.org had already deprecated (superseded or attic, e.g. `Code`, `fileFormat`): allowed, but ideally avoid them in new metadata and use the replacement.
7. Persistent absolute identifiers (ORCID, ROR, DOI, GeoNames, PRONOM) over local ones; never invent identifiers.
8. The version is the descriptor's `conformsTo`; RO-Crate profiles are declared on the root (1.2+), each with a `Profile` entity. Profiles of a single entity (Bioschemas) go in that entity's `conformsTo`, not the root's.
9. Attached crates (directory) and detached crates (metadata only, web data entities) both exist since 1.2.
10. Only released specifications, contexts and RO-Crate profile URIs are valid: never `-DRAFT`. The one exception is a Bioschemas profile for a type with no released profile, which may be declared as a draft on the entity, though it is not recommended (`ro-crate-bioschemas`).

## Version questions
Use the `ro-crate-compatibility` skill (comparison table, upgrade steps). The spec text itself has quirks: "Direct properties of the Root Data Entity" says the root MUST have all listed properties while the bullets use SHOULD for what values should do; some spec examples are abbreviated or not valid JSON; read prose before copying an example.

## Answering well
- Separate MUST/SHOULD/MAY (RFC 2119) and state the severity.
- Point to the section (page + heading) so the user can check it.
- If the spec is silent, say so, then give the common community practice and mark it as such.
- Profile-specific rules (Workflow RO-Crate, Run Crates, ARC, ...) come from the profile, not the core spec: see `ro-crate-profiles`.
