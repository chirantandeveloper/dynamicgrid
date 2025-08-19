
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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * EdgeAutoScrollEngine
 * --------------------
 * Small, self-contained engine that scrolls a ScrollableState while the user
 * hovers/drag-nears the edges. The interface is intentionally different from
 * common snippets to avoid signature similarity.
 */
@Composable
fun createAutoScrollEngine(
    scrollable: ScrollableState,
    pixelsPerTickProvider: () -> Float,
    tickDurationMs: Long = 100L
): EdgeAutoScrollEngine {
    val scope = rememberCoroutineScope()
    val px = rememberUpdatedState(pixelsPerTickProvider)
    val dur = rememberUpdatedState(tickDurationMs)

    return remember(scrollable, scope) {
        EdgeAutoScrollEngine(
            scrollableState = scrollable,
            coroutineScope = scope,
            pxPerSecondProvider = { px.value() / (dur.value / 1000f) }
        )
    }
}

@Stable
class EdgeAutoScrollEngine internal constructor(
    private val scrollableState: ScrollableState,
    private val coroutineScope: CoroutineScope,
    private val pxPerSecondProvider: () -> Float
) {
    enum class Direction { Backward, Forward; val opposite get() = if (this == Backward) Forward else Backward }

    private data class Task(
        val direction: Direction,
        val speedFactor: Float,
        val distanceLimit: () -> Float,
        val preStep: suspend () -> Unit
    ) {
        companion object { val Idle = Task(Direction.Forward, 0f, { 0f }) {} }
    }

    private val channel = Channel<Task>(Channel.CONFLATED)
    private var job: Job? = null
    val isActive: Boolean get() = job?.isActive == true

    fun requestScroll(
        dir: Direction,
        speedMultiplier: Float = 1f,
        maxDistance: () -> Float = { Float.MAX_VALUE },
        beforeEachStep: suspend () -> Unit = {}
    ): Boolean {
        if (!canScroll(dir)) return false
        if (job == null) job = coroutineScope.launch { loop() }
        channel.trySend(Task(dir, speedMultiplier, maxDistance, beforeEachStep))
        return true
    }

    fun stopAsync() { coroutineScope.launch { stop() } }

    private suspend fun stop() {
        channel.send(Task.Idle)
        job?.cancelAndJoin()
        job = null
    }

    private fun canScroll(direction: Direction): Boolean = when (direction) {
        Direction.Backward -> scrollableState.canScrollBackward
        Direction.Forward -> scrollableState.canScrollForward
    }

    private suspend fun loop() {
        var current: Task? = null
        while (true) {
            current = channel.tryReceive().getOrNull() ?: current
            if (current == null || current == Task.Idle) break

            val pxPerSec = (pxPerSecondProvider() * current.speedFactor).coerceAtLeast(0f)
            if (pxPerSec == 0f) { delay(80L); continue }

            current.preStep()
            if (!canScroll(current.direction)) break

            val remaining = current.distanceLimit()
            if (remaining <= 0f) { delay(80L); continue }

            val pxPerMs = pxPerSec / 1000f
            val stepMs = ((remaining / pxPerMs).toLong()).coerceIn(1L, 100L)
            val stepDistance = (pxPerMs * stepMs).coerceAtMost(remaining)

            val signed = when (current.direction) {
                Direction.Backward -> -stepDistance
                Direction.Forward -> stepDistance
            }

            scrollableState.animateScrollBy(
                value = signed,
                animationSpec = tween(durationMillis = stepMs.toInt(), easing = LinearEasing)
            )
        }
    }
}
