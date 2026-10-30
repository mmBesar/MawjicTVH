package app.mawjic.tvh.protocol

import java.io.EOFException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun createHtspTransport(): HtspTransport = JvmHtspTransport()

actual fun sha1(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(data)

private class JvmHtspTransport : HtspTransport {
    private var socket: Socket? = null

    override suspend fun connect(host: String, port: Int) = withContext(Dispatchers.IO) {
        require(host.isNotBlank()) { "Server address cannot be empty" }
        require(port in 1..65535) { "Port must be between 1 and 65535" }
        val connection = Socket()
        try {
            connection.tcpNoDelay = true
            connection.keepAlive = true
            connection.connect(InetSocketAddress(host.trim(), port), CONNECT_TIMEOUT_MS)
            connection.soTimeout = 0
            socket = connection
        } catch (error: Throwable) {
            runCatching { connection.close() }
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
    }
}
