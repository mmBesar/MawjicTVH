package app.mawjic.tvh.client

import app.mawjic.tvh.protocol.createHtspTransport
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlinx.coroutines.runBlocking

class JvmHtspTransportTest {
    @Test
    fun readsAndWritesFourByteBigEndianFrames() = runBlocking {
        val payloadFromClient = byteArrayOf(2, 5, 0, 0, 0, 2, 118, 97, 108, 117, 101, 0x39, 0x05)
        val payloadFromServer = byteArrayOf(3, 1, 0, 0, 0, 1, 120, 121)
        ServerSocket(0).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val peer = executor.submit {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val header = ByteArray(4)
                    readFully(input, header)
                    val length = ((header[0].toInt() and 0xff) shl 24) or
                        ((header[1].toInt() and 0xff) shl 16) or
                        ((header[2].toInt() and 0xff) shl 8) or
                        (header[3].toInt() and 0xff)
                    val received = ByteArray(length)
                    readFully(input, received)
                    check(received.contentEquals(payloadFromClient))

                    val output = socket.getOutputStream()
                    val size = payloadFromServer.size
                    output.write(byteArrayOf(
                        (size ushr 24).toByte(),
                        (size ushr 16).toByte(),
                        (size ushr 8).toByte(),
                        size.toByte(),
                    ))
                    output.write(payloadFromServer)
                    output.flush()
                }
            }

            val transport = createHtspTransport()
            try {
                transport.connect("127.0.0.1", server.localPort)
                transport.writeFrame(payloadFromClient)
                val received = transport.readFrame()
                assertContentEquals(payloadFromServer, received)
                peer.get()
            } finally {
                transport.close()
                executor.shutdownNow()
            }
        }
    }

    private fun readFully(input: java.io.InputStream, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            check(count > 0) { "Unexpected end of TCP stream" }
            offset += count
        }
    }
}
