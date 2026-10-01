package org.openflux.app.ui.profiles

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openflux.app.R
import org.openflux.app.data.CookiePushOutcome
import org.openflux.app.data.CookiePushRecord
import org.openflux.app.data.Profile
import org.openflux.app.data.transportLabel

// Editing/deleting are separate icon actions so they can't be triggered by accident while just selecting.
@Composable
fun ProfileRow(
    profile: Profile,
    active: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    cookieRecord: CookiePushRecord? = null,
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val selectionScale by animateFloatAsState(
        targetValue = if (active) 1.06f else 1f,
        animationSpec = tween(220),
        label = "profile-active-scale",
    )

    androidx.compose.material3.Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .scale(selectionScale),
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
                // Decorative only - the whole card's onSelect is the actual action.
                RadioButton(selected = active, onClick = null)
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ProfileSubtitles(profile, cookieRecord)
                }
            }
            if (onShare != null) {
                IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = stringResource(R.string.profile_share_title),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp).padding(start = 4.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.profile_edit_title),
                    modifier = Modifier.size(18.dp),
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).padding(start = 4.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.profiles_delete),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSubtitles(profile: Profile, cookieRecord: CookiePushRecord?) {
    val transportColor by animateColorAsState(
        targetValue = if (profile.mode == org.openflux.app.data.ProfileMode.MANUAL) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(220),
        label = "profile-transport-color",
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.profile_transport_label) + ": " + transportLabel(profile.manualTransport),
            style = MaterialTheme.typography.labelSmall,
            color = transportColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        CookieBadge(cookieRecord, Modifier.weight(1f, fill = false).padding(start = 8.dp))
    }
}

@Composable
private fun CookieBadge(record: CookiePushRecord?, modifier: Modifier = Modifier) {
    if (record == null || record.outcome == CookiePushOutcome.NONE) return
    val sent = record.outcome == CookiePushOutcome.SENT
    Box(
        modifier = modifier
            .background(
                color = if (sent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                },
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (sent) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (sent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = if (sent) {
                    stringResource(R.string.profiles_cookies_badge_sent)
                } else {
                    record.reason?.let { stringResource(it.messageRes()) }
                        ?: stringResource(R.string.profiles_cookies_badge_failed)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (sent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
