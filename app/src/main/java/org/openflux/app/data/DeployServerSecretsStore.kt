package org.openflux.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// Sensitive half of a deploy server, in its own encrypted file, same Keystore-backed approach as SecretsStore.
class DeployServerSecretsStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "openflux_deploy_secrets",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(id: String, secrets: DeployServerSecrets) {
        prefs.edit()
            .putString(key(id, "ssh_password"), secrets.sshPassword)
            .putString(key(id, "ssh_private_key"), secrets.sshPrivateKeyPem)
            .putString(key(id, "ssh_passphrase"), secrets.sshPassphrase)
            .putString(key(id, "admin_token"), secrets.adminToken)
            .putString(key(id, "db_password"), secrets.dbPassword)
            .putString(key(id, "node_token"), secrets.nodeToken)
            .apply()
    }

    /** Updates just the node token, e.g. once a deploy reports one - see DeployManager. */
    fun saveNodeToken(id: String, nodeToken: String) {
        prefs.edit().putString(key(id, "node_token"), nodeToken).apply()
    }

    fun load(id: String): DeployServerSecrets = DeployServerSecrets(
        sshPassword = prefs.getString(key(id, "ssh_password"), "") ?: "",
        sshPrivateKeyPem = prefs.getString(key(id, "ssh_private_key"), "") ?: "",
        sshPassphrase = prefs.getString(key(id, "ssh_passphrase"), "") ?: "",
        adminToken = prefs.getString(key(id, "admin_token"), "") ?: "",
        dbPassword = prefs.getString(key(id, "db_password"), "") ?: "",
        nodeToken = prefs.getString(key(id, "node_token"), "") ?: "",
    )

    fun delete(id: String) {
        prefs.edit()
            .remove(key(id, "ssh_password"))
            .remove(key(id, "ssh_private_key"))
            .remove(key(id, "ssh_passphrase"))
            .remove(key(id, "admin_token"))
            .remove(key(id, "db_password"))
            .remove(key(id, "node_token"))
            .apply()
    }

    private fun key(id: String, field: String) = "$id.$field"
}

data class DeployServerSecrets(
    val sshPassword: String = "",
    val sshPrivateKeyPem: String = "",
    val sshPassphrase: String = "",
    val adminToken: String = "",
    val dbPassword: String = "",
    val nodeToken: String = "",
)
