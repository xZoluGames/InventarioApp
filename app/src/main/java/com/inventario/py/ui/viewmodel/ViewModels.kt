package com.inventario.py.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.repository.AuthRepository
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SaleRepository
import com.inventario.py.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ========== Auth ViewModel ==========

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _loginSuccess = MutableStateFlow(false)
    val loginSuccess: StateFlow<Boolean> = _loginSuccess.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = _currentUser.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _loginError.value = null
            
            try {
                val user = authRepository.login(username, password)
                if (user != null) {
                    _currentUser.value = user
                    _loginSuccess.value = true
                } else {
                    _loginError.value = "Usuario o contraseña incorrectos"
                }
            } catch (e: Exception) {
                _loginError.value = "Error al iniciar sesión: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        _currentUser.value = null
        _loginSuccess.value = false
    }

    fun resetLoginSuccess() {
        _loginSuccess.value = false
    }

    fun clearError() {
        _loginError.value = null
    }

    // Permission checks
    val canViewCosts: Boolean get() = _currentUser.value?.canViewCosts == true
    val canEditProducts: Boolean get() = _currentUser.value?.canEditProducts == true
    val canManageUsers: Boolean get() = _currentUser.value?.canManageUsers == true
    val canAccessBackup: Boolean get() = _currentUser.value?.canAccessBackup == true
    val isOwner: Boolean get() = _currentUser.value?.isOwner == true
    val isManager: Boolean get() = _currentUser.value?.isManager == true
}

