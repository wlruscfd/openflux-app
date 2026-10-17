package org.openflux.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import mobile.Mobile

data class TransportInfo(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val wire: Int,
    val custom: Boolean,
    val update: List<String>,
    val pubkey: String,
    val disabled: Boolean,
    val hasPrevious: Boolean,
    val badVersion: String,
    val wired: Boolean,
)

data class TransportReport(
    val id: String,
    val name: String,
    val custom: Boolean,
    val from: String,
    val to: String,
    val status: String,
    val code: String,
    val notes: String,
    val wireBreak: Boolean,
)

class TransportException(val code: String) : Exception(code)

// Signed transport packages from the transports repository or a source the user added; the core verifies them and falls back to the built-in transport.
class TransportUpdater(private val context: Context, private val settings: SettingsRepository) {
    private val dir: String get() = File(context.filesDir, "transports").absolutePath

    suspend fun scriptsDir(): String? = if (settings.dynamicTransports.first()) dir else null

    private suspend fun catalogUrl() = settings.transportsCatalogUrl.first()

    private suspend fun channel() = settings.transportsChannel.first()

    suspend fun list(): List<TransportInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray(Mobile.transportsList(dir))
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                TransportInfo(
                    id = o.optString("id"),
                    name = o.optString("name").ifBlank { o.optString("id") },
                    description = o.optString("description"),
                    author = o.optString("author"),
                    version = o.optString("version"),
                    wire = o.optInt("wire", 1),
                    custom = o.optBoolean("custom"),
                    update = o.optJSONArray("update")?.let { u -> List(u.length()) { u.optString(it) } } ?: emptyList(),
                    pubkey = o.optString("pubkey"),
                    disabled = o.optBoolean("disabled"),
                    hasPrevious = o.optBoolean("hasPrevious"),
                    badVersion = o.optString("badVersion"),
                    wired = o.optBoolean("wired"),
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun check(): Result<List<TransportReport>> = withContext(Dispatchers.IO) {
        runCatching { reports(Mobile.transportsCheck(dir, catalogUrl(), channel())) }
    }

    suspend fun updateAll(): Result<List<TransportReport>> = withContext(Dispatchers.IO) {
        runCatching { reports(Mobile.transportsUpdate(dir, catalogUrl(), channel())) }
    }

    suspend fun updateOne(id: String, allowWireBreak: Boolean): Result<TransportReport> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = Mobile.transportsUpdateOne(dir, id, catalogUrl(), channel(), allowWireBreak)
            report(JSONObject(raw)).also { if (it.status == "error") throw TransportException(it.code) }
        }
    }

    suspend fun addCustom(source: String, pubkey: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val o = JSONObject(Mobile.transportsAddCustom(dir, source.trim(), pubkey.trim(), channel()))
            if (o.optString("status") != "installed") throw TransportException(o.optString("code", "error"))
            o.optString("id")
        }
    }

    suspend fun remove(id: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { JSONObject(Mobile.transportsRemove(dir, id)).optString("status") == "removed" }.getOrDefault(false)
    }

    suspend fun rollback(id: String) = withContext(Dispatchers.IO) { runCatching { Mobile.transportsRollback(dir, id) } }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        runCatching { Mobile.transportsSetEnabled(dir, id, enabled) }
    }

    private fun reports(raw: String): List<TransportReport> {
        if (raw.trimStart().startsWith("{")) throw TransportException(JSONObject(raw).optString("error", "update_failed"))
        val arr = JSONArray(raw)
        return List(arr.length()) { report(arr.getJSONObject(it)) }
    }

    private fun report(o: JSONObject) = TransportReport(
        id = o.optString("id"),
        name = o.optString("name").ifBlank { o.optString("id") },
        custom = o.optBoolean("custom"),
        from = o.optString("from"),
        to = o.optString("to"),
        status = o.optString("status"),
        code = o.optString("code"),
        notes = o.optString("notes"),
        wireBreak = o.optBoolean("wireBreak"),
    )
}
