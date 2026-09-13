package org.openflux.app.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.openflux.app.R

/**
 * The centered "share" context menu, opened from the share button on a
 * profile row. Two ways to hand a profile to another device: copy its
 * openflux://import deep link (see ProfileDeepLink.kt) to the clipboard, or
 * show it as a QR code (see ShareProfileDialog.kt) for a nearby camera.
 */
@Composable
fun ProfileShareMenuDialog(
    onExportClipboard: () -> Unit,
    onShowQr: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column {
                Text(
                    stringResource(R.string.profile_share_menu_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
                ShareMenuRow(
                    icon = Icons.Filled.ContentPaste,
                    label = stringResource(R.string.profile_share_export_clipboard),
                    onClick = onExportClipboard,
                )
                ShareMenuRow(
                    icon = Icons.Filled.QrCode2,
                    label = stringResource(R.string.profile_share_qr_code),
                    onClick = onShowQr,
                )
            }
        }
    }
}

@Composable
private fun ShareMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}