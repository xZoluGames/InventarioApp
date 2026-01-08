package com.inventario.py.data.local.dao

import androidx.room.*
import com.inventario.py.data.local.entity.CartItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {
    
    @Query("SELECT * FROM cart_items WHERE addedBy = :userId ORDER BY addedAt DESC")
    fun getCartItemsForUser(userId: String): Flow<List<CartItemEntity>>
    
    @Query("SELECT * FROM cart_items WHERE addedBy = :userId AND productId = :productId AND (variantId = :variantId OR (variantId IS NULL AND :variantId IS NULL))")
    suspend fun getCartItem(userId: String, productId: String, variantId: String?): CartItemEntity?

    @Query("SELECT * FROM cart_items WHERE addedBy = :userId ORDER BY addedAt DESC")
    suspend fun getCartItemsForUserSync(userId: String): List<CartItemEntity>

    @Query("SELECT * FROM cart_items WHERE id = :id")
    suspend fun getCartItemById(id: String): CartItemEntity?
    
    @Query("SELECT COUNT(*) FROM cart_items WHERE addedBy = :userId")
    fun getCartItemCount(userId: String): Flow<Int>
    
    @Query("SELECT SUM(quantity * unitPrice) FROM cart_items WHERE addedBy = :userId")
    fun getCartTotal(userId: String): Flow<Long?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: CartItemEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CartItemEntity>)
    
    @Query("UPDATE cart_items SET quantity = :quantity WHERE id = :id")
    suspend fun updateQuantity(id: String, quantity: Int)
    
    @Query("DELETE FROM cart_items WHERE id = :id")
    suspend fun delete(id: String)
    
    @Query("DELETE FROM cart_items WHERE addedBy = :userId")
    suspend fun clearCartForUser(userId: String)
    
    @Query("DELETE FROM cart_items")
    suspend fun clearAll()
}
