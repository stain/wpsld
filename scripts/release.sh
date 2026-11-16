#!/bin/sh
# Make a release of wpsld, Maven style: the version committed and tagged
# here, then pushed, rather than a tag made on GitHub.
#
#   scripts/release.sh <version> [<next>]
#
# <version>  the release, e.g. 0.7.0
# <next>     the version after it, without -SNAPSHOT (default: <version>
#            with its minor number one up, e.g. 0.8.0)
#
# On main, up to date with origin and with nothing uncommitted: sets
# version=<version> in gradle.properties, commits "Release <version>",
# tags it v<version> (annotated), then sets version=<next>-SNAPSHOT and
# commits that. Pushes nothing: it prints the push, which starts
# .github/workflows/release.yml on the tag, which builds the zips (and
# checks the tag against gradle.properties) and makes a draft release to
# review and publish on GitHub. Don't make the tag or release on GitHub's
# website: the workflow makes the release, with the tag's notes.

set -eu

if [ $# -lt 1 ]; then
    sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
fi
check() {
    case "$1" in
        *[!0-9.]* | .* | *. | *..* | "")
            echo "Not a version like 0.7.0: $1" >&2
            exit 1 ;;
    esac
}
VERSION=$1
check "$VERSION"
if [ $# -ge 2 ]; then
    NEXT=$2
else
    NEXT=$(echo "$VERSION" | awk -F. '{ printf "%d.%d.0", $1, $2 + 1 }')
fi
check "$NEXT"

cd "$(dirname "$0")/.."
PROPERTIES=gradle.properties

if [ "$(git rev-parse --abbrev-ref HEAD)" != main ]; then
    echo "Release from main, not $(git rev-parse --abbrev-ref HEAD)" >&2
    exit 1
fi
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "Commit or stash the changes first" >&2
    exit 1
fi
git fetch -q origin main --tags
if [ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]; then
    echo "main differs from origin/main: pull or push first" >&2
    exit 1
fi
if git rev-parse -q --verify "refs/tags/v$VERSION" > /dev/null; then
    echo "The tag v$VERSION exists already" >&2
    exit 1
fi

set_version() {
    sed "s/^version=.*/version=$1/" "$PROPERTIES" > "$PROPERTIES.tmp"
    mv "$PROPERTIES.tmp" "$PROPERTIES"
    grep -q "^version=$1\$" "$PROPERTIES"
}

set_version "$VERSION"
git commit -q -m "Release $VERSION" "$PROPERTIES"
git tag -a "v$VERSION" -m "wpsld $VERSION"
set_version "$NEXT-SNAPSHOT"
git commit -q -m "Version $NEXT-SNAPSHOT, after releasing $VERSION" "$PROPERTIES"

echo "Tagged v$VERSION; main is now at $NEXT-SNAPSHOT. To release, push both:"
echo
echo "    git push --atomic origin main v$VERSION"
echo
echo "then review and publish the draft release the workflow makes."
