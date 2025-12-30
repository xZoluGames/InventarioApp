package com.inventario.py.data.remote.dto

/**
 * DTOs faltantes para el API
 */

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
    val confirmPassword: String
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val expiresIn: Long = 3600000,
    val user: UserDto
)

data class UserDto(
    val id: String,
    val username: String,
    val email: String,
    val fullName: String,
    val role: String,
    val isActive: Boolean = true
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val fullName: String,
    val role: String = "SELLER"
)
