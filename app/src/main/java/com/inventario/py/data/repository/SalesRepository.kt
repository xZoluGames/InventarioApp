package com.inventario.py.data.repository

import com.inventario.py.data.local.dao.ProductDao
import com.inventario.py.data.local.dao.SalesDao
import com.inventario.py.data.local.entity.*
import com.inventario.py.data.remote.api.InventarioApi
import com.inventario.py.utils.Generators
import com.inventario.py.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalesRepository @Inject constructor(
    private val salesDao: SalesDao,
    private val productDao: ProductDao,
    private val api: InventarioApi,
    private val sessionManager: SessionManager
) {
    // ==================== CARRITO ====================
    
    fun getCartItems(): Flow<List<CartItemEntity>> {
        val userId = sessionManager.getUserId() ?: ""
        return salesDao.getCartItems(userId)
    }
    
    suspend fun getCartItemsSync(): List<CartItemEntity> {
        val userId = sessionManager.getUserId() ?: ""
        return salesDao.getCartItemsSync(userId)
    }
    
    fun getCartTotal(): Flow<Long?> {
        val userId = sessionManager.getUserId() ?: ""
        return salesDao.getCartTotal(userId)
    }
    
    fun getCartItemCount(): Flow<Int> {
        val userId = sessionManager.getUserId() ?: ""
        return salesDao.getCartItemCount(userId)
    }
    
    fun getCartTotalQuantity(): Flow<Int?> {
        val userId = sessionManager.getUserId() ?: ""
        return salesDao.getCartTotalQuantity(userId)
    }
    
    suspend fun addToCart(
        product: ProductEntity,
        variant: ProductVariantEntity? = null,
        quantity: Int = 1
    ) {
        val userId = sessionManager.getUserId() ?: return
        
        // Verificar si ya existe en el carrito
        val existingItem = salesDao.getCartItemByProductAndVariant(
            product.id, 
            variant?.id, 
            userId
        )
        
        if (existingItem != null) {
            // Actualizar cantidad
            val newQuantity = existingItem.quantity + quantity
            val unitPrice = product.salePrice + (variant?.additionalPrice ?: 0)
            val subtotal = unitPrice * newQuantity
            salesDao.updateCartItemQuantity(existingItem.id, newQuantity, subtotal)
        } else {
            // Agregar nuevo item
            val unitPrice = product.salePrice + (variant?.additionalPrice ?: 0)
            val variantDescription = variant?.let { "${it.variantLabel}: ${it.variantValue}" }
            
            val cartItem = CartItemEntity(
                id = Generators.generateId(),
                productId = product.id,
                productName = product.name,
                variantId = variant?.id,
                variantDescription = variantDescription,
                quantity = quantity,
                unitPrice = unitPrice,
                subtotal = unitPrice * quantity,
                imageUrl = product.imageUrl,
                addedBy = userId
            )
            salesDao.insertCartItem(cartItem)
        }
    }
    
    suspend fun updateCartItemQuantity(itemId: String, quantity: Int) {
        val item = salesDao.getCartItemById(itemId) ?: return
        if (quantity <= 0) {
            salesDao.deleteCartItemById(itemId)
        } else {
            val subtotal = item.unitPrice * quantity
            salesDao.updateCartItemQuantity(itemId, quantity, subtotal)
        }
    }
    
    suspend fun removeFromCart(itemId: String) {
        salesDao.deleteCartItemById(itemId)
    }
    
    suspend fun clearCart() {
        val userId = sessionManager.getUserId() ?: return
        salesDao.clearCart(userId)
    }
    
    // ==================== VENTAS ====================
    
    fun getAllSales(): Flow<List<SaleEntity>> = salesDao.getAllSales()
    
    fun getRecentSales(limit: Int = 50): Flow<List<SaleEntity>> = 
        salesDao.getRecentSales(limit)
    
    suspend fun getSaleById(id: String): SaleEntity? = salesDao.getSaleById(id)
    
    fun getSaleByIdFlow(id: String): Flow<SaleEntity?> = salesDao.getSaleByIdFlow(id)
    
    fun getSalesByUser(userId: String): Flow<List<SaleEntity>> = 
        salesDao.getSalesByUser(userId)
    
    fun getSalesByDateRange(startDate: Long, endDate: Long): Flow<List<SaleEntity>> = 
        salesDao.getSalesByDateRange(startDate, endDate)
    
    fun getSalesByDate(date: Long): Flow<List<SaleEntity>> = 
        salesDao.getSalesByDate(date)
    
    // Estadísticas
    fun getDailySalesTotal(date: Long = System.currentTimeMillis()): Flow<Long> = 
        salesDao.getDailySalesTotal(date)
    
    fun getDailySalesCount(date: Long = System.currentTimeMillis()): Flow<Int> = 
        salesDao.getDailySalesCount(date)
    
    fun getMonthlySalesTotal(date: Long = System.currentTimeMillis()): Flow<Long> = 
        salesDao.getMonthlySalesTotal(date)
    
    fun getMonthlySalesCount(date: Long = System.currentTimeMillis()): Flow<Int> = 
        salesDao.getMonthlySalesCount(date)
    
    fun getYearlySalesTotal(date: Long = System.currentTimeMillis()): Flow<Long> = 
        salesDao.getYearlySalesTotal(date)
    
    /**
     * Procesar venta desde el carrito
     */
    suspend fun processSale(
        customerId: String? = null,
        customerName: String? = null,
        totalDiscount: Long = 0,
        paymentMethod: String = SaleEntity.PAYMENT_CASH,
        amountPaid: Long,
        notes: String? = null
    ): SaleEntity? = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext null
        val user = sessionManager.getCurrentUser() ?: return@withContext null
        
        // Obtener items del carrito
        val cartItems = salesDao.getCartItemsSync(userId)
        if (cartItems.isEmpty()) return@withContext null
        
        // Calcular totales
        val subtotal = cartItems.sumOf { it.subtotal }
        val total = subtotal - totalDiscount
        val changeAmount = if (amountPaid > total) amountPaid - total else 0
        
        // Generar número de venta
        val lastSaleNumber = salesDao.getLastSaleNumber()
        val saleNumber = Generators.generateSaleNumber(lastSaleNumber)
        
        // Crear venta
        val sale = SaleEntity(
            id = Generators.generateId(),
            saleNumber = saleNumber,
            customerId = customerId,
            customerName = customerName,
            subtotal = subtotal,
            totalDiscount = totalDiscount,
            total = total,
            paymentMethod = paymentMethod,
            amountPaid = amountPaid,
            changeAmount = changeAmount,
            notes = notes,
            soldBy = userId,
            soldByName = user.fullName,
            syncStatus = 1
        )
        
        salesDao.insertSale(sale)
        
        // Crear items de venta y actualizar stock
        cartItems.forEach { cartItem ->
            val product = productDao.getProductById(cartItem.productId)
            
            val saleItem = SaleItemEntity(
                id = Generators.generateId(),
                saleId = sale.id,
                productId = cartItem.productId,
                productName = cartItem.productName,
                productIdentifier = product?.identifier ?: "",
                variantId = cartItem.variantId,
                variantDescription = cartItem.variantDescription,
                quantity = cartItem.quantity,
                unitPrice = cartItem.unitPrice,
                purchasePrice = product?.purchasePrice ?: 0,
                discount = cartItem.discount,
                subtotal = cartItem.subtotal,
                productImageUrl = cartItem.imageUrl,
                barcode = product?.barcode
            )
            salesDao.insertSaleItem(saleItem)
            
            // Actualizar stock
            if (cartItem.variantId != null) {
                val variant = productDao.getVariantById(cartItem.variantId)
                variant?.let {
                    val newStock = (it.stock - cartItem.quantity).coerceAtLeast(0)
                    productDao.updateVariantStock(it.id, newStock)
                }
            }
            
            product?.let {
                val newStock = (it.totalStock - cartItem.quantity).coerceAtLeast(0)
                productDao.updateStock(it.id, newStock)
                
                // Registrar movimiento de stock
                val movement = StockMovementEntity(
                    id = Generators.generateId(),
                    productId = it.id,
                    variantId = cartItem.variantId,
                    movementType = StockMovementEntity.TYPE_SALE,
                    quantity = -cartItem.quantity,
                    previousStock = it.totalStock,
                    newStock = newStock,
                    referenceId = sale.id,
                    referenceType = StockMovementEntity.REF_SALE,
                    createdBy = userId
                )
                salesDao.insertStockMovement(movement)
            }
        }
        
        // Actualizar estadísticas del cliente si existe
        customerId?.let {
            salesDao.updateCustomerPurchaseStats(it, total, System.currentTimeMillis())
        }
        
        // Limpiar carrito
        salesDao.clearCart(userId)
        
        return@withContext sale
    }
    
    suspend fun cancelSale(saleId: String, reason: String?): Boolean = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext false
        val sale = salesDao.getSaleById(saleId) ?: return@withContext false
        
        if (sale.status != SaleEntity.STATUS_COMPLETED) return@withContext false
        
        // Restaurar stock
        val saleItems = salesDao.getSaleItemsSync(saleId)
        saleItems.forEach { item ->
            val product = productDao.getProductById(item.productId)
            product?.let {
                val newStock = it.totalStock + item.quantity
                productDao.updateStock(it.id, newStock)
                
                // Registrar movimiento de stock (devolución)
                val movement = StockMovementEntity(
                    id = Generators.generateId(),
                    productId = it.id,
                    variantId = item.variantId,
                    movementType = StockMovementEntity.TYPE_RETURN,
                    quantity = item.quantity,
                    previousStock = it.totalStock,
                    newStock = newStock,
                    reason = "Cancelación de venta: $reason",
                    referenceId = saleId,
                    referenceType = StockMovementEntity.REF_RETURN,
                    createdBy = userId
                )
                salesDao.insertStockMovement(movement)
            }
            
            // Restaurar stock de variante si aplica
            item.variantId?.let { variantId ->
                val variant = productDao.getVariantById(variantId)
                variant?.let {
                    val newStock = it.stock + item.quantity
                    productDao.updateVariantStock(variantId, newStock)
                }
            }
        }
        
        // Actualizar estado de la venta
        salesDao.cancelSale(
            saleId = saleId,
            status = SaleEntity.STATUS_CANCELLED,
            cancelledAt = System.currentTimeMillis(),
            cancelledBy = userId,
            reason = reason
        )
        
        return@withContext true
    }
    
    // ==================== ITEMS DE VENTA ====================
    
    fun getSaleItems(saleId: String): Flow<List<SaleItemEntity>> = 
        salesDao.getSaleItems(saleId)
    
    suspend fun getSaleItemsSync(saleId: String): List<SaleItemEntity> = 
        salesDao.getSaleItemsSync(saleId)
    
    fun getSaleItemsByProduct(productId: String): Flow<List<SaleItemEntity>> = 
        salesDao.getSaleItemsByProduct(productId)
    
    suspend fun getRecentSalesOfProduct(productId: String, limit: Int = 20): List<SaleItemEntity> = 
        salesDao.getRecentSalesOfProduct(productId, limit)
    
    suspend fun getProductProfit(productId: String): Long = 
        salesDao.getProductProfit(productId) ?: 0
    
    suspend fun getProductTotalSold(productId: String): Int = 
        salesDao.getProductTotalSold(productId)
    
    // ==================== MOVIMIENTOS DE STOCK ====================
    
    fun getStockMovements(productId: String): Flow<List<StockMovementEntity>> = 
        salesDao.getStockMovements(productId)
    
    suspend fun addStockMovement(
        productId: String,
        variantId: String?,
        quantity: Int,
        movementType: String,
        reason: String?
    ) {
        val userId = sessionManager.getUserId() ?: return
        val product = productDao.getProductById(productId) ?: return
        
        val newStock = when (movementType) {
            StockMovementEntity.TYPE_IN -> product.totalStock + quantity
            StockMovementEntity.TYPE_OUT -> (product.totalStock - quantity).coerceAtLeast(0)
            StockMovementEntity.TYPE_ADJUSTMENT -> quantity
            else -> product.totalStock
        }
        
        val movement = StockMovementEntity(
            id = Generators.generateId(),
            productId = productId,
            variantId = variantId,
            movementType = movementType,
            quantity = if (movementType == StockMovementEntity.TYPE_OUT) -quantity else quantity,
            previousStock = product.totalStock,
            newStock = newStock,
            reason = reason,
            referenceType = StockMovementEntity.REF_ADJUSTMENT,
            createdBy = userId
        )
        
        salesDao.insertStockMovement(movement)
        productDao.updateStock(productId, newStock)
        
        // Actualizar variante si aplica
        variantId?.let { vId ->
            val variant = productDao.getVariantById(vId)
            variant?.let {
                val variantNewStock = when (movementType) {
                    StockMovementEntity.TYPE_IN -> it.stock + quantity
                    StockMovementEntity.TYPE_OUT -> (it.stock - quantity).coerceAtLeast(0)
                    StockMovementEntity.TYPE_ADJUSTMENT -> quantity
                    else -> it.stock
                }
                productDao.updateVariantStock(vId, variantNewStock)
            }
        }
    }
    
    // ==================== RESUMEN DIARIO ====================
    
    suspend fun getDailySummary(date: String): DailyCashSummaryEntity? = 
        salesDao.getDailySummary(date)
    
    fun getRecentDailySummaries(limit: Int = 30): Flow<List<DailyCashSummaryEntity>> = 
        salesDao.getRecentDailySummaries(limit)
    
    suspend fun closeDailyRegister(
        openingCash: Long,
        closingCash: Long,
        notes: String?
    ): DailyCashSummaryEntity? = withContext(Dispatchers.IO) {
        val userId = sessionManager.getUserId() ?: return@withContext null
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        // Obtener estadísticas del día
        val todayStart = getStartOfDay(System.currentTimeMillis())
        val todayEnd = getEndOfDay(System.currentTimeMillis())
        
        val sales = salesDao.getSalesByDateRange(todayStart, todayEnd)
        // Se necesita collect pero para simplificar usamos una consulta directa
        
        val summary = DailyCashSummaryEntity(
            id = Generators.generateId(),
            date = today,
            openingCash = openingCash,
            // Los totales se calcularían con los datos reales
            totalSales = 0, // TODO: Calcular
            closingCash = closingCash,
            isClosed = true,
            closedAt = System.currentTimeMillis(),
            closedBy = userId,
            notes = notes
        )
        
        salesDao.insertDailySummary(summary)
        return@withContext summary
    }
    
    // ==================== GASTOS ====================
    
    fun getAllExpenses(): Flow<List<ExpenseEntity>> = salesDao.getAllExpenses()
    
    fun getExpensesByDateRange(startDate: Long, endDate: Long): Flow<List<ExpenseEntity>> = 
        salesDao.getExpensesByDateRange(startDate, endDate)
    
    fun getDailyExpensesTotal(date: Long = System.currentTimeMillis()): Flow<Long> = 
        salesDao.getDailyExpensesTotal(date)
    
    suspend fun addExpense(
        description: String,
        amount: Long,
        category: String?,
        paymentMethod: String = "CASH",
        notes: String?
    ): ExpenseEntity {
        val userId = sessionManager.getUserId() ?: ""
        val expense = ExpenseEntity(
            id = Generators.generateId(),
            description = description,
            amount = amount,
            category = category,
            paymentMethod = paymentMethod,
            notes = notes,
            createdBy = userId
        )
        salesDao.insertExpense(expense)
        return expense
    }
    
    suspend fun deleteExpense(expense: ExpenseEntity) {
        salesDao.deleteExpense(expense)
    }
    
    // ==================== CLIENTES ====================
    
    fun getAllCustomers(): Flow<List<CustomerEntity>> = salesDao.getAllCustomers()
    
    suspend fun getCustomerById(id: String): CustomerEntity? = salesDao.getCustomerById(id)
    
    suspend fun searchCustomers(query: String): List<CustomerEntity> = 
        salesDao.searchCustomers(query)
    
    suspend fun createCustomer(
        name: String,
        ruc: String?,
        phone: String?,
        email: String?,
        address: String?,
        city: String?,
        notes: String?,
        creditLimit: Long = 0
    ): CustomerEntity {
        val customer = CustomerEntity(
            id = Generators.generateId(),
            name = name,
            ruc = ruc,
            phone = phone,
            email = email,
            address = address,
            city = city,
            notes = notes,
            creditLimit = creditLimit
        )
        salesDao.insertCustomer(customer)
        return customer
    }
    
    suspend fun updateCustomer(customer: CustomerEntity) {
        salesDao.updateCustomer(customer.copy(updatedAt = System.currentTimeMillis()))
    }
    
    // ==================== UTILIDADES ====================
    
    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    private fun getEndOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
}
