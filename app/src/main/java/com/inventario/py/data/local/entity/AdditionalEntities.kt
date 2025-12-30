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
 * NOTE: CartItemEntity is defined in SalesEntity.kt
 * DO NOT duplicate it here
 */

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
 * This is NOT an Entity, just a data holder
 */
data class CartItemWithProduct(
    val cartItem: CartItemEntity,
    val product: ProductEntity,
    val variant: ProductVariantEntity?
)
