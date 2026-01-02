package com.inventario.py.data.local.entity

import com.inventario.py.utils.CurrencyUtils

/**
 * ARCHIVO DE TIPOS Y EXTENSIONES - VERSIÓN CORREGIDA
 */

// ==================== ENUMS ====================

enum class UserRole {
    OWNER,
    ADMIN,
    SELLER,
    VIEWER;

    companion object {
        val EMPLOYEE = SELLER
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
    DATE_DESC;

    companion object {
        val entries = values().toList()
    }
}

enum class VariantType {
    COLOR,
    SIZE,
    CAPACITY,
    TYPE,
    QUALITY,
    CUSTOM;

    companion object {
        fun fromString(value: String): VariantType {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                CUSTOM
            }
        }
    }
}

enum class MovementType {
    IN,
    OUT,
    ADJUSTMENT,
    SALE,
    RETURN,
    CANCELLATION,
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
    val lowStockThreshold: Int get() = product.minStockAlert
    val minStock: Int get() = product.minStockAlert
    val isActive: Boolean get() = product.isActive
    val description: String? get() = product.description

    fun isLowStock(): Boolean = totalStock <= lowStockThreshold
    fun isOutOfStock(): Boolean = totalStock == 0
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
    val saleNumber: String get() = sale.saleNumber
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

    fun copyWith(
        variantName: String? = null,
        sku: String? = null,
        priceModifier: Long? = null,
        currentStock: Int? = null
    ): ProductVariant = copy(
        name = variantName ?: this.name,
        sku = sku ?: this.sku,
        additionalPrice = priceModifier ?: this.additionalPrice,
        stock = currentStock ?: this.stock
    )
}

data class StockMovement(
    val id: String,
    val productId: String,
    val variantId: String? = null,
    val movementType: String,
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
    val qrSales: Long = 0,
    val topProducts: List<TopProduct> = emptyList(),
    val dailySales: Map<String, Long> = emptyMap(),
    val paymentMethodBreakdown: Map<PaymentMethod, Long> = emptyMap(),
    val error: String? = null
) {
    // Propiedades calculadas para compatibilidad
    val averageSale: Long get() = if (totalTransactions > 0) totalSales / totalTransactions else 0
    val transactionCount: Int get() = totalTransactions

    // Breakdown de métodos de pago
    val cashTotal: Long get() = cashSales
    val cardTotal: Long get() = cardSales
    val transferTotal: Long get() = transferSales

    // Porcentajes de métodos de pago
    val cashPercentage: Float get() = if (totalSales > 0) (cashSales * 100f / totalSales) else 0f
    val cardPercentage: Float get() = if (totalSales > 0) (cardSales * 100f / totalSales) else 0f
    val transferPercentage: Float get() = if (totalSales > 0) (transferSales * 100f / totalSales) else 0f

    // Información de inventario (deberían venir del ViewModel)
    var totalProducts: Int = 0
    var lowStockCount: Int = 0
    var outOfStockCount: Int = 0
    var inventoryValue: Long = 0
}

data class TopProduct(
    val productId: String,
    val productName: String,
    val imageUrl: String? = null,
    val totalSold: Int = 0,
    val totalRevenue: Long = 0,
    val percentage: Float = 0f
)

data class CartItemWithProduct(
    val cartItem: CartItemEntity,
    val product: ProductEntity,
    val variant: ProductVariantEntity? = null
) {
    val id: String get() = cartItem.id
    val productId: String get() = cartItem.productId
    val productName: String get() = cartItem.productName
    val quantity: Int get() = cartItem.quantity
    val unitPrice: Long get() = cartItem.unitPrice
    val subtotal: Long get() = cartItem.subtotal
    val imageUrl: String? get() = cartItem.imageUrl
    val variantDescription: String? get() = cartItem.variantDescription
    val currentStock: Int get() = variant?.stock ?: product.totalStock
}

// ==================== EXTENSION PROPERTIES ====================

