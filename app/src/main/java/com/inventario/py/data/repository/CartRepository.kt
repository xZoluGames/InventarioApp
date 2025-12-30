package com.inventario.py.data.repository

import com.inventario.py.data.local.dao.CartDao
import com.inventario.py.data.local.dao.ProductDao
import com.inventario.py.data.local.entity.CartItemEntity
import com.inventario.py.data.local.entity.CartItemWithProduct
import com.inventario.py.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CartRepository @Inject constructor(
    private val cartDao: CartDao,
    private val productDao: ProductDao,
    private val sessionManager: SessionManager
) {
    
    private val currentUserId: String
        get() = sessionManager.getUserId() ?: "default"
    
    /**
     * Get all cart items for current user with product info
     */
    fun getCartItems(): Flow<List<CartItemWithProduct>> {
        return cartDao.getCartItemsForUser(currentUserId).map { cartItems ->
            cartItems.mapNotNull { cartItem ->
                val product = productDao.getProductByIdSync(cartItem.productId)
                if (product != null) {
                    val variant = cartItem.variantId?.let { variantId ->
                        productDao.getVariantByIdSync(variantId)
                    }
                    CartItemWithProduct(
                        cartItem = cartItem,
                        product = product,
                        variant = variant
                    )
                } else {
                    null
                }
            }
        }
    }
    
    /**
     * Add product to cart
     */
    suspend fun addToCart(
        productId: String,
        variantId: String? = null,
        quantity: Int = 1
    ) = withContext(Dispatchers.IO) {
        // Check if item already exists
        val existingItem = cartDao.getCartItem(currentUserId, productId, variantId)
        
        if (existingItem != null) {
            // Update quantity
            cartDao.updateQuantity(existingItem.id, existingItem.quantity + quantity)
        } else {
            // Get product price
            val product = productDao.getProductByIdSync(productId)
                ?: throw IllegalArgumentException("Product not found")
            
            val variant = variantId?.let { productDao.getVariantByIdSync(it) }
            val unitPrice = variant?.priceModifier ?: product.salePrice
            val subtotal = unitPrice * quantity
            
            // Create new cart item using correct entity fields
            val cartItem = CartItemEntity(
                id = UUID.randomUUID().toString(),
                productId = productId,
                productName = product.name,
                variantId = variantId,
                variantDescription = variant?.name,
                quantity = quantity,
                unitPrice = unitPrice,
                discount = 0,
                subtotal = subtotal,
                imageUrl = product.imageUrl,
                addedAt = System.currentTimeMillis(),
                addedBy = currentUserId
            )
            cartDao.insert(cartItem)
        }
    }
    
    /**
     * Update item quantity
     */
    suspend fun updateQuantity(cartItemId: String, newQuantity: Int) = withContext(Dispatchers.IO) {
        if (newQuantity <= 0) {
            cartDao.delete(cartItemId)
        } else {
            cartDao.updateQuantity(cartItemId, newQuantity)
        }
    }
    
    /**
     * Remove item from cart
     */
    suspend fun removeFromCart(cartItemId: String) = withContext(Dispatchers.IO) {
        cartDao.delete(cartItemId)
    }
    
    /**
     * Clear entire cart
     */
    suspend fun clearCart() = withContext(Dispatchers.IO) {
        cartDao.clearCartForUser(currentUserId)
    }
    
    /**
     * Get cart item count
     */
    fun getCartItemCount(): Flow<Int> {
        return cartDao.getCartItemCount(currentUserId)
    }
    
    /**
     * Get cart total
     */
    fun getCartTotal(): Flow<Long?> {
        return cartDao.getCartTotal(currentUserId)
    }
}
