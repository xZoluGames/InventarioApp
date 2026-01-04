package com.inventario.py.data.remote.dto

import com.inventario.py.data.local.entity.*

/**
 * Extensiones para convertir Entities a DTOs
 *
 * Los DTOs ya tienen toEntity() definido en ApiDtos.kt
 * Aquí agregamos las funciones inversas: Entity -> DTO
 */

// ==================== PRODUCTO ====================

fun ProductEntity.toDto(
    variants: List<ProductVariantDto>? = null,
    images: List<ProductImageDto>? = null
): ProductDto = ProductDto(
    id = id,
    name = name,
    description = description,
    barcode = barcode,
    identifier = identifier,
    imageUrl = imageUrl,
    categoryId = categoryId,
    subcategoryId = subcategoryId,
    totalStock = totalStock,
    minStockAlert = minStockAlert,
    isStockAlertEnabled = isStockAlertEnabled,
    salePrice = salePrice,
    purchasePrice = purchasePrice,
    supplierId = supplierId,
    supplierName = supplierName,
    quality = quality,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy,
    variants = variants,
    images = images
)

fun ProductEntity.toCreateRequest(): CreateProductRequest = CreateProductRequest(
    name = name,
    description = description,
    barcode = barcode,
    identifier = identifier,
    categoryId = categoryId,
    totalStock = totalStock,
    minStockAlert = minStockAlert,
    salePrice = salePrice,
    purchasePrice = purchasePrice,
    supplierId = supplierId,
    supplierName = supplierName,
    quality = quality,
    variants = null
)

// ==================== VARIANTE ====================

fun ProductVariantEntity.toDto(): ProductVariantDto = ProductVariantDto(
    id = id,
    productId = productId,
    variantType = variantType,
    variantLabel = variantLabel,
    variantValue = variantValue,
    stock = stock,
    additionalPrice = additionalPrice,
    barcode = barcode,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ProductVariantEntity.toCreateRequest(): CreateVariantRequest = CreateVariantRequest(
    variantType = variantType,
    variantLabel = variantLabel,
    variantValue = variantValue,
    stock = stock,
    additionalPrice = additionalPrice,
    barcode = barcode
)

// ==================== CATEGORÍA ====================

fun CategoryEntity.toDto(): CategoryDto = CategoryDto(
    id = id,
    name = name,
    description = description,
    parentId = parentId,
    iconName = iconName,
    colorHex = colorHex,
    sortOrder = sortOrder,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ==================== PROVEEDOR ====================

fun SupplierEntity.toDto(): SupplierDto = SupplierDto(
    id = id,
    name = name,
    contactName = contactName,
    phone = phone,
    email = email,
    address = address,
    city = city,
    notes = notes,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// ==================== VENTA ====================

fun SaleEntity.toDto(items: List<SaleItemDto>? = null): SaleDto = SaleDto(
    id = id,
    saleNumber = saleNumber,
    customerId = customerId,
    customerName = customerName,
    subtotal = subtotal,
    totalDiscount = totalDiscount,
    taxAmount = taxAmount,
    total = total,
    paymentMethod = paymentMethod,
    amountPaid = amountPaid,
    changeAmount = changeAmount,
    status = status,
    notes = notes,
    soldBy = soldBy,
    soldByName = soldByName,
    soldAt = soldAt,
    cancelledAt = cancelledAt,
    cancelledBy = cancelledBy,
    cancellationReason = cancellationReason,
    items = items
)

fun SaleItemEntity.toDto(): SaleItemDto = SaleItemDto(
    id = id,
    saleId = saleId,
    productId = productId,
    productName = productName,
    productIdentifier = productIdentifier,
    variantId = variantId,
    variantDescription = variantDescription,
    quantity = quantity,
    unitPrice = unitPrice,
    purchasePrice = purchasePrice,
    discount = discount,
    subtotal = subtotal,
    productImageUrl = productImageUrl,
    barcode = barcode
)

fun SaleEntity.toCreateRequest(items: List<CreateSaleItemRequest>): CreateSaleRequest = CreateSaleRequest(
    customerId = customerId,
    customerName = customerName,
    items = items,
    totalDiscount = totalDiscount,
    paymentMethod = paymentMethod,
    amountPaid = amountPaid,
    notes = notes
)

fun SaleItemEntity.toCreateRequest(): CreateSaleItemRequest = CreateSaleItemRequest(
    productId = productId,
    variantId = variantId,
    quantity = quantity,
    unitPrice = unitPrice,
    discount = discount
)

// ==================== MOVIMIENTO DE STOCK ====================

fun StockMovementEntity.toDto(): StockMovementDto = StockMovementDto(
    id = id,
    productId = productId,
    variantId = variantId,
    movementType = movementType,
    quantity = quantity,
    previousStock = previousStock,
    newStock = newStock,
    reason = reason,
    referenceId = referenceId,
    referenceType = referenceType,
    createdBy = createdBy,
    createdAt = createdAt
)

// ==================== USUARIO ====================

fun UserEntity.toDto(): UserDto = UserDto(
    id = id,
    username = username,
    email = email,
    fullName = fullName,
    role = role,
    isActive = isActive,
    profileImageUrl = profileImageUrl,
    phoneNumber = phoneNumber,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastLoginAt = lastLoginAt
)