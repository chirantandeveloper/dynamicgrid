
package com.dynamicgrid.grid.dragableGridComposable

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/** Defaults */
object DragDropDefaults {
    val EdgeTriggerDistance = 56.dp // slightly different default than v1
}

/** Helper padding class (merged) */
internal data class ScrollPadding(
    val start: Float,
    val end: Float,
    val top: Float,
    val bottom: Float,
) {
    fun mainAxis(orientation: Orientation, reverseLayout: Boolean): Pair<Float, Float> {
        val pair = when (orientation) {
            Orientation.Vertical -> start to end // top/bottom
            Orientation.Horizontal -> this.start to this.end // start/end
        }
        return if (reverseLayout) pair.second to pair.first else pair
    }
}

/** State adapters for different lazy collections */
internal interface LazyCollectionState<out T> {
    val firstVisibleItemIndex: Int
    val firstVisibleItemScrollOffset: Int
    val layoutInfo: LazyCollectionLayoutInfo<T>
    suspend fun animateScrollBy(value: Float, animationSpec: AnimationSpec<Float> = spring()): Float
    suspend fun scrollToItem(index: Int, scrollOffset: Int)
}

internal interface LazyCollectionLayoutInfo<out T> {
    val visibleItemsInfo: List<LazyCollectionItemInfo<T>>
    val viewportSize: IntSize
    val orientation: Orientation
    val reverseLayout: Boolean
    val beforeContentPadding: Int
    val mainAxisViewportSize: Int
        get() = if (orientation == Orientation.Vertical) viewportSize.height else viewportSize.width

    fun contentSpan(padding: ScrollPadding): Pair<Float, Float> {
        val (startPad, endPad) = padding.mainAxis(orientation, reverseLayout)
        val end = (if (orientation == Orientation.Vertical) viewportSize.height else viewportSize.width) - endPad
        return startPad to end
    }

    fun itemsInside(padding: ScrollPadding): List<LazyCollectionItemInfo<T>> {
        val (start, end) = contentSpan(padding)
        return visibleItemsInfo.filter { item ->
            val startPos = if (orientation == Orientation.Vertical) item.offset.y.toFloat() else item.offset.x.toFloat()
            val size = if (orientation == Orientation.Vertical) item.size.height.toFloat() else item.size.width.toFloat()
            startPos >= start && (startPos + size) <= end
        }
    }
}

internal interface LazyCollectionItemInfo<out T> {
    val index: Int
    val key: Any
    val offset: IntOffset
    val size: IntSize
    val data: T
    val center: IntOffset get() = IntOffset(offset.x + size.width / 2, offset.y + size.height / 2)
}


internal fun Offset.mainAxis(orientation: Orientation) = if (orientation == Orientation.Vertical) y else x
internal fun IntOffset.mainAxis(orientation: Orientation) = if (orientation == Orientation.Vertical) y else x
internal fun IntSize.mainAxis(orientation: Orientation) = if (orientation == Orientation.Vertical) height else width
internal fun Offset.flipFor(orientation: Orientation): Offset =
    if (orientation == Orientation.Vertical) copy(y = -y) else copy(x = -x)

internal operator fun IntOffset.plus(size: IntSize) = IntOffset(x + size.width, y + size.height)

/** Drag handle scope & registration */
@Stable
interface DragDropItemScope {
    fun Modifier.dragHandle(
        enabled: Boolean = true,
        interactionSource: MutableInteractionSource? = null,
        onStart: (Offset) -> Unit = {},
        onStop: () -> Unit = {},
        longPress: Boolean = false,
    ): Modifier
}

