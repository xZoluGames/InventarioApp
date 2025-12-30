package com.inventario.py.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.inventario.py.data.local.entities.*
import com.inventario.py.domain.model.PaymentMethod
import java.util.Date

// ========== Request DTOs ==========

data class LoginRequest(
    val username: String,
    val password: String
)

data class ProductRequest(
    val id: Long,
    val name: String,
    val description: String,
    val barcode: String?,
    val identifier: String?,
    val price: Long,
    val cost: Long,
    val stock: Int,
    @SerializedName("min_stock")
    val minStock: Int,
    val supplier: String?,
    val quality: String?,
    val category: String?,
    val colors: List<String>,
    val types: List<VariantDto>,
    val capacities: List<VariantDto>,
    @SerializedName("date_added")
    val dateAdded: Long,
    @SerializedName("is_active")
    val isActive: Boolean
)

data class SaleRequest(
    val id: Long,
    val items: List<SaleItemDto>,
    val total: Long,
    @SerializedName("total_cost")
    val totalCost: Long,
    val profit: Long,
    val date: Long,
    @SerializedName("employee_id")
    val employeeId: Long,
    @SerializedName("employee_name")
    val employeeName: String,
    @SerializedName("payment_method")
    val paymentMethod: String,
    val notes: String?
)

data class SyncRequest(
    val products: List<ProductRequest>,
    val sales: List<SaleRequest>,
    @SerializedName("last_sync")
    val lastSync: Long
)

data class BackupRequest(
    @SerializedName("device_id")
    val deviceId: String,
    val timestamp: Long,
    val data: BackupData
)

data class BackupData(
    val products: List<ProductRequest>,
    val sales: List<SaleRequest>,
    val users: List<UserDto>
)

// ========== Response DTOs ==========

data class LoginResponse(
    val success: Boolean,
    val message: String?,
    val token: String?,
    val user: UserDto?
)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?
)

data class SyncResponse(
    val success: Boolean,
    val message: String?,
    @SerializedName("server_products")
    val serverProducts: List<ProductResponse>,
    @SerializedName("server_sales")
    val serverSales: List<SaleResponse>,
    @SerializedName("sync_timestamp")
    val syncTimestamp: Long
)

data class ProductResponse(
    val id: Long,
    @SerializedName("server_id")
    val serverId: Long?,
    val name: String,
    val description: String,
    val barcode: String?,
    val identifier: String?,
    val price: Long,
    val cost: Long,
    val stock: Int,
    @SerializedName("min_stock")
    val minStock: Int,
    val supplier: String?,
    val quality: String?,
    val category: String?,
    val colors: List<String>,
    val types: List<VariantDto>,
    val capacities: List<VariantDto>,
    @SerializedName("date_added")
    val dateAdded: Long,
    @SerializedName("last_modified")
    val lastModified: Long,
    @SerializedName("is_active")
    val isActive: Boolean
)

data class SaleResponse(
    val id: Long,
    @SerializedName("server_id")
    val serverId: Long?,
    val items: List<SaleItemDto>,
    val total: Long,
    @SerializedName("total_cost")
    val totalCost: Long,
    val profit: Long,
    val date: Long,
    @SerializedName("employee_id")
    val employeeId: Long,
    @SerializedName("employee_name")
    val employeeName: String,
    @SerializedName("payment_method")
    val paymentMethod: String,
    val notes: String?
)

// ========== Common DTOs ==========

data class VariantDto(
    val name: String,
    @SerializedName("additional_price")
    val additionalPrice: Long = 0
)

data class SaleItemDto(
    val id: Long = 0,
    @SerializedName("sale_id")
    val saleId: Long = 0,
    @SerializedName("product_id")
    val productId: Long,
    @SerializedName("product_name")
    val productName: String,
    val barcode: String?,
    val quantity: Int,
    @SerializedName("unit_price")
    val unitPrice: Long,
    @SerializedName("unit_cost")
    val unitCost: Long,
    @SerializedName("selected_type")
    val selectedType: String?,
    @SerializedName("selected_capacity")
    val selectedCapacity: String?,
    @SerializedName("selected_color")
    val selectedColor: String?
)

data class UserDto(
    val id: Long,
    val username: String,
    val name: String,
    val role: String,
    @SerializedName("is_active")
    val isActive: Boolean
)

// ========== Mappers ==========

fun ProductEntity.toRequest() = ProductRequest(
    id = id,
    name = name,
    description = description,
    barcode = barcode,
    identifier = identifier,
    price = price,
    cost = cost,
    stock = stock,
    minStock = minStock,
    supplier = supplier,
    quality = quality,
    category = category,
    colors = colors,
    types = types.map { VariantDto(it.name, it.additionalPrice) },
    capacities = capacities.map { VariantDto(it.name, it.additionalPrice) },
    dateAdded = dateAdded.time,
    isActive = isActive
)

fun ProductResponse.toEntity() = ProductEntity(
    id = id,
    serverId = serverId,
    name = name,
    description = description,
    barcode = barcode,
    identifier = identifier,
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
    dateAdded = Date(dateAdded),
    lastModified = Date(lastModified),
    isActive = isActive,
    syncStatus = SyncStatus.SYNCED
)

fun SaleEntity.toRequest(saleItems: List<SaleItemEntity>) = SaleRequest(
    id = id,
    items = saleItems.map { it.toDto() },
    total = total,
    totalCost = totalCost,
    profit = profit,
    date = date.time,
    employeeId = employeeId,
    employeeName = employeeName,
    paymentMethod = paymentMethod.name,
    notes = notes
)

fun SaleItemEntity.toDto() = SaleItemDto(
    id = id,
    saleId = saleId,
    productId = productId,
    productName = productName,
    barcode = barcode,
    quantity = quantity,
    unitPrice = unitPrice,
    unitCost = unitCost,
    selectedType = selectedType,
    selectedCapacity = selectedCapacity,
    selectedColor = selectedColor
)

fun SaleResponse.toEntity() = SaleEntity(
    id = id,
    serverId = serverId,
    total = total,
    totalCost = totalCost,
    profit = profit,
    date = Date(date),
    employeeId = employeeId,
    employeeName = employeeName,
    paymentMethod = try { PaymentMethod.valueOf(paymentMethod) } catch (e: Exception) { PaymentMethod.CASH },
    notes = notes,
    syncStatus = SyncStatus.SYNCED
)

fun SaleItemDto.toEntity(parentSaleId: Long) = SaleItemEntity(
    id = id,
    saleId = parentSaleId,
    productId = productId,
    productName = productName,
    barcode = barcode,
    quantity = quantity,
    unitPrice = unitPrice,
    unitCost = unitCost,
    selectedType = selectedType,
    selectedCapacity = selectedCapacity,
    selectedColor = selectedColor
)