// ========== Product ViewModel ==========

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _filteredProducts = MutableStateFlow<List<Product>>(emptyList())
    val filteredProducts: StateFlow<List<Product>> = _filteredProducts.asStateFlow()

    private val _selectedProduct = MutableStateFlow<Product?>(null)
    val selectedProduct: StateFlow<Product?> = _selectedProduct.asStateFlow()

    private val _lowStockProducts = MutableStateFlow<List<Product>>(emptyList())
    val lowStockProducts: StateFlow<List<Product>> = _lowStockProducts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _dashboardStats = MutableStateFlow(DashboardStats())
    val dashboardStats: StateFlow<DashboardStats> = _dashboardStats.asStateFlow()

    init {
        loadProducts()
        loadLowStockProducts()
    }

    fun loadProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                productRepository.getAllProducts().collect { products ->
                    _products.value = products
                    applyFilters()
                }
            } catch (e: Exception) {
                _message.value = "Error al cargar productos: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadLowStockProducts() {
        viewModelScope.launch {
            productRepository.getLowStockProducts().collect { products ->
                _lowStockProducts.value = products
            }
        }
    }

    fun loadDashboardStats() {
        viewModelScope.launch {
            try {
                val totalProducts = productRepository.getProductCountValue()
                val lowStockCount = productRepository.getLowStockCount()
                val inStockCount = productRepository.getInStockCount()
                val outOfStockCount = productRepository.getOutOfStockCount()

                _dashboardStats.value = DashboardStats(
                    totalProducts = totalProducts,
                    lowStockCount = lowStockCount,
                    inStockCount = inStockCount,
                    outOfStockCount = outOfStockCount
                )
            } catch (e: Exception) {
                _message.value = "Error al cargar estadísticas: ${e.message}"
            }
        }
    }

    fun loadProduct(productId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _selectedProduct.value = productRepository.getProductById(productId)
            } catch (e: Exception) {
                _message.value = "Error al cargar producto: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchProducts(query: String) {
        _searchQuery.value = query
        _searchState.value = _searchState.value.copy(query = query)
        applyFilters()
    }

    fun filterByLowStock() {
        _searchState.value = SearchState(isLowStockFilter = true)
        applyFilters()
    }

    fun filterByInStock() {
        _searchState.value = SearchState(isInStockFilter = true)
        applyFilters()
    }

    fun filterByOutOfStock() {
        _searchState.value = SearchState(isOutOfStockFilter = true)
        applyFilters()
    }

    fun clearFilters() {
        _searchState.value = SearchState()
        applyFilters()
    }

    private fun applyFilters() {
        val allProducts = _products.value
        val state = _searchState.value

        _filteredProducts.value = allProducts.filter { product ->
            val matchesQuery = state.query.isEmpty() || 
                product.name.contains(state.query, ignoreCase = true) ||
                product.barcode?.contains(state.query, ignoreCase = true) == true ||
                product.identifier?.contains(state.query, ignoreCase = true) == true

            val matchesFilter = when {
                state.isLowStockFilter -> product.isLowStock
                state.isInStockFilter -> product.isInStock
                state.isOutOfStockFilter -> product.isOutOfStock
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    fun saveProduct(product: Product, userId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (product.id == 0L) {
                    productRepository.insertProduct(product, userId)
                    _message.value = "Producto creado exitosamente"
                } else {
                    productRepository.updateProduct(product, userId)
                    _message.value = "Producto actualizado exitosamente"
                }
                _saveSuccess.value = true
            } catch (e: Exception) {
                _message.value = "Error al guardar producto: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteProduct(productId: Long, userId: Long) {
        viewModelScope.launch {
            try {
                productRepository.deleteProduct(productId, userId)
                _message.value = "Producto eliminado"
            } catch (e: Exception) {
                _message.value = "Error al eliminar producto: ${e.message}"
            }
        }
    }

    fun updateStock(productId: Long, newStock: Int, userId: Long) {
        viewModelScope.launch {
            try {
                productRepository.updateStock(productId, newStock, userId)
                _message.value = "Stock actualizado"
            } catch (e: Exception) {
                _message.value = "Error al actualizar stock: ${e.message}"
            }
        }
    }

    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }

    fun clearMessage() {
        _message.value = null
    }
}

// ========== Cart ViewModel ==========

@HiltViewModel
class CartViewModel @Inject constructor(
    private val saleRepository: SaleRepository
) : ViewModel() {

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _cartTotal = MutableStateFlow(0L)
    val cartTotal: StateFlow<Long> = _cartTotal.asStateFlow()

    private val _cartItemCount = MutableStateFlow(0)
    val cartItemCount: StateFlow<Int> = _cartItemCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saleCompleted = MutableStateFlow(false)
    val saleCompleted: StateFlow<Boolean> = _saleCompleted.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadCart()
    }

    fun loadCart() {
        viewModelScope.launch {
            saleRepository.getCartItems().collect { items ->
                _cartItems.value = items
                _cartTotal.value = items.sumOf { it.subtotal }
                _cartItemCount.value = items.sumOf { it.quantity }
            }
        }
    }

    fun addToCart(product: Product, quantity: Int = 1, type: String? = null, capacity: String? = null, color: String? = null) {
        viewModelScope.launch {
            try {
                saleRepository.addToCart(product, quantity, type, capacity, color)
            } catch (e: Exception) {
                _message.value = "Error al agregar al carrito: ${e.message}"
            }
        }
    }

    fun updateQuantity(item: CartItem, newQuantity: Int) {
        viewModelScope.launch {
            try {
                saleRepository.updateCartItemQuantity(item.id, newQuantity)
            } catch (e: Exception) {
                _message.value = "Error al actualizar cantidad: ${e.message}"
            }
        }
    }

    fun incrementQuantity(item: CartItem) {
        if (item.quantity < item.maxQuantity) {
            updateQuantity(item, item.quantity + 1)
        }
    }

    fun decrementQuantity(item: CartItem) {
        updateQuantity(item, item.quantity - 1)
    }

    fun removeItem(itemId: Long) {
        viewModelScope.launch {
            try {
                saleRepository.removeFromCart(itemId)
            } catch (e: Exception) {
                _message.value = "Error al eliminar item: ${e.message}"
            }
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            try {
                saleRepository.clearCart()
            } catch (e: Exception) {
                _message.value = "Error al vaciar carrito: ${e.message}"
            }
        }
    }

    fun completeSale(employeeId: Long, employeeName: String, paymentMethod: PaymentMethod, notes: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val saleId = saleRepository.createSale(employeeId, employeeName, paymentMethod, notes)
                if (saleId > 0) {
                    _saleCompleted.value = true
                    _message.value = "Venta completada exitosamente"
                } else {
                    _message.value = "Error: El carrito está vacío"
                }
            } catch (e: Exception) {
                _message.value = "Error al completar venta: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetSaleCompleted() {
        _saleCompleted.value = false
    }

    fun clearMessage() {
        _message.value = null
    }
}

// ========== Sale ViewModel ==========

