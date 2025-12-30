package com.inventario.py.data.local.dao

import androidx.room.*
import com.inventario.py.data.local.entities.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

// ========== Product DAO ==========

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    suspend fun getAllProductsList(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    fun getProductByIdFlow(id: Long): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE barcode = :barcode AND isActive = 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE identifier = :identifier AND isActive = 1")
    suspend fun getProductByIdentifier(identifier: String): ProductEntity?

    @Query("""
        SELECT * FROM products 
        WHERE isActive = 1 AND (
            name LIKE '%' || :query || '%' OR 
            barcode LIKE '%' || :query || '%' OR 
            identifier LIKE '%' || :query || '%' OR
            description LIKE '%' || :query || '%'
        )
        ORDER BY name ASC
    """)
    fun searchProducts(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isActive = 1 AND stock <= minStock AND stock > 0 ORDER BY stock ASC")
    fun getLowStockProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isActive = 1 AND stock <= minStock AND stock > 0")
    suspend fun getLowStockProductsList(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE isActive = 1 AND stock > minStock ORDER BY name ASC")
    fun getInStockProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isActive = 1 AND stock <= 0 ORDER BY name ASC")
    fun getOutOfStockProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE category = :category AND isActive = 1")
    fun getProductsByCategory(category: String): Flow<List<ProductEntity>>

    @Query("SELECT DISTINCT category FROM products WHERE category IS NOT NULL AND isActive = 1")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT * FROM products WHERE syncStatus = :status")
    suspend fun getProductsBySyncStatus(status: SyncStatus): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("UPDATE products SET stock = :newStock, lastModified = :modified, syncStatus = :syncStatus WHERE id = :productId")
    suspend fun updateStock(productId: Long, newStock: Int, modified: Date = Date(), syncStatus: SyncStatus = SyncStatus.PENDING)

    @Query("UPDATE products SET syncStatus = :status WHERE id = :productId")
    suspend fun updateSyncStatus(productId: Long, status: SyncStatus)

    @Query("UPDATE products SET serverId = :serverId, syncStatus = :status WHERE id = :productId")
    suspend fun updateServerIdAndStatus(productId: Long, serverId: Long, status: SyncStatus)

    @Query("UPDATE products SET isActive = 0, lastModified = :modified WHERE id = :productId")
    suspend fun softDeleteProduct(productId: Long, modified: Date = Date())

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1")
    fun getProductCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1")
    suspend fun getProductCountValue(): Int

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1 AND stock <= minStock AND stock > 0")
    suspend fun getLowStockCount(): Int

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1 AND stock > minStock")
    suspend fun getInStockCount(): Int

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1 AND stock <= 0")
    suspend fun getOutOfStockCount(): Int

    @Query("SELECT SUM(stock * price) FROM products WHERE isActive = 1")
    fun getTotalInventoryValue(): Flow<Long?>

    @Query("SELECT SUM(stock * cost) FROM products WHERE isActive = 1")
    fun getTotalInventoryCost(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM products WHERE syncStatus = :status")
    suspend fun getPendingProductsCount(status: SyncStatus): Int
}

// ========== User DAO ==========

@Dao
interface UserDao {

    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY name ASC")
    suspend fun getAllUsersList(): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username AND passwordHash = :passwordHash AND isActive = 1")
    suspend fun authenticate(username: String, passwordHash: String): UserEntity?

    @Query("SELECT * FROM users WHERE isActive = 1")
    fun getActiveUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET lastLogin = :date WHERE id = :userId")
    suspend fun updateLastLogin(userId: Long, date: Date = Date())

    @Query("UPDATE users SET isActive = :isActive WHERE id = :userId")
    suspend fun updateUserActive(userId: Long, isActive: Boolean)

    @Query("UPDATE users SET passwordHash = :passwordHash WHERE id = :userId")
    suspend fun updatePassword(userId: Long, passwordHash: String)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: Long)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}

// ========== Sale DAO ==========

@Dao
interface SaleDao {

    @Query("SELECT * FROM sales ORDER BY date DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales ORDER BY date DESC")
    suspend fun getAllSalesList(): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSaleById(id: Long): SaleEntity?

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItems(saleId: Long): List<SaleItemEntity>

    @Transaction
    suspend fun getSaleWithItems(saleId: Long): SaleWithItems? {
        val sale = getSaleById(saleId) ?: return null
        val items = getSaleItems(saleId)
        return SaleWithItems(sale, items)
    }

    @Query("SELECT * FROM sales WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getSalesByDateRange(startDate: Date, endDate: Date): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    suspend fun getSalesByDateRangeList(startDate: Date, endDate: Date): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE employeeId = :employeeId ORDER BY date DESC")
    fun getSalesByEmployee(employeeId: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE syncStatus = :status")
    suspend fun getSalesBySyncStatus(status: SyncStatus): List<SaleEntity>

    @Query("SELECT SUM(total) FROM sales WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalByDateRange(startDate: Date, endDate: Date): Long?

    @Query("SELECT SUM(profit) FROM sales WHERE date >= :startDate AND date <= :endDate")
    suspend fun getProfitByDateRange(startDate: Date, endDate: Date): Long?

    @Query("SELECT COUNT(*) FROM sales WHERE date >= :startDate AND date <= :endDate")
    suspend fun getSalesCountByDateRange(startDate: Date, endDate: Date): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Transaction
    suspend fun insertSaleWithItems(sale: SaleEntity, items: List<SaleItemEntity>): Long {
        val saleId = insertSale(sale)
        val itemsWithSaleId = items.map { it.copy(saleId = saleId) }
        insertSaleItems(itemsWithSaleId)
        return saleId
    }

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Query("UPDATE sales SET syncStatus = :status WHERE id = :saleId")
    suspend fun updateSyncStatus(saleId: Long, status: SyncStatus)

    @Delete
    suspend fun deleteSale(sale: SaleEntity)

    @Query("DELETE FROM sales WHERE id = :saleId")
    suspend fun deleteSaleById(saleId: Long)

    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    suspend fun deleteSaleItems(saleId: Long)

    @Transaction
    suspend fun deleteSaleWithItems(saleId: Long) {
        deleteSaleItems(saleId)
        deleteSaleById(saleId)
    }

    @Query("DELETE FROM sales")
    suspend fun deleteAllSales()

    @Query("DELETE FROM sale_items")
    suspend fun deleteAllSaleItems()

    @Query("SELECT COUNT(*) FROM sales WHERE syncStatus = :status")
    suspend fun getPendingSalesCount(status: SyncStatus): Int
}

// ========== Cart DAO ==========

@Dao
interface CartDao {

    @Query("SELECT * FROM cart_items ORDER BY id ASC")
    fun getAllCartItems(): Flow<List<CartItemEntity>>

    @Query("SELECT * FROM cart_items ORDER BY id ASC")
    suspend fun getAllCartItemsList(): List<CartItemEntity>

    @Query("SELECT * FROM cart_items WHERE id = :id")
    suspend fun getCartItemById(id: Long): CartItemEntity?

    @Query("SELECT * FROM cart_items WHERE productId = :productId")
    suspend fun getCartItemByProductId(productId: Long): CartItemEntity?

    @Query("SELECT * FROM cart_items WHERE productId = :productId AND selectedType = :type AND selectedCapacity = :capacity AND selectedColor = :color")
    suspend fun getCartItemByProductAndVariant(productId: Long, type: String?, capacity: String?, color: String?): CartItemEntity?

    @Query("SELECT COUNT(*) FROM cart_items")
    fun getCartItemCount(): Flow<Int>

    @Query("SELECT SUM(unitPrice * quantity) FROM cart_items")
    fun getCartTotal(): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItem(item: CartItemEntity): Long

    @Update
    suspend fun updateCartItem(item: CartItemEntity)

    @Query("UPDATE cart_items SET quantity = :quantity WHERE id = :itemId")
    suspend fun updateQuantity(itemId: Long, quantity: Int)

    @Delete
    suspend fun deleteCartItem(item: CartItemEntity)

    @Query("DELETE FROM cart_items WHERE id = :itemId")
    suspend fun deleteCartItemById(itemId: Long)

    @Query("DELETE FROM cart_items")
    suspend fun clearCart()
}

// ========== Product History DAO ==========

@Dao
interface ProductHistoryDao {

    @Query("SELECT * FROM product_history WHERE productId = :productId ORDER BY date DESC")
    fun getProductHistory(productId: Long): Flow<List<ProductHistoryEntity>>

    @Query("SELECT * FROM product_history WHERE productId = :productId ORDER BY date DESC")
    suspend fun getProductHistoryList(productId: Long): List<ProductHistoryEntity>

    @Query("SELECT * FROM product_history ORDER BY date DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): Flow<List<ProductHistoryEntity>>

    @Query("SELECT * FROM product_history WHERE userId = :userId ORDER BY date DESC")
    fun getHistoryByUser(userId: Long): Flow<List<ProductHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ProductHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistories(histories: List<ProductHistoryEntity>)

    @Delete
    suspend fun deleteHistory(history: ProductHistoryEntity)

    @Query("DELETE FROM product_history WHERE productId = :productId")
    suspend fun deleteHistoryByProduct(productId: Long)

    @Query("DELETE FROM product_history")
    suspend fun deleteAllHistory()
}
