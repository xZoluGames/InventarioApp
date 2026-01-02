package com.inventario.py.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.*
import com.inventario.py.data.repository.AuthRepository
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SalesRepository
import com.inventario.py.utils.Generators
import com.inventario.py.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

// Estado del carrito
data class CartItem(
    val product: ProductEntity,
    val variant: ProductVariantEntity? = null,
    val quantity: Int = 1,
    val unitPrice: Long,
    val subtotal: Long = unitPrice * quantity
)

data class CartUiState(
    val items: List<CartItem> = emptyList(),
    val subtotal: Long = 0L,
    val discount: Long = 0L,
    val total: Long = 0L,
    val itemCount: Int = 0,
    val customerName: String = "",
    val customerPhone: String = "",
    val selectedPaymentMethod: PaymentMethod = PaymentMethod.CASH,
    val isProcessing: Boolean = false,
    val lastScannedBarcode: String? = null
)

sealed class SalesEvent {
    data class ProductAdded(val product: ProductEntity) : SalesEvent()
    data class ProductNotFound(val query: String) : SalesEvent()
    data class SaleCompleted(val sale: SaleEntity) : SalesEvent()
    data class Error(val message: String) : SalesEvent()
    object CartCleared : SalesEvent()
    data class InsufficientStock(val product: ProductEntity, val available: Int) : SalesEvent()
}

