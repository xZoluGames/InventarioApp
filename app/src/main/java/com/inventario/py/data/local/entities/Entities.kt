package com.inventario.py.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.inventario.py.data.local.database.Converters
import com.inventario.py.domain.model.*
import java.util.Date

// ========== Sync Status ==========

enum class SyncStatus {
    SYNCED,
    PENDING,
    CONFLICT,
    ERROR
}

// ========== Product Entity ==========

@Entity(tableName = "products")
@TypeConverters(Converters::class)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val barcode: String? = null,
    val identifier: String? = null,
    val imagePath: String? = null,
    val price: Long = 0,
    val cost: Long = 0,
    val stock: Int = 0,
    val minStock: Int = 5,
    val supplier: String? = null,
    val quality: String? = null,
    val category: String? = null,
    val colors: List<String> = emptyList(),
    val types: List<ProductVariantEntity> = emptyList(),
    val capacities: List<ProductVariantEntity> = emptyList(),
    val dateAdded: Date = Date(),
    val lastModified: Date = Date(),
    val isActive: Boolean = true,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val serverId: Long? = null
)

data class ProductVariantEntity(
    val name: String,
    val additionalPrice: Long = 0
)

fun ProductEntity.toDomain() = Product(
    id = id,
    name = name,
    description = description,
    barcode = barcode,
    identifier = identifier,
    imagePath = imagePath,
    price = price,
    cost = cost,
    stock = stock,
    minStock = minStock,
    supplier = supplier,
    quality = quality,
    category = category,
    colors = colors,
    types = types.map { ProductVariant(it.name, it.additionalPrice) },
    capacities = capacities.map { ProductVariant(it.name, it.additionalPrice) },
    dateAdded = dateAdded,
    lastModified = lastModified,
    isSynced = syncStatus == SyncStatus.SYNCED
)

fun Product.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING) = ProductEntity(
    id = id,
    name = name,
    description = description,
    barcode = barcode,
    identifier = identifier,
    imagePath = imagePath,
    price = price,
    cost = cost,
    stock = stock,
    minStock = minStock,
    supplier = supplier,
    quality = quality,
    category = category,
    colors = colors,
    types = types.map { ProductVariantEntity(it.name, it.additionalPrice) },
    capacities = capacities.map { ProductVariantEntity(it.name, it.additionalPrice) },
    dateAdded = dateAdded,
    lastModified = lastModified,
    syncStatus = syncStatus
)

// ========== User Entity ==========

@Entity(tableName = "users")
@TypeConverters(Converters::class)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val passwordHash: String,
    val name: String,
    val role: UserRole = UserRole.EMPLOYEE,
    val isActive: Boolean = true,
    val lastLogin: Date? = null,
    val createdAt: Date = Date(),
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val serverId: Long? = null
)

fun UserEntity.toDomain() = User(
    id = id,
    username = username,
    name = name,
    role = role,
    isActive = isActive,
    lastLogin = lastLogin,
    createdAt = createdAt
)

// ========== Sale Entity ==========

@Entity(tableName = "sales")
@TypeConverters(Converters::class)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val total: Long,
    val totalCost: Long = 0,
    val profit: Long = 0,
    val date: Date = Date(),
    val employeeId: Long,
    val employeeName: String,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val notes: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val serverId: Long? = null
)

// ========== Sale Item Entity ==========

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
    indices = [Index("saleId")]
)
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val barcode: String? = null,
    val unitPrice: Long,
    val unitCost: Long = 0,
    val quantity: Int,
    val selectedType: String? = null,
    val selectedCapacity: String? = null,
    val selectedColor: String? = null
)

fun SaleItemEntity.toDomain() = SaleItem(
    id = id,
    saleId = saleId,
    productId = productId,
    productName = productName,
    barcode = barcode,
    unitPrice = unitPrice,
    unitCost = unitCost,
    quantity = quantity,
    selectedType = selectedType,
    selectedCapacity = selectedCapacity,
    selectedColor = selectedColor
)

fun SaleItem.toEntity(saleId: Long) = SaleItemEntity(
    id = id,
    saleId = saleId,
    productId = productId,
    productName = productName,
    barcode = barcode,
    unitPrice = unitPrice,
    unitCost = unitCost,
    quantity = quantity,
    selectedType = selectedType,
    selectedCapacity = selectedCapacity,
    selectedColor = selectedColor
)

// ========== Cart Item Entity ==========

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey(autoGenerate = true)
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
)

fun CartItemEntity.toDomain() = CartItem(
    id = id,
    productId = productId,
    productName = productName,
    barcode = barcode,
    imagePath = imagePath,
    unitPrice = unitPrice,
    unitCost = unitCost,
    quantity = quantity,
    maxQuantity = maxQuantity,
    selectedType = selectedType,
    selectedCapacity = selectedCapacity,
    selectedColor = selectedColor
)

fun CartItem.toEntity() = CartItemEntity(
    id = id,
    productId = productId,
    productName = productName,
    barcode = barcode,
    imagePath = imagePath,
    unitPrice = unitPrice,
    unitCost = unitCost,
    quantity = quantity,
    maxQuantity = maxQuantity,
    selectedType = selectedType,
    selectedCapacity = selectedCapacity,
    selectedColor = selectedColor
)

// ========== Product History Entity ==========

@Entity(
    tableName = "product_history",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId")]
)
@TypeConverters(Converters::class)
data class ProductHistoryEntity(
    @PrimaryKey(autoGenerate = true)
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

fun ProductHistoryEntity.toDomain() = ProductHistory(
    id = id,
    productId = productId,
    action = action,
    description = description,
    oldValue = oldValue,
    newValue = newValue,
    userId = userId,
    userName = userName,
    date = date
)

fun ProductHistory.toEntity() = ProductHistoryEntity(
    id = id,
    productId = productId,
    action = action,
    description = description,
    oldValue = oldValue,
    newValue = newValue,
    userId = userId,
    userName = userName,
    date = date
)

// ========== Sale with Items ==========

data class SaleWithItems(
    val sale: SaleEntity,
    val items: List<SaleItemEntity>
)

fun SaleWithItems.toDomain() = Sale(
    id = sale.id,
    items = items.map { it.toDomain() },
    total = sale.total,
    totalCost = sale.totalCost,
    profit = sale.profit,
    date = sale.date,
    employeeId = sale.employeeId,
    employeeName = sale.employeeName,
    paymentMethod = sale.paymentMethod,
    notes = sale.notes,
    isSynced = sale.syncStatus == SyncStatus.SYNCED
)

fun Sale.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING) = SaleEntity(
    id = id,
    total = total,
    totalCost = totalCost,
    profit = profit,
    date = date,
    employeeId = employeeId,
    employeeName = employeeName,
    paymentMethod = paymentMethod,
    notes = notes,
    syncStatus = syncStatus
)
