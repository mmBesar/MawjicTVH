# MawjicTVH

**A native Tvheadend client for Android and Linux.**

MawjicTVH connects directly to Tvheadend over its HTSP TCP protocol. It is a separate application from Mawjic IPTV, with its own Android package identity (`app.mawjic.tvh`), Linux package identity, launcher icon, source tree, and data model.

This repository is a clean implementation. It does **not** contain the original Mawjic IPTV playlist parser, M3U/XMLTV ingestion, IPTV source manager, or IPTV player.

## Current milestone

The first milestone establishes a real server connection and reads Tvheadend's native metadata:

- HTSMSG binary message codec and length-prefixed TCP framing.
- HTSP `hello` negotiation, challenge-response authentication, and `enableAsyncMetadata`.
- Initial channel, tag, and EPG metadata ingestion, including live update/delete notifications.
- A minimal Android/Linux Compose UI for connecting and browsing the received channel list.
- Separate Android application ID and a new TV/signal launcher icon.

**Playback is not implemented in this milestone.** The next technical gate is an end-to-end `subscribe`/`muxpkt` proof, followed by a proper packet/timestamp bridge into Android Media3 and Linux mpv. DVR controls, artwork authentication, reconnection policy, server discovery, and persistent profile storage are also future work.

## Connect to Tvheadend

1. Enable HTSP on your Tvheadend server. The default HTSP TCP port is `9982`, unless you changed it.
2. Enter the server's hostname/IP and port.
3. Enter both username and password, or leave both empty when the server's access-control rules intentionally grant the client access based on its source address.
4. Press **Connect**. The channel list populates after Tvheadend sends `initialSyncCompleted`. Servers older than HTSP version 6 are rejected because that is the minimum version that includes EPG event notifications.

HTSP is a TCP protocol; the password is not sent directly, but the challenge-response digest is not transport encryption. Use a trusted LAN or VPN. Do not expose the HTSP port directly to the public internet.

## Build

Requirements: JDK 21, network access for Gradle/Maven dependencies, and the Android SDK for Android builds.

```bash
./gradlew :composeApp:desktopTest
./gradlew :composeApp:createDistributable :composeApp:packageDeb :composeApp:packageRpm
./gradlew :composeApp:assembleDebug
```

The checksum-verifying `gradlew` script bootstraps Gradle 8.14.6. No Rust toolchain or Android NDK is required by this new repository.

Linux package tasks run on the host architecture. GitHub Actions builds AMD64 and ARM64 independently, then verifies DEB/RPM metadata and package architecture before uploading artifacts. The CI log prints the full Compose package output tree before staging; it will fail with a targeted diagnostic if Gradle did not create either package.

## Project layout

```text
composeApp/src/commonMain/
  protocol/   HTSMSG values, codec, HTSP transport contract
  client/     HTSP request/reply session and authentication
  model/      Tvheadend metadata repository and models
  ui/         Shared Android/Linux Compose interface
composeApp/src/commonTest/   HTSMSG codec fixtures and malformed-frame tests
composeApp/src/desktopTest/  Simulated HTSP handshake/authentication/metadata test
composeApp/src/androidMain/  Android activity and socket transport
composeApp/src/desktopMain/  Linux entry point and socket transport
assets/icons/                Independent MawjicTVH icon
```

## Protocol notes

See [docs/HTSP_IMPLEMENTATION.md](docs/HTSP_IMPLEMENTATION.md) for the wire format, handshake, authentication, metadata flow, known limitations, and upstream references.

## Development status

See [BUILD_STATUS.md](BUILD_STATUS.md). Green source-level checks are not a substitute for the first hosted Android/Linux Gradle run or a live test against a real Tvheadend server.

## Licence

MIT. See [LICENSE](LICENSE).
