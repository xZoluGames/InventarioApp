package com.inventario.py.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.ProductEntity
import com.inventario.py.data.local.entity.SyncState
import com.inventario.py.data.local.entity.UserEntity
import com.inventario.py.data.local.entity.UserRole
import com.inventario.py.data.local.entity.totalAmount
import com.inventario.py.data.repository.AuthRepository
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SalesRepository
import com.inventario.py.data.repository.SyncRepository
import com.inventario.py.utils.SessionManager
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
    private val salesRepository: SalesRepository,
    private val syncRepository: SyncRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Estado de sincronización
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Productos con bajo stock (count)
    private val _lowStockProducts = MutableStateFlow(0)
    val lowStockProducts: StateFlow<Int> = _lowStockProducts.asStateFlow()

    init {
        loadUserAndData()
        observeLowStockProducts()
    }

    private fun loadUserAndData() {
        viewModelScope.launch {
            authRepository.getCurrentUser().collectLatest { user ->
                if (user != null) {
                    _uiState.value = _uiState.value.copy(
                        currentUser = user,
                        isOwner = user.role == UserRole.OWNER.name
                    )
                    loadDashboardData()
                }
            }
        }
    }

    private fun observeLowStockProducts() {
        viewModelScope.launch {
            productRepository.getLowStockProducts().collectLatest { products ->
                _lowStockProducts.value = products.size
                _uiState.value = _uiState.value.copy(
                    lowStockProducts = products,
                    stats = _uiState.value.stats.copy(lowStockProducts = products.size)
                )
            }
        }
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Load products count
                productRepository.getProductCount().collectLatest { count ->
                    _uiState.value = _uiState.value.copy(
                        stats = _uiState.value.stats.copy(totalProducts = count)
                    )
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }

        // Load recent products
        viewModelScope.launch {
            productRepository.getRecentProducts(10).collectLatest { products ->
                _uiState.value = _uiState.value.copy(recentProducts = products)
            }
        }
    }

    /**
     * Sincroniza datos con el servidor
     */
    fun syncData() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                val result = syncRepository.syncAll()
                result.onSuccess {
                    _syncState.value = SyncState.Success
                }.onFailure { error ->
                    _syncState.value = SyncState.Error(error.message ?: "Error de sincronización")
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Error de sincronización")
            }
        }
    }

    /**
     * Obtiene el ID del usuario actual
     */
    fun getCurrentUserId(): Long {
        return sessionManager.getUserId()?.toLongOrNull() ?: 0L
    }

    /**
     * Verifica si el usuario actual es dueño
     */
    fun isOwner(): Boolean {
        return sessionManager.isOwner()
    }
    fun syncNow() = syncData()
}