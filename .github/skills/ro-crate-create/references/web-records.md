# A detached crate for each record of a web application

For a catalogue, registry or portal that has a page per record (a training material, an event, a sample) and wants to offer an RO-Crate for each one, usually generated from schema.org or Bioschemas JSON-LD it already produces. To serve and advertise it see `ro-crate-publish` (references/signposting.md there). Spec: `structure#detached-ro-crate-package`, `crate-focus`, `root-data-entity#root-data-entity-identifier`.

## Shape
Keep the record and the thing it describes as two entities:

| Entity | `@id` | `@type` | Properties |
|---|---|---|---|
| Root data entity = the record | the record page's absolute URL | `Dataset` only | `name`, `description`, `datePublished` (record date), `license`, `publisher` = the site (`Organization`), `mainEntity`, `hasPart` only for content (below) |
| Main entity = the described thing | its own URL if that URL identifies it, else `#local` with `url` as a string | its own schema.org type (`LearningResource`, `Course`, `Event`, ...) | its schema.org/Bioschemas properties, `conformsTo` its Bioschemas profile, `identifier` (its DOI), `sdPublisher` = the site |

- **Do not merge the described thing's type into the root** (`["Dataset", "LearningResource"]` on the root): the record and the resource have different authors, dates, licences and identifiers.
- **`hasPart` only for content.** A learning material, document or dataset at a URL is a web data entity of the crate: list it in `hasPart` and as `mainEntity`. An event, course instance, person or organisation is not content: `mainEntity` only (spec: "RO-Crates with a contextual entity as `mainEntity`"). A web data entity that is an HTML page need not be `File`; `File` is required only for downloadable files (and then the `@id` must download directly).
- **The described thing's profile** (e.g. `https://bioschemas.org/profiles/TrainingMaterial/1.0-RELEASE`; the released version where one exists) goes as `conformsTo` on the main entity, with a `["CreativeWork", "Profile"]` entity: it is a profile of that entity, not an RO-Crate profile, so it does not belong in the root's `conformsTo`. `rocrate-validator` 0.13.0 then gives a RECOMMENDED warning that the root "SHOULD have a `conformsTo`"; that is expected. Some types have no released Bioschemas profile (Event, BioSample): a draft may then be declared, although it is not recommended, or none at all (`ro-crate-bioschemas` lists the released ones and the trade-off).
- **Identifiers.** Root `cite-as`/`identifier` only for a PID that resolves to this crate. A DOI of the described resource (often resolving to Zenodo or the provider) is the main entity's `identifier` (`https://doi.org/…` form). Never invent PIDs.
- **Root `@id` without a trailing `/`** (a page URL such as `https://example.org/materials/42`) is allowed for detached crates in 1.2+; it fails the 1.1 rules ("Root Data Entity URI MUST end with `/`"), so validate against the declared version.
- **Licences** as entities with the SPDX license URI `http://spdx.org/licenses/<id>`. If the record has no licence, a text value saying none is specified satisfies the root's required `license`; never pick one.
- Invalid URLs in source data (free text in a URL field) become no `@id` and no part: keep them as a `url` string or leave them out.

Example (also a test fixture of this repository, passing `rocrate-validator -p ro-crate-1.3` at REQUIRED):
```json
{"@id": "https://catalogue.example.org/materials/42", "@type": "Dataset",
 "name": "Introduction to sequence alignment", "description": "Catalogue record for a training material.",
 "datePublished": "2026-10-01", "license": {"@id": "http://spdx.org/licenses/CC-BY-4.0"},
 "publisher": {"@id": "https://catalogue.example.org/"},
 "mainEntity": {"@id": "https://training.example.com/alignment/"},
 "hasPart": {"@id": "https://training.example.com/alignment/"}},
{"@id": "https://training.example.com/alignment/", "@type": "LearningResource",
 "name": "Introduction to sequence alignment", "description": "Tutorial on sequence alignment.",
 "conformsTo": {"@id": "https://bioschemas.org/profiles/TrainingMaterial/1.0-RELEASE"},
 "sdPublisher": {"@id": "https://catalogue.example.org/"}}
```
For an event, the main entity is `{"@id": "https://events.example.com/workshop-2027", "@type": "Event", ...}` and the root has no `hasPart`. Note that the event and its web page are then the same `@id`; to keep them apart give the `Event` a `#event` id and a `url` (or `subjectOf` a `WebPage`).

## Generating it from existing schema.org JSON-LD
- Start from the app's existing generator output, not a second hand-written mapping, so the two stay in step.
- Flatten nested objects into the `@graph` with `{"@id"}` references (JSON-LD flattening, or a small recursive pass that gives nested objects without `@id` a `#type-n` id); merge entities that appear twice (the same publisher) instead of duplicating them.
- List the root right after the descriptor; readers that do not look it up via `about` then still find it.
- Check every type and property against the RO-Crate context (`curl -sL -H 'Accept: application/ld+json' https://w3id.org/ro/crate/1.3/context`), which is fixed to one schema.org release (1.3: 30.0); the app may emit newer schema.org terms. For terms it lacks (Bioschemas extensions such as `eligibility`, or app-specific keys) either add them to an `@context` array with their definition (`references/jsonld.md`) or leave them out of the crate and say which ones; never emit undefined terms.
- Normalise values: dates as ISO 8601 with offset (`2026-12-11T09:00:00+01:00`, not `2026-12-11 08:00:00 UTC`), DOIs as `https://doi.org/` URLs, plain string keys (generators in Ruby and other languages may use symbol keys that a string lookup silently misses).
- Validate a few generated crates (`ro-crate-validator`), including records with missing licence, missing or invalid URL and no DOI.
