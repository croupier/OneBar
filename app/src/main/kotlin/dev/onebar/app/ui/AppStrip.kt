package dev.onebar.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.onebar.app.data.LaunchableApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

private const val APP_STRIP_ITEMS = 4
private const val FIRST_AUTO_SCROLL_MS = 3_000L
private const val IDLE_AUTO_SCROLL_MS = 5_000L
private val AutoScrollDistance = 40.dp

/** Однострочный список: четыре плитки по границам контактов и мягкое смещение после простоя. */
@Composable
fun AppAutoStrip(
    apps: List<LaunchableApp>,
    favoritePackages: Set<String>,
    onOpen: (LaunchableApp) -> Unit,
    onMenu: (LaunchableApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) return
    val rowState = rememberLazyListState()
    val interactions = remember { Channel<Unit>(Channel.CONFLATED) }
    val distance = with(LocalDensity.current) { AutoScrollDistance.toPx() }

    LaunchedEffect(rowState, distance) {
        var first = true
        while (isActive) {
            val interrupted = withTimeoutOrNull(
                if (first) FIRST_AUTO_SCROLL_MS else IDLE_AUTO_SCROLL_MS,
            ) { interactions.receive() }
            if (interrupted != null) {
                first = false
                continue
            }
            try {
                rowState.animateScrollBy(distance, tween(450, easing = FastOutSlowInEasing))
                currentCoroutineContext().ensureActive()
                rowState.animateScrollBy(-distance, tween(450, easing = FastOutSlowInEasing))
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
            }
            first = false
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = (
            (maxWidth - AppRowStart - AppRowEndInset - AppItemWidth * APP_STRIP_ITEMS) /
                (APP_STRIP_ITEMS - 1)
            ).coerceAtLeast(0.dp)
        LazyRow(
            state = rowState,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        interactions.trySend(Unit)
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) {
                                interactions.trySend(Unit)
                                break
                            }
                        }
                    }
                }
                .testTag("apps-strip"),
            contentPadding = PaddingValues(start = AppRowStart, end = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppItem(
                    app = app,
                    favorite = app.packageName in favoritePackages,
                    onClick = { onOpen(app) },
                    onLongClick = { onMenu(app) },
                    modifier = Modifier.testTag("app-strip-slot-${app.packageName}"),
                )
            }
        }
    }
}
