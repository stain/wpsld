# Signposting and content negotiation for a web application

For a site that serves one record per page (a catalogue, registry or repository) and wants machines to find each record's RO-Crate. FAIR Signposting: https://signposting.org/FAIR/ ; spec: https://www.researchobject.org/ro-crate/specification/1.3/data-entities#retrieving-an-ro-crate and `/root-data-entity#resolvable-persistent-identifiers-and-citation-text`. For the crate itself see `ro-crate-create` (references/web-records.md there).

## Links on each record page
Send the same set twice: as an HTTP `Link` header and as `<link>` elements in the HTML `<head>` (some clients read only one of them).

| rel | Target | Notes |
|---|---|---|
| `describedby` | the crate's metadata document | `type="application/ld+json"`, `profile="https://w3id.org/ro/crate"`; other metadata (schema.org JSON-LD, a JSON API) can be further `describedby` links with their own `type`/`profile` |
| `item` | a downloadable archive or file of the record's content | `type` of the download; with `profile="https://w3id.org/ro/crate"` only if the archive is a crate. Not for HTML pages: those are not items |
| `cite-as` | the persistent identifier of what the page is about | only a real PID; normalise DOIs to `https://doi.org/10.…` (from `doi:`, bare `10.…`, `http://dx.doi.org/`); skip values that are not a DOI or an http(s) URI |
| `author` | ORCID URLs of the authors | |
| `license` | the licence URI | the same URI as the crate's `license` (e.g. `http://spdx.org/licenses/CC-BY-4.0`) |
| `type` | schema.org type of the page's subject, plus `https://schema.org/AboutPage` | |

```
Link: <https://example.org/materials/42/ro-crate-metadata.json> ; rel="describedby" ;
      type="application/ld+json" ; profile="https://w3id.org/ro/crate" ,
      <https://doi.org/10.1234/example> ; rel="cite-as" ,
      <http://spdx.org/licenses/CC-BY-4.0> ; rel="license"
```
```html
<link rel="describedby" type="application/ld+json" profile="https://w3id.org/ro/crate"
      href="https://example.org/materials/42/ro-crate-metadata.json">
```

**`cite-as` in the header versus the crate.** A record about something with its own DOI (a training material on Zenodo, a dataset) may signpost that DOI, but the crate's root `cite-as` MUST lead to the crate itself (spec, root data entity identifier). If the DOI resolves elsewhere, put it as `identifier` on the described entity, not as root `cite-as`.

## Serving the crate
- A stable URL per record, e.g. `/<type>/<id>/ro-crate-metadata.json`, served as `application/ld+json` (the profile parameter as in SKILL.md).
- Content negotiation on the page URL: `Accept: application/ld+json; profile="https://w3id.org/ro/crate"` returns the crate. Keep plain `application/ld+json` returning what it returned before (often schema.org JSON-LD), so existing harvesters are not broken.
- Apply the same access rules as the page: no crate (and no links) for hidden or unpublished records.

## Pitfalls (seen in practice)
- **Framework `Link` headers.** Web frameworks add their own links to the same header (Rails: `rel=preload` asset hints with unquoted `integrity=sha256-…=` values and a valueless `nopush`). Strict parsers then reject the **whole** header, signposts included: `httplink` 0.2.0, used by the Python `signposting` 0.9.9 library, raises "Bad link" on the unquoted base64 `=`. Quote those values (or drop them) on pages with signposting.
- **Order.** Put the signposting links first in the header. Some clients look only at the first link's `rel`: the Signposting Sniffing browser extension 1.3 ignored any header that started with a preload link.
- **Bot protection.** CDNs and challenge pages (e.g. Cloudflare) can answer non-browser clients with 403 or an HTML challenge, which hides the `Link` header and the crate from every machine client. Check with `curl -I` from outside; exempting the crate URLs and `HEAD` requests is a deployment decision for the site's operators.
- **In-page navigation.** Single-page apps and Turbolinks/Turbo load pages without a full request, so browser extensions see no new `Link` header; test with a full reload or `curl`.

## Testing
- Use an independent client against a running instance, not only the app's own unit tests: the Python `signposting` library (`pip install signposting`; `signposting <url>`, or `signposting.find_signposting_http(url)` in pytest) checks the header and HTML links, then follow `describedby` and validate what it returns with `rocrate-validator` (`ro-crate-validator` skill).
- Assert that the header and the HTML links agree, that content negotiation returns the crate only for the RO-Crate profile, and that DOIs come out normalised.
- Such tests need a live server, so they often run as an opt-in suite. Ask the project's maintainers before adding a CI job for them.
