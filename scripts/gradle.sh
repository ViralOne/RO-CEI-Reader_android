#!/usr/bin/env bash
set -euo pipefail
IMAGE=cei-reader-build
docker build -q --platform linux/amd64 -t "$IMAGE" . >/dev/null
docker run --rm -t --platform linux/amd64 \
  -v "$PWD":/workspace \
  -v cei-gradle-cache:/root/.gradle \
  "$IMAGE" ./gradlew "$@"
