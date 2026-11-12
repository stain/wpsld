#!/usr/bin/env python3
"""Make actions written by rocrate-action-recorder reachable from the root data entity.

Usage: link_actions.py <crate dir | ro-crate-metadata.json> [--dry-run] [--software-url ID=URL ...]

Adds every CreateAction/UpdateAction/ActivateAction that the root does not yet list to the
root's `mentions` (Process Run Crate: SHOULD be mentioned by the root). Optionally sets a `url`
on a SoftwareApplication ONLY when you pass its real URL explicitly with --software-url
(a `url` is required by rocrate-validator; never invent one). Standard library only.
"""
import json, sys
from pathlib import Path

ACTIONS = {"CreateAction", "UpdateAction", "ActivateAction"}


def aslist(v):
    return v if isinstance(v, list) else ([] if v is None else [v])


def main(argv):
    dry = "--dry-run" in argv
    urls = {}
    args, i = [], 0
    while i < len(argv):
        if argv[i] == "--software-url":
            k, _, v = argv[i + 1].partition("=")
            urls[k] = v
            i += 2
        elif argv[i].startswith("--"):
            i += 1
        else:
            args.append(argv[i]); i += 1
    if len(args) != 1:
        print(__doc__); return 2
    p = Path(args[0])
    meta = p / "ro-crate-metadata.json" if p.is_dir() else p
    doc = json.loads(meta.read_text(encoding="utf-8"))
    graph = doc["@graph"]
    emap = {e["@id"]: e for e in graph}
    desc = emap["ro-crate-metadata.json"]
    root = emap[desc["about"]["@id"]]
    mentioned = {m["@id"] for m in aslist(root.get("mentions")) if isinstance(m, dict)}
    added = [e["@id"] for e in graph if ACTIONS & set(aslist(e.get("@type"))) and e["@id"] not in mentioned]
    if added:
        root["mentions"] = aslist(root.get("mentions")) + [{"@id": i} for i in added]
    for sid, url in urls.items():
        if sid not in emap:
            print(f"no entity {sid!r}; not set"); continue
        emap[sid]["url"] = url
        print(f"set url of {sid}")
    print(f"mentions added: {added or 'none'}")
    for e in graph:
        if "SoftwareApplication" in aslist(e.get("@type")) and not e.get("url"):
            print(f"WARNING: {e['@id']} has no url (rocrate-validator wants one): pass --software-url {e['@id']}=<real URL>")
    if not dry:
        meta.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
