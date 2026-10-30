#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly SRC="$ROOT/composeApp/src"
readonly GREEN='\033[32m'
readonly RED='\033[31m'
readonly RESET='\033[0m'

ok() { printf '%bPASS%b %s\n' "$GREEN" "$RESET" "$1"; }
fail() { printf '%bFAIL%b %s\n' "$RED" "$RESET" "$1" >&2; exit 1; }

[[ -d "$SRC/commonMain" && -d "$SRC/androidMain" && -d "$SRC/desktopMain" ]] || fail 'Expected clean Kotlin Multiplatform source sets.'
[[ ! -d "$ROOT/rust-core" ]] || fail 'The new Tvheadend client must not inherit the old IPTV Rust parser.'
[[ ! -d "$SRC/commonMain/kotlin/app/mawjic/tv" ]] || fail 'Old app.mawjic.tv source tree is present.'
grep -Rq 'applicationId = "app.mawjic.tvh"' "$ROOT/composeApp/build.gradle.kts" || fail 'Android application ID is not app.mawjic.tvh.'
grep -Rq 'namespace = "app.mawjic.tvh"' "$ROOT/composeApp/build.gradle.kts" || fail 'Android namespace is not app.mawjic.tvh.'
grep -Rq 'MawjicTVH' "$ROOT/composeApp/src/androidMain/AndroidManifest.xml" || fail 'Android label is missing.'
[[ -s "$ROOT/assets/icons/mawjictvh.svg" && -s "$ROOT/assets/icons/mawjictvh.png" ]] || fail 'The distinct Tvheadend app icon is missing.'

if grep -RniE 'app\.mawjic\.tv([^h]|$)|M3U|XMLTV|PlaylistParser|playlist source' "$SRC"; then
  fail 'Legacy Mawjic IPTV identity or playlist/XMLTV implementation found in application source.'
fi
grep -Rq 'PREFERRED_HTSP_VERSION = 44' "$SRC/commonMain" || fail 'HTSP protocol negotiation is missing.'
grep -Rq 'enableAsyncMetadata' "$SRC/commonMain" || fail 'Native HTSP metadata initialization is missing.'
grep -Rq 'channelAdd' "$SRC/commonMain" || fail 'HTSP channel metadata handling is missing.'

echo 'Source boundary checks completed.'
