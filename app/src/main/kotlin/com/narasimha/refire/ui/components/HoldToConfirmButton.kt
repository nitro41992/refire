package com.narasimha.refire.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A button that requires the user to hold it for a specified duration before triggering the action.
 * Used for destructive bulk actions where accidental taps should be prevented.
 *
 * @param holdDurationMs Duration in milliseconds that the user must hold before action triggers
 * @param onConfirm Callback invoked when the hold duration is completed
 * @param modifier Modifier for the component
 */
@Composable
fun HoldToConfirmButton(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdDurationMs: Long = 1000L
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Animation progress from 0f to 1f
    val progress = remember { Animatable(0f) }
    var isHolding by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<Job?>(null) }

    // Track if we've already triggered (to prevent double-fire)
    var hasTriggered by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (hasTriggered) return@detectTapGestures

                        isHolding = true
                        holdJob = scope.launch {
                            // Animate progress from current value to 1f
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = holdDurationMs.toInt(),
                                    easing = LinearEasing
                                )
                            )
                            // If we reached 1f, trigger the action
                            if (progress.value >= 1f && !hasTriggered) {
                                hasTriggered = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onConfirm()
                                // Brief delay then reset
                                delay(200)
                                progress.snapTo(0f)
                                hasTriggered = false
                            }
                        }

                        // Wait for release
                        tryAwaitRelease()

                        // Released early - cancel and reset
                        isHolding = false
                        holdJob?.cancel()
                        if (!hasTriggered) {
                            scope.launch {
                                progress.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isHolding) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f + (progress.value * 0.7f))
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Progress indicator wrapping the icon
            Box(contentAlignment = Alignment.Center) {
                // Background track (always visible, subtle)
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round
                )

                // Actual progress
                CircularProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.size(32.dp),
                    color = if (isHolding) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round
                )

                // Icon in center
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isHolding) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Dismiss all",
                style = MaterialTheme.typography.titleMedium,
                color = if (isHolding) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
