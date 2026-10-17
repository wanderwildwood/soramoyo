package com.wanderwildwood.soramoyo.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * A list that turns a page to a swipe, and turns it on whole rows.
 *
 * MMD's list steps four items to a swipe and lets the list clamp at its end, so its last page
 * lines up with the bottom and the row at the top sits half under the bar; four tall rows can
 * also step past one that was never fully seen. Here a swipe up brings the first row that did
 * not fit to the top, a swipe down is the same backwards, and a row that does not fit at the
 * foot is not drawn until it is the next page's first. The last page keeps its rows whole and
 * leaves the space under them blank. No scrollbar, as everywhere in the house apps.
 */
@Composable
internal fun PagedColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val surface = MaterialTheme.colorScheme.surface
    BoxWithConstraints(modifier) {
        // Room after the last row for it to come to the top, so the list never has to line
        // its end up with the bottom of the screen.
        val tail = maxHeight
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state) {
                    var turned = false
                    detectVerticalDragGestures(
                        onDragStart = { turned = false },
                        onDragEnd = { turned = false },
                        onDragCancel = { turned = false },
                    ) { _, drag ->
                        if (turned || drag == 0f) return@detectVerticalDragGestures
                        turned = true
                        scope.launch { if (drag < 0) state.pageForward() else state.pageBack() }
                    }
                }
                .drawWithContent {
                    drawContent()
                    state.cutAt()?.let { y ->
                        drawRect(surface, topLeft = Offset(0f, y), size = Size(size.width, size.height - y))
                    }
                },
            state = state,
            // The list turns its own pages.
            userScrollEnabled = false,
        ) {
            content()
            item(key = TAIL) { Spacer(Modifier.height(tail)) }
        }
    }
}

private const val TAIL = "paged-column-tail"

/** The rows on screen, without the blank room after the last. */
private fun LazyListState.rows(): List<LazyListItemInfo> =
    layoutInfo.visibleItemsInfo.filter { it.key != TAIL }

/** Where the first row that runs off the foot starts, while a whole row sits above it. */
internal fun LazyListState.cutAt(): Float? {
    val bottom = layoutInfo.viewportSize.height
    val rows = rows()
    val cut = rows.firstOrNull { it.offset + it.size > bottom } ?: return null
    return if (cut.offset > 0) cut.offset.toFloat() else null
}

/** The first row that did not fit, brought to the top. */
internal suspend fun LazyListState.pageForward() {
    val bottom = layoutInfo.viewportSize.height
    val rows = rows()
    if (rows.isEmpty()) return
    val cut = rows.firstOrNull { it.offset + it.size > bottom }
    when {
        // Everything left fitted: this is the last page.
        cut == null -> return
        // One row taller than the screen: through it a screen at a time.
        cut.offset <= 0 -> scrollBy(bottom.toFloat())
        else -> scrollToItem(cut.index)
    }
}

/**
 * Back a page: the row before the top one comes to rest whole at the foot, with as many whole
 * rows above it as fit.
 */
internal suspend fun LazyListState.pageBack() {
    if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) return
    val from = firstVisibleItemIndex
    scrollBy(-layoutInfo.viewportSize.height.toFloat())
    // A row cut by the top edge waits for the page before; unless it is the one row being
    // gone back to, taller than the screen.
    if (firstVisibleItemScrollOffset > 0 && firstVisibleItemIndex + 1 < from) {
        scrollToItem(firstVisibleItemIndex + 1)
    }
}
