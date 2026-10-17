package org.openflux.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.openflux.app.data.ThemeDesign

private val ThemeTween = tween<Color>(durationMillis = 420, easing = androidx.compose.animation.core.FastOutSlowInEasing)

// Every role is set explicitly; anything left unset falls back to M3's stock purple baseline.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BA2F5),
    onPrimary = Color(0xFF071224),
    primaryContainer = Color(0xFF223A5E),
    onPrimaryContainer = Color(0xFFD3E2FF),
    inversePrimary = Color(0xFF3D6FD6),
    secondary = Color(0xFF9AA8C7),
    onSecondary = Color(0xFF16202F),
    secondaryContainer = Color(0xFF2B3547),
    onSecondaryContainer = Color(0xFFD7DEEC),
    tertiary = Color(0xFFE0AC2B),
    onTertiary = Color(0xFF2A1E00),
    tertiaryContainer = Color(0xFF4A3800),
    onTertiaryContainer = Color(0xFFFFDFA0),
    error = Color(0xFFEF5A5A),
    onError = Color(0xFF2A0A0A),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0A0E16),
    onBackground = Color(0xFFE8ECF4),
    surface = Color(0xFF121826),
    onSurface = Color(0xFFE8ECF4),
    surfaceVariant = Color(0xFF171F30),
    onSurfaceVariant = Color(0xFF8993A8),
    surfaceTint = Color(0xFF7BA2F5),
    surfaceDim = Color(0xFF0A0E16),
    surfaceBright = Color(0xFF2A3142),
    surfaceContainerLowest = Color(0xFF060A11),
    surfaceContainerLow = Color(0xFF0F1420),
    surfaceContainer = Color(0xFF141B29),
    surfaceContainerHigh = Color(0xFF1B2333),
    surfaceContainerHighest = Color(0xFF232C3E),
    outline = Color(0xFF3A4457),
    outlineVariant = Color(0xFF272F40),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE8ECF4),
    inverseOnSurface = Color(0xFF1A2233),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D6FD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6FB),
    onPrimaryContainer = Color(0xFF14284F),
    inversePrimary = Color(0xFF7BA2F5),
    secondary = Color(0xFF5B6472),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E6F0),
    onSecondaryContainer = Color(0xFF1B2331),
    tertiary = Color(0xFF9C6F00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE0A3),
    onTertiaryContainer = Color(0xFF2E2100),
    error = Color(0xFFC4453D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF1A2233),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2233),
    surfaceVariant = Color(0xFFEEF1F7),
    onSurfaceVariant = Color(0xFF5B6472),
    surfaceTint = Color(0xFF3D6FD6),
    surfaceDim = Color(0xFFDCE0E9),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F5FA),
    surfaceContainer = Color(0xFFEDF0F6),
    surfaceContainerHigh = Color(0xFFE7EBF2),
    surfaceContainerHighest = Color(0xFFE1E6EE),
    outline = Color(0xFFD8DEE9),
    outlineVariant = Color(0xFFE7EBF2),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2E3648),
    inverseOnSurface = Color(0xFFF1F4F9),
)

/**
 * The app's theme. [design] picks the visual language: CLASSIC is the fixed palette, DYNAMIC is
 * Material You. [darkTheme] decides light or dark and normally follows the system.
 *
 * Switching either animates rather than snapping: colours cross-fade over a short spring, so a
 * design change reads as the app changing rather than the screen flashing.
 */
@Composable
fun OpenFluxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    design: ThemeDesign = ThemeDesign.DYNAMIC,
    content: @Composable () -> Unit,
) {
    val target = when (design) {
        ThemeDesign.DYNAMIC -> dynamicColorScheme(darkTheme)
        ThemeDesign.CLASSIC -> if (darkTheme) DarkColors else LightColors
    }
    val colors = animatedColorScheme(target)
    val shapes = OpenFluxShapesFor(design)

    // Recolors the system status/nav bars to match, instead of leaving the pre-Compose window theme's color.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalThemeMotion provides ThemeMotion(design == ThemeDesign.DYNAMIC)) {
        MaterialTheme(
            colorScheme = colors,
            typography = OpenFluxTypography,
            shapes = shapes,
            content = content,
        )
    }
}

/**
 * Cross-fades every role of the scheme instead of swapping it. A design change then reads as the
 * app recolouring itself rather than a hard cut, which is most of what makes the switch feel finished
 * rather than merely different.
 */
@Composable
private fun animatedColorScheme(target: ColorScheme): ColorScheme {
    val primary by animateColorAsState(target.primary, ThemeTween)
    val onPrimary by animateColorAsState(target.onPrimary, ThemeTween)
    val primaryContainer by animateColorAsState(target.primaryContainer, ThemeTween)
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, ThemeTween)
    val inversePrimary by animateColorAsState(target.inversePrimary, ThemeTween)
    val secondary by animateColorAsState(target.secondary, ThemeTween)
    val onSecondary by animateColorAsState(target.onSecondary, ThemeTween)
    val secondaryContainer by animateColorAsState(target.secondaryContainer, ThemeTween)
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, ThemeTween)
    val tertiary by animateColorAsState(target.tertiary, ThemeTween)
    val onTertiary by animateColorAsState(target.onTertiary, ThemeTween)
    val tertiaryContainer by animateColorAsState(target.tertiaryContainer, ThemeTween)
    val onTertiaryContainer by animateColorAsState(target.onTertiaryContainer, ThemeTween)
    val error by animateColorAsState(target.error, ThemeTween)
    val onError by animateColorAsState(target.onError, ThemeTween)
    val errorContainer by animateColorAsState(target.errorContainer, ThemeTween)
    val onErrorContainer by animateColorAsState(target.onErrorContainer, ThemeTween)
    val background by animateColorAsState(target.background, ThemeTween)
    val onBackground by animateColorAsState(target.onBackground, ThemeTween)
    val surface by animateColorAsState(target.surface, ThemeTween)
    val onSurface by animateColorAsState(target.onSurface, ThemeTween)
    val surfaceVariant by animateColorAsState(target.surfaceVariant, ThemeTween)
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, ThemeTween)
    val surfaceTint by animateColorAsState(target.surfaceTint, ThemeTween)
    val surfaceDim by animateColorAsState(target.surfaceDim, ThemeTween)
    val surfaceBright by animateColorAsState(target.surfaceBright, ThemeTween)
    val surfaceContainerLowest by animateColorAsState(target.surfaceContainerLowest, ThemeTween)
    val surfaceContainerLow by animateColorAsState(target.surfaceContainerLow, ThemeTween)
    val surfaceContainer by animateColorAsState(target.surfaceContainer, ThemeTween)
    val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, ThemeTween)
    val surfaceContainerHighest by animateColorAsState(target.surfaceContainerHighest, ThemeTween)
    val outline by animateColorAsState(target.outline, ThemeTween)
    val outlineVariant by animateColorAsState(target.outlineVariant, ThemeTween)
    val scrim by animateColorAsState(target.scrim, ThemeTween)
    val inverseSurface by animateColorAsState(target.inverseSurface, ThemeTween)
    val inverseOnSurface by animateColorAsState(target.inverseOnSurface, ThemeTween)

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        surfaceDim = surfaceDim,
        surfaceBright = surfaceBright,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
    )
}
