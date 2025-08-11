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

/**
 * Makes an element draggable with immediate response to drag gestures.
 * 
 * @param key Unique key for this draggable modifier
 * @param enabled Whether dragging is enabled
 * @param interactionSource Optional source for tracking drag interactions
 * @param onDragStarted Callback when drag starts with the start position
 * @param onDragStopped Callback when drag stops
 * @param onDrag Callback for each drag movement with the change and delta
 */
internal fun Modifier.draggable(
    key: Any?,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onDragStarted: (Offset) -> Unit = { },
    onDragStopped: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) = composed {
    val coroutineScope = rememberCoroutineScope()
    var currentDragInteraction by remember { mutableStateOf<DragInteraction.Start?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    // Clean up when the key changes or the composable leaves composition
    DisposableEffect(key) {
        onDispose {
            if (isDragging) {
                // Cancel any ongoing drag interaction
                currentDragInteraction?.let { interaction ->
                    coroutineScope.launch {
                        interactionSource?.emit(DragInteraction.Cancel(interaction))
                    }
                }
                
                // Notify that dragging has stopped
                onDragStopped()
                isDragging = false
            }
        }
    }

    pointerInput(key, enabled) {
        if (enabled) {
            detectDragGestures(
                onDragStart = { startPosition ->
                    isDragging = true
                    
                    // Start drag interaction
                    currentDragInteraction = DragInteraction.Start().also { interaction ->
                        coroutineScope.launch {
                            interactionSource?.emit(interaction)
                        }
                    }
                    
                    onDragStarted(startPosition)
                },
                onDragEnd = {
                    // End drag interaction
                    currentDragInteraction?.let { interaction ->
                        coroutineScope.launch {
                            interactionSource?.emit(DragInteraction.Stop(interaction))
                        }
                    }
                    
                    if (isDragging) {
                        onDragStopped()
                    }
                    
                    isDragging = false
                    currentDragInteraction = null
                },
                onDragCancel = {
                    // Cancel drag interaction
                    currentDragInteraction?.let { interaction ->
                        coroutineScope.launch {
                            interactionSource?.emit(DragInteraction.Cancel(interaction))
                        }
                    }
                    
                    if (isDragging) {
                        onDragStopped()
                    }
                    
                    isDragging = false
                    currentDragInteraction = null
                },
                onDrag = onDrag,
            )
        }
    }
}

/**
 * Makes an element draggable after a long press gesture.
 * 
 * @param key Unique key for this draggable modifier
 * @param enabled Whether dragging is enabled
 * @param interactionSource Optional source for tracking drag interactions
 * @param onDragStarted Callback when drag starts with the start position
 * @param onDragStopped Callback when drag stops
 * @param onDrag Callback for each drag movement with the change and delta
 */
internal fun Modifier.longPressDraggable(
    key: Any?,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onDragStarted: (Offset) -> Unit = { },
    onDragStopped: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) = composed {
    val coroutineScope = rememberCoroutineScope()
    var currentDragInteraction by remember { mutableStateOf<DragInteraction.Start?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    // Clean up when the key changes or the composable leaves composition
    DisposableEffect(key) {
        onDispose {
            if (isDragging) {
                // Cancel any ongoing drag interaction
                currentDragInteraction?.let { interaction ->
                    coroutineScope.launch {
                        interactionSource?.emit(DragInteraction.Cancel(interaction))
                    }
                }
                
                // Notify that dragging has stopped
                onDragStopped()
                isDragging = false
            }
        }
    }

    pointerInput(key, enabled) {
        if (enabled) {
            detectDragGesturesAfterLongPress(
                onDragStart = { startPosition ->
                    isDragging = true
                    
                    // Start drag interaction
                    currentDragInteraction = DragInteraction.Start().also { interaction ->
                        coroutineScope.launch {
                            interactionSource?.emit(interaction)
                        }
                    }
                    
                    onDragStarted(startPosition)
                },
                onDragEnd = {
                    // End drag interaction
                    currentDragInteraction?.let { interaction ->
                        coroutineScope.launch {
                            interactionSource?.emit(DragInteraction.Stop(interaction))
                        }
                    }
                    
                    if (isDragging) {
                        onDragStopped()
                    }
                    
                    isDragging = false
                    currentDragInteraction = null
                },
                onDragCancel = {
                    // Cancel drag interaction
                    currentDragInteraction?.let { interaction ->
                        coroutineScope.launch {
                            interactionSource?.emit(DragInteraction.Cancel(interaction))
                        }
                    }
                    
                    if (isDragging) {
                        onDragStopped()
                    }
                    
                    isDragging = false
                    currentDragInteraction = null
                },
                onDrag = onDrag,
            )
        }
    }
}
