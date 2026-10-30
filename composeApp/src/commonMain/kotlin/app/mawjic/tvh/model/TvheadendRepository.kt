package app.mawjic.tvh.model

import app.mawjic.tvh.client.HtspClient
import app.mawjic.tvh.protocol.HtsMessage
import app.mawjic.tvh.protocol.HtsValue
import kotlinx.coroutines.CoroutineScope
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
)

/** Converts native HTSP metadata notifications into immutable UI state. */
class TvheadendRepository(private val client: HtspClient) {
    private val _state = MutableStateFlow(TvheadendSnapshot())
    val state: StateFlow<TvheadendSnapshot> = _state.asStateFlow()

    fun observe(scope: CoroutineScope): Job = scope.launch {
        client.messages.collect(::applyMessage)
    }

    private fun applyMessage(message: HtsMessage) {
        when (message.text("method")) {
            "channelAdd", "channelUpdate" -> upsertChannel(message)
            "channelDelete" -> message.long("channelId")?.let { id ->
                _state.value = _state.value.copy(channels = _state.value.channels - id)
            }
            "tagAdd", "tagUpdate" -> upsertTag(message)
            "tagDelete" -> message.long("tagId")?.let { id ->
                _state.value = _state.value.copy(tags = _state.value.tags - id)
            }
            "eventAdd", "eventUpdate" -> upsertEvent(message)
            "eventDelete" -> message.long("eventId")?.let { id ->
                _state.value = _state.value.copy(events = _state.value.events - id)
            }
            "initialSyncCompleted" -> _state.value = _state.value.copy(initialSyncCompleted = true)
            // Stream/status methods are deliberately not interpreted as metadata.
            else -> Unit
        }
    }

    private fun upsertChannel(message: HtsMessage) {
        val id = message.long("channelId") ?: return
        val old = _state.value.channels[id]
        val currentEventId = if (message.value("eventId") != null) {
            message.long("eventId")?.takeIf { it > 0L }
        } else {
            old?.currentEventId
        }
        val channel = TvChannel(
            id = id,
            number = message.long("channelNumber") ?: old?.number ?: 0,
            minorNumber = message.long("channelNumberMinor") ?: old?.minorNumber ?: 0,
            name = message.text("channelName") ?: old?.name ?: "Channel $id",
            iconPath = message.text("channelIcon") ?: old?.iconPath,
            currentEventId = currentEventId,
            tagIds = message.list("tags")?.mapNotNull { (it as? HtsValue.Int64)?.value } ?: old?.tagIds.orEmpty(),
        )
        _state.value = _state.value.copy(channels = _state.value.channels + (id to channel))
    }

    private fun upsertTag(message: HtsMessage) {
        val id = message.long("tagId") ?: return
        val old = _state.value.tags[id]
        val tag = TvTag(
            id = id,
            name = message.text("tagName") ?: old?.name ?: "Group $id",
            index = message.long("tagIndex") ?: old?.index ?: 0,
        )
        _state.value = _state.value.copy(tags = _state.value.tags + (id to tag))
    }

    private fun upsertEvent(message: HtsMessage) {
        val id = message.long("eventId") ?: return
        val old = _state.value.events[id]
        val event = TvEvent(
            id = id,
            channelId = message.long("channelId") ?: old?.channelId ?: 0,
            title = message.text("title") ?: old?.title ?: "Untitled programme",
            subtitle = message.text("subtitle") ?: old?.subtitle,
            summary = message.text("summary") ?: old?.summary,
            description = message.text("description") ?: old?.description,
            startEpochSeconds = message.long("start") ?: old?.startEpochSeconds ?: 0,
            stopEpochSeconds = message.long("stop") ?: old?.stopEpochSeconds ?: 0,
            isNew = (message.long("isNew") ?: 0) != 0L || message.text("isNew") == "1",
        )
        _state.value = _state.value.copy(events = _state.value.events + (id to event))
    }
}
