package com.inventario.py.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.ProductEntity
import com.inventario.py.data.local.entity.ProductVariantEntity
import com.inventario.py.data.local.entity.ProductWithVariants
import com.inventario.py.data.local.entity.StockMovementEntity
import com.inventario.py.data.repository.CartRepository
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SalesRepository
import com.inventario.py.utils.Generators
import com.inventario.py.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class ProductDetailState(
    val isLoading: Boolean = true,
    val product: ProductWithVariants? = null,
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
    private val salesRepository: CartRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductDetailState())
    val uiState: StateFlow<ProductDetailState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProductDetailEvent>()
    val events: SharedFlow<ProductDetailEvent> = _events.asSharedFlow()

    private var currentProductId: String? = null

    // Estados públicos para observar desde el Fragment
    private val _productState = MutableStateFlow(ProductDetailState())
    val productState: StateFlow<ProductDetailState> = _productState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

    fun loadProduct(productId: String) {
        currentProductId = productId
        viewModelScope.launch {
            _isLoading.value = true
            _uiState.value = _uiState.value.copy(isLoading = true)
            _productState.value = _productState.value.copy(isLoading = true)

            try {
                val product = productRepository.getProductById(productId)
                if (product != null) {
                    val variants = productRepository.getVariantsByProductSync(productId)
                    val movements = productRepository.getStockMovementsForProductSync(productId)

                    val productWithVariants = ProductWithVariants(
                        product = product,
                        variants = variants
                    )

                    val state = ProductDetailState(
                        isLoading = false,
                        product = productWithVariants,
                        variants = variants,
                        stockMovements = movements
                    )

                    _uiState.value = state
                    _productState.value = state
                } else {
                    val errorState = ProductDetailState(
                        isLoading = false,
                        error = "Producto no encontrado"
                    )
                    _uiState.value = errorState
                    _productState.value = errorState
                }
            } catch (e: Exception) {
                val errorState = ProductDetailState(
                    isLoading = false,
                    error = e.message ?: "Error al cargar producto"
                )
                _uiState.value = errorState
                _productState.value = errorState
            }

            _isLoading.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun addToCart(variant: ProductVariantEntity? = null) {
        viewModelScope.launch {
            val product = _uiState.value.product?.product ?: return@launch
            try {
                salesRepository.addToCart(product, variant, 1)
                _events.emit(ProductDetailEvent.AddedToCart(product.name))
                _message.value = "Producto agregado al carrito"
            } catch (e: Exception) {
                _events.emit(ProductDetailEvent.Error("Error al agregar al carrito: ${e.message}"))
                _message.value = "Error al agregar al carrito"
            }
        }
    }

    fun deleteProduct() {
        viewModelScope.launch {
            val productId = currentProductId ?: return@launch
            try {
                productRepository.deleteProduct(productId)
                _events.emit(ProductDetailEvent.ProductDeleted)
                _deleteSuccess.value = true
            } catch (e: Exception) {
                _events.emit(ProductDetailEvent.Error("Error al eliminar: ${e.message}"))
                _message.value = "Error al eliminar producto"
            }
        }
    }

    /**
     * Ajusta el stock del producto
     */
    fun adjustStock(quantity: Int, reason: String, isAddition: Boolean) {
        viewModelScope.launch {
            val product = _uiState.value.product?.product ?: return@launch
            val productId = product.id
            val userId = sessionManager.getUserId() ?: return@launch

            try {
                val newStock = if (isAddition) {
                    product.totalStock + quantity
                } else {
                    (product.totalStock - quantity).coerceAtLeast(0)
                }

                // Actualizar stock
                productRepository.updateStock(productId, newStock)

                // Registrar movimiento
                val movement = StockMovementEntity(
                    id = Generators.generateId(),
                    productId = productId,
                    variantId = null,
                    movementType = if (isAddition) StockMovementEntity.TYPE_IN else StockMovementEntity.TYPE_OUT,
                    quantity = quantity,
                    previousStock = product.totalStock,
                    newStock = newStock,
                    reason = reason,
                    referenceId = null,
                    referenceType = StockMovementEntity.REF_ADJUSTMENT,
                    createdBy = userId
                )
                productRepository.saveStockMovement(movement)

                // Recargar producto
                loadProduct(productId)
                _message.value = "Stock actualizado correctamente"
            } catch (e: Exception) {
                _message.value = "Error al ajustar stock: ${e.message}"
            }
        }
    }

    /**
     * Agrega una variante al producto
     */
    fun addVariant(
        variantType: String,
        variantLabel: String,
        variantValue: String,
        stock: Int,
        additionalPrice: Long,
        barcode: String?
    ) {
        viewModelScope.launch {
            val productId = currentProductId ?: return@launch

            try {
                productRepository.createVariant(
                    productId = productId,
                    variantType = variantType,
                    variantLabel = variantLabel,
                    variantValue = variantValue,
                    stock = stock,
                    additionalPrice = additionalPrice,
                    barcode = barcode
                )

                // Recargar producto
                loadProduct(productId)
                _message.value = "Variante agregada correctamente"
            } catch (e: Exception) {
                _message.value = "Error al agregar variante: ${e.message}"
            }
        }
    }

    /**
     * Actualiza una variante existente
     */
    fun updateVariant(
        variantId: String,
        variantName: String,
        priceModifier: Long,
        currentStock: Int
    ) {
        viewModelScope.launch {
            try {
                val variant = productRepository.getVariantById(variantId)
                if (variant != null) {
                    val updatedVariant = variant.copy(
                        variantValue = variantName,
                        additionalPrice = priceModifier,
                        stock = currentStock,
                        updatedAt = System.currentTimeMillis()
                    )
                    productRepository.saveVariant(updatedVariant)

                    // Recargar producto
                    currentProductId?.let { loadProduct(it) }
                    _message.value = "Variante actualizada correctamente"
                }
            } catch (e: Exception) {
                _message.value = "Error al actualizar variante: ${e.message}"
            }
        }
    }

    /**
     * Elimina una variante por ID
     */
    fun deleteVariant(variantId: String) {
        viewModelScope.launch {
            try {
                val variant = productRepository.getVariantById(variantId)
                if (variant != null) {
                    productRepository.deleteVariant(variant)

                    // Recargar producto
                    currentProductId?.let { loadProduct(it) }
                    _message.value = "Variante eliminada correctamente"
                }
            } catch (e: Exception) {
                _message.value = "Error al eliminar variante: ${e.message}"
            }
        }
    }

    // ==================== SOBRECARGAS PARA COMPATIBILIDAD ====================

    /**
     * Sobrecarga de addVariant para ProductDetailFragment
     * Usado como: addVariant(name, sku, price, stock)
     */
    fun addVariant(name: String, sku: String?, price: Double?, stock: Int) {
        addVariant(
            variantType = "CUSTOM",
            variantLabel = "Variante",
            variantValue = name,
            stock = stock,
            additionalPrice = price?.toLong() ?: 0L,
            barcode = sku
        )
    }

    /**
     * Sobrecarga de updateVariant que recibe un ProductVariant
     * Usado como: updateVariant(variant.copy(...))
     */
    fun updateVariant(variant: com.inventario.py.data.local.entity.ProductVariant) {
        updateVariant(
            variantId = variant.id,
            variantName = variant.name,
            priceModifier = variant.additionalPrice,
            currentStock = variant.stock
        )
    }

    /**
     * Sobrecarga de deleteVariant que recibe un ProductVariant
     * Usado como: deleteVariant(variant)
     */
    fun deleteVariant(variant: com.inventario.py.data.local.entity.ProductVariant) {
        deleteVariant(variant.id)
    }

    /**
     * Sobrecarga de adjustStock con 1 parámetro (solo cantidad)
     * Asume que es una adición si es positivo, sustracción si negativo
     */
    fun adjustStock(quantity: Int) {
        val isAddition = quantity >= 0
        val absQuantity = kotlin.math.abs(quantity)
        adjustStock(absQuantity, "Ajuste manual", isAddition)
    }
}