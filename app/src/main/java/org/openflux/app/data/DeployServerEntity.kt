package org.openflux.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Non-secret fields; SSH credentials and generated admin/DB secrets live in [DeployServerSecretsStore] instead.
@Entity(tableName = "deploy_servers")
data class DeployServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authMethod: String, // "password" or "key"
    val tlsMode: String, // "domain" or "ip"
    val domain: String,
    val email: String,
    val repoUrl: String,
    val gitRef: String,
    val deployScriptUrl: String,
    val registerNode: Boolean,
    val nodeName: String,
    val nodeMaxKeys: Int,
    val runNodeHere: Boolean,
    val knownHostKeyFingerprint: String,
    val lastDeployStatus: String, // "" | "running" | "success" | "failed"
    val lastDeployAt: Long, // epoch millis, 0 = never
    val createdAt: Long,
)
