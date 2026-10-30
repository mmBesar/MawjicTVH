package app.mawjic.tvh.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Platform decoder: Media3 on Android, mpv on Linux. */
@Composable
expect fun PlatformTvheadendPlayer(
    streamUrl: String,
    title: String,
    modifier: Modifier,
    onPlaybackError: (String) -> Unit,
)
