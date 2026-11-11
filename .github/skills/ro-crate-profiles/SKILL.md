---
name: ro-crate-profiles
description: "Find, declare, apply and author RO-Crate profiles and Profile Crates. Use when a crate should conform to a domain profile (Workflow RO-Crate, Workflow Run Crate, ARC/ISA, ELN, ...), when reading or writing conformsTo on a root data entity, when defining a new profile, or when creating a machine-readable Profile Crate (hasResource, ResourceDescriptor, isProfileOf). For the full list of profile permalinks and validators see its references; for run-crate provenance see ro-crate-workflows."
---

# RO-Crate profiles (RO-Crate 1.2+)

Spec: https://www.researchobject.org/ro-crate/specification/1.3/profiles . A profile is a set of conventions (types, properties, versions, packaging) a subset of crates follows; a Profile Crate is an RO-Crate that describes one. Profiles are conventions, not strict inheritance.

## Do not guess
Ask the user for facts about them and their project: authors, publisher, organisation, affiliation, ORCID/ROR/DOI, licence, funders, dates of events, and software or tool versions. Do not infer them from the user's email address, username, git config or machine, and never invent identifiers. You may derive what you can observe: file sizes and media types from the files, `tool --version` output, titles and descriptions from the content (say that you did).
If you cannot ask: leave optional properties out. For what the root data entity must have (`license`, `name`, `description`, `datePublished`) and for any other property the specification, a profile or the validator requires (for example `version` on a `SoftwareApplication` or `ComputerLanguage` you could not run), use an obviously temporary value that starts with `TODO:` (for example `"license": "TODO: choose a licence"`; today's date is fine for the `datePublished` of a draft), and list every `TODO:` value in your answer. The lint script (`ro-crate-validator`) warns about `TODO:` values. Never publish, deposit or hand over a crate that still contains one.

## Find a profile
Reuse before inventing: registry https://profiles.ro-crate.org/ , overview https://www.researchobject.org/ro-crate/profiles . Known families: Workflow RO-Crate (+ Workflow Testing), Workflow Run Crate (Process / Workflow / Provenance, see `ro-crate-workflows`), Common Provenance Model, ARC/ISA profiles, ELN (`.eln`), RELIANCE, Describo profiles, RO-Crates-and-Excel. Use the profile's own page for its requirements and current version. Use-case table with permalinks and validator support: [references/known-profiles.md](references/known-profiles.md). Bioschemas profiles are profiles of individual entities, not RO-Crate profiles: they go in `conformsTo` of the described entity, not the root (see `ro-crate-bioschemas`).

## Declare conformance
On the **root data entity** (not the descriptor), one entry per profile:
```json
{"@id": "./", "@type": "Dataset", "conformsTo": [{"@id": "https://w3id.org/ro/wfrun/process/0.6"}]},
{"@id": "https://w3id.org/ro/wfrun/process/0.6", "@type": ["CreativeWork", "Profile"],
 "name": "Process Run Crate", "version": "0.6"}
```
- Every listed profile MUST have a contextual entity with `Profile` in `@type` (plus `CreativeWork` for a web page, `Dataset` for a Profile Crate), an absolute versioned `@id`, `name`, optional `version`.
- Use the profile's released, versioned permalink; never a `-DRAFT` one. (Bioschemas profiles are entity profiles with their own rules, including when a draft may be used: see `ro-crate-bioschemas`.) Add the profile's JSON-LD context to `@context` if it defines terms.
- The descriptor keeps a single `conformsTo` for the RO-Crate version. (In 1.0/1.1 crates profiles may appear as an array there: read them, and move them to the root when upgrading.)
- Declaring conformance is a claim, not proof: validate (`rocrate-validator` supports profiles) and read the profile's text.

## Applying a profile as a consumer
Resolve each profile URI to its description (or Profile Crate via content negotiation/Signposting, see `ro-crate-retrieve`); a retrieved crate's own `conformsTo` is authoritative over hints in a referencing crate. If no Profile Crate resolves, display the profile's `name` as a link.

## Publish a profile
- Profile URI: resolves to a human-readable description, SHOULD be a permalink (w3id, purl, DOI), SHOULD be versioned `MAJOR.MINOR`. The description uses RFC 2119 keywords and states required/suggested entity types, properties, RO-Crate version, context, packaging.
- Optionally a Profile Crate at the same URI: see [references/profile-crate.md](references/profile-crate.md).
- Formal forms: JSON Schema, SHACL/ShEx shapes, hard-coded validators; combine structural JSON-LD checks with them.
