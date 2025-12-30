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
    
    @Query("SELECT * FROM products WHERE totalStock = 0 AND isActive = 1")
    fun getOutOfStockProducts(): Flow<List<ProductEntity>>
    
    @Query("SELECT * FROM products WHERE syncStatus != 0")
    suspend fun getUnsyncedProducts(): List<ProductEntity>
    
    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1")
    fun getProductCount(): Flow<Int>
    
    @Query("SELECT SUM(totalStock * salePrice) FROM products WHERE isActive = 1")
    fun getTotalInventoryValue(): Flow<Long?>
    
    @Query("SELECT SUM(totalStock * purchasePrice) FROM products WHERE isActive = 1")
    fun getTotalInventoryCost(): Flow<Long?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)
    
    @Update
    suspend fun updateProduct(product: ProductEntity)
    
    @Query("UPDATE products SET totalStock = :stock, updatedAt = :updatedAt, syncStatus = :syncStatus WHERE id = :productId")
    suspend fun updateStock(productId: String, stock: Int, updatedAt: Long = System.currentTimeMillis(), syncStatus: Int = 1)
    
    @Query("UPDATE products SET syncStatus = :syncStatus WHERE id = :productId")
    suspend fun updateSyncStatus(productId: String, syncStatus: Int)
    
    @Query("UPDATE products SET isActive = 0, updatedAt = :updatedAt WHERE id = :productId")
    suspend fun softDeleteProduct(productId: String, updatedAt: Long = System.currentTimeMillis())
    
    @Delete
    suspend fun deleteProduct(product: ProductEntity)
    
    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
    
    // ==================== VARIANTES ====================
    
    @Query("SELECT * FROM product_variants WHERE productId = :productId AND isActive = 1 ORDER BY variantType, variantValue")
    fun getVariantsByProduct(productId: String): Flow<List<ProductVariantEntity>>
    
    @Query("SELECT * FROM product_variants WHERE productId = :productId AND isActive = 1")
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
    
    @Query("SELECT * FROM suppliers WHERE isActive = 1 ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<SupplierEntity>>
    
    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getSupplierById(id: String): SupplierEntity?
    
    @Query("SELECT * FROM suppliers WHERE name LIKE '%' || :query || '%' AND isActive = 1")
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
    
    @Query("SELECT * FROM product_images WHERE productId = :productId ORDER BY isPrimary DESC, sortOrder")
    fun getImagesByProduct(productId: String): Flow<List<ProductImageEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ProductImageEntity)
    
    @Delete
    suspend fun deleteImage(image: ProductImageEntity)
    
    @Query("DELETE FROM product_images WHERE productId = :productId")
    suspend fun deleteImagesByProduct(productId: String)
    
    // ==================== HISTORIAL DE PRECIOS ====================
    
    @Query("SELECT * FROM product_price_history WHERE productId = :productId ORDER BY changedAt DESC")
    fun getPriceHistory(productId: String): Flow<List<ProductPriceHistoryEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceHistory(priceHistory: ProductPriceHistoryEntity)
}
