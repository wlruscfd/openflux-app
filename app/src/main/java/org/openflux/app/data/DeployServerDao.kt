package org.openflux.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeployServerDao {
    @Query("SELECT * FROM deploy_servers ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<DeployServerEntity>>

    @Query("SELECT * FROM deploy_servers ORDER BY createdAt ASC")
    suspend fun getAll(): List<DeployServerEntity>

    @Query("SELECT * FROM deploy_servers WHERE id = :id")
    suspend fun getById(id: String): DeployServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeployServerEntity)

    @Query("DELETE FROM deploy_servers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE deploy_servers SET lastDeployStatus = :status, lastDeployAt = :at, knownHostKeyFingerprint = :fingerprint WHERE id = :id")
    suspend fun recordDeployResult(id: String, status: String, at: Long, fingerprint: String)

    @Query("UPDATE deploy_servers SET lastDeployStatus = :status, lastDeployAt = :at WHERE id = :id")
    suspend fun recordDeployStatus(id: String, status: String, at: Long)
}
