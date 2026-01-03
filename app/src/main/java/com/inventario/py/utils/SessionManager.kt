package com.inventario.py.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.inventario.py.data.local.entity.UserEntity
import com.inventario.py.data.local.entity.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val PREFS_NAME = "inventario_session"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_CURRENT_USER = "current_user"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val DEFAULT_SERVER_URL = "https://inventariopy.ddns.net"
    }
    
    private val gson = Gson()
    
    // StateFlow para el usuario actual
    private val _currentUserFlow = MutableStateFlow<UserEntity?>(null)
    val currentUserFlow: Flow<UserEntity?> = _currentUserFlow.asStateFlow()
    
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    init {
        // Cargar usuario al inicio
        _currentUserFlow.value = getCurrentUser()
    }
    
    // ==================== AUTH TOKEN ====================
    
    fun saveAuthToken(token: String, expiresIn: Long = 3600000) {
        prefs.edit().apply {
            putString(KEY_AUTH_TOKEN, token)
            putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + expiresIn)
            apply()
        }
    }
    
    fun getAuthToken(): String? {
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        if (System.currentTimeMillis() > expiry) {
            clearAuthToken()
            return null
        }
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }
    
    fun clearAuthToken() {
        prefs.edit().remove(KEY_AUTH_TOKEN).remove(KEY_TOKEN_EXPIRY).apply()
    }
    
    // ==================== REFRESH TOKEN ====================
    
    fun saveRefreshToken(token: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }
    
    fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }
    
    // ==================== USER INFO ====================
    
    fun saveUserId(userId: String) {
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }
    
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    
    fun saveUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
    }
    
    fun getUserName(): String? = prefs.getString(KEY_USER_NAME, null)
    
    fun saveUserRole(role: String) {
        prefs.edit().putString(KEY_USER_ROLE, role).apply()
    }
    
    fun getUserRole(): String? = prefs.getString(KEY_USER_ROLE, null)
    
    // ==================== CURRENT USER ====================
    
    fun saveCurrentUser(user: UserEntity) {
        val userJson = gson.toJson(user)
        prefs.edit().apply {
            putString(KEY_CURRENT_USER, userJson)
            putString(KEY_USER_ID, user.id)
            putString(KEY_USER_NAME, user.fullName)
            putString(KEY_USER_EMAIL, user.email)
            putString(KEY_USER_ROLE, user.role)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
        // Actualizar el Flow
        _currentUserFlow.value = user
    }
    
    fun getCurrentUser(): UserEntity? {
        val userJson = prefs.getString(KEY_CURRENT_USER, null) ?: return null
        return try {
            gson.fromJson(userJson, UserEntity::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    fun getCurrentUserId(): String? = getUserId()
    
    // ==================== LOGIN STATE ====================
    
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false) && getAuthToken() != null
    }

    
    // ==================== ROLE CHECK ====================
    
    fun isOwner(): Boolean = getUserRole() == UserRole.OWNER.name
    

    
    fun isAdmin(): Boolean {
        val role = getUserRole()
        return role == UserRole.OWNER.name || role == UserRole.ADMIN.name
    }
    
    // ==================== SERVER CONFIG ====================
    
    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }
    
    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }
    
    // ==================== SESSION MANAGEMENT ====================
    
    fun createSession(token: String, refreshToken: String, user: UserEntity, expiresIn: Long = 3600000) {
        saveAuthToken(token, expiresIn)
        saveRefreshToken(refreshToken)
        saveCurrentUser(user)
    }
    
    fun clearSession() {
        prefs.edit().clear().apply()
        _currentUserFlow.value = null
    }
    
    fun logout() {
        clearSession()
    }
}
