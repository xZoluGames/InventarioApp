package com.inventario.py.domain.model

import java.util.Date

// ========== Enums ==========

enum class UserRole {
    OWNER,
    MANAGER,
    EMPLOYEE
}

enum class PaymentMethod {
    CASH,
    CARD,
    QR,
    TRANSFER
}

enum class HistoryAction {
    CREATED,
    UPDATED,
    DELETED,
    STOCK_ADJUSTED,
    PRICE_CHANGED,
    SOLD
}

enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

enum class ReportPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    ANNUAL,
    CUSTOM
}

// ========== Product Models ==========

data class Product(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val barcode: String? = null,
    val identifier: String? = null,
    val imagePath: String? = null,
    val price: Long,
    val cost: Long = 0,
    val stock: Int,
    val minStock: Int = 5,
    val supplier: String? = null,
    val quality: String? = null,
    val category: String? = null,
    val colors: List<String> = emptyList(),
    val types: List<ProductVariant> = emptyList(),
    val capacities: List<ProductVariant> = emptyList(),
    val dateAdded: Date = Date(),
    val lastModified: Date = Date(),
    val isSynced: Boolean = false
) {
    val isLowStock: Boolean get() = stock in 1..minStock
    val isOutOfStock: Boolean get() = stock <= 0
    val isInStock: Boolean get() = stock > minStock
    val profit: Long get() = price - cost
    val profitMargin: Float get() = if (cost > 0) ((price - cost).toFloat() / cost * 100) else 0f
    val stockValue: Long get() = price * stock
    val costValue: Long get() = cost * stock
}

data class ProductVariant(
    val name: String,
    val additionalPrice: Long = 0
)

// ========== Sale Models ==========

data class Sale(
    val id: Long = 0,
    val items: List<SaleItem> = emptyList(),
    val total: Long,
    val totalCost: Long = 0,
    val profit: Long = 0,
    val date: Date = Date(),
    val employeeId: Long,
    val employeeName: String,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val notes: String? = null,
    val isSynced: Boolean = false
) {
    val totalItems: Int get() = items.sumOf { it.quantity }
}

data class SaleItem(
    val id: Long = 0,
    val saleId: Long = 0,
    val productId: Long,
    val productName: String,
    val barcode: String? = null,
    val unitPrice: Long,
    val unitCost: Long = 0,
    val quantity: Int,
    val selectedType: String? = null,
    val selectedCapacity: String? = null,
    val selectedColor: String? = null
) {
    val subtotal: Long get() = unitPrice * quantity
    val itemCost: Long get() = unitCost * quantity
    val itemProfit: Long get() = subtotal - itemCost
}

// ========== Cart Models ==========

data class CartItem(
    val id: Long = 0,
    val productId: Long,
    val productName: String,
    val barcode: String? = null,
    val imagePath: String? = null,
    val unitPrice: Long,
    val unitCost: Long = 0,
    val quantity: Int,
    val maxQuantity: Int,
    val selectedType: String? = null,
    val selectedCapacity: String? = null,
    val selectedColor: String? = null
) {
    val subtotal: Long get() = unitPrice * quantity
    val totalCost: Long get() = unitCost * quantity
}

// ========== User Models ==========

data class User(
    val id: Long = 0,
    val username: String,
    val name: String,
    val role: UserRole = UserRole.EMPLOYEE,
    val isActive: Boolean = true,
    val lastLogin: Date? = null,
    val createdAt: Date = Date()
) {
    val isOwner: Boolean get() = role == UserRole.OWNER
    val isManager: Boolean get() = role == UserRole.MANAGER || role == UserRole.OWNER
    val canViewCosts: Boolean get() = role != UserRole.EMPLOYEE
    val canEditProducts: Boolean get() = role != UserRole.EMPLOYEE
    val canManageUsers: Boolean get() = role == UserRole.OWNER
    val canAccessBackup: Boolean get() = role == UserRole.OWNER
}

// ========== History Models ==========

data class ProductHistory(
    val id: Long = 0,
    val productId: Long,
    val action: HistoryAction,
    val description: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val userId: Long,
    val userName: String = "",
    val date: Date = Date()
)

// ========== Report Models ==========

data class SalesReport(
    val period: ReportPeriod,
    val startDate: Date,
    val endDate: Date,
    val totalSales: Long,
    val totalCost: Long,
    val totalProfit: Long,
    val salesCount: Int,
    val averageTicket: Long,
    val topProducts: List<ProductStat> = emptyList(),
    val dailyBreakdown: List<DailyStat> = emptyList()
)

data class ProductStat(
    val productId: Long,
    val productName: String,
    val quantitySold: Int,
    val revenue: Long,
    val profit: Long
)

data class DailyStat(
    val date: Date,
    val sales: Long,
    val profit: Long,
    val count: Int
)

// ========== Dashboard Models ==========

data class DashboardStats(
    val todaySalesCount: Int = 0,
    val todayRevenue: Long = 0,
    val todayProfit: Long = 0,
    val monthSalesCount: Int = 0,
    val monthRevenue: Long = 0,
    val monthProfit: Long = 0,
    val totalProducts: Int = 0,
    val inStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val lowStockCount: Int = 0
)

// ========== Settings Models ==========

data class AppSettings(
    val serverUrl: String = "",
    val autoSync: Boolean = true,
    val syncInterval: Int = 15,
    val lowStockAlerts: Boolean = true,
    val lowStockThreshold: Int = 5,
    val theme: AppTheme = AppTheme.SYSTEM,
    val language: String = "es"
)

// ========== Backup Models ==========

data class BackupFile(
    val name: String,
    val path: String,
    val size: Long,
    val date: Date
) {
    val formattedSize: String
        get() = when {
            size >= 1_000_000 -> "%.1f MB".format(size / 1_000_000.0)
            size >= 1_000 -> "%.1f KB".format(size / 1_000.0)
            else -> "$size bytes"
        }
}

// ========== Form State Models ==========

data class LoginFormState(
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

data class ProductFormState(
    val id: Long? = null,
    val name: String = "",
    val description: String = "",
    val barcode: String = "",
    val identifier: String = "",
    val imagePath: String? = null,
    val price: String = "",
    val cost: String = "",
    val stock: String = "",
    val minStock: String = "5",
    val supplier: String = "",
    val quality: String = "",
    val category: String = "",
    val colors: List<String> = emptyList(),
    val types: List<ProductVariant> = emptyList(),
    val capacities: List<ProductVariant> = emptyList(),
    val nameError: String? = null,
    val priceError: String? = null,
    val stockError: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false
)

data class UserFormState(
    val id: Long? = null,
    val username: String = "",
    val name: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val role: UserRole = UserRole.EMPLOYEE,
    val isActive: Boolean = true,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false
)

// ========== Search State ==========

data class SearchState(
    val query: String = "",
    val isLowStockFilter: Boolean = false,
    val isInStockFilter: Boolean = false,
    val isOutOfStockFilter: Boolean = false
) {
    val hasActiveFilter: Boolean get() = isLowStockFilter || isInStockFilter || isOutOfStockFilter
    val activeFilterName: String? get() = when {
        isLowStockFilter -> "Stock Bajo"
        isInStockFilter -> "En Stock"
        isOutOfStockFilter -> "Sin Stock"
        else -> null
    }
}
