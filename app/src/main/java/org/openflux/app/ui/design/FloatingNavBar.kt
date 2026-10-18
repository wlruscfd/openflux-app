package org.openflux.app.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class NavBarItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun FloatingNavBar(
    items: List<NavBarItem>,
    selectedRoute: String?,
    onSelect: (NavBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 14.dp,
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val selected = item.route == selectedRoute
                    NavPill(
                        item = item,
                        selected = selected,
                        modifier = Modifier.weight(animateWeight(selected)),
                        onClick = {
                            if (!selected) haptics.tick()
                            onSelect(item)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun animateWeight(selected: Boolean): Float {
    val weight by animateFloatAsState(
        targetValue = if (selected) 2.3f else 1f,
        animationSpec = Motion.bouncy(),
        label = "nav-weight",
    )
    return weight.coerceAtLeast(0.05f)
}

@Composable
private fun NavPill(
    item: NavBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = Motion.smooth(),
        label = "nav-container",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = Motion.smooth(),
        label = "nav-content",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = Motion.bouncy(),
        label = "nav-icon-scale",
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .height(56.dp)
            .pressScale(interaction, 0.9f)
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = item.icon,
                contentDescription = if (selected) null else item.label,
                tint = content,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(Motion.bouncy()) + fadeIn(),
                exit = shrinkHorizontally(Motion.smooth()) + fadeOut(),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