// Para ProductEntity
val ProductEntity.lowStockThreshold: Int get() = this.minStockAlert
val ProductEntity.category: String? get() = this.categoryId
val ProductEntity.minStock: Int get() = this.minStockAlert
val ProductEntity.currentStock: Int get() = this.totalStock

// Para ProductVariantEntity
val ProductVariantEntity.priceModifier: Long get() = this.additionalPrice
val ProductVariantEntity.currentStock: Int get() = this.stock
val ProductVariantEntity.variantName: String get() = this.variantValue
val ProductVariantEntity.name: String get() = this.variantValue
val ProductVariantEntity.sku: String? get() = this.barcode
val ProductVariantEntity.type: String get() = this.variantType

// Para SaleEntity
val SaleEntity.totalAmount: Long get() = this.total
val SaleEntity.createdAt: Long get() = this.soldAt
val SaleEntity.discount: Long get() = this.totalDiscount

// Para StockMovementEntity
fun StockMovementEntity.getMovementType(): MovementType = MovementType.fromString(this.movementType)

// Para UserEntity
val UserEntity.name: String get() = this.fullName

// Para CartItemEntity
val CartItemEntity.currentStock: Int get() = 0 // Se debe obtener del producto

// ==================== FORMAT EXTENSIONS ====================

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

fun ProductVariantEntity.toProductVariant(): ProductVariant = ProductVariant(
    id = this.id,
    productId = this.productId,
    name = this.variantValue,
    sku = this.barcode,
    barcode = this.barcode,
    additionalPrice = this.additionalPrice,
    stock = this.stock,
    isActive = this.isActive)

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
fun List<ProductVariantEntity>.toProductVariants(): List<ProductVariant> =
    this.map { it.toProductVariant() }
fun List<SaleItemEntity>.toSaleItems() = this.map { it.toSaleItem() }

// ==================== HELPER FUNCTIONS ====================

fun String.toPaymentMethod(): PaymentMethod = PaymentMethod.fromString(this)
fun String.toSaleStatus(): SaleStatus = SaleStatus.fromString(this)
fun String.toMovementType(): MovementType = MovementType.fromString(this)
fun String.toVariantType(): VariantType = VariantType.fromString(this)

// ==================== TYPE ALIASES ====================

typealias ProductId = String
typealias VariantId = String
typealias SaleId = String
typealias UserId = String
/**
 * EXTENSIONES ADICIONALES PARA AGREGAR A MissingTypes.kt
 *
 * Copiar el contenido de este archivo y pegarlo AL FINAL del archivo
 * MissingTypes.kt existente en:
 * app/src/main/java/com/inventario/py/data/local/entity/MissingTypes.kt
 */

// ==================== EXTENSIONES ADICIONALES PARA ProductEntity ====================

// Alias para compatibilidad - costPrice es lo mismo que purchasePrice
val ProductEntity.costPrice: Long get() = this.purchasePrice

// supplier devuelve el nombre del proveedor
val ProductEntity.supplier: String? get() = this.supplierName

// ==================== DATA CLASS ProductVariant MEJORADA ====================

/**
 * Si ProductVariant ya existe en MissingTypes.kt, reemplazarla con esta versión
 * que incluye el método copyWith para compatibilidad con ProductDetailFragment
 */



// ==================== EXTENSIONES PARA ProductWithVariants ====================

// Agregar propiedad supplier a ProductWithVariants
val ProductWithVariants.supplier: String? get() = product.supplierName
val ProductWithVariants.costPrice: Long get() = product.purchasePrice

// ==================== DATE RANGE ENUM (si no existe) ====================

/**
 * Enum para rangos de fecha en reportes
 * Agregar solo si no existe DateRange en el proyecto
 */
enum class DateRange {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    WEEK,      // Alias para THIS_WEEK
    THIS_MONTH,
    MONTH,     // Alias para THIS_MONTH
    THIS_YEAR,
    YEAR,      // Alias para THIS_YEAR
    CUSTOM,
    ALL;

    companion object {
        fun fromString(value: String): DateRange {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                TODAY
            }
        }
    }
}

