package org.openflux.app.data

import android.content.Context
import android.content.SharedPreferences
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * Trust for a control plane the app talks to with a key token. A certificate a public CA vouches
 * for is accepted as usual. A self-signed one (the IP-address install) is trusted on first use and
 * pinned from then on: a different certificate for the same address is refused instead of silently
 * accepted, so a network that hijacks the connection cannot read the key token or feed a document.
 */
object ControlTrust {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("openflux_control_pins", Context.MODE_PRIVATE)
    }

    fun apply(builder: OkHttpClient.Builder, baseUrl: String) {
        val key = pinKey(baseUrl) ?: return
        val system = systemTrustManager()
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                system.checkClientTrusted(chain, authType)

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    system.checkServerTrusted(chain, authType)
                } catch (e: CertificateException) {
                    val seen = fingerprint(chain.firstOrNull() ?: throw e)
                    when (val pinned = prefs?.getString(key, null)) {
                        null -> {
                            if (prefs == null) throw e
                            prefs?.edit()?.putString(key, seen)?.apply()
                        }
                        seen -> Unit
                        else -> throw CertificateException("the certificate of $key changed (pinned $pinned)")
                    }
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), SecureRandom()) }
        builder.sslSocketFactory(context.socketFactory, trustManager)
        val standard = HttpsURLConnection.getDefaultHostnameVerifier()
        builder.hostnameVerifier { host, session ->
            standard.verify(host, session) || runCatching {
                val leaf = session.peerCertificates.first() as X509Certificate
                prefs?.getString(key, null) == fingerprint(leaf)
            }.getOrDefault(false)
        }
    }

    fun isPinned(baseUrl: String): Boolean {
        val key = pinKey(baseUrl) ?: return false
        return prefs?.contains(key) == true
    }

    fun forget(baseUrl: String) {
        val key = pinKey(baseUrl) ?: return
        prefs?.edit()?.remove(key)?.apply()
    }

    private fun pinKey(baseUrl: String): String? = runCatching {
        val uri = URI(baseUrl.trim())
        val host = uri.host?.lowercase() ?: return null
        val port = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
        "$host:$port"
    }.getOrNull()

    private fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }

    private fun systemTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}
