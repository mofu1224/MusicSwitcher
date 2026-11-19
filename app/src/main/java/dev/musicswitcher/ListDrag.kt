package dev.musicswitcher

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal class ListDrag(val list: LazyListState) {
    var handleDown: String? = null
    var source by mutableStateOf<String?>(null)
    var target by mutableStateOf<String?>(null)
    var y by mutableFloatStateOf(0f)
    fun hit(): String? = list.layoutInfo.let { layout ->
        val pos = y + layout.viewportStartOffset
        layout.visibleItemsInfo.firstOrNull { pos >= it.offset && pos < it.offset + it.size }?.key as? String
    }
    fun clear() { source = null; target = null }
}

@Composable
internal fun rememberListDrag(list: LazyListState): ListDrag {
    val drag = remember(list) { ListDrag(list) }
    LaunchedEffect(drag.source) {
        if (drag.source == null) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            val height = list.layoutInfo.viewportSize.height
            val edge = height * .12f
            val delta = when {
                drag.y < edge -> (drag.y - edge) * .18f
                drag.y > height - edge -> (drag.y - height + edge) * .18f
                else -> 0f
            }
            if (delta != 0f && list.scrollBy(delta) != 0f) drag.hit()?.let { drag.target = it }
        }
    }
    return drag
}

@Composable
internal fun Modifier.dragList(drag: ListDrag, move: (String, String) -> Unit, actions: (String) -> Unit): Modifier {
    val latest by rememberUpdatedState(move)
    val open by rememberUpdatedState(actions)
    val view = LocalView.current
    return pointerInput(drag, view) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val handle = drag.handleDown
            drag.handleDown = null
            drag.y = down.position.y
            val source = drag.hit() ?: return@awaitEachGesture
            if (source != handle) {
                val systemDelay = viewConfiguration.longPressTimeoutMillis
                val delay = if (systemDelay <= 500) systemDelay * 4 / 5 else systemDelay
                val cancelled = withTimeoutOrNull(delay) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val p = event.changes.singleOrNull() ?: break
                        if (p.id != down.id || !p.pressed || p.isConsumed) break
                        val delta = p.position - down.position
                        if (delta.getDistance() > viewConfiguration.touchSlop) {
                            if (kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y)) p.consume()
                            break
                        }
                    }
                    true
                }
                if (cancelled == null && drag.hit() == source) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    open(source)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
                return@awaitEachGesture
            }
            try {
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.singleOrNull() ?: break
                    if (change.isConsumed) break
                    drag.y = change.position.y
                    if (kotlin.math.abs(drag.y - down.position.y) > viewConfiguration.touchSlop) drag.source = source
                    if (drag.source != null) drag.hit()?.let { drag.target = it }
                    change.consume()
                    if (!change.pressed) {
                        drag.target?.takeIf { it != source }?.let { latest(source, it) }
                        break
                    }
                }
            } finally {
                drag.clear()
            }
        }
    }
}

@Composable
internal fun ReorderHandle(drag: ListDrag, id: String) {
    Box(Modifier.size(48.dp).pointerInput(drag, id) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            drag.handleDown = id
        }
    }, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text("≡", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun Modifier.dragRow(id: String, previous: String?, next: String?, drag: ListDrag,
                             move: (String, String) -> Unit, label: String, action: () -> Unit): Modifier {
    val ink = MaterialTheme.colorScheme.onSurface
    val open by rememberUpdatedState(action)
    return this.background(if (drag.source == id) ink.copy(alpha = .14f) else androidx.compose.ui.graphics.Color.Transparent)
        .drawBehind {
            if (drag.target == id && drag.source != id) {
                val sourceIndex = drag.list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == drag.source }?.index
                val destIndex = drag.list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.index
                val y = if (sourceIndex != null && destIndex != null && sourceIndex < destIndex) size.height else 0f
                drawLine(ink, Offset(0f, y), Offset(size.width, y), 3.dp.toPx())
            }
        }.semantics {
            customActions = listOfNotNull(previous?.let { CustomAccessibilityAction("Move up") { move(id, it); true } },
                next?.let { CustomAccessibilityAction("Move down") { move(id, it); true } },
                CustomAccessibilityAction(label) { open(); true })
        }
}
