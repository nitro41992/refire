package com.narasimha.refire.ui.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * Custom SwipeToDismiss state that disables velocity-based dismissal.
 * Supports asymmetric thresholds: different thresholds for left vs right swipe.
 *
 * @param startToEndThreshold Threshold for swipe right (dismiss) - default 30%
 * @param endToStartThreshold Threshold for swipe left (schedule/extend) - default 25%
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Stable
class NoVelocitySwipeToDismissState(
    initialValue: SwipeToDismissBoxValue = SwipeToDismissBoxValue.Settled,
    private val startToEndThreshold: Float = 0.3f,
    private val endToStartThreshold: Float = 0.25f,
    private val confirmValueChange: (SwipeToDismissBoxValue) -> Boolean = { true }
) {
    // Track width for asymmetric threshold enforcement
    internal var width: Float = 0f

    // Use the lower threshold for positionalThreshold so swiping feels responsive
    // Higher thresholds are enforced in confirmValueChange
    private val minThreshold = minOf(startToEndThreshold, endToStartThreshold)

    // Holder to allow access to the state from within its own confirmValueChange
    private val stateHolder = object {
        lateinit var state: AnchoredDraggableState<SwipeToDismissBoxValue>
    }

    internal val anchoredDraggableState: AnchoredDraggableState<SwipeToDismissBoxValue> = AnchoredDraggableState(
        initialValue = initialValue,
        positionalThreshold = { totalDistance -> totalDistance * minThreshold },
        velocityThreshold = { Float.MAX_VALUE }, // Disable velocity-based dismissal
        snapAnimationSpec = tween(durationMillis = 300),
        decayAnimationSpec = exponentialDecay(),
        confirmValueChange = { newValue: SwipeToDismissBoxValue ->
            when (newValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Enforce higher threshold for dismiss (swipe right)
                    if (width > 0f) {
                        val currentOffset = try { stateHolder.state.requireOffset() } catch (e: IllegalStateException) { 0f }
                        val progress = currentOffset / width
                        progress >= startToEndThreshold && confirmValueChange(newValue)
                    } else {
                        confirmValueChange(newValue)
                    }
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Use lower threshold for schedule (swipe left)
                    if (width > 0f) {
                        val currentOffset = try { stateHolder.state.requireOffset() } catch (e: IllegalStateException) { 0f }
                        val progress = -currentOffset / width  // Negative offset for left swipe
                        progress >= endToStartThreshold && confirmValueChange(newValue)
                    } else {
                        confirmValueChange(newValue)
                    }
                }
                else -> confirmValueChange(newValue)
            }
        }
    ).also { stateHolder.state = it }

    val currentValue: SwipeToDismissBoxValue get() = anchoredDraggableState.currentValue
    val targetValue: SwipeToDismissBoxValue get() = anchoredDraggableState.targetValue

    /**
     * The current offset of the swipe. Use this to determine swipe direction
     * for showing background content immediately (not waiting for threshold).
     */
    val offset: Float get() = try {
        anchoredDraggableState.requireOffset()
    } catch (e: IllegalStateException) {
        0f
    }

    /**
     * The direction currently being swiped based on offset, regardless of threshold.
     * Use this for showing swipe backgrounds immediately as user swipes.
     */
    val swipeDirection: SwipeToDismissBoxValue get() = when {
        offset > 0f -> SwipeToDismissBoxValue.StartToEnd
        offset < 0f -> SwipeToDismissBoxValue.EndToStart
        else -> SwipeToDismissBoxValue.Settled
    }

    /**
     * The dismiss direction (only set after threshold is met).
     * Use swipeDirection instead for immediate visual feedback.
     */
    val dismissDirection: SwipeToDismissBoxValue get() = anchoredDraggableState.targetValue

    internal fun updateAnchors(
        newAnchors: DraggableAnchors<SwipeToDismissBoxValue>
    ) {
        anchoredDraggableState.updateAnchors(newAnchors)
    }

    companion object {
        fun Saver(
            startToEndThreshold: Float,
            endToStartThreshold: Float,
            confirmValueChange: (SwipeToDismissBoxValue) -> Boolean
        ) = Saver<NoVelocitySwipeToDismissState, SwipeToDismissBoxValue>(
            save = { it.currentValue },
            restore = { NoVelocitySwipeToDismissState(it, startToEndThreshold, endToStartThreshold, confirmValueChange) }
        )
    }
}

/**
 * Remember a NoVelocitySwipeToDismissState with asymmetric thresholds.
 * Uses remember (not rememberSaveable) since swipe state is transient UI state
 * that shouldn't persist across configuration changes.
 *
 * @param startToEndThreshold Threshold for swipe right (dismiss) - default 30%
 * @param endToStartThreshold Threshold for swipe left (schedule/extend) - default 25%
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberNoVelocitySwipeToDismissState(
    initialValue: SwipeToDismissBoxValue = SwipeToDismissBoxValue.Settled,
    startToEndThreshold: Float = 0.3f,
    endToStartThreshold: Float = 0.25f,
    confirmValueChange: (SwipeToDismissBoxValue) -> Boolean = { true }
): NoVelocitySwipeToDismissState {
    return remember {
        NoVelocitySwipeToDismissState(initialValue, startToEndThreshold, endToStartThreshold, confirmValueChange)
    }
}

/**
 * Custom SwipeToDismissBox that only uses positional threshold, ignoring velocity.
 * This prevents accidental dismissals from fast short swipes.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NoVelocitySwipeToDismissBox(
    state: NoVelocitySwipeToDismissState,
    backgroundContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    enableDismissFromStartToEnd: Boolean = true,
    enableDismissFromEndToStart: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                val width = size.width.toFloat()
                state.width = width  // Track width for asymmetric threshold enforcement
                val newAnchors = DraggableAnchors {
                    SwipeToDismissBoxValue.Settled at 0f
                    if (enableDismissFromStartToEnd) {
                        SwipeToDismissBoxValue.StartToEnd at width
                    }
                    if (enableDismissFromEndToStart) {
                        SwipeToDismissBoxValue.EndToStart at -width
                    }
                }
                state.updateAnchors(newAnchors)
            }
    ) {
        Box(
            modifier = Modifier.matchParentSize(),
            content = backgroundContent
        )

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = state.anchoredDraggableState
                            .requireOffset()
                            .roundToInt(),
                        y = 0
                    )
                }
                .anchoredDraggable(
                    state = state.anchoredDraggableState,
                    orientation = Orientation.Horizontal,
                    reverseDirection = isRtl
                ),
            content = content
        )
    }
}
