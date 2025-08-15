package com.dynamicgrid.grid.dragableGridComposable

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

// Orientation-aware geometry helpers. The API is unchanged; implementation order and style differ.

internal fun Offset.getAxis(orientation: Orientation): Float =
    if (orientation == Orientation.Vertical) y else x

internal fun IntOffset.getAxis(orientation: Orientation): Int =
    if (orientation == Orientation.Vertical) y else x

internal fun Offset.reverseAxis(orientation: Orientation): Offset = when (orientation) {
    Orientation.Vertical -> copy(y = -y)
    Orientation.Horizontal -> copy(x = -x)
}

internal fun IntOffset.Companion.fromAxis(orientation: Orientation, value: Int): IntOffset =
    if (orientation == Orientation.Vertical) IntOffset(0, value) else IntOffset(value, 0)

internal fun IntSize.getAxis(orientation: Orientation): Int =
    if (orientation == Orientation.Vertical) height else width

internal operator fun Offset.plus(size: Size): Offset = Offset(x + size.width, y + size.height)

internal operator fun IntOffset.plus(size: IntSize): IntOffset = IntOffset(x + size.width, y + size.height)

internal operator fun IntOffset.minus(size: IntSize): IntOffset = IntOffset(x - size.width, y - size.height)
