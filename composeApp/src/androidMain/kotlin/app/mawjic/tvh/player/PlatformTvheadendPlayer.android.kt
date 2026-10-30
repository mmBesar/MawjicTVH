package app.mawjic.tvh.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
actual fun PlatformTvheadendPlayer(
    streamUrl: String,
    title: String,
    modifier: Modifier,
    onPlaybackError: (String) -> Unit,
) {
    val context = LocalContext.current
    val latestErrorCallback = rememberUpdatedState(onPlaybackError)
    val player = remember(context, streamUrl) { ExoPlayer.Builder(context).build() }

    DisposableEffect(player, streamUrl) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Do not expose the ticket-bearing stream URL in UI error messages or logs.
                val message = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Could not reach the Tvheadend streaming service. Check the host, streaming port and network."
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                        "Tvheadend refused or could not find this channel stream. Check channel and streaming permissions."
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                        "Android cannot decode this channel's selected audio/video format. Check the Tvheadend streaming profile."
                    else -> "Playback failed (${error.errorCodeName}). Check the server stream and codec compatibility."
                }
                latestErrorCallback.value(message)
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.playWhenReady = true
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setControllerAutoShow(true)
                }
            },
            update = { it.player = player },
        )
    }
}
