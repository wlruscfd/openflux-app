package org.openflux.app.data

import android.net.Uri
import android.util.Base64
import java.nio.charset.StandardCharsets
import org.json.JSONObject

// Shares a profile as openflux://import?data=<base64url-json>; the payload excludes the id, so import is a fresh draft.
object ProfileDeepLink {
    const val SCHEME = "openflux"
    const val HOST = "import"
    private const val DATA_PARAM = "data"

    fun buildUri(profile: Profile): Uri =
        Uri.parse("$SCHEME://$HOST").buildUpon()
            .appendQueryParameter(DATA_PARAM, encode(profile))
            .build()

    fun parse(uri: Uri): Profile? {
        if (!uri.scheme.equals(SCHEME, ignoreCase = true) || uri.host != HOST) return null
        val data = uri.getQueryParameter(DATA_PARAM) ?: return null
        return decode(data)
    }

    private fun encode(profile: Profile): String {
        val json = JSONObject().apply {
            put("name", profile.name)
            put("mode", if (profile.mode == ProfileMode.MANUAL) "manual" else "key")
            put("control_url", profile.controlUrl)
            put("key_token", profile.keyToken)
            put("transport", transportName(profile.manualTransport))
            put("doc_url", profile.docUrl)
            // Omitted (rather than an empty array) for every transport but YANDEX_MULTISTREAM.
            if (profile.docUrls.isNotEmpty()) {
                put("doc_urls", org.json.JSONArray(profile.docUrls))
            }
            put("max_token", profile.maxToken)
            put("max_uid", profile.maxUid)
            put("mtu", profile.mtu)
            put("dns_upstream", profile.dnsUpstream)
            put("auto_reconnect", profile.autoReconnect)
            if (profile.e2eEncryption) {
                put("e2e_encryption", true)
            }
        }
        val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        return Base64.encodeToString(bytes, BASE64_FLAGS)
    }

    private fun decode(data: String): Profile? = runCatching {
        val bytes = Base64.decode(data, BASE64_FLAGS)
        val json = JSONObject(String(bytes, StandardCharsets.UTF_8))
        val docUrlsArray = json.optJSONArray("doc_urls")
        Profile(
            id = "",
            name = json.optString("name", ""),
            mode = if (json.optString("mode") == "manual") ProfileMode.MANUAL else ProfileMode.KEY,
            controlUrl = json.optString("control_url", ""),
            keyToken = json.optString("key_token", ""),
            manualTransport = parseTransportName(json.optString("transport")),
            docUrl = json.optString("doc_url", ""),
            docUrls = docUrlsArray?.let { arr -> List(arr.length()) { arr.optString(it, "") } } ?: emptyList(),
            maxToken = json.optString("max_token", ""),
            maxUid = json.optLong("max_uid", 0),
            mtu = json.optInt("mtu", 1400),
            dnsUpstream = json.optString("dns_upstream", "77.88.8.8"),
            autoReconnect = json.optBoolean("auto_reconnect", true),
            e2eEncryption = json.optBoolean("e2e_encryption", false),
        )
    }.getOrNull()

    private val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
}
