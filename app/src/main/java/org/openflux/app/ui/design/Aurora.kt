package org.openflux.app.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TwoPi = (2 * PI).toFloat()

/** Soft colour blobs that drift behind a screen; the colours come from the wallpaper-derived scheme and swell with [intensity]. */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 0f,
    content: @Composable () -> Unit,
) {
    val strength by animateFloatAsState(intensity, tween(1200), label = "aurora-intensity")
    val transition = rememberInfiniteTransition(label = "aurora")
    val a = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart), label = "a")
    val b = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(34000, easing = LinearEasing), RepeatMode.Restart), label = "b")
    val c = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(43000, easing = LinearEasing), RepeatMode.Restart), label = "c")
    val scheme = MaterialTheme.colorScheme
    val first = scheme.primary
    val second = scheme.tertiary
    val third = scheme.secondary

    Box(
        modifier = modifier.drawBehind {
            val w = size.width
            val h = size.height
            val base = 0.16f + 0.2f * strength
            blob(first, Offset(w * (0.2f + 0.3f * sin(a.value * TwoPi)), h * (0.16f + 0.08f * cos(b.value * TwoPi))), w * 0.95f, base)
            blob(second, Offset(w * (0.85f + 0.2f * cos(b.value * TwoPi)), h * (0.5f + 0.14f * sin(c.value * TwoPi))), w * 0.85f, base * 0.85f)
            blob(third, Offset(w * (0.35f + 0.3f * sin(c.value * TwoPi + 1.3f)), h * (0.9f + 0.06f * cos(a.value * TwoPi))), w * 0.8f, base * 0.7f)
        },
    ) {
        content()
    }
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
