# MawjicTVH

**A native Tvheadend client for Android and Linux.**

MawjicTVH is a separate application from Mawjic IPTV. It has its own Android application ID (`app.mawjic.tvh`), Linux package identity (`mawjictvh`), artwork, source tree, server model, and playback flow. It does not import M3U playlists or XMLTV files.

## Current milestone: connect, browse, and watch

The first functional baseline includes:

- HTSMSG binary messages and framed HTSP TCP transport.
- HTSP `hello` negotiation, challenge-response authentication, and asynchronous metadata initialization.
- Tvheadend channel, tag, and EPG metadata updates.
- Channel selection and ticketed live playback using Tvheadend's native HTSP `getTicket` RPC.
- Android: embedded AndroidX Media3 / ExoPlayer video player with playback controls and error reporting.
- Linux: launches the system `mpv` player with the Tvheadend ticketed stream; playback controls are provided by mpv in its own window.

### How playback works

1. MawjicTVH connects and authenticates over HTSP (default TCP port `9982`).
2. When a channel is selected, it calls HTSP `getTicket` with that Tvheadend channel ID.
3. Tvheadend returns the authorized stream path and a temporary access ticket.
4. MawjicTVH combines that server-provided path/ticket with the configured HTTP(S) streaming listener (default HTTP port `9981`).
5. Android Media3 or Linux mpv opens the resulting live stream and decodes the actual audio/video.

This is not playlist playback: channel discovery, metadata, and playback authorization come from the authenticated Tvheadend HTSP session. Tvheadend's own HTTP streaming endpoint carries the media bytes, as its documented `getTicket` operation is designed to do. The configured Tvheadend streaming profile determines the output container and codecs.

Android Media3 supports MPEG-TS as a progressive container, subject to the contained audio/video codecs being supported by the device. If a server profile uses an unsupported format or codec, the app reports a playback error rather than silently claiming success. Linux delegates decoding to mpv.

## Connect to Tvheadend

1. Enable HTSP and HTTP streaming on your Tvheadend server. Defaults are HTSP TCP `9982` and HTTP `9981`; change these values in the app if your server or reverse proxy uses different ports.
2. Enter the server hostname/IP, HTSP port, and streaming port.
3. Enable HTTPS only when the configured streaming listener/reverse proxy really serves TLS on the port entered.
4. Enter both username and password, or leave both empty only when Tvheadend's access-control rules intentionally grant the connection without credentials.
5. Tap a channel to request its ticket and start playback.

The account needs HTSP access and permission to stream the selected channel. If Tvheadend returns no ticket, check channel access and streaming permissions.

**Security:** HTSP authentication is challenge-response, not transport encryption. HTTPS protects the media HTTP stream only; it does not encrypt the HTSP TCP control connection. Use a trusted LAN or VPN for HTSP, and do not expose port `9982` directly to the public internet. Avoid logging or sharing ticket URLs: the ticket grants temporary access to the selected stream.

## Linux playback requirement

Linux playback uses the system `mpv` executable. Install it before launching MawjicTVH:

- Debian/Ubuntu: `sudo apt install mpv`
- Fedora: `sudo dnf install mpv`
- Arch Linux: `sudo pacman -S mpv`

The Linux app opens mpv in a separate window; the Android player is embedded in MawjicTVH. The Linux DEB/RPM currently does not bundle mpv, so install the runtime dependency separately.

## Build

Requirements: JDK 21, network access for Gradle/Maven dependencies, and the Android SDK for Android builds. Linux native packages additionally need `fakeroot`, `dpkg-deb`, `rpm`/`rpmbuild`; Linux playback needs `mpv` at runtime.

```bash
./gradlew :composeApp:desktopTest
./gradlew :composeApp:createDistributable :composeApp:packageDeb :composeApp:packageRpm
./gradlew :composeApp:assembleDebug
```

The checksum-verifying wrapper bootstraps Gradle 8.14.6. No Rust toolchain or Android NDK is used in this clean project.

Linux package tasks are built on the matching host architecture. GitHub Actions builds AMD64 and ARM64 independently, inspects the complete Compose packaging output, validates DEB/RPM metadata and CPU architecture, and uploads the installers. The first hosted run remains the build gate; this authoring environment could not download the pinned Gradle distribution, so a successful Gradle/Android/Linux build is not claimed here.

## Project layout

```text
composeApp/src/commonMain/
  protocol/   HTSMSG codec and HTSP transport contract
  client/     HTSP session, authentication and RPC correlation
  model/      Tvheadend channel/tag/EPG repository
  playback/   getTicket and HTTP(S) stream URL construction
  player/     platform player contract
  ui/         Android/Linux Compose interface
composeApp/src/commonTest/   protocol and ticket URL tests
composeApp/src/desktopTest/  simulated HTSP handshake and getTicket integration test
composeApp/src/androidMain/  Android activity, TCP transport and Media3 player
composeApp/src/desktopMain/ Linux entry point, TCP transport and mpv adapter
assets/icons/                independent MawjicTVH icon
```

## Status and limitations

See [BUILD_STATUS.md](BUILD_STATUS.md) for exactly what has been verified. A simulated HTSP server test proves RPC/ticket handling; it does not replace a real-server playback test. First test the configured Tvheadend profile and one channel on the real server before declaring end-to-end playback validated.

## Licence

MIT. See [LICENSE](LICENSE).
