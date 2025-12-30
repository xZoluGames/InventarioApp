package com.inventario.py.data.remote.dto

import com.inventario.py.data.local.entity.*

// ==================== AUTENTICACIÓN ====================

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val success: Boolean,
    val message: String?,
    val data: AuthData?
)

data class AuthData(
    val token: String,
    val refreshToken: String,
    val user: UserDto,
    val expiresIn: Long
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class RefreshTokenResponse(
    val success: Boolean,
    val token: String?,
    val refreshToken: String?,
    val expiresIn: Long?
)

// ==================== USUARIO ====================

data class UserDto(
    val id: String,
    val username: String,
    val email: String,
    val fullName: String,
    val role: String,
    val isActive: Boolean,
    val profileImageUrl: String?,
    val phoneNumber: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastLoginAt: Long?
) {
    fun toEntity(): UserEntity = UserEntity(
        id = id,
        username = username,
        email = email,
        passwordHash = "",
        fullName = fullName,
        role = role,
        isActive = isActive,
        profileImageUrl = profileImageUrl,
        phoneNumber = phoneNumber,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastLoginAt = lastLoginAt,
        syncStatus = UserEntity.SYNC_STATUS_SYNCED
    )
}

data class CreateUserRequest(
    val username: String,
    val email: String,
    val password: String,
    val fullName: String,
    val role: String,
    val phoneNumber: String?
)

// ==================== PRODUCTO ====================

data class ProductDto(
    val id: String,
    val name: String,
    val description: String?,
    val barcode: String?,
    val identifier: String,
    val imageUrl: String?,
    val categoryId: String?,
    val subcategoryId: String?,
    val totalStock: Int,
    val minStockAlert: Int,
    val isStockAlertEnabled: Boolean,
    val salePrice: Long,
    val purchasePrice: Long,
    val supplierId: String?,
    val supplierName: String?,
    val quality: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val createdBy: String?,
    val variants: List<ProductVariantDto>?,
    val images: List<ProductImageDto>?
) {
    fun toEntity(): ProductEntity = ProductEntity(
        id = id,
        name = name,
        description = description,
        barcode = barcode,
        identifier = identifier,
        imageUrl = imageUrl,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        totalStock = totalStock,
        minStockAlert = minStockAlert,
        isStockAlertEnabled = isStockAlertEnabled,
        salePrice = salePrice,
        purchasePrice = purchasePrice,
        supplierId = supplierId,
        supplierName = supplierName,
        quality = quality,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        createdBy = createdBy,
        syncStatus = ProductEntity.SYNC_STATUS_SYNCED
    )
}

data class ProductVariantDto(
    val id: String,
    val productId: String,
    val variantType: String,
    val variantLabel: String,
    val variantValue: String,
    val stock: Int,
    val additionalPrice: Long,
    val barcode: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toEntity(): ProductVariantEntity = ProductVariantEntity(
        id = id,
        productId = productId,
        variantType = variantType,
        variantLabel = variantLabel,
        variantValue = variantValue,
        stock = stock,
        additionalPrice = additionalPrice,
        barcode = barcode,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = 0
    )
}

data class ProductImageDto(
    val id: String,
    val productId: String,
    val imageUrl: String?,
    val isPrimary: Boolean,
    val sortOrder: Int,
    val createdAt: Long
) {
    fun toEntity(): ProductImageEntity = ProductImageEntity(
        id = id,
        productId = productId,
        imageUrl = imageUrl,
        isPrimary = isPrimary,
        sortOrder = sortOrder,
        createdAt = createdAt,
        syncStatus = 0
    )
}

data class CreateProductRequest(
    val name: String,
    val description: String?,
    val barcode: String?,
    val identifier: String,
    val categoryId: String?,
    val totalStock: Int,
    val minStockAlert: Int,
    val salePrice: Long,
    val purchasePrice: Long,
    val supplierId: String?,
    val supplierName: String?,
    val quality: String?,
    val variants: List<CreateVariantRequest>?
)

data class CreateVariantRequest(
    val variantType: String,
    val variantLabel: String,
    val variantValue: String,
    val stock: Int,
    val additionalPrice: Long,
    val barcode: String?
)

data class UpdateStockRequest(
    val productId: String,
    val variantId: String?,
    val quantity: Int,
    val movementType: String, // "IN", "OUT", "ADJUSTMENT"
    val reason: String?
)

// ==================== CATEGORÍA ====================

data class CategoryDto(
    val id: String,
    val name: String,
    val description: String?,
    val parentId: String?,
    val iconName: String?,
    val colorHex: String?,
    val sortOrder: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toEntity(): CategoryEntity = CategoryEntity(
        id = id,
        name = name,
        description = description,
        parentId = parentId,
        iconName = iconName,
        colorHex = colorHex,
        sortOrder = sortOrder,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = 0
    )
}

// ==================== PROVEEDOR ====================

data class SupplierDto(
    val id: String,
    val name: String,
    val contactName: String?,
    val phone: String?,
    val email: String?,
    val address: String?,
    val city: String?,
    val notes: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toEntity(): SupplierEntity = SupplierEntity(
        id = id,
        name = name,
        contactName = contactName,
        phone = phone,
        email = email,
        address = address,
        city = city,
        notes = notes,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
        syncStatus = 0
    )
}

// ==================== VENTA ====================

data class SaleDto(
    val id: String,
    val saleNumber: String,
    val customerId: String?,
    val customerName: String?,
    val subtotal: Long,
    val totalDiscount: Long,
    val taxAmount: Long,
    val total: Long,
    val paymentMethod: String,
    val amountPaid: Long,
    val changeAmount: Long,
    val status: String,
    val notes: String?,
    val soldBy: String,
    val soldByName: String,
    val soldAt: Long,
    val cancelledAt: Long?,
    val cancelledBy: String?,
    val cancellationReason: String?,
    val items: List<SaleItemDto>?
) {
    fun toEntity(): SaleEntity = SaleEntity(
        id = id,
        saleNumber = saleNumber,
        customerId = customerId,
        customerName = customerName,
        subtotal = subtotal,
        totalDiscount = totalDiscount,
        taxAmount = taxAmount,
        total = total,
        paymentMethod = paymentMethod,
        amountPaid = amountPaid,
        changeAmount = changeAmount,
        status = status,
        notes = notes,
        soldBy = soldBy,
        soldByName = soldByName,
        soldAt = soldAt,
        cancelledAt = cancelledAt,
        cancelledBy = cancelledBy,
        cancellationReason = cancellationReason,
        syncStatus = 0
    )
}

data class SaleItemDto(
    val id: String,
    val saleId: String,
    val productId: String,
    val productName: String,
    val productIdentifier: String,
    val variantId: String?,
    val variantDescription: String?,
    val quantity: Int,
    val unitPrice: Long,
    val purchasePrice: Long,
    val discount: Long,
    val subtotal: Long,
    val productImageUrl: String?,
    val barcode: String?
) {
    fun toEntity(): SaleItemEntity = SaleItemEntity(
        id = id,
        saleId = saleId,
        productId = productId,
        productName = productName,
        productIdentifier = productIdentifier,
        variantId = variantId,
        variantDescription = variantDescription,
        quantity = quantity,
        unitPrice = unitPrice,
        purchasePrice = purchasePrice,
        discount = discount,
        subtotal = subtotal,
        productImageUrl = productImageUrl,
        barcode = barcode
    )
}

data class CreateSaleRequest(
    val customerId: String?,
    val customerName: String?,
    val items: List<CreateSaleItemRequest>,
    val totalDiscount: Long,
    val paymentMethod: String,
    val amountPaid: Long,
    val notes: String?
)

data class CreateSaleItemRequest(
    val productId: String,
    val variantId: String?,
    val quantity: Int,
    val unitPrice: Long,
    val discount: Long
)

// ==================== SINCRONIZACIÓN ====================

data class SyncRequest(
    val lastSyncAt: Long,
    val products: List<ProductDto>?,
    val variants: List<ProductVariantDto>?,
    val sales: List<SaleDto>?,
    val stockMovements: List<StockMovementDto>?
)

data class SyncResponse(
    val success: Boolean,
    val message: String?,
    val serverTime: Long,
    val products: List<ProductDto>?,
    val categories: List<CategoryDto>?,
    val suppliers: List<SupplierDto>?,
    val sales: List<SaleDto>?,
    val deletedProductIds: List<String>?,
    val deletedSaleIds: List<String>?
)

data class StockMovementDto(
    val id: String,
    val productId: String,
    val variantId: String?,
    val movementType: String,
    val quantity: Int,
    val previousStock: Int,
    val newStock: Int,
    val reason: String?,
    val referenceId: String?,
    val referenceType: String?,
    val createdAt: Long,
    val createdBy: String
)

// ==================== RESPUESTAS GENÉRICAS ====================

data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?
)

data class PaginatedResponse<T>(
    val success: Boolean,
    val data: List<T>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int
)

// ==================== ESTADÍSTICAS ====================

data class DashboardStatsDto(
    val todaySales: Long,
    val todayTransactions: Int,
    val monthSales: Long,
    val monthTransactions: Int,
    val yearSales: Long,
    val yearTransactions: Int,
    val totalProducts: Int,
    val lowStockProducts: Int,
    val outOfStockProducts: Int,
    val inventoryValue: Long,
    val todayProfit: Long,
    val monthProfit: Long,
    val topSellingProducts: List<TopProductDto>?
)

data class TopProductDto(
    val productId: String,
    val productName: String,
    val totalSold: Int,
    val totalRevenue: Long
)

data class SalesReportDto(
    val startDate: Long,
    val endDate: Long,
    val totalSales: Long,
    val totalTransactions: Int,
    val totalProfit: Long,
    val averageTicket: Long,
    val salesByPaymentMethod: Map<String, Long>,
    val salesByDay: List<DailySalesDto>,
    val topProducts: List<TopProductDto>
)

data class DailySalesDto(
    val date: String,
    val total: Long,
    val transactions: Int,
    val profit: Long
)
