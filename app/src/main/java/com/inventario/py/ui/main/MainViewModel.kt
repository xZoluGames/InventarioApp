package com.inventario.py.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.ProductEntity
import com.inventario.py.data.local.entity.UserEntity
import com.inventario.py.data.local.entity.UserRole
import com.inventario.py.data.repository.AuthRepository
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SalesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DashboardStats(
    val totalProducts: Int = 0,
    val lowStockProducts: Int = 0,
    val todaySales: Long = 0L,
    val todayTransactions: Int = 0,
    val monthSales: Long = 0L,
    val monthTransactions: Int = 0,
    val todayProfit: Long = 0L,
    val monthProfit: Long = 0L
)

data class MainUiState(
    val isLoading: Boolean = true,
    val currentUser: UserEntity? = null,
    val isOwner: Boolean = false,
    val stats: DashboardStats = DashboardStats(),
    val lowStockProducts: List<ProductEntity> = emptyList(),
    val recentProducts: List<ProductEntity> = emptyList()
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val productRepository: ProductRepository,
    private val salesRepository: SalesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadUserAndData()
    }

    private fun loadUserAndData() {
        viewModelScope.launch {
            authRepository.getCurrentUser().collectLatest { user ->
                if (user != null) {
                    _uiState.value = _uiState.value.copy(
                        currentUser = user,
                        isOwner = user.role == UserRole.OWNER
                    )
                    loadDashboardData()
                }
            }
        }
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                // Obtener fechas para consultas
                val calendar = Calendar.getInstance()
                val today = calendar.timeInMillis
                
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.timeInMillis
                
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val startOfMonth = calendar.timeInMillis

                // Combinar flujos de datos
                combine(
                    productRepository.getAllProducts(),
                    productRepository.getLowStockProducts(),
                    salesRepository.getSalesByDateRange(startOfDay, today),
                    salesRepository.getSalesByDateRange(startOfMonth, today)
                ) { allProducts, lowStock, todaySales, monthSales ->
                    
                    val todayTotal = todaySales.sumOf { it.totalAmount }
                    val todayProfit = todaySales.sumOf { it.totalAmount - (it.totalAmount * 0.7).toLong() } // Estimación
                    val monthTotal = monthSales.sumOf { it.totalAmount }
                    val monthProfit = monthSales.sumOf { it.totalAmount - (it.totalAmount * 0.7).toLong() }

                    DashboardStats(
                        totalProducts = allProducts.size,
                        lowStockProducts = lowStock.size,
                        todaySales = todayTotal,
                        todayTransactions = todaySales.size,
                        monthSales = monthTotal,
                        monthTransactions = monthSales.size,
                        todayProfit = todayProfit,
                        monthProfit = monthProfit
                    ) to lowStock
                }.collectLatest { (stats, lowStock) ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        stats = stats,
                        lowStockProducts = lowStock.take(5)
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun refreshData() {
        loadDashboardData()
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
