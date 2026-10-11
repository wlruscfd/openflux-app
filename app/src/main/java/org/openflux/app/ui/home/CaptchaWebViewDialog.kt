package org.openflux.app.ui.home

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.core.content.ContextCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.openflux.app.R
import kotlin.coroutines.resume

private const val POLL_INTERVAL_MS = 700L
private const val TAG = "OpenFluxCaptcha"

private const val ACCEPT_CONSENT_JS = """
(function () {
  var wanted = ['Allow all', 'Разрешить все', 'Accept all', 'Allow essential cookies',
               'Разрешить обязательные'];
  var nodes = document.querySelectorAll('button, [role=button], a');
  for (var i = 0; i < nodes.length; i++) {
    var t = (nodes[i].innerText || nodes[i].textContent || '').trim();
    for (var j = 0; j < wanted.length; j++) {
      if (t === wanted[j]) { nodes[i].click(); return 'clicked:' + t; }
    }
  }
  return 'none';
})();
"""

private val PROBE_JS = """
(function () {
  try {
    return [
      'href=' + location.href,
      'ready=' + document.readyState,
      'title=' + document.title,
      'bodyLen=' + (document.body ? document.body.innerText.length : -1),
      'frames=' + document.querySelectorAll('iframe').length,
      'imgs=' + document.querySelectorAll('img').length,
      'online=' + navigator.onLine,
      'docCookie=' + document.cookie.replace(/=[^;]*/g, '=').slice(0, 220)
    ].join(' | ');
  } catch (e) {
    return 'probeError=' + e;
  }
})();
"""

private val READY_JS = """
(function () {
  if (location.pathname.toLowerCase().indexOf('captcha') !== -1) return false;
  return document.readyState === 'complete' &&
         !!document.getElementById('client-config');
})();
"""

/**
 * Solves a provider check in a host-rendered WebView and hands the resulting session back.
 *
 * [proxyHostPort] points at the tunnel's own local SOCKS5 proxy. Routing the page through it is
 * what makes the pass usable by the exit node: the provider binds the check to the address it saw,
 * so a pass earned on the phone's own address is rejected by the node and vice versa. Solving
 * through the tunnel puts both the check and the node's later fetch on one address.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaWebViewDialog(
    docUrl: String,
    proxyHostPort: String?,
    onDismiss: () -> Unit,
    onSolved: (cookies: String, resolvedUrl: String) -> Unit,
) {
    val context = LocalContext.current
    var challengeSolved by remember(docUrl) { mutableStateOf(false) }
    var probeText by remember(docUrl) { mutableStateOf("starting") }

    val webView = remember(docUrl) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    Log.i(TAG, "progress=$newProgress url=${view.url}")
                }

                override fun onReceivedTitle(view: WebView, title: String?) {
                    Log.i(TAG, "title=$title")
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    Log.i(TAG, "pageStarted url=$url")
                }

                override fun onPageFinished(view: WebView, url: String) {
                    Log.i(TAG, "pageFinished url=$url")
                }

                @Suppress("DEPRECATION")
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    Log.e(TAG, "error ${error.errorCode} ${error.description} for ${request.url} main=${request.isForMainFrame}")
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                    Log.e(TAG, "http ${errorResponse.statusCode} for ${request.url}")
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    Log.e(TAG, "renderProcessGone didCrash=${detail.didCrash()}")
                    return true
                }
            }
        }
    }

    fun collectCookies(): String {
        val cm = CookieManager.getInstance()
        val urls = listOfNotNull(
            docUrl.takeIf { it.isNotBlank() },
            webView.url,
            "https://yandex.com/",
            "https://yandex.ru/",
            "https://passport.yandex.ru/",
            "https://auth.yandex.ru/",
            "https://disk.yandex.ru/",
            "https://docs.yandex.ru/",
        )
        val merged = LinkedHashMap<String, String>()
        urls.forEach { u -> cm.getCookie(u)?.split(";")?.forEach { part ->
            val pair = part.trim()
            if (pair.isEmpty() || !pair.contains('=')) return@forEach
            val name = pair.substringBefore('=')
            merged[name] = pair.substringAfter('=')
        } }
        Log.i(TAG, "collected cookie names=${merged.keys}")
        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    suspend fun evalJs(js: String): String = suspendCancellableCoroutine { cont ->
        runCatching { webView.evaluateJavascript(js) { result ->
            if (cont.isActive) cont.resume(result ?: "null")
        } }.onFailure { if (cont.isActive) cont.resume("evalFailed") }
    }

    LaunchedEffect(docUrl) {
        val proxy = proxyHostPort
        if (proxy != null) {
            val cut = proxy.lastIndexOf(':')
            val host = if (cut > 0) proxy.substring(0, cut) else proxy
            val port = if (cut > 0) proxy.substring(cut + 1).toIntOrNull() ?: 0 else 0
            runCatching {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                    error("WebView does not support PROXY_OVERRIDE")
                }
                val executor = ContextCompat.getMainExecutor(context)
                ProxyController.getInstance().setProxyOverride(
                    ProxyConfig.Builder()
                        .addProxyRule("socks5://$host:$port", "*")
                        .build(),
                    executor,
                    Runnable {
                        probeText = "proxy=$host:$port set"
                        Log.i(TAG, "proxy override set to socks5://$host:$port")
                    },
                )
            }.onFailure {
                Log.e(TAG, "proxy override failed: ${it.message}")
                probeText = "proxyError=${it.message}"
            }
        }
        webView.loadUrl("https://api.ipify.org/")
        delay(2500)
        val egress = runCatching {
            evalJs("(function () { return document.body ? document.body.innerText : ''; })()")
        }.getOrDefault("")
        if (egress.isNotBlank() && egress.length < 60) {
            probeText = "egress=$egress"
            Log.i(TAG, "egress ip = $egress")
        }
        webView.loadUrl(docUrl)
        var pollCount = 0
        while (isActive) {
            delay(POLL_INTERVAL_MS)
            pollCount++
            val probe = runCatching { evalJs(PROBE_JS) }.getOrDefault("probeFailed")
            val shown = probe
            if (shown != probeText) {
                probeText = shown
                Log.i(TAG, "probe $shown")
            }
            runCatching { evalJs(ACCEPT_CONSENT_JS) }
            val solved = runCatching { evalJs(READY_JS) == "true" }.getOrDefault(false)
            if (!solved) continue

            val cookies = collectCookies()
            if (cookies.isNotBlank()) {
                challengeSolved = true
                onSolved(cookies, webView.url.orEmpty())
                return@LaunchedEffect
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                runCatching {
                    ProxyController.getInstance().clearProxyOverride(
                        ContextCompat.getMainExecutor(context),
                        Runnable { },
                    )
                }.onFailure { Log.w(TAG, "clearProxyOverride failed: ${it.message}") }
            }
            webView.destroy()
        }
    }

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

                if (proxyHostPort != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(0.dp),
                        )
                        Text(
                            text = stringResource(R.string.captcha_dialog_via_tunnel, proxyHostPort),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.captcha_dialog_wbv_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }

                Text(
                    text = probeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { webView },
                )

                TextButton(
                    onClick = {
                        if (!challengeSolved) return@TextButton
                        val cookies = collectCookies()
                        if (cookies.isNotBlank()) onSolved(cookies, webView.url.orEmpty())
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