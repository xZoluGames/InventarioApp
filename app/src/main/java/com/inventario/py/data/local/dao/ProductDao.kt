package com.inventario.py.data.local.dao

import androidx.room.*
import com.inventario.py.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    // ==================== PRODUCTOS ====================

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentProducts(limit: Int = 50): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductByIdSync(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    fun getProductByIdFlow(id: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE barcode = :barcode AND isActive = 1 LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE identifier = :identifier AND isActive = 1 LIMIT 1")
    suspend fun getProductByIdentifier(identifier: String): ProductEntity?

    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 
        AND (name LIKE '%' || :query || '%' 
             OR identifier LIKE '%' || :query || '%' 
             OR barcode LIKE '%' || :query || '%'
             OR description LIKE '%' || :query || '%')
        ORDER BY 
            CASE WHEN name LIKE :query || '%' THEN 0 ELSE 1 END,
            name ASC
        LIMIT :limit
    """)
    suspend fun searchProducts(query: String, limit: Int = 50): List<ProductEntity>

    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 
        AND (name LIKE '%' || :query || '%' 
             OR identifier LIKE '%' || :query || '%' 
             OR barcode LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    fun searchProductsFlow(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE categoryId = :categoryId AND isActive = 1 ORDER BY name ASC")
    fun getProductsByCategory(categoryId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE totalStock <= minStockAlert AND isStockAlertEnabled = 1 AND isActive = 1")
    fun getLowStockProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE totalStock <= 0 AND isActive = 1")
    fun getOutOfStockProducts(): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1")
    fun getProductCount(): Flow<Int>

    @Query("SELECT SUM(salePrice * totalStock) FROM products WHERE isActive = 1")
    fun getTotalInventoryValue(): Flow<Long?>

    @Query("SELECT SUM(purchasePrice * totalStock) FROM products WHERE isActive = 1")
    fun getTotalInventoryCost(): Flow<Long?>

    @Query("SELECT * FROM products WHERE syncStatus != 0")
    suspend fun getUnsyncedProducts(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET totalStock = :stock, syncStatus = :syncStatus, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun updateStock(
        productId: String,
        stock: Int,
        syncStatus: Int = ProductEntity.SYNC_STATUS_PENDING,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE products SET isActive = 0, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun softDeleteProduct(productId: String, updatedAt: Long = System.currentTimeMillis())
    @Query("UPDATE products SET syncStatus = :syncStatus, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun updateSyncStatus(
        productId: String,
        syncStatus: Int,
        updatedAt: Long = System.currentTimeMillis()
    )
    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    // ==================== VARIANTES ====================

    @Query("SELECT * FROM product_variants WHERE productId = :productId AND isActive = 1 ORDER BY variantValue ASC")
    fun getVariantsByProduct(productId: String): Flow<List<ProductVariantEntity>>

    @Query("SELECT * FROM product_variants WHERE productId = :productId AND isActive = 1 ORDER BY variantValue ASC")
    suspend fun getVariantsByProductSync(productId: String): List<ProductVariantEntity>

    @Query("SELECT * FROM product_variants WHERE id = :variantId")
    suspend fun getVariantById(variantId: String): ProductVariantEntity?

    @Query("SELECT * FROM product_variants WHERE id = :variantId")
    suspend fun getVariantByIdSync(variantId: String): ProductVariantEntity?

    @Query("SELECT * FROM product_variants WHERE barcode = :barcode AND isActive = 1 LIMIT 1")
    suspend fun getVariantByBarcode(barcode: String): ProductVariantEntity?

    @Query("SELECT SUM(stock) FROM product_variants WHERE productId = :productId AND isActive = 1")
    suspend fun getTotalVariantStock(productId: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: ProductVariantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariants(variants: List<ProductVariantEntity>)

    @Update
    suspend fun updateVariant(variant: ProductVariantEntity)

    @Query("UPDATE product_variants SET stock = :stock, updatedAt = :updatedAt WHERE id = :variantId")
    suspend fun updateVariantStock(variantId: String, stock: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM product_variants WHERE productId = :productId")
    suspend fun deleteVariantsByProduct(productId: String)

    @Query("DELETE FROM product_variants WHERE id = :variantId")
    suspend fun deleteVariantById(variantId: String)

    @Delete
    suspend fun deleteVariant(variant: ProductVariantEntity)

    // ==================== MOVIMIENTOS DE STOCK ====================
    @Query("UPDATE product_variants SET syncStatus = :syncStatus, updatedAt = :updatedAt WHERE id = :variantId")
    suspend fun updateVariantSyncStatus(
        variantId: String,
        syncStatus: Int,
        updatedAt: Long = System.currentTimeMillis()
    )
    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC")
    fun getStockMovementsByProduct(productId: String): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC")
    suspend fun getStockMovementsByProductSync(productId: String): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentStockMovements(productId: String, limit: Int = 20): List<StockMovementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovement(movement: StockMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovements(movements: List<StockMovementEntity>)

    // ==================== CATEGORÍAS ====================

    @Query("SELECT * FROM categories WHERE isActive = 1 ORDER BY sortOrder, name")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE parentId IS NULL AND isActive = 1 ORDER BY sortOrder, name")
    fun getMainCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE parentId = :parentId AND isActive = 1 ORDER BY sortOrder, name")
    fun getSubcategories(parentId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    // ==================== PROVEEDORES ====================
    @Query("UPDATE categories SET syncStatus = :syncStatus, updatedAt = :updatedAt WHERE id = :categoryId")
    suspend fun updateCategorySyncStatus(
        categoryId: String,
        syncStatus: Int,
        updatedAt: Long = System.currentTimeMillis()
    )
    @Query("SELECT * FROM suppliers WHERE isActive = 1 ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getSupplierById(id: String): SupplierEntity?

    @Query("""
        SELECT * FROM suppliers 
        WHERE isActive = 1 
        AND (name LIKE '%' || :query || '%' 
             OR contactName LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    suspend fun searchSuppliers(query: String): List<SupplierEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuppliers(suppliers: List<SupplierEntity>)

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Delete
    suspend fun deleteSupplier(supplier: SupplierEntity)

    // ==================== IMÁGENES ====================
    @Query("UPDATE suppliers SET syncStatus = :syncStatus, updatedAt = :updatedAt WHERE id = :supplierId")
    suspend fun updateSupplierSyncStatus(
        supplierId: String,
        syncStatus: Int,
        updatedAt: Long = System.currentTimeMillis()
    )
    @Query("SELECT * FROM product_images WHERE productId = :productId ORDER BY sortOrder")
    fun getImagesByProduct(productId: String): Flow<List<ProductImageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ProductImageEntity)

    @Delete
    suspend fun deleteImage(image: ProductImageEntity)

    // ==================== HISTORIAL DE PRECIOS ====================

    @Query("SELECT * FROM product_price_history WHERE productId = :productId ORDER BY changedAt DESC")
    fun getPriceHistory(productId: String): Flow<List<ProductPriceHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(history: ProductPriceHistoryEntity)
}