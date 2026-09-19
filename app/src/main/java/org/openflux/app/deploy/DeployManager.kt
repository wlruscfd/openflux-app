package org.openflux.app.deploy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mobile.DeployCallback
import mobile.Mobile
import org.openflux.app.data.DeployServer
import org.openflux.app.data.DeployServerRepository
import org.openflux.app.data.DeployStatus
import org.openflux.app.data.toDeployOptionsJson
import org.openflux.app.data.toSshTargetJson

private const val MAX_LOG_LINES_PER_SERVER = 1000

// A singleton, not ViewModel-scoped, so switching tabs (which clears back stacks) can't cancel an in-flight deploy.
object DeployManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val runningJobs = mutableMapOf<String, Job>()

    private val _status = MutableStateFlow<Map<String, DeployStatus>>(emptyMap())
    val status: StateFlow<Map<String, DeployStatus>> = _status

    private val _logs = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val logs: StateFlow<Map<String, List<String>>> = _logs

    // Surfaces install.sh's latest "==> <message>" step instead of a static "Deploying..." for the whole run.
    private val _currentStep = MutableStateFlow<Map<String, String>>(emptyMap())
    val currentStep: StateFlow<Map<String, String>> = _currentStep

    private val _batchRunning = MutableStateFlow(false)
    val batchRunning: StateFlow<Boolean> = _batchRunning

    fun isRunning(serverId: String): Boolean = _status.value[serverId] == DeployStatus.RUNNING

    /** Deploys to a single server. No-op if a deploy for it is already running. */
    fun deploy(repository: DeployServerRepository, server: DeployServer) {
        if (isRunning(server.id)) return
        scope.launch { runOne(repository, server) }
    }

    /** Deploys to every server in order, one at a time, continuing past a failure. */
    fun deployAll(repository: DeployServerRepository, servers: List<DeployServer>) {
        if (_batchRunning.value) return
        scope.launch {
            _batchRunning.value = true
            try {
                for (server in servers) {
                    if (isRunning(server.id)) continue
                    runOne(repository, server)
                }
            } finally {
                _batchRunning.value = false
            }
        }
    }

    private suspend fun runOne(repository: DeployServerRepository, server: DeployServer) {
        val id = server.id
        _logs.update { it + (id to emptyList()) }
        _status.update { it + (id to DeployStatus.RUNNING) }
        _currentStep.update { it - id }

        var fingerprint = server.knownHostKeyFingerprint
        val callback = object : DeployCallback {
            override fun onLog(line: String) {
                _logs.update { current ->
                    val updated = (current[id].orEmpty() + line).takeLast(MAX_LOG_LINES_PER_SERVER)
                    current + (id to updated)
                }
                parseStep(line)?.let { step -> _currentStep.update { it + (id to step) } }
            }

            override fun onHostKeyFingerprint(fp: String) {
                fingerprint = fp
            }

            override fun onDeployResult(panelURL: String, adminToken: String, nodeToken: String) {
                if (nodeToken.isNotBlank()) {
                    scope.launch { repository.recordNodeToken(id, nodeToken) }
                }
            }
        }

        val succeeded = try {
            Mobile.deploy(server.toSshTargetJson(), server.toDeployOptionsJson(), callback)
            true
        } catch (t: Throwable) {
            callback.onLog("error: ${t.message}")
            false
        }

        val finalStatus = if (succeeded) DeployStatus.SUCCESS else DeployStatus.FAILED
        _status.update { it + (id to finalStatus) }
        repository.recordDeployResult(id, finalStatus, fingerprint)
    }

    // ANSI codes survive the SSH stdout stream verbatim, so they must be stripped before matching install.sh's "==>".
    private val ansiEscape = Regex("\\[[0-9;]*m")
    private val stepPrefix = Regex("^==>\\s*(.+)$")

    private fun parseStep(line: String): String? {
        val clean = ansiEscape.replace(line, "").trim()
        return stepPrefix.matchEntire(clean)?.groupValues?.get(1)
    }
}
