package com.inventario.py.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.inventario.py.data.local.database.Converters

@Entity(tableName = "products")
@TypeConverters(Converters::class)
data class ProductEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String? = null,
    val barcode: String? = null,
    val identifier: String, // Identificador único personalizado
    val imageUrl: String? = null,
    val localImagePath: String? = null,
    
    // Categoría y subcategoría
    val categoryId: String? = null,
    val subcategoryId: String? = null,
    
    // Stock y alertas
    val totalStock: Int = 0,
    val minStockAlert: Int = 5, // Cantidad mínima para alertar
    val isStockAlertEnabled: Boolean = true,
    
    // Precios (en Guaraníes)
    val salePrice: Long = 0, // Precio de venta al público
    val purchasePrice: Long = 0, // Precio de compra (solo visible para dueño)
    
    // Proveedor (solo visible para dueño)
    val supplierId: String? = null,
    val supplierName: String? = null,
    
    // Calidad
    val quality: String? = null, // "Alta", "Media", "Baja", o personalizado
    
    // Metadata
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdBy: String? = null,
    
    // Sincronización
    val syncStatus: Int = SYNC_STATUS_SYNCED,
    val lastSyncAt: Long? = null
) {
    companion object {
        const val SYNC_STATUS_SYNCED = 0
        const val SYNC_STATUS_PENDING = 1
        const val SYNC_STATUS_FAILED = 2
    }
    
    fun isLowStock(): Boolean = totalStock <= minStockAlert && isStockAlertEnabled
    
    fun getProfitMargin(): Long = salePrice - purchasePrice
    
    fun getProfitPercentage(): Double {
        return if (purchasePrice > 0) {
            ((salePrice - purchasePrice).toDouble() / purchasePrice) * 100
        } else 0.0
    }
}

/**
 * Variantes del producto (Tipos, Colores, Capacidades)
 */
@Entity(tableName = "product_variants")
data class ProductVariantEntity(
    @PrimaryKey
    val id: String,
    val productId: String,
    val variantType: String, // "COLOR", "TYPE", "CAPACITY"
    val variantLabel: String, // Etiqueta personalizada (ej: "Color", "Tipo", "Capacidad")
    val variantValue: String, // Valor (ej: "Rojo", "Transparente", "64GB")
    val stock: Int = 0,
    val additionalPrice: Long = 0, // Precio adicional si aplica
    val barcode: String? = null, // Código de barras específico de la variante
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0
) {
    companion object {
        const val TYPE_COLOR = "COLOR"
        const val TYPE_TYPE = "TYPE"
        const val TYPE_CAPACITY = "CAPACITY"
        const val TYPE_SIZE = "SIZE"
        const val TYPE_CUSTOM = "CUSTOM"
    }
}

/**
 * Imágenes adicionales del producto
 */
@Entity(tableName = "product_images")
data class ProductImageEntity(
    @PrimaryKey
    val id: String,
    val productId: String,
    val imageUrl: String? = null,
    val localPath: String? = null,
    val isPrimary: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0
)

/**
 * Historial de precios del producto
 */
@Entity(tableName = "product_price_history")
data class ProductPriceHistoryEntity(
    @PrimaryKey
    val id: String,
    val productId: String,
    val salePrice: Long,
    val purchasePrice: Long,
    val changedAt: Long = System.currentTimeMillis(),
    val changedBy: String? = null,
    val reason: String? = null,
    val syncStatus: Int = 0
)

/**
 * Categorías de productos
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String? = null,
    val parentId: String? = null, // Para subcategorías
    val iconName: String? = null,
    val colorHex: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0
)

/**
 * Proveedores
 */
@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val city: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0
)
