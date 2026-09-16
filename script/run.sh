#!/usr/bin/env bash
# Launch the game locally (via the core's run task).
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :core:run --no-daemon "$@"
