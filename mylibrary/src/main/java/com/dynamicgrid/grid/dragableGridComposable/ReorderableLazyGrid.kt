package com.dynamicgrid.grid.dragableGridComposable

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun rememberDraggableGridState(
    gridState: LazyGridState,
    edgePadding: PaddingValues = PaddingValues(0.dp),
    edgeScrollThreshold: Dp = DraggableGridDefaults.ScrollTriggerDistance,
    autoScroller: AutoScroller = rememberAutoScroller(
        scrollableState = gridState,
        scrollPixelsProvider = { gridState.layoutInfo.mainAxisViewportSize * ScrollFactor },
    ),
    onItemMoved: suspend CoroutineScope.(startItem: LazyGridItemInfo, endItem: LazyGridItemInfo) -> Unit,
): DraggableGridState {

    val density = LocalDensity.current
    val scrollTriggerPx = with(density) { edgeScrollThreshold.toPx() }

    // Capture the coroutine scope and latest onItemMoved handler
    val coroutineScope = rememberCoroutineScope()
    val moveAction = rememberUpdatedState(onItemMoved)

    // Handle layout direction (LTR/RTL) and padding calculation
    val layoutDirection = LocalLayoutDirection.current
    val pixelPadding = with(density) {
        AbsolutePixelPadding(
            start = edgePadding.calculateStartPadding(layoutDirection).toPx(),
            end = edgePadding.calculateEndPadding(layoutDirection).toPx(),
            top = edgePadding.calculateTopPadding().toPx(),
            bottom = edgePadding.calculateBottomPadding().toPx(),
        )
    }

    // Create and remember the state object
    val draggableState = remember(
        coroutineScope, gridState, scrollTriggerPx, pixelPadding, autoScroller
    ) {
        DraggableGridState(
            gridState = gridState,
            coroutineScope = coroutineScope,
            moveAction = moveAction,
            scrollTriggerPx = scrollTriggerPx,
            pixelPadding = pixelPadding,
            autoScroller = autoScroller,
            layoutDirection = layoutDirection
        )
    }

    return draggableState
}



private val LazyGridLayoutInfo.mainAxisViewportSize: Int
    get() = when (orientation) {
        Orientation.Vertical -> viewportSize.height
        Orientation.Horizontal -> viewportSize.width
    }

private fun LazyGridItemInfo.toLazyCollectionItemInfo() =
    object : LazyCollectionItemInfo<LazyGridItemInfo> {
        override val index: Int
            get() = this@toLazyCollectionItemInfo.index
        override val key: Any
            get() = this@toLazyCollectionItemInfo.key
        override val offset: IntOffset
            get() = this@toLazyCollectionItemInfo.offset
        override val size: IntSize
            get() = this@toLazyCollectionItemInfo.size
        override val data: LazyGridItemInfo
            get() = this@toLazyCollectionItemInfo

    }

private fun LazyGridLayoutInfo.toLazyCollectionLayoutInfo() =
    object : LazyCollectionLayoutInfo<LazyGridItemInfo> {
        override val visibleItemsInfo: List<LazyCollectionItemInfo<LazyGridItemInfo>>
            get() = this@toLazyCollectionLayoutInfo.visibleItemsInfo.map {
                it.toLazyCollectionItemInfo()
            }
        override val viewportSize: IntSize
            get() = this@toLazyCollectionLayoutInfo.viewportSize
        override val orientation: Orientation
            get() = this@toLazyCollectionLayoutInfo.orientation
        override val reverseLayout: Boolean
            get() = this@toLazyCollectionLayoutInfo.reverseLayout
        override val beforeContentPadding: Int
            get() = this@toLazyCollectionLayoutInfo.beforeContentPadding

    }

@Stable
class DraggableGridState internal constructor(
    gridState: LazyGridState,
    coroutineScope: CoroutineScope,
    moveAction: State<suspend CoroutineScope.(from: LazyGridItemInfo, to: LazyGridItemInfo) -> Unit>,
    scrollTriggerPx: Float,
    pixelPadding: AbsolutePixelPadding,
    autoScroller: AutoScroller,
    layoutDirection: LayoutDirection,
) : ReorderableLazyCollectionState<LazyGridItemInfo>(
    gridState.toLazyCollectionState(),
    coroutineScope,
    moveAction,
    scrollTriggerPx,
    pixelPadding,
    autoScroller,
    layoutDirection,
)


