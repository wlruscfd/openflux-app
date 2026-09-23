package org.openflux.app.ui.home

import android.annotation.SuppressLint
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.openflux.app.R

private const val POLL_INTERVAL_MS = 500L
private const val AUTO_SOLVE_WINDOW_MS = 8_000L

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaWebViewDialog(
    docUrl: String,
    onDismiss: () -> Unit,
    onSolved: (cookies: String) -> Unit,
) {
    val context = LocalContext.current
    var revealed by remember(docUrl) { mutableStateOf(false) }

    val webView = remember(docUrl) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            webViewClient = WebViewClient()
        }
    }

    LaunchedEffect(docUrl) {
        webView.loadUrl(docUrl)
        val deadline = System.currentTimeMillis() + AUTO_SOLVE_WINDOW_MS
        while (isActive) {
            delay(POLL_INTERVAL_MS)
            val solved = suspendCancellableCoroutine { cont ->
                webView.evaluateJavascript("!!document.getElementById('client-config')") { result ->
                    if (cont.isActive) cont.resumeWith(Result.success(result == "true"))
                }
            }
            if (solved) {
                val cookies = CookieManager.getInstance().getCookie(docUrl)
                if (!cookies.isNullOrBlank()) {
                    onSolved(cookies)
                    return@LaunchedEffect
                }
            }
            if (!revealed && System.currentTimeMillis() > deadline) {
                revealed = true
            }
        }
    }

    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }

    if (!revealed) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.captcha_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { webView },
                )

                TextButton(
                    onClick = {
                        val cookies = CookieManager.getInstance().getCookie(docUrl)
                        if (!cookies.isNullOrBlank()) {
                            onSolved(cookies)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    Text(stringResource(R.string.captcha_dialog_continue))
                }
            }
        }
    }
}
