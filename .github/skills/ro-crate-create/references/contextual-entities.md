# Contextual entities: patterns (RO-Crate 1.2/1.3)

Spec: https://www.researchobject.org/ro-crate/specification/1.3/contextual-entities

Give every referenced thing an entity in `@graph`, with a real absolute `@id` when one exists (ORCID, ROR, DOI, GeoNames URI, PRONOM) and `#local` otherwise. Do not give two different things the same `@id`. Never invent ORCIDs/DOIs/RORs; the ORCID `https://orcid.org/0000-0002-1825-0097` (fictional Josiah Carberry) is a deliberate example.

| Need | Pattern |
|---|---|
| Author | root `author: {"@id": ".../orcid"}`; `Person` with `name`; `affiliation` → `Organization` entity (not a string) |
| Organisation / publisher | `Organization` with `name`, `url`; root `publisher` (SHOULD, an Organization, may be a Person) |
| Contact | `Person`/`Organization` with `contactPoint` → `ContactPoint` (`email`, `contactType`); `Dataset` itself has no `contactPoint` |
| Publication | root `citation: {"@id": "https://doi.org/..."}` MUST be a URL; describe as `ScholarlyArticle`/`CreativeWork` with `name`, `author`, `datePublished`; `creditText` for the citation string |
| Funding | root `funder` → `Organization` (the project may have its own `funder` chain); reference funders from the root too |
| Licence | entity of `@type` `CreativeWork`, `@id` = licence URL, `name`, `description`; for SPDX licences use the SPDX license URI `http://spdx.org/licenses/<id>` (as in SPDX's own RDF; not the `https://…/<id>.html` page), the same URI in Signposting `rel="license"`; a different licence per data entity is allowed; the metadata can have its own: `license` on the descriptor (CC0 for metadata is common) |
| Place | `contentLocation` or `spatialCoverage` → `Place` (GeoNames `@id`); `geo` → `Geometry` blank node `_:g1` with `asWKT` (`<http://www.opengis.net/def/crs/OGC/1.3/CRS84> POINT (150.30 -33.71)`; longitude first) |
| Subject / keywords | `about` → entity (e.g. DBpedia URI); `keywords` is ONE comma-separated string |
| Time | `temporalCoverage: "1950/1975"` |
| Thumbnail | `thumbnail` → image `File` that is in the crate |
| Extra property/values (Exif) | `PropertyValue` entities (`#id`, `name`, `value`) listed from e.g. `exifData` |
| Persistent identifier of the root | `identifier` → `PropertyValue` (`propertyID`, `value`, `url`) and `cite-as` string; or use the DOI URL as the root `@id` |

A contextual entity that is only a value (no name) such as a `Geometry` uses a `_:blank` id; a named one (`Person` without ORCID) uses `#name`. Each contextual entity should be referenced from at least one other entity.
