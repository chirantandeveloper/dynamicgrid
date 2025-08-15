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

private enum class DragStartMode { Immediate, AfterLongPress }

private fun Modifier.configureDrag(
    mode: DragStartMode,
    key: Any?,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    onDragStarted: (Offset) -> Unit,
    onDragStopped: () -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    var startInteraction by remember { mutableStateOf<DragInteraction.Start?>(null) }
    var dragging by remember { mutableStateOf(false) }

    // Ensure we tidy up interactions on disposal or key change
    DisposableEffect(key) {
        onDispose {
            if (dragging) {
                startInteraction?.let { s ->
                    scope.launch { interactionSource?.emit(DragInteraction.Cancel(s)) }
                }
                onDragStopped()
                dragging = false
            }
        }
    }

    pointerInput(key, enabled) {
        if (!enabled) return@pointerInput
        val commonStart: (Offset) -> Unit = { pos ->
            dragging = true
            startInteraction = DragInteraction.Start().also { s ->
                scope.launch { interactionSource?.emit(s) }
            }
            onDragStarted(pos)
        }
        val commonEnd: () -> Unit = {
            startInteraction?.let { s -> scope.launch { interactionSource?.emit(DragInteraction.Stop(s)) } }
            if (dragging) onDragStopped()
            dragging = false
            startInteraction = null
        }
        val commonCancel: () -> Unit = {
            startInteraction?.let { s -> scope.launch { interactionSource?.emit(DragInteraction.Cancel(s)) } }
            if (dragging) onDragStopped()
            dragging = false
            startInteraction = null
        }
        when (mode) {
            DragStartMode.Immediate ->
                detectDragGestures(
                    onDragStart = commonStart,
                    onDragEnd = commonEnd,
                    onDragCancel = commonCancel,
                    onDrag = onDrag,
                )
            DragStartMode.AfterLongPress ->
                detectDragGesturesAfterLongPress(
                    onDragStart = commonStart,
                    onDragEnd = commonEnd,
                    onDragCancel = commonCancel,
                    onDrag = onDrag,
                )
        }
    }
}

/**
 * Immediate drag handler.
 */
internal fun Modifier.draggable(
    key: Any?,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onDragStarted: (Offset) -> Unit = { },
    onDragStopped: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) = configureDrag(
    mode = DragStartMode.Immediate,
    key = key,
    enabled = enabled,
    interactionSource = interactionSource,
    onDragStarted = onDragStarted,
    onDragStopped = onDragStopped,
    onDrag = onDrag,
)

/**
 * Long-press to start dragging.
 */
internal fun Modifier.longPressDraggable(
    key: Any?,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onDragStarted: (Offset) -> Unit = { },
    onDragStopped: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) = configureDrag(
    mode = DragStartMode.AfterLongPress,
    key = key,
    enabled = enabled,
    interactionSource = interactionSource,
    onDragStarted = onDragStarted,
    onDragStopped = onDragStopped,
    onDrag = onDrag,
)
