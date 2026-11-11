#!/usr/bin/env python3
"""Offline structural lint for an RO-Crate (versions 1.0-1.3). Standard library only.

Usage: lint_crate.py <crate dir | ro-crate-metadata.json | crate.zip> [--json]
Exit status: 0 no errors, 1 errors found, 2 not readable.

Complements `rocrate-validator` (which needs network access and does not check everything
here). It checks structure and the rules every released version shares; it does not
check profiles or the semantic quality of descriptions.
"""
import json, re, sys
from pathlib import Path
from urllib.parse import unquote

RELEASED = ("1.0", "1.1", "1.2", "1.3")
DATE = re.compile(r"^\d{4}(-\d{2}(-\d{2}(T[\d:.]+(Z|[+-]\d{2}:?\d{2})?)?)?)?$")
ABS = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*:")


def aslist(v):
    return v if isinstance(v, list) else ([] if v is None else [v])


def refs(entity):
    """Yield (property, @id) for every {"@id": ...} value."""
    for k, v in entity.items():
        if k.startswith("@"):
            continue
        for x in aslist(v):
            if isinstance(x, dict) and isinstance(x.get("@id"), str):
                yield k, x["@id"]


def lint(path):
    out = []  # (level, message)
    add = lambda lvl, msg: out.append((lvl, msg))
    path = Path(path)
    exists = None  # exists(rel) -> bool: is a crate-relative path present?
    if path.is_file() and path.suffix == ".zip":
        import zipfile
        try:
            zf = zipfile.ZipFile(path)
        except zipfile.BadZipFile as e:
            return [("ERROR", f"not a readable zip: {e}")], None
        names = zf.namelist()
        member = next((n for n in sorted(names, key=len)
                       if re.fullmatch(r"([^/]+/)?ro-crate-metadata\.json(ld)?", n)), None)
        if member is None:
            return [("ERROR", "no ro-crate-metadata.json at the zip root or in its single top-level folder")], None
        prefix = member[: -len("ro-crate-metadata.json")] if member.endswith(".json") else member[: -len("ro-crate-metadata.jsonld")]
        text = zf.read(member).decode("utf-8")
        attached = True
        exists = lambda rel: (prefix + rel) in names or any(n.startswith(prefix + rel.rstrip("/") + "/") for n in names)
        meta = Path(member)
    else:
        base = path.parent if path.is_file() else path
        meta = path if path.is_file() else next(
            (base / n for n in ("ro-crate-metadata.json", "ro-crate-metadata.jsonld") if (base / n).exists()), None)
        if meta is None and path.is_dir():  # detached crate: <prefix>-ro-crate-metadata.json
            cands = sorted(path.glob("*-ro-crate-metadata.json"))
            meta = cands[0] if len(cands) == 1 else None
        if meta is None:
            return [("ERROR", "no ro-crate-metadata.json (or .jsonld) found")], None
        attached = path.is_dir() or meta.name in ("ro-crate-metadata.json", "ro-crate-metadata.jsonld")
        text = meta.read_text(encoding="utf-8")
        exists = lambda rel: (base / rel).exists()
    if re.search(r"\d-DRAFT", text):
        add("ERROR", "uses a -DRAFT (unreleased) specification, context or profile URI")
    try:
        doc = json.loads(text)
    except ValueError as e:
        return [("ERROR", f"invalid JSON: {e}")], None
    ctx = doc.get("@context")
    ctxs = [c for c in aslist(ctx) if isinstance(c, str)]
    if not any(re.match(r"https://w3id\.org/ro/crate/1\.\d/context$", c) for c in ctxs):
        add("ERROR", "@context does not reference https://w3id.org/ro/crate/1.x/context")
    graph = doc.get("@graph")
    if not isinstance(graph, list):
        return out + [("ERROR", "@graph is missing or not an array (not flattened?)")], None
    emap, seen = {}, set()
    for i, e in enumerate(graph):
        if not isinstance(e, dict) or not isinstance(e.get("@id"), str):
            add("ERROR", f"@graph[{i}] has no string @id")
            continue
        if "@type" not in e:
            add("ERROR", f"{e['@id']}: no @type")
        if e["@id"] in seen:
            add("ERROR", f"duplicate @id {e['@id']}")
        seen.add(e["@id"])
        emap[e["@id"]] = e
    desc = emap.get("ro-crate-metadata.json") or emap.get("ro-crate-metadata.jsonld")
    if desc is None:
        return out + [("ERROR", "no metadata descriptor entity (@id ro-crate-metadata.json)")], None
    if "jsonld" in desc["@id"]:
        add("WARN", "legacy descriptor ro-crate-metadata.jsonld (RO-Crate 1.0)")
    ver = None
    conf = [x.get("@id") for x in aslist(desc.get("conformsTo")) if isinstance(x, dict)]
    specs = [c for c in conf if c and c.startswith("https://w3id.org/ro/crate/")]
    if not specs:
        add("ERROR", "descriptor conformsTo has no https://w3id.org/ro/crate/<version> value")
    else:
        m = re.match(r"https://w3id\.org/ro/crate/(\d+\.\d+)$", specs[0])
        ver = m.group(1) if m else None
        if ver not in RELEASED:
            add("ERROR", f"descriptor conformsTo {specs[0]!r} is not a released version {RELEASED}")
        if len(conf) > 1:
            add("WARN", "descriptor conformsTo has several values; profiles belong on the root (1.2+)")
    about = desc.get("about")
    root = emap.get(about.get("@id")) if isinstance(about, dict) else None
    if root is None:
        return out + [("ERROR", "descriptor about does not reference an entity in the graph")], ver
    if "Dataset" not in aslist(root.get("@type")):
        add("ERROR", "root data entity @type does not include Dataset")
    for p in ("name", "description", "license"):
        if not root.get(p):
            add("ERROR", f"root data entity lacks {p}")
    dp = root.get("datePublished")
    if not dp:
        add("ERROR", "root data entity lacks datePublished")
    elif not isinstance(dp, str) or not DATE.match(dp):
        add("ERROR", f"root datePublished {dp!r} is not a single ISO 8601 string")
    rid = root["@id"]
    if rid != "./" and not ABS.match(rid):
        add("WARN", f"root @id {rid!r} is neither './' nor an absolute URI")
    # leftover TODO: placeholders (see the "Do not guess" rule in the skills)
    def todos(v, where):
        if isinstance(v, str) and v.strip().upper().startswith("TODO"):
            add("WARN", f"{where}: placeholder value {v[:40]!r} must be replaced before publishing")
        elif isinstance(v, list):
            for x in v:
                todos(x, where)
        elif isinstance(v, dict):
            for k, x in v.items():
                todos(x, where)
    for e in emap.values():
        for k, v in e.items():
            if not k.startswith("@"):
                todos(v, f"{e['@id']} {k}")
    # reachability and dangling references
    reach, todo = {desc["@id"], rid}, [rid, desc["@id"]]
    while todo:
        for _, r in refs(emap[todo.pop()]):
            if r in emap and r not in reach:
                reach.add(r); todo.append(r)
    for i in emap:
        if i not in reach and i != "ro-crate-preview.html":
            add("WARN", f"{i}: not reachable from the root data entity")
    for e in emap.values():
        for prop, r in refs(e):
            if prop == "hasPart" and r not in emap:
                add("WARN", f"{e['@id']} hasPart {r}: no entity describes it")
                if attached and not ABS.match(r) and not r.startswith(("#", "_:")) and not exists(unquote(r)):
                    add("ERROR", f"{r}: listed in hasPart but not present in the crate")
            if prop == "hasPart" and r.startswith("ro-crate-preview"):
                add("ERROR", f"{e['@id']} lists {r} in hasPart (the preview is not payload)")
    # data entities
    for i, e in emap.items():
        types = aslist(e.get("@type"))
        if not ({"File", "Dataset"} & set(types)) or i.startswith("#") or i.startswith("_:") or ABS.match(i):
            continue
        if "../" in i:
            add("ERROR", f"{i}: relative @id climbs out of the crate root")
        if i == rid:
            continue
        if "Dataset" in types and not i.endswith("/"):
            add("WARN", f"{i}: directory @id should end with '/'")
        if attached:
            if not exists(unquote(i)):
                add("ERROR", f"{i}: data entity not present in the crate at that path")
    return out, ver


def main(argv):
    args = [a for a in argv if not a.startswith("--")]
    if len(args) != 1:
        print(__doc__); return 2
    results, ver = lint(args[0])
    errors = sum(1 for l, _ in results if l == "ERROR")
    if "--json" in argv:
        print(json.dumps({"version": ver, "errors": errors, "findings": [{"level": l, "message": m} for l, m in results]}, indent=2))
    else:
        for l, m in results:
            print(f"{l}: {m}")
        print(f"declared version: {ver or 'unknown'}; {errors} error(s), {len(results) - errors} warning(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
