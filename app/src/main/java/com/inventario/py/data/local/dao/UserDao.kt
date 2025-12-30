package com.inventario.py.data.local.dao

import androidx.room.*
import com.inventario.py.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    
    @Query("SELECT * FROM users WHERE isActive = 1 ORDER BY fullName ASC")
    fun getAllUsers(): Flow<List<UserEntity>>
    
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): UserEntity?
    
    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserByIdFlow(id: String): Flow<UserEntity?>
    
    @Query("SELECT * FROM users WHERE username = :username AND isActive = 1 LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?
    
    @Query("SELECT * FROM users WHERE email = :email AND isActive = 1 LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?
    
    @Query("SELECT * FROM users WHERE role = :role AND isActive = 1 ORDER BY fullName ASC")
    fun getUsersByRole(role: String): Flow<List<UserEntity>>
    
    @Query("SELECT COUNT(*) FROM users WHERE role = 'OWNER' AND isActive = 1")
    suspend fun getOwnerCount(): Int
    
    @Query("SELECT * FROM users WHERE syncStatus != 0")
    suspend fun getUnsyncedUsers(): List<UserEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)
    
    @Update
    suspend fun updateUser(user: UserEntity)
    
    @Query("UPDATE users SET lastLoginAt = :loginTime WHERE id = :userId")
    suspend fun updateLastLogin(userId: String, loginTime: Long = System.currentTimeMillis())
    
    @Query("UPDATE users SET syncStatus = :syncStatus WHERE id = :userId")
    suspend fun updateSyncStatus(userId: String, syncStatus: Int)
    
    @Query("UPDATE users SET isActive = 0, updatedAt = :updatedAt WHERE id = :userId")
    suspend fun deactivateUser(userId: String, updatedAt: Long = System.currentTimeMillis())
    
    @Delete
    suspend fun deleteUser(user: UserEntity)
    
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}
