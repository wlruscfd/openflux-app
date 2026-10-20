package org.openflux.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import org.openflux.app.R

@Composable
fun ConnectionButtonClassic(
    state: ConnectionButtonState,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    accentColor: Color? = null,
    surfaceColor: Color = Color.White,
    enabled: Boolean = state != ConnectionButtonState.Connecting,
) {
    val accent = accentColor ?: defaultAccentFor(state)
    val phase by animateFloatAsState(
        targetValue = state.phase,
        animationSpec = tween(durationMillis = 680, easing = EmphasizedEasing),
        label = "connection-button-phase",
    )
    val emphasis by animateFloatAsState(
        targetValue = state.emphasis,
        animationSpec = tween(durationMillis = 680, easing = EmphasizedEasing),
        label = "connection-button-emphasis",
    )

    // Must use the actual measured size, not a fixed px canvas - a hardcoded 148f misplaced the clip path at 3x density.
    val shape = remember(phase) {
        GenericShape { size, _ ->
            addPath(buildCookiePath(size.width, size.height, phase))
        }
    }
    val scale = 1f + emphasis * 0.06f

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .drawBehind { drawGlow(accent, emphasis) }
                .clip(shape)
                .background(surfaceColor)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(size * 0.23f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_power),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(16.dp))

        AnimatedContent(
            targetState = label,
            transitionSpec = {
                fadeIn(tween(280, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(tween(200, easing = FastOutSlowInEasing))
            },
            label = "connection-button-label",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = if (state == ConnectionButtonState.Connected) {
                    accent
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

/** No separate spinner state exists here, so the blue "busy" accent stands in for it. */
@Composable
private fun defaultAccentFor(state: ConnectionButtonState): Color = when (state) {
    ConnectionButtonState.Connected -> ConnectedGreen
    else -> MaterialTheme.colorScheme.primary
}

private val ConnectedGreen = Color(0xFF10B981)

private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private const val Samples = 96
private val BaseCos = FloatArray(Samples) { cos(it / Samples.toFloat() * PI.toFloat() * 2f) }
private val BaseSin = FloatArray(Samples) { sin(it / Samples.toFloat() * PI.toFloat() * 2f) }

private val CircleRadii = FloatArray(Samples) { 1f }
private val ConnectingRadii = cookieRadii(sides = 5, depth = 0.12f, sharpness = 1.35f)
private val ConnectedRadii = cookieRadii(sides = 8, depth = 0.1f, sharpness = 1.08f)
private val EstablishingRadii = cookieRadii(sides = 6, depth = 0.12f, sharpness = 1.08f)

private fun cookieRadii(sides: Int, depth: Float, sharpness: Float): FloatArray =
    FloatArray(Samples) { index ->
        val theta = index / Samples.toFloat() * PI.toFloat() * 2f
        val lobe = (1f + cos(sides * (theta - (-PI.toFloat() / 2f)))) / 2f
        val valley = (1f - lobe).pow(sharpness)
        (1f - depth * valley).coerceIn(0f, 1f)
    }

// Interpolates between adjacent pre-computed shapes so a state change reads as unfolding, not a hard cut.
private fun buildCookiePath(width: Float, height: Float, phase: Float): Path {
    val p = phase.coerceIn(0f, 3f)
    val (from, to, t) = when {
        p <= 1f -> Triple(CircleRadii, ConnectingRadii, p)
        p <= 2f -> Triple(ConnectingRadii, ConnectedRadii, p - 1f)
        else -> Triple(ConnectedRadii, EstablishingRadii, p - 2f)
    }

    val centerX = width / 2f
    val centerY = height / 2f
    val radius = min(width, height) / 2f
    val xs = FloatArray(Samples)
    val ys = FloatArray(Samples)
    for (i in 0 until Samples) {
        val r = radius * (from[i] + (to[i] - from[i]) * t)
        xs[i] = centerX + BaseCos[i] * r
        ys[i] = centerY + BaseSin[i] * r
    }

    val path = Path()
    path.moveTo((xs[0] + xs[1]) / 2f, (ys[0] + ys[1]) / 2f)
    for (i in 1 until Samples) {
        val next = (i + 1) % Samples
        path.quadraticTo(xs[i], ys[i], (xs[i] + xs[next]) / 2f, (ys[i] + ys[next]) / 2f)
    }
    path.quadraticTo(xs[0], ys[0], (xs[0] + xs[1]) / 2f, (ys[0] + ys[1]) / 2f)
    path.close()
    return path
}

// Radial gradient, not a blurred silhouette: setShadowLayer on a Path is ignored on hardware layers below API 28.
private fun DrawScope.drawGlow(accent: Color, emphasis: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f
    val reach = radius + 16f + emphasis * 12f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.38f), Color.Transparent),
            center = center,
            radius = reach,
        ),
        radius = reach,
        center = center,
    )
}
