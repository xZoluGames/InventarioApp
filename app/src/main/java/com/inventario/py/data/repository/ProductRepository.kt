package com.inventario.py.data.repository

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
        return 1L // Room no devuelve el ID para String PK
    }

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
    ): ProductEntity {
        val product = ProductEntity(
            id = Generators.generateId(),
            name = name,
            description = description,
            barcode = barcode,
            identifier = identifier ?: Generators.generateIdentifier(),
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
        return product
    }

    suspend fun updateProduct(product: ProductEntity) {
        productDao.updateProduct(product.copy(
            syncStatus = ProductEntity.SYNC_STATUS_PENDING,
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun updateStock(productId: String, newStock: Int) {
        productDao.updateStock(productId, newStock, syncStatus = ProductEntity.SYNC_STATUS_PENDING)
    }

    /**
     * Actualiza el stock del producto (con parámetros adicionales)
     */
    suspend fun updateProductStock(productId: String, newStock: Int, reason: String? = null) {
        productDao.updateStock(productId, newStock, syncStatus = ProductEntity.SYNC_STATUS_PENDING)
    }

    suspend fun deleteProduct(productId: String) {
        productDao.softDeleteProduct(productId)
    }

    suspend fun deleteProduct(product: ProductEntity) {
        productDao.softDeleteProduct(product.id)
    }

    // ==================== VARIANTES ====================

    fun getVariantsByProduct(productId: String): Flow<List<ProductVariantEntity>> =
        productDao.getVariantsByProduct(productId)

    suspend fun getVariantsByProductSync(productId: String): List<ProductVariantEntity> =
        productDao.getVariantsByProductSync(productId)

    /**
     * Alias para getVariantsByProductSync
     */
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
        // Actualizar stock total del producto
        updateProductTotalStock(variant.productId)
    }

    /**
     * Guarda múltiples variantes
     */
    suspend fun saveVariants(variants: List<ProductVariantEntity>) {
        variants.forEach { variant ->
            saveVariant(variant)
        }
    }

    suspend fun createVariant(
        productId: String,
        variantType: String,
        variantLabel: String,
        variantValue: String,
        stock: Int,
        additionalPrice: Long = 0,
        barcode: String? = null
    ): ProductVariantEntity {
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
        productDao.insertVariant(variant)
        updateProductTotalStock(productId)
        return variant
    }

    suspend fun updateVariantStock(variantId: String, stock: Int) {
        val variant = productDao.getVariantById(variantId)
        variant?.let {
            productDao.updateVariantStock(variantId, stock)
            updateProductTotalStock(it.productId)
        }
    }

    suspend fun deleteVariant(variant: ProductVariantEntity) {
        productDao.deleteVariantById(variant.id)
        updateProductTotalStock(variant.productId)
    }

    suspend fun deleteVariantsByProduct(productId: String) {
        productDao.deleteVariantsByProduct(productId)
    }

    private suspend fun updateProductTotalStock(productId: String) {
        val totalStock = productDao.getTotalVariantStock(productId) ?: 0
        productDao.updateStock(productId, totalStock)
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
}