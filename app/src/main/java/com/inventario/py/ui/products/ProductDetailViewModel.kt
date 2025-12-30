package com.inventario.py.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.ProductEntity
import com.inventario.py.data.local.entity.ProductVariantEntity
import com.inventario.py.data.local.entity.StockMovementEntity
import com.inventario.py.data.repository.CartRepository
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SalesRepository
import com.inventario.py.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class ProductDetailState(
    val isLoading: Boolean = true,
    val product: ProductEntity? = null,
    val variants: List<ProductVariantEntity> = emptyList(),
    val stockMovements: List<StockMovementEntity> = emptyList(),
    val error: String? = null
)

sealed class ProductDetailEvent {
    data class AddedToCart(val productName: String) : ProductDetailEvent()
    data class Error(val message: String) : ProductDetailEvent()
    object ProductDeleted : ProductDetailEvent()
}

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val salesRepository: SalesRepository,
    private val cartRepository: CartRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductDetailState())
    val uiState: StateFlow<ProductDetailState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProductDetailEvent>()
    val events: SharedFlow<ProductDetailEvent> = _events.asSharedFlow()

    private var currentProductId: String? = null

    fun loadProduct(productId: String) {
        currentProductId = productId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val product = productRepository.getProductById(productId)
                if (product != null) {
                    val variants = productRepository.getVariantsByProductId(productId)
                    val movements = productRepository.getStockMovementsForProduct(productId)
                    
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        product = product,
                        variants = variants,
                        stockMovements = movements
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Producto no encontrado"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Error al cargar el producto"
                )
            }
        }
    }

    fun addToCart(variant: ProductVariantEntity? = null, quantity: Int = 1) {
        viewModelScope.launch {
            try {
                val product = _uiState.value.product ?: return@launch
                
                cartRepository.addToCart(
                    productId = product.id,
                    variantId = variant?.id,
                    quantity = quantity
                )
                
                _events.emit(ProductDetailEvent.AddedToCart(product.name))
            } catch (e: Exception) {
                _events.emit(ProductDetailEvent.Error(e.message ?: "Error al agregar al carrito"))
            }
        }
    }

    fun deleteProduct() {
        viewModelScope.launch {
            try {
                currentProductId?.let { id ->
                    productRepository.deleteProduct(id)
                    _events.emit(ProductDetailEvent.ProductDeleted)
                }
            } catch (e: Exception) {
                _events.emit(ProductDetailEvent.Error(e.message ?: "Error al eliminar el producto"))
            }
        }
    }

    fun updateStock(newStock: Int, reason: String? = null) {
        viewModelScope.launch {
            try {
                currentProductId?.let { id ->
                    productRepository.updateProductStock(id, newStock, reason)
                    loadProduct(id) // Reload product
                }
            } catch (e: Exception) {
                _events.emit(ProductDetailEvent.Error(e.message ?: "Error al actualizar el stock"))
            }
        }
    }

    fun refreshProduct() {
        currentProductId?.let { loadProduct(it) }
    }
}
