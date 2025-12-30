package com.inventario.py.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.ProductVariant
import com.inventario.py.data.local.entity.ProductWithVariants
import com.inventario.py.data.local.entity.StockMovement
import com.inventario.py.data.repository.CartRepository
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.StockMovementRepository
import com.inventario.py.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val stockMovementRepository: StockMovementRepository,
    private val cartRepository: CartRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _productState = MutableStateFlow(ProductDetailState())
    val productState: StateFlow<ProductDetailState> = _productState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    private var currentProductId: Long = 0

    fun loadProduct(productId: Long) {
        currentProductId = productId
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // Combine product and stock movements
                combine(
                    productRepository.getProductById(productId),
                    stockMovementRepository.getMovementsForProduct(productId)
                ) { product, movements ->
                    ProductDetailState(
                        product = product,
                        stockMovements = movements
                    )
                }.collect { state ->
                    _productState.value = state
                }
            } catch (e: Exception) {
                _message.value = "Error al cargar el producto"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addToCart() {
        viewModelScope.launch {
            val product = _productState.value.product ?: return@launch
            
            try {
                cartRepository.addToCart(
                    productId = product.product.id,
                    variantId = null,
                    quantity = 1
                )
                _message.value = "Producto agregado al carrito"
            } catch (e: Exception) {
                _message.value = "Error al agregar al carrito"
            }
        }
    }

    fun adjustStock(quantity: Int, reason: String) {
        viewModelScope.launch {
            val product = _productState.value.product ?: return@launch
            val userId = sessionManager.getUserId() ?: return@launch
            
            try {
                val movementType = if (quantity > 0) "ADJUSTMENT_IN" else "ADJUSTMENT_OUT"
                
                val movement = StockMovement(
                    productId = product.product.id,
                    variantId = null,
                    movementType = movementType,
                    quantity = kotlin.math.abs(quantity),
                    reason = reason.ifEmpty { "Ajuste manual" },
                    createdBy = userId,
                    createdAt = Date()
                )
                
                stockMovementRepository.addMovement(movement)
                
                // Update product stock
                val newStock = product.product.currentStock + quantity
                productRepository.updateStock(product.product.id, newStock)
                
                _message.value = "Stock actualizado"
            } catch (e: Exception) {
                _message.value = "Error al ajustar stock"
            }
        }
    }

    fun addVariant(name: String, sku: String, price: Double?, stock: Int) {
        viewModelScope.launch {
            val product = _productState.value.product ?: return@launch
            
            try {
                val variant = ProductVariant(
                    productId = product.product.id,
                    variantName = name,
                    variantValue = name,
                    sku = sku,
                    barcode = null,
                    priceModifier = price,
                    currentStock = stock,
                    imageUrl = null
                )
                
                productRepository.addVariant(variant)
                _message.value = "Variante agregada"
            } catch (e: Exception) {
                _message.value = "Error al agregar variante"
            }
        }
    }

    fun updateVariant(variant: ProductVariant) {
        viewModelScope.launch {
            try {
                productRepository.updateVariant(variant)
                _message.value = "Variante actualizada"
            } catch (e: Exception) {
                _message.value = "Error al actualizar variante"
            }
        }
    }

    fun deleteVariant(variant: ProductVariant) {
        viewModelScope.launch {
            try {
                productRepository.deleteVariant(variant)
                _message.value = "Variante eliminada"
            } catch (e: Exception) {
                _message.value = "Error al eliminar variante"
            }
        }
    }

    fun deleteProduct() {
        viewModelScope.launch {
            val product = _productState.value.product ?: return@launch
            
            try {
                productRepository.deleteProduct(product.product)
                _message.value = "Producto eliminado"
                _deleteSuccess.value = true
            } catch (e: Exception) {
                _message.value = "Error al eliminar producto"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

data class ProductDetailState(
    val product: ProductWithVariants? = null,
    val stockMovements: List<StockMovement> = emptyList()
)
