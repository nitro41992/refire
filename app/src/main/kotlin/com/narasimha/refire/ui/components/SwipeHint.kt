package com.narasimha.refire.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Displays swipe action hints with gesture icons.
 * Left side shows swipe-right action, right side shows swipe-left action.
 */
@Composable
fun SwipeHint(
    leftLabel: String?,
    rightLabel: String,
    modifier: Modifier = Modifier
) {
    val hintColor = MaterialTheme.colorScheme.outline

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left hint (swipe right action)
        if (leftLabel != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SwipeRight,
                    contentDescription = null,
                    tint = hintColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = leftLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = hintColor
                )
            }
        } else {
            // Empty spacer for alignment when no left action
            Row {}
        }

        // Right hint (swipe left action)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.labelSmall,
                color = hintColor
            )
            Icon(
                imageVector = Icons.Default.SwipeLeft,
                contentDescription = null,
                tint = hintColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
