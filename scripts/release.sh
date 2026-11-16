#!/bin/sh
# Make a release of wpsld, Maven style: the version committed and tagged
# here, then pushed, rather than a tag made on GitHub. main's rules on
# GitHub take changes only through pull requests, so the commits go on a
# branch, release-<version>, merged by a pull request.
#
#   scripts/release.sh <version> [<next>]
#
# <version>  the release, e.g. 0.7.0
# <next>     the version after it, without -SNAPSHOT (default: <version>
#            with its minor number one up, e.g. 0.8.0)
#
# On main, up to date with origin and with nothing uncommitted: makes the
# branch release-<version> from main, sets version=<version> in
# gradle.properties and the version (and codemeta's dateModified, today) in
# codemeta.json and .zenodo.json, commits "Release <version>", tags it v<version>
# (annotated), then sets version=<next>-SNAPSHOT and commits that; main
# itself is left as it was. Pushes nothing: it prints the push of the
# branch and the tag. The tag starts .github/workflows/release.yml, which
# builds the zips (and checks the tag against gradle.properties) and makes a
# draft release to review and publish on GitHub. Merge the branch's pull
# request with "Create a merge commit", whenever its checks pass, before or
# after publishing: squashing or rebasing would make new commits, leaving
# the tagged one out of main. Don't make the tag or release on GitHub's
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
BRANCH=release-$VERSION
if git rev-parse -q --verify "refs/heads/$BRANCH" > /dev/null ||
        git ls-remote --exit-code -q --heads origin "$BRANCH" > /dev/null; then
    echo "The branch $BRANCH exists already" >&2
    exit 1
fi

set_version() {
    sed "s/^version=.*/version=$1/" "$PROPERTIES" > "$PROPERTIES.tmp"
    mv "$PROPERTIES.tmp" "$PROPERTIES"
    grep -q "^version=$1\$" "$PROPERTIES"
}

# The software's metadata: CodeMeta, and Zenodo's for the DOI it registers
# when the release is published. Each has one "version" key, at the top
set_metadata() {
    for file in codemeta.json .zenodo.json; do
        sed -e "s/^\(  \"version\": \)\"[^\"]*\"/\1\"$1\"/" \
            -e "s/^\(  \"dateModified\": \)\"[^\"]*\"/\1\"$(date -u +%Y-%m-%d)\"/" \
            "$file" > "$file.tmp"
        mv "$file.tmp" "$file"
        grep -q "^  \"version\": \"$1\"" "$file"
    done
}

git switch -q -c "$BRANCH"
set_version "$VERSION"
set_metadata "$VERSION"
git commit -q -m "Release $VERSION" "$PROPERTIES" codemeta.json .zenodo.json
git tag -a "v$VERSION" -m "wpsld $VERSION"
set_version "$NEXT-SNAPSHOT"
git commit -q -m "Version $NEXT-SNAPSHOT, after releasing $VERSION" "$PROPERTIES"
git switch -q main

echo "Tagged v$VERSION on the branch $BRANCH, which then has $NEXT-SNAPSHOT."
echo "To release, push both:"
echo
echo "    git push --atomic origin $BRANCH v$VERSION"
echo
echo "then open a pull request of $BRANCH and merge it with \"Create a merge"
echo "commit\" (not squash or rebase, which leave the tagged commit out of"
echo "main), and review and publish the draft release the workflow makes."
