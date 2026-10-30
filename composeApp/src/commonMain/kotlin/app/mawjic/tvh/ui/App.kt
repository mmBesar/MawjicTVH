package app.mawjic.tvh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.onDispose
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.mawjic.tvh.client.HtspClient
import app.mawjic.tvh.client.HtspServerInfo
import app.mawjic.tvh.model.TvChannel
import app.mawjic.tvh.model.TvheadendRepository
import app.mawjic.tvh.model.TvheadendSnapshot
import app.mawjic.tvh.playback.TvheadendHttpEndpoint
import app.mawjic.tvh.playback.TvheadendPlaybackService
import app.mawjic.tvh.player.PlatformTvheadendPlayer
import app.mawjic.tvh.protocol.createHtspTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val MawjicPalette = darkColorScheme(
    primary = Color(0xFF73C9F5),
    onPrimary = Color(0xFF082033),
    secondary = Color(0xFFA9A0F5),
    background = Color(0xFF090E17),
    surface = Color(0xFF131C2A),
    surfaceVariant = Color(0xFF1C2839),
    onSurface = Color(0xFFE9F0F8),
    onSurfaceVariant = Color(0xFFB0BECE),
    error = Color(0xFFFF8C8C),
)

@Composable
fun App() {
    MaterialTheme(colorScheme = MawjicPalette) {
        var host by remember { mutableStateOf("") }
        var htspPort by remember { mutableStateOf("9982") }
        var httpPort by remember { mutableStateOf("9981") }
        var useHttps by remember { mutableStateOf(false) }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var connecting by remember { mutableStateOf(false) }
        var requestingPlayback by remember { mutableStateOf(false) }
        var connectionError by remember { mutableStateOf<String?>(null) }
        var playbackError by remember { mutableStateOf<String?>(null) }
        var serverInfo by remember { mutableStateOf<HtspServerInfo?>(null) }
        var client by remember { mutableStateOf<HtspClient?>(null) }
        var repository by remember { mutableStateOf<TvheadendRepository?>(null) }
        var playbackService by remember { mutableStateOf<TvheadendPlaybackService?>(null) }
        var currentChannel by remember { mutableStateOf<TvChannel?>(null) }
        var streamUrl by remember { mutableStateOf<String?>(null) }
        var observer by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val snapshotFlow = remember(repository) { repository?.state ?: MutableStateFlow(TvheadendSnapshot()) }
        val snapshot by snapshotFlow.collectAsState()

        DisposableEffect(client) {
            onDispose { client?.close() }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).background(Color(0xFF1C3850), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("TVH", color = Color(0xFF91D9FA), fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("MawjicTVH", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Native Tvheadend client", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    when {
                        serverInfo == null -> ConnectionForm(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            host = host,
                            onHostChange = { host = it },
                            htspPort = htspPort,
                            onHtspPortChange = { htspPort = it.filter(Char::isDigit).take(5) },
                            httpPort = httpPort,
                            onHttpPortChange = { httpPort = it.filter(Char::isDigit).take(5) },
                            useHttps = useHttps,
                            onUseHttpsChange = { useHttps = it },
                            username = username,
                            onUsernameChange = { username = it },
                            password = password,
                            onPasswordChange = { password = it },
                            connecting = connecting,
                            error = connectionError,
                            onConnect = {
                                connectionError = null
                                val parsedHtspPort = htspPort.toIntOrNull()
                                val parsedHttpPort = httpPort.toIntOrNull()
                                when {
                                    host.isBlank() -> connectionError = "Enter the hostname or IP address of your Tvheadend server."
                                    host.contains("://") -> connectionError = "Enter the hostname or IP address only, without http:// or https://."
                                    parsedHtspPort == null || parsedHtspPort !in 1..65535 -> connectionError = "Enter a valid HTSP TCP port."
                                    parsedHttpPort == null || parsedHttpPort !in 1..65535 -> connectionError = "Enter a valid HTTP port."
                                    else -> {
                                        connecting = true
                                        scope.launch {
                                            val newClient = HtspClient(createHtspTransport())
                                            val newRepository = TvheadendRepository(newClient)
                                            val newObserver = newRepository.observe(scope)
                                            try {
                                                val info = newClient.connect(
                                                    host = host.trim(),
                                                    port = parsedHtspPort,
                                                    username = username.trim().ifBlank { null },
                                                    password = password.ifBlank { null },
                                                )
                                                client = newClient
                                                repository = newRepository
                                                playbackService = TvheadendPlaybackService(
                                                    newClient,
                                                    TvheadendHttpEndpoint(
                                                        host = host.trim(),
                                                        port = parsedHttpPort,
                                                        useHttps = useHttps,
                                                        webRoot = info.webRoot,
                                                    ),
                                                )
                                                observer = newObserver
                                                serverInfo = info
                                            } catch (error: Exception) {
                                                newObserver.cancel()
                                                newClient.close()
                                                connectionError = error.message ?: "Connection failed."
                                            } finally {
                                                connecting = false
                                            }
                                        }
                                    }
                                }
                            },
                        )

                        streamUrl != null && currentChannel != null -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(currentChannel!!.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    Text("Live from ${serverInfo!!.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(onClick = {
                                    streamUrl = null
                                    currentChannel = null
                                    playbackError = null
                                }) { Text("Back to channels") }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (playbackError != null) {
                                Text(playbackError!!, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                            }
                            PlatformTvheadendPlayer(
                                streamUrl = streamUrl!!,
                                title = currentChannel!!.name,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                onPlaybackError = { playbackError = it },
                            )
                        }

                        else -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(serverInfo!!.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${serverInfo!!.version} · HTSP ${serverInfo!!.protocolVersion}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(onClick = {
                                    streamUrl = null
                                    currentChannel = null
                                    playbackService = null
                                    client?.close()
                                    observer?.cancel()
                                    observer = null
                                    client = null
                                    repository = null
                                    serverInfo = null
                                    playbackError = null
                                }) { Text("Disconnect") }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(Color(0xFF70D6A2), CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (snapshot.initialSyncCompleted) "${snapshot.channels.size} channels · ${snapshot.tags.size} groups · ${snapshot.events.size} EPG events"
                                    else "Connected · receiving server metadata…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (playbackError != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(playbackError!!, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.height(8.dp))
                            when {
                                !snapshot.initialSyncCompleted -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(14.dp))
                                        Text("Loading channels and guide from Tvheadend…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                snapshot.channels.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    Text("The server connected, but it did not provide any accessible channels.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                else -> ChannelList(
                                    snapshot = snapshot,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                    requestingPlayback = requestingPlayback,
                                    onSelect = { channel ->
                                        if (!requestingPlayback) {
                                            requestingPlayback = true
                                            playbackError = null
                                            scope.launch {
                                                try {
                                                    val url = playbackService?.createChannelStreamUrl(channel.id)
                                                        ?: error("Playback is not connected to the Tvheadend server.")
                                                    currentChannel = channel
                                                    streamUrl = url
                                                } catch (error: Exception) {
                                                    playbackError = error.message ?: "Could not request this channel's stream."
                                                } finally {
                                                    requestingPlayback = false
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                            if (requestingPlayback) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Requesting a playback ticket…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("MawjicTVH · HTSP control · ticketed Tvheadend streams", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ConnectionForm(
    modifier: Modifier,
    host: String,
    onHostChange: (String) -> Unit,
    htspPort: String,
    onHtspPortChange: (String) -> Unit,
    httpPort: String,
    onHttpPortChange: (String) -> Unit,
    useHttps: Boolean,
    onUseHttpsChange: (Boolean) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    connecting: Boolean,
    error: String?,
    onConnect: () -> Unit,
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
                Text("Connect to your server", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Channels and authorization use Tvheadend's native HTSP connection. Playback uses a temporary ticket issued by that same server.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = host, onValueChange = onHostChange,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Server address") }, placeholder = { Text("tvheadend.home") },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = htspPort, onValueChange = onHtspPortChange,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("HTSP TCP port") }, placeholder = { Text("9982") },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = httpPort, onValueChange = onHttpPortChange,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Streaming port (HTTP/HTTPS)") }, placeholder = { Text("9981") },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use HTTPS for playback")
                        Text("Use the HTTPS port configured on Tvheadend or your reverse proxy.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = useHttps, onCheckedChange = onUseHttpsChange)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = username, onValueChange = onUsernameChange,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Username (optional)") },
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password, onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Password (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onConnect,
                    enabled = !connecting,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    if (connecting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (connecting) "Connecting…" else "Connect")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "HTSP itself is not encrypted. Use a trusted LAN or VPN for the HTSP connection. For playback over HTTPS, enter the HTTPS listener or reverse-proxy port. The account needs permission to stream the selected channel.",
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ChannelList(
    snapshot: TvheadendSnapshot,
    modifier: Modifier = Modifier,
    requestingPlayback: Boolean,
    onSelect: (TvChannel) -> Unit,
) {
    val sortedChannels = snapshot.channels.values.sortedWith(compareBy<TvChannel> { it.number }.thenBy { it.name.lowercase() })
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(9.dp),
        contentPadding = PaddingValues(bottom = 10.dp),
    ) {
        item {
            Text("Tap a channel to watch", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        }
        items(sortedChannels, key = { it.id }) { channel ->
            val event = channel.currentEventId?.let { snapshot.events[it] }
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = !requestingPlayback) { onSelect(channel) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (channel.number > 0) channel.number.toString() else "TV", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(channel.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text(
                            event?.let { listOfNotNull(it.title, it.subtitle).joinToString(" · ") }
                                ?: if (channel.currentEventId != null) "EPG event ${channel.currentEventId}" else "No current programme information",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("▶", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
