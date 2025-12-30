package com.inventario.py.data.local.entity

import com.inventario.py.utils.CurrencyUtils

/**
 * ARCHIVO DE TIPOS Y EXTENSIONES - CORREGIDO
 */

// ==================== ENUMS ====================

enum class UserRole {
    OWNER,
    ADMIN,
    SELLER,
    VIEWER;
    
    companion object {
        val EMPLOYEE = SELLER // Alias para compatibilidad
    }
}

enum class PaymentMethod {
    CASH,
    CARD,
    TRANSFER,
    CREDIT,
    QR,
    MIXED;
    
    companion object {
        fun fromString(value: String): PaymentMethod {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                CASH
            }
        }
    }
}

enum class SaleStatus {
    PENDING,
    COMPLETED,
    CANCELLED,
    REFUNDED;
    
    companion object {
        fun fromString(value: String): SaleStatus {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                PENDING
            }
        }
    }
}

enum class StockFilter {
    ALL,
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK
}

enum class SortOption {
    NAME_ASC,
    NAME_DESC,
    PRICE_ASC,
    PRICE_DESC,
    STOCK_ASC,
    STOCK_DESC,
    DATE_ASC,
    DATE_DESC
}

enum class VariantType {
    COLOR,
    SIZE,
    CAPACITY,
    TYPE,
    QUALITY,
    CUSTOM
}

enum class DateFilter {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    THIS_YEAR,
    CUSTOM
}

enum class MovementType {
    IN,
    OUT,
    ADJUSTMENT,
    SALE,
    RETURN,
    TRANSFER;
    
    companion object {
        fun fromString(value: String): MovementType {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                ADJUSTMENT
            }
        }
    }
}

// ==================== SEALED CLASSES ====================

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val user: UserEntity) : LoginState()
    data class Error(val message: String) : LoginState()
}

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}

// ==================== DATA CLASSES ====================

data class ProductWithVariants(
    val product: ProductEntity,
    val variants: List<ProductVariantEntity> = emptyList()
) {
    val id: String get() = product.id
    val name: String get() = product.name
    val totalStock: Int
        get() = if (variants.isNotEmpty()) variants.sumOf { it.stock } else product.totalStock
    val hasVariants: Boolean get() = variants.isNotEmpty()
    val salePrice: Long get() = product.salePrice
    val purchasePrice: Long get() = product.purchasePrice
    val imageUrl: String? get() = product.imageUrl
    val barcode: String? get() = product.barcode
    val identifier: String get() = product.identifier
    val categoryId: String? get() = product.categoryId
    val lowStockThreshold: Int get() = product.lowStockThreshold
    val minStock: Int get() = product.lowStockThreshold
    val isActive: Boolean get() = product.isActive
}

data class SaleWithDetails(
    val sale: SaleEntity,
    val items: List<SaleItemEntity> = emptyList(),
    val seller: UserEntity? = null
) {
    val id: String get() = sale.id
    val itemCount: Int get() = items.sumOf { it.quantity }
    val totalAmount: Long get() = sale.total
    val createdAt: Long get() = sale.soldAt
    val discount: Long get() = sale.totalDiscount
    val paymentMethod: String get() = sale.paymentMethod
    val status: String get() = sale.status
}

data class SaleItem(
    val id: String,
    val productId: String,
    val productName: String,
    val variantInfo: String? = null,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long,
    val imageUrl: String? = null
)

data class ProductVariant(
    val id: String,
    val productId: String,
    val name: String,
    val sku: String? = null,
    val barcode: String? = null,
    val additionalPrice: Long = 0,
    val stock: Int = 0,
    val isActive: Boolean = true
) {
    val variantName: String get() = name
    val priceModifier: Long get() = additionalPrice
    val currentStock: Int get() = stock
}

data class StockMovement(
    val id: String,
    val productId: String,
    val variantId: String? = null,
    val movementType: MovementType,
    val quantity: Int,
    val previousStock: Int,
    val newStock: Int,
    val reason: String? = null,
    val referenceId: String? = null,
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class ReportsState(
    val isLoading: Boolean = false,
    val totalSales: Long = 0,
    val totalTransactions: Int = 0,
    val totalProfit: Long = 0,
    val totalExpenses: Long = 0,
    val cashSales: Long = 0,
    val cardSales: Long = 0,
    val transferSales: Long = 0,
    val topProducts: List<TopProduct> = emptyList(),
    val dailySales: Map<String, Long> = emptyMap(),
    val error: String? = null
)

data class TopProduct(
    val productId: String,
    val productName: String,
    val imageUrl: String? = null,
    val totalSold: Int = 0,
    val totalRevenue: Long = 0,
    val percentage: Float = 0f
)

// ==================== EXTENSION PROPERTIES ====================

// Para ProductVariantEntity
val ProductVariantEntity.priceModifier: Long get() = this.additionalPrice
val ProductVariantEntity.currentStock: Int get() = this.stock
val ProductVariantEntity.variantName: String get() = this.name

// Para SaleEntity  
val SaleEntity.totalAmount: Long get() = this.total
val SaleEntity.createdAt: Long get() = this.soldAt
val SaleEntity.discount: Long get() = this.totalDiscount

// Para StockMovementEntity
val StockMovementEntity.movementType: MovementType 
    get() = MovementType.fromString(this.type)

// Para UserEntity
val UserEntity.name: String get() = this.fullName

// Para ProductEntity
val ProductEntity.category: String? get() = this.categoryId
val ProductEntity.minStock: Int get() = this.lowStockThreshold

// ==================== FORMAT EXTENSIONS ====================

/**
 * Extensiones de formato para usar en todo el proyecto
 */
fun Long.formatGuarani(): String = CurrencyUtils.formatGuarani(this)
fun Int.formatGuarani(): String = CurrencyUtils.formatGuarani(this)
fun Double.formatGuarani(): String = CurrencyUtils.formatGuarani(this)

// ==================== CONVERSION FUNCTIONS ====================

fun SaleItemEntity.toSaleItem() = SaleItem(
    id = this.id,
    productId = this.productId,
    productName = this.productName,
    variantInfo = this.variantDescription,
    quantity = this.quantity,
    unitPrice = this.unitPrice,
    subtotal = this.subtotal,
    imageUrl = this.productImageUrl
)

fun ProductVariantEntity.toProductVariant() = ProductVariant(
    id = this.id,
    productId = this.productId,
    name = this.name,
    sku = this.sku,
    barcode = this.barcode,
    additionalPrice = this.additionalPrice,
    stock = this.stock,
    isActive = this.isActive
)

fun StockMovementEntity.toStockMovement() = StockMovement(
    id = this.id,
    productId = this.productId,
    variantId = this.variantId,
    movementType = this.movementType,
    quantity = this.quantity,
    previousStock = this.previousStock,
    newStock = this.newStock,
    reason = this.reason,
    referenceId = this.referenceId,
    createdBy = this.createdBy,
    createdAt = this.createdAt
)

// Lista conversions
fun List<StockMovementEntity>.toStockMovements() = this.map { it.toStockMovement() }
fun List<ProductVariantEntity>.toProductVariants() = this.map { it.toProductVariant() }
fun List<SaleItemEntity>.toSaleItems() = this.map { it.toSaleItem() }

// ==================== HELPER FUNCTIONS ====================

fun String.toPaymentMethod(): PaymentMethod = PaymentMethod.fromString(this)
fun String.toSaleStatus(): SaleStatus = SaleStatus.fromString(this)
fun String.toMovementType(): MovementType = MovementType.fromString(this)
