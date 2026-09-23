package org.openflux.app.ui.home

import android.annotation.SuppressLint
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.openflux.app.R

/**
 * Shown when Yandex serves a CAPTCHA instead of the doc (see MobileCallback.captchaDocUrl) - the
 * Go transport is headless and can't solve one itself, so this loads the same doc_url in a real
 * WebView and lets the user solve it like they would in a browser. Once the actual doc editor
 * loads (detected the same way the Go side detects it - the presence of the "client-config"
 * script tag), the WebView's own session cookies are handed back to the engine and the dialog
 * closes on its own; the visible chrome is limited to a title and a close button, and nothing
 * from this page is stored or read beyond that one cookie string.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaWebViewDialog(
    docUrl: String,
    onDismiss: () -> Unit,
    onSolved: (cookies: String) -> Unit,
) {
    val context = LocalContext.current

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

                val webView = remember {
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        webView.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                // Same signal the Go side keys off (transport/yandex/yandex.go's fetchDocInfo):
                                // the doc editor's page carries this script tag, a bare CAPTCHA/interstitial page doesn't.
                                view.evaluateJavascript(
                                    "!!document.getElementById('client-config')",
                                ) { result ->
                                    if (result == "true") {
                                        val cookies = CookieManager.getInstance().getCookie(docUrl)
                                        if (!cookies.isNullOrBlank()) {
                                            onSolved(cookies)
                                        }
                                    }
                                }
                            }
                        }
                        webView.loadUrl(docUrl)
                        webView
                    },
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

                DisposableEffect(Unit) {
                    onDispose { webView.destroy() }
                }
            }
        }
    }
}
