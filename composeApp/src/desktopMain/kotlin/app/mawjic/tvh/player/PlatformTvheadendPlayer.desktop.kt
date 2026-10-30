package app.mawjic.tvh.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
actual fun PlatformTvheadendPlayer(
    streamUrl: String,
    title: String,
    modifier: Modifier,
    onPlaybackError: (String) -> Unit,
) {
    var process by remember(streamUrl) { mutableStateOf<Process?>(null) }
    var status by remember(streamUrl) { mutableStateOf("Starting mpv…") }

    LaunchedEffect(streamUrl, title) {
        try {
            val launched = withContext(Dispatchers.IO) {
                ProcessBuilder(
                    "mpv",
                    "--force-window=yes",
                    "--title=MawjicTVH — $title",
                    "--",
                    streamUrl,
                ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            }
            process = launched
            status = "mpv opened the stream. Use mpv's window for playback controls."
            val exitCode = withContext(Dispatchers.IO) { launched.waitFor() }
            if (process === launched) {
                process = null
                status = if (exitCode == 0) {
                    "Playback ended."
                } else {
                    "mpv could not play this stream (exit status $exitCode). Check the Tvheadend streaming profile and mpv codec support."
                }
                if (exitCode != 0) onPlaybackError(status)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            val message = if (error.message?.contains("error=2") == true) {
                "mpv is not installed. Install it with your Linux distribution’s package manager, then try again."
            } else {
                error.message ?: "Could not start mpv."
            }
            status = message
            onPlaybackError(message)
        } catch (error: Exception) {
            val message = error.message ?: "Could not start Linux playback."
            status = message
            onPlaybackError(message)
        }
    }

    DisposableEffect(streamUrl) {
        onDispose {
            process?.let { running ->
                if (running.isAlive) running.destroy()
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Linux playback uses the system mpv player. The stream URL is a temporary Tvheadend ticket, not a playlist URL.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