@Stable
open class ReorderableLazyCollectionState<out T> internal constructor(
    private val state: LazyCollectionState<T>,
    private val scope: CoroutineScope,
    private val onMoveState: State<suspend CoroutineScope.(from: T, to: T) -> Unit>,
    private val scrollThreshold: Float,
    private val scrollThresholdPadding: AbsolutePixelPadding,
    private val autoScroller: AutoScroller,

    private val layoutDirection: LayoutDirection,

    private val lazyVerticalStaggeredGridRtlFix: Boolean = false,


    private val shouldItemMove: (draggingItem: Rect, item: Rect) -> Boolean = { draggingItem, item ->
        draggingItem.contains(item.center)
    },
) : ReorderableLazyCollectionStateInterface {
    private val onMoveStateMutex: Mutex = Mutex()

    internal val orientation: Orientation
        get() = state.layoutInfo.orientation

    private var draggingItemKey by mutableStateOf<Any?>(null)
    private val draggingItemIndex: Int?
        get() = draggingItemLayoutInfo?.index

    /**
     * Whether any item is being dragged. This property is observable.
     */
    override val isAnyItemDragging by derivedStateOf {
        draggingItemKey != null
    }

    private var draggingItemDraggedDelta by mutableStateOf(Offset.Zero)
    private var draggingItemInitialOffset by mutableStateOf(IntOffset.Zero)

    // visibleItemsInfo doesn't update immediately after onMove, draggingItemLayoutInfo.item may be outdated for a short time.
    // not a clean solution, but it works.
    private var oldDraggingItemIndex by mutableStateOf<Int?>(null)
    private var predictedDraggingItemOffset by mutableStateOf<IntOffset?>(null)

    private val draggingItemLayoutInfo: LazyCollectionItemInfo<T>?
        get() = draggingItemKey?.let { draggingItemKey ->
            state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingItemKey }
        }
    internal val draggingItemOffset: Offset
        get() = (draggingItemLayoutInfo?.let {
            val offset =
                if (it.index != oldDraggingItemIndex || oldDraggingItemIndex == null) {
                    oldDraggingItemIndex = null
                    predictedDraggingItemOffset = null
                    it.offset
                } else {
                    predictedDraggingItemOffset ?: it.offset
                }

            draggingItemDraggedDelta +
                    (draggingItemInitialOffset.toOffset() - offset.toOffset())
                        .reverseAxisIfNecessary()
                        .reverseAxisWithLayoutDirectionIfLazyVerticalStaggeredGridRtlFix()
        }) ?: Offset.Zero

    // the offset of the handle center from the top left of the dragging item when dragging starts
    private var draggingItemHandleOffset = Offset.Zero

    internal val reorderableKeys = HashSet<Any?>()

    internal var previousDraggingItemKey by mutableStateOf<Any?>(null)
        private set
    internal var previousDraggingItemOffset = Animatable(Offset.Zero, Offset.VectorConverter)
        private set

    private fun Offset.reverseAxisWithReverseLayoutIfNecessary() =
        when (state.layoutInfo.reverseLayout) {
            true -> reverseAxis(orientation)
            false -> this
        }

    private fun Offset.reverseAxisWithLayoutDirectionIfNecessary() = when (orientation) {
        Orientation.Vertical -> this
        Orientation.Horizontal -> reverseAxisWithLayoutDirection()
    }

    private fun Offset.reverseAxisWithLayoutDirection() = when (layoutDirection) {
        LayoutDirection.Ltr -> this
        LayoutDirection.Rtl -> reverseAxis(Orientation.Horizontal)
    }

    private fun Offset.reverseAxisWithLayoutDirectionIfLazyVerticalStaggeredGridRtlFix() =
        when (layoutDirection) {
            LayoutDirection.Ltr -> this
            LayoutDirection.Rtl -> if (lazyVerticalStaggeredGridRtlFix && orientation == Orientation.Vertical)
                reverseAxis(Orientation.Horizontal)
            else this
        }

    private fun Offset.reverseAxisIfNecessary() =
        this.reverseAxisWithReverseLayoutIfNecessary()
            .reverseAxisWithLayoutDirectionIfNecessary()

    private fun Offset.mainAxis() = getAxis(orientation)

    private fun IntOffset.mainAxis() = getAxis(orientation)

    internal suspend fun onDragStart(key: Any, handleOffset: Offset) {
        state.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key == key
        }?.also {
            val mainAxisOffset = it.offset.mainAxis()
            if (mainAxisOffset < 0) {
                // if item is not fully in view, scroll to it
                state.animateScrollBy(mainAxisOffset.toFloat(), spring())
            }

            draggingItemKey = key
            draggingItemInitialOffset = it.offset
            draggingItemHandleOffset = handleOffset
        }
    }

    internal fun onDragStop() {
        val previousDraggingItemInitialOffset = draggingItemLayoutInfo?.offset

        if (draggingItemIndex != null) {
            previousDraggingItemKey = draggingItemKey
            val startOffset = draggingItemOffset
            scope.launch {
                previousDraggingItemOffset.snapTo(startOffset)
                previousDraggingItemOffset.animateTo(
                    Offset.Zero,
                    spring(
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = Offset.VisibilityThreshold
                    )
                )
                previousDraggingItemKey = null
            }
        }
        draggingItemDraggedDelta = Offset.Zero
        draggingItemKey = null
        draggingItemInitialOffset = previousDraggingItemInitialOffset ?: IntOffset.Zero
        autoScroller.tryStop()
        oldDraggingItemIndex = null
        predictedDraggingItemOffset = null
    }

    internal fun onDrag(offset: Offset) {
        draggingItemDraggedDelta += offset

        val draggingItem = draggingItemLayoutInfo ?: return
        // how far the dragging item is from the original position
        val dragOffset = draggingItemOffset.reverseAxisIfNecessary()
            .reverseAxisWithLayoutDirectionIfLazyVerticalStaggeredGridRtlFix()
        val startOffset = draggingItem.offset.toOffset() + dragOffset
        val endOffset = startOffset + draggingItem.size.toSize()
        val (contentStartOffset, contentEndOffset) = state.layoutInfo.getScrollAreaOffsets(
            scrollThresholdPadding
        )

        // the distance from the top or left of the list to the center of the dragging item handle
        val handleOffset =
            when (state.layoutInfo.reverseLayout ||
                    (layoutDirection == LayoutDirection.Rtl &&
                            orientation == Orientation.Horizontal)) {
                true -> endOffset - draggingItemHandleOffset
                false -> startOffset + draggingItemHandleOffset
            } + IntOffset.fromAxis(
                orientation,
                state.layoutInfo.beforeContentPadding
            ).toOffset()

        // check if the handle center is in the scroll threshold
        val distanceFromStart = (handleOffset.getAxis(orientation) - contentStartOffset)
            .coerceAtLeast(0f)
        val distanceFromEnd = (contentEndOffset - handleOffset.getAxis(orientation))
            .coerceAtLeast(0f)

        val isScrollingStarted = if (distanceFromStart < scrollThreshold) {
            autoScroller.start(
                AutoScroller.Direction.BACKWARD,
                getScrollSpeedMultiplier(distanceFromStart),
                maxScrollDistanceProvider = {
                    // distance from the start of the dragging item's stationary position to the end of the list
                    (draggingItemLayoutInfo?.let {
                        state.layoutInfo.mainAxisViewportSize -
                                it.offset.toOffset().getAxis(orientation) - 1f
                    }) ?: 0f
                },
                onScroll = {
                    moveDraggingItemToEnd(AutoScroller.Direction.BACKWARD)
                }
            )
        } else if (distanceFromEnd < scrollThreshold) {
            autoScroller.start(
                AutoScroller.Direction.FORWARD,
                getScrollSpeedMultiplier(distanceFromEnd),
                maxScrollDistanceProvider = {
                    // distance from the end of the dragging item's stationary position to the start of the list
                    (draggingItemLayoutInfo?.let {
                        val visibleItems = state.layoutInfo.visibleItemsInfo
                        // use the item before the dragging item to prevent the dragging item from becoming the firstVisibleItem
                        // TODO(foundation v1.7.0): remove once foundation v1.7.0 is out
                        val itemBeforeDraggingItem =
                            visibleItems.getOrNull(visibleItems.indexOfFirst { it.key == draggingItemKey } - 1)
                        var itemToAlmostScrollOff = itemBeforeDraggingItem ?: it
                        var scrollDistance =
                            itemToAlmostScrollOff.offset.toOffset().getAxis(orientation) +
                                    itemToAlmostScrollOff.size.getAxis(orientation) - 1f
                        if (scrollDistance <= 0f) {
                            itemToAlmostScrollOff = it
                            scrollDistance =
                                itemToAlmostScrollOff.offset.toOffset().getAxis(orientation) +
                                        itemToAlmostScrollOff.size.getAxis(orientation) - 1f
                        }

                        scrollDistance
                    }) ?: 0f
                },
                onScroll = {
                    moveDraggingItemToEnd(AutoScroller.Direction.FORWARD)
                }
            )
        } else {
            autoScroller.tryStop()
            false
        }

        if (!onMoveStateMutex.tryLock()) return
        if (!autoScroller.isScrolling && !isScrollingStarted) {
            val draggingItemRect = Rect(startOffset, endOffset)
            // find a target item to move with
            val targetItem = findTargetItem(
                draggingItemRect,
                items = state.layoutInfo.visibleItemsInfo,
            ) {
                it.index != draggingItem.index
            }
            if (targetItem != null) {
                scope.launch {
                    moveItems(draggingItem, targetItem)
                }
            }
        }
        onMoveStateMutex.unlock()
    }

    // keep dragging item in visible area to prevent it from disappearing
    private suspend fun moveDraggingItemToEnd(
        direction: AutoScroller.Direction,
    ) {
        // wait for the current moveItems to finish
        onMoveStateMutex.lock()

        val draggingItem = draggingItemLayoutInfo
        if (draggingItem == null) {
            onMoveStateMutex.unlock()
            return
        }
        val isDraggingItemAtEnd = when (direction) {
            AutoScroller.Direction.FORWARD -> draggingItem.index == state.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            AutoScroller.Direction.BACKWARD -> draggingItem.index == state.firstVisibleItemIndex
        }
        if (isDraggingItemAtEnd) {
            onMoveStateMutex.unlock()
            return
        }
        val dragOffset = draggingItemOffset.reverseAxisIfNecessary()
            .reverseAxisWithLayoutDirectionIfLazyVerticalStaggeredGridRtlFix()
        val startOffset = draggingItem.offset.toOffset() + dragOffset
        val endOffset = startOffset + draggingItem.size.toSize()
        val draggingItemRect = Rect(startOffset, endOffset).maxOutAxis(orientation)
        val targetItem = findTargetItem(
            draggingItemRect,
            items = state.layoutInfo.getItemsInContentArea(scrollThresholdPadding),
            direction.opposite,
        ) {
            // TODO(foundation v1.7.0): remove `state.firstVisibleItemIndex` check once foundation v1.7.0 is out
            it.index != state.firstVisibleItemIndex
        } ?: state.layoutInfo.getItemsInContentArea(
            scrollThresholdPadding
        ).let {
            val targetItemFunc = { item: LazyCollectionItemInfo<T> ->
                item.key in reorderableKeys && item.index != state.firstVisibleItemIndex
            }
            when (direction) {
                AutoScroller.Direction.FORWARD -> it.findLast(targetItemFunc)
                AutoScroller.Direction.BACKWARD -> it.find(targetItemFunc)
            }
        }
        val job = scope.launch {
            if (targetItem != null) {
                moveItems(draggingItem, targetItem)
            }
        }
        onMoveStateMutex.unlock()
        job.join()
    }

    private fun Rect.maxOutAxis(orientation: Orientation): Rect {
        return when (orientation) {
            Orientation.Vertical -> copy(
                top = Float.NEGATIVE_INFINITY,
                bottom = Float.POSITIVE_INFINITY,
            )

            Orientation.Horizontal -> copy(
                left = Float.NEGATIVE_INFINITY,
                right = Float.POSITIVE_INFINITY,
            )
        }
    }

    private fun findTargetItem(
        draggingItemRect: Rect,
        items: List<LazyCollectionItemInfo<T>> = state.layoutInfo.getItemsInContentArea(),
        direction: AutoScroller.Direction = AutoScroller.Direction.FORWARD,
        additionalPredicate: (LazyCollectionItemInfo<T>) -> Boolean = { true },
    ): LazyCollectionItemInfo<T>? {
        val targetItemFunc = { item: LazyCollectionItemInfo<T> ->
            val targetItemRect = Rect(item.offset.toOffset(), item.size.toSize())

            shouldItemMove(draggingItemRect, targetItemRect)
                    && item.key in reorderableKeys
                    && additionalPredicate(item)
        }
        val targetItem = when (direction) {
            AutoScroller.Direction.FORWARD -> items.find(targetItemFunc)
            AutoScroller.Direction.BACKWARD -> items.findLast(targetItemFunc)
        }
        return targetItem
    }

    private val layoutInfoFlow = snapshotFlow { state.layoutInfo }

    companion object {
        const val MoveItemsLayoutInfoUpdateMaxWaitDuration = 1000L
    }

    private suspend fun moveItems(
        draggingItem: LazyCollectionItemInfo<T>,
        targetItem: LazyCollectionItemInfo<T>,
    ) {
        if (draggingItem.index == targetItem.index) return

        val scrollToIndex = if (targetItem.index == state.firstVisibleItemIndex) {
            draggingItem.index
        } else if (draggingItem.index == state.firstVisibleItemIndex) {
            targetItem.index
        } else {
            null
        }
        if (scrollToIndex != null) {
            state.scrollToItem(scrollToIndex, state.firstVisibleItemScrollOffset)
        }

        try {
            onMoveStateMutex.withLock {
                oldDraggingItemIndex = draggingItem.index

                scope.(onMoveState.value)(draggingItem.data, targetItem.data)

                predictedDraggingItemOffset = if (targetItem.index > draggingItem.index) {
                    (targetItem.offset + targetItem.size) - draggingItem.size
                } else {
                    targetItem.offset
                }

                withTimeout(MoveItemsLayoutInfoUpdateMaxWaitDuration) {
                    // the first result from layoutInfoFlow is the current layoutInfo
                    // the second result is the updated layoutInfo
                    layoutInfoFlow.take(2).collect()
                }

                oldDraggingItemIndex = null
                predictedDraggingItemOffset = null
            }
        } catch (e: CancellationException) {
            // do nothing
        }
    }

    internal fun isItemDragging(key: Any): State<Boolean> {
        return derivedStateOf {
            key == draggingItemKey
        }
    }

    private fun getScrollSpeedMultiplier(distance: Float): Float {
        // map distance in scrollThreshold..-scrollThreshold to 1..10
        return (1 - ((distance + scrollThreshold) / (scrollThreshold * 2)).coerceIn(
            0f,
            1f
        )) * 10
    }
}

