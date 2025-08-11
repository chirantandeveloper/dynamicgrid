package com.dynamicgrid.grid.dragableGridComposable

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Extension functions for geometric calculations and transformations.
 * These utilities help with orientation-aware operations on offsets and sizes.
 */

/**
 * Gets the value along the specified axis (x for horizontal, y for vertical).
 * 
 * @param orientation The axis orientation
 * @return The coordinate value along the specified axis
 */
internal fun Offset.getAxis(orientation: Orientation): Float = when (orientation) {
    Orientation.Vertical -> y
    Orientation.Horizontal -> x
}

/**
 * Gets the value along the specified axis (x for horizontal, y for vertical).
 * 
 * @param orientation The axis orientation
 * @return The coordinate value along the specified axis
 */
internal fun IntOffset.getAxis(orientation: Orientation): Int = when (orientation) {
    Orientation.Vertical -> y
    Orientation.Horizontal -> x
}

/**
 * Reverses the sign of the coordinate along the specified axis.
 * 
 * @param orientation The axis to reverse
 * @return New offset with the specified axis reversed
 */
internal fun Offset.reverseAxis(orientation: Orientation): Offset =
    when (orientation) {
        Orientation.Vertical -> Offset(x, -y)
        Orientation.Horizontal -> Offset(-x, y)
    }

/**
 * Creates an IntOffset with a value along the specified axis.
 * 
 * @param orientation The axis to set the value on
 * @param value The value to set
 * @return IntOffset with the value set on the specified axis, 0 on the other
 */
internal fun IntOffset.Companion.fromAxis(orientation: Orientation, value: Int): IntOffset =
    when (orientation) {
        Orientation.Vertical -> IntOffset(0, value)
        Orientation.Horizontal -> IntOffset(value, 0)
    }

/**
 * Gets the dimension along the specified axis (width for horizontal, height for vertical).
 * 
 * @param orientation The axis orientation
 * @return The dimension along the specified axis
 */
internal fun IntSize.getAxis(orientation: Orientation): Int = when (orientation) {
    Orientation.Vertical -> height
    Orientation.Horizontal -> width
}

/**
 * Adds a Size to an Offset, treating size dimensions as offset deltas.
 * 
 * @param size The size to add
 * @return New offset with size dimensions added
 */
internal operator fun Offset.plus(size: Size): Offset = 
    Offset(x + size.width, y + size.height)

/**
 * Adds an IntSize to an IntOffset, treating size dimensions as offset deltas.
 * 
 * @param size The size to add
 * @return New offset with size dimensions added
 */
internal operator fun IntOffset.plus(size: IntSize): IntOffset = 
    IntOffset(x + size.width, y + size.height)

/**
 * Subtracts an IntSize from an IntOffset, treating size dimensions as offset deltas.
 * 
 * @param size The size to subtract
 * @return New offset with size dimensions subtracted
 */
internal operator fun IntOffset.minus(size: IntSize): IntOffset = 
    IntOffset(x - size.width, y - size.height)
