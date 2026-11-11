# Starting from CITATION.cff, codemeta.json or other metadata

When a project already has `CITATION.cff` or `codemeta.json`, reuse it for the root data entity instead of retyping. Use existing tools for the conversion, then review: the result is a draft.

## CITATION.cff (tested: cffconvert 3.x, pyld)
```bash
python -m venv /tmp/cff && /tmp/cff/bin/pip install cffconvert pyld
/tmp/cff/bin/cffconvert --validate                         # checks ./CITATION.cff against the CFF schema
/tmp/cff/bin/cffconvert -f schema.org -o schema.json       # also: codemeta, zenodo, bibtex, apalike, ris, endnote, cff
```
`schema.org` output is JSON-LD with **nested** objects (authors, affiliation). RO-Crate needs a **flat** graph with `{"@id"}` references, which JSON-LD flattening produces against the RO-Crate context (the spec recommends flattening and compaction for this):
```python
import json
from pyld import jsonld
flat = jsonld.flatten(json.load(open("schema.json")), "https://w3id.org/ro/crate/1.3/context")
```
Result on a test file: one `SoftwareSourceCode` node (`name`, `description`, `version`, `datePublished`, `license`, `identifier`, `keywords`, `codeRepository`, `url`), a `Person` per author (ORCID as `@id`, `givenName`, `familyName`) and an `Organization` per affiliation (blank node `_:b1`, `legalName`).

Turn that into the crate (all by review, nothing is automatic):
- The root data entity is `./`, `@type` `Dataset` (keep `SoftwareSourceCode` too if the crate is the software), not a blank node; add the descriptor entity.
- `keywords` becomes **one comma-separated string**; `license` becomes a reference to a `CreativeWork` entity (`@id` the SPDX URL, `name`) not a bare string; `identifier` a DOI becomes a `PropertyValue` (or keep the string URI) plus `cite-as`; blank-node affiliations become `Organization` entities with `name` (use a ROR `@id` only when given); authors without ORCID get `#family-given` ids.
- Add what CFF does not provide: `datePublished` (CFF `date-released` is the release date, fine if that is what you mean), the data files (`hasPart`), `name`/`description` for them.
- Apply the **Do not guess** rule: if an author has no ORCID or no affiliation, leave it out.

Reverse direction (crate to CITATION.cff): copy `name`, `author` (name, ORCID), `version`, `license` (SPDX id), `datePublished`, `identifier`/`cite-as` by hand; no tool was tested.

## codemeta.json
CodeMeta terms are in the RO-Crate context (`buildInstructions`, `developmentStatus`, `continuousIntegration`, `embargoEndDate`, `hasSourceCode`, `isSourceCodeOf`, `issueTracker`, `readme`, `referencePublication`, `softwareSuggestions`), so most properties carry over unchanged. Flatten with the same `jsonld.flatten(..., ctx)` call (not tested with a codemeta file) and fix the same points (root node, keywords string, license entity).

## Other sources
Zenodo/DataCite JSON, `package.json`, `pyproject.toml`, README front matter: map the same handful of properties (`name`, `description`, `author`, `license`, `version`, `datePublished`, `keywords`, `identifier`, `url`). Do not copy fields you cannot map faithfully; record the origin with `isBasedOn` or in the change history (`references/provenance.md`) if useful.
