package org.openflux.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.openflux.app.R

// Inter (SIL OFL 1.1, google/fonts ofl/inter), bundled since it has full Cyrillic coverage for the app's EN/RU split.
@OptIn(ExperimentalTextApi::class)
private fun interWeight(weight: Int) = Font(
    R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Inter = FontFamily(
    interWeight(400),
    interWeight(500),
    interWeight(600),
    interWeight(700),
)

private val SemiBold = FontWeight.SemiBold
private val Medium = FontWeight.Medium
private val Regular = FontWeight.Normal

// Same type scale (sizes/line-heights) as Material3's default Typography; only family, weight, and tracking change.
val OpenFluxTypography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontWeight = SemiBold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Inter, fontWeight = SemiBold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.25).sp),
    displaySmall = TextStyle(fontFamily = Inter, fontWeight = SemiBold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),

    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),

    titleLarge = TextStyle(fontFamily = Inter, fontWeight = SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.05.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.05.sp),

    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = Regular, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.25.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = Regular, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = Regular, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),

    labelLarge = TextStyle(fontFamily = Inter, fontWeight = SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    // Tighter tracking: the default M3 tracking pushed the longest Russian nav label onto two lines.
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
)
