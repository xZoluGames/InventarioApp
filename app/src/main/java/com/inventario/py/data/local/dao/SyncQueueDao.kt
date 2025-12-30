package com.inventario.py.data.local.dao

import androidx.room.*
import com.inventario.py.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>
    
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<SyncQueueEntity>
    
    @Query("SELECT * FROM sync_queue ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<SyncQueueEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncQueueEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SyncQueueEntity>)
    
    @Query("UPDATE sync_queue SET status = 'SYNCED', syncedAt = :timestamp WHERE id = :id")
    suspend fun markAsSynced(id: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE sync_queue SET status = 'FAILED', errorMessage = :error, retryCount = retryCount + 1 WHERE id = :id")
    suspend fun markAsFailed(id: Long, error: String)
    
    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED' AND syncedAt < :before")
    suspend fun deleteOldSynced(before: Long)
    
    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)
    
    @Query("DELETE FROM sync_queue")
    suspend fun deleteAll()
}
