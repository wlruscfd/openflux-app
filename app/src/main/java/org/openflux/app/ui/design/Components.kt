package org.openflux.app.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        text.forEachIndexed { index, ch ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically(Motion.snappy()) { it } + fadeIn(tween(160))) togetherWith
                            (slideOutVertically(tween(180)) { -it } + fadeOut(tween(120)))
                    },
                    modifier = Modifier.clipToBounds(),
                    label = "rolling-char",
                ) { c ->
                    Text(
                        text = c.toString(),
                        style = style.copy(fontFeatureSettings = "tnum"),
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
fun StatPill(
    icon: Painter,
    value: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(modifier = modifier, shape = CircleShape, color = container) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painter = icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            RollingText(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