internal class DragDropItemScopeImpl(
    private val state: DragDropLazyState<*>,
    private val key: Any,
    private val itemPositionProvider: () -> Offset,
) : DragDropItemScope {
    override fun Modifier.dragHandle(
        enabled: Boolean,
        interactionSource: MutableInteractionSource?,
        onStart: (Offset) -> Unit,
        onStop: () -> Unit,
        longPress: Boolean
    ): Modifier = composed {
        var handleOffset by remember { mutableStateOf(Offset.Zero) }
        var handleSize by remember { mutableStateOf(IntSize.Zero) }
        val scope = rememberCoroutineScope()

        onGloballyPositioned {
            handleOffset = it.positionInRoot()
            handleSize = it.size
        }.let {
            val started: (Offset) -> Unit = { startOffset ->
                scope.launch {
                    val relative = handleOffset - itemPositionProvider()
                    val center = Offset(
                        relative.x + handleSize.width / 2f,
                        relative.y + handleSize.height / 2f
                    )
                    state.onDragStart(key, center)
                }
                onStart(startOffset)
            }
            val stopped: () -> Unit = {
                state.onDragStop()
                onStop()
            }
            val onDrag: (PointerInputChange, Offset) -> Unit = { change, amount ->
                change.consume()
                state.onDrag(amount)
            }

            if (longPress) it.attachHoldDrag(
                key = state, enabled = enabled && (state.isItemDragging(key).value || !state.isAnyItemDragging),
                interactionSource = interactionSource, onStart = started, onStop = stopped, onDrag = onDrag
            ) else it.attachInstantDrag(
                key = state, enabled = enabled && (state.isItemDragging(key).value || !state.isAnyItemDragging),
                interactionSource = interactionSource, onStart = started, onStop = stopped, onDrag = onDrag
            )
        }
    }
}


@ExperimentalFoundationApi
@Composable
internal fun DragDropCollectionCell(
    state: DragDropLazyState<*>,
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dragging: Boolean,
    content: @Composable DragDropItemScope.(Boolean) -> Unit
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.onGloballyPositioned {
            if (state.isAnyItemDragging) {
                val p = it.positionInRoot()
                if (p != itemPosition) itemPosition = p
            }
        }
    ) {
        val scope = remember(state, key) {
            DragDropItemScopeImpl(state, key) { itemPosition }
        }
        scope.content(dragging)
    }

    DisposableEffect(key, enabled) {
        if (enabled) state.reorderableKeys.add(key)
        onDispose { state.reorderableKeys.remove(key) }
    }
}


