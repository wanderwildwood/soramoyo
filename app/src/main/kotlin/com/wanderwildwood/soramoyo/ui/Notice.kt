package com.wanderwildwood.soramoyo.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD

/**
 * A short thing the app has to say (a backup saved, a paste with nothing in it), along the foot
 * of whatever screen is up. It stays until the next press anywhere in the app: on this panel a
 * toast or a message on a timer could be gone before it had been read.
 */
object Notice {
    var text by mutableStateOf<String?>(null)
        private set

    /**
     * Presses begun so far. A line said in place (a pull's answer, "asked") keeps the count it
     * was said at and stays while that is still the count: until the next finger comes down.
     */
    var presses by mutableIntStateOf(0)
        private set

    fun say(message: String) {
        text = message
    }

    fun say(context: Context, @StringRes id: Int) = say(context.getString(id))

    internal fun down() {
        presses++
    }

    internal fun pressed() {
        text = null
    }
}

/**
 * The app's screens, with [Notice] along the foot under them. Every press anywhere takes the
 * notice down as it ends, before the press does its own thing, so a press that says something
 * new leaves the new thing said.
 */
@Composable
fun NoticeFrame(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().onEachPress(onDown = Notice::down) { Notice.pressed() }) {
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        Notice.text?.let { said ->
            Column(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding(),
            ) {
                HorizontalDividerMMD()
                TextMMD(
                    text = said,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notice")
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * Calls [onDown] as each press anywhere on this layout begins and [onPress] as it ends (a tap, a
 * scroll, a hold), each before the press does anything itself. Nothing is consumed. Both are
 * read once: they should only write state.
 */
fun Modifier.onEachPress(onDown: () -> Unit = {}, onPress: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onDown()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
        } while (event.changes.any { it.pressed })
        onPress()
    }
}
