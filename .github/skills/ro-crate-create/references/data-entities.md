# Data entities: patterns (RO-Crate 1.2/1.3)

Spec: https://www.researchobject.org/ro-crate/specification/1.3/data-entities

A data entity is a "directory, file, or other Web resource" the crate contains, with an `@id` that is a relative or absolute URI. Files MUST have `File` in `@type` (directories `Dataset`); a web resource that is not a downloadable file, such as an online tutorial page, may be a part typed `CreativeWork` or its most specific schema.org subtype (`LearningResource`, `WebPage`, `ScholarlyArticle`, ...). A `#`-prefixed `@id` makes it a plain contextual entity (still linkable from `hasPart`). Things that are not content (an event, a person) are contextual entities, never `hasPart`; a crate *about* one uses `mainEntity` (see [web-records.md](web-records.md)).

## Directory with undescribed bulk files
```json
{"@id": "lots_of_little_files/", "@type": "Dataset", "name": "Daily readings",
 "description": "One file per day, named YYYY-MM-DD.csv, hourly temperatures in the cave."}
```
Files spread through the crate that you will not describe individually: a `#ai-files` `Dataset` with a `description`, linked from the root `hasPart`.

## Local file also on the web
```json
{"@id": "survey-2019.csv", "@type": "File", "name": "Survey responses", "encodingFormat": "text/csv",
 "contentUrl": "https://example.com/downloads/survey-2019.csv",
 "subjectOf": {"@id": "https://example.com/reports/annual-survey.html"}}
```
`contentUrl` = direct download (redirects allowed). `url` = landing page; `identifier` = DOI string or `PropertyValue`; `subjectOf`/`mainEntityOfPage` link a describing page.

## Web-based file (not in the crate)
```json
{"@id": "https://example.com/files/report.pdf", "@type": "File", "name": "Report", "contentSize": "310691",
 "encodingFormat": "application/pdf", "sdDatePublished": "2025-04-09T13:09:21+01:00",
 "localPath": "docs/report.pdf"}
```
The `@id` must download directly (not a DOI/landing page/viewer). `localPath` says where it goes if the crate is made attached. In a detached crate all data entities are like this.

## Directory on the web
Prefer a complete `hasPart` list, another RO-Crate (see retrieval), or an archive:
```json
{"@id": "data/", "@type": "Dataset", "distribution": {"@id": "https://example.com/data.zip"}},
{"@id": "https://example.com/data.zip", "@type": "DataDownload", "contentSize": "82818928",
 "encodingFormat": ["application/zip", {"@id": "https://www.nationalarchives.gov.uk/PRONOM/x-fmt/263"}]}
```

## File formats
`encodingFormat` is an IANA media type string, optionally plus an entity: PRONOM URL with `@type` `["WebPage", "Standard"]`, or a `WebPageElement` for an undocumented format, or a local Markdown `File` describing it. A format profile (e.g. CSV dialect) uses `conformsTo` to a `["CreativeWork", "Profile"]` entity.

## Paths
`/` separators; percent-encode: `Results%20and%20Diagrams/almost-50%25.png`; international characters may stay UTF-8; never `../`.

## Not data entities
`ro-crate-metadata.json` (descriptor) and `ro-crate-preview.html`/`ro-crate-preview_files/` are not payload and must not be in `hasPart`. If the preview needs metadata: `{"@id": "ro-crate-preview.html", "@type": "CreativeWork", "about": {"@id": "./"}}`.
