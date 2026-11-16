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
  # Verify the downloaded distribution against Gradle's published SHA-256.
  PHOTO_PDF_GRADLE_EXPECTED_SHA="d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab"
  if command -v sha256sum >/dev/null 2>&1; then
    PHOTO_PDF_GRADLE_ACTUAL_SHA=$(sha256sum "$ARCHIVE" | cut -d ' ' -f1)
  elif command -v shasum >/dev/null 2>&1; then
    PHOTO_PDF_GRADLE_ACTUAL_SHA=$(shasum -a 256 "$ARCHIVE" | cut -d ' ' -f1)
  else
    echo "SHA-256 verification tool not found" >&2
    exit 1
  fi
  if [ "$PHOTO_PDF_GRADLE_ACTUAL_SHA" != "$PHOTO_PDF_GRADLE_EXPECTED_SHA" ]; then
    rm -f "$ARCHIVE"
    echo "Gradle download checksum mismatch" >&2
    exit 1
  fi
  unzip -q -o "$ARCHIVE" -d "$BASE"
  rm -f "$ARCHIVE"
fi
exec "$DIST/bin/gradle" "$@"
