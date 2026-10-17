package org.openflux.app.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.openflux.app.data.ThemeDesign

/**
 * The Material You palette is only available from Android 12, so below that the wallpaper cannot
 * colour the app. Rather than falling back to the classic blue, these tones stand in: they are a
 * violet-teal ramp with the tonal separation Material relies on, so contrast and hierarchy hold up
 * even though nothing came from the wallpaper.
 */
private val DynamicFallbackDark: ColorScheme = darkColorScheme(
    primary = Color(0xFFC9C2FF),
    onPrimary = Color(0xFF2C1B75),
    primaryContainer = Color(0xFF423389),
    onPrimaryContainer = Color(0xFFE7E0FF),
    inversePrimary = Color(0xFF9A8DFB),
    secondary = Color(0xFFC6C6DD),
    onSecondary = Color(0xFF2F2F42),
    secondaryContainer = Color(0xFF454659),
    onSecondaryContainer = Color(0xFFE2E1F6),
    tertiary = Color(0xFF7FD8C4),
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF1E5146),
    onTertiaryContainer = Color(0xFF9BF5E1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    surfaceTint = Color(0xFFC9C2FF),
    surfaceDim = Color(0xFF121318),
    surfaceBright = Color(0xFF38393F),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A2A30),
    surfaceContainerHighest = Color(0xFF35353B),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF47464F),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE4E1E9),
    inverseOnSurface = Color(0xFF2F3038),
)

private val DynamicFallbackLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF5B4CB8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4DFFF),
    onPrimaryContainer = Color(0xFF180066),
    inversePrimary = Color(0xFFC9C2FF),
    secondary = Color(0xFF5C5A6E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2DFF6),
    onSecondaryContainer = Color(0xFF191829),
    tertiary = Color(0xFF006B59),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF8FF8E2),
    onTertiaryContainer = Color(0xFF00201A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE5E0EC),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceTint = Color(0xFF5B4CB8),
    surfaceDim = Color(0xFFDCD9E0),
    surfaceBright = Color(0xFFFDFBFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF2ECF6),
    surfaceContainerHigh = Color(0xFFECE7F1),
    surfaceContainerHighest = Color(0xFFE7E1EB),
    outline = Color(0xFF78767F),
    outlineVariant = Color(0xFFC8C5D0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF3EFF7),
)

/** Material You leans on generous rounding; the classic design keeps the tighter app shapes. */
val DynamicShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Whether the chosen design asks for springy, interpolated motion instead of instant swaps. */
@Immutable
data class ThemeMotion(val expressive: Boolean)

val LocalThemeMotion = androidx.compose.runtime.staticCompositionLocalOf { ThemeMotion(false) }

@Composable
fun dynamicColorScheme(darkTheme: Boolean): ColorScheme {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return if (darkTheme) DynamicFallbackDark else DynamicFallbackLight
}

internal fun OpenFluxShapesFor(design: ThemeDesign): Shapes =
    if (design == ThemeDesign.DYNAMIC) DynamicShapes else OpenFluxShapes