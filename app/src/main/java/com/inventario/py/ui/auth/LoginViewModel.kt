package com.inventario.py.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.LoginState
import com.inventario.py.data.local.entity.UserEntity
import com.inventario.py.data.repository.AuthRepository
import com.inventario.py.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val username: String = "",
    val password: String = "",
    val usernameError: String? = null,
    val passwordError: String? = null,
    val rememberMe: Boolean = false
)

sealed class LoginEvent {
    data class Success(val user: UserEntity) : LoginEvent()
    data class Error(val message: String) : LoginEvent()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    // LoginState para observar desde la Activity
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    init {
        checkExistingSession()
    }

    private fun checkExistingSession() {
        viewModelScope.launch {
            if (authRepository.isLoggedIn()) {
                authRepository.getCurrentUserOnce()?.let { user ->
                    _events.emit(LoginEvent.Success(user))
                    _loginState.value = LoginState.Success(user)
                }
            }
        }
    }

    /**
     * Verifica si el usuario ya está logueado
     */
    fun isLoggedIn(): Boolean {
        return sessionManager.isLoggedIn()
    }

    fun onUsernameChanged(username: String) {
        _uiState.value = _uiState.value.copy(
            username = username,
            usernameError = null
        )
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(
            password = password,
            passwordError = null
        )
    }

    fun onRememberMeChanged(remember: Boolean) {
        _uiState.value = _uiState.value.copy(rememberMe = remember)
    }

    /**
     * Login sin parámetros - usa los valores del estado
     */
    fun login() {
        val currentState = _uiState.value
        performLogin(currentState.username, currentState.password)
    }

    /**
     * Login con parámetros - para compatibilidad con LoginActivity
     */
    fun login(email: String, password: String) {
        performLogin(email, password)
    }

    private fun performLogin(username: String, password: String) {
        // Validaciones
        var hasError = false
        var newState = _uiState.value

        if (username.isBlank()) {
            newState = newState.copy(usernameError = "Ingrese su usuario")
            hasError = true
        }

        if (password.isBlank()) {
            newState = newState.copy(passwordError = "Ingrese su contraseña")
            hasError = true
        } else if (password.length < 4) {
            newState = newState.copy(passwordError = "La contraseña debe tener al menos 4 caracteres")
            hasError = true
        }

        if (hasError) {
            _uiState.value = newState
            _loginState.value = LoginState.Error("Por favor complete todos los campos")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            _loginState.value = LoginState.Loading

            authRepository.login(username.trim(), password)
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(LoginEvent.Success(user))
                    _loginState.value = LoginState.Success(user)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    val errorMessage = error.message ?: "Error al iniciar sesión. Verifique sus credenciales."
                    _events.emit(LoginEvent.Error(errorMessage))
                    _loginState.value = LoginState.Error(errorMessage)
                }
        }
    }

    /**
     * Obtiene la URL del servidor
     */
    fun getServerUrl(): String {
        return sessionManager.getServerUrl()
    }

    /**
     * Establece la URL del servidor
     */
    fun setServerUrl(url: String) {
        sessionManager.setServerUrl(url)
    }
}