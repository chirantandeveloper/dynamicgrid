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
 * Provide and retain a scrolling engine to be used during drag edge-hover.
 * The API surface is unchanged but the implementation is streamlined to make
 * small granular scroll steps, continuously adapting to updated requests.
 */
@Composable
fun rememberAutoScroller(
    scrollableState: ScrollableState,
    scrollPixelsProvider: () -> Float,
    scrollDurationMs: Long = DEFAULT_SCROLL_DURATION_MS,
): AutoScroller {
    val scope = rememberCoroutineScope()
    val pxProvider = rememberUpdatedState(scrollPixelsProvider)
    val durMs = rememberUpdatedState(scrollDurationMs)

    return remember(scrollableState, scope, scrollDurationMs) {
        AutoScroller(
            scrollableState = scrollableState,
            coroutineScope = scope,
            pixelsPerSecondProvider = {
                // convert "pixels per duration" into pixels per second on demand
                pxProvider.value() / (durMs.value / 1000f)
            },
        )
    }
}

private const val DEFAULT_SCROLL_DURATION_MS = 100L

/**
 * Auto-scroll engine used by drag and drop to scroll when hovering near edges.
 */
@Stable
class AutoScroller internal constructor(
    private val scrollableState: ScrollableState,
    private val coroutineScope: CoroutineScope,
    private val pixelsPerSecondProvider: () -> Float,
) {
    companion object {
        private const val MAX_STEP_DURATION_MS = 100L
        private const val IDLE_DELAY_MS = 100L
    }

    internal enum class ScrollDirection { BACKWARD, FORWARD; val opposite: ScrollDirection get() = if (this == BACKWARD) FORWARD else BACKWARD }

    private data class ScrollRequest(
        val dir: ScrollDirection,
        val speedFactor: Float,
        val maxDistance: () -> Float,
        val onTick: suspend () -> Unit,
    ) {
        companion object { val None = ScrollRequest(ScrollDirection.FORWARD, 0f, { 0f }) {} }
    }

    private var job: Job? = null

    val isScrolling: Boolean get() = job?.isActive == true

    private val channel = Channel<ScrollRequest>(capacity = Channel.CONFLATED)

    internal fun start(
        direction: ScrollDirection,
        speedMultiplier: Float = 1f,
        maxDistanceProvider: () -> Float = { Float.MAX_VALUE },
        onScroll: suspend () -> Unit = {},
    ): Boolean {
        if (!canScroll(direction)) return false
        if (job == null) job = coroutineScope.launch { loop() }
        channel.trySend(ScrollRequest(direction, speedMultiplier, maxDistanceProvider, onScroll))
        return true
    }

    private suspend fun loop() {
        var current: ScrollRequest? = null
        while (true) {
            // Consume the latest request if any
            current = channel.tryReceive().getOrNull() ?: current
            if (current == null || current == ScrollRequest.None) break

            val pxPerSec = (pixelsPerSecondProvider() * current.speedFactor).coerceAtLeast(0f)
            if (pxPerSec == 0f) { delay(IDLE_DELAY_MS); continue }

            // Let the caller update state before we compute scrolling
            current.onTick()
            if (!canScroll(current.dir)) break

            val remaining = current.maxDistance()
            if (remaining <= 0f) { delay(IDLE_DELAY_MS); continue }

            val pxPerMs = pxPerSec / 1000f
            val stepDuration = ((remaining / pxPerMs).toLong()).coerceIn(1L, MAX_STEP_DURATION_MS)
            val stepDistance = remaining.coerceAtLeast(0f) * (stepDuration / (remaining / pxPerMs))

            val signedDelta = when (current.dir) {
                ScrollDirection.BACKWARD -> -stepDistance
                ScrollDirection.FORWARD -> stepDistance
            }

            scrollableState.animateScrollBy(
                value = signedDelta,
                animationSpec = tween(durationMillis = stepDuration.toInt(), easing = LinearEasing)
            )
        }
    }

    private fun canScroll(direction: ScrollDirection): Boolean = when (direction) {
        ScrollDirection.BACKWARD -> scrollableState.canScrollBackward
        ScrollDirection.FORWARD -> scrollableState.canScrollForward
    }

    internal suspend fun stop() {
        channel.send(ScrollRequest.None)
        job?.cancelAndJoin()
        job = null
    }

    internal fun tryStop() { coroutineScope.launch { stop() } }
}
