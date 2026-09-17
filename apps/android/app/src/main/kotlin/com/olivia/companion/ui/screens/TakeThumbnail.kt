package com.olivia.companion.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

@Composable
internal fun TakeThumbnail(
    modifier: Modifier = Modifier
        .size(72.dp)
        .clip(RoundedCornerShape(16.dp)),
) {
    val stripe = MaterialTheme.colorScheme.outlineVariant
    val ground = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = modifier,
    ) {
        drawRect(ground)
        val step = 8.dp.toPx()
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = stripe,
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = 4.dp.toPx(),
            )
            x += step
        }
    }
}
