#!/usr/bin/env bash
# Run the JUnit test suite.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew test --no-daemon "$@"
