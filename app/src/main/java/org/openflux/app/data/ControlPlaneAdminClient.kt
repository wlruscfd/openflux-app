package org.openflux.app.data

import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AdminNode(
    val id: String,
    val name: String,
    val maxKeys: Int,
    val status: String,
    val lastHeartbeatAt: String?,
    val createdAt: String,
)

data class AdminKey(
    val id: String,
    val label: String,
    val transport: String,
    val docUrl: String,
    val enabled: Boolean,
    val trafficLimitBytes: Long?,
    val bytesSentTotal: Long,
    val bytesReceivedTotal: Long,
    val ownerRef: String,
    val createdAt: String,
)

data class AdminIngestToken(
    val id: String,
    val label: String,
    val scope: String,
    val enabled: Boolean,
    val createdAt: String,
)

data class UsageDay(
    val day: String,
    val bytesSent: Long,
    val bytesReceived: Long,
    val activeKeys: Int,
) {
    val totalBytes: Long get() = bytesSent + bytesReceived
}

data class StatsSummary(
    val totalBytesSent: Long,
    val todayBytesSent: Long,
    val totalKeys: Int,
    val enabledKeys: Int,
    val overQuotaKeys: Int,
    val expiredKeys: Int,
    val onlineNodes: Int,
)

/** id + the raw token, shown once by the server - never retrievable again. */
data class CreatedWithToken(val id: String, val token: String)

// Like CreatedWithToken but also carries a deep link, when CONTROLPLANE_PUBLIC_URL is configured.
data class KeyToken(val token: String, val deepLink: String?)

