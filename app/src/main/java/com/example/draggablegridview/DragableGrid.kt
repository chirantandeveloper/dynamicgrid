package com.example.draggablegridview

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.draggablegridview.data.Item

@Composable
fun DraggableGrid(items: List<Item>, onSwap: (Int, Int) -> Unit) {
    // State for tracking item positions
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggedOffset by remember { mutableStateOf(Offset.Zero) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3), // Change column count as needed
        modifier = Modifier.fillMaxSize()
    ) {
        items(items.size) { index ->
            val item = items[index]

            Box(
                Modifier
                    .size(100.dp)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                draggedItemIndex = index
                            },
                            onDragEnd = {
                                draggedItemIndex = null
                                draggedOffset = Offset.Zero
                            },
                            onDragCancel = {
                                draggedItemIndex = null
                                draggedOffset = Offset.Zero
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggedOffset += Offset(dragAmount.x, dragAmount.y)

                                // Check for swapping items if dragged near another item
                                val targetIndex = calculateTargetIndex(index, dragAmount)
                                if (targetIndex != null && targetIndex != index) {
                                    onSwap(index, targetIndex)
                                }
                            }
                        )
                    }
                    .zIndex(if (draggedItemIndex == index) 1f else 0f)
                    .offset { if (draggedItemIndex == index) draggedOffset.toIntOffset() else Offset.Zero.toIntOffset() }
            ) {
                BasicText(
                    text = item.text,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

fun calculateTargetIndex(draggedIndex: Int, dragAmount: Offset): Int? {
    // Logic to calculate new target index based on drag direction and position
    // Could be based on the column and row structure of the grid
    return null
}

fun Offset.toIntOffset() = androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt())

@Composable
fun DraggableGridDemo() {
    var items by remember { mutableStateOf((0..200).map {
        Item(it, "Item $it", it+1)
    }) }

    DraggableGrid(items = items, onSwap = { fromIndex, toIndex ->
        items = items.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    })
}
