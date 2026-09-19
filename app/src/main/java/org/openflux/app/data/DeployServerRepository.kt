package org.openflux.app.data

import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class DeployServerRepository(
    private val dao: DeployServerDao,
    private val secrets: DeployServerSecretsStore,
) {
    fun observeAll(): Flow<List<DeployServer>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain(secrets.load(it.id)) } }

    suspend fun getById(id: String): DeployServer? =
        dao.getById(id)?.let { it.toDomain(secrets.load(it.id)) }

    // Admin token and DB password are generated once, on first save, and kept stable across later edits.
    suspend fun save(server: DeployServer): DeployServer {
        val id = server.id.ifBlank { UUID.randomUUID().toString() }
        val existing = if (server.id.isBlank()) null else dao.getById(id)
        val existingSecrets = existing?.let { secrets.load(id) }

        val adminToken = server.adminToken.ifBlank { existingSecrets?.adminToken?.ifBlank { null } ?: randomHex(32) }
        val dbPassword = server.dbPassword.ifBlank { existingSecrets?.dbPassword?.ifBlank { null } ?: randomHex(24) }

        dao.upsert(
            DeployServerEntity(
                id = id,
                name = server.name,
                host = server.host,
                port = server.port,
                username = server.username,
                authMethod = server.authMethod.name.lowercase(),
                tlsMode = server.tlsMode.name.lowercase(),
                domain = server.domain,
                email = server.email,
                repoUrl = server.repoUrl,
                gitRef = server.gitRef,
                deployScriptUrl = server.deployScriptUrl,
                registerNode = server.registerNode,
                nodeName = server.nodeName,
                nodeMaxKeys = server.nodeMaxKeys,
                runNodeHere = server.runNodeHere,
                knownHostKeyFingerprint = server.knownHostKeyFingerprint,
                lastDeployStatus = existing?.lastDeployStatus ?: "",
                lastDeployAt = existing?.lastDeployAt ?: 0,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
        secrets.save(
            id,
            DeployServerSecrets(
                sshPassword = server.sshPassword,
                sshPrivateKeyPem = server.sshPrivateKeyPem,
                sshPassphrase = server.sshPassphrase,
                adminToken = adminToken,
                dbPassword = dbPassword,
                nodeToken = existingSecrets?.nodeToken.orEmpty(),
            ),
        )
        return server.copy(id = id, adminToken = adminToken, dbPassword = dbPassword)
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
        secrets.delete(id)
    }

    suspend fun recordDeployResult(id: String, status: DeployStatus, hostKeyFingerprint: String) {
        dao.recordDeployResult(id, status.name.lowercase(), System.currentTimeMillis(), hostKeyFingerprint)
    }

    /** Called once a deploy's install.sh reports a freshly-registered node's token. */
    suspend fun recordNodeToken(id: String, nodeToken: String) {
        secrets.saveNodeToken(id, nodeToken)
    }

    private fun DeployServerEntity.toDomain(s: DeployServerSecrets): DeployServer = DeployServer(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        authMethod = runCatching { SshAuthMethod.valueOf(authMethod.uppercase()) }.getOrDefault(SshAuthMethod.PASSWORD),
        sshPassword = s.sshPassword,
        sshPrivateKeyPem = s.sshPrivateKeyPem,
        sshPassphrase = s.sshPassphrase,
        tlsMode = runCatching { TlsMode.valueOf(tlsMode.uppercase()) }.getOrDefault(TlsMode.DOMAIN),
        domain = domain,
        email = email,
        repoUrl = repoUrl,
        gitRef = gitRef,
        deployScriptUrl = deployScriptUrl,
        registerNode = registerNode,
        nodeName = nodeName,
        nodeMaxKeys = nodeMaxKeys,
        runNodeHere = runNodeHere,
        adminToken = s.adminToken,
        dbPassword = s.dbPassword,
        nodeToken = s.nodeToken,
        knownHostKeyFingerprint = knownHostKeyFingerprint,
        lastDeployStatus = runCatching { DeployStatus.valueOf(lastDeployStatus.uppercase()) }.getOrDefault(DeployStatus.NONE),
        lastDeployAt = lastDeployAt,
    )
}

private fun randomHex(numBytes: Int): String {
    val bytes = ByteArray(numBytes)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

// Builds the JSON the `mobile` Go package's Deploy expects for its target argument (see mobile/deploy.go).
fun DeployServer.toSshTargetJson(): String = JSONObject().apply {
    put("host", host)
    put("port", port)
    put("username", username)
    put("auth_method", if (authMethod == SshAuthMethod.KEY) "key" else "password")
    put("password", sshPassword)
    put("private_key_pem", sshPrivateKeyPem)
    put("passphrase", sshPassphrase)
    put("known_host_key_fingerprint", knownHostKeyFingerprint)
}.toString()

// Builds the JSON the `mobile` Go package's Deploy expects for its opts argument (see mobile/deploy.go).
fun DeployServer.toDeployOptionsJson(): String = JSONObject().apply {
    put("deploy_script_url", deployScriptUrl)
    put("repo_url", repoUrl)
    put("git_ref", gitRef)
    put("tls_mode", when (tlsMode) {
        TlsMode.IP -> "ip"
        TlsMode.DOMAIN -> "domain"
        TlsMode.HTTP -> "http"
    })
    put("domain", domain)
    put("email", email)
    put("server_ip", host) // the box we're SSHing into is the same one getting the cert in ip/http mode
    put("admin_token", adminToken)
    put("db_password", dbPassword)
    put("register_node", registerNode)
    put("node_name", nodeName)
    put("node_max_keys", nodeMaxKeys)
    put("run_node_here", runNodeHere)
}.toString()
