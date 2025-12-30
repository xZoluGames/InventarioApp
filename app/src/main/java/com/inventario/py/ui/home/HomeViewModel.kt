package com.inventario.py.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.ProductWithVariants
import com.inventario.py.data.local.entity.SaleWithDetails
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SaleRepository
import com.inventario.py.data.repository.SyncRepository
import com.inventario.py.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val saleRepository: SaleRepository,
    private val syncRepository: SyncRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _dashboardState = MutableStateFlow(DashboardState())
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun getUserName(): String {
        return sessionManager.getUserName() ?: "Usuario"
    }

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // Get today's date range
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val startOfDay = calendar.time
                
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                val endOfDay = calendar.time

                // Collect all data
                val productsFlow = productRepository.getAllProducts()
                val lowStockFlow = productRepository.getLowStockProducts()
                val todaySalesFlow = saleRepository.getSalesByDateRange(startOfDay, endOfDay)
                val recentSalesFlow = saleRepository.getRecentSales(5)
                val pendingSyncFlow = syncRepository.getPendingChangesCount()

                combine(
                    productsFlow,
                    lowStockFlow,
                    todaySalesFlow,
                    recentSalesFlow,
                    pendingSyncFlow
                ) { products, lowStock, todaySales, recentSales, pendingSync ->
                    
                    val totalProducts = products.size
                    val activeProducts = products.count { it.product.isActive }
                    val lowStockCount = lowStock.size
                    val outOfStockCount = products.count { getTotalStock(it) == 0 }
                    
                    val todaySalesTotal = todaySales.sumOf { it.sale.totalAmount }
                    val todayTransactions = todaySales.size
                    
                    DashboardState(
                        todaySales = todaySalesTotal,
                        todayTransactions = todayTransactions,
                        totalProducts = totalProducts,
                        activeProducts = activeProducts,
                        lowStockCount = lowStockCount,
                        outOfStockCount = outOfStockCount,
                        pendingSyncCount = pendingSync,
                        lowStockProducts = lowStock.take(10),
                        recentSales = recentSales
                    )
                }.collect { state ->
                    _dashboardState.value = state
                }
                
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getTotalStock(productWithVariants: ProductWithVariants): Int {
        return if (productWithVariants.variants.isEmpty()) {
            productWithVariants.product.currentStock
        } else {
            productWithVariants.variants.sumOf { it.currentStock }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            try {
                syncRepository.syncAll()
            } catch (e: Exception) {
                // Handle sync error
            }
        }
    }
}

data class DashboardState(
    val todaySales: Double = 0.0,
    val todayTransactions: Int = 0,
    val totalProducts: Int = 0,
    val activeProducts: Int = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val pendingSyncCount: Int = 0,
    val lowStockProducts: List<ProductWithVariants> = emptyList(),
    val recentSales: List<SaleWithDetails> = emptyList()
)
