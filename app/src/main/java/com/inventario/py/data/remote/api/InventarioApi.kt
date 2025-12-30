package com.inventario.py.data.remote.api

import com.inventario.py.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface InventarioApi {
    
    // ==================== AUTENTICACIÓN ====================
    
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
    
    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>
    
    @POST("auth/logout")
    suspend fun logout(): Response<ApiResponse<Unit>>
    
    @GET("auth/me")
    suspend fun getCurrentUser(): Response<ApiResponse<UserDto>>
    
    @PUT("auth/password")
    suspend fun changePassword(
        @Body request: Map<String, String>
    ): Response<ApiResponse<Unit>>
    
    // ==================== USUARIOS ====================
    
    @GET("users")
    suspend fun getUsers(): Response<ApiResponse<List<UserDto>>>
    
    @GET("users/{id}")
    suspend fun getUserById(@Path("id") id: String): Response<ApiResponse<UserDto>>
    
    @POST("users")
    suspend fun createUser(@Body request: CreateUserRequest): Response<ApiResponse<UserDto>>
    
    @PUT("users/{id}")
    suspend fun updateUser(
        @Path("id") id: String,
        @Body user: UserDto
    ): Response<ApiResponse<UserDto>>
    
    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: String): Response<ApiResponse<Unit>>
    
    // ==================== PRODUCTOS ====================
    
    @GET("products")
    suspend fun getProducts(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("category") categoryId: String? = null,
        @Query("search") search: String? = null,
        @Query("lowStock") lowStock: Boolean? = null
    ): Response<PaginatedResponse<ProductDto>>
    
    @GET("products/{id}")
    suspend fun getProductById(@Path("id") id: String): Response<ApiResponse<ProductDto>>
    
    @GET("products/barcode/{barcode}")
    suspend fun getProductByBarcode(@Path("barcode") barcode: String): Response<ApiResponse<ProductDto>>
    
    @GET("products/identifier/{identifier}")
    suspend fun getProductByIdentifier(@Path("identifier") identifier: String): Response<ApiResponse<ProductDto>>
    
    @POST("products")
    suspend fun createProduct(@Body request: CreateProductRequest): Response<ApiResponse<ProductDto>>
    
    @PUT("products/{id}")
    suspend fun updateProduct(
        @Path("id") id: String,
        @Body product: ProductDto
    ): Response<ApiResponse<ProductDto>>
    
    @DELETE("products/{id}")
    suspend fun deleteProduct(@Path("id") id: String): Response<ApiResponse<Unit>>
    
    @POST("products/{id}/stock")
    suspend fun updateStock(
        @Path("id") id: String,
        @Body request: UpdateStockRequest
    ): Response<ApiResponse<ProductDto>>
    
    @Multipart
    @POST("products/{id}/image")
    suspend fun uploadProductImage(
        @Path("id") id: String,
        @Part image: MultipartBody.Part
    ): Response<ApiResponse<String>>
    
    // ==================== VARIANTES ====================
    
    @GET("products/{productId}/variants")
    suspend fun getVariants(@Path("productId") productId: String): Response<ApiResponse<List<ProductVariantDto>>>
    
    @POST("products/{productId}/variants")
    suspend fun createVariant(
        @Path("productId") productId: String,
        @Body request: CreateVariantRequest
    ): Response<ApiResponse<ProductVariantDto>>
    
    @PUT("variants/{id}")
    suspend fun updateVariant(
        @Path("id") id: String,
        @Body variant: ProductVariantDto
    ): Response<ApiResponse<ProductVariantDto>>
    
    @DELETE("variants/{id}")
    suspend fun deleteVariant(@Path("id") id: String): Response<ApiResponse<Unit>>
    
    // ==================== CATEGORÍAS ====================
    
    @GET("categories")
    suspend fun getCategories(): Response<ApiResponse<List<CategoryDto>>>
    
    @POST("categories")
    suspend fun createCategory(@Body category: CategoryDto): Response<ApiResponse<CategoryDto>>
    
    @PUT("categories/{id}")
    suspend fun updateCategory(
        @Path("id") id: String,
        @Body category: CategoryDto
    ): Response<ApiResponse<CategoryDto>>
    
    @DELETE("categories/{id}")
    suspend fun deleteCategory(@Path("id") id: String): Response<ApiResponse<Unit>>
    
    // ==================== PROVEEDORES ====================
    
    @GET("suppliers")
    suspend fun getSuppliers(): Response<ApiResponse<List<SupplierDto>>>
    
    @POST("suppliers")
    suspend fun createSupplier(@Body supplier: SupplierDto): Response<ApiResponse<SupplierDto>>
    
    @PUT("suppliers/{id}")
    suspend fun updateSupplier(
        @Path("id") id: String,
        @Body supplier: SupplierDto
    ): Response<ApiResponse<SupplierDto>>
    
    @DELETE("suppliers/{id}")
    suspend fun deleteSupplier(@Path("id") id: String): Response<ApiResponse<Unit>>
    
    // ==================== VENTAS ====================
    
    @GET("sales")
    suspend fun getSales(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("startDate") startDate: Long? = null,
        @Query("endDate") endDate: Long? = null,
        @Query("status") status: String? = null
    ): Response<PaginatedResponse<SaleDto>>
    
    @GET("sales/{id}")
    suspend fun getSaleById(@Path("id") id: String): Response<ApiResponse<SaleDto>>
    
    @POST("sales")
    suspend fun createSale(@Body request: CreateSaleRequest): Response<ApiResponse<SaleDto>>
    
    @POST("sales/{id}/cancel")
    suspend fun cancelSale(
        @Path("id") id: String,
        @Body reason: Map<String, String>
    ): Response<ApiResponse<SaleDto>>
    
    // ==================== ESTADÍSTICAS ====================
    
    @GET("stats/dashboard")
    suspend fun getDashboardStats(): Response<ApiResponse<DashboardStatsDto>>
    
    @GET("stats/sales-report")
    suspend fun getSalesReport(
        @Query("startDate") startDate: Long,
        @Query("endDate") endDate: Long
    ): Response<ApiResponse<SalesReportDto>>
    
    @GET("stats/product/{productId}")
    suspend fun getProductStats(@Path("productId") productId: String): Response<ApiResponse<Map<String, Any>>>
    
    // ==================== SINCRONIZACIÓN ====================
    
    @POST("sync")
    suspend fun syncData(@Body request: SyncRequest): Response<SyncResponse>
    
    @GET("sync/changes")
    suspend fun getChanges(
        @Query("since") since: Long
    ): Response<SyncResponse>
    
    // ==================== BACKUP ====================
    
    @GET("backup/export")
    suspend fun exportData(): Response<ApiResponse<String>> // Returns download URL
    
    @Multipart
    @POST("backup/import")
    suspend fun importData(
        @Part file: MultipartBody.Part
    ): Response<ApiResponse<Unit>>
}
