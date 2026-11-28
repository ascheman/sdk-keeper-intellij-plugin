#!/usr/bin/env bash
# Publishes the version in gradle.properties to the JetBrains Marketplace and tags it.
#   scripts/publish.sh            build, test, verify, upload, tag v<version> and push the tag
#   scripts/publish.sh --dry-run  everything up to the upload, without uploading or tagging
# The Marketplace token comes from the macOS Keychain, stored once with:
#   security add-generic-password -U -a "$USER" -s sdk-keeper-marketplace -w
set -euo pipefail

cd "$(dirname "$0")/.."
dry_run=false
[[ "${1:-}" == "--dry-run" ]] && dry_run=true

fail() { echo "publish: $*" >&2; exit 1; }

version=$(sed -n 's/^pluginVersion *= *//p' gradle.properties)
[[ -n "$version" ]] || fail "no pluginVersion in gradle.properties"
[[ "$version" != *-SNAPSHOT ]] || fail "version $version is a snapshot; set the release version first"
grep -q "^## \[$version\]" CHANGELOG.md || fail "CHANGELOG.md has no section for $version"

if ! $dry_run; then
  [[ "$(git symbolic-ref --short HEAD)" == main ]] || fail "not on main"
  [[ -z "$(git status --porcelain)" ]] || fail "working tree not clean"
  git fetch --quiet origin
  [[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || fail "main differs from origin/main"
  ! git rev-parse --quiet --verify "refs/tags/v$version" >/dev/null || fail "tag v$version exists already"
fi

PUBLISH_TOKEN=$(security find-generic-password -a "$USER" -s sdk-keeper-marketplace -w 2>/dev/null) \
  || fail "no Marketplace token in the Keychain (service sdk-keeper-marketplace)"
export PUBLISH_TOKEN

tasks=(clean test verifyPlugin)
$dry_run || tasks+=(publishPlugin)
echo "publish: $version: ${tasks[*]}"
./gradlew --console=plain "${tasks[@]}"

if $dry_run; then
  echo "publish: dry run done; $(ls build/distributions/*.zip) not uploaded"
  exit 0
fi

git tag -a "v$version" -m "SDK Keeper $version"
git push --quiet origin "v$version"
echo "publish: $version uploaded and tagged v$version; it appears once the Marketplace has checked it"
