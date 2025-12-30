package com.inventario.py.data.remote.api

import com.inventario.py.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface InventarioApi {

    // ========== Autenticación ==========
    
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<ApiResponse<Unit>>

    @POST("auth/refresh")
    suspend fun refreshToken(): Response<LoginResponse>

    // ========== Productos ==========

    @GET("products")
    suspend fun getAllProducts(): Response<ApiResponse<List<ProductResponse>>>

    @GET("products/{id}")
    suspend fun getProduct(@Path("id") id: String): Response<ApiResponse<ProductResponse>>

    @POST("products")
    suspend fun createProduct(@Body product: ProductRequest): Response<ApiResponse<ProductResponse>>

    @PUT("products/{id}")
    suspend fun updateProduct(
        @Path("id") id: String,
        @Body product: ProductRequest
    ): Response<ApiResponse<ProductResponse>>

    @DELETE("products/{id}")
    suspend fun deleteProduct(@Path("id") id: String): Response<ApiResponse<Unit>>

    @GET("products/barcode/{barcode}")
    suspend fun getProductByBarcode(@Path("barcode") barcode: String): Response<ApiResponse<ProductResponse>>

    @GET("products/low-stock")
    suspend fun getLowStockProducts(): Response<ApiResponse<List<ProductResponse>>>

    // ========== Ventas ==========

    @GET("sales")
    suspend fun getAllSales(): Response<ApiResponse<List<SaleResponse>>>

    @GET("sales/{id}")
    suspend fun getSale(@Path("id") id: String): Response<ApiResponse<SaleResponse>>

    @POST("sales")
    suspend fun createSale(@Body sale: SaleRequest): Response<ApiResponse<SaleResponse>>

    @GET("sales/range")
    suspend fun getSalesByDateRange(
        @Query("start") startDate: Long,
        @Query("end") endDate: Long
    ): Response<ApiResponse<List<SaleResponse>>>

    @GET("sales/today")
    suspend fun getTodaySales(): Response<ApiResponse<List<SaleResponse>>>

    @GET("sales/stats")
    suspend fun getSalesStats(
        @Query("start") startDate: Long,
        @Query("end") endDate: Long
    ): Response<ApiResponse<SalesStatsDto>>

    // ========== Sincronización ==========

    @POST("sync")
    suspend fun syncData(@Body request: SyncRequest): Response<SyncResponse>

    @GET("sync/changes")
    suspend fun getChanges(@Query("since") lastSync: Long): Response<SyncResponse>

    // ========== Backup ==========

    @POST("backup")
    suspend fun createBackup(@Body request: BackupRequest): Response<ApiResponse<BackupInfo>>

    @GET("backup/latest")
    suspend fun getLatestBackup(@Query("device_id") deviceId: String): Response<ApiResponse<BackupData>>

    @GET("backup/list")
    suspend fun getBackupList(): Response<ApiResponse<List<BackupInfo>>>

    @GET("backup/{id}")
    suspend fun getBackup(@Path("id") id: String): Response<ApiResponse<BackupData>>

    // ========== Usuarios (solo dueño) ==========

    @GET("users")
    suspend fun getAllUsers(): Response<ApiResponse<List<UserDto>>>

    @POST("users")
    suspend fun createUser(@Body user: CreateUserRequest): Response<ApiResponse<UserDto>>

    @PUT("users/{id}")
    suspend fun updateUser(
        @Path("id") id: String,
        @Body user: UpdateUserRequest
    ): Response<ApiResponse<UserDto>>

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: String): Response<ApiResponse<Unit>>

    // ========== Health Check ==========

    @GET("health")
    suspend fun healthCheck(): Response<ApiResponse<HealthStatus>>
}

// DTOs adicionales para la API

data class SalesStatsDto(
    val totalSales: Long,
    val totalProfit: Long,
    val salesCount: Int,
    val averageTicket: Long,
    val topProducts: List<TopProductDto>
)

data class TopProductDto(
    val productId: String,
    val productName: String,
    val soldQuantity: Int,
    val totalRevenue: Long
)

data class BackupInfo(
    val id: String,
    val deviceId: String,
    val timestamp: Long,
    val size: Long,
    val productCount: Int,
    val salesCount: Int
)

data class CreateUserRequest(
    val username: String,
    val password: String,
    val name: String,
    val role: String
)

data class UpdateUserRequest(
    val name: String?,
    val role: String?,
    val password: String?,
    val isActive: Boolean?
)

data class HealthStatus(
    val status: String,
    val database: String,
    val timestamp: Long
)
