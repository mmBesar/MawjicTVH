#!/usr/bin/env bash
# Checksum-verifying Gradle bootstrap. GitHub Actions uses setup-gradle; local use
# downloads the pinned official distribution only when it is not already cached.
set -euo pipefail
readonly GRADLE_VERSION="8.14.6"
readonly DIST_SHA256="7988ed071b2a07900e2ec715fca15c6ab72bce6db433af301fa7fa7e9407bd24"
readonly DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
readonly CACHE_ROOT="${GRADLE_USER_HOME:-${HOME}/.gradle}/wrapper/dists/mawjictvh-gradle-${GRADLE_VERSION}"
readonly DIST_DIR="${CACHE_ROOT}/gradle-${GRADLE_VERSION}"
if [[ -x "${DIST_DIR}/bin/gradle" ]]; then exec "${DIST_DIR}/bin/gradle" "$@"; fi
if command -v gradle >/dev/null 2>&1; then
  found="$(gradle --version | awk '/^Gradle / {print $2; exit}')"
  if [[ "$found" == "$GRADLE_VERSION" ]]; then exec gradle "$@"; fi
fi
for tool in curl sha256sum unzip; do
  command -v "$tool" >/dev/null 2>&1 || { echo "ERROR: $tool is required to bootstrap Gradle $GRADLE_VERSION" >&2; exit 127; }
done
mkdir -p "$CACHE_ROOT"
readonly ZIP_TMP="${CACHE_ROOT}/gradle-${GRADLE_VERSION}-bin.zip.tmp"
readonly ZIP_FILE="${CACHE_ROOT}/gradle-${GRADLE_VERSION}-bin.zip"
rm -f "$ZIP_TMP"
printf 'Downloading Gradle %s (SHA-256 pinned)…\n' "$GRADLE_VERSION"
curl --fail --location --retry 3 --connect-timeout 15 "$DIST_URL" --output "$ZIP_TMP"
printf '%s  %s\n' "$DIST_SHA256" "$ZIP_TMP" | sha256sum --check --status || { rm -f "$ZIP_TMP"; echo 'ERROR: Gradle checksum mismatch; refusing to execute it.' >&2; exit 1; }
mv "$ZIP_TMP" "$ZIP_FILE"
TMP_DIR="${CACHE_ROOT}/unpack.$$"
mkdir -p "$TMP_DIR"
unzip -q "$ZIP_FILE" -d "$TMP_DIR"
[[ -x "${TMP_DIR}/gradle-${GRADLE_VERSION}/bin/gradle" ]] || { rm -rf "$TMP_DIR"; echo 'ERROR: invalid Gradle distribution' >&2; exit 1; }
mv "$TMP_DIR/gradle-${GRADLE_VERSION}" "$DIST_DIR"
rm -rf "$TMP_DIR"
exec "${DIST_DIR}/bin/gradle" "$@"
