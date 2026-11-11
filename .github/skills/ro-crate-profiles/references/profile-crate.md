# Profile Crate (RO-Crate 1.2/1.3)

Spec section: https://www.researchobject.org/ro-crate/specification/1.3/profiles#profile-crate

A Profile Crate is an RO-Crate whose root is also a `Profile`.

```json
{"@id": "ro-crate-metadata.json", "@type": "CreativeWork",
 "conformsTo": {"@id": "https://w3id.org/ro/crate/1.3"},
 "about": {"@id": "https://example.org/my-profile/1.0"}},
{"@id": "https://example.org/my-profile/1.0", "@type": ["Dataset", "Profile"],
 "name": "My profile", "version": "1.0.0", "identifier": "https://example.org/my-profile/1.0",
 "isProfileOf": [{"@id": "https://w3id.org/ro/crate/1.3"}],
 "hasPart": [{"@id": "index.html"}],
 "hasResource": [{"@id": "#hasSpecification"}]},
{"@id": "index.html", "@type": "File", "name": "Profile description", "encodingFormat": "text/html",
 "about": {"@id": "https://example.org/my-profile/1.0"}},
{"@id": "#hasSpecification", "@type": "ResourceDescriptor",
 "hasRole": {"@id": "http://www.w3.org/ns/dx/prof/role/specification"},
 "hasArtifact": {"@id": "index.html"}}
```

Rules:
- Root `@id` SHOULD be an absolute URI (the profile URI), and the descriptor's `about` updated accordingly; MUST include `Profile` in `@type`; MUST list the human-readable profile description in `hasPart`; SHOULD name `isProfileOf` (minimum RO-Crate spec) and list related files in `hasPart`; MAY use `hasResource`.
- Each resource's role: a `ResourceDescriptor` with `hasRole` (predefined roles at http://www.w3.org/ns/dx/prof/role/: `specification`, `guidance`, `schema`, `constraints`, `validation`, `vocabulary`, `example`, `mapping`; or `http://purl.org/dc/terms/conformsTo` to recommend another profile) and `hasArtifact` pointing to the data entity. The artifact SHOULD have `encodingFormat` (and `conformsTo` for specialised languages such as SHACL `text/turtle`, JSON Schema `application/schema+json`).
- Vocabularies: `DefinedTermSet` (`@id` = namespace, `url` = human page, `hasDefinedTerm`), terms as `DefinedTerm`/`rdfs:Class`/`rdf:Property`; key terms (`termCode`) usable in conforming crates' `@context`. A profile with its own context lists it as a `CreativeWork` with `encodingFormat` `application/ld+json` (must be retrievable, SHOULD be a versioned permalink).
- `ro-crate-preview.html` may serve as the profile description (then it is listed in `hasPart`; the one allowed exception).
- Retrieve a Profile Crate with the same algorithm as any crate (see `ro-crate-retrieve`). Conforming crates need not repeat the profile's shared contextual entities, but may copy them for archiving (`distribution` of a snapshot).
- Multiple profiles: list each in `conformsTo` of the crate; a Profile Crate may suggest others via `hasPart` + a `ResourceDescriptor` with role `dcterms:conformsTo`.
- Updating a profile context: add terms or patch fixes only; do not remove or remap published terms.
