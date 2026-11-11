---
name: ro-crate-bioschemas
description: "Use Bioschemas profiles and types in RO-Crates: choose a Bioschemas profile, released where one exists (TrainingMaterial, Course, Dataset, ComputationalTool, Taxon, Gene, ...), declare it with conformsTo on the described entity, map Bioschemas terms the RO-Crate context lacks (TaxonName, scientificName, BioSample, ...), and turn existing Bioschemas JSON-LD markup into crate entities. Use when a crate describes life-science resources, when a site's Bioschemas markup is the source of a crate, or when a crate or validator mentions bioschemas.org. For Workflow RO-Crate see ro-crate-workflows."
---

# Bioschemas in RO-Crate

Bioschemas (https://bioschemas.org/) adds community **profiles** (which schema.org properties a life-science resource should have: minimum, recommended, optional) and a few **types and properties** (https://bioschemas.org/types/) on top of schema.org. Profiles: https://bioschemas.org/profiles/ . RO-Crate 1.3 maps Bioschemas terms in the `https://bioschemas.org/terms/` namespace.

## Choose a profile: prefer released versions
Declare a `-RELEASE` profile whenever one exists for the type (checked 2026-10-23), even if the site's own markup names a newer draft of it:

| Profile | URI (`https://bioschemas.org/profiles/…`) | Main type |
|---|---|---|
| TrainingMaterial | `TrainingMaterial/1.0-RELEASE` | `LearningResource` |
| Course / CourseInstance | `Course/1.0-RELEASE`, `CourseInstance/1.0-RELEASE` | `Course`, `CourseInstance` |
| Dataset / DataCatalog | `Dataset/1.0-RELEASE`, `DataCatalog/0.3-RELEASE-2019_07_01` | `Dataset`, `DataCatalog` |
| ComputationalTool | `ComputationalTool/1.0-RELEASE` | `SoftwareApplication` |
| ComputationalWorkflow / FormalParameter | `ComputationalWorkflow/1.0-RELEASE`, `FormalParameter/1.0-RELEASE` | see `ro-crate-workflows` |
| Taxon / TaxonName | `Taxon/1.0-RELEASE`, `TaxonName/1.0-RELEASE` | `Taxon`, `TaxonName` |
| Gene, Protein | `Gene/1.0-RELEASE`, `Protein/0.11-RELEASE` | `Gene`, `Protein` |
| ChemicalSubstance, MolecularEntity | `ChemicalSubstance/0.4-RELEASE`, `MolecularEntity/0.5-RELEASE` | `ChemicalSubstance`, `MolecularEntity` |
| Sample | `Sample/0.2-RELEASE-2018_11_10` (old; the newer BioSample profile is a draft) | |

**No released profile** (only drafts): Event, BioSample, LabProtocol, LabProcess, Person, Organization, ScholarlyArticle, Journal, Disease, Phenotype, ProteinStructure, ... For these a draft profile **may** still be declared, although it is not recommended: drafts change without notice and consumers may not recognise them. Use the latest draft listed on the profiles page (not an older one the markup happens to name, unless the user wants to keep it), describe it as a `Profile` entity whose `name` says it is a draft, and tell the user. Leaving out the Bioschemas `conformsTo` and using only the schema.org type is the other option; ask which they prefer. Re-check https://bioschemas.org/profiles/ before relying on this list: a draft may since have been released.

## Declaring it
- `conformsTo` goes on the **entity** the profile describes (the `LearningResource`, the `Gene`), never on the root data entity: a Bioschemas profile is not an RO-Crate profile, and the root's `conformsTo` is for RO-Crate profiles only. This is another reason to split the Bioschemas entity from the root (below). It holds in Workflow RO-Crate too: the root declares the Workflow RO-Crate profile, the workflow entity declares the Bioschemas ComputationalWorkflow profile (`ro-crate-workflows`).
- **RO-Crate's compatibility layer.** RO-Crate 1.3 maps the Bioschemas terms `ComputationalWorkflow`, `FormalParameter`, `input` and `output` in its context (namespace `https://bioschemas.org/terms/`; 1.1 and 1.2 used `https://bioschemas.org/`), and says workflow data entities SHOULD comply with ComputationalWorkflow 1.0-RELEASE and SHOULD declare it with `conformsTo` on the data entity (spec `workflows`, "Complying with Bioschemas Computational Workflow profile"). Other Bioschemas profiles follow the same pattern.
- Describe the profile: `{"@id": "https://bioschemas.org/profiles/TrainingMaterial/1.0-RELEASE", "@type": ["CreativeWork", "Profile"], "name": "Bioschemas TrainingMaterial profile 1.0-RELEASE"}`.
- Bioschemas markup often writes `dct:conformsTo`; in a crate it is plain `conformsTo` (the RO-Crate context maps it to `http://purl.org/dc/terms/conformsTo`).
- `rocrate-validator` may then warn that the root "SHOULD have a `conformsTo`": expected (see `ro-crate-validator`).

## Terms the RO-Crate 1.3 context does not define
Most Bioschemas types are schema.org terms already in the context (`BioChemEntity`, `Gene`, `Protein`, `Taxon`, `MolecularEntity`, `ChemicalSubstance`, `taxonRank`, `parentTaxon`, ...), and the context maps `ComputationalWorkflow`, `FormalParameter`, `input` and `output` to `https://bioschemas.org/terms/`. These are missing and must be added to the crate's `@context` array before use (`ro-crate-create`, references/jsonld.md):

| Term | Kind | Used by | Map to |
|---|---|---|---|
| `TaxonName` | type | TaxonName, Taxon (released) | `https://bioschemas.org/terms/TaxonName` |
| `scientificName`, `alternateScientificName` | property of `Taxon` | Taxon (released) | `https://bioschemas.org/terms/<name>` |
| `vernacularName` | property of `Taxon` | Taxon (released) | `http://rs.tdwg.org/dwc/terms/vernacularName` (Darwin Core) |
| `hasStatus` | property of `Gene` | Gene (released) | `https://bioschemas.org/terms/hasStatus` |
| `BioSample` | type | BioSample (draft) | `https://bioschemas.org/terms/BioSample` |
| `collector`, `custodian`, `isControl`, `samplingAge` | properties of `BioSample` | BioSample (draft) | `https://bioschemas.org/terms/<name>` |

```json
"@context": ["https://w3id.org/ro/crate/1.3/context",
             {"TaxonName": "https://bioschemas.org/terms/TaxonName",
              "scientificName": "https://bioschemas.org/terms/scientificName"}]
```
Regenerate this table when Bioschemas or RO-Crate release new versions: `python scripts/bioschemas_terms.py bioschemas_types.jsonld context.json [bioschemas_profiles_shacl.jsonld]` (downloads listed in the script; standard library only).

## From Bioschemas markup to crate entities
- **Split out a root dataset.** Do not turn the Bioschemas entity itself into the root data entity (no `["Dataset", "LearningResource"]` root, and do not reuse its profile, authors or dates for the root). Make a separate root `Dataset` for the crate (`name`, `description`, `datePublished`, `license`, `publisher`), and link the entity of the Bioschemas type/profile from it: `mainEntity` when the crate is about that entity, plus `hasPart` when it is content the crate contains (a training material, dataset, tool's code). Things that are not content (`Event`, `Taxon`, `Gene`, `Person`) are `mainEntity` only, or `mentions` when there are several. Even a Bioschemas `Dataset` stays a separate entity with its own `@id`, linked by `mainEntity`/`hasPart`. A catalogue page per record: `ro-crate-create`, references/web-records.md (record = root, resource = `mainEntity`).
- **`https` versus `http` schema.org.** Bioschemas markup usually uses `"@context": "https://schema.org"`, which expands to `https://schema.org/…` IRIs; the RO-Crate context uses `http://schema.org/`. JSON-LD flattening against the RO-Crate context then leaves full IRIs (`"https://schema.org/name"`) instead of terms (checked with pyld 3.3.0). Rewrite the source context to `http://schema.org/` (or the IRIs) before flattening, or map by term name.
- Flatten nested objects into the `@graph`; check every remaining key against the context; drop or map terms it lacks (TeSS-style extras such as `eligibility` or `hostInstitution` are not schema.org); never emit undefined terms.
- Profile URIs: replace a draft named in the markup by the released version when one exists; keep a draft only where no release exists (see above).

## Validating against Bioschemas profiles
- `rocrate-validator` checks RO-Crate rules, not Bioschemas ones. For the profile's minimum/recommended properties read the profile page, or use the SHACL shapes of the released profiles (https://bioschemas.org/profiles/bioschemas_profiles_shacl.jsonld, `.ttl`) with a SHACL engine such as `pyshacl`.
- **Caution with those shapes** (checked 2026-10-23): some property paths use `https://discovery.biothings.io/view/bioschemas/…` IRIs instead of the real terms (`programmingLanguage`, `input`, `output`, `isBasedOn`, `codeRepository` in ComputationalTool/ComputationalWorkflow; `isBasedOn` in Course, Dataset, Taxon, TaxonName; `scientificName`, `alternateScientificName` in Taxon; `hasStatus` in Gene). Those are artefacts of the profile generator, not term IRIs: never use them in a crate or `@context`. A SHACL run reports such properties as missing even when the crate has them; check those by hand against the profile page.
