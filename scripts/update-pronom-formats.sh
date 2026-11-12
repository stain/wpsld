#!/bin/sh
# Bundle the file formats of PRONOM with wpsld, pinned to a DROID signature
# file version.
#
#   scripts/update-pronom-formats.sh <version>|latest
#
# <version>   version of The National Archives' DROID signature file, e.g.
#             125, or latest (as https://pronom.nationalarchives.gov.uk/signatures.json
#             names it)
#
# Downloads that DROID_SignatureFile_V<version>.xml (about 3.7 MB, with the
# byte signatures for identifying files) and keeps, for each format, only
# what choosing one needs: its PUID (e.g. fmt/19), name, version, media
# types and file extensions. Writes lib/src/main/resources/pronom/formats.json,
# source.json (the version, its address and SHA-256) and an attribution
# README.md. Review the changes with git diff before committing, and update
# the version in ui/src/notices/THIRD-PARTY-NOTICES.txt. Needs curl and
# python3.
#
# Not part of the build: run it on purpose to update the bundled formats.

set -eu

SITE=https://pronom.nationalarchives.gov.uk
DIR=$(dirname "$0")/../lib/src/main/resources/pronom

if [ $# -lt 1 ]; then
    sed -n '2,21p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
fi
VERSION=$1
if [ "$VERSION" = latest ]; then
    VERSION=$(curl -fsSL "$SITE/signatures.json" |
        python3 -I -c 'import json, sys; print(json.load(sys.stdin)["latest_signature"]["version"])')
fi
case "$VERSION" in
    *[!0-9]*|'') echo "Not a signature file version: $VERSION" >&2; exit 1 ;;
esac
SOURCE=$SITE/signatures/DROID_SignatureFile_V$VERSION.xml

TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
echo "Downloading $SOURCE"
curl -fsSL -o "$TEMP/signatures.xml" "$SOURCE"
SHA256=$(sha256sum "$TEMP/signatures.xml" | cut -d' ' -f1)

mkdir -p "$DIR"
python3 -I - "$TEMP/signatures.xml" "$DIR" "$VERSION" "$SOURCE" "$SHA256" <<'EOF'
import json, re, sys, xml.etree.ElementTree as ET

signatures, folder, version, source, sha256 = sys.argv[1:]
root = ET.parse(signatures).getroot()
ns = {"d": root.tag.split("}")[0][1:]}
formats = []
for f in root.iterfind(".//d:FileFormat", ns):
    puid = f.get("PUID", "").strip()
    name = f.get("Name", "").strip()
    if not puid or not name:
        continue
    entry = {"puid": puid, "name": name}
    if f.get("Version", "").strip():
        entry["version"] = f.get("Version").strip()
    media = [m.strip() for m in f.get("MIMEType", "").split(",") if m.strip()]
    if media:
        entry["mediaTypes"] = media
    extensions = [e.text.strip() for e in f.iterfind("d:Extension", ns) if e.text and e.text.strip()]
    if extensions:
        entry["extensions"] = extensions
    formats.append(entry)

def order(entry):
    # fmt/2 before fmt/10, x-fmt after fmt
    prefix, _, number = entry["puid"].partition("/")
    return (prefix, int(number) if number.isdigit() else 0, entry["puid"])

formats.sort(key=order)
with open(f"{folder}/formats.json", "w", encoding="utf-8") as out:
    out.write('{"version": %s, "dateCreated": %s, "formats": [\n' % (
        json.dumps(version), json.dumps(root.get("DateCreated", ""))))
    out.write(",\n".join(json.dumps(e, ensure_ascii=False) for e in formats))
    out.write("\n]}\n")
with open(f"{folder}/source.json", "w", encoding="utf-8") as out:
    json.dump({"version": version, "dateCreated": root.get("DateCreated", ""), "source": source,
               "sha256": sha256, "formats": len(formats)}, out, indent=2)
    out.write("\n")
print(len(formats))
EOF
COUNT=$(python3 -I -c 'import json, sys; print(json.load(open(sys.argv[1]))["formats"])' "$DIR/source.json")

cat > "$DIR/README.md" <<EOF
# PRONOM file formats, DROID signature file V$VERSION

\`formats.json\` is from [PRONOM](https://pronom.nationalarchives.gov.uk/),
The National Archives' registry of file formats: its DROID signature file
V$VERSION, <$SOURCE> (SHA-256 \`$SHA256\`).

Changed: only the $COUNT file formats are kept, each with its PUID, name,
version, media types and file extensions, as JSON. The byte signatures for
identifying files, and their priorities, are left out.

Contains public sector information licensed under the
[Open Government Licence v3.0](https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/).
© Crown copyright, The National Archives.

Made by \`scripts/update-pronom-formats.sh $VERSION\`.
EOF

echo "Wrote $COUNT formats of PRONOM signature file V$VERSION to $DIR"
