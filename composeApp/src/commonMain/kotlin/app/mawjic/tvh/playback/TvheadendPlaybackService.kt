package app.mawjic.tvh.playback

import app.mawjic.tvh.client.HtspClient
import app.mawjic.tvh.client.HtspException
import app.mawjic.tvh.protocol.HtsMessage
import app.mawjic.tvh.protocol.hstr
import app.mawjic.tvh.protocol.s64

/** HTTP listener used only for the media bytes; control and authorization stay on HTSP. */
data class TvheadendHttpEndpoint(
    val host: String,
    val port: Int = 9981,
    val useHttps: Boolean = false,
    val webRoot: String? = null,
) {
    init {
        require(host.isNotBlank()) { "Server address cannot be empty" }
        require(port in 1..65535) { "Streaming port must be between 1 and 65535" }
        require(!host.contains("://")) { "Enter a hostname or IP address, not a URL" }
        require(host.trim().none { it.isWhitespace() || it == '/' || it == '?' || it == '#' || it == '\\' }) {
            "Enter a hostname or IP address only, without a path or URL characters"
        }
    }
}

/** Obtains temporary Tvheadend HTTP playback tickets through the authenticated HTSP session. */
class TvheadendPlaybackService(
    private val client: HtspClient,
    private val endpoint: TvheadendHttpEndpoint,
) {
    suspend fun createChannelStreamUrl(channelId: Long): String {
        require(channelId in 1..0xffff_ffffL) { "Invalid Tvheadend channel ID" }
        val ticketReply = client.request(
            "getTicket",
            HtsMessage.mapOf("channelId" to s64(channelId)),
        )
        val path = ticketReply.text("path")
            ?: throw HtspException("Tvheadend did not return a stream path for this channel.")
        val ticket = ticketReply.text("ticket")
            ?: throw HtspException("Tvheadend did not return a playback ticket for this channel.")
        if (!path.startsWith('/') || path.startsWith("//") ||
            path.any { it.isWhitespace() || it == '\\' || it == '#' }) {
            throw HtspException("Tvheadend returned an invalid playback path.")
        }
        if (ticket.isBlank()) {
            throw HtspException("Tvheadend returned an invalid playback ticket.")
        }
        return buildTicketedStreamUrl(endpoint, path, ticket)
    }
}

internal fun buildTicketedStreamUrl(
    endpoint: TvheadendHttpEndpoint,
    path: String,
    ticket: String,
): String {
    val root = endpoint.webRoot.orEmpty().trim()
        .let { if (it.isBlank() || it == "/") "" else "/" + it.trim('/') }
    val rootedPath = when {
        root.isEmpty() -> path
        path == root || path.startsWith("$root/") -> path
        else -> root + path
    }
    val host = endpoint.host.trim().removePrefix("[").removeSuffix("]")
    val authorityHost = if (host.contains(':')) "[$host]" else host
    val scheme = if (endpoint.useHttps) "https" else "http"
    val separator = if ('?' in rootedPath) '&' else '?'
    return "$scheme://$authorityHost:${endpoint.port}$rootedPath${separator}ticket=${encodeQueryComponent(ticket)}"
}

/** RFC 3986 query-component encoding, implemented in common Kotlin without platform URL APIs. */
private fun encodeQueryComponent(value: String): String = buildString {
    val hex = "0123456789ABCDEF"
    for (byte in value.encodeToByteArray()) {
        val unsigned = byte.toInt() and 0xff
        val character = unsigned.toChar()
        if ((character in 'a'..'z') || (character in 'A'..'Z') ||
            (character in '0'..'9') || character in "-._~") {
            append(character)
        } else {
            append('%')
            append(hex[unsigned ushr 4])
            append(hex[unsigned and 0x0f])
        }
    }
}
