package com.inventario.py.data.repository

import com.inventario.py.data.local.dao.UserDao
import com.inventario.py.data.local.entity.UserEntity
import com.inventario.py.data.remote.api.InventarioApi
import com.inventario.py.data.remote.dto.LoginRequest
import com.inventario.py.data.remote.dto.ChangePasswordRequest
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
                sessionManager.saveAuthToken(authResponse.token)
                sessionManager.saveRefreshToken(authResponse.refreshToken)
                
                val user = authResponse.user.toEntity()
                userDao.insertUser(user)
                sessionManager.saveCurrentUser(user)
                
                Result.success(user)
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
        // En producción, usar BCrypt o similar
        // Por ahora, comparación simple para desarrollo
        return password.hashCode().toString() == hash || hash == password
    }
    
    suspend fun logout() {
        try {
            val token = sessionManager.getAuthToken()
            if (token != null) {
                api.logout("Bearer $token")
            }
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
            
            val response = api.refreshToken(refreshToken)
            if (response.isSuccessful && response.body() != null) {
                val newToken = response.body()!!.token
                sessionManager.saveAuthToken(newToken)
                sessionManager.saveRefreshToken(response.body()!!.refreshToken)
                Result.success(newToken)
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
            val response = api.changePassword(
                ChangePasswordRequest(currentPassword, newPassword)
            )
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
