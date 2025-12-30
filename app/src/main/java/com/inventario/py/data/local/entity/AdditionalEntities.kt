package com.inventario.py.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityType: String, // PRODUCT, SALE, USER, CATEGORY, SUPPLIER
    val entityId: String,
    val operation: String, // INSERT, UPDATE, DELETE
    val status: String = "PENDING", // PENDING, SYNCED, FAILED
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
    val retryCount: Int = 0,
    val errorMessage: String? = null
)

/**
 * Additional entities that might be needed
 */

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val productId: String,
    val variantId: String? = null,
    val quantity: Int,
    val unitPrice: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String? = null,
    val iconName: String? = null,
    val color: String? = null,
    val parentId: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stock_movements")
data class StockMovementEntity(
    @PrimaryKey
    val id: String,
    val productId: String,
    val variantId: String? = null,
    val movementType: MovementType,
    val quantity: Int,
    val previousStock: Int,
    val newStock: Int,
    val reason: String? = null,
    val referenceId: String? = null, // Sale ID, Purchase ID, etc.
    val referenceType: String? = null, // SALE, PURCHASE, ADJUSTMENT
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class MovementType {
    IN,      // Entrada de stock
    OUT,     // Salida de stock
    ADJUST   // Ajuste de inventario
}

@Entity(tableName = "price_history")
data class PriceHistoryEntity(
    @PrimaryKey
    val id: String,
    val productId: String,
    val variantId: String? = null,
    val priceType: String, // SALE, COST
    val oldPrice: Long,
    val newPrice: Long,
    val reason: String? = null,
    val changedBy: String,
    val changedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "business_info")
data class BusinessInfoEntity(
    @PrimaryKey
    val id: String = "default",
    val name: String,
    val ruc: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val logoUrl: String? = null,
    val currency: String = "PYG",
    val lowStockThreshold: Int = 5,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Data class for cart items with product info (used for display)
 */
data class CartItemWithProduct(
    val cartItem: CartItemEntity,
    val product: ProductEntity,
    val variant: ProductVariantEntity?
)