@HiltViewModel
class CartViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val salesRepository: SalesRepository,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SalesEvent>()
    val events: SharedFlow<SalesEvent> = _events.asSharedFlow()

    private val _cartItems = MutableStateFlow<List<CartItemEntity>>(emptyList())
    val cartItems: StateFlow<List<CartItemEntity>> = _cartItems.asStateFlow()

    private var currentUserId: String? = null

    init {
        loadCurrentUser()
        observeCart()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            authRepository.getCurrentUser().collectLatest { user ->
                currentUserId = user?.id
            }
        }
    }

    private fun observeCart() {
        viewModelScope.launch {
            salesRepository.getCartItems().collectLatest { items ->
                _cartItems.value = items
                updateCartState(items)
            }
        }
    }

    private fun updateCartState(items: List<CartItemEntity>) {
        val subtotal = items.sumOf { it.subtotal }
        val discount = _uiState.value.discount
        val total = subtotal - discount

        _uiState.value = _uiState.value.copy(
            itemCount = items.sumOf { it.quantity },
            subtotal = subtotal,
            total = total
        )
    }

    fun addProductByBarcode(barcode: String) {
        viewModelScope.launch {
            val product = productRepository.getProductByBarcode(barcode)
                ?: productRepository.getProductByIdentifier(barcode)

            if (product == null) {
                _events.emit(SalesEvent.ProductNotFound(barcode))
                return@launch
            }

            if (product.totalStock <= 0) {
                _events.emit(SalesEvent.InsufficientStock(product, 0))
                return@launch
            }

            salesRepository.addToCart(product, null, 1)
            _events.emit(SalesEvent.ProductAdded(product))
            _uiState.value = _uiState.value.copy(lastScannedBarcode = barcode)
        }
    }

    fun addProduct(product: ProductEntity, variant: ProductVariantEntity? = null, quantity: Int = 1) {
        viewModelScope.launch {
            val availableStock = variant?.stock ?: product.totalStock
            if (availableStock < quantity) {
                _events.emit(SalesEvent.InsufficientStock(product, availableStock))
                return@launch
            }

            salesRepository.addToCart(product, variant, quantity)
            _events.emit(SalesEvent.ProductAdded(product))
        }
    }

    fun updateQuantity(cartItem: CartItemWithProduct, newQuantity: Int) {
        viewModelScope.launch {
            if (newQuantity <= 0) {
                salesRepository.removeFromCart(cartItem.id)
            } else {
                salesRepository.updateCartItemQuantity(cartItem.id, newQuantity)
            }
        }
    }

    fun removeFromCart(cartItem: CartItemWithProduct) {
        viewModelScope.launch {
            salesRepository.removeFromCart(cartItem.id)
        }
    }

    fun restoreCartItem(cartItem: CartItemWithProduct) {
        viewModelScope.launch {
            // Re-agregar el item al carrito
            val product = productRepository.getProductById(cartItem.productId)
            if (product != null) {
                salesRepository.addToCart(product, null, cartItem.quantity)
            }
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            salesRepository.clearCart()
            _events.emit(SalesEvent.CartCleared)
        }
    }

    fun setCustomerName(name: String) {
        _uiState.value = _uiState.value.copy(customerName = name)
    }

    fun setCustomerPhone(phone: String) {
        _uiState.value = _uiState.value.copy(customerPhone = phone)
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _uiState.value = _uiState.value.copy(selectedPaymentMethod = method)
    }

    fun setDiscount(discount: Long) {
        val state = _uiState.value
        _uiState.value = state.copy(
            discount = discount,
            total = state.subtotal - discount
        )
    }

    fun completeSale(amountPaid: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)

            try {
                val state = _uiState.value
                val userId = currentUserId ?: return@launch
                val userName = sessionManager.getUserName() ?: "Usuario"

                val saleNumber = salesRepository.generateSaleNumber()

                val sale = SaleEntity(
                    id = Generators.generateId(),
                    saleNumber = saleNumber,
                    customerName = state.customerName.ifBlank { null },
                    subtotal = state.subtotal,
                    totalDiscount = state.discount,
                    total = state.total,
                    paymentMethod = state.selectedPaymentMethod.name,
                    amountPaid = amountPaid,
                    changeAmount = (amountPaid - state.total).coerceAtLeast(0),
                    soldBy = userId,
                    soldByName = userName,
                    status = SaleEntity.STATUS_COMPLETED
                )

                // Obtener items del carrito
                val cartItems = salesRepository.getCartItemsSync()
                val saleItems = cartItems.map { item ->
                    val product = productRepository.getProductById(item.productId)
                    SaleItemEntity(
                        id = Generators.generateId(),
                        saleId = sale.id,
                        productId = item.productId,
                        productName = item.productName,
                        productIdentifier = product?.identifier ?: "",
                        variantId = item.variantId,
                        variantDescription = item.variantDescription,
                        quantity = item.quantity,
                        unitPrice = item.unitPrice,
                        purchasePrice = product?.purchasePrice ?: 0L,
                        discount = 0L,
                        subtotal = item.subtotal,
                        productImageUrl = item.imageUrl,
                        barcode = product?.barcode
                    )
                }

                // Crear venta
                salesRepository.createSale(sale, saleItems)

                // Actualizar stock de productos
                for (item in cartItems) {
                    val product = productRepository.getProductById(item.productId)
                    if (product != null) {
                        val newStock = (product.totalStock - item.quantity).coerceAtLeast(0)
                        productRepository.updateStock(product.id, newStock)
                    }
                }

                // Limpiar carrito
                salesRepository.clearCart()

                _uiState.value = _uiState.value.copy(isProcessing = false)
                _events.emit(SalesEvent.SaleCompleted(sale))

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isProcessing = false)
                _events.emit(SalesEvent.Error("Error al procesar venta: ${e.message}"))
            }
        }
    }
}

// ==================== SALES HISTORY VIEW MODEL ====================

data class SalesHistoryUiState(
    val isLoading: Boolean = false,
    val sales: List<SaleEntity> = emptyList(),
    val filteredSales: List<SaleEntity> = emptyList(),
    val dateFilter: DateFilter = DateFilter.TODAY,
    val statusFilter: SaleStatus? = null,
    val paymentMethodFilter: PaymentMethod? = null,
    val customDateStart: Long? = null,
    val customDateEnd: Long? = null
)

enum class DateFilter {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    THIS_YEAR,
    ALL,
    CUSTOM
}

data class SalesStatistics(
    val totalSales: Long = 0L,
    val totalTransactions: Int = 0,
    val averageTicket: Long = 0L,
    val totalDiscount: Long = 0L
)

