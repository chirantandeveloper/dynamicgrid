package com.dynamicgrid.grid.dragableGridComposable

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

/**
 * Creates and remembers a [DraggableGridState] for managing drag-and-drop in a lazy grid.
 * 
 * @param gridState The underlying [LazyGridState] to control
 * @param contentPadding Padding around the draggable content area
 * @param edgeScrollThreshold Distance from edges that triggers auto-scrolling
 * @param autoScroller Optional custom auto-scroller for controlling scroll behavior
 * @param onItemMoved Callback invoked when an item is moved to a new position
 * @return A [DraggableGridState] that manages the drag-and-drop interactions
 */
@Composable
fun rememberDraggableGridState(
    gridState: LazyGridState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    edgeScrollThreshold: Dp = DraggableGridDefaults.EDGE_SCROLL_THRESHOLD,
    autoScroller: AutoScroller = rememberAutoScroller(
        scrollableState = gridState,
        scrollPixelsProvider = { gridState.layoutInfo.mainAxisViewportSize * SCROLL_SPEED_FACTOR },
    ),
    onItemMoved: suspend CoroutineScope.(from: LazyGridItemInfo, to: LazyGridItemInfo) -> Unit,
): DraggableGridState {

    val density = LocalDensity.current
    val scrollThresholdPx = with(density) { edgeScrollThreshold.toPx() }

    // Capture the coroutine scope and latest onItemMoved handler
    val coroutineScope = rememberCoroutineScope()
    val onItemMovedState = rememberUpdatedState(onItemMoved)

    // Handle layout direction (LTR/RTL) and padding calculation
    val layoutDirection = LocalLayoutDirection.current
    val absolutePadding = with(density) {
        AbsolutePixelPadding(
            start = contentPadding.calculateStartPadding(layoutDirection).toPx(),
            end = contentPadding.calculateEndPadding(layoutDirection).toPx(),
            top = contentPadding.calculateTopPadding().toPx(),
            bottom = contentPadding.calculateBottomPadding().toPx(),
        )
    }

    // Create and remember the state object
    return remember(
        coroutineScope, gridState, scrollThresholdPx, absolutePadding, autoScroller
    ) {
        DraggableGridState(
            gridState = gridState,
            coroutineScope = coroutineScope,
            onItemMovedState = onItemMovedState,
            scrollThresholdPx = scrollThresholdPx,
            absolutePadding = absolutePadding,
            autoScroller = autoScroller,
            layoutDirection = layoutDirection
        )
    }
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

/**
 * State holder for managing drag-and-drop operations in a lazy grid.
 */
@Stable
class DraggableGridState internal constructor(
    gridState: LazyGridState,
    coroutineScope: CoroutineScope,
    onItemMovedState: State<suspend CoroutineScope.(from: LazyGridItemInfo, to: LazyGridItemInfo) -> Unit>,
    scrollThresholdPx: Float,
    absolutePadding: AbsolutePixelPadding,
    autoScroller: AutoScroller,
    layoutDirection: LayoutDirection,
) : ReorderableLazyCollectionState<LazyGridItemInfo>(
    state = gridState.toLazyCollectionState(),
    scope = coroutineScope,
    onMoveState = onItemMovedState,
    scrollThreshold = scrollThresholdPx,
    scrollThresholdPadding = absolutePadding,
    autoScroller = autoScroller,
    layoutDirection = layoutDirection,
)


/**
 * Base state class for managing reorderable lazy collections.
 * 
 * This class handles the core drag-and-drop logic including:
 * - Tracking the dragging item
 * - Managing auto-scrolling at edges
 * - Coordinating item movements
 * - Handling animations and visual feedback
 * 
 * @param T Type of data associated with collection items
 */
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
                direction = AutoScroller.ScrollDirection.BACKWARD,
                speedMultiplier = getScrollSpeedMultiplier(distanceFromStart),
                maxDistanceProvider = {
                    // distance from the start of the dragging item's stationary position to the end of the list
                    (draggingItemLayoutInfo?.let {
                        state.layoutInfo.mainAxisViewportSize -
                                it.offset.toOffset().getAxis(orientation) - 1f
                    }) ?: 0f
                },
                onScroll = {
                    moveDraggingItemToEnd(AutoScroller.ScrollDirection.BACKWARD)
                }
            )
        } else if (distanceFromEnd < scrollThreshold) {
            autoScroller.start(
                direction = AutoScroller.ScrollDirection.FORWARD,
                speedMultiplier = getScrollSpeedMultiplier(distanceFromEnd),
                maxDistanceProvider = {
                    // distance from the end of the dragging item's stationary position to the start of the list
                    (draggingItemLayoutInfo?.let {
                        val visibleItems = state.layoutInfo.visibleItemsInfo
                        // use the item before the dragging item to prevent the dragging item from becoming the firstVisibleItem
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
                    moveDraggingItemToEnd(AutoScroller.ScrollDirection.FORWARD)
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

    /**
     * Keeps the dragging item in the visible area to prevent it from disappearing.
     */
    private suspend fun moveDraggingItemToEnd(
        direction: AutoScroller.ScrollDirection,
    ) {
        // wait for the current moveItems to finish
        onMoveStateMutex.lock()

        val draggingItem = draggingItemLayoutInfo
        if (draggingItem == null) {
            onMoveStateMutex.unlock()
            return
        }
        val isDraggingItemAtEnd = when (direction) {
            AutoScroller.ScrollDirection.FORWARD -> draggingItem.index == state.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            AutoScroller.ScrollDirection.BACKWARD -> draggingItem.index == state.firstVisibleItemIndex
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
                AutoScroller.ScrollDirection.FORWARD -> it.findLast(targetItemFunc)
                AutoScroller.ScrollDirection.BACKWARD -> it.find(targetItemFunc)
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
        direction: AutoScroller.ScrollDirection = AutoScroller.ScrollDirection.FORWARD,
        additionalPredicate: (LazyCollectionItemInfo<T>) -> Boolean = { true },
    ): LazyCollectionItemInfo<T>? {
        val targetItemFunc = { item: LazyCollectionItemInfo<T> ->
            val targetItemRect = Rect(item.offset.toOffset(), item.size.toSize())

            shouldItemMove(draggingItemRect, targetItemRect)
                    && item.key in reorderableKeys
                    && additionalPredicate(item)
        }
        val targetItem = when (direction) {
            AutoScroller.ScrollDirection.FORWARD -> items.find(targetItemFunc)
            AutoScroller.ScrollDirection.BACKWARD -> items.findLast(targetItemFunc)
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

    internal fun isItemJustDropped(key: Any): State<Boolean> {
        return derivedStateOf {
            key == previousDraggingItemKey
        }
    }

    private fun getScrollSpeedMultiplier(distance: Float): Float {
        // Distance near the edge accelerates scrolling: 0..threshold -> 10..~1
        val normalized = ((distance + scrollThreshold) / (scrollThreshold * 2)).coerceIn(0f, 1f)
        val inverted = 1f - normalized
        return inverted * 10f
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

        override suspend fun scrollToItem(index: Int, scrollOffset: Int) =
            this@toLazyCollectionState.scrollToItem(index, scrollOffset)
    }

/**
 * A reorderable item in a lazy grid that supports drag-and-drop.
 * 
 * This composable must be used within a [LazyGridItemScope] to create draggable items
 * in a lazy grid. It handles the visual feedback during drag operations including
 * elevation changes and smooth animations.
 * 
 * @param state The [DraggableGridState] managing the drag-and-drop operations
 * @param key Unique key identifying this item
 * @param modifier Modifier to apply to this item
 * @param enabled Whether this item can be dragged
 * @param content The content of this item, receives whether it's currently being dragged
 */
@ExperimentalFoundationApi
@Composable
fun LazyGridItemScope.ReorderableItem(
    state: DraggableGridState,
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable ReorderableCollectionItemScope.(isDragging: Boolean) -> Unit,
) {
    val isDragging by state.isItemDragging(key)
    val isJustDropped by state.isItemJustDropped(key)

    // Apply appropriate modifiers based on drag state
    val offsetModifier = if (isDragging) {
        // Currently dragging - apply translation and elevation
        Modifier
            .zIndex(1f)
            .graphicsLayer {
                translationY = state.draggingItemOffset.y
                translationX = state.draggingItemOffset.x
            }
    } else if (isJustDropped) {
        // Was just dragged - animate back to position
        Modifier
            .zIndex(1f)
            .graphicsLayer {
                translationY = state.previousDraggingItemOffset.value.y
                translationX = state.previousDraggingItemOffset.value.x
            }
    } else {
        // Normal item - use default item animation
        Modifier.animateItem()
    }

    ReorderableCollectionItem(
        state = state,
        key = key,
        modifier = modifier.then(offsetModifier),
        enabled = enabled,
        dragging = isDragging,
        content = content,
    )
}
