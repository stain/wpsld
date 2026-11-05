#!/bin/sh
# Bundle RO-Crate MASP profile crates with wpsld, pinned to an upstream commit.
#
#   scripts/update-masp-profiles.sh <commit> [profile...]
#
# <commit>   commit (or branch/tag) of
#            https://github.com/Language-Research-Technology/ro-crate-masp,
#            resolved to its full SHA, as the profiles have no releases yet
# profile    folder under profiles/ upstream, default: ro-crate (the core
#            RO-Crate profile)
#
# Writes lib/src/main/resources/masp-profiles/<profile>.json and the index
# lib/src/main/resources/masp-profiles.json, recording for each profile its
# source, commit, SHA-256, name, version and identifiers (from its conformance
# indicators, or its root @id if absolute), and an attribution README.
# Review the changes with git diff before committing. Needs curl and jq.
#
# Not part of the build: run it on purpose to update the bundled profiles.

set -eu

REPO=Language-Research-Technology/ro-crate-masp
RESOURCES=$(dirname "$0")/../lib/src/main/resources
DIR=$RESOURCES/masp-profiles
INDEX=$RESOURCES/masp-profiles.json

if [ $# -lt 1 ]; then
    sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
fi
REF=$1
shift
[ $# -gt 0 ] || set -- ro-crate

COMMIT=$(curl -fsSL "https://api.github.com/repos/$REPO/commits/$REF" | jq -r .sha)
case "$COMMIT" in
    [0-9a-f]*) ;;
    *) echo "Can't resolve $REF in $REPO" >&2; exit 1 ;;
esac
echo "Using $REPO commit $COMMIT"

mkdir -p "$DIR"
ENTRIES=$(mktemp)
trap 'rm -f "$ENTRIES"' EXIT

for PROFILE in "$@"; do
    UPSTREAM=profiles/$PROFILE/profile-crate/ro-crate-metadata.json
    URL=https://raw.githubusercontent.com/$REPO/$COMMIT/$UPSTREAM
    FILE=masp-profiles/$PROFILE.json
    echo "  $UPSTREAM"
    curl -fsSL -o "$RESOURCES/$FILE" "$URL"
    jq -e '."@graph" | type == "array"' "$RESOURCES/$FILE" > /dev/null \
        || { echo "$URL is not a flattened RO-Crate" >&2; exit 1; }
    SHA256=$(sha256sum "$RESOURCES/$FILE" | cut -d' ' -f1)
    # Name, version and identifiers, as ProfileReader reads them
    jq --arg file "$FILE" --arg url "$URL" --arg commit "$COMMIT" --arg upstream "$UPSTREAM" \
        --arg sha256 "$SHA256" --arg repo "https://github.com/$REPO" '
        def ids: if type == "array" then map(."@id") elif type == "object" then [."@id"] else [] end;
        ."@graph" as $g
        | ($g[] | select(."@id" == "ro-crate-metadata.json") | .about."@id") as $rootId
        | ($g[] | select(."@id" == $rootId)) as $root
        | ([$root.hasResource | ids[] as $r | $g[] | select(."@id" == $r)
            | select((.hasRole | ids) | index("https://w3id.org/ro/conformance-indicators"))
            | .hasArtifact | ids[] as $a | $g[] | select(."@id" == $a) | .itemListElement | ids[]]) as $indicators
        | {
            "identifiers": ((if ($rootId | test("^[A-Za-z][A-Za-z0-9+.-]*:")) then [$rootId] else [] end)
                + $indicators | unique),
            "X-Classpath": $file,
            "name": $root.name,
            "version": $root.version,
            "repository": $repo,
            "commit": $commit,
            "path": $upstream,
            "source": $url,
            "sha256": $sha256
          }' "$RESOURCES/$FILE" >> "$ENTRIES"
done

# Keep entries of other profiles already bundled, replacing those updated
if [ -f "$INDEX" ]; then
    jq --slurpfile new "$ENTRIES" '[.[] | select(."X-Classpath" as $f | ($new | map(."X-Classpath") | index($f) | not))]
        + $new | sort_by(."X-Classpath")' "$INDEX" > "$INDEX.new"
else
    jq -s 'sort_by(."X-Classpath")' "$ENTRIES" > "$INDEX.new"
fi
mv "$INDEX.new" "$INDEX"

cat > "$DIR/README.md" <<EOF
# Bundled RO-Crate MASP profiles

Profile crates from [RO-Crate MASP](https://github.com/$REPO), licensed
Apache-2.0, copied by \`scripts/update-masp-profiles.sh\` and listed with their
source, commit and SHA-256 in \`../masp-profiles.json\`:

$(jq -r '.[] | "- `\(."X-Classpath" | sub("masp-profiles/"; ""))`: \(.name) \(.version // ""), from `\(.path)` at commit [`\(.commit[0:7])`](\(.repository)/tree/\(.commit))"' "$INDEX")

The upstream profiles have no releases yet, so they are pinned to a commit.
To update, run the script with a newer commit and review the changes.
EOF

echo "Wrote $INDEX"
