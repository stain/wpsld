#!/bin/sh
# Bundle the SPDX License List with wpsld, as JSON-LD, pinned to a release.
#
#   scripts/update-spdx-licenses.sh <tag>
#
# <tag>   release of https://github.com/spdx/license-list-data, e.g. v3.29.0
#
# Downloads that release's jsonld/licenses.jsonld (about 21 MB, with every
# licence's text) and keeps, for each listed licence, only what choosing one
# needs: its @id (e.g. http://spdx.org/licenses/CC-BY-4.0), name, whether it
# is OSI approved, FSF libre or deprecated, and its rdfs:seeAlso links. The
# licence exceptions and the link checks (spdx:CrossRef) are left out.
# Writes lib/src/main/resources/spdx-licenses/licenses.jsonld, source.json
# (the release, its address and SHA-256) and an attribution README.md.
# Review the changes with git diff before committing, and update the
# version in ui/src/notices/THIRD-PARTY-NOTICES.txt. Needs curl and jq.
#
# Not part of the build: run it on purpose to update the bundled list.

set -eu

REPO=spdx/license-list-data
DIR=$(dirname "$0")/../lib/src/main/resources/spdx-licenses

if [ $# -lt 1 ]; then
    sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
fi
TAG=$1
VERSION=${TAG#v}
SOURCE=https://raw.githubusercontent.com/$REPO/$TAG/jsonld/licenses.jsonld

TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
echo "Downloading $SOURCE"
curl -fsSL -o "$TEMP/licenses.jsonld" "$SOURCE"
SHA256=$(sha256sum "$TEMP/licenses.jsonld" | cut -d' ' -f1)

mkdir -p "$DIR"
jq '{"@context": .["@context"],
     "@graph": [.["@graph"][]
        | select(.["@type"] == "spdx:ListedLicense")
        | {"@id", "@type", "spdx:name", "spdx:isOsiApproved", "spdx:isFsfLibre",
           "spdx:isDeprecatedLicenseId", "spdx:deprecatedVersion", "rdfs:seeAlso"}
        | with_entries(select(.value != null))]
        | sort_by(.["@id"] | ascii_downcase)}' \
    "$TEMP/licenses.jsonld" > "$DIR/licenses.jsonld"
COUNT=$(jq '.["@graph"] | length' "$DIR/licenses.jsonld")
[ "$COUNT" -gt 0 ] || { echo "No licences in $SOURCE" >&2; exit 1; }

jq -n --arg version "$VERSION" --arg tag "$TAG" --arg source "$SOURCE" --arg sha256 "$SHA256" \
    --argjson licences "$COUNT" \
    '{version: $version, tag: $tag, source: $source, sha256: $sha256, licences: $licences}' \
    > "$DIR/source.json"

cat > "$DIR/README.md" <<EOF
# SPDX License List $VERSION

\`licenses.jsonld\` is from the [SPDX License List](https://spdx.org/licenses/)
$VERSION, release [$TAG](https://github.com/$REPO/releases/tag/$TAG) of
[spdx/license-list-data](https://github.com/$REPO), file
\`jsonld/licenses.jsonld\` (SHA-256 \`$SHA256\`).

Changed: only the $COUNT listed licences are kept, each with its \`@id\`,
\`@type\`, \`spdx:name\`, \`spdx:isOsiApproved\`, \`spdx:isFsfLibre\`,
\`spdx:isDeprecatedLicenseId\`, \`spdx:deprecatedVersion\` and \`rdfs:seeAlso\`,
sorted by \`@id\`. The licence texts and templates, the licence exceptions
and the link checks (\`spdx:CrossRef\`) are left out.

The SPDX License List is licensed under the
[Creative Commons Attribution 3.0 Unported](https://creativecommons.org/licenses/by/3.0/)
licence (CC-BY-3.0), as license-list-data's
[accessingLicenses.md](https://github.com/$REPO/blob/main/accessingLicenses.md)
gives it. SPDX is a registered trademark of The Linux Foundation.

Made by \`scripts/update-spdx-licenses.sh $TAG\`.
EOF

echo "Wrote $COUNT licences of SPDX License List $VERSION to $DIR"
