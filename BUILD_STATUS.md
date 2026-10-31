# Build and implementation status

**Baseline:** 0.1.0 · first functional playback milestone · 2026-10-31

## Implemented in source

- New Kotlin Multiplatform + Compose Android/Linux application, separate from Mawjic IPTV.
- Android namespace/application ID: `app.mawjic.tvh`; Linux package ID: `mawjictvh`; distinct launcher artwork.
- HTSMSG binary codec, bounded HTSP TCP framing, protocol negotiation, SHA-1 challenge-response authentication, RPC sequence correlation, and asynchronous metadata initialization.
- Tvheadend channel/tag/EPG initial metadata and update/delete notifications.
- Channel browser with current EPG title where available.
- Native HTSP `getTicket` request for the selected Tvheadend channel and safe construction of the returned ticketed stream URL, including optional `webroot`, HTTPS, IPv6 literals, and query encoding.
- Android embedded Media3/ExoPlayer player with buffering indicator, controls, keep-screen-on, resource release, and user-facing network/permission/codec error categories.
- Linux mpv process adapter. It opens a separate mpv window and reports missing mpv and non-zero playback exits.
- CI matrix for Linux AMD64/ARM64 DEB+RPM packages and an Android debug APK. Linux diagnostics inspect the complete packaging output tree and identify missing package tools/artifacts.

## Playback design and scope

This milestone uses the server's documented HTSP `getTicket` operation to obtain a temporary, channel-specific HTTP(S) access ticket. The server-provided path and ticket are then passed to the platform decoder. The media bytes come from Tvheadend's built-in streaming listener; metadata and playback authorization are handled through HTSP. This is an official Tvheadend-supported playback route, not imported M3U/XMLTV content.

Android's Media3 supports MPEG-TS as a progressive container, but decoding still depends on the actual audio/video codecs and device decoder. Linux uses mpv, which must be installed separately. The current Linux DEB/RPM do not declare/install mpv automatically.

## Not yet implemented

- End-to-end playback test against a live Tvheadend server and validation of actual streaming profiles/codecs on Android and Linux.
- Direct HTSP `subscribe`/`subscriptionStart`/`muxpkt` packet playback path. The current MVP deliberately uses `getTicket` because Tvheadend provides that official HTTP ticket route for players.
- Artwork fetching/authentication, full EPG timeline UI, favourites, search, reconnection, saved server profiles, credential vault, tuner diagnostics, DVR/recording controls, and release signing.
- Embedded Linux video surface; Linux playback currently opens the external mpv window.

## CI hotfix — Compose disposal import

- Removed the invalid `import androidx.compose.runtime.onDispose` from `App.kt`. `onDispose` is a member of `DisposableEffectScope` and is resolved inside `DisposableEffect { ... }`; the cleanup block itself is unchanged.
- Added a source-sanity guard that emits a direct explanation if that invalid import is reintroduced.
- This addresses the reported shared Kotlin compile failure; a hosted CI run is still required to discover any subsequent compile or packaging errors.

## Verification performed in the authoring environment

- Compiled the common HTSP codec, session/client, metadata repository, transport contract, JVM socket transport, and new playback-ticket service with the installed Kotlin compiler.
- Ran a standalone simulated-server smoke test covering HTSP 44 negotiation, challenge authentication, native channel metadata, `getTicket`, reverse-proxy webroot handling, IPv6 URL formatting, and percent-encoded ticket construction; all passed.
- Added Gradle unit tests for ticket URLs (HTTP, HTTPS/webroot, IPv6, query encoding and invalid hosts) and expanded the desktop HTSP integration test to verify the requested channel ID and returned ticket URL. The hosted Gradle test run is still required.
- Source-boundary checks, shell syntax checks, XML/SVG parsing, YAML parsing and Git whitespace checks are included in the local validation pass.
- This environment cannot resolve the Gradle download host and has no cached pinned Gradle distribution or Android SDK. Therefore Android/Compose compilation, hosted Gradle tests, APK generation and Linux installers are **not claimed as build-verified**.
- No live Tvheadend server is attached. Do not call playback end-to-end tested until one real server/channel has been played successfully on each platform.

## First hosted CI acceptance criteria

- Linux AMD64 and ARM64: `desktopTest`, `createDistributable`, `packageDeb`, `packageRpm`; verify package metadata and CPU architecture.
- Android: `assembleDebug`; inspect the built APK and confirm `app.mawjic.tvh`.
- All jobs green, followed by a real Tvheadend channel playback check, before calling the release baseline complete.
