package com.inventario.py.ui.checkout

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

data class CheckoutUiState(
    val items: List<CartItemWithProduct> = emptyList(),
    val subtotal: Long = 0L,
    val discount: Long = 0L,
    val total: Long = 0L,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val amountReceived: Long = 0L,
    val changeAmount: Long = 0L,
    val isProcessing: Boolean = false,
    val customerName: String = "",
    val notes: String = ""
)

sealed class CheckoutEvent {
    data class SaleCompleted(val saleId: String, val saleNumber: String, val total: Long) : CheckoutEvent()
    data class Error(val message: String) : CheckoutEvent()
    object PrintReceipt : CheckoutEvent()
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val salesRepository: SalesRepository,
    private val productRepository: ProductRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CheckoutEvent>()
    val events: SharedFlow<CheckoutEvent> = _events.asSharedFlow()

    private var lastCompletedSaleId: String? = null

    init {
        loadCartItems()
    }

    private fun loadCartItems() {
        viewModelScope.launch {
            cartRepository.getCartItems().collectLatest { items ->
                val subtotal = items.sumOf { item ->
                    val price = item.variant?.additionalPrice?.let { 
                        item.product.salePrice + it 
                    } ?: item.product.salePrice
                    price * item.cartItem.quantity
                }

                _uiState.value = _uiState.value.copy(
                    items = items,
                    subtotal = subtotal,
                    total = subtotal - _uiState.value.discount
                )
            }
        }
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _uiState.value = _uiState.value.copy(
            paymentMethod = method,
            amountReceived = 0L,
            changeAmount = 0L
        )
    }

    fun setAmountReceived(amount: Long) {
        val total = _uiState.value.total
        val change = if (amount > total) amount - total else 0L

        _uiState.value = _uiState.value.copy(
            amountReceived = amount,
            changeAmount = change
        )
    }

    fun setDiscount(discount: Long) {
        val subtotal = _uiState.value.subtotal
        val newTotal = subtotal - discount

        _uiState.value = _uiState.value.copy(
            discount = discount,
            total = newTotal
        )
    }

    fun completeSale(customerName: String, notes: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)

            try {
                val state = _uiState.value
                val userId = sessionManager.getUserId() ?: throw Exception("Usuario no autenticado")
                val userName = sessionManager.getUserName() ?: "Vendedor"

                // Generar número de venta
                val saleNumber = salesRepository.generateSaleNumber()
                val saleId = Generators.generateId()

                // Crear la venta
                val sale = SaleEntity(
                    id = saleId,
                    saleNumber = saleNumber,
                    customerName = customerName.ifEmpty { null },
                    subtotal = state.subtotal,
                    totalDiscount = state.discount,
                    total = state.total,
                    paymentMethod = state.paymentMethod.name,
                    amountPaid = if (state.paymentMethod == PaymentMethod.CASH) state.amountReceived else state.total,
                    changeAmount = state.changeAmount,
                    status = SaleEntity.STATUS_COMPLETED,
                    notes = notes.ifEmpty { null },
                    soldBy = userId,
                    soldByName = userName
                )

                // Crear los items de la venta
                val saleItems = state.items.map { cartItem ->
                    val unitPrice = cartItem.variant?.additionalPrice?.let {
                        cartItem.product.salePrice + it
                    } ?: cartItem.product.salePrice

                    SaleItemEntity(
                        id = Generators.generateId(),
                        saleId = saleId,
                        productId = cartItem.product.id,
                        productName = cartItem.product.name,
                        productIdentifier = cartItem.product.identifier,
                        variantId = cartItem.variant?.id,
                        variantDescription = cartItem.cartItem.variantDescription,
                        quantity = cartItem.cartItem.quantity,
                        unitPrice = unitPrice,
                        discount = 0L,
                        subtotal = unitPrice * cartItem.cartItem.quantity,
                        costPrice = cartItem.product.purchasePrice
                    )
                }

                // Guardar venta
                salesRepository.createSale(sale, saleItems)

                // Actualizar stock de productos
                for (item in state.items) {
                    val newStock = item.product.totalStock - item.cartItem.quantity
                    productRepository.updateStock(item.product.id, newStock.coerceAtLeast(0))

                    // Registrar movimiento de stock
                    val movement = StockMovementEntity(
                        id = Generators.generateId(),
                        productId = item.product.id,
                        variantId = item.variant?.id,
                        movementType = MovementType.SALE.name,
                        quantity = -item.cartItem.quantity,
                        previousStock = item.product.totalStock,
                        newStock = newStock.coerceAtLeast(0),
                        reason = "Venta #$saleNumber",
                        referenceType = "SALE",
                        referenceId = saleId,
                        performedBy = userId
                    )
                    productRepository.saveStockMovement(movement)
                }

                // Limpiar carrito
                cartRepository.clearCart()

                lastCompletedSaleId = saleId

                _uiState.value = _uiState.value.copy(isProcessing = false)
                _events.emit(CheckoutEvent.SaleCompleted(saleId, saleNumber, state.total))

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isProcessing = false)
                _events.emit(CheckoutEvent.Error(e.message ?: "Error al procesar la venta"))
            }
        }
    }

    fun printReceipt() {
        viewModelScope.launch {
            lastCompletedSaleId?.let {
                _events.emit(CheckoutEvent.PrintReceipt)
            }
        }
    }
}
