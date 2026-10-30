package app.mawjic.tvh.protocol

/** A framed TCP transport. Each frame contains one HTSMSG body (without its 4-byte length). */
interface HtspTransport {
    suspend fun connect(host: String, port: Int)
    suspend fun readFrame(): ByteArray
    suspend fun writeFrame(body: ByteArray)
    fun close()
}

expect fun createHtspTransport(): HtspTransport
expect fun sha1(data: ByteArray): ByteArray
