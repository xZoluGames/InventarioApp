package com.inventario.py.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Carrito de compras temporal
 */
@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey
    val id: String,
    val productId: String,
    val productName: String,
    val variantId: String? = null,
    val variantDescription: String? = null, // "Rojo - 64GB"
    val quantity: Int = 1,
    val unitPrice: Long, // Precio unitario en Gs.
    val discount: Long = 0, // Descuento aplicado
    val subtotal: Long, // (unitPrice * quantity) - discount
    val imageUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val addedBy: String // ID del usuario que agregó
)

/**
 * Venta realizada
 */
@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey
    val id: String,
    val saleNumber: String, // Número de venta secuencial
    val customerId: String? = null,
    val customerName: String? = null,
    
    // Totales
    val subtotal: Long, // Suma de items antes de descuentos
    val totalDiscount: Long = 0,
    val taxAmount: Long = 0, // IVA si aplica
    val total: Long, // Total final
    
    // Pago
    val paymentMethod: String = PAYMENT_CASH, // "CASH", "CARD", "TRANSFER", "CREDIT"
    val amountPaid: Long,
    val changeAmount: Long = 0, // Vuelto
    
    // Estado
    val status: String = STATUS_COMPLETED, // "COMPLETED", "CANCELLED", "REFUNDED"
    val notes: String? = null,
    
    // Auditoría
    val soldBy: String, // ID del vendedor
    val soldByName: String,
    val soldAt: Long = System.currentTimeMillis(),
    val cancelledAt: Long? = null,
    val cancelledBy: String? = null,
    val cancellationReason: String? = null,
    
    // Sincronización
    val syncStatus: Int = 0,
    val lastSyncAt: Long? = null
) {
    companion object {
        const val PAYMENT_CASH = "CASH"
        const val PAYMENT_CARD = "CARD"
        const val PAYMENT_TRANSFER = "TRANSFER"
        const val PAYMENT_CREDIT = "CREDIT"
        const val PAYMENT_MIXED = "MIXED"
        
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_REFUNDED = "REFUNDED"
        const val STATUS_PENDING = "PENDING"
    }
}

/**
 * Items de una venta
 */
@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("saleId"), Index("productId")]
)
data class SaleItemEntity(
    @PrimaryKey
    val id: String,
    val saleId: String,
    val productId: String,
    val productName: String,
    val productIdentifier: String,
    val variantId: String? = null,
    val variantDescription: String? = null,
    
    val quantity: Int,
    val unitPrice: Long, // Precio de venta al momento
    val purchasePrice: Long, // Costo al momento (para cálculo de ganancia)
    val discount: Long = 0,
    val subtotal: Long,
    
    // Para historial
    val productImageUrl: String? = null,
    val barcode: String? = null
)

/**
 * Historial de stock (movimientos)
 */
@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey
    val id: String,
    val productId: String,
    val variantId: String? = null,
    val movementType: String, // "IN", "OUT", "ADJUSTMENT", "RETURN"
    val quantity: Int, // Positivo para entrada, negativo para salida
    val previousStock: Int,
    val newStock: Int,
    val reason: String? = null,
    val referenceId: String? = null, // ID de venta, compra, etc.
    val referenceType: String? = null, // "SALE", "PURCHASE", "ADJUSTMENT"
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String,
    val syncStatus: Int = 0
) {
    companion object {
        const val TYPE_IN = "IN"
        const val TYPE_OUT = "OUT"
        const val TYPE_ADJUSTMENT = "ADJUSTMENT"
        const val TYPE_RETURN = "RETURN"
        const val TYPE_SALE = "SALE"
        
        const val REF_SALE = "SALE"
        const val REF_PURCHASE = "PURCHASE"
        const val REF_ADJUSTMENT = "ADJUSTMENT"
        const val REF_RETURN = "RETURN"
    }
}

/**
 * Resumen de caja diaria
 */
@Entity(tableName = "daily_cash_summary")
data class DailyCashSummaryEntity(
    @PrimaryKey
    val id: String,
    val date: String, // "2024-01-15"
    val openingCash: Long = 0, // Efectivo inicial
    val totalSales: Long = 0,
    val totalCashSales: Long = 0,
    val totalCardSales: Long = 0,
    val totalTransferSales: Long = 0,
    val totalCreditSales: Long = 0,
    val totalRefunds: Long = 0,
    val totalExpenses: Long = 0,
    val closingCash: Long = 0,
    val expectedCash: Long = 0,
    val difference: Long = 0, // Diferencia entre esperado y real
    val salesCount: Int = 0,
    val itemsSold: Int = 0,
    val profit: Long = 0, // Ganancia del día
    val isClosed: Boolean = false,
    val closedAt: Long? = null,
    val closedBy: String? = null,
    val notes: String? = null,
    val syncStatus: Int = 0
)

/**
 * Gastos/Egresos
 */
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey
    val id: String,
    val description: String,
    val amount: Long,
    val category: String? = null, // "SUPPLIES", "RENT", "UTILITIES", "OTHER"
    val paymentMethod: String = "CASH",
    val receiptImageUrl: String? = null,
    val date: Long = System.currentTimeMillis(),
    val createdBy: String,
    val notes: String? = null,
    val syncStatus: Int = 0
)

/**
 * Clientes (opcional)
 */
@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val ruc: String? = null, // RUC paraguayo
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val city: String? = null,
    val notes: String? = null,
    val totalPurchases: Long = 0,
    val purchaseCount: Int = 0,
    val lastPurchaseAt: Long? = null,
    val creditLimit: Long = 0,
    val currentCredit: Long = 0, // Deuda actual
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0
)
