package com.inventario.py.data.local.entity

/**
 * ARCHIVO DE TIPOS FALTANTES
 * Contiene todos los enums, sealed classes y data classes que el proyecto necesita
 */

// ==================== ENUMS ====================

enum class UserRole {
    OWNER,
    ADMIN,
    SELLER,
    VIEWER
}

enum class PaymentMethod {
    CASH,
    CARD,
    TRANSFER,
    CREDIT,
    QR,
    MIXED
}

enum class SaleStatus {
    PENDING,
    COMPLETED,
    CANCELLED,
    REFUNDED
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

/**
 * Producto con sus variantes
 */
data class ProductWithVariants(
    val product: ProductEntity,
    val variants: List<ProductVariantEntity> = emptyList()
) {
    val totalStock: Int
        get() = if (variants.isNotEmpty()) variants.sumOf { it.stock } else product.totalStock
    
    val hasVariants: Boolean
        get() = variants.isNotEmpty()
}

/**
 * Venta con detalles completos
 */
data class SaleWithDetails(
    val sale: SaleEntity,
    val items: List<SaleItemEntity> = emptyList(),
    val seller: UserEntity? = null
) {
    val itemCount: Int
        get() = items.sumOf { it.quantity }
    
    val totalAmount: Long
        get() = sale.total
    
    val createdAt: Long
        get() = sale.soldAt
    
    val discount: Long
        get() = sale.totalDiscount
}

/**
 * Item de venta simplificado para UI
 */
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

/**
 * Variante de producto simplificada para UI
 */
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
    // Aliases para compatibilidad
    val variantName: String get() = name
    val priceModifier: Long get() = additionalPrice
    val currentStock: Int get() = stock
}

/**
 * Movimiento de stock
 */
data class StockMovement(
    val id: String,
    val productId: String,
    val variantId: String? = null,
    val type: MovementType,
    val quantity: Int,
    val previousStock: Int,
    val newStock: Int,
    val reason: String? = null,
    val referenceId: String? = null,
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Estado de reportes
 */
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

// ==================== EXTENSION FUNCTIONS ====================

/**
 * Extensiones para ProductVariantEntity
 */
val ProductVariantEntity.priceModifier: Long
    get() = this.additionalPrice

val ProductVariantEntity.currentStock: Int
    get() = this.stock

val ProductVariantEntity.variantName: String
    get() = this.name

/**
 * Extensiones para SaleEntity
 */
val SaleEntity.totalAmount: Long
    get() = this.total

val SaleEntity.createdAt: Long
    get() = this.soldAt

val SaleEntity.discount: Long
    get() = this.totalDiscount

/**
 * Convertir SaleItemEntity a SaleItem
 */
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

/**
 * Convertir ProductVariantEntity a ProductVariant
 */
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
