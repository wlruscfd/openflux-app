package org.openflux.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import org.openflux.app.R
import org.openflux.app.ui.design.Motion
import org.openflux.app.ui.design.pressScale
import org.openflux.app.ui.design.rememberHaptics

// The cookie shape is ported from Etonify (yamixdev/Etonify, GPL-3.0) home_connection_button.dart.
enum class ConnectionButtonState(
    internal val phase: Float,
    internal val emphasis: Float,
) {
    Idle(phase = 0f, emphasis = 0f),
    Connecting(phase = 1f, emphasis = 0.52f),
    Establishing(phase = 3f, emphasis = 0.96f),
    Connected(phase = 2f, emphasis = 1.16f),
}

private const val TwoPi = (2 * PI).toFloat()

@Composable
fun ConnectionButton(
    state: ConnectionButtonState,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 176.dp,
    enabled: Boolean = state != ConnectionButtonState.Connecting,
) {
    val scheme = MaterialTheme.colorScheme
    val live = state == ConnectionButtonState.Connected
    val busy = state == ConnectionButtonState.Connecting || state == ConnectionButtonState.Establishing

    val face by animateColorAsState(
        targetValue = when {
            live -> scheme.primary
            busy -> scheme.secondaryContainer
            else -> scheme.primaryContainer
        },
        animationSpec = tween(520, easing = EmphasizedEasing),
        label = "connection-face",
    )
    val icon by animateColorAsState(
        targetValue = when {
            live -> scheme.onPrimary
            busy -> scheme.onSecondaryContainer
            else -> scheme.onPrimaryContainer
        },
        animationSpec = tween(520, easing = EmphasizedEasing),
        label = "connection-icon",
    )
    val accent by animateColorAsState(
        targetValue = if (busy) scheme.secondary else scheme.primary,
        animationSpec = tween(520, easing = EmphasizedEasing),
        label = "connection-accent",
    )
    val phase by animateFloatAsState(state.phase, tween(720, easing = EmphasizedEasing), label = "connection-phase")
    val emphasis by animateFloatAsState(state.emphasis, tween(720, easing = EmphasizedEasing), label = "connection-emphasis")
    val liveMix by animateFloatAsState(if (live) 1f else 0f, tween(700, easing = EmphasizedEasing), label = "connection-live")
    val busyMix by animateFloatAsState(if (busy) 1f else 0f, tween(700, easing = EmphasizedEasing), label = "connection-busy")
    val restMix by animateFloatAsState(if (!live && !busy) 1f else 0f, tween(700, easing = EmphasizedEasing), label = "connection-rest")

    val infinite = rememberInfiniteTransition(label = "connection-loop")
    val ripple = infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "ripple")
    val wave = infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "wave")
    val spin = infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val breath = infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)), RepeatMode.Reverse), label = "breath")

    val shape = remember(phase) {
        GenericShape { box, _ -> addPath(buildCookiePath(box.width, box.height, phase)) }
    }
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val punch = remember { Animatable(1f) }
    LaunchedEffect(state) {
        punch.snapTo(0.84f)
        punch.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 210f))
    }
    val scale = (1f + emphasis * 0.05f) * punch.value

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(size * 1.7f), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(size * 1.7f)) {
                val core = this.size.minDimension / 2f / 1.7f
                drawBreathingHalo(accent, core, breath.value, restMix, liveMix)
                drawWaveRings(accent, core, wave.value, busyMix)
                drawRipples(accent, core, ripple.value, liveMix)
            }
            Box(
                modifier = Modifier
                    .size(size)
                    .scale(scale)
                    .graphicsLayer { rotationZ = spin.value * (0.35f * busyMix + 0.08f * liveMix) }
                    .pressScale(interaction, 0.92f)
                    .clip(shape)
                    .background(face)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        interactionSource = interaction,
                        indication = null,
                        onClick = {
                            haptics.confirm()
                            onClick()
                        },
                    )
                    .padding(size * 0.27f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_power),
                    contentDescription = null,
                    tint = icon,
                    modifier = Modifier
                        .size(size * 0.46f)
                        .graphicsLayer { rotationZ = -spin.value * (0.35f * busyMix + 0.08f * liveMix) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        AnimatedContent(
            targetState = label,
            transitionSpec = {
                (slideInVertically(Motion.bouncy()) { it / 2 } + fadeIn(tween(260))) togetherWith
                    (slideOutVertically(tween(220)) { -it / 2 } + fadeOut(tween(160)))
            },
            label = "connection-label",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (live) scheme.primary else scheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private fun DrawScope.drawBreathingHalo(color: Color, core: Float, breath: Float, restMix: Float, liveMix: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val reach = core * (1.55f + 0.2f * breath)
    val alpha = 0.34f * restMix + 0.2f * liveMix
    if (alpha <= 0.001f) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha * (0.7f + 0.3f * breath)), Color.Transparent),
            center = center,
            radius = reach,
        ),
        radius = reach,
        center = center,
    )
}

private fun DrawScope.drawWaveRings(color: Color, core: Float, t: Float, mix: Float) {
    if (mix <= 0.001f) return
    val center = Offset(size.width / 2f, size.height / 2f)
    val stroke = 3.dp.toPx()
    repeat(2) { ring ->
        val direction = if (ring == 0) 1f else -1f
        val base = core * (1.28f + 0.16f * ring)
        val amplitude = core * (0.045f + 0.02f * ring)
        val lobes = 7 + ring * 3
        val path = Path()
        val steps = 140
        for (i in 0..steps) {
            val theta = i / steps.toFloat() * TwoPi
            val r = base + amplitude * sin(lobes * theta + direction * t * TwoPi * 2f)
            val x = center.x + r * cos(theta)
            val y = center.y + r * sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color.copy(alpha = (0.75f - 0.3f * ring) * mix), style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

private fun DrawScope.drawRipples(color: Color, core: Float, t: Float, mix: Float) {
    if (mix <= 0.001f) return
    val center = Offset(size.width / 2f, size.height / 2f)
    repeat(3) { i ->
        val p = (t + i / 3f) % 1f
        val radius = core * (1.05f + 0.62f * p)
        val alpha = (1f - p).pow(1.6f) * 0.42f * mix
        drawCircle(color = color.copy(alpha = alpha), radius = radius, center = center, style = Stroke(width = (4f - 2.5f * p).dp.toPx()))
    }
}

private const val Samples = 96
private val BaseCos = FloatArray(Samples) { cos(it / Samples.toFloat() * TwoPi) }
private val BaseSin = FloatArray(Samples) { sin(it / Samples.toFloat() * TwoPi) }

private val CircleRadii = FloatArray(Samples) { 1f }
private val ConnectingRadii = cookieRadii(sides = 5, depth = 0.12f, sharpness = 1.35f)
private val ConnectedRadii = cookieRadii(sides = 8, depth = 0.1f, sharpness = 1.08f)
private val EstablishingRadii = cookieRadii(sides = 6, depth = 0.12f, sharpness = 1.08f)

private fun cookieRadii(sides: Int, depth: Float, sharpness: Float): FloatArray =
    FloatArray(Samples) { index ->
        val theta = index / Samples.toFloat() * TwoPi
        val lobe = (1f + cos(sides * (theta - (-PI.toFloat() / 2f)))) / 2f
        val valley = (1f - lobe).pow(sharpness)
        (1f - depth * valley).coerceIn(0f, 1f)
    }

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