interface ReorderableLazyCollectionStateInterface {
    val isAnyItemDragging: Boolean
}

private fun LazyGridState.toLazyCollectionState() =
    object : LazyCollectionState<LazyGridItemInfo> {
        override val firstVisibleItemIndex: Int
            get() = this@toLazyCollectionState.firstVisibleItemIndex
        override val firstVisibleItemScrollOffset: Int
            get() = this@toLazyCollectionState.firstVisibleItemScrollOffset
        override val layoutInfo: LazyCollectionLayoutInfo<LazyGridItemInfo>
            get() = this@toLazyCollectionState.layoutInfo.toLazyCollectionLayoutInfo()

        override suspend fun animateScrollBy(value: Float, animationSpec: AnimationSpec<Float>) =
            this@toLazyCollectionState.animateScrollBy(value, animationSpec)

        override suspend fun scrollToItem(scrollToIndex: Int, firstVisibleItemScrollOffset: Int) =
            this@toLazyCollectionState.scrollToItem(scrollToIndex, firstVisibleItemScrollOffset)
    }

@ExperimentalFoundationApi
@Composable
fun LazyGridItemScope.ReorderableItem(
    state: DraggableGridState,
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ReorderableCollectionItemScope.(isDragging: Boolean) -> Unit,
) {
    val dragging by state.isItemDragging(key)
    val offsetModifier = if (dragging) {
        Log.e("TAG", "ReorderableItem: dragging $key", )
        Modifier
            .zIndex(1f)
            .graphicsLayer {
                translationY = state.draggingItemOffset.y
                translationX = state.draggingItemOffset.x
            }
    } else if (key == state.previousDraggingItemKey) {
        Modifier
            .zIndex(1f)
            .graphicsLayer {
                translationY =
                    state.previousDraggingItemOffset.value.y
                translationX =
                    state.previousDraggingItemOffset.value.x
            }
    } else {
        Modifier.animateItem()
    }

    ReorderableCollectionItem(
        state = state,
        key = key,
        modifier = modifier.then(offsetModifier),
        enabled = enabled,
        dragging = dragging,
        content = content,
    )
}
