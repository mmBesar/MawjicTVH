package app.mawjic.tvh.client

import app.mawjic.tvh.model.TvheadendRepository
import app.mawjic.tvh.protocol.HtsMessage
import app.mawjic.tvh.protocol.HtsMessageCodec
import app.mawjic.tvh.protocol.HtsValue
import app.mawjic.tvh.protocol.HtspTransport
import app.mawjic.tvh.protocol.hbin
import app.mawjic.tvh.protocol.hstr
import app.mawjic.tvh.protocol.s64
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtspClientTest {
    @Test
    fun connectsAuthenticatesAndReceivesNativeMetadata() = runBlocking {
        val challenge = ByteArray(32) { (it * 3).toByte() }
        val transport = FakeHtspTransport(challenge, expectedUsername = "tvh", expectedPassword = "secret")
        val client = HtspClient(transport, requestTimeoutMillis = 2_000)
        val scope = CoroutineScope(Dispatchers.Default)
        val repository = TvheadendRepository(client)
        val observer = repository.observe(scope)

        try {
            val server = client.connect("tvheadend.home", username = "tvh", password = "secret")
            assertEquals("Living Room TV", server.name)
            assertEquals("4.3-999~g123456", server.version)
            assertEquals(44, server.protocolVersion)

            val state = withTimeout(2_000) {
                repository.state.first { it.initialSyncCompleted }
            }
            assertEquals("BBC One", state.channels[101]?.name)
            assertEquals(1L, state.channels[101]?.number)
            assertEquals(listOf(7L), state.channels[101]?.tagIds)
            assertEquals("News at Ten", state.events[9001]?.title)
            assertEquals(true, state.events[9001]?.isNew)
            assertEquals(7L, state.tags[7]?.index)
            assertTrue(transport.authenticationVerified)
        } finally {
            observer.cancel()
            scope.cancel()
            client.close()
        }
    }

    private class FakeHtspTransport(
        private val challenge: ByteArray,
        private val expectedUsername: String,
        private val expectedPassword: String,
    ) : HtspTransport {
        private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
        var authenticationVerified: Boolean = false
            private set

        override suspend fun connect(host: String, port: Int) {
            assertEquals("tvheadend.home", host)
            assertEquals(9982, port)
        }

        override suspend fun readFrame(): ByteArray = incoming.receive()

        override suspend fun writeFrame(body: ByteArray) {
            val request = HtsMessageCodec.decodeBody(body)
            val seq = request.long("seq") ?: error("RPC request has no sequence")
            val reply = when (request.text("method")) {
                "hello" -> HtsMessage.mapOf(
                    "seq" to s64(seq),
                    "htspversion" to s64(44),
                    "servername" to hstr("Living Room TV"),
                    "serverversion" to hstr("4.3-999~g123456"),
                    "challenge" to hbin(challenge),
                    "servercapability" to HtsValue.ListValue(listOf(hstr("imagecache"), hstr("timeshift"))),
                )
                "authenticate" -> {
                    val expectedDigest = MessageDigest.getInstance("SHA-1")
                        .digest(expectedPassword.encodeToByteArray() + challenge)
                    val valid = request.text("username") == expectedUsername &&
                        request.binary("digest")?.contentEquals(expectedDigest) == true
                    authenticationVerified = valid
                    HtsMessage.mapOf(
                        "seq" to s64(seq),
                        "noaccess" to s64(if (valid) 0 else 1),
                    )
                }
                "enableAsyncMetadata" -> HtsMessage.mapOf("seq" to s64(seq))
                else -> error("Unexpected HTSP RPC: ${request.text("method")}")
            }
            incoming.send(HtsMessageCodec.encodeBody(reply))

            if (request.text("method") == "enableAsyncMetadata") {
                incoming.send(HtsMessageCodec.encodeBody(HtsMessage.mapOf(
                    "method" to hstr("tagAdd"),
                    "tagId" to s64(7),
                    "tagName" to hstr("News"),
                    "tagIndex" to s64(7),
                )))
                incoming.send(HtsMessageCodec.encodeBody(HtsMessage.mapOf(
                    "method" to hstr("channelAdd"),
                    "channelId" to s64(101),
                    "channelNumber" to s64(1),
                    "channelName" to hstr("BBC One"),
                    "tags" to HtsValue.ListValue(listOf(s64(7))),
                    "eventId" to s64(9001),
                )))
                incoming.send(HtsMessageCodec.encodeBody(HtsMessage.mapOf(
                    "method" to hstr("eventAdd"),
                    "eventId" to s64(9001),
                    "channelId" to s64(101),
                    "title" to hstr("News at Ten"),
                    "start" to s64(1_800_000_000),
                    "stop" to s64(1_800_003_600),
                    "isNew" to HtsValue.BooleanValue(true),
                )))
                incoming.send(HtsMessageCodec.encodeBody(HtsMessage.mapOf("method" to hstr("initialSyncCompleted"))))
            }
        }

        override fun close() {
            incoming.close()
        }
    }
}
