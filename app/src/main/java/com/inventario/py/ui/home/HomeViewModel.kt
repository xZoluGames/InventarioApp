package com.inventario.py.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.ProductEntity
import com.inventario.py.data.local.entity.ProductWithVariants
import com.inventario.py.data.local.entity.SaleEntity
import com.inventario.py.data.local.entity.SaleWithDetails
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SalesRepository
import com.inventario.py.data.repository.SyncRepository
import com.inventario.py.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class DashboardState(
    val isLoading: Boolean = true,
    val totalProducts: Int = 0,
    val lowStockCount: Int = 0,
    val todaySalesTotal: Long = 0L,
    val todaySalesCount: Int = 0,
    val monthSalesTotal: Long = 0L,
    val monthSalesCount: Int = 0,
    val lowStockProducts: List<ProductWithVariants> = emptyList(),
    val recentSales: List<SaleWithDetails> = emptyList(),
    val pendingSyncCount: Int = 0,
    val error: String? = null,
    // Propiedades adicionales para compatibilidad
    val todaySales: Long = 0L,
    val todayTransactions: Int = 0,
    val activeProducts: Int = 0,
    val outOfStockCount: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val salesRepository: SalesRepository,
    private val syncRepository: SyncRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadDashboardData()
        observePendingSync()
    }

    fun getUserName(): String {
        return sessionManager.getUserName() ?: "Usuario"
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            _dashboardState.value = _dashboardState.value.copy(isLoading = true)

            try {
                // Load product count
                productRepository.getProductCount().collectLatest { count ->
                    _dashboardState.value = _dashboardState.value.copy(
                        totalProducts = count,
                        activeProducts = count
                    )
                }
            } catch (e: Exception) {
                _dashboardState.value = _dashboardState.value.copy(
                    error = e.message
                )
            }
        }

        // Load low stock products
        viewModelScope.launch {
            productRepository.getLowStockProducts().collectLatest { products ->
                val productsWithVariants = products.map { product ->
                    ProductWithVariants(product = product, variants = emptyList())
                }
                _dashboardState.value = _dashboardState.value.copy(
                    lowStockCount = products.size,
                    lowStockProducts = productsWithVariants
                )
            }
        }

        // Load out of stock count
        viewModelScope.launch {
            productRepository.getOutOfStockProducts().collectLatest { products ->
                _dashboardState.value = _dashboardState.value.copy(
                    outOfStockCount = products.size
                )
            }
        }

        // Load recent sales
        viewModelScope.launch {
            salesRepository.getRecentSales(10).collectLatest { sales ->
                val salesWithDetails = sales.map { sale ->
                    SaleWithDetails(sale = sale, items = emptyList(), seller = null)
                }
                _dashboardState.value = _dashboardState.value.copy(
                    recentSales = salesWithDetails
                )
            }
        }

        // Load today's sales
        viewModelScope.launch {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val todayStart = calendar.timeInMillis

            salesRepository.getSalesByDateRange(todayStart, System.currentTimeMillis()).collectLatest { sales ->
                val completedSales = sales.filter { it.status == SaleEntity.STATUS_COMPLETED }
                val totalSales = completedSales.sumOf { it.total }
                _dashboardState.value = _dashboardState.value.copy(
                    todaySalesTotal = totalSales,
                    todaySalesCount = completedSales.size,
                    todaySales = totalSales,
                    todayTransactions = completedSales.size
                )
            }
        }

        _isLoading.value = false
        _dashboardState.value = _dashboardState.value.copy(isLoading = false)
    }

    private fun observePendingSync() {
        viewModelScope.launch {
            syncRepository.getPendingSyncCount().collectLatest { count ->
                _dashboardState.value = _dashboardState.value.copy(
                    pendingSyncCount = count
                )
            }
        }
    }

    /**
     * Sincroniza ahora los datos pendientes
     */
    fun syncNow() {
        viewModelScope.launch {
            try {
                syncRepository.syncAll()
            } catch (e: Exception) {
                _dashboardState.value = _dashboardState.value.copy(
                    error = "Error de sincronización: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _dashboardState.value = _dashboardState.value.copy(error = null)
    }
}