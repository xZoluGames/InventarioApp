package com.inventario.py.data.repository

import android.util.Log
import com.inventario.py.data.local.dao.ProductDao
import com.inventario.py.data.local.entity.*
import com.inventario.py.data.remote.api.InventarioApi
import com.inventario.py.data.remote.dto.*
import com.inventario.py.utils.Generators
import com.inventario.py.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao,
    private val api: InventarioApi,
    private val sessionManager: SessionManager
) {

    companion object {
        private const val TAG = "ProductRepository"
    }

    // ==================== PRODUCTOS ====================

    fun getAllProducts(): Flow<List<ProductEntity>> = productDao.getAllProducts()

    fun getRecentProducts(limit: Int = 50): Flow<List<ProductEntity>> =
        productDao.getRecentProducts(limit)

    suspend fun getProductById(id: String): ProductEntity? = productDao.getProductById(id)

    fun getProductByIdFlow(id: String): Flow<ProductEntity?> = productDao.getProductByIdFlow(id)

    suspend fun getProductByBarcode(barcode: String): ProductEntity? =
        productDao.getProductByBarcode(barcode)

    suspend fun getProductByIdentifier(identifier: String): ProductEntity? =
        productDao.getProductByIdentifier(identifier)

    suspend fun searchProducts(query: String, limit: Int = 50): List<ProductEntity> =
        productDao.searchProducts(query, limit)

    fun searchProductsFlow(query: String): Flow<List<ProductEntity>> =
        productDao.searchProductsFlow(query)

    fun getProductsByCategory(categoryId: String): Flow<List<ProductEntity>> =
        productDao.getProductsByCategory(categoryId)

    fun getLowStockProducts(): Flow<List<ProductEntity>> = productDao.getLowStockProducts()

    fun getOutOfStockProducts(): Flow<List<ProductEntity>> = productDao.getOutOfStockProducts()

    fun getProductCount(): Flow<Int> = productDao.getProductCount()

    fun getTotalInventoryValue(): Flow<Long?> = productDao.getTotalInventoryValue()

    fun getTotalInventoryCost(): Flow<Long?> = productDao.getTotalInventoryCost()

    suspend fun saveProduct(product: ProductEntity) {
        productDao.insertProduct(product.copy(
            syncStatus = ProductEntity.SYNC_STATUS_PENDING,
            updatedAt = System.currentTimeMillis()
        ))
    }

    /**
     * Inserta un producto (alias para saveProduct)
     */
    suspend fun insertProduct(product: ProductEntity): Long {
        productDao.insertProduct(product.copy(
            syncStatus = ProductEntity.SYNC_STATUS_PENDING,
            updatedAt = System.currentTimeMillis()
        ))
        return 1L
    }

    /**
     * Crea un nuevo producto - intenta enviar a API primero, luego guarda localmente
     */
    suspend fun createProduct(
        name: String,
        description: String?,
        barcode: String?,
        identifier: String?,
        categoryId: String?,
        totalStock: Int,
        minStockAlert: Int,
        salePrice: Long,
        purchasePrice: Long,
        supplierId: String?,
        supplierName: String?,
        quality: String?,
        imageUrl: String?
    ): ProductEntity = withContext(Dispatchers.IO) {
        val generatedId = Generators.generateId()
        val generatedIdentifier = identifier ?: Generators.generateIdentifier()

        // Crear request para API
        val request = CreateProductRequest(
            name = name,
            description = description,
            barcode = barcode,
            identifier = generatedIdentifier,
            categoryId = categoryId,
            totalStock = totalStock,
            minStockAlert = minStockAlert,
            salePrice = salePrice,
            purchasePrice = purchasePrice,
            supplierId = supplierId,
            supplierName = supplierName,
            quality = quality,
            variants = null
        )

        try {
            // Intentar enviar a API
            Log.d(TAG, "Creating product on server: $name")
            val response = api.createProduct(request)

            if (response.isSuccessful && response.body()?.data != null) {
                // Producto creado en servidor, guardar localmente con estado SYNCED
                val serverProduct = response.body()!!.data!!
                val localProduct = serverProduct.toEntity()
                productDao.insertProduct(localProduct)
                Log.d(TAG, "Product created successfully: ${localProduct.id}")
                return@withContext localProduct
            } else {
                // Error del servidor, guardar localmente como PENDING
                val errorMsg = response.errorBody()?.string() ?: response.message()
                Log.e(TAG, "Server error: $errorMsg")
                throw Exception(errorMsg)
            }
        } catch (e: Exception) {
            // Sin conexión o error, guardar localmente como PENDING
            Log.e(TAG, "Error creating on server: ${e.message}, saving locally")
            val product = ProductEntity(
                id = generatedId,
                name = name,
                description = description,
                barcode = barcode,
                identifier = generatedIdentifier,
                categoryId = categoryId,
                totalStock = totalStock,
                minStockAlert = minStockAlert,
                salePrice = salePrice,
                purchasePrice = purchasePrice,
                supplierId = supplierId,
                supplierName = supplierName,
                quality = quality,
                imageUrl = imageUrl,
                createdBy = sessionManager.getUserId(),
                syncStatus = ProductEntity.SYNC_STATUS_PENDING
            )
            productDao.insertProduct(product)
            return@withContext product
        }
    }

    /**
     * Actualiza un producto existente - intenta enviar a API primero
     */
    suspend fun updateProduct(product: ProductEntity) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating product on server: ${product.id}")

            // Crear DTO para enviar al servidor
            val productDto = ProductDto(
                id = product.id,
                name = product.name,
                description = product.description,
                barcode = product.barcode,
                identifier = product.identifier,
                imageUrl = product.imageUrl,
                categoryId = product.categoryId,
                subcategoryId = product.subcategoryId,
                totalStock = product.totalStock,
                minStockAlert = product.minStockAlert,
                isStockAlertEnabled = product.isStockAlertEnabled,
                salePrice = product.salePrice,
                purchasePrice = product.purchasePrice,
                supplierId = product.supplierId,
                supplierName = product.supplierName,
                quality = product.quality,
                isActive = product.isActive,
                createdAt = product.createdAt,
                updatedAt = System.currentTimeMillis(),
                createdBy = product.createdBy,
                variants = null,
                images = null
            )

            val response = api.updateProduct(product.id, productDto)
            if (response.isSuccessful) {
                productDao.updateProduct(product.copy(
                    syncStatus = ProductEntity.SYNC_STATUS_SYNCED,
                    updatedAt = System.currentTimeMillis()
                ))
                Log.d(TAG, "Product updated successfully: ${product.id}")
            } else {
                throw Exception(response.message())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating on server: ${e.message}, saving locally as pending")
            productDao.updateProduct(product.copy(
                syncStatus = ProductEntity.SYNC_STATUS_PENDING,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    /**
     * Actualiza el stock de un producto
     */
    suspend fun updateStock(productId: String, newStock: Int) {
        productDao.updateStock(productId, newStock, syncStatus = ProductEntity.SYNC_STATUS_PENDING)
    }

    /**
     * Actualiza el stock del producto (con parámetros adicionales)
     */
    suspend fun updateProductStock(productId: String, newStock: Int, reason: String? = null) {
        productDao.updateStock(productId, newStock, syncStatus = ProductEntity.SYNC_STATUS_PENDING)
    }

    /**
     * Elimina un producto - intenta eliminar de API primero
     */
    suspend fun deleteProduct(productId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Deleting product from server: $productId")
            val response = api.deleteProduct(productId)
            if (response.isSuccessful) {
                productDao.softDeleteProduct(productId)
                Log.d(TAG, "Product deleted successfully: $productId")
            } else {
                throw Exception(response.message())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting from server: ${e.message}, marking as deleted locally")
            // Marcar para eliminar en sincronización posterior
            productDao.softDeleteProduct(productId)
        }
    }

    suspend fun deleteProduct(product: ProductEntity) {
        deleteProduct(product.id)
    }

    // ==================== VARIANTES ====================

    fun getVariantsByProduct(productId: String): Flow<List<ProductVariantEntity>> =
        productDao.getVariantsByProduct(productId)

    suspend fun getVariantsByProductSync(productId: String): List<ProductVariantEntity> =
        productDao.getVariantsByProductSync(productId)

    suspend fun getVariantsByProductId(productId: String): List<ProductVariantEntity> =
        productDao.getVariantsByProductSync(productId)

    suspend fun getVariantById(variantId: String): ProductVariantEntity? =
        productDao.getVariantById(variantId)

    suspend fun getVariantByBarcode(barcode: String): ProductVariantEntity? =
        productDao.getVariantByBarcode(barcode)

    suspend fun saveVariant(variant: ProductVariantEntity) {
        productDao.insertVariant(variant.copy(
            syncStatus = 1,
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun createVariant(
        productId: String,
        variantType: String,
        variantLabel: String,
        variantValue: String,
        stock: Int,
        additionalPrice: Long,
        barcode: String?
    ): ProductVariantEntity = withContext(Dispatchers.IO) {
        val variant = ProductVariantEntity(
            id = Generators.generateId(),
            productId = productId,
            variantType = variantType,
            variantLabel = variantLabel,
            variantValue = variantValue,
            stock = stock,
            additionalPrice = additionalPrice,
            barcode = barcode,
            syncStatus = 1
        )

        try {
            val request = CreateVariantRequest(
                variantType = variantType,
                variantLabel = variantLabel,
                variantValue = variantValue,
                stock = stock,
                additionalPrice = additionalPrice,
                barcode = barcode
            )

            val response = api.createVariant(productId, request)
            if (response.isSuccessful && response.body()?.data != null) {
                val serverVariant = response.body()!!.data!!.toEntity()
                productDao.insertVariant(serverVariant)
                return@withContext serverVariant
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating variant on server: ${e.message}")
        }

        // Si falla la API, guardar localmente
        productDao.insertVariant(variant)
        return@withContext variant
    }

    suspend fun updateVariantStock(variantId: String, newStock: Int) {
        productDao.updateVariantStock(variantId, newStock)

        // Actualizar stock total del producto
        val variant = productDao.getVariantById(variantId)
        variant?.let {
            val totalStock = productDao.getTotalVariantStock(it.productId) ?: 0
            productDao.updateStock(it.productId, totalStock)
        }
    }

    suspend fun deleteVariant(variant: ProductVariantEntity) {
        try {
            val response = api.deleteVariant(variant.id)
            if (response.isSuccessful) {
                productDao.deleteVariant(variant)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting variant from server: ${e.message}")
            productDao.deleteVariant(variant)
        }
    }

    // ==================== MOVIMIENTOS DE STOCK ====================

    fun getStockMovementsForProduct(productId: String): Flow<List<StockMovementEntity>> =
        productDao.getStockMovementsByProduct(productId)

    suspend fun getStockMovementsForProductSync(productId: String): List<StockMovementEntity> =
        productDao.getStockMovementsByProductSync(productId)

    suspend fun saveStockMovement(movement: StockMovementEntity) {
        productDao.insertStockMovement(movement)
    }

    // ==================== CATEGORÍAS ====================

    fun getAllCategories(): Flow<List<CategoryEntity>> = productDao.getAllCategories()

    fun getMainCategories(): Flow<List<CategoryEntity>> = productDao.getMainCategories()

    fun getSubcategories(parentId: String): Flow<List<CategoryEntity>> =
        productDao.getSubcategories(parentId)

    suspend fun getCategoryById(id: String): CategoryEntity? = productDao.getCategoryById(id)

    suspend fun saveCategory(category: CategoryEntity) {
        productDao.insertCategory(category.copy(
            syncStatus = 1,
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun createCategory(
        name: String,
        description: String?,
        parentId: String?,
        iconName: String?,
        colorHex: String?
    ): CategoryEntity {
        val category = CategoryEntity(
            id = Generators.generateId(),
            name = name,
            description = description,
            parentId = parentId,
            iconName = iconName,
            colorHex = colorHex,
            syncStatus = 1
        )
        productDao.insertCategory(category)
        return category
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        productDao.deleteCategory(category)
    }

    // ==================== PROVEEDORES ====================

    fun getAllSuppliers(): Flow<List<SupplierEntity>> = productDao.getAllSuppliers()

    suspend fun getSupplierById(id: String): SupplierEntity? = productDao.getSupplierById(id)

    suspend fun searchSuppliers(query: String): List<SupplierEntity> =
        productDao.searchSuppliers(query)

    suspend fun saveSupplier(supplier: SupplierEntity) {
        productDao.insertSupplier(supplier.copy(
            syncStatus = 1,
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun createSupplier(
        name: String,
        contactName: String?,
        phone: String?,
        email: String?,
        address: String?,
        city: String?,
        notes: String?
    ): SupplierEntity {
        val supplier = SupplierEntity(
            id = Generators.generateId(),
            name = name,
            contactName = contactName,
            phone = phone,
            email = email,
            address = address,
            city = city,
            notes = notes,
            syncStatus = 1
        )
        productDao.insertSupplier(supplier)
        return supplier
    }

    suspend fun deleteSupplier(supplier: SupplierEntity) {
        productDao.deleteSupplier(supplier)
    }

    // ==================== IMÁGENES ====================

    fun getImagesByProduct(productId: String): Flow<List<ProductImageEntity>> =
        productDao.getImagesByProduct(productId)

    suspend fun saveImage(image: ProductImageEntity) {
        productDao.insertImage(image)
    }

    suspend fun deleteImage(image: ProductImageEntity) {
        productDao.deleteImage(image)
    }

    // ==================== HISTORIAL DE PRECIOS ====================

    fun getPriceHistory(productId: String): Flow<List<ProductPriceHistoryEntity>> =
        productDao.getPriceHistory(productId)

    suspend fun savePriceHistory(
        productId: String,
        salePrice: Long,
        purchasePrice: Long,
        reason: String?
    ) {
        val history = ProductPriceHistoryEntity(
            id = Generators.generateId(),
            productId = productId,
            salePrice = salePrice,
            purchasePrice = purchasePrice,
            changedBy = sessionManager.getUserId(),
            reason = reason
        )
        productDao.insertPriceHistory(history)
    }

    // ==================== SINCRONIZACIÓN ====================

    /**
     * Sincroniza productos desde el servidor
     */
    suspend fun syncFromServer(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var totalSynced = 0
            var page = 1
            var hasMore = true

            while (hasMore) {
                val response = api.getProducts(page = page, limit = 100)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val products = body.data.map { it.toEntity() }
                    productDao.insertProducts(products)
                    totalSynced += products.size

                    // También guardar variantes
                    body.data.forEach { productDto ->
                        productDto.variants?.let { variants ->
                            val variantEntities = variants.map { it.toEntity() }
                            productDao.insertVariants(variantEntities)
                        }
                    }

                    hasMore = page < body.totalPages
                    page++
                } else {
                    hasMore = false
                }
            }

            Log.d(TAG, "Synced $totalSynced products from server")
            Result.success(totalSynced)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing from server: ${e.message}")
            Result.failure(e)
        }
    }
}