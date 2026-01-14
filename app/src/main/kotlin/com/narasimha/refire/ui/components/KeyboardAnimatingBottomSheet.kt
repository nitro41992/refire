package com.narasimha.refire.ui.components

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

/**
 * A custom bottom sheet that synchronizes its animation with the keyboard.
 * Uses Dialog + WindowInsetsAnimationCompat for smooth keyboard coordination.
 *
 * On Android 11+ (API 30+): Pixel-perfect keyboard sync
 * On older versions: Falls back to adjustResize behavior
 */
@Composable
fun KeyboardAnimatingBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Track internal visibility for exit animation
    var showDialog by remember { mutableStateOf(false) }
    var animateContent by remember { mutableStateOf(false) }

    // Show dialog when visible becomes true
    LaunchedEffect(visible) {
        if (visible) {
            showDialog = true
            // Small delay to ensure dialog is mounted before animating content
            kotlinx.coroutines.delay(16)
            animateContent = true
        } else {
            animateContent = false
            // Wait for exit animation to complete before hiding dialog
            kotlinx.coroutines.delay(250)
            showDialog = false
        }
    }

    // Track if we're in the process of dismissing (for exit animation)
    var isDismissing by remember { mutableStateOf(false) }

    // Handle delayed dismiss after exit animation
    LaunchedEffect(isDismissing) {
        if (isDismissing) {
            animateContent = false
            kotlinx.coroutines.delay(250) // Wait for exit animation
            isDismissing = false
            onDismiss()
        }
    }

    if (showDialog) {
        Dialog(
            onDismissRequest = {
                if (!isDismissing) {
                    isDismissing = true
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false // We handle scrim clicks ourselves
            )
        ) {
            // Setup edge-to-edge for the dialog window
            SetupDialogWindow()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding() // Apply here so sheet stays above keyboard
            ) {
                // Scrim with fade animation
                AnimatedVisibility(
                    visible = animateContent,
                    enter = fadeIn(animationSpec = tween(150)),
                    exit = fadeOut(animationSpec = tween(150))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.32f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (!isDismissing) {
                                        isDismissing = true
                                    }
                                }
                            )
                    )
                }

                // Bottom sheet content with slide animation
                AnimatedVisibility(
                    visible = animateContent,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                    )
                ) {
                    Surface(
                        modifier = modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { /* Consume clicks on sheet */ }
                            ),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp
                    ) {
                        Column {
                            // Drag handle
                            DragHandle()
                            content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Material 3 style drag handle for the bottom sheet
 */
@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 22.dp)
                .size(width = 32.dp, height = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

/**
 * Configure the dialog window for edge-to-edge display and keyboard animations
 */
@Composable
private fun SetupDialogWindow() {
    val view = LocalView.current

    DisposableEffect(view) {
        // Get the dialog window from the view hierarchy
        val window = (view.parent as? DialogWindowProvider)?.window

        if (window == null) {
            android.util.Log.w("KeyboardBottomSheet", "DialogWindowProvider not found - keyboard insets may not work correctly")
        }

        window?.let { w ->
            // Enable edge-to-edge - critical for inset handling
            WindowCompat.setDecorFitsSystemWindows(w, false)

            // Don't let the system adjust the window - let Compose handle insets
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

            // Make window full screen for proper scrim
            w.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Transparent background for custom scrim
            w.setBackgroundDrawableResource(android.R.color.transparent)
        }
        onDispose { }
    }
}

/**
 * Optional: Wrapper that provides keyboard animation progress for custom animations.
 * Can be used to sync other animations with the keyboard.
 */
@Composable
fun rememberKeyboardAnimationProgress(): Float {
    var progress by remember { mutableStateOf(0f) }
    val view = LocalView.current

    DisposableEffect(view) {
        val callback = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: List<WindowInsetsAnimationCompat>
            ): WindowInsetsCompat {
                val imeAnimation = runningAnimations.find {
                    it.typeMask and WindowInsetsCompat.Type.ime() != 0
                }
                progress = imeAnimation?.interpolatedFraction ?: 0f
                return insets
            }
        }

        ViewCompat.setWindowInsetsAnimationCallback(view, callback)

        onDispose {
            ViewCompat.setWindowInsetsAnimationCallback(view, null)
        }
    }

    return progress
}
