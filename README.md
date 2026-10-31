# MawjicTVH

**A native Tvheadend client for Android and Linux.**

MawjicTVH is a separate application from Mawjic IPTV. It has its own Android application ID (`app.mawjic.tvh`), Linux package identity (`mawjictvh`), artwork, source tree, server model, and playback flow. It does not import M3U playlists or XMLTV files.

## Current milestone: connect, browse, and watch

The first functional baseline includes:

- HTSMSG binary messages and framed HTSP TCP transport.
- HTSP `hello` negotiation, challenge-response authentication, and asynchronous metadata initialization.
- Tvheadend channel, tag, and EPG metadata updates. Async EPG is explicitly enabled (`epg=1`) and the large event dump is processed off the UI thread.
- Channel selection and ticketed live playback using Tvheadend's native HTSP `getTicket` RPC.
- Android: embedded AndroidX Media3 / ExoPlayer video player with playback controls and error reporting.
- Linux: launches the system `mpv` player with the Tvheadend ticketed stream; playback controls are provided by mpv in its own window.

### How playback works

1. MawjicTVH connects and authenticates over HTSP (default TCP port `9982`).
2. When a channel is selected, it calls HTSP `getTicket` with that Tvheadend channel ID.
3. Tvheadend returns the authorized stream path and a temporary access ticket.
4. MawjicTVH combines that server-provided path/ticket with the configured HTTP(S) streaming listener. For a Caddy setup, the default playback endpoint is HTTPS port `443`; direct Tvheadend HTTP is usually port `9981`.
5. Android Media3 or Linux mpv opens the resulting live stream and decodes the actual audio/video.

This is not playlist playback: channel discovery, metadata, and playback authorization come from the authenticated Tvheadend HTSP session. Tvheadend's own HTTP streaming endpoint carries the media bytes, as its documented `getTicket` operation is designed to do. The configured Tvheadend streaming profile determines the output container and codecs.

Android Media3 supports MPEG-TS as a progressive container, subject to the contained audio/video codecs being supported by the device. If a server profile uses an unsupported format or codec, the app reports a playback error rather than silently claiming success. Linux delegates decoding to mpv.

## Connect to Tvheadend

MawjicTVH uses two distinct network endpoints. This matters when Tvheadend is behind Caddy:

1. **HTSP server address:** enter the Tvheadend machine's LAN/VPN address (normally port `9982`). This is raw TCP, not an HTTP URL. Keep the `Use TLS for HTSP` option off unless you have a TLS/Layer-4 listener that forwards raw HTSP. A normal Caddy `reverse_proxy` is not an HTSP TCP proxy.
2. **HTTPS playback hostname:** enter the Caddy hostname (for example `tv.example.net`) without a scheme or path. Leave the playback port at `443` and `Use HTTPS for playback` enabled. Caddy should reverse-proxy HTTP requests to Tvheadend's streaming listener (normally `9981`). Leave this field blank only when playback should use the same hostname as HTSP.
3. Enter both username and password, or leave both empty only when Tvheadend's access-control rules intentionally grant this client access by source IP.
4. Tap Connect. The app requests channel/tag metadata and EPG over HTSP; when a channel is tapped it requests a temporary `getTicket` over that same session and opens the ticketed HTTPS stream through Caddy.

A basic Caddy site for a Tvheadend HTTP listener is:

```caddyfile
tv.example.net {
    reverse_proxy 192.168.1.10:9981
}
```

Caddy's HTTP `reverse_proxy` forwards HTTP streaming and sets the `X-Forwarded-Proto`, `X-Forwarded-Host`, and `X-Forwarded-For` headers by default. Tvheadend's reverse-proxy guide requires the forwarded protocol to be `https` so the server knows it is behind HTTPS. If you use a path prefix or custom rewrites, ensure the ticket path returned by Tvheadend is reachable at that same public path; the app currently uses the server-provided `webroot` but does not have a separate configurable Caddy path-prefix field.

### Caddy's self-signed/local CA certificate on Android

The app trusts Android system CAs and user-installed CA certificates; it does **not** turn off certificate or hostname validation. When Caddy uses its local CA, install Caddy's **root CA certificate** (`root.crt`) in Android's user CA store, then reconnect. Do not copy or install Caddy's private root key (`root.key`). The certificate must contain a SAN matching the exact Caddy hostname entered in the playback field.

If you also route HTSP through Caddy, use a Caddy Layer 4 TCP listener/route to the Tvheadend HTSP port and enable `Use TLS for HTSP`. The certificate must cover the HTSP hostname too. Otherwise, the recommended and simpler setup is raw HTSP over a trusted LAN/VPN and Caddy HTTPS for the media stream.

On Linux, mpv uses the operating-system CA store for HTTPS playback. If you enable HTSP TLS through a Caddy Layer 4 endpoint, the JVM running MawjicTVH must also trust the Caddy root CA. The Tvheadend account needs HTSP access and permission to stream the selected channel. If sync completes but no channels appear, check the account's channel access and `Streaming` permission. The `epg=1` flag controls EPG events only; it does not control channel delivery. If no messages arrive, check that the HTSP field points to the actual native TCP listener, not the Caddy HTTP site. The app's wait diagnostic shows the number of HTSP server messages and the last method received to distinguish these cases.

**Security:** HTSP authentication is challenge-response, not transport encryption. HTTPS protects the media stream only; it does not encrypt an un-TLS HTSP connection. Use a trusted LAN/VPN for plain HTSP and do not expose port `9982` directly to the public internet. Avoid logging or sharing ticket URLs: the ticket grants temporary access to the selected stream.

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

The next CI patch version is `0.1.2` (Android versionCode `3`) so it can update the already-installed `0.1.1` debug build. The checksum-verifying wrapper bootstraps Gradle 8.14.6. No Rust toolchain or Android NDK is used in this clean project.

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
