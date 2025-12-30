package com.inventario.py.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.ProductEntity
import com.inventario.py.data.local.entity.SaleEntity
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
    val lowStockProducts: List<ProductEntity> = emptyList(),
    val recentSales: List<SaleEntity> = emptyList(),
    val pendingSyncCount: Int = 0,
    val error: String? = null
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
    }

    fun getUserName(): String {
        return sessionManager.getUserName() ?: "Usuario"
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _dashboardState.value = _dashboardState.value.copy(isLoading = true)
            
            try {
                // Get today's date range
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis
                
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val endOfDay = calendar.timeInMillis

                // Month range
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfMonth = calendar.timeInMillis

                // Collect data
                launch {
                    productRepository.getAllProducts().collect { products ->
                        _dashboardState.value = _dashboardState.value.copy(
                            totalProducts = products.size
                        )
                    }
                }

                launch {
                    productRepository.getLowStockProducts().collect { lowStock ->
                        _dashboardState.value = _dashboardState.value.copy(
                            lowStockCount = lowStock.size,
                            lowStockProducts = lowStock.take(5)
                        )
                    }
                }

                launch {
                    salesRepository.getDailySalesTotal(startOfDay).collect { total ->
                        _dashboardState.value = _dashboardState.value.copy(
                            todaySalesTotal = total ?: 0L
                        )
                    }
                }

                launch {
                    salesRepository.getDailySalesCount(startOfDay).collect { count ->
                        _dashboardState.value = _dashboardState.value.copy(
                            todaySalesCount = count
                        )
                    }
                }

                launch {
                    salesRepository.getMonthlySalesTotal(startOfMonth).collect { total ->
                        _dashboardState.value = _dashboardState.value.copy(
                            monthSalesTotal = total ?: 0L
                        )
                    }
                }

                launch {
                    salesRepository.getMonthlySalesCount(startOfMonth).collect { count ->
                        _dashboardState.value = _dashboardState.value.copy(
                            monthSalesCount = count
                        )
                    }
                }

                launch {
                    salesRepository.getRecentSales(5).collect { sales ->
                        _dashboardState.value = _dashboardState.value.copy(
                            recentSales = sales
                        )
                    }
                }

                launch {
                    syncRepository.getPendingChangesCount().collect { count ->
                        _dashboardState.value = _dashboardState.value.copy(
                            pendingSyncCount = count
                        )
                    }
                }

                _dashboardState.value = _dashboardState.value.copy(isLoading = false)
                
            } catch (e: Exception) {
                _dashboardState.value = _dashboardState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun refreshData() {
        loadDashboardData()
    }
}
