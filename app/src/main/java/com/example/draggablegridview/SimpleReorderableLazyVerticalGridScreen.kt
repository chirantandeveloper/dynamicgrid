package com.example.draggablegridview

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dynamicgrid.grid.dragableGridComposable.ReorderableItem
import com.dynamicgrid.grid.dragableGridComposable.rememberDraggableGridState
import com.example.draggablegridview.data.Item

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimpleReorderableLazyVerticalGridScreen(items : List<Item>, onMove : (Int, Int) -> Unit) {

    var list by remember { mutableStateOf(items) }
    val lazyGridState = rememberLazyGridState()
    val reorderableLazyGridState = rememberDraggableGridState(lazyGridState) { from, to ->
        list = list.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onMove(to.index, from.index)


    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        state = lazyGridState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(list, key = { _, item -> item.id }) { index, item ->
            ReorderableItem(reorderableLazyGridState, item.id) {
                Card(
                    onClick = {
                        Log.e("TAG", "SimpleReorderableLazyVerticalGridScreen: $index  ${item.text}  $item", )
                    },
                    modifier = Modifier
                        .height(96.dp)
                        .longPressDraggableHandle(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            item.text,
                            Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
