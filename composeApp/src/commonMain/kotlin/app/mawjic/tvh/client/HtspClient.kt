package app.mawjic.tvh.client

import app.mawjic.tvh.protocol.HtsField
import app.mawjic.tvh.protocol.HtsMessage
import app.mawjic.tvh.protocol.HtsMessageCodec
import app.mawjic.tvh.protocol.HtsValue
import app.mawjic.tvh.protocol.HtspTransport
import app.mawjic.tvh.protocol.hbin
import app.mawjic.tvh.protocol.hstr
import app.mawjic.tvh.protocol.currentEpochSeconds
import app.mawjic.tvh.protocol.s64
import app.mawjic.tvh.protocol.sha1
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class HtspException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class HtspServerInfo(
    val name: String,
    val version: String,
    val protocolVersion: Int,
    val webRoot: String?,
    val capabilities: List<String>,
)

/**
 * One authenticated HTSP session. Replies are matched by `seq`; server-originated
 * metadata and stream messages arrive on [messages] without a sequence number.
 */
class HtspClient(
    private val transport: HtspTransport,
    private val requestTimeoutMillis: Long = 15_000,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingLock = Mutex()
    private val writeLock = Mutex()
    private val pending = mutableMapOf<Long, CompletableDeferred<HtsMessage>>()
    private val inbound = Channel<HtsMessage>(Channel.UNLIMITED)
    private val shutdown = CompletableDeferred<Unit>()
    private var reader: Job? = null
    private var nextSequence = 0L
    private var closed = false

    val messages: Flow<HtsMessage> = inbound.receiveAsFlow()

    suspend fun connect(
        host: String,
        port: Int = 9982,
        username: String? = null,
        password: String? = null,
    ): HtspServerInfo {
        if (username.isNullOrBlank() != password.isNullOrBlank()) {
            throw HtspException("Enter both a Tvheadend username and password, or leave both empty.")
        }
        try {
            transport.connect(host, port)
            reader = scope.launch { readLoop() }
            val hello = request(
                "hello",
                HtsMessage.mapOf(
                    "htspversion" to s64(PREFERRED_HTSP_VERSION.toLong()),
                    "clientname" to hstr(CLIENT_NAME),
                    "clientversion" to hstr(CLIENT_VERSION),
                ),
            )
            val protocolVersion = hello.long("htspversion")?.toInt()
                ?: throw HtspException("Tvheadend did not return an HTSP protocol version.")
            if (protocolVersion < MIN_HTSP_VERSION) {
                throw HtspException("This server supports HTSP $protocolVersion; MawjicTVH requires HTSP $MIN_HTSP_VERSION or newer.")
            }
            val challenge = hello.binary("challenge")
                ?: throw HtspException("Tvheadend did not provide an authentication challenge.")
            if (challenge.size != 32) throw HtspException("Tvheadend returned an invalid authentication challenge length.")
            if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                // Tvheadend calculates SHA-1(password bytes || 32-byte server challenge).
                val digest = sha1(password.encodeToByteArray() + challenge)
                val authReply = request(
                    "authenticate",
                    HtsMessage.mapOf(
                        "username" to hstr(username),
                        "digest" to hbin(digest),
                    ),
                )
                if ((authReply.long("noaccess") ?: 0L) != 0L) {
                    throw HtspException("Tvheadend rejected these credentials or this account has no HTSP access.")
                }
            }

            // Tvheadend defaults async EPG delivery to OFF. `epg=1` is required.
            // In the current server implementation, `lastUpdate` is the lower bound
            // for EPG event start times (events at/before it are skipped), while
            // `epgMaxTime` defines the rolling upper bound. Keep a 12-hour backfill
            // so long-running programmes which began before now are still included.
            val now = currentEpochSeconds()
            request(
                "enableAsyncMetadata",
                HtsMessage.mapOf(
                    "epg" to s64(1),
                    "lastUpdate" to s64((now - EPG_BACKFILL_SECONDS).coerceAtLeast(0)),
                    "epgMaxTime" to s64(now + EPG_LOOKAHEAD_SECONDS),
                ),
            )

            return HtspServerInfo(
                name = hello.text("servername") ?: "Tvheadend",
                version = hello.text("serverversion") ?: "Unknown version",
                protocolVersion = protocolVersion,
                webRoot = hello.text("webroot"),
                capabilities = hello.list("servercapability").orEmpty().mapNotNull { (it as? HtsValue.Text)?.value },
            )
        } catch (error: Throwable) {
            close()
            if (error is CancellationException) throw error
            if (error is HtspException) throw error
            throw HtspException(error.message ?: "Could not connect to the Tvheadend server.", error)
        }
    }

    suspend fun request(method: String, arguments: HtsMessage): HtsMessage {
        val response = CompletableDeferred<HtsMessage>()
        val sequence = pendingLock.withLock {
            if (closed) throw HtspException("The Tvheadend connection is closed.")
            nextSequence = if (nextSequence >= 0x7fff_fffeL) 1L else nextSequence + 1L
            pending[nextSequence] = response
            nextSequence
        }
        val outbound = HtsMessage(
            listOf(
                HtsField("method", HtsValue.Text(method)),
                HtsField("seq", HtsValue.Int64(sequence)),
            ) + arguments.fields,
        )
        try {
            val body = HtsMessageCodec.encodeBody(outbound)
            writeLock.withLock { transport.writeFrame(body) }
            val reply = withTimeout(requestTimeoutMillis) {
                select<HtsMessage> {
                    response.onAwait { it }
                    shutdown.onAwait { throw HtspException("The Tvheadend connection was closed.") }
                }
            }
            reply.text("error")?.let { throw HtspException("Tvheadend $method failed: $it") }
            if ((reply.long("noaccess") ?: 0L) != 0L) {
                throw HtspException("Tvheadend denied access to '$method'. Check the account's streaming permissions.")
            }
            return reply
        } finally {
            pendingLock.withLock { pending.remove(sequence) }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        shutdown.complete(Unit)
        reader?.cancel()
        transport.close()
        scope.cancel()
        inbound.close()
    }

    private suspend fun readLoop() {
        try {
            while (!closed) {
                val frameBody = transport.readFrame()
                val message = HtsMessageCodec.decodeBody(frameBody)
                val sequence = message.long("seq")
                if (sequence != null) {
                    val waiter = pendingLock.withLock { pending[sequence] }
                    waiter?.complete(message)
                } else {
                    inbound.send(message)
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val wrapped = HtspException(error.message ?: "The Tvheadend connection was closed.", error)
            pendingLock.withLock {
                pending.values.forEach { it.completeExceptionally(wrapped) }
                pending.clear()
            }
            if (!closed) inbound.close(wrapped)
            closed = true
            shutdown.complete(Unit)
            transport.close()
        }
    }

    companion object {
        const val PREFERRED_HTSP_VERSION = 44
        const val MIN_HTSP_VERSION = 6
        const val CLIENT_NAME = "MawjicTVH"
        const val CLIENT_VERSION = "0.1.2"
        const val EPG_BACKFILL_SECONDS = 12L * 60L * 60L
        const val EPG_LOOKAHEAD_SECONDS = 14L * 24L * 60L * 60L
    }
}
