package com.dynamicgrid.grid.dragableGridComposable

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Default configuration values for the draggable grid.
 */
object DraggableGridDefaults {
    /**
     * Distance from the edge that triggers auto-scrolling during drag operations.
     */
    val EDGE_SCROLL_THRESHOLD = 48.dp
}

/**
 * Factor used to calculate scroll speed relative to viewport size.
 */
internal const val SCROLL_SPEED_FACTOR = 0.05f

/**
 * Interface representing the state of a lazy collection (grid, list, etc.).
 * 
 * @param T Type of data associated with collection items
 */
internal interface LazyCollectionState<out T> {
    /**
     * Index of the first visible item in the collection.
     */
    val firstVisibleItemIndex: Int
    
    /**
     * Scroll offset of the first visible item.
     */
    val firstVisibleItemScrollOffset: Int
    
    /**
     * Layout information about the collection.
     */
    val layoutInfo: LazyCollectionLayoutInfo<T>

    /**
     * Animates scrolling by the specified amount.
     */
    suspend fun animateScrollBy(
        value: Float,
        animationSpec: AnimationSpec<Float> = spring(),
    ): Float

    /**
     * Scrolls to the specified item.
     */
    suspend fun scrollToItem(index: Int, scrollOffset: Int)
}

/**
 * Layout information for a lazy collection.
 * 
 * @param T Type of data associated with collection items
 */
internal interface LazyCollectionLayoutInfo<out T> {
    /**
     * List of currently visible items.
     */
    val visibleItemsInfo: List<LazyCollectionItemInfo<T>>
    
    /**
     * Size of the viewport in pixels.
     */
    val viewportSize: IntSize
    
    /**
     * Orientation of the collection (vertical or horizontal).
     */
    val orientation: Orientation
    
    /**
     * Whether the layout is reversed.
     */
    val reverseLayout: Boolean
    
    /**
     * Padding before the first item.
     */
    val beforeContentPadding: Int

    /**
     * Size of the viewport along the main axis.
     */
    val mainAxisViewportSize: Int
        get() = when (orientation) {
            Orientation.Vertical -> viewportSize.height
            Orientation.Horizontal -> viewportSize.width
        }

    fun getScrollAreaOffsets(
        padding: AbsolutePixelPadding,
    ) = getScrollAreaOffsets(
        CollectionScrollPadding.fromAbsolutePixelPadding(
            orientation,
            padding,
            reverseLayout,
        )
    )

    fun getScrollAreaOffsets(padding: CollectionScrollPadding): ScrollAreaOffsets {
        val (startPadding, endPadding) = padding
        val contentEndOffset = when (orientation) {
            Orientation.Vertical -> viewportSize.height
            Orientation.Horizontal -> viewportSize.width
        } - endPadding

        return ScrollAreaOffsets(
            start = startPadding,
            end = contentEndOffset,
        )
    }


    fun getItemsInContentArea(padding: AbsolutePixelPadding) = getItemsInContentArea(
        CollectionScrollPadding.fromAbsolutePixelPadding(
            orientation,
            padding,
            reverseLayout,
        )
    )

    /**
     * Gets items that are fully inside the content area.
     * 
     * @param padding Padding to consider when calculating the content area
     * @return List of items fully within the content area
     */
    fun getItemsInContentArea(padding: CollectionScrollPadding = CollectionScrollPadding.ZERO): List<LazyCollectionItemInfo<T>> {
        val (contentStart, contentEnd) = getScrollAreaOffsets(padding)
        // Compute the main-axis start and end for each item and keep those fully inside the content span.
        return visibleItemsInfo.filter { item ->
            val start = when (orientation) {
                Orientation.Vertical -> item.offset.y.toFloat()
                Orientation.Horizontal -> item.offset.x.toFloat()
            }
            val extent = when (orientation) {
                Orientation.Vertical -> item.size.height.toFloat()
                Orientation.Horizontal -> item.size.width.toFloat()
            }
            start >= contentStart && (start + extent) <= contentEnd
        }
    }
}

