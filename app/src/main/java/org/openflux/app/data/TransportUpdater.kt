package org.openflux.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import mobile.Mobile

data class TransportUpdateResult(val id: String, val from: String, val to: String, val status: String)

data class InstalledTransport(val id: String, val version: String, val disabled: Boolean, val hasPrevious: Boolean)

// Signed transport packages from the openflux-transports repository; the core verifies them and falls back to the built-in transport.
class TransportUpdater(private val context: Context, private val settings: SettingsRepository) {
    private val dir: String get() = File(context.filesDir, "transports").absolutePath

    suspend fun scriptsDir(): String? = if (settings.dynamicTransports.first()) dir else null

    suspend fun checkNow(): Result<List<TransportUpdateResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = Mobile.transportsUpdate(dir, "", "stable")
            if (raw.trimStart().startsWith("{")) error(JSONObject(raw).optString("error", "update failed"))
            val arr = JSONArray(raw)
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                TransportUpdateResult(o.optString("id"), o.optString("from"), o.optString("to"), o.optString("status"))
            }
        }
    }

    suspend fun installed(): List<InstalledTransport> = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray(Mobile.transportsInstalled(dir))
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                InstalledTransport(o.optString("id"), o.optString("version"), o.optBoolean("disabled"), o.optBoolean("hasPrevious"))
            }
        }.getOrDefault(emptyList())
    }

    suspend fun rollback(id: String) = withContext(Dispatchers.IO) { runCatching { Mobile.transportsRollback(dir, id) } }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        runCatching { Mobile.transportsSetEnabled(dir, id, enabled) }
    }
}
