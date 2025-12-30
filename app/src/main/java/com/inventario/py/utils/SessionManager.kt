package com.inventario.py.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.inventario.py.data.local.entity.UserEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session_prefs")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    
    companion object {
        private val KEY_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_USER = stringPreferencesKey("current_user")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_ROLE = stringPreferencesKey("user_role")
        private val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val KEY_TOKEN_EXPIRY = longPreferencesKey("token_expiry")
        private val KEY_LAST_SYNC = longPreferencesKey("last_sync")
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
    }
    
    // ==================== TOKEN ====================
    
    suspend fun saveAuthToken(token: String, refreshToken: String, expiresIn: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOKEN] = token
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_TOKEN_EXPIRY] = System.currentTimeMillis() + (expiresIn * 1000)
        }
    }
    
    fun getAuthToken(): String? {
        return runBlocking {
            context.dataStore.data.first()[KEY_TOKEN]
        }
    }
    
    val authTokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_TOKEN]
    }
    
    fun getRefreshToken(): String? {
        return runBlocking {
            context.dataStore.data.first()[KEY_REFRESH_TOKEN]
        }
    }
    
    suspend fun clearTokens() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_TOKEN_EXPIRY)
        }
    }
    
    fun isTokenExpired(): Boolean {
        return runBlocking {
            val expiry = context.dataStore.data.first()[KEY_TOKEN_EXPIRY] ?: 0L
            System.currentTimeMillis() >= expiry
        }
    }
    
    // ==================== USER ====================
    
    suspend fun saveUser(user: UserEntity) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER] = gson.toJson(user)
            prefs[KEY_USER_ID] = user.id
            prefs[KEY_USER_ROLE] = user.role
            prefs[KEY_IS_LOGGED_IN] = true
        }
    }
    
    fun getCurrentUser(): UserEntity? {
        return runBlocking {
            val userJson = context.dataStore.data.first()[KEY_USER]
            userJson?.let { gson.fromJson(it, UserEntity::class.java) }
        }
    }
    
    val currentUserFlow: Flow<UserEntity?> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER]?.let { gson.fromJson(it, UserEntity::class.java) }
    }
    
    fun getUserId(): String? {
        return runBlocking {
            context.dataStore.data.first()[KEY_USER_ID]
        }
    }
    
    fun getUserRole(): String? {
        return runBlocking {
            context.dataStore.data.first()[KEY_USER_ROLE]
        }
    }
    
    fun isOwner(): Boolean {
        return getUserRole() == UserEntity.ROLE_OWNER
    }
    
    fun isEmployee(): Boolean {
        return getUserRole() == UserEntity.ROLE_EMPLOYEE
    }
    
    val isLoggedInFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_LOGGED_IN] ?: false
    }
    
    fun isLoggedIn(): Boolean {
        return runBlocking {
            context.dataStore.data.first()[KEY_IS_LOGGED_IN] ?: false
        }
    }
    
    // ==================== SYNC ====================
    
    suspend fun saveLastSyncTime(time: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC] = time
        }
    }
    
    fun getLastSyncTime(): Long {
        return runBlocking {
            context.dataStore.data.first()[KEY_LAST_SYNC] ?: 0L
        }
    }
    
    val lastSyncTimeFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNC] ?: 0L
    }
    
    // ==================== SERVER URL ====================
    
    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = url
        }
    }
    
    fun getServerUrl(): String? {
        return runBlocking {
            context.dataStore.data.first()[KEY_SERVER_URL]
        }
    }
    
    // ==================== LOGOUT ====================
    
    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
    
    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_USER)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_ROLE)
            prefs[KEY_IS_LOGGED_IN] = false
        }
    }
}
