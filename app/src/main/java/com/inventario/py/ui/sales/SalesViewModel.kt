package com.inventario.py.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.*
import com.inventario.py.data.repository.AuthRepository
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SalesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SalesEvent>()
    val events: SharedFlow<SalesEvent> = _events.asSharedFlow()

    private var currentUserId: String? = null

    init {
        loadCurrentUser()
        loadSavedCart()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            authRepository.getCurrentUserOnce()?.let { user ->
                currentUserId = user.id
            }
        }
    }

    private fun loadSavedCart() {
        viewModelScope.launch {
            currentUserId?.let { userId ->
                salesRepository.getCartItems(userId).collectLatest { cartItems ->
                    val items = cartItems.mapNotNull { cartItem ->
                        productRepository.getProductById(cartItem.productId)?.let { product ->
                            val variant = cartItem.variantId?.let { 
                                productRepository.getVariantById(it) 
                            }
                            CartItem(
                                product = product,
                                variant = variant,
                                quantity = cartItem.quantity,
                                unitPrice = cartItem.unitPrice,
                                subtotal = cartItem.quantity * cartItem.unitPrice
                            )
                        }
                    }
                    updateCartState(items)
                }
            }
        }
    }

    fun searchAndAddProduct(query: String) {
        viewModelScope.launch {
            val product = productRepository.getProductByBarcode(query)
                ?: productRepository.getProductByIdentifier(query)
                ?: productRepository.searchProducts(query).firstOrNull()?.firstOrNull()

            if (product != null) {
                addToCart(product)
                _events.emit(SalesEvent.ProductAdded(product))
            } else {
                _events.emit(SalesEvent.ProductNotFound(query))
            }
        }
    }

    fun addToCart(product: ProductEntity, variant: ProductVariantEntity? = null, quantity: Int = 1) {
        viewModelScope.launch {
            val availableStock = variant?.stock ?: product.totalStock
            
            if (availableStock < quantity) {
                _events.emit(SalesEvent.InsufficientStock(product, availableStock))
                return@launch
            }

            val currentItems = _uiState.value.items.toMutableList()
            val existingIndex = currentItems.indexOfFirst { 
                it.product.id == product.id && it.variant?.id == variant?.id 
            }

            val unitPrice = product.salePrice + (variant?.additionalPrice ?: 0L)

            if (existingIndex >= 0) {
                val existing = currentItems[existingIndex]
                val newQuantity = existing.quantity + quantity
                
                if (newQuantity > availableStock) {
                    _events.emit(SalesEvent.InsufficientStock(product, availableStock))
                    return@launch
                }
                
                currentItems[existingIndex] = existing.copy(
                    quantity = newQuantity,
                    subtotal = newQuantity * unitPrice
                )
            } else {
                currentItems.add(
                    CartItem(
                        product = product,
                        variant = variant,
                        quantity = quantity,
                        unitPrice = unitPrice,
                        subtotal = quantity * unitPrice
                    )
                )
            }

            updateCartState(currentItems)
            saveCartToDatabase(currentItems)
        }
    }

    fun updateQuantity(productId: String, variantId: String?, newQuantity: Int) {
        viewModelScope.launch {
            if (newQuantity <= 0) {
                removeFromCart(productId, variantId)
                return@launch
            }

            val currentItems = _uiState.value.items.toMutableList()
            val index = currentItems.indexOfFirst { 
                it.product.id == productId && it.variant?.id == variantId 
            }

            if (index >= 0) {
                val item = currentItems[index]
                val availableStock = item.variant?.stock ?: item.product.totalStock
                
                if (newQuantity > availableStock) {
                    _events.emit(SalesEvent.InsufficientStock(item.product, availableStock))
                    return@launch
                }

                currentItems[index] = item.copy(
                    quantity = newQuantity,
                    subtotal = newQuantity * item.unitPrice
                )
                updateCartState(currentItems)
                saveCartToDatabase(currentItems)
            }
        }
    }

    fun removeFromCart(productId: String, variantId: String?) {
        viewModelScope.launch {
            val currentItems = _uiState.value.items.toMutableList()
            currentItems.removeAll { it.product.id == productId && it.variant?.id == variantId }
            updateCartState(currentItems)
            saveCartToDatabase(currentItems)
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            updateCartState(emptyList())
            currentUserId?.let { userId ->
                salesRepository.clearCart(userId)
            }
            _events.emit(SalesEvent.CartCleared)
        }
    }

    fun setDiscount(discount: Long) {
        val state = _uiState.value
        val newTotal = (state.subtotal - discount).coerceAtLeast(0)
        _uiState.value = state.copy(
            discount = discount,
            total = newTotal
        )
    }

    fun setCustomerInfo(name: String, phone: String) {
        _uiState.value = _uiState.value.copy(
            customerName = name,
            customerPhone = phone
        )
    }

    fun setPaymentMethod(method: PaymentMethod) {
        _uiState.value = _uiState.value.copy(selectedPaymentMethod = method)
    }

    fun processSale() {
        viewModelScope.launch {
            val state = _uiState.value
            
            if (state.items.isEmpty()) {
                _events.emit(SalesEvent.Error("El carrito está vacío"))
                return@launch
            }

            _uiState.value = state.copy(isProcessing = true)

            try {
                val userId = currentUserId ?: throw Exception("Usuario no autenticado")
                
                // Generar número de venta
                val saleNumber = salesRepository.generateSaleNumber()

                // Crear la venta
                val sale = SaleEntity(
                    id = UUID.randomUUID().toString(),
                    saleNumber = saleNumber,
                    customerName = state.customerName.takeIf { it.isNotBlank() },
                    customerPhone = state.customerPhone.takeIf { it.isNotBlank() },
                    subtotal = state.subtotal,
                    discount = state.discount,
                    totalAmount = state.total,
                    paymentMethod = state.selectedPaymentMethod,
                    status = SaleStatus.COMPLETED,
                    soldBy = userId,
                    createdAt = System.currentTimeMillis()
                )

                // Crear items de venta
                val saleItems = state.items.map { cartItem ->
                    SaleItemEntity(
                        saleId = sale.id,
                        productId = cartItem.product.id,
                        variantId = cartItem.variant?.id,
                        productName = cartItem.product.name,
                        variantInfo = cartItem.variant?.let { "${it.variantLabel}: ${it.variantValue}" },
                        quantity = cartItem.quantity,
                        unitPrice = cartItem.unitPrice,
                        purchasePrice = cartItem.product.purchasePrice,
                        subtotal = cartItem.subtotal
                    )
                }

                // Guardar venta y actualizar stock
                salesRepository.createSale(sale, saleItems)

                // Actualizar stock de productos
                for (item in state.items) {
                    if (item.variant != null) {
                        productRepository.updateVariantStock(item.variant.id, -item.quantity)
                    } else {
                        productRepository.updateProductStock(item.product.id, -item.quantity)
                    }
                }

                // Limpiar carrito
                clearCart()

                _uiState.value = _uiState.value.copy(isProcessing = false)
                _events.emit(SalesEvent.SaleCompleted(sale))

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isProcessing = false)
                _events.emit(SalesEvent.Error("Error al procesar venta: ${e.message}"))
            }
        }
    }

    private fun updateCartState(items: List<CartItem>) {
        val subtotal = items.sumOf { it.subtotal }
        val discount = _uiState.value.discount.coerceAtMost(subtotal)
        val total = subtotal - discount

        _uiState.value = _uiState.value.copy(
            items = items,
            subtotal = subtotal,
            discount = discount,
            total = total,
            itemCount = items.sumOf { it.quantity }
        )
    }

    private suspend fun saveCartToDatabase(items: List<CartItem>) {
        currentUserId?.let { userId ->
            salesRepository.clearCart(userId)
            items.forEach { item ->
                val cartItem = CartItemEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    productId = item.product.id,
                    variantId = item.variant?.id,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice,
                    addedAt = System.currentTimeMillis()
                )
                salesRepository.addCartItem(cartItem)
            }
        }
    }
}

