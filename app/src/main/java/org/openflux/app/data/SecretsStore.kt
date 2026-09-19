package org.openflux.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// Holds the sensitive half of a profile, keyed by profile id, in an Android Keystore-backed encrypted file.
class SecretsStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "openflux_secrets",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(id: String, secrets: ProfileSecrets) {
        prefs.edit()
            .putString(key(id, "control_url"), secrets.controlUrl)
            .putString(key(id, "key_token"), secrets.keyToken)
            .putString(key(id, "doc_url"), secrets.docUrl)
            .putString(key(id, "doc_urls"), secrets.docUrls.joinToString("\n"))
            .putString(key(id, "max_token"), secrets.maxToken)
            .putLong(key(id, "max_uid"), secrets.maxUid)
            .apply()
    }

    fun load(id: String): ProfileSecrets = ProfileSecrets(
        controlUrl = prefs.getString(key(id, "control_url"), "") ?: "",
        keyToken = prefs.getString(key(id, "key_token"), "") ?: "",
        docUrl = prefs.getString(key(id, "doc_url"), "") ?: "",
        docUrls = (prefs.getString(key(id, "doc_urls"), "") ?: "")
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() },
        maxToken = prefs.getString(key(id, "max_token"), "") ?: "",
        maxUid = prefs.getLong(key(id, "max_uid"), 0),
    )

    fun delete(id: String) {
        prefs.edit()
            .remove(key(id, "control_url"))
            .remove(key(id, "key_token"))
            .remove(key(id, "doc_url"))
            .remove(key(id, "doc_urls"))
            .remove(key(id, "max_token"))
            .remove(key(id, "max_uid"))
            .apply()
    }

    private fun key(id: String, field: String) = "$id.$field"
}

data class ProfileSecrets(
    val controlUrl: String = "",
    val keyToken: String = "",
    val docUrl: String = "",
    val docUrls: List<String> = emptyList(),
    val maxToken: String = "",
    val maxUid: Long = 0,
)
