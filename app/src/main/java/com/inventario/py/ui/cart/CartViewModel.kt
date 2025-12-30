package com.inventario.py.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.CartItemWithProduct
import com.inventario.py.data.repository.CartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository
) : ViewModel() {

    val cartItems: StateFlow<List<CartItemWithProduct>> = cartRepository
        .getCartItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _cartTotal = MutableStateFlow(CartTotal())
    val cartTotal: StateFlow<CartTotal> = _cartTotal.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var lastDeletedItem: CartItemWithProduct? = null

    init {
        // Calculate totals whenever cart items change
        viewModelScope.launch {
            cartItems.collect { items ->
                calculateTotals(items)
            }
        }
    }

    private fun calculateTotals(items: List<CartItemWithProduct>) {
        val subtotal = items.sumOf { item ->
            val price = item.variant?.priceModifier ?: item.product.salePrice
            price * item.cartItem.quantity
        }
        
        val itemCount = items.sumOf { it.cartItem.quantity }
        
        // For now, no discount - can be implemented later
        val discount = 0.0
        
        _cartTotal.value = CartTotal(
            subtotal = subtotal,
            discount = discount,
            total = subtotal - discount,
            itemCount = itemCount
        )
    }

    fun updateQuantity(item: CartItemWithProduct, newQuantity: Int) {
        viewModelScope.launch {
            if (newQuantity <= 0) {
                removeFromCart(item)
            } else {
                // Check stock availability
                val availableStock = item.variant?.currentStock ?: item.product.currentStock
                
                if (newQuantity > availableStock) {
                    _message.value = "Stock insuficiente. Disponible: $availableStock"
                    return@launch
                }
                
                try {
                    cartRepository.updateQuantity(item.cartItem.id, newQuantity)
                } catch (e: Exception) {
                    _message.value = "Error al actualizar cantidad"
                }
            }
        }
    }

    fun removeFromCart(item: CartItemWithProduct) {
        viewModelScope.launch {
            try {
                lastDeletedItem = item
                cartRepository.removeFromCart(item.cartItem.id)
            } catch (e: Exception) {
                _message.value = "Error al eliminar producto"
            }
        }
    }

    fun restoreCartItem(item: CartItemWithProduct) {
        viewModelScope.launch {
            try {
                cartRepository.addToCart(
                    productId = item.cartItem.productId,
                    variantId = item.cartItem.variantId,
                    quantity = item.cartItem.quantity
                )
            } catch (e: Exception) {
                _message.value = "Error al restaurar producto"
            }
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            try {
                cartRepository.clearCart()
                _message.value = "Carrito vaciado"
            } catch (e: Exception) {
                _message.value = "Error al vaciar carrito"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

data class CartTotal(
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val total: Double = 0.0,
    val itemCount: Int = 0
)
