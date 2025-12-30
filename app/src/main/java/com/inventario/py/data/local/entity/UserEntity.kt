package com.inventario.py.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: String,
    val username: String,
    val email: String,
    val passwordHash: String,
    val fullName: String,
    val role: String, // "OWNER" o "EMPLOYEE"
    val isActive: Boolean = true,
    val profileImageUrl: String? = null,
    val phoneNumber: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long? = null,
    val syncStatus: Int = SYNC_STATUS_SYNCED
) {
    companion object {
        const val ROLE_OWNER = "OWNER"
        const val ROLE_EMPLOYEE = "EMPLOYEE"
        
        const val SYNC_STATUS_SYNCED = 0
        const val SYNC_STATUS_PENDING = 1
        const val SYNC_STATUS_FAILED = 2
    }
    
    fun isOwner(): Boolean = role == ROLE_OWNER
    fun isEmployee(): Boolean = role == ROLE_EMPLOYEE
}
