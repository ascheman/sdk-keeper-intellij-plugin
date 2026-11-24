#!/usr/bin/env bash
# Takes the Marketplace screenshots and a recording in a Linux container; results land in build/marketplace-media.
# Gradle and IDE downloads are cached in Docker volumes, so only the first run downloads them.
set -euo pipefail

cd "$(dirname "$0")/.."
image=sdk-keeper-marketplace-media
out="$PWD/build/marketplace-media"
mkdir -p "$out"

docker build --quiet --tag "$image" docker/marketplace-media >/dev/null
docker run --rm \
  --shm-size=1g \
  --volume "$PWD:/src:ro" \
  --volume sdk-keeper-media-gradle:/root/.gradle \
  --volume sdk-keeper-media-ide:/work/out \
  --volume "$out:/media" \
  "$image" "$@"

echo "Media in $out"
