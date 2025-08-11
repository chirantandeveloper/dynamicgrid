package com.dynamicgrid.grid.dragableGridComposable

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Creates and remembers an [AutoScroller] instance for automatic scrolling during drag operations.
 * 
 * @param scrollableState The scrollable state to control
 * @param scrollPixelsProvider Provider for the number of pixels to scroll
 * @param scrollDurationMs Duration for each scroll animation in milliseconds (default: 100ms)
 * @return An [AutoScroller] instance that handles automatic scrolling
 */
@Composable
fun rememberAutoScroller(
    scrollableState: ScrollableState,
    scrollPixelsProvider: () -> Float,
    scrollDurationMs: Long = DEFAULT_SCROLL_DURATION_MS,
): AutoScroller {
    val coroutineScope = rememberCoroutineScope()
    val updatedPixelProvider = rememberUpdatedState(scrollPixelsProvider)
    val updatedDuration = rememberUpdatedState(scrollDurationMs)

    return remember(scrollableState, coroutineScope, scrollDurationMs) {
        AutoScroller(
            scrollableState = scrollableState,
            coroutineScope = coroutineScope,
            pixelsPerSecondProvider = {
                updatedPixelProvider.value() / (updatedDuration.value / 1000f)
            },
        )
    }
}

private const val DEFAULT_SCROLL_DURATION_MS = 100L


/**
 * Handles automatic scrolling during drag-and-drop operations.
 * 
 * This class manages smooth auto-scrolling when dragging items near the edges of a scrollable container.
 * It provides configurable scroll speed and direction control.
 */
@Stable
class AutoScroller internal constructor(
    private val scrollableState: ScrollableState,
    private val coroutineScope: CoroutineScope,
    private val pixelsPerSecondProvider: () -> Float,
) {
    companion object {
        // Maximum duration for a single scroll animation step in milliseconds
        private const val MAX_SCROLL_DURATION_MS = 100L
        
        // Delay when no scrolling is possible but scroll is requested
        private const val IDLE_SCROLL_DELAY_MS = 100L
    }

    /**
     * Represents the direction of automatic scrolling.
     */
    internal enum class ScrollDirection {
        BACKWARD, // Scroll towards the start (up/left)
        FORWARD;  // Scroll towards the end (down/right)

        /**
         * Returns the opposite scroll direction.
         */
        val opposite: ScrollDirection
            get() = when (this) {
                BACKWARD -> FORWARD
                FORWARD -> BACKWARD
            }
    }

    /**
     * Contains information about the current scroll operation.
     */
    private data class ScrollConfiguration(
        val direction: ScrollDirection,
        val speedMultiplier: Float,
        val maxDistanceProvider: () -> Float,
        val onScrollCallback: suspend () -> Unit,
    ) {
        companion object {
            val EMPTY = ScrollConfiguration(
                direction = ScrollDirection.FORWARD,
                speedMultiplier = 0f,
                maxDistanceProvider = { 0f },
                onScrollCallback = {}
            )
        }
    }

    private var activeScrollJob: Job? = null
    
    /**
     * Indicates whether auto-scrolling is currently active.
     */
    val isScrolling: Boolean
        get() = activeScrollJob?.isActive == true

    private val scrollConfigChannel = Channel<ScrollConfiguration>(Channel.CONFLATED)

    /**
     * Starts automatic scrolling in the specified direction.
     * 
     * @param direction The direction to scroll
     * @param speedMultiplier Multiplier for scroll speed (default: 1.0)
     * @param maxDistanceProvider Provider for maximum scroll distance
     * @param onScroll Callback invoked during each scroll iteration
     * @return true if scrolling was started, false if scrolling is not possible
     */
    internal fun start(
        direction: ScrollDirection,
        speedMultiplier: Float = 1f,
        maxDistanceProvider: () -> Float = { Float.MAX_VALUE },
        onScroll: suspend () -> Unit = {},
    ): Boolean {
        if (!canScrollInDirection(direction)) return false

        // Start the scroll loop if not already running
        if (activeScrollJob == null) {
            activeScrollJob = coroutineScope.launch {
                performScrollLoop()
            }
        }

        val scrollConfig = ScrollConfiguration(
            direction = direction,
            speedMultiplier = speedMultiplier,
            maxDistanceProvider = maxDistanceProvider,
            onScrollCallback = onScroll
        )

        scrollConfigChannel.trySend(scrollConfig)
        return true
    }

    /**
     * Main scroll loop that processes scroll configurations and performs scrolling.
     */
    private suspend fun performScrollLoop() {
        var currentConfig: ScrollConfiguration? = null

        while (true) {
            // Update configuration if a new one is available
            currentConfig = scrollConfigChannel.tryReceive().getOrNull() ?: currentConfig
            
            // Exit if no configuration or empty configuration
            if (currentConfig == null || currentConfig == ScrollConfiguration.EMPTY) {
                break
            }

            val (direction, speedMultiplier, maxDistanceProvider, onScrollCallback) = currentConfig

            // Calculate scroll speed
            val pixelsPerSecond = pixelsPerSecondProvider() * speedMultiplier
            val pixelsPerMillisecond = pixelsPerSecond / 1000f

            // Execute scroll callback
            onScrollCallback()

            // Check if scrolling is still possible
            if (!canScrollInDirection(direction)) {
                break
            }

            // Calculate scroll distance and duration
            val maxDistance = maxDistanceProvider()
            if (maxDistance <= 0f) {
                delay(IDLE_SCROLL_DELAY_MS)
                continue
            }
            
            val requiredDuration = maxDistance / pixelsPerMillisecond
            val actualDuration = requiredDuration.toLong().coerceIn(1L, MAX_SCROLL_DURATION_MS)
            val actualDistance = maxDistance * (actualDuration / requiredDuration)
            
            // Apply direction to distance
            val scrollDelta = when (direction) {
                ScrollDirection.BACKWARD -> -actualDistance
                ScrollDirection.FORWARD -> actualDistance
            }

            // Perform animated scroll
            scrollableState.animateScrollBy(
                value = scrollDelta,
                animationSpec = tween(
                    durationMillis = actualDuration.toInt(),
                    easing = LinearEasing
                )
            )
        }
    }

    /**
     * Checks if scrolling is possible in the specified direction.
     */
    private fun canScrollInDirection(direction: ScrollDirection): Boolean {
        return when (direction) {
            ScrollDirection.BACKWARD -> scrollableState.canScrollBackward
            ScrollDirection.FORWARD -> scrollableState.canScrollForward
        }
    }

    /**
     * Stops the automatic scrolling and waits for completion.
     */
    internal suspend fun stop() {
        scrollConfigChannel.send(ScrollConfiguration.EMPTY)
        activeScrollJob?.cancelAndJoin()
        activeScrollJob = null
    }

    /**
     * Attempts to stop scrolling without blocking.
     */
    internal fun tryStop() {
        coroutineScope.launch {
            stop()
        }
    }
}