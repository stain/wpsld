#!/usr/bin/env python3
"""Check Bioschemas types and properties against an RO-Crate JSON-LD context.

Usage: bioschemas_terms.py <bioschemas_types.jsonld> <ro-crate context.json> [bioschemas_profiles_shacl.jsonld]

Inputs (download first):
  https://bioschemas.org/types/bioschemas_types.jsonld
  curl -sL -H 'Accept: application/ld+json' https://w3id.org/ro/crate/1.3/context
  https://bioschemas.org/profiles/bioschemas_profiles_shacl.jsonld  (optional: SHACL of all profiles)

Prints each Bioschemas term the context does not define, with the IRI to map it to
(https://bioschemas.org/terms/<name>, the namespace the RO-Crate 1.3 context uses for Bioschemas terms).
With the SHACL file, also lists, per RELEASE profile, property paths the context does not define.
SHACL paths under discovery.biothings.io are generator artefacts, not term IRIs: they are reported
separately by name and never suggested as mappings. Standard library only.
"""
import collections
import json
import sys

SH = "http://www.w3.org/ns/shacl#"
RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
BIOSCHEMAS = "https://bioschemas.org/terms/"
ARTEFACT = "discovery.biothings.io"


def ids(value):
    values = value if isinstance(value, list) else [value]
    return [v["@id"] if isinstance(v, dict) else v for v in values if v]


def load_context(path):
    context = json.load(open(path, encoding="utf-8"))["@context"]
    if isinstance(context, list):
        context = {k: v for c in context if isinstance(c, dict) for k, v in c.items()}
    iris = set()
    for value in context.values():
        value = value.get("@id") if isinstance(value, dict) else value
        if isinstance(value, str):
            iris.add(value)
    return context, iris


def schema_http(iri):
    return iri.replace("https://schema.org/", "http://schema.org/")


def check_types(types_path, context):
    print("Bioschemas types/properties not in the RO-Crate context:")
    for node in json.load(open(types_path, encoding="utf-8"))["@graph"]:
        name = node["@id"].split(":", 1)[-1]
        if name in context:
            continue
        kind = "class" if "rdfs:Class" in ids(node.get("@type")) else "property"
        domain = ", ".join(d.split(":")[-1] for d in ids(node.get("schema:domainIncludes")))
        print(f"  {kind} {name}: map to {BIOSCHEMAS}{name}" + (f" (on {domain})" if domain else ""))


def check_shacl(shacl_path, context, iris):
    data = json.load(open(shacl_path, encoding="utf-8"))
    nodes = {n["@id"]: n for n in data}

    def paths(value):
        out = set()
        for item in value if isinstance(value, list) else [value]:
            if isinstance(item, dict) and "@list" in item:
                out |= paths(item["@list"])
                continue
            iri = item.get("@id") if isinstance(item, dict) else None
            if not iri or iri == RDF + "nil":
                continue
            if iri.startswith("_:"):  # alternativePath / RDF list
                node = nodes.get(iri, {})
                for key in (SH + "alternativePath", RDF + "first", RDF + "rest"):
                    if key in node:
                        out |= paths(node[key])
            else:
                out.add(schema_http(iri))
        return out

    missing = collections.defaultdict(set)
    artefacts = collections.defaultdict(set)
    for shape in data:
        if SH + "NodeShape" not in shape.get("@type", []) or "RELEASE" not in shape["@id"]:
            continue
        profile = shape["@id"].split("/profiles/")[-1]
        for ref in shape.get(SH + "property", []):
            found = paths(nodes.get(ref["@id"], {}).get(SH + "path", []))
            if not found or found & iris:
                continue
            for iri in found:
                name = iri.rstrip("/").rsplit("/", 1)[-1].split("#")[-1]
                if ARTEFACT in iri:
                    artefacts[name].add(profile)
                elif name not in context:
                    missing[iri].add(profile)
    print("Properties of RELEASE profiles not in the RO-Crate context:")
    for iri, profiles in sorted(missing.items()):
        print(f"  {iri} <- {', '.join(sorted(profiles))}")
    print(f"SHACL paths that only use {ARTEFACT} IRIs (shape bug; the real term is the schema.org or Bioschemas one):")
    for name, profiles in sorted(artefacts.items()):
        status = "in context" if name in context else "NOT in context"
        print(f"  {name} ({status}) <- {', '.join(sorted(profiles))}")


def main(argv):
    if len(argv) < 3:
        sys.exit(__doc__)
    context, iris = load_context(argv[2])
    check_types(argv[1], context)
    if len(argv) > 3:
        check_shacl(argv[3], context, iris)


if __name__ == "__main__":
    main(sys.argv)
