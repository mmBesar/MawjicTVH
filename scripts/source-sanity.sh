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
if grep -RFn 'import androidx.compose.runtime.onDispose' "$SRC"; then
  fail 'Do not import onDispose: it is a DisposableEffectScope member and is available only inside DisposableEffect { ... }.'
fi

if grep -RniE 'app\.mawjic\.tv([^h]|$)|M3U|XMLTV|PlaylistParser|playlist source' "$SRC"; then
  fail 'Legacy Mawjic IPTV identity or playlist/XMLTV implementation found in application source.'
fi
grep -Rq 'PREFERRED_HTSP_VERSION = 44' "$SRC/commonMain" || fail 'HTSP protocol negotiation is missing.'
grep -Rq 'CLIENT_VERSION = \"0.1.2\"' "$SRC/commonMain/kotlin/app/mawjic/tvh/client/HtspClient.kt" || fail 'HTSP client version is stale.'
grep -Rq 'enableAsyncMetadata' "$SRC/commonMain" || fail 'Native HTSP metadata initialization is missing.'
grep -Rq 'EVENT_PUBLISH_BATCH_SIZE' "$SRC/commonMain/kotlin/app/mawjic/tvh/model/TvheadendRepository.kt" || fail 'Large initial EPG dumps are not batched.'
if grep -Fq '_state.value.events +' "$SRC/commonMain/kotlin/app/mawjic/tvh/model/TvheadendRepository.kt"; then
  fail 'Do not copy the entire EPG map for each incoming event.'
fi
grep -Rq '"epg" to s64(1)' "$SRC/commonMain" || fail 'HTSP async EPG delivery is not explicitly enabled.'
grep -Rq 'createHtspTransport(useTls' "$SRC/commonMain/kotlin/app/mawjic/tvh/protocol" || fail 'Optional HTSP-over-TLS transport is missing.'
grep -Rq 'endpointIdentificationAlgorithm = \"HTTPS\"' "$SRC/androidMain/kotlin/app/mawjic/tvh/protocol" || fail 'HTSP TLS hostname verification is missing.'
grep -Rq 'endpointIdentificationAlgorithm = \"HTTPS\"' "$SRC/desktopMain/kotlin/app/mawjic/tvh/protocol" || fail 'Desktop HTSP TLS hostname verification is missing.'
grep -Rq 'certificates src="user"' "$SRC/androidMain/res/xml/network_security_config.xml" || fail 'Android user-installed CA trust is missing.'
[[ -s "$SRC/androidMain/res/xml/network_security_config.xml" ]] || fail 'Android network trust configuration for user-installed CAs is missing.'
grep -Rq 'channelAdd' "$SRC/commonMain" || fail 'HTSP channel metadata handling is missing.'
grep -Rq 'getTicket' "$SRC/commonMain" || fail 'Native Tvheadend ticket-based playback authorization is missing.'
grep -Rq '@RunWith(JUnit4::class)' "$SRC/desktopTest" || fail 'Desktop tests must pin the JUnit 4 runner explicitly.'
grep -Rq 'junit:junit:4.13.2' "$ROOT/composeApp/build.gradle.kts" || fail 'Desktop JUnit 4 runtime dependency is missing.'
grep -Rq 'media3-exoplayer' "$ROOT/composeApp/build.gradle.kts" || fail 'Android Media3 player dependency is missing.'
grep -Rq 'ProcessBuilder' "$SRC/desktopMain/kotlin/app/mawjic/tvh/player" || fail 'Linux mpv playback adapter is missing.'
[[ -s "$SRC/androidMain/kotlin/app/mawjic/tvh/player/PlatformTvheadendPlayer.android.kt" ]] || fail 'Android player implementation is missing.'
[[ -s "$SRC/desktopMain/kotlin/app/mawjic/tvh/player/PlatformTvheadendPlayer.desktop.kt" ]] || fail 'Linux player implementation is missing.'

echo 'Source boundary checks completed.'
