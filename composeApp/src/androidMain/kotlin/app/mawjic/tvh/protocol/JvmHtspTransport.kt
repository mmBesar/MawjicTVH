package app.mawjic.tvh.protocol

import java.io.EOFException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun createHtspTransport(useTls: Boolean): HtspTransport = JvmHtspTransport(useTls)

actual fun sha1(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(data)

actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1_000L

private class JvmHtspTransport(private val useTls: Boolean) : HtspTransport {
    private var socket: Socket? = null

    override suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "Server address cannot be empty" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        val address = InetSocketAddress(host.trim(), port)
        val raw = Socket()
        var active: Socket = raw
        try {
            raw.tcpNoDelay = true
            raw.keepAlive = true
            raw.connect(address, CONNECT_TIMEOUT_MS)
            if (useTls) {
                val factory = HttpsURLConnection.getDefaultSSLSocketFactory()
                val secure = factory.createSocket(raw, host.trim(), port, true) as SSLSocket
                val parameters: SSLParameters = secure.sslParameters
                parameters.endpointIdentificationAlgorithm = "HTTPS"
                secure.sslParameters = parameters
                secure.soTimeout = TLS_HANDSHAKE_TIMEOUT_MS
                secure.startHandshake()
                // SSLSocket does not enable hostname checks by default. Setting the HTTPS
                // endpoint-identification algorithm makes the TLS handshake validate the
                // certificate SAN against the requested host (without a trust-all verifier).
                active = secure
            }
            active.soTimeout = 0
            socket = active
        } catch (error: Throwable) {
            runCatching { active.close() }
            runCatching { raw.close() }
            throw error
        }
    }

    override suspend fun readFrame(): ByteArray = withContext(Dispatchers.IO) {
        val input = socket?.getInputStream() ?: error("HTSP socket is not connected")
        val header = ByteArray(4)
        readFully(input, header)
        val length = ((header[0].toInt() and 0xff) shl 24) or
            ((header[1].toInt() and 0xff) shl 16) or
            ((header[2].toInt() and 0xff) shl 8) or
            (header[3].toInt() and 0xff)
        require(length in 0..HtsMessageCodec.MAX_MESSAGE_BYTES) {
            "Tvheadend sent an invalid HTSP frame length: $length"
        }
        ByteArray(length).also { readFully(input, it) }
    }

    override suspend fun writeFrame(body: ByteArray) = withContext(Dispatchers.IO) {
        require(body.size <= HtsMessageCodec.MAX_MESSAGE_BYTES) { "HTSP frame exceeds the configured size limit" }
        val output = socket?.getOutputStream() ?: error("HTSP socket is not connected")
        val size = body.size
        output.write(byteArrayOf(
            (size ushr 24).toByte(),
            (size ushr 16).toByte(),
            (size ushr 8).toByte(),
            size.toByte(),
        ))
        output.write(body)
        output.flush()
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun readFully(input: InputStream, destination: ByteArray) {
        var offset = 0
        while (offset < destination.size) {
            val count = input.read(destination, offset, destination.size - offset)
            if (count < 0) throw EOFException("Tvheadend closed the HTSP connection")
            if (count == 0) continue
            offset += count
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val TLS_HANDSHAKE_TIMEOUT_MS = 10_000
    }
}
