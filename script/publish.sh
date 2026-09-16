#!/usr/bin/env bash
# One-click multi-platform release.
#
# Builds the game (core/build/libs/rf.jar, self-contained with every
# platform's LWJGL natives) and the independent launcher
# (modules/launcher/build/libs/launcher.jar), then assembles a platform-ready
# package under build/dist/.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION=$(sed -n 's/^rf_version=//p' gradle.properties | tr -d '\r')
DIST_DIR="build/dist"
STAGE="$DIST_DIR/rf-$VERSION"

./gradlew clean build :core:fatJar :launcher:fatJar --no-daemon

# note: the stage dir must be created after the build — gradle clean wipes build/
mkdir -p "$STAGE"

cp "core/build/libs/rf.jar" "$STAGE/rf.jar"
cp "modules/launcher/build/libs/launcher.jar" "$STAGE/launcher.jar"

# Windows launch script (runs the launcher, which spawns the game jar)
cat > "$STAGE/rf.bat" <<'BATEOF'
@echo off
java -jar "%~dp0launcher.jar" %*
BATEOF

# Unix launch script
cat > "$STAGE/rf" <<'SHEOF'
#!/usr/bin/env sh
exec java -jar "$(dirname "$0")/launcher.jar" "$@"
SHEOF
chmod +x "$STAGE/rf"

if command -v zip >/dev/null 2>&1; then
  (cd "$DIST_DIR" && rm -f "rf-$VERSION.zip" && zip -qr "rf-$VERSION.zip" "$(basename "$STAGE")")
  echo "Published: $DIST_DIR/rf-$VERSION.zip"
else
  (cd "$DIST_DIR" && rm -f "rf-$VERSION.tar.gz" && tar -czf "rf-$VERSION.tar.gz" "$(basename "$STAGE")")
  echo "Published: $DIST_DIR/rf-$VERSION.tar.gz (zip not available)"
fi
