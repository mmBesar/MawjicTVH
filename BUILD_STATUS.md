# Build and implementation status

**Baseline:** 0.1.0 · first clean Tvheadend-native milestone · 2026-10-31

## Implemented in source

- New Kotlin Multiplatform + Compose Android/Linux application.
- Android namespace and application ID: `app.mawjic.tvh` (not `app.mawjic.tv`).
- Independent Android launcher artwork and Linux package icon; Linux package ID: `mawjictvh`.
- HTSMSG binary message codec for map, list, S64, string, binary, boolean, and 64-bit UUID fields.
- Bounded HTSP TCP framing, `hello` protocol negotiation, SHA-1 challenge-response authentication, RPC sequence correlation, and `enableAsyncMetadata` initialization.
- Tvheadend channel/tag/EPG initial metadata ingestion and update/delete handling.
- Basic Android/Linux connection UI and channel list.
- GitHub Actions matrix for independent Linux AMD64/ARM64 DEB+RPM packages and Android debug APK.
- CI package step inspects the complete Compose output tree and emits actionable diagnostics when DEB/RPM artifacts are absent.

## Not implemented yet

- Actual live playback: `subscribe`, `subscriptionStart`, `muxpkt`, timestamp/codec mapping and Media3/mpv adapters.
- Artwork fetching/authentication, programme timeline UI, favourites, search, DVR/recording controls, tuner diagnostics, reconnection and saved server profiles.
- Release APK signing and public release automation.

## Verification performed in the authoring environment

- Compiled the common HTSP codec, session/client, metadata repository, transport contract, and JVM socket transport with the installed Kotlin compiler. The standalone compile emits only the expected Kotlin 1.9 warning caused by compiling `expect` and `actual` together for this check.
- Ran six codec tests and a simulated-server integration test covering `hello`, SHA-1 challenge authentication, `enableAsyncMetadata`, and channel/tag/EPG metadata delivery; all passed.
- `scripts/source-sanity.sh`, shell syntax checks, XML/SVG parsing, YAML parsing, and Git whitespace checks are run before packaging.
- This environment cannot resolve the Gradle download host and has no cached pinned Gradle distribution or Android SDK. Therefore the Compose Android/UI build, actual Gradle `desktopTest` task, Android APK, and Linux installers are **not claimed as locally verified**. The first fresh GitHub Actions run is the required build gate.
- No live Tvheadend server is attached to this authoring environment, so socket interoperability and the channel/EPG snapshot are not claimed as server-tested.

## Acceptance criteria for the first hosted CI run

- Linux AMD64 and ARM64: `desktopTest`, `createDistributable`, `packageDeb`, `packageRpm`; verify package metadata and CPU architecture.
- Android: `assembleDebug`; inspect the built APK to confirm `app.mawjic.tvh`. It is a platform-neutral debug APK (and runs on ARM64); it is not an ABI-filtered ARM64-only package.
- All jobs must be green before calling this baseline build-verified.

## Next milestone

Build a repeatable playback proof against a real Tvheadend server: subscribe to one channel, decode stream descriptors, persistently receive `muxpkt`, preserve DTS/PTS, and demonstrate a stable stream on Android and Linux. Do not start a major UI redesign until this is demonstrated.
