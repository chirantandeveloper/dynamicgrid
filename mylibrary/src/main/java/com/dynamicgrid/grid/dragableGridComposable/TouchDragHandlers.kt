
package com.dynamicgrid.grid.dragableGridComposable

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

private enum class StartMode { Instant, Hold }

private fun Modifier.attachDragInternal(
    mode: StartMode,
    key: Any?,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    onStart: (Offset) -> Unit,
    onStop: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit
) = composed {
    val scope = rememberCoroutineScope()
    var startInteraction by remember { mutableStateOf<DragInteraction.Start?>(null) }
    var dragging by remember { mutableStateOf(false) }

    DisposableEffect(key) {
        onDispose {
            if (dragging) {
                startInteraction?.let { s ->
                    scope.launch { interactionSource?.emit(DragInteraction.Cancel(s)) }
                }
                onStop()
                dragging = false
            }
        }
    }

    pointerInput(key, enabled) {
        if (!enabled) return@pointerInput
        val start: (Offset) -> Unit = { pos ->
            dragging = true
            startInteraction = DragInteraction.Start().also { s ->
                scope.launch { interactionSource?.emit(s) }
            }
            onStart(pos)
        }
        val end: () -> Unit = {
            startInteraction?.let { s -> scope.launch { interactionSource?.emit(DragInteraction.Stop(s)) } }
            if (dragging) onStop()
            dragging = false
            startInteraction = null
        }
        val cancel: () -> Unit = {
            startInteraction?.let { s -> scope.launch { interactionSource?.emit(DragInteraction.Cancel(s)) } }
            if (dragging) onStop()
            dragging = false
            startInteraction = null
        }

        when (mode) {
            StartMode.Instant -> detectDragGestures(
                onDragStart = start, onDragEnd = end, onDragCancel = cancel, onDrag = onDrag
            )
            StartMode.Hold -> detectDragGesturesAfterLongPress(
                onDragStart = start, onDragEnd = end, onDragCancel = cancel, onDrag = onDrag
            )
        }
    }
}

internal fun Modifier.attachInstantDrag(
    key: Any?,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onStart: (Offset) -> Unit = {},
    onStop: () -> Unit = {},
    onDrag: (PointerInputChange, Offset) -> Unit
) = attachDragInternal(StartMode.Instant, key, enabled, interactionSource, onStart, onStop, onDrag)

internal fun Modifier.attachHoldDrag(
    key: Any?,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onStart: (Offset) -> Unit = {},
    onStop: () -> Unit = {},
    onDrag: (PointerInputChange, Offset) -> Unit
) = attachDragInternal(StartMode.Hold, key, enabled, interactionSource, onStart, onStop, onDrag)
