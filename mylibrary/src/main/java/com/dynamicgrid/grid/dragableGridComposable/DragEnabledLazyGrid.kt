
package com.dynamicgrid.grid.dragableGridComposable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dynamicgrid.grid.dragableGridComposable.DragDropCollectionCell
import com.dynamicgrid.grid.dragableGridComposable.DragDropDefaults
import com.dynamicgrid.grid.dragableGridComposable.DragDropItemScope
import com.dynamicgrid.grid.dragableGridComposable.DragDropLazyState
import com.dynamicgrid.grid.dragableGridComposable.LazyCollectionItemInfo
import com.dynamicgrid.grid.dragableGridComposable.LazyCollectionLayoutInfo
import com.dynamicgrid.grid.dragableGridComposable.LazyCollectionState
import com.dynamicgrid.grid.dragableGridComposable.ScrollPadding
import kotlinx.coroutines.CoroutineScope


private val LazyGridLayoutInfo.mainAxisViewportSize: Int
    get() = if (orientation == Orientation.Vertical) viewportSize.height else viewportSize.width


@Composable
fun rememberGridReorderManager(
    gridState: LazyGridState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    edgeScrollThreshold: Dp = DragDropDefaults.EdgeTriggerDistance,
    autoScrollEngine: EdgeAutoScrollEngine = createAutoScrollEngine(
        scrollable = gridState,
        pixelsPerTickProvider = { gridState.layoutInfo.mainAxisViewportSize * 0.05f }
    ),
    onMove: suspend CoroutineScope.(from: LazyGridItemInfo, to: LazyGridItemInfo) -> Unit
): GridReorderManager {
    val density = LocalDensity.current
    val thresholdPx = with(density) { edgeScrollThreshold.toPx() }
    val scope = rememberCoroutineScope()
    val moveState = rememberUpdatedState(onMove)
    val layoutDirection = LocalLayoutDirection.current

    val padding = with(density) {
        ScrollPadding(
            start = contentPadding.calculateStartPadding(layoutDirection).toPx(),
            end = contentPadding.calculateEndPadding(layoutDirection).toPx(),
            top = contentPadding.calculateTopPadding().toPx(),
            bottom = contentPadding.calculateBottomPadding().toPx(),
        )
    }

    return remember(gridState, scope, thresholdPx, padding, autoScrollEngine, layoutDirection) {
        GridReorderManager(
            gridState = gridState,
            scope = scope,
            onMoveState = moveState,
            edgeThresholdPx = thresholdPx,
            padding = padding,
            autoScroll = autoScrollEngine,
            layoutDirection = layoutDirection
        )
    }
}

/** Concrete state for LazyGrid */
@Stable
class GridReorderManager internal constructor(
    gridState: LazyGridState,
    scope: CoroutineScope,
    onMoveState: State<suspend CoroutineScope.(from: LazyGridItemInfo, to: LazyGridItemInfo) -> Unit>,
    edgeThresholdPx: Float,
    padding: ScrollPadding,
    autoScroll: EdgeAutoScrollEngine,
    layoutDirection: LayoutDirection
) : DragDropLazyState<LazyGridItemInfo>(
    state = gridState.toAdapter(),
    scope = scope,
    onMoveState = onMoveState as State<suspend CoroutineScope.(from: LazyGridItemInfo, to: LazyGridItemInfo) -> Unit>,
    edgeThresholdPx = edgeThresholdPx,
    padding = padding,
    autoScroll = autoScroll,
    rtl = layoutDirection
)


private fun LazyGridState.toAdapter() = object : LazyCollectionState<LazyGridItemInfo> {
    override val firstVisibleItemIndex: Int get() = this@toAdapter.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset: Int get() = this@toAdapter.firstVisibleItemScrollOffset
    override val layoutInfo: LazyCollectionLayoutInfo<LazyGridItemInfo>
        get() = this@toAdapter.layoutInfo.let { info ->
            object : LazyCollectionLayoutInfo<LazyGridItemInfo> {
                override val visibleItemsInfo: List<LazyCollectionItemInfo<LazyGridItemInfo>>
                    get() = info.visibleItemsInfo.map { item ->
                        object : LazyCollectionItemInfo<LazyGridItemInfo> {
                            override val index: Int get() = item.index
                            override val key: Any get() = item.key
                            override val offset: IntOffset get() = item.offset
                            override val size: IntSize get() = item.size
                            override val data: LazyGridItemInfo get() = item
                        }
                    }
                override val viewportSize: IntSize get() = info.viewportSize
                override val orientation: Orientation get() = info.orientation
                override val reverseLayout: Boolean get() = info.reverseLayout
                override val beforeContentPadding: Int get() = info.beforeContentPadding
            }
        }

    override suspend fun animateScrollBy(value: Float, animationSpec: androidx.compose.animation.core.AnimationSpec<Float>): Float =
        this@toAdapter.animateScrollBy(value, animationSpec)

    override suspend fun scrollToItem(index: Int, scrollOffset: Int) =
        this@toAdapter.scrollToItem(index, scrollOffset)
}

@ExperimentalFoundationApi
@Composable
fun LazyGridItemScope.DragEnabledGridItem(
    manager: GridReorderManager,
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable DragDropItemScope.(isDragging: Boolean) -> Unit
) {
    val isDragging by manager.isItemDragging(key)
    val isDropped by manager.isItemJustDropped(key)

    val offsetModifier = when {
        isDragging -> {
            Modifier
                .zIndex(1f)
                .graphicsLayer {
                    translationX = manager.draggingVisualOffset.x
                    translationY = manager.draggingVisualOffset.y
                }
        }
        isDropped -> {
            Modifier
                .zIndex(1f)
                .graphicsLayer {
                    translationX = manager.previousDraggingOffset.value.x
                    translationY = manager.previousDraggingOffset.value.y
                }
        }
        else -> Modifier.animateItem() // keep default placement animation
    }

    DragDropCollectionCell(
        state = manager,
        key = key,
        modifier = modifier.then(offsetModifier),
        enabled = enabled,
        dragging = isDragging,
        content = content
    )
}
