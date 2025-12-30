package com.inventario.py.data.repository

import com.inventario.py.data.local.dao.UserDao
import com.inventario.py.data.local.entity.UserEntity
import com.inventario.py.data.remote.api.InventarioApi
import com.inventario.py.data.remote.dto.LoginRequest
import com.inventario.py.data.remote.dto.RefreshTokenRequest
import com.inventario.py.utils.SessionManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: InventarioApi,
    private val userDao: UserDao,
    private val sessionManager: SessionManager
) {
    suspend fun login(username: String, password: String): Result<UserEntity> {
        return try {
            val response = api.login(LoginRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                // Acceder a través de .data
                val authData = authResponse.data
                if (authData != null) {
                    sessionManager.saveAuthToken(authData.token, authData.expiresIn)
                    sessionManager.saveRefreshToken(authData.refreshToken)
                    
                    val user = authData.user.toEntity()
                    userDao.insertUser(user)
                    sessionManager.saveCurrentUser(user)
                    
                    Result.success(user)
                } else {
                    Result.failure(Exception(authResponse.message ?: "Error de autenticación"))
                }
            } else {
                Result.failure(Exception(response.message() ?: "Error de autenticación"))
            }
        } catch (e: Exception) {
            // Intentar login offline
            val localUser = userDao.getUserByUsername(username)
            if (localUser != null && verifyPasswordOffline(password, localUser.passwordHash)) {
                sessionManager.saveCurrentUser(localUser)
                Result.success(localUser)
            } else {
                Result.failure(e)
            }
        }
    }
    
    private fun verifyPasswordOffline(password: String, hash: String): Boolean {
        return password.hashCode().toString() == hash || hash == password
    }
    
    suspend fun logout() {
        try {
            api.logout()
        } catch (e: Exception) {
            // Ignorar errores de red al cerrar sesión
        } finally {
            sessionManager.clearSession()
        }
    }
    
    suspend fun refreshToken(): Result<String> {
        return try {
            val refreshToken = sessionManager.getRefreshToken()
            if (refreshToken == null) {
                return Result.failure(Exception("No hay token de refresh"))
            }
            
            val response = api.refreshToken(RefreshTokenRequest(refreshToken))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val newToken = body.token
                val newRefresh = body.refreshToken
                if (newToken != null) {
                    sessionManager.saveAuthToken(newToken, body.expiresIn ?: 3600000)
                    if (newRefresh != null) {
                        sessionManager.saveRefreshToken(newRefresh)
                    }
                    Result.success(newToken)
                } else {
                    sessionManager.clearSession()
                    Result.failure(Exception("Token inválido"))
                }
            } else {
                sessionManager.clearSession()
                Result.failure(Exception("Sesión expirada"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            val request = mapOf(
                "currentPassword" to currentPassword,
                "newPassword" to newPassword,
                "confirmPassword" to newPassword
            )
            val response = api.changePassword(request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message() ?: "Error al cambiar contraseña"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getCurrentUser(): Flow<UserEntity?> = sessionManager.currentUserFlow
    
    suspend fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()
    
    suspend fun getCurrentUserOnce(): UserEntity? = sessionManager.getCurrentUser()
}
