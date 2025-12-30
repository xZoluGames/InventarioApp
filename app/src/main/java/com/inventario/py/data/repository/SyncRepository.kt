package com.inventario.py.data.repository

import android.content.Context
import com.inventario.py.data.local.database.InventarioDatabase
import com.inventario.py.data.remote.api.InventarioApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val database: InventarioDatabase,
    private val apiService: InventarioApi,
    @ApplicationContext private val context: Context
) {
    
    /**
     * Get count of pending sync items
     */
    fun getPendingSyncCount(): Flow<Int> {
        return database.syncQueueDao().getPendingCount()
    }
    
    /**
     * Sync all pending changes with server
     */
    suspend fun syncAll(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Get all pending sync items
            val pendingItems = database.syncQueueDao().getAllPending()
            
            if (pendingItems.isEmpty()) {
                return@withContext Result.success(Unit)
            }
            
            for (item in pendingItems) {
                try {
                    when (item.entityType) {
                        "PRODUCT" -> syncProduct(item.entityId, item.operation)
                        "SALE" -> syncSale(item.entityId, item.operation)
                        "USER" -> syncUser(item.entityId, item.operation)
                        "CATEGORY" -> syncCategory(item.entityId, item.operation)
                        "SUPPLIER" -> syncSupplier(item.entityId, item.operation)
                    }
                    // Mark as synced
                    database.syncQueueDao().markAsSynced(item.id)
                } catch (e: Exception) {
                    // Mark failed, will retry later
                    database.syncQueueDao().markAsFailed(item.id, e.message ?: "Unknown error")
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun syncProduct(entityId: String, operation: String) {
        when (operation) {
            "INSERT", "UPDATE" -> {
                val product = database.productDao().getProductById(entityId)
                product?.let {
                    // apiService.upsertProduct(it.toDto())
                }
            }
            "DELETE" -> {
                // apiService.deleteProduct(entityId)
            }
        }
    }
    
    private suspend fun syncSale(entityId: String, operation: String) {
        when (operation) {
            "INSERT" -> {
                val sale = database.salesDao().getSaleById(entityId)
                sale?.let {
                    // apiService.createSale(it.toDto())
                }
            }
            "UPDATE" -> {
                val sale = database.salesDao().getSaleById(entityId)
                sale?.let {
                    // apiService.updateSale(it.toDto())
                }
            }
        }
    }
    
    private suspend fun syncUser(entityId: String, operation: String) {
        // User sync logic
    }
    
    private suspend fun syncCategory(entityId: String, operation: String) {
        // Category sync logic
    }
    
    private suspend fun syncSupplier(entityId: String, operation: String) {
        // Supplier sync logic
    }
    
    /**
     * Export local database as backup
     */
    suspend fun exportLocalBackup(): File = withContext(Dispatchers.IO) {
        val backupDir = File(context.getExternalFilesDir(null), "backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        
        val backupFile = File(backupDir, "backup_${System.currentTimeMillis()}.db")
        
        // Close database and copy
        database.close()
        val dbFile = context.getDatabasePath("inventario_database")
        dbFile.copyTo(backupFile, overwrite = true)
        
        backupFile
    }
    
    /**
     * Import backup from file
     */
    suspend fun importLocalBackup(filePath: String) = withContext(Dispatchers.IO) {
        val backupFile = File(filePath)
        if (!backupFile.exists()) {
            throw IllegalArgumentException("Archivo de respaldo no encontrado")
        }
        
        // Close database and replace
        database.close()
        val dbFile = context.getDatabasePath("inventario_database")
        backupFile.copyTo(dbFile, overwrite = true)
    }
    
    /**
     * Pull all data from server
     */
    suspend fun pullFromServer(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Pull products
            // val products = apiService.getAllProducts()
            // database.productDao().insertAll(products.map { it.toEntity() })
            
            // Pull categories
            // val categories = apiService.getAllCategories()
            // database.categoryDao().insertAll(categories.map { it.toEntity() })
            
            // Pull suppliers
            // val suppliers = apiService.getAllSuppliers()
            // database.supplierDao().insertAll(suppliers.map { it.toEntity() })
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Add item to sync queue
     */
    suspend fun addToSyncQueue(entityType: String, entityId: String, operation: String) {
        withContext(Dispatchers.IO) {
            database.syncQueueDao().insert(
                com.inventario.py.data.local.entity.SyncQueueEntity(
                    entityType = entityType,
                    entityId = entityId,
                    operation = operation,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }
}
