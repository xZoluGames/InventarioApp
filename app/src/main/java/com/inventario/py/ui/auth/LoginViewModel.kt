package com.inventario.py.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.UserEntity
import com.inventario.py.data.repository.AuthRepository
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
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    init {
        checkExistingSession()
    }

    private fun checkExistingSession() {
        viewModelScope.launch {
            if (authRepository.isLoggedIn()) {
                authRepository.getCurrentUserOnce()?.let { user ->
                    _events.emit(LoginEvent.Success(user))
                }
            }
        }
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

    fun login() {
        val currentState = _uiState.value
        
        // Validaciones
        var hasError = false
        var newState = currentState

        if (currentState.username.isBlank()) {
            newState = newState.copy(usernameError = "Ingrese su usuario")
            hasError = true
        }

        if (currentState.password.isBlank()) {
            newState = newState.copy(passwordError = "Ingrese su contraseña")
            hasError = true
        } else if (currentState.password.length < 4) {
            newState = newState.copy(passwordError = "La contraseña debe tener al menos 4 caracteres")
            hasError = true
        }

        if (hasError) {
            _uiState.value = newState
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true)
            
            authRepository.login(currentState.username.trim(), currentState.password)
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(LoginEvent.Success(user))
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(LoginEvent.Error(
                        error.message ?: "Error al iniciar sesión. Verifique sus credenciales."
                    ))
                }
        }
    }
}
