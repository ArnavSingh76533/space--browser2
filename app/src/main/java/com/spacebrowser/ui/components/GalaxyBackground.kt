package com.spacebrowser.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.spacebrowser.core.settings.SpaceSettings
import com.spacebrowser.ui.theme.accentAt
import com.spacebrowser.ui.theme.isAppInDarkTheme
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private class Star(
    val x: Float,        // 0..1 of width
    val y: Float,        // 0..1 of height
    val radius: Float,   // px at 1x density baseline
    val layer: Float,    // 0.3 near .. 1.0 far (parallax speed factor)
    val twinklePhase: Float,
    val twinkleRate: Float,
)

private fun makeStars(count: Int, seed: Int = 42): List<Star> {
    val rnd = Random(seed)
    return List(count) {
        Star(
            x = rnd.nextFloat(),
            y = rnd.nextFloat(),
            radius = 0.8f + rnd.nextFloat() * 2.2f,
            layer = 0.3f + rnd.nextFloat() * 0.7f,
            twinklePhase = rnd.nextFloat() * 6.2832f,
            twinkleRate = 0.5f + rnd.nextFloat() * 1.5f,
        )
    }
}

/**
 * SPACE's signature surface: a drifting, twinkling starfield with two nebula
 * glows and a slow aurora band, all drawn in a single Canvas pass. When the
 * user disables animation (or lowers intensity) it renders one static frame,
 * so low-end devices and battery saver pay almost nothing.
 */
@Composable
fun GalaxyBackground(
    settings: SpaceSettings,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dark = isAppInDarkTheme(settings)
    val accent = accentAt(settings.accentIndex)
    val background = MaterialTheme.colorScheme.background

    val starCount = (140 * settings.animationLevel).toInt().coerceIn(40, 140)
    val stars = remember(starCount) { makeStars(starCount) }

    val time: Float = if (settings.animatedBackground) {
        val transition = rememberInfiniteTransition(label = "galaxy")
        val t by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 60_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "galaxyTime",
        )
        t
    } else 0.35f // pleasant static frame

    Box(modifier = modifier.fillMaxSize().background(background)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val starAlphaBase = if (dark) 1f else 0.35f

            // Nebula glows -----------------------------------------------------
            val nebulaAlpha = (if (dark) 0.16f else 0.10f) * settings.animationLevel
            val n1x = w * (0.25f + 0.05f * sin(time * 6.2832f))
            val n1y = h * (0.30f + 0.04f * sin(time * 6.2832f + 1.7f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.color.copy(alpha = nebulaAlpha), Color.Transparent),
                    center = Offset(n1x, n1y),
                    radius = w * 0.7f,
                ),
                radius = w * 0.7f,
                center = Offset(n1x, n1y),
            )
            val n2x = w * (0.85f - 0.05f * sin(time * 6.2832f + 3.1f))
            val n2y = h * (0.75f + 0.04f * sin(time * 6.2832f + 4.4f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.secondary.copy(alpha = nebulaAlpha), Color.Transparent),
                    center = Offset(n2x, n2y),
                    radius = w * 0.6f,
                ),
                radius = w * 0.6f,
                center = Offset(n2x, n2y),
            )

            // Aurora band ------------------------------------------------------
            val auroraAlpha = (if (dark) 0.08f else 0.05f) * settings.animationLevel
            val bandCenter = h * (0.15f + 0.70f * ((sin(time * 6.2832f * 0.5f) + 1f) / 2f))
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        accent.secondary.copy(alpha = auroraAlpha),
                        accent.color.copy(alpha = auroraAlpha * 0.7f),
                        Color.Transparent,
                    ),
                    startY = bandCenter - h * 0.25f,
                    endY = bandCenter + h * 0.25f,
                ),
            )

            // Starfield --------------------------------------------------------
            for (star in stars) {
                // Parallax drift: nearer layers move faster; wrap around.
                val drift = (time * star.layer * 0.6f)
                val x = ((star.x + drift) % 1f) * w
                val y = star.y * h
                val twinkle = 0.35f + 0.65f * abs(sin(time * 6.2832f * star.twinkleRate + star.twinklePhase))
                drawCircle(
                    color = Color.White.copy(alpha = starAlphaBase * twinkle * (0.4f + 0.6f * star.layer)),
                    radius = star.radius * (0.7f + 0.3f * star.layer),
                    center = Offset(x, y),
                )
            }
        }
        content()
    }
}
