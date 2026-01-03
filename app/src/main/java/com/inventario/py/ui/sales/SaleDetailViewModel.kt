package com.inventario.py.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.*
import com.inventario.py.data.repository.CartRepository
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SalesRepository
import com.inventario.py.utils.Generators
import com.inventario.py.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SaleDetailUiState(
    val isLoading: Boolean = true,
    val sale: SaleEntity? = null,
    val items: List<SaleItem> = emptyList(),
    val canCancel: Boolean = false,
    val isOwner: Boolean = false
)

sealed class SaleDetailEvent {
    object SaleCancelled : SaleDetailEvent()
    object SaleDuplicated : SaleDetailEvent()
    object PrintReceipt : SaleDetailEvent()
    data class Error(val message: String) : SaleDetailEvent()
}

@HiltViewModel
class SaleDetailViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
    private val cartRepository: CartRepository,
    private val productRepository: ProductRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SaleDetailUiState())
    val uiState: StateFlow<SaleDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SaleDetailEvent>()
    val events: SharedFlow<SaleDetailEvent> = _events.asSharedFlow()

    private var currentSaleId: String? = null

    init {
        checkUserRole()
    }

    private fun checkUserRole() {
        val isOwner = sessionManager.isOwner()
        _uiState.value = _uiState.value.copy(isOwner = isOwner)
    }

    fun loadSale(saleId: String) {
        currentSaleId = saleId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                val saleWithItems = salesRepository.getSaleWithItems(saleId)

                if (saleWithItems != null) {
                    val items = saleWithItems.items.map { it.toSaleItem() }
                    val canCancel = saleWithItems.sale.status == SaleEntity.STATUS_COMPLETED

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        sale = saleWithItems.sale,
                        items = items,
                        canCancel = canCancel
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(SaleDetailEvent.Error("Venta no encontrada"))
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.emit(SaleDetailEvent.Error(e.message ?: "Error al cargar la venta"))
            }
        }
    }

    fun cancelSale(reason: String) {
        val saleId = currentSaleId ?: return

        viewModelScope.launch {
            try {
                val success = salesRepository.cancelSale(saleId, reason)

                if (success) {
                    // Recargar la venta para mostrar estado actualizado
                    loadSale(saleId)

                    // Restaurar el stock de los productos
                    restoreStock()

                    _events.emit(SaleDetailEvent.SaleCancelled)
                } else {
                    _events.emit(SaleDetailEvent.Error("No se pudo cancelar la venta"))
                }

            } catch (e: Exception) {
                _events.emit(SaleDetailEvent.Error(e.message ?: "Error al cancelar"))
            }
        }
    }

    private suspend fun restoreStock() {
        val items = _uiState.value.items
        val userId = sessionManager.getUserId() ?: return

        for (item in items) {
            val product = productRepository.getProductById(item.productId) ?: continue
            val newStock = product.totalStock + item.quantity

            productRepository.updateStock(item.productId, newStock)

            // Registrar movimiento de stock
            val movement = StockMovementEntity(
                id = Generators.generateId(),
                productId = item.productId,
                variantId = null,
                movementType = MovementType.CANCELLATION.name,
                quantity = item.quantity,
                previousStock = product.totalStock,
                newStock = newStock,
                reason = "Cancelación de venta",
                referenceType = "SALE_CANCELLATION",
                referenceId = currentSaleId,
                createdBy = userId,
            )
            productRepository.saveStockMovement(movement)
        }
    }

    fun duplicateSale() {
        viewModelScope.launch {
            try {
                val items = _uiState.value.items

                // Agregar cada item al carrito
                for (item in items) {
                    val product = productRepository.getProductById(item.productId)
                    if (product != null) {
                        cartRepository.addToCart(
                            productId = product.id,
                            variantId = null, // TODO: Guardar variantId en SaleItem si es necesario
                            quantity = item.quantity
                        )
                    }
                }

                _events.emit(SaleDetailEvent.SaleDuplicated)

            } catch (e: Exception) {
                _events.emit(SaleDetailEvent.Error(e.message ?: "Error al duplicar venta"))
            }
        }
    }

    fun printReceipt() {
        viewModelScope.launch {
            // TODO: Implementar impresión real
            _events.emit(SaleDetailEvent.PrintReceipt)
        }
    }
}

// Data class para items de venta (UI)
data class SaleItem(
    val id: String,
    val productId: String,
    val productName: String,
    val variantInfo: String?,
    val quantity: Int,
    val unitPrice: Long,
    val discount: Long,
    val subtotal: Long
)

// Extensión para convertir SaleItemEntity a SaleItem
fun SaleItemEntity.toSaleItem() = SaleItem(
    id = this.id,
    productId = this.productId,
    productName = this.productName,
    variantInfo = this.variantDescription,
    quantity = this.quantity,
    unitPrice = this.unitPrice,
    discount = this.discount,
    subtotal = this.subtotal
)

