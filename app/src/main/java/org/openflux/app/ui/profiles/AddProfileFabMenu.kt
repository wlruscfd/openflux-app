package org.openflux.app.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openflux.app.R

/**
 * The profiles tab's add-profile speed dial (Material 3 "FAB menu"): the
 * same "+" FAB as before, but tapping it expands a short menu of the three
 * ways a profile can be added. The open state is hoisted (expanded +
 * onExpandedChange) so the calling screen can collapse the menu on a
 * tap-anywhere or otherwise react to it.
 *
 * Closed it's a rounded-square "+" button. Tapping it morphs the button into
 * a round close "×" (the plus rotates 45° while the corners round out) and
 * the items cascade into view one by one (nearest to the FAB first). Tapping
 * the FAB again or pressing back folds it back down, top-first. Everything
 * lives in the FAB's own layout slot, so items stay exactly on top of the
 * button - nothing moves when the menu opens.
 */
@Composable
fun AddProfileFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddManually: () -> Unit,
    onAddFromClipboard: () -> Unit,
    onScanQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // "Rounded square + plus" closed; while open the FAB morphs to
    // "circle + rotated plus (a ×)". The corner radius and rotation drive
    // off the same open state so the two morphs are in lockstep.
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(140),
        label = "fabIconRotation",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 100.dp else 12.dp,
        animationSpec = tween(140),
        label = "fabCornerRadius",
    )
    val fabShape = RoundedCornerShape(cornerRadius)

    // Items and the trigger FAB share one right-aligned column, so the whole
    // stack sits on the FAB's axis. The small FABs get an 8dp right pad each
    // so their icon is centred exactly under the trigger's icon - they're
    // 40dp vs the trigger's 56dp, so flush right edges would leave the icons
    // off-centre.
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        FabMenuItem(
            label = stringResource(R.string.profiles_add_manual),
            icon = Icons.Filled.Edit,
            visible = expanded,
            enterDelayMillis = 70,
            exitDelayMillis = 0,
            onClick = { onExpandedChange(false); onAddManually() },
            modifier = Modifier.padding(bottom = 12.dp),
        )
        FabMenuItem(
            label = stringResource(R.string.profiles_add_clipboard),
            icon = Icons.Filled.ContentPaste,
            visible = expanded,
            enterDelayMillis = 35,
            exitDelayMillis = 35,
            onClick = { onExpandedChange(false); onAddFromClipboard() },
            modifier = Modifier.padding(bottom = 12.dp),
        )
        FabMenuItem(
            label = stringResource(R.string.profiles_add_qr),
            icon = Icons.Filled.QrCodeScanner,
            visible = expanded,
            enterDelayMillis = 0,
            exitDelayMillis = 70,
            onClick = { onExpandedChange(false); onScanQr() },
            // Wider gap between the menu and the trigger FAB (whose "+" has
            // turned into the close "×") so they don't crowd one another.
            modifier = Modifier.padding(bottom = 24.dp),
        )

        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            shape = fabShape,
        ) {
            Icon(
                Icons.Filled.Add,
                modifier = Modifier.rotate(rotation),
                contentDescription = stringResource(R.string.profiles_add),
            )
        }
    }

    BackHandler(enabled = expanded) { onExpandedChange(false) }
}

@Composable
private fun FabMenuItem(
    label: String,
    icon: ImageVector,
    visible: Boolean,
    enterDelayMillis: Int,
    exitDelayMillis: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        // Entrance cascades bottom-up (nearest to the FAB first); exit folds
        // top-first back down into the FAB, each item on its own tick.
        enter = expandVertically(
            expandFrom = Alignment.Bottom,
            animationSpec = tween(140, delayMillis = enterDelayMillis),
        ) + fadeIn(animationSpec = tween(90, delayMillis = enterDelayMillis)),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Bottom,
            animationSpec = tween(110, delayMillis = exitDelayMillis),
        ) + fadeOut(animationSpec = tween(70, delayMillis = exitDelayMillis)),
    ) {
        // The whole row is tappable, label and icon alike; the small FAB
        // keeps its own onClick (tap target) but taps anywhere on the row
        // (including the label) trigger the action too.
        Row(
            modifier = modifier.clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 3.dp,
                modifier = Modifier.padding(end = 12.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            SmallFloatingActionButton(
                onClick = onClick,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(icon, contentDescription = null)
            }
        }
    }
}