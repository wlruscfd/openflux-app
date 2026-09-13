package org.openflux.app.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openflux.app.R
import org.openflux.app.data.Profile

/**
 * One profile row, shared by the Home tab's quick picker and the full
 * Profiles list: tapping anywhere on the card selects it as active (a
 * device only ever has one active VPN connection); editing and deleting are
 * separate icon actions so they can't be triggered by accident while just
 * choosing which profile to use.
 */
@Composable
fun ProfileRow(
    profile: Profile,
    active: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = onSelect,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Decorative only - onSelect (the whole card) is the actual
                // action, so this doesn't need its own click target.
                RadioButton(selected = active, onClick = null)
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (active) {
                        Text(stringResource(R.string.profiles_active_badge))
                    }
                }
            }
            if (onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.profile_share_title))
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.profile_edit_title))
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.profiles_delete))
                }
            }
        }
    }
}
