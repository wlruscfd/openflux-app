package org.openflux.app.ui.profiles

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openflux.app.R
import org.openflux.app.data.Profile
import org.openflux.app.data.ProfileDeepLink

private const val QR_SIZE_PX = 640

/**
 * Shows a profile's openflux://import deep link as a QR code (for a nearby
 * device's camera), with a Send action to hand the link to any app. - see
 * ProfileDeepLink.kt for the link format. Works for an unsaved draft too:
 * the link is built purely from the profile's fields, never its id.
 */
@Composable
fun ShareProfileDialog(profile: Profile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val link = remember(profile) { ProfileDeepLink.buildUri(profile).toString() }
    val qrBitmap = remember(link) { generateQrBitmap(link, QR_SIZE_PX) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_share_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = null,
                        // Fill the dialog's width while staying square - a
                        // safe-size QR scans better than a small centred one.
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                } else {
                    Text(stringResource(R.string.profile_share_qr_error))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, link)
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            }) { Text(stringResource(R.string.profile_share_send)) }
        },
    )
}
