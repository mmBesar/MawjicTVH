# HTSP implementation notes

## Protocol identity

HTSP (Home TV Streaming Protocol) is Tvheadend's TCP protocol for live television subscriptions and associated metadata (channels, channel tags, EPG). It is not an M3U/XMLTV transport and it is not MessagePack.

The current Tvheadend upstream server source inspected for this baseline declares `HTSP_PROTO_VERSION 44`. The client offers version 44 in `hello`; the server selects the lower of the client and server versions. This first metadata milestone requires HTSP version 6 or newer because EPG event notifications were added in version 6. The version value is negotiated per connection and all optional fields must be interpreted according to that selected version.

## Binary wire format implemented here

Each TCP message is framed as:

- 4-byte unsigned message-body length, big-endian; the length excludes those four bytes.
- That many bytes of HTSMSG field records.

Each field record contains:

- 1-byte field type.
- 1-byte field-name length.
- 4-byte payload length, big-endian.
- UTF-8 field name (zero bytes for list entries).
- Payload bytes.

Implemented field types:

| ID | Type | Encoding |
|---:|---|---|
| 1 | Map | Nested field records; payload length is the nested bytes only. |
| 2 | S64 | Little-endian variable-width integer; negative values use 8 bytes. |
| 3 | String | UTF-8 without a trailing NUL. |
| 4 | Binary | Raw bytes. |
| 5 | List | Nested unnamed field records. |
| 7 | Boolean | Empty payload for false, one byte for true. |
| 8 | UUID | 8 raw bytes. |

The published type table also lists double (type 6), but Tvheadend's current `htsmsg_binary.c` codec does not implement it in its serializer/deserializer switch. This application currently rejects it rather than guessing at an encoding it does not use. The codec also places a 16 MiB frame limit and 32-level nested-message limit on inbound data.

## Session sequence

1. Open a TCP connection (default port `9982`).
2. Send `hello` with `htspversion`, `clientname`, and `clientversion`.
3. Validate the returned protocol version and retain the session challenge and server metadata.
4. When credentials are supplied, calculate `SHA-1(UTF-8(password) || challenge)` and send the 20-byte digest with `authenticate`.
5. Send `enableAsyncMetadata`.
6. Consume server messages without `seq`. The initial metadata snapshot includes `channelAdd`, `tagAdd`, EPG event messages and ends with `initialSyncCompleted`; update/delete messages keep the local model current.
7. RPC responses with `seq` are correlated to the matching request.

The client currently treats a response with `error` or `noaccess` as an RPC failure. Empty credentials are permitted for installations that intentionally grant HTSP access by source IP. A username/password pair must be supplied together.

## Metadata supported in this milestone

- `channelAdd`, `channelUpdate`, `channelDelete`
- `tagAdd`, `tagUpdate`, `tagDelete`
- `eventAdd`, `eventUpdate`, `eventDelete`
- `initialSyncCompleted`

Unknown methods are ignored by the metadata repository. The client transport still receives them so future work can add subscription status, tuner signal, and streaming packet handling without changing the wire layer.

## Playback path implemented in this milestone

The first functional player uses Tvheadend's own ticket mechanism rather than attempting to reconstruct a media stream from raw `muxpkt` packets before we have an end-to-end player:

1. The app authenticates and obtains channel metadata on the persistent HTSP connection.
2. On channel selection it calls HTSP `getTicket` with `channelId`.
3. Tvheadend returns `path` and `ticket`. The ticket authorizes access to that channel over the HTTP streaming interface.
4. The app builds the stream URL from the server-provided path and the configured streaming listener, including `webroot` when configured.
5. Android hands the URL to Media3/ExoPlayer; Linux launches mpv with the same URL.

This is not playlist playback. The `getTicket` RPC is defined by Tvheadend's HTSP API and the media URL is issued by the server. The output container/codec is controlled by the Tvheadend streaming profile. Android Media3 supports MPEG-TS as a progressive container, while actual audio/video decoding still depends on sample codec support on the device. Linux delegates decoding to mpv.

The next gate is to play a real channel from a real Tvheadend installation on both platforms, checking permissions, reverse-proxy `webroot`, HTTPS listener, stream profile and codec compatibility. The UI must show a clear failure instead of claiming playback is working when a ticket, stream or decoder fails.

A later advanced option is a direct HTSP packet pipeline using `subscribe`, `subscriptionStart`, and `muxpkt`. It must preserve stream descriptors, codec initialization, DTS/PTS and stream changes, and should only replace the ticketed route if it provides a concrete advantage without regressions.

## Security and operational boundaries

- Do not log usernames, passwords, digests, challenges, or packet payloads.
- HTSP authentication is challenge-response, not encryption. Use a protected LAN/VPN and do not publish the TCP port to the public internet.
- TCP reads are length-bounded; malformed/truncated messages fail the session.
- User-provided server address/port are validated before connecting.
- Automatic reconnect, credential vaulting, certificate support, server discovery and artwork authorization are not implemented yet.

## Upstream references

- [Tvheadend HTSP overview](https://docs.tvheadend.org/documentation/development/htsp/general)
- [HTSMSG binary format](https://docs.tvheadend.org/documentation/development/htsp/htsmsg-binary-format)
- [HTSP communication and authentication](https://docs.tvheadend.org/documentation/development/htsp/communication)
- [Client-to-server RPC methods](https://docs.tvheadend.org/documentation/development/htsp/client-to-server-rpc-methods)
- [Server-to-client methods](https://docs.tvheadend.org/documentation/development/htsp/server-to-client-methods)
- [Tvheadend upstream `htsmsg_binary.c`](https://github.com/tvheadend/tvheadend/blob/master/src/htsmsg_binary.c)
- [Tvheadend upstream `htsp_server.c`](https://github.com/tvheadend/tvheadend/blob/master/src/htsp_server.c)