/**
 * Information about a single item in a lazy collection.
 * 
 * @param T Type of data associated with the item
 */
internal interface LazyCollectionItemInfo<out T> {
    /**
     * Index of the item in the collection.
     */
    val index: Int
    
    /**
     * Unique key identifying the item.
     */
    val key: Any
    
    /**
     * Position of the item in pixels.
     */
    val offset: IntOffset
    
    /**
     * Size of the item in pixels.
     */
    val size: IntSize
    
    /**
     * Additional data associated with the item.
     */
    val data: T

    /**
     * Center point of the item.
     */
    val center: IntOffset
        get() = IntOffset(offset.x + size.width / 2, offset.y + size.height / 2)
}

/**
 * Represents padding values in pixels for all sides.
 */
internal data class AbsolutePixelPadding(
    val start: Float,
    val end: Float,
    val top: Float,
    val bottom: Float,
)

/**
 * Padding for scroll calculations along the main axis.
 */
internal data class CollectionScrollPadding(
    val start: Float,
    val end: Float,
) {
    companion object {
        val ZERO = CollectionScrollPadding(0f, 0f)

        fun fromAbsolutePixelPadding(
            orientation: Orientation,
            padding: AbsolutePixelPadding,
            reverseLayout: Boolean,
        ): CollectionScrollPadding {
            return when (orientation) {
                Orientation.Vertical -> CollectionScrollPadding(
                    start = padding.top,
                    end = padding.bottom,
                )

                Orientation.Horizontal -> CollectionScrollPadding(
                    start = padding.start,
                    end = padding.end,
                )
            }.let {
                when (reverseLayout) {
                    true -> CollectionScrollPadding(
                        start = it.end,
                        end = it.start,
                    )

                    false -> it
                }
            }
        }
    }
}

/**
 * Represents the start and end offsets of the scrollable area.
 */
internal data class ScrollAreaOffsets(
    val start: Float,
    val end: Float,
)

/**
 * A reorderable item in a lazy collection that supports drag-and-drop.
 * 
 * @param state The reorderable collection state
 * @param key Unique key for this item
 * @param modifier Modifier to apply to the item
 * @param enabled Whether this item can be dragged
 * @param dragging Whether this item is currently being dragged
 * @param content Content of the item
 */
@ExperimentalFoundationApi
@Composable
internal fun ReorderableCollectionItem(
    state: ReorderableLazyCollectionState<*>,
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dragging: Boolean,
    content: @Composable ReorderableCollectionItemScope.(isDragging: Boolean) -> Unit,
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier.onGloballyPositioned {
            // Reduce recompositions: update only when dragging and when value actually changes
            if (state.isAnyItemDragging) {
                val newPos = it.positionInRoot()
                if (newPos != itemPosition) itemPosition = newPos
            }
        }
    ) {
        val itemScope = remember(state, key) {
            ReorderableCollectionItemScopeImpl(
                reorderableLazyCollectionState = state,
                key = key,
                itemPositionProvider = { itemPosition },
            )
        }
        itemScope.content(dragging)
    }
    
    // Register/unregister this item as reorderable using DisposableEffect to avoid relaunch churn
    androidx.compose.runtime.DisposableEffect(key, enabled) {
        if (enabled) {
            state.reorderableKeys.add(key)
        }
        onDispose {
            state.reorderableKeys.remove(key)
        }
    }
}

/**
 * Scope for a reorderable collection item providing drag handle modifiers.
 */
@Stable
interface ReorderableCollectionItemScope {
    /**
     * Makes this element a drag handle for immediate dragging.
     * 
     * @param enabled Whether dragging is enabled
     * @param interactionSource Optional interaction source for tracking interactions
     * @param onDragStarted Callback when dragging starts
     * @param onDragStopped Callback when dragging stops
     */
    fun Modifier.draggableHandle(
        enabled: Boolean = true,
        interactionSource: MutableInteractionSource? = null,
        onDragStarted: (startedPosition: Offset) -> Unit = {},
        onDragStopped: () -> Unit = {},
    ): Modifier
    
