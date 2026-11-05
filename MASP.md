# RO-Crate MASP and how wpsld could use it

Notes on [RO-Crate Machine Actionable Schemas and Profiles (MASP)](https://github.com/Language-Research-Technology/ro-crate-masp)
and a plan for generalising wpsld's schema.org reader (`no.s11.wpsld.soss.SchemaFromJsonLd`)
to read MASP schemas and profiles, including profile-specific specialisations of
classes and properties.

Based on the repository at commit
[`6d7a018`](https://github.com/Language-Research-Technology/ro-crate-masp/tree/6d7a018baf827b5716d79a17462e3b3edb15725f)
(2026-10-14), in particular the self-describing
[MASP profile documentation](https://github.com/Language-Research-Technology/ro-crate-masp/blob/main/profiles/ro-crate-masp/profile-crate/profile-documentation.md).
MASP is a **prototype / proof of concept** (JavaScript reference implementation,
Apache-2.0); its example profiles and schemas are explicitly not their permanent
home, so pin anything we copy to a commit.

## What MASP is

MASP packages a schema or a profile **as an RO-Crate**, with the rules as
contextual entities in the Schema.org-style-schema (SoSS) shape that schema.org
itself uses (`rdfs:Class`, `rdf:Property`, `domainIncludes`, `rangeIncludes`).
The same crate drives generated documentation, validation, and (planned)
editor configuration for [Crate-O](https://github.com/Language-Research-Technology/crate-o).

| Term | Meaning |
|------|---------|
| **Schema** (general purpose) | Classes and properties for use across profiles, loose domains/ranges with inheritance, e.g. schema.org, the LDAC terms `https://w3id.org/ldac/terms#`. Schema crates conform to `https://w3id.org/ro/profiles/schema/1.0`. |
| **Profile** | A specialisation for a purpose ([W3C dx-prof](https://www.w3.org/TR/dx-prof/)): a subset of schema terms with stricter constraints and local descriptions. Root is `["Dataset", "Profile"]`, `isProfileOf` the RO-Crate spec, conforms to the MASP profile. |
| **Profile Crate** | The RO-Crate holding a profile's rules plus documentation (`profile-documentation.md`, `index.html`) and optionally a Crate-O mode (`crate-o-mode.json`). |

Example chain: *LDAC Profile* —specialises→ *LDAC Schema* —extends→ *schema.org*.

### Example profiles and schemas

The [example profiles and schemas landing page](https://language-research-technology.github.io/ro-crate-masp/)
(GitHub Pages, generated from the same repository's `catalogue.json`) lists and
filters them all, with generated documentation for each. The most relevant here:

| Name | Kind | Classes / properties | Why it matters for wpsld |
|------|------|----------------------|---------------------------|
| [RO-Crate MASP Profile](https://language-research-technology.github.io/ro-crate-masp/profiles/ro-crate-masp/profile-crate/index.html) | Profile | 13 / 29 | Defines MASP crates themselves; self-describing, a good first fixture |
| [RO-Crate Machine Actionable Profile](https://language-research-technology.github.io/ro-crate-masp/profiles/ro-crate/profile-crate/index.html) | Profile | 3 / 6 | Minimal rules for RO-Crate itself, the default for any crate |
| [Language Data Commons Comprehensive Collection Profile](https://language-research-technology.github.io/ro-crate-masp/profiles/ldac/profile-crate/index.html) | Profile | 30 / 99 | Realistic profile on top of a schema crate, with a Crate-O mode |
| [Language Data Commons Schema Terms](https://language-research-technology.github.io/ro-crate-masp/schemas/ldac/schema-crate/index.html) | Schema | 5 / 54 | A general-purpose schema extending schema.org |
| [RO-Crate Photo Collection Profile](https://language-research-technology.github.io/ro-crate-masp/profiles/rocphotos/profile-crate/index.html) | Profile | 20 / 65 | Collections kept at rest on a file system as nested crates, close to wpsld's in-place idea |
| [Schema.org Vocabulary](https://language-research-technology.github.io/ro-crate-masp/schemas/schema-org/schema-crate/index.html) | Schema | 933 / 1521 | schema.org as a MASP schema crate (older release, `https://` IRIs) |
| [Workflow Profile](https://language-research-technology.github.io/ro-crate-masp/profiles/workflow/profile-crate/index.html) | Profile (experimental) | 9 / 19 | Port of an established RO-Crate profile |

Like the repository says, these are working examples whose URLs may move, so
link to them for documentation but bundle pinned copies for tests.

## Structure of a MASP crate

### Finding the rules

The root's `hasResource` lists `ResourceDescriptor`s (dx-prof). The one with
`hasRole` `http://www.w3.org/ns/dx/prof/role/schema` (conventionally
`#hasSpecializedSchema`) lists **all rule entities in its `hasPart`**; only
those are rules, everything else in the crate is ignored. Other roles seen:

| Role | Artifact |
|------|----------|
| `…/dx/prof/role/schema` | the rules (`hasPart`) |
| `…/dx/prof/role/vocabulary` | enumeration values (e.g. `#hasEnumerationValues` in the schema.org schema crate) |
| `…/dx/prof/role/specification` | `index.html` |
| `…/dx/prof/role/guidance` | `profile-documentation.md` |
| `https://w3id.org/ro/mode` | `crate-o-mode.json` (Crate-O editor layout) |
| `https://w3id.org/ro/conformance-indicators` | conformance indicators |

### Rule entities

**Class rule** (`rdfs:Class`), local id such as `#class_ScholarlyArticle`:

```json
{
  "@id": "#class_ScholarlyArticle",
  "@type": "rdfs:Class",
  "prov:specializationOf": {"@id": "http://schema.org/ScholarlyArticle"},
  "rdfs:label": "ScholarlyArticle",
  "rdfs:comment": "A scholarly article in this profile's context.",
  "sh:minCount": 1,
  "sh:maxCount": 1
}
```

- `prov:specializationOf` links to the base vocabulary class; an entity matches
  the rule when its (context-resolved) `@type` matches that.
- `sh:minCount`/`sh:maxCount` **on a class** = how many instances of the type the
  whole crate must/may have.
- May also carry `rdfs:subClassOf` (LDAC: `CollectionEvent` ⊂ `Event`, `CreateAction`).

**Property rule** (`rdf:Property`), one **per property per class**, e.g. `#prop_author_ScholarlyArticle`:

```json
{
  "@id": "#prop_author_ScholarlyArticle",
  "@type": "rdf:Property",
  "prov:specializationOf": {"@id": "http://schema.org/author"},
  "rdfs:label": "author",
  "domainIncludes": {"@id": "#class_ScholarlyArticle"},
  "rangeIncludes": {"@id": "#class_Person"},
  "sh:minCount": 1
}
```

- `rdfs:label` is the property name as used (compacted) in the target crate's JSON.
- `domainIncludes` points to **class rules** (local ids), not vocabulary classes.
- `sh:minCount`/`sh:maxCount` **on a property** = number of values per matching entity.
- `rangeIncludes` may point to: other class rules (values are entity references,
  validated recursively), primitive types (Text, Number, Boolean, Date),
  `PropertyValue` constraints, or an `ItemList` of allowed values.

**Value constraints**

- `PropertyValue` with `value`: exact literal or `{"@id"}` match, or a regex
  string in JavaScript syntax such as `/^readme\.md$/i`. Referenced only through
  `rangeIncludes`, never as a `value` on the property rule itself.
- `ItemList` (`itemListElement`): the value must be one of the listed `@id`s;
  list items can carry further required properties.
- `DefinedTermSet` / `DefinedTerm`: documented terms (validator passes through).

**Root detection**: the metadata descriptor's class rule is found via a
property rule with `rdfs:label` `"@id"` whose `rangeIncludes` is a
`PropertyValue` with value `ro-crate-metadata.json`; its `about` property rule
then ranges over the root data entity's class rule.

### Validation algorithm (reference implementation)

1. Find the schema `ResourceDescriptor`, read its `hasPart`.
2. Parse rules: `rdfs:Class` → ClassRule, `rdf:Property` → PropertyRule,
   `ItemList` → ItemListRule, `PropertyValue` → constraint, `DefinedTermSet` → TermRule.
3. Detect the root class rule (pattern above).
4. For each class rule, find crate entities whose resolved `@type` matches its
   `prov:specializationOf`; validate the linked property rules; count instances
   against class cardinality.
5. Property rules: cardinality, `PropertyValue` constraints, range checks
   (primitives by JS type, references recursively, `ItemList` membership).
6. Report `{ error, success, rules }` per rule and entity.

## Observations that matter for a Java implementation

- **Contexts.** MASP crates use the RO-Crate **1.2** context plus local additions,
  e.g. `["https://w3id.org/ro/crate/1.2/context", {"@vocab": "http://schema.org/", "ldac": "https://w3id.org/ldac/terms#"}]`,
  sometimes with extra term definitions (`rdfs:label`, `@base: null`).
  Unprefixed `domainIncludes`/`rangeIncludes` rely on that context, while
  `rdfs:`, `prov:` and `sh:` compact IRIs are used as-is. We need a small
  context resolver (context arrays, prefixes, `@vocab`, string and `{"@id"}`
  term definitions) with **bundled, pinned copies** of the RO-Crate 1.2 and 1.3
  contexts, but not full JSON-LD expansion, since crates are flattened.
- **Contexts and profiles are separate.** JSON-LD contexts only map terms to
  IRIs; MASP profile and schema crates are RO-Crates (using contexts) that
  carry rules. `no.s11.wpsld.jsonld` handles contexts: `ContextCache` is a
  pre-warmed cache of bundled context documents, listed in `jarcache.json`
  (JSON-LD Java's format, so other jars can add contexts), with the RO-Crate
  1.0–1.3 contexts (CC0); `JsonLdContext` processes a `@context` (arrays,
  `@vocab`, prefixes, term definitions in any order) without fetching and
  without throwing. Profiles will get their own loading and cache.
- **RO-Crate names and terms outside schema.org.** `CrateTerms` names
  schema.org terms as RO-Crates write them, through the RO-Crate 1.3 context:
  the schema.org name, except `File` for `schema:MediaObject` (checked over
  all of schema.org 30.0: no other name differs). The context also maps terms
  outside schema.org (`conformsTo`, `Profile`, `hasResource`… from Dublin
  Core, dx-prof, PAV, PCDM, CodeMeta, Bioschemas, GeoSPARQL, PROV, IANA link
  relations, RO terms); the schema model has no definitions for them, so the
  editor keeps their values but offers no fields. The core MASP RO-Crate
  profile should cover them.
- **JSON-LD 1.0.** RO-Crate 1.2/1.3: the metadata document "MUST be a
  document which is valid JSON-LD 1.0 in flattened and compacted form", so
  JSON-LD 1.1 context features (scoped contexts, `@import`, `@protected`,
  `@propagate`, `"@version": 1.1`) are reported as not valid rather than
  processed. (JSON-LD Java, whose cache format we borrow, is JSON-LD 1.0 only
  too; we don't use the library.)
- **Unlicensed contexts.** The Workflow Run, Workflow Testing
  (`ResearchObject/ro-terms`) and LDAC contexts state no licence, so they are
  not bundled; ask the maintainers, or fetch them at run time once remote
  loading is added.
- **http vs https schema.org.** RO-Crate contexts and profiles (e.g. LDAC) use
  `http://schema.org/`; MASP's own schema.org *schema crate* uses
  `https://schema.org/`. Treat them as the same namespace and normalise to
  `http://schema.org/` (as our pinned 30.0 file and the RO-Crate 1.3 context do).
- **`sh:` is not defined.** MASP crates use `sh:minCount`/`sh:maxCount`, but
  their context (`["https://w3id.org/ro/crate/1.2/context", {"@vocab":
  "http://schema.org/"}]`) doesn't define the `sh` prefix, so in JSON-LD
  they are absolute IRIs with the scheme `sh`, not SHACL terms. The MASP
  JavaScript tools match the written key. `CrateReader` reports the undefined
  prefix once; the profile reader should accept both `sh:minCount` and
  `http://www.w3.org/ns/shacl#minCount` (and suggest MASP define the prefix).
- **Reading profile crates.** `CrateReader` reads them into a `CrateDocument`
  whose `types()`, `values()` and `references()` work by expanded IRI (so
  `domainIncludes` and `schema:domainIncludes` match, `https://schema.org/`
  taken as `http://`). The MASP profile crate (13 classes, 29 properties) and
  the minimal RO-Crate profile (3, 6) load without errors; test fixtures
  pinned to commit `6d7a018`.
- **Reading the rules.** `ProfileReader` (package `no.s11.wpsld.masp`) reads
  a profile crate into a `Profile` of `ClassRule`s and `PropertyRule`s (label,
  description, `prov:specializationOf`, `rdfs:subClassOf`, domain, range,
  `Cardinality`), with ranges as class rules, IRIs, `PropertyValue`
  constraints (literal, `@id` or `/regex/flags`) or allowed values
  (`ItemList` elements, or the terms of a `DefinedTermSet`), and finds the
  descriptor and root class rules. The MASP, minimal RO-Crate and LDAC
  profiles read without problems (13/29, 3/6 and 30/99 rules, as in MASP's
  catalogue). Findings on the way:
  - Rule `@id`s can be absolute IRIs (`http://schema.org/name` in the
    RO-Crate profile) or compact IRIs (`ldac:AccessTypes` in LDAC, referred
    to expanded elsewhere), so rules are identified by expanded `@id`.
  - Ranges are written as words, `{"@id": "Text"}`: in JSON-LD a relative
    IRI, but meant as `schema:Text`, so read as a vocabulary term.
  - Cardinalities are numbers or strings (`"1"`).
- **Loose literals.** `sh:minCount` appears both as numbers and strings (`"1"`);
  values can be single objects or arrays; regexes are JavaScript-style `/…/flags`
  and need translating to `java.util.regex`.
- **schema.org as MASP.** The MASP schema.org schema crate (933 classes,
  1521 properties, an older release than our pinned 30.0 with 1007/1684) has the
  same SoSS shape as schema.org's own JSON-LD release. Our pinned
  `schemaorg-30.0-all-http.jsonld` stays the base vocabulary, being the one
  RO-Crate 1.3 uses.
- **Same property, different rules per class**: e.g. `name` on `ProfileDataset`
  and on `DefinedTermSet` are separate property rules. The model must key
  property rules by (class rule, property), not by property IRI alone.
- **Crate-O modes** (`crate-o-mode.json`) carry editor layout (input groups,
  ordering); MASP intends to generate them from the rules.

## Plan: generalise `SchemaFromJsonLd`

Goal: one model that the editor can query — *for an entity with these `@type`s,
under these profiles, which properties apply, which are required, how many
values, and what kind of values* — built from schema.org plus any MASP schema
or profile crate.

1. **Split reading from modelling.**
   - `SoSSReader`: reads any SoSS-shaped flattened JSON-LD (schema.org release
     file, MASP schema crate, MASP profile crate) into term definitions, using
     a minimal `JsonLdContext` resolver (bundled RO-Crate 1.2/1.3 contexts,
     prefixes, `@vocab`, https→http schema.org normalisation).
   - `Vocabulary`: the general-purpose layer (today's `ClassDef`, `PropertyDef`,
     `DefinedTerm`, plus `supersededBy`), merged from several sources: schema.org
     30.0 as the base, then schema crates such as the LDAC terms.
2. **Profile layer.**
   - `Profile`: loaded from a MASP profile crate via the schema `ResourceDescriptor`.
   - `ClassRule` (local id, `specializationOf` → vocabulary `ClassDef`, label,
     comment, instance cardinality, `subClassOf`).
   - `PropertyRule` (local id, label = JSON key, `specializationOf` →
     `PropertyDef`, domain `ClassRule`s, value cardinality, range as
     `ClassRule`s / datatypes / `ValueConstraint` (`PropertyValue` literal,
     `@id` or regex) / `AllowedValues` (`ItemList`, `DefinedTermSet`)).
   - Root detection by the `@id` + `ro-crate-metadata.json` pattern.
3. **Editor query API**, e.g. `profile.rulesFor(Set<URI> types)` returning the
   ordered property rules (required first), falling back to the vocabulary's
   `domainIncludes` (including superclasses) for "other properties" when no
   profile or no rule applies.

   With a profile, the form distinguishes three kinds of properties for an
   entity's type(s):

   | Kind | From | In the form |
   |------|------|-------------|
   | **Required** | property rules of the matching class rule with `sh:minCount` ≥ 1 | first, open, marked as required, using the profile's label and description; saving warns while they are missing |
   | **Optional, named by the profile** | property rules without `sh:minCount` (or 0) | next, open: what the profile suggests describing |
   | **Other properties of the type** | the vocabulary (e.g. schema.org `domainIncludes`) of the type and its superclasses, not named by the profile | last, folded (and searchable), grouped by defining class as today, so nothing schema.org allows is out of reach |

   `sh:maxCount` limits the values of the first two kinds ("+" hidden at the
   maximum; 1 means a single value); range constraints (`PropertyValue`,
   `ItemList`) shape their controls. For the third kind the vocabulary is the
   one the profile is bound to (see caching above), and for a class rule
   specialising a term outside schema.org (e.g. `ldac:CollectionEvent`), its
   `rdfs:subClassOf` superclasses and any schema crate defining it. Values the
   entity has outside all three stay under "Not properties of …" as today.
   Without a profile (the default) there is only the third kind, as now.
   In the editor, a **profile drop-down** chooses the `FormFields` used: the
   default is today's schema.org fields (`SchemaOrgFormFields`, in effect
   "no profile"), and the MASP profiles that can be loaded are listed after
   it; a crate's root `conformsTo` can preselect one.
4. **Validation (later)**: port the MASP algorithm on top of the same model,
   reporting per entity and property, so the editor can show problems inline.
5. **Profile resolution**: a crate's root `conformsTo` names its profiles; start
   with bundled profile crates looked up by IRI, add fetching later.
6. **Tests and fixtures**: bundle copies (pinned to commit `6d7a018`, Apache-2.0,
   with attribution) of the MASP profile crate itself (self-describing, 13
   classes / 29 properties), the minimal RO-Crate profile and the LDAC profile
   and schema; test that rules, cardinalities and specialisations resolve to our
   schema.org 30.0 terms.

### Caching once profiles are involved

Today `SchemaFromJsonLd` loads one vocabulary, schema.org 30.0, and caches its
traversals (superclasses, domains, ranges) lazily in the `ClassDef` and
`PropertyDef` objects, which is safe because that vocabulary never changes once
loaded. Measured: about 14 MB retained heap, mostly the Jackson tree kept for
lookups; the model objects themselves are well under 1 MB.

With MASP that single cache is no longer enough:

- **Each profile has its own view.** Which properties apply to a class,
  which are required, their cardinality, allowed values and even labels and
  descriptions differ per profile. Profile-specific information must never
  be stored on the shared vocabulary objects; the profile layer (`ClassRule`,
  `PropertyRule`) refers to vocabulary terms and adds its own rules on top.
- **Profiles may target different schema versions.** Each RO-Crate context
  declares the schema.org release it is based on in its `schemaVersion`
  (read with `ContextCache.schemaOrgVersion`):

  | RO-Crate context | `schemaVersion` | schema.org |
  |---|---|---|
  | 1.0 | `http://schema.org/version/5.0/` | 5.0 |
  | 1.1 | `http://schema.org/version/10.0/` | 10.0 |
  | 1.2 | `https://schema.org/docs/releases.html#v22.0` | 22.0 |
  | 1.3 | `https://schema.org/docs/releases.html#v30.0` | 30.0 |

  So a profile written for the RO-Crate 1.2 context implies schema.org 22.0
  (1.3 implies 30.0, the one bundled today), and
  MASP's own schema.org schema crate uses `https://` IRIs. Terms can be added,
  superseded or retired between releases, so a profile must be resolved
  against the vocabulary version it was written for, not silently against
  ours. That means several vocabulary instances side by side, each pinned,
  checksummed and cached by (vocabulary IRI, version), and shared by all
  profiles bound to that version.
- **Cache per view, not per term.** Results such as
  `fieldsFor(types)` are cached per profile (or per set of profiles, when a
  crate conforms to several and their rules are combined), keyed by profile
  IRI and version, and lazily filled per set of types.
- **Memory.** Holding several schema.org versions at ~14 MB each is fine, but
  if it adds up, the Jackson tree can be dropped once a vocabulary's model is
  fully built (every lazy value forced, e.g. by the QA checks), keeping only
  the model objects.

### Loading profiles and handling errors

Today the only schema is bundled in the application (pinned schema.org 30.0,
checksum tested), so a load failure can only be a corrupt file or a bug, which
would fail the same way again; the editor shows the error instead of retrying.
Profiles loaded dynamically, from a crate's `conformsTo`, a local file or the
network, need more care, and must never leave the editor waiting forever:

- **Never block the editor on a profile.** Load each profile (and the schema
  crates and vocabulary versions it needs) asynchronously, and keep editing
  possible meanwhile with the plain schema.org fields; switch to the profile's
  fields when it arrives, and say which profile is in use.
- **Time limits and cancellation.** Every fetch has a timeout (e.g.
  `CompletableFuture.orTimeout`), and loading can be cancelled when the user
  opens another crate or closes the window.
- **Transient vs. permanent errors.** Retry with back-off only for transient
  failures (network down, timeout, HTTP 5xx). Do not retry deterministic ones:
  not found (404), not a valid RO-Crate, not a MASP profile, wrong checksum.
- **Partial results.** A profile with some broken rules (unresolvable
  `prov:specializationOf`, unknown vocabulary version, malformed cardinality,
  invalid regex) should still load what it can, with each problem reported as a
  warning against that rule, rather than failing the whole profile.
- **Missing dependencies.** If a profile's schema crate or vocabulary version
  can't be loaded, fall back to the closest available (e.g. our bundled
  schema.org 30.0) and warn that terms may differ.
- **Offline first.** Bundle the commonly used profiles, and cache fetched
  profile crates locally (by IRI, version and checksum), so a crate opened
  before can be edited offline; fetching is then only for new or updated ones.
- **Visible status.** Show per profile whether it is loading, loaded, loaded
  with warnings, failed (with the reason and a retry button) or fell back, so
  a slow or broken profile is never a silent, endless spinner.

### Types from profile vocabularies

A profile may offer classes from its own vocabulary (e.g. `ldac:CollectionEvent`)
that are not in the RO-Crate context. In RO-Crate an entity SHOULD have a
schema.org type, so when the user picks only such types the editor should
warn and suggest adding a schema.org type too — the class's schema.org
superclass where the profile gives one (`rdfs:subClassOf`, e.g. `Event`), or
otherwise a default such as `CreativeWork` or `Thing`. Superclasses of a
selected type need not be added, as elsewhere (see PLAN.md).

### Bundled and dynamic profiles

- **Bundled:** the core RO-Crate profile (`profiles/ro-crate`) is bundled in
  `lib/src/main/resources/masp-profiles/`, listed in `masp-profiles.json`
  with its identifiers, source, commit and SHA-256. Upstream has no releases,
  so profiles are pinned to a commit with `scripts/update-masp-profiles.sh
  <commit> [profile...]`, and updates are reviewed as a diff. Tests check
  the checksums and that each bundled profile reads.
- **Identifiers:** a profile crate's root is often `./`, so crates name it in
  `conformsTo` by its conformance indicators (an `ItemList` under the role
  `https://w3id.org/ro/conformance-indicators`), which `ProfileReader` reads
  as `Profile.identifiers()`.
- **Dynamic:** other profiles (e.g. LDAC) are loaded when needed with
  `ProfileCache.load(URI)` (`https:`, `http:` or `file:`, of the crate or its
  folder): time limits, never throws, failures not cached so they can be
  retried; loaded profiles are kept for the session and found by identifier.
- **Next, caching like JSON-LD Java:** its default client is Apache
  HttpClient 4's `CachingHttpClientBuilder` (in memory, 500 entries of up to
  256 kB, HTTP caching headers honoured, else one day), with the
  `jarcache.json` documents layered in front as cached responses. Rather than
  adding HttpClient (not modular, see the SHACL survey), keep Java's
  `HttpClient` and add our own disk cache layer: bundled → memory → disk
  (`ETag`, `Last-Modified`, `max-age`) → network with conditional requests
  (`304` reuses the copy), and a stored copy used when offline, with a
  warning that it may be out of date.

### Testing with Jena

Generating SHACL from MASP (below) needs no library. To run the generated
shapes, and to validate our own `ro-crate-metadata.json` independently of our
code, Apache Jena 6.2 (`jena-shacl`, with JSON-LD parsing) can be a
**test-only** Gradle dependency: it runs on the module path with a few fixes
(see the survey), and keeping it out of the application avoids its ~15 MB and
the module descriptors jlink would need. The tests can run in the Temurin
container rather than the conda env.

### Later: converting MASP to SHACL

A converter from MASP rules to SHACL shapes should be fairly direct for the
constraints: unroll the `prov:specializationOf` intermediaries (a class rule
becomes a node shape for the vocabulary type, a property rule a property
shape with `sh:path` the specialised property), and map the rest to existing
SHACL terms: `sh:minCount`/`sh:maxCount` as they are, class-rule ranges as
`sh:node`/`sh:class`, data types as `sh:datatype`, `PropertyValue` literals as
`sh:hasValue`/`sh:in`, regexes as `sh:pattern` (with `sh:flags`), and
`ItemList`/`DefinedTermSet` values as `sh:in`.

The tricky part is **selecting the focus nodes**: RO-Crate types are not
specific enough. `Dataset` is the type of both the root data entity and every
folder, so `sh:targetClass schema:Dataset` would apply the root's rules to
all folders. The root must be found by a pattern instead: it is what the
metadata descriptor is `about`. In SHACL Core that could be a shape targeting
the descriptor (`sh:targetNode`, its `@id` being fixed) with a property shape
`sh:path schema:about` and `sh:node` to the root's shape, though the
descriptor's IRI depends on the base the crate is parsed with; otherwise
SPARQL-based targets (SHACL-AF) are needed. MASP's class-rule cardinality on
instances in the whole crate also has no direct SHACL Core counterpart.
rocrate-validator uses SHACL for the RO-Crate profiles and is worth comparing
for how it selects the root.

Simpler: make the shapes **profile-specific and layered**. The core RO-Crate
profile is validated first, and only its shapes need the descriptor pattern
to find the root (and tell the root from other `Dataset`s, data entities
from contextual entities). A profile's own shapes can then assume a crate
that passed the core profile, as MASP profiles are profiles of RO-Crate
(`isProfileOf`). The core layer could also mark what it found, e.g. give
the root a specific class through a SHACL rule (SHACL-AF), so later layers
target it with `sh:targetClass`; to check whether rocrate-validator already
does this for its RO-Crate classes.

### Open questions

- MASP is still a prototype, so how stable are the rule shapes? Which MASP
  version should a profile crate declare, and how do we detect changes?
- Should wpsld's own crates declare a profile (`conformsTo`) by default, and which?
- Use Crate-O mode files for layout, or derive layout from the rules only?
- Write profile documentation back (e.g. editing profiles in wpsld itself)?
- Matching rules when an entity has several `@type`s, or a subtype of the
  specialised class: does a `Person` rule apply to a schema.org subclass?