@HiltViewModel
class SaleViewModel @Inject constructor(
    private val saleRepository: SaleRepository
) : ViewModel() {

    private val _sales = MutableStateFlow<List<Sale>>(emptyList())
    val sales: StateFlow<List<Sale>> = _sales.asStateFlow()

    private val _todaySales = MutableStateFlow<List<Sale>>(emptyList())
    val todaySales: StateFlow<List<Sale>> = _todaySales.asStateFlow()

    private val _weekSales = MutableStateFlow<List<Sale>>(emptyList())
    val weekSales: StateFlow<List<Sale>> = _weekSales.asStateFlow()

    private val _monthSales = MutableStateFlow<List<Sale>>(emptyList())
    val monthSales: StateFlow<List<Sale>> = _monthSales.asStateFlow()

    private val _selectedSale = MutableStateFlow<Sale?>(null)
    val selectedSale: StateFlow<Sale?> = _selectedSale.asStateFlow()

    private val _todayStats = MutableStateFlow(DashboardStats())
    val todayStats: StateFlow<DashboardStats> = _todayStats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadSales()
    }

    fun loadSales() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                saleRepository.getAllSales().collect { sales ->
                    _sales.value = sales
                }
            } catch (e: Exception) {
                _message.value = "Error al cargar ventas: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadTodaySales() {
        viewModelScope.launch {
            try {
                _todaySales.value = saleRepository.getTodaySales()
            } catch (e: Exception) {
                _message.value = "Error al cargar ventas de hoy: ${e.message}"
            }
        }
    }

    fun loadWeekSales() {
        viewModelScope.launch {
            try {
                _weekSales.value = saleRepository.getWeekSales()
            } catch (e: Exception) {
                _message.value = "Error al cargar ventas de la semana: ${e.message}"
            }
        }
    }

    fun loadMonthSales() {
        viewModelScope.launch {
            try {
                _monthSales.value = saleRepository.getMonthSales()
            } catch (e: Exception) {
                _message.value = "Error al cargar ventas del mes: ${e.message}"
            }
        }
    }

    fun loadTodayStats() {
        viewModelScope.launch {
            try {
                val todayCount = saleRepository.getTodayCount()
                val todayRevenue = saleRepository.getTodayTotal()
                val todayProfit = saleRepository.getTodayProfit()
                val monthCount = saleRepository.getMonthCount()
                val monthRevenue = saleRepository.getMonthTotal()
                val monthProfit = saleRepository.getMonthProfit()

                _todayStats.value = DashboardStats(
                    todaySalesCount = todayCount,
                    todayRevenue = todayRevenue,
                    todayProfit = todayProfit,
                    monthSalesCount = monthCount,
                    monthRevenue = monthRevenue,
                    monthProfit = monthProfit
                )
            } catch (e: Exception) {
                _message.value = "Error al cargar estadísticas: ${e.message}"
            }
        }
    }

    fun loadSale(saleId: Long) {
        viewModelScope.launch {
            try {
                _selectedSale.value = saleRepository.getSaleById(saleId)
            } catch (e: Exception) {
                _message.value = "Error al cargar venta: ${e.message}"
            }
        }
    }

    fun deleteSale(saleId: Long) {
        viewModelScope.launch {
            try {
                saleRepository.deleteSale(saleId)
                _message.value = "Venta eliminada"
                loadSales()
            } catch (e: Exception) {
                _message.value = "Error al eliminar venta: ${e.message}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}

// ========== Settings ViewModel ==========

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _backups = MutableStateFlow<List<BackupFile>>(emptyList())
    val backups: StateFlow<List<BackupFile>> = _backups.asStateFlow()

    init {
        loadUsers()
    }

    fun loadUsers() {
        viewModelScope.launch {
            try {
                authRepository.getAllUsers().collect { users ->
                    _users.value = users
                }
            } catch (e: Exception) {
                _message.value = "Error al cargar usuarios: ${e.message}"
            }
        }
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
        _settings.value = _settings.value.copy(
            theme = if (_isDarkMode.value) AppTheme.DARK else AppTheme.LIGHT
        )
    }

    fun setTheme(theme: AppTheme) {
        _settings.value = _settings.value.copy(theme = theme)
        _isDarkMode.value = theme == AppTheme.DARK
    }

    fun setAutoSync(enabled: Boolean) {
        _settings.value = _settings.value.copy(autoSync = enabled)
    }

    fun setSyncInterval(minutes: Int) {
        _settings.value = _settings.value.copy(syncInterval = minutes)
    }

    fun setLowStockAlerts(enabled: Boolean) {
        _settings.value = _settings.value.copy(lowStockAlerts = enabled)
    }

    fun setServerUrl(url: String) {
        _settings.value = _settings.value.copy(serverUrl = url)
    }

    fun createUser(username: String, password: String, name: String, role: UserRole) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.createUser(username, password, name, role)
                _message.value = "Usuario creado exitosamente"
            } catch (e: Exception) {
                _message.value = "Error al crear usuario: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateUser(user: User) {
        viewModelScope.launch {
            try {
                authRepository.updateUser(user)
                _message.value = "Usuario actualizado"
            } catch (e: Exception) {
                _message.value = "Error al actualizar usuario: ${e.message}"
            }
        }
    }

    fun deleteUser(userId: Long) {
        viewModelScope.launch {
            try {
                authRepository.deleteUser(userId)
                _message.value = "Usuario eliminado"
            } catch (e: Exception) {
                _message.value = "Error al eliminar usuario: ${e.message}"
            }
        }
    }

    fun toggleUserActive(userId: Long) {
        viewModelScope.launch {
            try {
                authRepository.toggleUserActive(userId)
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
