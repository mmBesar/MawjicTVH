# Build and implementation status

**Baseline:** 0.1.2 · metadata batching, Caddy TLS and desktop-test corrections · 2026-10-31

## Implemented in source

- New Kotlin Multiplatform + Compose Android/Linux application, separate from Mawjic IPTV.
- Android namespace/application ID: `app.mawjic.tvh`; Linux package ID: `mawjictvh`; distinct launcher artwork.
- HTSMSG binary codec, bounded HTSP TCP framing, protocol negotiation, SHA-1 challenge-response authentication, RPC sequence correlation, and asynchronous metadata initialization.
- Tvheadend channel/tag/EPG initial metadata and update/delete notifications; `enableAsyncMetadata` explicitly sets `epg=1`, with a 12-hour backfill and 14-day lookahead.
- Metadata ingestion runs on a background dispatcher; channel rows are shown as they arrive, receive-loop errors appear in the UI, and a 15-second no-metadata diagnostic shows the last server method.
- Separate HTSP and playback hostnames so the app can use a LAN/VPN HTSP address and a Caddy HTTPS hostname independently. Playback defaults to HTTPS port `443`; HTSP defaults to TCP `9982`.
- Optional TLS on the raw HTSP TCP socket with CA-chain validation and explicit hostname verification. Android Network Security Config trusts system and user-installed CAs, without a trust-all bypass.
- Channel browser with current EPG title where available.
- Native HTSP `getTicket` request for the selected Tvheadend channel and safe construction of the returned ticketed stream URL, including optional `webroot`, HTTPS, IPv6 literals, and query encoding.
- Android embedded Media3/ExoPlayer player with buffering indicator, controls, keep-screen-on, resource release, and user-facing network/permission/codec error categories.
- Linux mpv process adapter. It opens a separate mpv window and reports missing mpv and non-zero playback exits.
- CI matrix for Linux AMD64/ARM64 DEB+RPM packages and an Android debug APK. Desktop tests are a separate CI step using explicit JUnit 4, and full logs plus JUnit XML/HTML reports are uploaded even after failure. Android build logs/reports are also uploaded on failure.

## Playback design and scope

This milestone uses the server's documented HTSP `getTicket` operation to obtain a temporary, channel-specific HTTP(S) access ticket. The server-provided path and ticket are then passed to the platform decoder. The media bytes come from Tvheadend's built-in streaming listener; metadata and playback authorization are handled through HTSP. This is an official Tvheadend-supported playback route, not imported M3U/XMLTV content.

Android's Media3 supports MPEG-TS as a progressive container, but decoding still depends on the actual audio/video codecs and device decoder. Linux uses mpv, which must be installed separately. The current Linux DEB/RPM do not declare/install mpv automatically.

## Not yet implemented

- End-to-end playback test against a live Tvheadend server and validation of actual streaming profiles/codecs on Android and Linux.
- Direct HTSP `subscribe`/`subscriptionStart`/`muxpkt` packet playback path. The current MVP deliberately uses `getTicket` because Tvheadend provides that official HTTP ticket route for players.
- Artwork fetching/authentication, full EPG timeline UI, favourites, search, reconnection, saved server profiles, credential vault, tuner diagnostics, DVR/recording controls, and release signing.
- Embedded Linux video surface; Linux playback currently opens the external mpv window.

## Metadata, Caddy and desktop-test troubleshooting patch (0.1.2)