// Talks to controlplane's admin JSON API; every method is a blocking network call, invoke off the main thread.
class ControlPlaneAdminClient(baseUrl: String, private val adminToken: String) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .also { ControlTrust.apply(it, baseUrl) }
        .build()

    private val baseUrl = baseUrl.trimEnd('/')

    fun listNodes(): List<AdminNode> = requestArray("GET", "/v1/admin/nodes").map { obj ->
        AdminNode(
            id = obj.getString("ID"),
            name = obj.optString("Name"),
            maxKeys = obj.optInt("MaxKeys"),
            status = obj.optString("Status"),
            lastHeartbeatAt = obj.optString("LastHeartbeatAt").takeIf { it.isNotBlank() && it != "null" },
            createdAt = obj.optString("CreatedAt"),
        )
    }

    fun createNode(name: String, maxKeys: Int): CreatedWithToken {
        val body = JSONObject().put("name", name).put("max_keys", maxKeys)
        val resp = requestObject("POST", "/v1/admin/nodes", body)!!
        return CreatedWithToken(resp.getString("id"), resp.getString("token"))
    }

    fun rotateNodeToken(id: String): String =
        requestObject("POST", "/v1/admin/nodes/$id/rotate-token")!!.getString("token")

    fun listKeys(ownerRef: String? = null): List<AdminKey> {
        val path = if (ownerRef.isNullOrBlank()) "/v1/admin/keys"
        else "/v1/admin/keys?owner_ref=" + URLEncoder.encode(ownerRef, "UTF-8")
        return requestArray("GET", path).map { obj ->
            AdminKey(
                id = obj.getString("ID"),
                label = obj.optString("Label"),
                transport = obj.optString("Transport"),
                docUrl = obj.optString("DocURL"),
                enabled = obj.optBoolean("Enabled"),
                trafficLimitBytes = if (obj.isNull("TrafficLimitBytes")) null else obj.optLong("TrafficLimitBytes"),
                bytesSentTotal = obj.optLong("BytesSentTotal"),
                bytesReceivedTotal = obj.optLong("BytesReceivedTotal"),
                ownerRef = obj.optString("OwnerRef"),
                createdAt = obj.optString("CreatedAt"),
            )
        }
    }

    fun createKey(label: String, docUrl: String, trafficLimitBytes: Long?, ownerRef: String, transport: String): KeyToken {
        val body = JSONObject()
            .put("label", label)
            .put("doc_url", docUrl)
            .put("transport", transport)
            .put("owner_ref", ownerRef)
        if (trafficLimitBytes != null) body.put("traffic_limit_bytes", trafficLimitBytes)
        val resp = requestObject("POST", "/v1/admin/keys", body)!!
        return KeyToken(resp.getString("token"), resp.optString("deep_link").ifBlank { null })
    }

    // Only way to get a usable token again, since the original is stored hashed and never returned after creation.
    fun rotateKeyToken(id: String): KeyToken {
        val resp = requestObject("POST", "/v1/admin/keys/$id/rotate-token")!!
        return KeyToken(resp.getString("token"), resp.optString("deep_link").ifBlank { null })
    }

    fun setKeyEnabled(id: String, enabled: Boolean) {
        requestObject("POST", "/v1/admin/keys/$id/${if (enabled) "enable" else "disable"}")
    }

    fun deleteKey(id: String) {
        requestObject("DELETE", "/v1/admin/keys/$id")
    }

    fun patchKeyLimit(id: String, limitBytes: Long?) {
        val body = JSONObject()
        if (limitBytes == null) body.put("traffic_limit_bytes", JSONObject.NULL) else body.put("traffic_limit_bytes", limitBytes)
        requestObject("PATCH", "/v1/admin/keys/$id", body)
    }

    fun patchKey(
        id: String,
        label: String,
        transport: String,
        docUrl: String,
        trafficLimitBytes: Long?,
        enabled: Boolean,
    ) {
        val body = JSONObject()
            .put("label", label)
            .put("transport", transport)
            .put("doc_url", docUrl)
            .put("enabled", enabled)
        if (trafficLimitBytes == null) body.put("traffic_limit_bytes", JSONObject.NULL)
        else body.put("traffic_limit_bytes", trafficLimitBytes)
        requestObject("PATCH", "/v1/admin/keys/$id", body)
    }

    fun keyUsage(id: String, days: Int): List<UsageDay> =
        requestArray("GET", "/v1/admin/keys/$id/usage?days=$days").map { obj ->
            UsageDay(
                day = obj.optString("day"),
                bytesSent = obj.optLong("bytes_sent"),
                bytesReceived = obj.optLong("bytes_received"),
                activeKeys = obj.optInt("active_keys"),
            )
        }

    fun usageSummary(days: Int): List<UsageDay> =
        requestArray("GET", "/v1/admin/stats/usage?days=$days").map { obj ->
            UsageDay(
                day = obj.optString("day"),
                bytesSent = obj.optLong("bytes_sent"),
                bytesReceived = obj.optLong("bytes_received"),
                activeKeys = obj.optInt("active_keys"),
            )
        }

    fun statsSummary(): StatsSummary {
        val obj = requestObject("GET", "/v1/admin/stats/summary") ?: JSONObject()
        return StatsSummary(
            totalBytesSent = obj.optLong("total_bytes_sent"),
            todayBytesSent = obj.optLong("today_bytes_sent"),
            totalKeys = obj.optInt("total_keys"),
            enabledKeys = obj.optInt("enabled_keys"),
            overQuotaKeys = obj.optInt("over_quota_keys"),
            expiredKeys = obj.optInt("expired_keys"),
            onlineNodes = obj.optInt("online_nodes"),
        )
    }

    fun listIngestTokens(): List<AdminIngestToken> = requestArray("GET", "/v1/admin/ingest-tokens").map { obj ->
        AdminIngestToken(
            id = obj.getString("ID"),
            label = obj.optString("Label"),
            scope = obj.optString("Scope"),
            enabled = obj.optBoolean("Enabled"),
            createdAt = obj.optString("CreatedAt"),
        )
    }

    fun createIngestToken(label: String): CreatedWithToken {
        val body = JSONObject().put("label", label).put("scope", "keys:write")
        val resp = requestObject("POST", "/v1/admin/ingest-tokens", body)!!
        return CreatedWithToken(resp.getString("id"), resp.getString("token"))
    }

    fun setIngestTokenEnabled(id: String, enabled: Boolean) {
        requestObject("POST", "/v1/admin/ingest-tokens/$id/${if (enabled) "enable" else "disable"}")
    }

    private fun requestArray(method: String, path: String): List<JSONObject> {
        val text = requestRaw(method, path, null)
        if (text.isNullOrBlank()) return emptyList()
        val arr = JSONArray(text)
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun requestObject(method: String, path: String, body: JSONObject? = null): JSONObject? {
        val text = requestRaw(method, path, body) ?: return null
        return if (text.isBlank()) null else JSONObject(text)
    }

    private fun requestRaw(method: String, path: String, body: JSONObject?): String? {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = body?.toString()?.toRequestBody(jsonMediaType)

        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $adminToken")

        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            "POST" -> builder.post(requestBody ?: "".toRequestBody(jsonMediaType))
            "PATCH" -> builder.patch(requestBody ?: "".toRequestBody(jsonMediaType))
            else -> throw IllegalArgumentException("unsupported method $method")
        }

        httpClient.newCall(builder.build()).execute().use { response ->
            if (response.code == 204) return null
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(text).optString("error") }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "HTTP ${response.code}"
                throw IOException(message)
            }
            return text
        }
    }
}
