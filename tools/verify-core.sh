#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc app/src/main/java/dev/streamcatch/android/core/MediaUrlDetector.kt tools/test_core.kt -include-runtime -d "$TMP/test.jar"
java -jar "$TMP/test.jar"
