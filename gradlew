#!/bin/sh
# Bootstrap Gradle on macOS/Linux when the official wrapper JAR is not bundled.
set -eu
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/photo-pdf-bootstrap"
DIST="$BASE/gradle-8.9"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$BASE"
  ARCHIVE="$BASE/gradle-8.9-bin.zip"
  curl --fail --location --retry 2 --output "$ARCHIVE" \
    https://services.gradle.org/distributions/gradle-8.9-bin.zip
  unzip -q -o "$ARCHIVE" -d "$BASE"
  rm -f "$ARCHIVE"
fi
exec "$DIST/bin/gradle" "$@"
