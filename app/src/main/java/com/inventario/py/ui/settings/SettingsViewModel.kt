package com.inventario.py.ui.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.UserEntity
import com.inventario.py.data.local.entity.UserRole
import com.inventario.py.data.repository.AuthRepository
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SyncRepository
import com.inventario.py.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val currentUser: UserEntity? = null,
    val isOwner: Boolean = false,
    val isDarkMode: Boolean = false,
    val isSoundEnabled: Boolean = true,
    val isLowStockNotificationsEnabled: Boolean = true,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val lastSyncTime: Long? = null,
    val pendingChangesCount: Int = 0,
    val serverUrl: String = "",
    val appVersion: String = "",
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false
)

enum class SyncStatus {
    SYNCED, PENDING, OFFLINE, ERROR
}

sealed class SettingsEvent {
    object LogoutSuccess : SettingsEvent()
    object SyncStarted : SettingsEvent()
    object SyncCompleted : SettingsEvent()
    data class SyncError(val message: String) : SettingsEvent()
    data class Error(val message: String) : SettingsEvent()
    object ThemeChanged : SettingsEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val productRepository: ProductRepository,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    init {
        loadCurrentUser()
        loadSettings()
        observeSyncStatus()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            authRepository.getCurrentUser().collectLatest { user ->
                _uiState.value = _uiState.value.copy(
                    currentUser = user,
                    isOwner = user?.role == UserRole.OWNER.name
                )
            }
        }
    }

    private fun loadSettings() {
        val isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        val isSoundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        val isLowStockNotifications = prefs.getBoolean(KEY_LOW_STOCK_NOTIFICATIONS, true)
        val serverUrl = prefs.getString(KEY_SERVER_URL, "") ?: ""
        
        // Get app version
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }

        _uiState.value = _uiState.value.copy(
            isDarkMode = isDarkMode,
            isSoundEnabled = isSoundEnabled,
            isLowStockNotificationsEnabled = isLowStockNotifications,
            serverUrl = serverUrl,
            appVersion = appVersion
        )
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            // Observe pending sync count
            syncRepository.getPendingSyncCount().collectLatest { count ->
                val status = when {
                    count > 0 -> SyncStatus.PENDING
                    !isNetworkAvailable() -> SyncStatus.OFFLINE
                    else -> SyncStatus.SYNCED
                }
                _uiState.value = _uiState.value.copy(
                    pendingChangesCount = count,
                    syncStatus = status
                )
            }
        }

        viewModelScope.launch {
            // Get last sync time
            val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
            if (lastSync > 0) {
                _uiState.value = _uiState.value.copy(lastSyncTime = lastSync)
            }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _uiState.value = _uiState.value.copy(isDarkMode = enabled)
        
        // Apply theme
        AppCompatDelegate.setDefaultNightMode(
            if (enabled) AppCompatDelegate.MODE_NIGHT_YES 
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        
        viewModelScope.launch {
            _events.emit(SettingsEvent.ThemeChanged)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        _uiState.value = _uiState.value.copy(isSoundEnabled = enabled)
    }

    fun setLowStockNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_STOCK_NOTIFICATIONS, enabled).apply()
        _uiState.value = _uiState.value.copy(isLowStockNotificationsEnabled = enabled)
    }

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
        _uiState.value = _uiState.value.copy(serverUrl = url)
    }

    fun syncNow() {
        viewModelScope.launch {
            if (_uiState.value.isSyncing) return@launch
            
            _uiState.value = _uiState.value.copy(isSyncing = true)
            _events.emit(SettingsEvent.SyncStarted)
            
            try {
                val result = syncRepository.syncAll()
                
                if (result.isSuccess) {
                    prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncStatus = SyncStatus.SYNCED,
                        lastSyncTime = System.currentTimeMillis()
                    )
                    _events.emit(SettingsEvent.SyncCompleted)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncStatus = SyncStatus.ERROR
                    )
                    _events.emit(SettingsEvent.SyncError(result.exceptionOrNull()?.message ?: "Error desconocido"))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncStatus = SyncStatus.ERROR
                )
                _events.emit(SettingsEvent.SyncError(e.message ?: "Error de sincronización"))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                // Clear session
                sessionManager.clearSession()
                
                // Clear user from repository
                authRepository.logout()
                
                _events.emit(SettingsEvent.LogoutSuccess)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.emit(SettingsEvent.Error("Error al cerrar sesión: ${e.message}"))
            }
        }
    }

    fun exportBackup() {
        viewModelScope.launch {
            try {
                // Export local database backup
                syncRepository.exportLocalBackup()
                _events.emit(SettingsEvent.SyncCompleted) // Reuse event for success notification
            } catch (e: Exception) {
                _events.emit(SettingsEvent.Error("Error al exportar respaldo: ${e.message}"))
            }
        }
    }

    fun importBackup(filePath: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                syncRepository.importLocalBackup(filePath)
                
                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.emit(SettingsEvent.SyncCompleted)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.emit(SettingsEvent.Error("Error al importar respaldo: ${e.message}"))
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork
        return network != null
    }

    companion object {
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_LOW_STOCK_NOTIFICATIONS = "low_stock_notifications"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_LAST_SYNC = "last_sync_time"
    }
}
