package com.example.draggablegridview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicgrid.grid.dragableGridComposable.DragEnabledGridItem
import com.dynamicgrid.grid.dragableGridComposable.rememberGridReorderManager
import com.example.draggablegridview.data.Item

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SampleGrid(
    items: SnapshotStateList<Item>,
    gridState: LazyGridState = rememberLazyGridState(),
    onclick:(Int, Item)->Unit
) {
    val manager = rememberGridReorderManager(
        gridState = gridState,
        onMove = { from, to ->
            val fromIdx = from.index
            val toIdx = to.index
            if (fromIdx in items.indices && toIdx in items.indices) {
                items.add(toIdx, items.removeAt(fromIdx))
            }
        }
    )

    LazyVerticalGrid(columns = GridCells.Fixed(4)
        , state = gridState,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            count = items.size,
            key = { idx -> items[idx].id } // must be stable
        ) { index ->
            DragEnabledGridItem(manager = manager, key = items[index].id) { isDragging ->
                // Inside here you can attach a handle:
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(color = Color.Red, shape = RoundedCornerShape(12.dp))
                        .border(width = 1.dp, color = Color.Black, shape = RoundedCornerShape(12.dp))
                        .padding(8.dp)
                        .dragHandle()
                        .clickable {
                            onclick(index, items[index])
                        }
                ) {
                    Text(text = "item ${items[index].id}", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(
                        Alignment.Center) )
                }
            }
        }
    }
}
