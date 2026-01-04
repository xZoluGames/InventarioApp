package com.inventario.py.data.repository

import android.content.Context
import android.util.Log
import com.inventario.py.data.local.database.InventarioDatabase
import com.inventario.py.data.local.entity.*
import com.inventario.py.data.remote.api.InventarioApi
import com.inventario.py.data.remote.dto.*
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
    private val api: InventarioApi,
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "SyncRepository"
        private const val PREFS_NAME = "sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_at"
    }

    private val syncPrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get count of pending sync items
     */
    fun getPendingSyncCount(): Flow<Int> {
        return database.syncQueueDao().getPendingCount()
    }

    /**
     * Get last sync timestamp
     */
    fun getLastSyncTime(): Long {
        return syncPrefs.getLong(KEY_LAST_SYNC, 0L)
    }

    /**
     * Save last sync timestamp
     */
    private fun saveLastSyncTime(timestamp: Long) {
        syncPrefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply()
    }

    /**
     * Sync all pending changes with server
     */
    suspend fun syncAll(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting sync...")

            // Get all pending sync items
            val pendingItems = database.syncQueueDao().getAllPending()

            if (pendingItems.isEmpty()) {
                Log.d(TAG, "No pending items to sync")
                // Even if no pending items, pull from server
                pullFromServer()
                return@withContext Result.success(Unit)
            }

            Log.d(TAG, "Found ${pendingItems.size} pending items")

            for (item in pendingItems) {
                try {
                    when (item.entityType) {
                        "PRODUCT" -> syncProduct(item.entityId, item.operation)
                        "SALE" -> syncSale(item.entityId, item.operation)
                        "CATEGORY" -> syncCategory(item.entityId, item.operation)
                        "SUPPLIER" -> syncSupplier(item.entityId, item.operation)
                        "VARIANT" -> syncVariant(item.entityId, item.operation)
                    }
                    // Mark as synced
                    database.syncQueueDao().markAsSynced(item.id)
                    Log.d(TAG, "Synced ${item.entityType} ${item.entityId}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync ${item.entityType} ${item.entityId}: ${e.message}")
                    // Mark failed, will retry later
                    database.syncQueueDao().markAsFailed(item.id, e.message ?: "Unknown error")
                }
            }

            // After pushing changes, pull latest from server
            pullFromServer()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Sync a product to server
     */
    private suspend fun syncProduct(entityId: String, operation: String) {
        when (operation) {
            "INSERT" -> {
                val product = database.productDao().getProductById(entityId)
                product?.let {
                    val request = CreateProductRequest(
                        name = it.name,
                        description = it.description,
                        barcode = it.barcode,
                        identifier = it.identifier,
                        categoryId = it.categoryId,
                        totalStock = it.totalStock,
                        minStockAlert = it.minStockAlert,
                        salePrice = it.salePrice,
                        purchasePrice = it.purchasePrice,
                        supplierId = it.supplierId,
                        supplierName = it.supplierName,
                        quality = it.quality,
                        variants = null
                    )
                    val response = api.createProduct(request)
                    if (response.isSuccessful && response.body()?.data != null) {
                        // Update local with server data
                        val serverProduct = response.body()!!.data!!.toEntity()
                        database.productDao().updateProduct(serverProduct)
                        Log.d(TAG, "Product created on server: ${serverProduct.id}")
                    } else {
                        throw Exception(response.message() ?: "Failed to create product")
                    }
                }
            }
            "UPDATE" -> {
                val product = database.productDao().getProductById(entityId)
                product?.let {
                    val dto = ProductDto(
                        id = it.id,
                        name = it.name,
                        description = it.description,
                        barcode = it.barcode,
                        identifier = it.identifier,
                        imageUrl = it.imageUrl,
                        categoryId = it.categoryId,
                        subcategoryId = it.subcategoryId,
                        totalStock = it.totalStock,
                        minStockAlert = it.minStockAlert,
                        isStockAlertEnabled = it.isStockAlertEnabled,
                        salePrice = it.salePrice,
                        purchasePrice = it.purchasePrice,
                        supplierId = it.supplierId,
                        supplierName = it.supplierName,
                        quality = it.quality,
                        isActive = it.isActive,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt,
                        createdBy = it.createdBy,
                        variants = null,
                        images = null
                    )
                    val response = api.updateProduct(it.id, dto)
                    if (response.isSuccessful) {
                        database.productDao().updateSyncStatus(it.id, ProductEntity.SYNC_STATUS_SYNCED)
                        Log.d(TAG, "Product updated on server: ${it.id}")
                    } else {
                        throw Exception(response.message() ?: "Failed to update product")
                    }
                }
            }
            "DELETE" -> {
                val response = api.deleteProduct(entityId)
                if (response.isSuccessful) {
                    Log.d(TAG, "Product deleted on server: $entityId")
                } else {
                    throw Exception(response.message() ?: "Failed to delete product")
                }
            }
        }
    }

    /**
     * Sync a sale to server
     */
    private suspend fun syncSale(entityId: String, operation: String) {
        when (operation) {
            "INSERT" -> {
                val sale = database.salesDao().getSaleById(entityId)
                val items = database.salesDao().getSaleItemsSync(entityId)

                sale?.let {
                    val itemRequests = items.map { item ->
                        CreateSaleItemRequest(
                            productId = item.productId,
                            variantId = item.variantId,
                            quantity = item.quantity,
                            unitPrice = item.unitPrice,
                            discount = item.discount
                        )
                    }

                    val request = CreateSaleRequest(
                        customerId = it.customerId,
                        customerName = it.customerName,
                        items = itemRequests,
                        totalDiscount = it.totalDiscount,
                        paymentMethod = it.paymentMethod,
                        amountPaid = it.amountPaid,
                        notes = it.notes
                    )

                    val response = api.createSale(request)
                    if (response.isSuccessful && response.body()?.data != null) {
                        database.salesDao().updateSyncStatus(entityId, 0)
                        Log.d(TAG, "Sale created on server: $entityId")
                    } else {
                        throw Exception(response.message() ?: "Failed to create sale")
                    }
                }
            }
            "CANCEL" -> {
                val sale = database.salesDao().getSaleById(entityId)
                sale?.let {
                    val response = api.cancelSale(entityId, mapOf("reason" to (it.cancellationReason ?: "")))
                    if (response.isSuccessful) {
                        database.salesDao().updateSyncStatus(entityId, 0)
                        Log.d(TAG, "Sale cancelled on server: $entityId")
                    } else {
                        throw Exception(response.message() ?: "Failed to cancel sale")
                    }
                }
            }
        }
    }

    /**
     * Sync a category to server
     */
    private suspend fun syncCategory(entityId: String, operation: String) {
        when (operation) {
            "INSERT", "UPDATE" -> {
                val category = database.productDao().getCategoryById(entityId)
                category?.let {
                    val dto = CategoryDto(
                        id = it.id,
                        name = it.name,
                        description = it.description,
                        parentId = it.parentId,
                        iconName = it.iconName,
                        colorHex = it.colorHex,
                        sortOrder = it.sortOrder,
                        isActive = it.isActive,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt
                    )

                    val response = if (operation == "INSERT") {
                        api.createCategory(dto)
                    } else {
                        api.updateCategory(it.id, dto)
                    }

                    if (response.isSuccessful) {
                        database.productDao().updateCategorySyncStatus(it.id, 0)
                        Log.d(TAG, "Category synced: ${it.id}")
                    } else {
                        throw Exception(response.message() ?: "Failed to sync category")
                    }
                }
            }
            "DELETE" -> {
                val response = api.deleteCategory(entityId)
                if (response.isSuccessful) {
                    Log.d(TAG, "Category deleted on server: $entityId")
                } else {
                    throw Exception(response.message() ?: "Failed to delete category")
                }
            }
        }
    }

    /**
     * Sync a supplier to server
     */
    private suspend fun syncSupplier(entityId: String, operation: String) {
        when (operation) {
            "INSERT", "UPDATE" -> {
                val supplier = database.productDao().getSupplierById(entityId)
                supplier?.let {
                    val dto = SupplierDto(
                        id = it.id,
                        name = it.name,
                        contactName = it.contactName,
                        phone = it.phone,
                        email = it.email,
                        address = it.address,
                        city = it.city,
                        notes = it.notes,
                        isActive = it.isActive,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt
                    )

                    val response = if (operation == "INSERT") {
                        api.createSupplier(dto)
                    } else {
                        api.updateSupplier(it.id, dto)
                    }

                    if (response.isSuccessful) {
                        database.productDao().updateSupplierSyncStatus(it.id, 0)
                        Log.d(TAG, "Supplier synced: ${it.id}")
                    } else {
                        throw Exception(response.message() ?: "Failed to sync supplier")
                    }
                }
            }
            "DELETE" -> {
                val response = api.deleteSupplier(entityId)
                if (response.isSuccessful) {
                    Log.d(TAG, "Supplier deleted on server: $entityId")
                } else {
                    throw Exception(response.message() ?: "Failed to delete supplier")
                }
            }
        }
    }

    /**
     * Sync a variant to server
     */
    private suspend fun syncVariant(entityId: String, operation: String) {
        when (operation) {
            "INSERT" -> {
                val variant = database.productDao().getVariantById(entityId)
                variant?.let {
                    val request = CreateVariantRequest(
                        variantType = it.variantType,
                        variantLabel = it.variantLabel,
                        variantValue = it.variantValue,
                        stock = it.stock,
                        additionalPrice = it.additionalPrice,
                        barcode = it.barcode
                    )
                    val response = api.createVariant(it.productId, request)
                    if (response.isSuccessful) {
                        database.productDao().updateVariantSyncStatus(it.id, 0)
                        Log.d(TAG, "Variant created on server: ${it.id}")
                    } else {
                        throw Exception(response.message() ?: "Failed to create variant")
                    }
                }
            }
            "UPDATE" -> {
                val variant = database.productDao().getVariantById(entityId)
                variant?.let {
                    val dto = ProductVariantDto(
                        id = it.id,
                        productId = it.productId,
                        variantType = it.variantType,
                        variantLabel = it.variantLabel,
                        variantValue = it.variantValue,
                        stock = it.stock,
                        additionalPrice = it.additionalPrice,
                        barcode = it.barcode,
                        isActive = it.isActive,
                        createdAt = it.createdAt,
                        updatedAt = it.updatedAt
                    )
                    val response = api.updateVariant(it.id, dto)
                    if (response.isSuccessful) {
                        database.productDao().updateVariantSyncStatus(it.id, 0)
                        Log.d(TAG, "Variant updated on server: ${it.id}")
                    } else {
                        throw Exception(response.message() ?: "Failed to update variant")
                    }
                }
            }
            "DELETE" -> {
                val response = api.deleteVariant(entityId)
                if (response.isSuccessful) {
                    Log.d(TAG, "Variant deleted on server: $entityId")
                } else {
                    throw Exception(response.message() ?: "Failed to delete variant")
                }
            }
        }
    }

    /**
     * Pull all data from server
     */
    suspend fun pullFromServer(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val lastSync = getLastSyncTime()
            Log.d(TAG, "Pulling from server since: $lastSync")

            // Pull categories
            try {
                val categoriesResponse = api.getCategories()
                if (categoriesResponse.isSuccessful && categoriesResponse.body()?.data != null) {
                    val categories = categoriesResponse.body()!!.data!!.map { it.toEntity() }
                    database.productDao().insertCategories(categories)
                    Log.d(TAG, "Pulled ${categories.size} categories")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pull categories: ${e.message}")
            }

            // Pull suppliers
            try {
                val suppliersResponse = api.getSuppliers()
                if (suppliersResponse.isSuccessful && suppliersResponse.body()?.data != null) {
                    val suppliers = suppliersResponse.body()!!.data!!.map { it.toEntity() }
                    database.productDao().insertSuppliers(suppliers)
                    Log.d(TAG, "Pulled ${suppliers.size} suppliers")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pull suppliers: ${e.message}")
            }

            // Pull products (paginated)
            try {
                var page = 1
                var hasMore = true
                while (hasMore) {
                    val productsResponse = api.getProducts(page = page, limit = 100)
                    if (productsResponse.isSuccessful && productsResponse.body() != null) {
                        val body = productsResponse.body()!!
                        val products = body.data.map { it.toEntity() }
                        database.productDao().insertProducts(products)

                        // Also save variants if present
                        body.data.forEach { productDto ->
                            productDto.variants?.let { variants ->
                                val variantEntities = variants.map { it.toEntity() }
                                database.productDao().insertVariants(variantEntities)
                            }
                        }

                        Log.d(TAG, "Pulled ${products.size} products (page $page)")
                        hasMore = page < body.totalPages
                        page++
                    } else {
                        hasMore = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pull products: ${e.message}")
            }

            // Save sync time
            saveLastSyncTime(System.currentTimeMillis())

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Pull from server failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Add item to sync queue
     */
    suspend fun addToSyncQueue(entityType: String, entityId: String, operation: String) {
        withContext(Dispatchers.IO) {
            database.syncQueueDao().insert(
                SyncQueueEntity(
                    entityType = entityType,
                    entityId = entityId,
                    operation = operation,
                    createdAt = System.currentTimeMillis()
                )
            )
            Log.d(TAG, "Added to sync queue: $entityType $entityId $operation")
        }
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
     * Clear all sync queue
     */
    suspend fun clearSyncQueue() = withContext(Dispatchers.IO) {
        database.syncQueueDao().deleteAll()
    }

    /**
     * Get failed sync items for retry
     */
    suspend fun getFailedSyncItems(): List<SyncQueueEntity> = withContext(Dispatchers.IO) {
        database.syncQueueDao().getFailedItems()
    }

    /**
     * Retry failed sync items
     */
    suspend fun retryFailedItems(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val failedItems = database.syncQueueDao().getFailedItems()
            for (item in failedItems) {
                // Reset to pending
                database.syncQueueDao().resetToPending(item.id)
            }
            // Try sync again
            syncAll()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}