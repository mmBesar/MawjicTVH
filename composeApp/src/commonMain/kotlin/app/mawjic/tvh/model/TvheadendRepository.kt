package app.mawjic.tvh.model

import app.mawjic.tvh.client.HtspClient
import app.mawjic.tvh.protocol.HtsMessage
import app.mawjic.tvh.protocol.HtsValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class TvChannel(
    val id: Long,
    val number: Long,
    val minorNumber: Long,
    val name: String,
    val iconPath: String?,
    val currentEventId: Long?,
    val tagIds: List<Long>,
)

data class TvTag(
    val id: Long,
    val name: String,
    val index: Long,
)

data class TvEvent(
    val id: Long,
    val channelId: Long,
    val title: String,
    val subtitle: String?,
    val summary: String?,
    val description: String?,
    val startEpochSeconds: Long,
    val stopEpochSeconds: Long,
    val isNew: Boolean,
)

data class TvheadendSnapshot(
    val channels: Map<Long, TvChannel> = emptyMap(),
    val tags: Map<Long, TvTag> = emptyMap(),
    val events: Map<Long, TvEvent> = emptyMap(),
    val initialSyncCompleted: Boolean = false,
    val receivedMessageCount: Long = 0,
    val lastMessageMethod: String? = null,
    val syncError: String? = null,
)

/** Converts native HTSP metadata notifications into immutable UI state. */
class TvheadendRepository(private val client: HtspClient) {
    private val _state = MutableStateFlow(TvheadendSnapshot())
    val state: StateFlow<TvheadendSnapshot> = _state.asStateFlow()

    // HTSP's initial EPG dump may contain tens of thousands of events. Keep the
    // working model mutable on the background collector and publish snapshots in
    // batches, rather than copying an ever-growing immutable map for every event.
    private val channels = linkedMapOf<Long, TvChannel>()
    private val tags = linkedMapOf<Long, TvTag>()
    private val events = linkedMapOf<Long, TvEvent>()
    private var initialSyncCompleted = false
    private var receivedMessageCount = 0L
    private var lastMessageMethod: String? = null
    private var pendingEventMessages = 0

    fun observe(scope: CoroutineScope): Job = scope.launch(Dispatchers.Default) {
        try {
            client.messages.collect(::applyMessage)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            publish(
                syncError = error.message?.takeIf { it.isNotBlank() }
                    ?: "The HTSP connection stopped while receiving server metadata.",
            )
        }
    }

    private fun applyMessage(message: HtsMessage) {
        val method = message.text("method")
        receivedMessageCount++
        lastMessageMethod = method ?: "(message without method)"

        when (method) {
            "channelAdd", "channelUpdate" -> {
                upsertChannel(message)
                publish()
            }
            "channelDelete" -> {
                message.long("channelId")?.let(channels::remove)
                publish()
            }
            "tagAdd", "tagUpdate" -> {
                upsertTag(message)
                publish()
            }
            "tagDelete" -> {
                message.long("tagId")?.let(tags::remove)
                publish()
            }
            "eventAdd", "eventUpdate" -> {
                upsertEvent(message)
                publishEventUpdate()
            }
            "eventDelete" -> {
                message.long("eventId")?.let(events::remove)
                publishEventUpdate()
            }
            "initialSyncCompleted" -> {
                initialSyncCompleted = true
                publish()
            }
            else -> if (receivedMessageCount % PROGRESS_PUBLISH_INTERVAL == 0L) publish()
        }
    }

    private fun publishEventUpdate() {
        pendingEventMessages++
        if (initialSyncCompleted || pendingEventMessages >= EVENT_PUBLISH_BATCH_SIZE) {
            publish()
        }
    }

    private fun publish(syncError: String? = null) {
        pendingEventMessages = 0
        _state.value = TvheadendSnapshot(
            channels = channels.toMap(),
            tags = tags.toMap(),
            events = events.toMap(),
            initialSyncCompleted = initialSyncCompleted,
            receivedMessageCount = receivedMessageCount,
            lastMessageMethod = lastMessageMethod,
            syncError = syncError,
        )
    }

    private fun upsertChannel(message: HtsMessage) {
        val id = message.long("channelId") ?: return
        val old = channels[id]
        val currentEventId = if (message.value("eventId") != null) {
            message.long("eventId")?.takeIf { it > 0L }
        } else {
            old?.currentEventId
        }
        channels[id] = TvChannel(
            id = id,
            number = message.long("channelNumber") ?: old?.number ?: 0,
            minorNumber = message.long("channelNumberMinor") ?: old?.minorNumber ?: 0,
            name = message.text("channelName") ?: old?.name ?: "Channel $id",
            iconPath = message.text("channelIcon") ?: old?.iconPath,
            currentEventId = currentEventId,
            tagIds = message.list("tags")?.mapNotNull { (it as? HtsValue.Int64)?.value } ?: old?.tagIds.orEmpty(),
        )
    }

    private fun upsertTag(message: HtsMessage) {
        val id = message.long("tagId") ?: return
        val old = tags[id]
        tags[id] = TvTag(
            id = id,
            name = message.text("tagName") ?: old?.name ?: "Group $id",
            index = message.long("tagIndex") ?: old?.index ?: 0,
        )
    }

    private fun upsertEvent(message: HtsMessage) {
        val id = message.long("eventId") ?: return
        val old = events[id]
        events[id] = TvEvent(
            id = id,
            channelId = message.long("channelId") ?: old?.channelId ?: 0,
            title = message.text("title") ?: old?.title ?: "Untitled programme",
            subtitle = message.text("subtitle") ?: old?.subtitle,
            summary = message.text("summary") ?: old?.summary,
            description = message.text("description") ?: old?.description,
            startEpochSeconds = message.long("start") ?: old?.startEpochSeconds ?: 0,
            stopEpochSeconds = message.long("stop") ?: old?.stopEpochSeconds ?: 0,
            isNew = message.boolean("isNew") ?: ((message.long("isNew") ?: 0L) != 0L || message.text("isNew") == "1"),
        )
    }

    private companion object {
        const val EVENT_PUBLISH_BATCH_SIZE = 256
        const val PROGRESS_PUBLISH_INTERVAL = 128L
    }
}
