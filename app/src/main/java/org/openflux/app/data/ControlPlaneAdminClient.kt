package org.openflux.app.data

import java.io.IOException
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

/** id + the raw token, shown once by the server - never retrievable again. */
data class CreatedWithToken(val id: String, val token: String)

// Like CreatedWithToken but also carries a deep link, when CONTROLPLANE_PUBLIC_URL is configured.
data class KeyToken(val token: String, val deepLink: String?)

// Talks to controlplane's admin JSON API; every method is a blocking network call, invoke off the main thread.
class ControlPlaneAdminClient(baseUrl: String, private val adminToken: String) {
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
        val path = if (ownerRef.isNullOrBlank()) "/v1/admin/keys" else "/v1/admin/keys?owner_ref=$ownerRef"
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

    fun createKey(label: String, docUrl: String, trafficLimitBytes: Long?, ownerRef: String): KeyToken {
        val body = JSONObject()
            .put("label", label)
            .put("doc_url", docUrl)
            .put("transport", "yandex")
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

    companion object {
        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
