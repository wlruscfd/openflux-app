package org.openflux.app.ui.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openflux.app.R
import org.openflux.app.data.UiStyle

val LocalUiStyle = staticCompositionLocalOf { UiStyle.STANDARD }

@Composable
fun isExpressive(): Boolean = LocalUiStyle.current == UiStyle.EXPRESSIVE

@Composable
fun StyledBackdrop(
    expressive: Boolean,
    intensity: Float,
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable () -> Unit,
) {
    if (expressive) {
        AuroraBackground(modifier = modifier, intensity = intensity, content = content)
    } else {
        Box(modifier = modifier) { content() }
    }
}

@Composable
fun UiStyleChooser(
    style: UiStyle,
    onChange: (UiStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = style == UiStyle.STANDARD,
                onClick = { onChange(UiStyle.STANDARD) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.settings_ui_style_standard)) }
            SegmentedButton(
                selected = style == UiStyle.EXPRESSIVE,
                onClick = { onChange(UiStyle.EXPRESSIVE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.settings_ui_style_expressive)) }
        }
        Text(
            text = stringResource(
                if (style == UiStyle.EXPRESSIVE) R.string.settings_ui_style_expressive_hint else R.string.settings_ui_style_standard_hint,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