// ViewModel para historial de ventas
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

    private fun applyFilters() {
        val state = _uiState.value
        _uiState.value = state.copy(
            filteredSales = filterSales(state.sales)
        )
    }

    private fun filterSales(sales: List<SaleEntity>): List<SaleEntity> {
        val state = _uiState.value
        var filtered = sales

        // Filtrar por fecha
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        
        filtered = when (state.dateFilter) {
            DateFilter.TODAY -> {
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                val startOfDay = calendar.timeInMillis
                filtered.filter { it.createdAt >= startOfDay }
            }
            DateFilter.THIS_WEEK -> {
                calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                val startOfWeek = calendar.timeInMillis
                filtered.filter { it.createdAt >= startOfWeek }
            }
            DateFilter.THIS_MONTH -> {
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                val startOfMonth = calendar.timeInMillis
                filtered.filter { it.createdAt >= startOfMonth }
            }
            DateFilter.THIS_YEAR -> {
                calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                val startOfYear = calendar.timeInMillis
                filtered.filter { it.createdAt >= startOfYear }
            }
            DateFilter.ALL -> filtered
            DateFilter.CUSTOM -> {
                val start = state.customDateStart ?: 0L
                val end = state.customDateEnd ?: now
                filtered.filter { it.createdAt in start..end }
            }
        }

        // Filtrar por estado
        state.statusFilter?.let { status ->
            filtered = filtered.filter { it.status == status }
        }

        // Filtrar por método de pago
        state.paymentMethodFilter?.let { method ->
            filtered = filtered.filter { it.paymentMethod == method }
        }

        return filtered.sortedByDescending { it.createdAt }
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
        val filtered = _uiState.value.filteredSales.filter { it.status == SaleStatus.COMPLETED }
        return SalesStatistics(
            totalSales = filtered.sumOf { it.totalAmount },
            totalTransactions = filtered.size,
            averageTicket = if (filtered.isNotEmpty()) filtered.sumOf { it.totalAmount } / filtered.size else 0L,
            totalDiscount = filtered.sumOf { it.discount }
        )
    }
}

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
    TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, ALL, CUSTOM
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
