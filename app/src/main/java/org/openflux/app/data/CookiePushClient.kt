package org.openflux.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

sealed class CookiePushResult {
    data object Sent : CookiePushResult()
    data class Failed(val reason: PushFailure) : CookiePushResult()
}

enum class PushFailure { NO_CONTROL_URL, NO_KEY_TOKEN, BAD_CONTROL_URL, REJECTED, NETWORK, NO_TUNNEL, RATE_LIMITED, UNKNOWN_KEY }

enum class PushRoute {
    DIRECT,
    VIA_TUNNEL,
}

/**
 * Uploads a cookie jar to the controlplane, which hands it to the node running this key.
 *
 * The direct route is the fast one, but the controlplane is exactly the kind of host a blocked
 * network refuses to reach, so [PushRoute.VIA_TUNNEL] sends the same request through another
 * profile's local SOCKS5 proxy instead. The tunnel only hides where the request comes from - the
 * controlplane still authenticates this key.
 */
class CookiePushClient(
    baseUrl: String,
    private val keyToken: String,
    private val route: PushRoute = PushRoute.DIRECT,
    socksPort: Int = 0,
) {
    private val baseUrl = baseUrl.trimEnd('/')
    private val socksPort = socksPort

    suspend fun push(cookies: String): CookiePushResult = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext CookiePushResult.Failed(PushFailure.NO_CONTROL_URL)
        if (keyToken.isBlank()) return@withContext CookiePushResult.Failed(PushFailure.NO_KEY_TOKEN)
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            return@withContext CookiePushResult.Failed(PushFailure.BAD_CONTROL_URL)
        }
        if (cookies.isBlank()) return@withContext CookiePushResult.Failed(PushFailure.REJECTED)
        if (route == PushRoute.VIA_TUNNEL && socksPort <= 0) {
            return@withContext CookiePushResult.Failed(PushFailure.NO_TUNNEL)
        }

        val body = JSONObject().put("cookies", cookies).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/v1/keys/cookies")
            .header("Authorization", "Bearer $keyToken")
            .post(body)
            .build()

        runCatching { client().newCall(request).execute() }.fold(
            onSuccess = { response ->
                response.use {
                    when {
                        response.isSuccessful -> CookiePushResult.Sent
                        response.code == 429 -> CookiePushResult.Failed(PushFailure.RATE_LIMITED)
                        response.code == 401 || response.code == 404 -> CookiePushResult.Failed(PushFailure.UNKNOWN_KEY)
                        else -> CookiePushResult.Failed(PushFailure.REJECTED)
                    }
                }
            },
            onFailure = { CookiePushResult.Failed(PushFailure.NETWORK) },
        )
    }

    private fun client(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
        if (route == PushRoute.VIA_TUNNEL) {
            builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
        }
        applyPermissiveTls(builder)
        return builder.build()
    }

    companion object {
        fun applyPermissiveTls(builder: OkHttpClient.Builder) {
            val trustManager = object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            }
            runCatching {
                javax.net.ssl.SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(trustManager), java.security.SecureRandom())
                }
            }.onSuccess { builder.sslSocketFactory(it.socketFactory, trustManager) }
            builder.hostnameVerifier { _, _ -> true }
        }
    }
}
