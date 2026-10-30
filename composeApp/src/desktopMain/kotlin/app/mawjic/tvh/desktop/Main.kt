package app.mawjic.tvh.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.mawjic.tvh.ui.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MawjicTVH · Tvheadend",
    ) {
        App()
    }
}