- Fixed the missing `epg=1` flag in `enableAsyncMetadata`; Tvheadend defaults async EPG delivery to off. The client now requests a bounded rolling window (`lastUpdate=now-12h`, `epgMaxTime=now+14d`). The current server source uses `lastUpdate` as a lower bound on EPG event start time (events with `start <= lastUpdate` are skipped); it does not control channel delivery. Empty channels therefore require separate HTSP endpoint/access investigation.
- Metadata is applied on `Dispatchers.Default` using mutable working maps; channels are published immediately and EPG snapshots are batched every 256 initial events, avoiding a full-map copy per event. Reader termination errors are exposed, and the UI times out after 15 seconds with message-count/last-method diagnostics.
- Separated the HTSP server address from the optional HTTPS playback hostname. Playback defaults to Caddy HTTPS/443; direct Tvheadend HTTP/9981 remains selectable.
- Added Android trust for user-installed CA certificates, plus HTSP TLS when raw TCP is exposed through Caddy Layer 4. TLS still requires a trusted certificate chain and a matching hostname; no certificate-verification bypass is used.
- Updated desktop tests to explicit JUnit 4 imports, `@RunWith(JUnit4::class)`, and explicit block-bodied `Unit` methods. The prior `InvalidTestClassError` report did not include JUnit's nested reason, so this is a defensive runner/method-signature correction, not a claim that the exact nested validation issue was proven.
- Split desktop tests from packaging and upload full test logs and XML/HTML reports even on failure. Android Gradle logs and reports are also uploaded. Android `versionCode` is incremented to 3 so the next debug APK can update the already-installed `versionCode=2` build.

## CI hotfix — Compose disposal import

- Removed the invalid `import androidx.compose.runtime.onDispose` from `App.kt`. `onDispose` is a member of `DisposableEffectScope` and is resolved inside `DisposableEffect { ... }`; the cleanup block itself is unchanged.
- Added a source-sanity guard that emits a direct explanation if that invalid import is reintroduced.
- This addresses the reported shared Kotlin compile failure; a hosted CI run is still required to discover any subsequent compile or packaging errors.

## Verification performed in the authoring environment

- Compiled the common HTSP codec, session/client, metadata repository, transport contract, JVM TLS-capable socket transport, and ticketed playback service using the locally available Kotlin 1.9 compiler (expect/actual emitted only the expected same-module warnings in this standalone compile).
- Ran a standalone simulated-server smoke test covering HTSP 44 negotiation, explicit `epg=1`, channel/EPG async notifications, `getTicket`, and generation of a Caddy HTTPS playback URL using a separate hostname; passed. The desktop integration test now sends 520 synthetic EPG events and verifies the full batched snapshot at `initialSyncCompleted`; passed.
- Compiled both desktop test source files against JUnit API stubs, verified their emitted JVM test methods are public `void` methods with no-argument constructors, and directly executed both test bodies; the simulated HTSP metadata/ticket test (including 520 EPG events) and local TCP framing test passed. This verifies source/test-body behavior, not Gradle's JUnit discovery integration.
- Parsed the updated CI YAML and Android XML, ran `source-sanity.sh`, shell syntax checks and `git diff --check`; passed.
- Ran a local TLS socket smoke test using a generated CA and server certificate: a trusted certificate with matching hostname connected successfully, while a trusted certificate with a mismatched hostname was rejected. This validates the desktop/JVM socket path; Android user-CA trust was XML-validated but not exercised on a physical device.
- Re-ran the local Gradle attempt: the checksum-pinned bootstrap could not resolve `services.gradle.org` (`curl: (6) Could not resolve host`). No pinned Gradle distribution or Android SDK is cached here, so the Gradle task and full Android/Compose build remain unverified.
- Source-boundary checks, shell syntax checks, XML/SVG parsing, YAML parsing and Git whitespace checks are included in the local validation pass.
- This environment cannot resolve the Gradle download host and has no cached pinned Gradle distribution or Android SDK. Therefore Android/Compose compilation, Gradle/JUnit discovery, APK generation and Linux installers are **not claimed as build-verified**.
- No live Tvheadend server is attached. Do not call playback end-to-end tested until one real server/channel has been played successfully on each platform.

## First hosted CI acceptance criteria

- Linux AMD64 and ARM64: `desktopTest`, `createDistributable`, `packageDeb`, `packageRpm`; verify package metadata and CPU architecture.
- Android: `assembleDebug`; inspect the built APK and confirm `app.mawjic.tvh`.
- All jobs green, followed by a real Tvheadend channel playback check, before calling the release baseline complete.
