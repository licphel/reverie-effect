#!/usr/bin/env bash
# Build the game (compile + test). Passes extra args through to Gradle.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew build --no-daemon "$@"
