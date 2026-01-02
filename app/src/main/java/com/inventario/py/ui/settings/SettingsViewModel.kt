package com.inventario.py.ui.settings

import android.content.Context
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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

    companion object {
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_LOW_STOCK_NOTIFICATIONS = "low_stock_notifications"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_LAST_SYNC = "last_sync"
    }

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
        val serverUrl = sessionManager.getServerUrl()
        val lastSyncTime = prefs.getLong(KEY_LAST_SYNC, 0).takeIf { it > 0 }

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
            appVersion = appVersion,
            lastSyncTime = lastSyncTime
        )
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            syncRepository.getPendingSyncCount().collectLatest { count ->
                _uiState.value = _uiState.value.copy(
                    pendingChangesCount = count,
                    syncStatus = if (count > 0) SyncStatus.PENDING else SyncStatus.SYNCED
                )
            }
        }
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _uiState.value = _uiState.value.copy(isDarkMode = enabled)
        viewModelScope.launch {
            _events.emit(SettingsEvent.ThemeChanged)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        _uiState.value = _uiState.value.copy(isSoundEnabled = enabled)
    }

    fun setLowStockNotifications(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_STOCK_NOTIFICATIONS, enabled).apply()
        _uiState.value = _uiState.value.copy(isLowStockNotificationsEnabled = enabled)
    }

    fun setServerUrl(url: String) {
        sessionManager.setServerUrl(url)
        _uiState.value = _uiState.value.copy(serverUrl = url)
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            _events.emit(SettingsEvent.SyncStarted)

            try {
                val result = syncRepository.syncAll()
                result.onSuccess {
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        lastSyncTime = now,
                        syncStatus = SyncStatus.SYNCED
                    )
                    _events.emit(SettingsEvent.SyncCompleted)
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncStatus = SyncStatus.ERROR
                    )
                    _events.emit(SettingsEvent.SyncError(error.message ?: "Error de sincronización"))
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
                authRepository.logout()
                _events.emit(SettingsEvent.LogoutSuccess)
            } catch (e: Exception) {
                _events.emit(SettingsEvent.Error("Error al cerrar sesión: ${e.message}"))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = authRepository.changePassword(currentPassword, newPassword)
                result.onSuccess {
                    _events.emit(SettingsEvent.Error("Contraseña cambiada exitosamente"))
                }.onFailure { error ->
                    _events.emit(SettingsEvent.Error(error.message ?: "Error al cambiar contraseña"))
                }
            } catch (e: Exception) {
                _events.emit(SettingsEvent.Error(e.message ?: "Error al cambiar contraseña"))
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                // Limpiar caché de imágenes y datos temporales
                context.cacheDir.deleteRecursively()
                _events.emit(SettingsEvent.Error("Caché limpiada exitosamente"))
            } catch (e: Exception) {
                _events.emit(SettingsEvent.Error("Error al limpiar caché: ${e.message}"))
            }
        }
    }
}