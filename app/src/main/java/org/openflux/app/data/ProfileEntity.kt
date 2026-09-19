package org.openflux.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Non-secret fields only; the connection secret lives in [SecretsStore], never in this plain SQLite row.
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mode: String, // ProfileMode.name
    val manualTransport: String, // ManualTransport.name; only meaningful when mode == MANUAL
    val mtu: Int,
    val dnsUpstream: String,
    val forceBootstrapDns: String = "",
    val autoReconnect: Boolean,
    val e2eEncryption: Boolean = false,
    val createdAt: Long,
)