    /**
     * Makes this element a drag handle that requires long press to start dragging.
     * 
     * @param enabled Whether dragging is enabled
     * @param interactionSource Optional interaction source for tracking interactions
     * @param onDragStarted Callback when dragging starts
     * @param onDragStopped Callback when dragging stops
     */
    fun Modifier.longPressDraggableHandle(
        enabled: Boolean = true,
        interactionSource: MutableInteractionSource? = null,
        onDragStarted: (startedPosition: Offset) -> Unit = {},
        onDragStopped: () -> Unit = {},
    ): Modifier
}

internal class ReorderableCollectionItemScopeImpl(
    private val reorderableLazyCollectionState: ReorderableLazyCollectionState<*>,
    private val key: Any,
    private val itemPositionProvider: () -> Offset,
) : ReorderableCollectionItemScope {
    override fun Modifier.draggableHandle(
        enabled: Boolean,
        interactionSource: MutableInteractionSource?,
        onDragStarted: (startedPosition: Offset) -> Unit,
        onDragStopped: () -> Unit,
    ) = composed {
        var handleOffset by remember { mutableStateOf(Offset.Zero) }
        var handleSize by remember { mutableStateOf(IntSize.Zero) }

        val coroutineScope = rememberCoroutineScope()

        onGloballyPositioned {
            handleOffset = it.positionInRoot()
            handleSize = it.size
        }.draggable(
            key = reorderableLazyCollectionState,
            enabled = enabled && (reorderableLazyCollectionState.isItemDragging(key).value || !reorderableLazyCollectionState.isAnyItemDragging),
            interactionSource = interactionSource,
            onDragStarted = {
                coroutineScope.launch {
                    val handleOffsetRelativeToItem = handleOffset - itemPositionProvider()
                    val handleCenter = Offset(
                        handleOffsetRelativeToItem.x + handleSize.width / 2f,
                        handleOffsetRelativeToItem.y + handleSize.height / 2f
                    )

                    reorderableLazyCollectionState.onDragStart(key, handleCenter)
                }
                onDragStarted(it)
            },
            onDragStopped = {
                reorderableLazyCollectionState.onDragStop()
                onDragStopped()
            },
            onDrag = { change, dragAmount ->
                change.consume()
                reorderableLazyCollectionState.onDrag(dragAmount)
            },
        )
    }


    override fun Modifier.longPressDraggableHandle(
        enabled: Boolean,
        interactionSource: MutableInteractionSource?,
        onDragStarted: (startedPosition: Offset) -> Unit,
        onDragStopped: () -> Unit,
    ) = composed {
        var handleOffset by remember { mutableStateOf(Offset.Zero) }
        var handleSize by remember { mutableStateOf(IntSize.Zero) }

        val coroutineScope = rememberCoroutineScope()

        onGloballyPositioned {
            handleOffset = it.positionInRoot()
            handleSize = it.size
        }.longPressDraggable(
            key = reorderableLazyCollectionState,
            enabled = enabled && (reorderableLazyCollectionState.isItemDragging(key).value || !reorderableLazyCollectionState.isAnyItemDragging),
            interactionSource = interactionSource,
            onDragStarted = {
                coroutineScope.launch {
                    val handleOffsetRelativeToItem = handleOffset - itemPositionProvider()
                    val handleCenter = Offset(
                        handleOffsetRelativeToItem.x + handleSize.width / 2f,
                        handleOffsetRelativeToItem.y + handleSize.height / 2f
                    )

                    reorderableLazyCollectionState.onDragStart(key, handleCenter)
                }
                onDragStarted(it)
            },
            onDragStopped = {
                reorderableLazyCollectionState.onDragStop()
                onDragStopped()
            },
            onDrag = { change, dragAmount ->
                change.consume()
                reorderableLazyCollectionState.onDrag(dragAmount)
            },
        )
    }
}