data class SaleWithItems(
    val sale: SaleEntity,
    val items: List<SaleItemEntity>
)

sealed class SalesHistoryEvent {
    object SaleCancelled : SalesHistoryEvent()
    data class Error(val message: String) : SalesHistoryEvent()
}

@HiltViewModel
class SalesHistoryViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesHistoryUiState())
    val uiState: StateFlow<SalesHistoryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SalesHistoryEvent>()
    val events: SharedFlow<SalesHistoryEvent> = _events.asSharedFlow()

    init {
        loadSales()
    }

    private fun loadSales() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            salesRepository.getAllSales().collectLatest { sales ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    sales = sales,
                    filteredSales = filterSales(sales)
                )
            }
        }
    }

    fun setDateFilter(filter: DateFilter) {
        _uiState.value = _uiState.value.copy(dateFilter = filter)
        applyFilters()
    }

    fun setStatusFilter(status: SaleStatus?) {
        _uiState.value = _uiState.value.copy(statusFilter = status)
        applyFilters()
    }

    fun setPaymentMethodFilter(method: PaymentMethod?) {
        _uiState.value = _uiState.value.copy(paymentMethodFilter = method)
        applyFilters()
    }

    fun setCustomDateRange(start: Long, end: Long) {
        _uiState.value = _uiState.value.copy(
            dateFilter = DateFilter.CUSTOM,
            customDateStart = start,
            customDateEnd = end
        )
        applyFilters()
    }

    private fun applyFilters() {
        val filteredSales = filterSales(_uiState.value.sales)
        _uiState.value = _uiState.value.copy(filteredSales = filteredSales)
    }

    private fun filterSales(sales: List<SaleEntity>): List<SaleEntity> {
        val state = _uiState.value
        var filtered = sales

        // Filtrar por fecha
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        filtered = when (state.dateFilter) {
            DateFilter.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis
                filtered.filter { it.soldAt >= startOfDay }
            }
            DateFilter.YESTERDAY -> {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfYesterday = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                val endOfYesterday = calendar.timeInMillis
                filtered.filter { it.soldAt in startOfYesterday until endOfYesterday }
            }
            DateFilter.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfWeek = calendar.timeInMillis
                filtered.filter { it.soldAt >= startOfWeek }
            }
            DateFilter.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfMonth = calendar.timeInMillis
                filtered.filter { it.soldAt >= startOfMonth }
            }
            DateFilter.THIS_YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfYear = calendar.timeInMillis
                filtered.filter { it.soldAt >= startOfYear }
            }
            DateFilter.ALL -> filtered
            DateFilter.CUSTOM -> {
                val start = state.customDateStart ?: 0L
                val end = state.customDateEnd ?: now
                filtered.filter { it.soldAt in start..end }
            }
        }

        // Filtrar por estado
        state.statusFilter?.let { status ->
            filtered = filtered.filter { it.status == status.name }
        }

        // Filtrar por método de pago
        state.paymentMethodFilter?.let { method ->
            filtered = filtered.filter { it.paymentMethod == method.name }
        }

        return filtered.sortedByDescending { it.soldAt }
    }

    fun cancelSale(saleId: String, reason: String) {
        viewModelScope.launch {
            try {
                salesRepository.cancelSale(saleId, reason)
                _events.emit(SalesHistoryEvent.SaleCancelled)
            } catch (e: Exception) {
                _events.emit(SalesHistoryEvent.Error("Error al cancelar: ${e.message}"))
            }
        }
    }

    suspend fun getSaleDetails(saleId: String): SaleWithItems? {
        return salesRepository.getSaleWithItems(saleId)
    }

    fun getStatistics(): SalesStatistics {
        val filtered = _uiState.value.filteredSales.filter { it.status == SaleStatus.COMPLETED.name }
        return SalesStatistics(
            totalSales = filtered.sumOf { it.total },
            totalTransactions = filtered.size,
            averageTicket = if (filtered.isNotEmpty()) filtered.sumOf { it.total } / filtered.size else 0L,
            totalDiscount = filtered.sumOf { it.totalDiscount }
        )
    }
}