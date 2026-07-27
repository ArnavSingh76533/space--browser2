package com.spacebrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue

/** Translucent "glass" panel used for bars, sheets and cards. */
fun Modifier.glass(
    shape: Shape,
    surface: Color,
    borderColor: Color,
    alpha: Float = 0.72f,
): Modifier = this
    .clip(shape)
    .background(surface.copy(alpha = alpha))
    .border(1.dp, borderColor.copy(alpha = 0.25f), shape)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier.glass(
            shape = shape,
            surface = MaterialTheme.colorScheme.surface,
            borderColor = MaterialTheme.colorScheme.primary,
        ),
    ) { content() }
}

/** Deterministic two-color gradient avatar with the site's first letter. */
@Composable
fun LetterAvatar(text: String, size: Dp = 44.dp) {
    val letter = text.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"
    val palette = listOf(
        Color(0xFF8B5CF6), Color(0xFF22D3EE), Color(0xFFEC4899), Color(0xFFF59E0B),
        Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFFF87171), Color(0xFFA78BFA),
    )
    val i = text.hashCode().absoluteValue % palette.size
    val j = (i + 3) % palette.size
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(palette[i], palette[j]))),
    ) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold)
    }
}
