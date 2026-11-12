#!/usr/bin/env python3
"""List the schema.org subtypes of CreativeWork (or another class) as a Markdown tree.

Usage: creativework_types.py <schemaorg-current-http.jsonld> <ro-crate context.json> [Class]

Use the schema.org release the RO-Crate context is fixed to (its schemaVersion; 1.3: 30.0) and the
http namespace, as the context does:
https://raw.githubusercontent.com/schemaorg/schemaorg/main/data/releases/30.0/schemaorg-all-http.jsonld
(the "all" file includes attic terms; latest release:
https://schema.org/version/latest/schemaorg-all-http.jsonld ; see
https://schema.org/docs/developers.html#defs). Context:
curl -sL -H 'Accept: application/ld+json' https://w3id.org/ro/crate/1.3/context
Marks types that are only in schema.org's pending area, superseded (with the replacement) or in the attic,
and types the RO-Crate context does not define.
Standard library only.
"""
import collections
import json
import sys


def ids(value):
    values = value if isinstance(value, list) else [value]
    return [v["@id"] if isinstance(v, dict) else v for v in values if v]


def main(vocab_path, context_path, top="CreativeWork"):
    graph = json.load(open(vocab_path, encoding="utf-8"))["@graph"]
    context = json.load(open(context_path, encoding="utf-8"))["@context"]
    if isinstance(context, list):
        context = {k: v for c in context if isinstance(c, dict) for k, v in c.items()}
    subclasses = collections.defaultdict(set)
    parts = {}
    superseded = {}
    for node in graph:
        if "rdfs:Class" not in ids(node.get("@type")):
            continue
        name = node["@id"].replace("schema:", "")
        parts[name] = ids(node.get("schema:isPartOf"))
        superseded[name] = [i.replace("schema:", "") for i in ids(node.get("schema:supersededBy"))]
        for parent in ids(node.get("rdfs:subClassOf")):
            subclasses[parent.replace("schema:", "")].add(name)

    seen = set()

    def walk(cls, depth):
        for sub in sorted(subclasses[cls]):
            if sub in seen:  # multiple inheritance: list once, under its first parent
                continue
            seen.add(sub)
            notes = []
            if any("pending" in p for p in parts.get(sub, [])):
                notes.append("pending")
            if any("attic" in p for p in parts.get(sub, [])):
                notes.append("attic")
            if superseded.get(sub):
                notes.append("superseded by " + ", ".join(f"`{s}`" for s in superseded[sub]))
            if sub not in context:
                notes.append("not in RO-Crate context")
            yield "  " * depth + f"- `{sub}`" + (f" ({', '.join(notes)})" if notes else "")
            yield from walk(sub, depth + 1)

    lines = list(walk(top, 0))
    print(f"{len(lines)} subtypes of {top}")
    print("\n".join(lines))


if __name__ == "__main__":
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    main(*sys.argv[1:4])
