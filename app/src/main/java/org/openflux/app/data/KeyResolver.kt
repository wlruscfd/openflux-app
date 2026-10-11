package org.openflux.app.data

import java.net.InetSocketAddress
import java.net.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class ResolvedKey(
    val docUrl: String,
    val docUrls: List<String>,
    val transportName: String,
    val e2eEncryption: Boolean,
)

/**
 * A managed cupsonline key starts with an empty document: the node creates its rooms and writes
 * the list back a moment later, so a deep link handed out at creation time can still be blank.
 * Resolving fills it in, which is also the only way an already-shared link ever becomes usable.
 */
class KeyResolver(private val socksPort: () -> Int = { 0 }) {

    suspend fun resolve(controlUrl: String, keyToken: String, viaTunnel: Boolean = false): ResolvedKey? =
        withContext(Dispatchers.IO) {
            val base = controlUrl.trim().trimEnd('/')
            if (base.isBlank() || keyToken.isBlank()) return@withContext null
            if (!base.startsWith("http://") && !base.startsWith("https://")) return@withContext null

            val builder = OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            val port = if (viaTunnel) socksPort() else 0
            if (port > 0) {
                builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
            }
            CookiePushClient.applyPermissiveTls(builder)

            val request = Request.Builder()
                .url("$base/v1/resolve")
                .header("Authorization", "Bearer $keyToken")
                .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            runCatching {
                builder.build().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val json = JSONObject(response.body?.string() ?: return@use null)
                    if (json.optString("status") == "not_found") {
                        null
                    } else {
                        ResolvedKey(
                            docUrl = json.optString("doc_url", ""),
                            docUrls = json.optJSONArray("doc_urls")?.let { arr ->
                                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                            } ?: emptyList(),
                            transportName = json.optString("transport", ""),
                            e2eEncryption = json.optBoolean("e2e_encryption", false),
                        )
                    }
                }
            }.getOrNull()
        }
}