@Stable
open class DragDropLazyState<out T> internal constructor(
    private val state: LazyCollectionState<T>,
    private val scope: CoroutineScope,
    private val onMoveState: State<suspend CoroutineScope.(from: T, to: T) -> Unit>,
    private val edgeThresholdPx: Float,
    private val padding: ScrollPadding,
    private val autoScroll: EdgeAutoScrollEngine,
    private val rtl: androidx.compose.ui.unit.LayoutDirection,
    private val itemShouldSwap: (drag: Rect, item: Rect) -> Boolean = { dragRect, itemRect ->
        dragRect.contains(itemRect.center)
    }
) {
    internal val orientation: Orientation get() = state.layoutInfo.orientation

    private var draggingKey by mutableStateOf<Any?>(null)
    private val draggingIndex: Int? get() = draggingInfo?.index

    val isAnyItemDragging by derivedStateOf { draggingKey != null }

    private var dragDelta by mutableStateOf(Offset.Zero)
    private var dragInitialOffset by mutableStateOf(IntOffset.Zero)
    private var lastIndexSnapshot by mutableStateOf<Int?>(null)
    private var predictedOffset by mutableStateOf<IntOffset?>(null)

    private val draggingInfo: LazyCollectionItemInfo<T>?
        get() = draggingKey?.let { k -> state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == k } }

    internal val reorderableKeys = HashSet<Any?>()

    internal var previousDraggingKey by mutableStateOf<Any?>(null)
        private set
    internal var previousDraggingOffset = androidx.compose.animation.core.Animatable(Offset.Zero, Offset.VectorConverter)
        private set

    private var handleCenterFromTopLeft = Offset.Zero

    internal val draggingVisualOffset: Offset
        get() = (draggingInfo?.let {
            val base = if (it.index != lastIndexSnapshot || lastIndexSnapshot == null) {
                lastIndexSnapshot = null
                predictedOffset = null
                it.offset
            } else predictedOffset ?: it.offset

            dragDelta + (dragInitialOffset.toOffset() - base.toOffset())
        }) ?: Offset.Zero

    internal fun isItemDragging(key: Any): State<Boolean> = derivedStateOf { key == draggingKey }
    internal fun isItemJustDropped(key: Any): State<Boolean> = derivedStateOf { key == previousDraggingKey }

    internal suspend fun onDragStart(key: Any, handleOffset: Offset) {
        state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.also { item ->
            val top = item.offset.mainAxis(orientation)
            if (top < 0) state.animateScrollBy(top.toFloat(), spring())

            draggingKey = key
            dragInitialOffset = item.offset
            handleCenterFromTopLeft = handleOffset
        }
    }

    internal fun onDragStop() {
        val prevInitial = draggingInfo?.offset
        if (draggingIndex != null) {
            previousDraggingKey = draggingKey
            val start = draggingVisualOffset
            scope.launch {
                previousDraggingOffset.snapTo(start)
                previousDraggingOffset.animateTo(
                    Offset.Zero, spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Offset.VisibilityThreshold)
                )
                previousDraggingKey = null
            }
        }
        dragDelta = Offset.Zero
        draggingKey = null
        dragInitialOffset = prevInitial ?: IntOffset.Zero
        autoScroll.stopAsync()
        lastIndexSnapshot = null
        predictedOffset = null
    }

    internal fun onDrag(amount: Offset) {
        dragDelta += amount
        val info = draggingInfo ?: return

        val dragOffset = draggingVisualOffset
        val start = info.offset.toOffset() + dragOffset
        val end = start + info.size.toSize() // now works fine

        val (contentStart, contentEnd) = state.layoutInfo.contentSpan(padding)
        val handle = if (state.layoutInfo.reverseLayout ||
            (rtl == androidx.compose.ui.unit.LayoutDirection.Rtl && orientation == Orientation.Horizontal)
        ) {
            end - handleCenterFromTopLeft
        } else {
            start + handleCenterFromTopLeft
        } + IntOffset(
            if (orientation == Orientation.Horizontal) state.layoutInfo.beforeContentPadding else 0,
            if (orientation == Orientation.Vertical) state.layoutInfo.beforeContentPadding else 0
        ).toOffset()

        val distStart = (handle.mainAxis(orientation) - contentStart).coerceAtLeast(0f)
        val distEnd = (contentEnd - handle.mainAxis(orientation)).coerceAtLeast(0f)

        val started = if (distStart < edgeThresholdPx) {
            autoScroll.requestScroll(
                dir = EdgeAutoScrollEngine.Direction.Backward,
                speedMultiplier = scrollSpeedMultiplier(distStart),
                maxDistance = {
                    (draggingInfo?.let {
                        state.layoutInfo.mainAxisViewportSize - it.offset.toOffset().mainAxis(orientation) - 1f
                    }) ?: 0f
                },
                beforeEachStep = { keepDraggingItemVisible(EdgeAutoScrollEngine.Direction.Backward) }
            )
        } else if (distEnd < edgeThresholdPx) {
            autoScroll.requestScroll(
                dir = EdgeAutoScrollEngine.Direction.Forward,
                speedMultiplier = scrollSpeedMultiplier(distEnd),
                maxDistance = {
                    (draggingInfo?.let {
                        val visible = state.layoutInfo.visibleItemsInfo
                        val before = visible.getOrNull(visible.indexOfFirst { it.key == draggingKey } - 1)
                        val candidate = before ?: it
                        var distance = candidate.offset.toOffset().mainAxis(orientation) + candidate.size.mainAxis(orientation) - 1f
                        if (distance <= 0f) {
                            distance = it.offset.toOffset().mainAxis(orientation) + it.size.mainAxis(orientation) - 1f
                        }
                        distance
                    }) ?: 0f
                },
                beforeEachStep = { keepDraggingItemVisible(EdgeAutoScrollEngine.Direction.Forward) }
            )
        } else {
            autoScroll.stopAsync(); false
        }

        if (!moveLock.tryLock()) return
        if (!autoScroll.isActive && !started) {
            val dragRect = Rect(start, end)
            val target = pickTarget(
                dragRect,
                state.layoutInfo.visibleItemsInfo
            ) { it.index != info.index }
            if (target != null) {
                scope.launch { swapWith(info, target) }
            }
        }
        moveLock.unlock()
    }

    private val moveLock = Mutex()
    private val layoutInfoFlow = snapshotFlow { state.layoutInfo }

    private suspend fun keepDraggingItemVisible(dir: EdgeAutoScrollEngine.Direction) {
        moveLock.lock()

        val info = draggingInfo
        if (info == null) {
            moveLock.unlock(); return
        }
        val atEnd = when (dir) {
            EdgeAutoScrollEngine.Direction.Forward -> info.index == state.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            EdgeAutoScrollEngine.Direction.Backward -> info.index == state.firstVisibleItemIndex
        }
        if (atEnd) { moveLock.unlock(); return }

        val start = info.offset.toOffset() + draggingVisualOffset
        val end = start + info.size.toSize()
        val rect = Rect(start, end).let {
            when (orientation) {
                Orientation.Vertical -> it.copy(top = Float.NEGATIVE_INFINITY, bottom = Float.POSITIVE_INFINITY)
                Orientation.Horizontal -> it.copy(left = Float.NEGATIVE_INFINITY, right = Float.POSITIVE_INFINITY)
            }
        }

        val candidates = state.layoutInfo.itemsInside(padding)
        val target = pickTarget(
            rect, candidates,
            if (dir == EdgeAutoScrollEngine.Direction.Forward) EdgeAutoScrollEngine.Direction.Backward else EdgeAutoScrollEngine.Direction.Forward
        ) { it.index != state.firstVisibleItemIndex } ?: run {
            val predicate = { item: LazyCollectionItemInfo<T> -> item.key in reorderableKeys && item.index != state.firstVisibleItemIndex }
            when (dir) {
                EdgeAutoScrollEngine.Direction.Forward -> candidates.findLast(predicate)
                EdgeAutoScrollEngine.Direction.Backward -> candidates.find(predicate)
            }
        }

        val job = scope.launch { if (target != null) swapWith(info, target) }
        moveLock.unlock()
        job.join()
    }

    private fun pickTarget(
        dragRect: Rect,
        items: List<LazyCollectionItemInfo<T>>,
        direction: EdgeAutoScrollEngine.Direction = EdgeAutoScrollEngine.Direction.Forward,
        extra: (LazyCollectionItemInfo<T>) -> Boolean = { true }
    ): LazyCollectionItemInfo<T>? {
        val f: (LazyCollectionItemInfo<T>) -> Boolean = { candidate ->
            val r = Rect(candidate.offset.toOffset(), candidate.size.toSize())
            itemShouldSwap(dragRect, r) && candidate.key in reorderableKeys && extra(candidate)
        }
        return when (direction) {
            EdgeAutoScrollEngine.Direction.Forward -> items.find(f)
            EdgeAutoScrollEngine.Direction.Backward -> items.findLast(f)
        }
    }

    companion object { const val LayoutUpdateTimeout = 1000L }

    private suspend fun swapWith(a: LazyCollectionItemInfo<T>, b: LazyCollectionItemInfo<T>) {
        if (a.index == b.index) return

        val toKeepIndex = if (b.index == state.firstVisibleItemIndex) a.index
        else if (a.index == state.firstVisibleItemIndex) b.index
        else null

        if (toKeepIndex != null) state.scrollToItem(toKeepIndex, state.firstVisibleItemScrollOffset)

        try {
            moveLock.withLock {
                lastIndexSnapshot = a.index

                scope.(onMoveState.value)(a.data, b.data)

                predictedOffset = if (b.index > a.index) (b.offset + b.size) - a.size else b.offset

                withTimeout(LayoutUpdateTimeout) { layoutInfoFlow.take(2).collect() }

                lastIndexSnapshot = null
                predictedOffset = null
            }
        } catch (_: CancellationException) { /* ignore */ }
    }

    private fun scrollSpeedMultiplier(distance: Float): Float {
        val normalized = ((distance + edgeThresholdPx) / (edgeThresholdPx * 2)).coerceIn(0f, 1f)
        return (1f - normalized) * 10f
    }
}
internal operator fun Offset.plus(size: Size): Offset =
    Offset(x + size.width, y + size.height)

internal operator fun IntOffset.minus(size: IntSize): IntOffset = IntOffset(x - size.width, y - size.height)
