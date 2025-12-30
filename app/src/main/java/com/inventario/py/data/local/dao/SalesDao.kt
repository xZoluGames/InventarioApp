package com.inventario.py.data.local.dao

import androidx.room.*
import com.inventario.py.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesDao {
    
    // ==================== CARRITO ====================
    
    @Query("SELECT * FROM cart_items WHERE addedBy = :userId ORDER BY addedAt DESC")
    fun getCartItems(userId: String): Flow<List<CartItemEntity>>
    
    @Query("SELECT * FROM cart_items WHERE addedBy = :userId")
    suspend fun getCartItemsSync(userId: String): List<CartItemEntity>
    
    @Query("SELECT * FROM cart_items WHERE id = :id")
    suspend fun getCartItemById(id: String): CartItemEntity?
    
    @Query("SELECT * FROM cart_items WHERE productId = :productId AND variantId = :variantId AND addedBy = :userId LIMIT 1")
    suspend fun getCartItemByProductAndVariant(productId: String, variantId: String?, userId: String): CartItemEntity?
    
    @Query("SELECT SUM(subtotal) FROM cart_items WHERE addedBy = :userId")
    fun getCartTotal(userId: String): Flow<Long?>
    
    @Query("SELECT COUNT(*) FROM cart_items WHERE addedBy = :userId")
    fun getCartItemCount(userId: String): Flow<Int>
    
    @Query("SELECT SUM(quantity) FROM cart_items WHERE addedBy = :userId")
    fun getCartTotalQuantity(userId: String): Flow<Int?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItem(item: CartItemEntity)
    
    @Update
    suspend fun updateCartItem(item: CartItemEntity)
    
    @Query("UPDATE cart_items SET quantity = :quantity, subtotal = :subtotal WHERE id = :itemId")
    suspend fun updateCartItemQuantity(itemId: String, quantity: Int, subtotal: Long)
    
    @Delete
    suspend fun deleteCartItem(item: CartItemEntity)
    
    @Query("DELETE FROM cart_items WHERE id = :itemId")
    suspend fun deleteCartItemById(itemId: String)
    
    @Query("DELETE FROM cart_items WHERE addedBy = :userId")
    suspend fun clearCart(userId: String)
    
    // ==================== VENTAS ====================
    
    @Query("SELECT * FROM sales ORDER BY soldAt DESC")
    fun getAllSales(): Flow<List<SaleEntity>>
    
    @Query("SELECT * FROM sales ORDER BY soldAt DESC LIMIT :limit")
    fun getRecentSales(limit: Int = 50): Flow<List<SaleEntity>>
    
    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSaleById(id: String): SaleEntity?
    
    @Query("SELECT * FROM sales WHERE id = :id")
    fun getSaleByIdFlow(id: String): Flow<SaleEntity?>
    
    @Query("SELECT * FROM sales WHERE saleNumber = :saleNumber LIMIT 1")
    suspend fun getSaleBySaleNumber(saleNumber: String): SaleEntity?
    
    @Query("SELECT * FROM sales WHERE soldBy = :userId ORDER BY soldAt DESC")
    fun getSalesByUser(userId: String): Flow<List<SaleEntity>>
    
    @Query("""
        SELECT * FROM sales 
        WHERE soldAt >= :startDate AND soldAt <= :endDate 
        AND status = 'COMPLETED'
        ORDER BY soldAt DESC
    """)
    fun getSalesByDateRange(startDate: Long, endDate: Long): Flow<List<SaleEntity>>
    
    @Query("""
        SELECT * FROM sales 
        WHERE date(soldAt/1000, 'unixepoch', 'localtime') = date(:date/1000, 'unixepoch', 'localtime')
        AND status = 'COMPLETED'
        ORDER BY soldAt DESC
    """)
    fun getSalesByDate(date: Long): Flow<List<SaleEntity>>
    
    // Estadísticas diarias
    @Query("""
        SELECT COALESCE(SUM(total), 0) FROM sales 
        WHERE date(soldAt/1000, 'unixepoch', 'localtime') = date(:date/1000, 'unixepoch', 'localtime')
        AND status = 'COMPLETED'
    """)
    fun getDailySalesTotal(date: Long): Flow<Long>
    
    @Query("""
        SELECT COUNT(*) FROM sales 
        WHERE date(soldAt/1000, 'unixepoch', 'localtime') = date(:date/1000, 'unixepoch', 'localtime')
        AND status = 'COMPLETED'
    """)
    fun getDailySalesCount(date: Long): Flow<Int>
    
    // Estadísticas mensuales
    @Query("""
        SELECT COALESCE(SUM(total), 0) FROM sales 
        WHERE strftime('%Y-%m', soldAt/1000, 'unixepoch', 'localtime') = strftime('%Y-%m', :date/1000, 'unixepoch', 'localtime')
        AND status = 'COMPLETED'
    """)
    fun getMonthlySalesTotal(date: Long): Flow<Long>
    
    @Query("""
        SELECT COUNT(*) FROM sales 
        WHERE strftime('%Y-%m', soldAt/1000, 'unixepoch', 'localtime') = strftime('%Y-%m', :date/1000, 'unixepoch', 'localtime')
        AND status = 'COMPLETED'
    """)
    fun getMonthlySalesCount(date: Long): Flow<Int>
    
    // Estadísticas anuales
    @Query("""
        SELECT COALESCE(SUM(total), 0) FROM sales 
        WHERE strftime('%Y', soldAt/1000, 'unixepoch', 'localtime') = strftime('%Y', :date/1000, 'unixepoch', 'localtime')
        AND status = 'COMPLETED'
    """)
    fun getYearlySalesTotal(date: Long): Flow<Long>
    
    @Query("SELECT * FROM sales WHERE syncStatus != 0")
    suspend fun getUnsyncedSales(): List<SaleEntity>
    
    @Query("SELECT MAX(CAST(saleNumber AS INTEGER)) FROM sales")
    suspend fun getLastSaleNumber(): Int?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSales(sales: List<SaleEntity>)
    
    @Update
    suspend fun updateSale(sale: SaleEntity)
    
    @Query("UPDATE sales SET status = :status, cancelledAt = :cancelledAt, cancelledBy = :cancelledBy, cancellationReason = :reason WHERE id = :saleId")
    suspend fun cancelSale(saleId: String, status: String, cancelledAt: Long, cancelledBy: String, reason: String?)
    
    @Query("UPDATE sales SET syncStatus = :syncStatus WHERE id = :saleId")
    suspend fun updateSyncStatus(saleId: String, syncStatus: Int)
    
    @Delete
    suspend fun deleteSale(sale: SaleEntity)
    
    // ==================== ITEMS DE VENTA ====================
    
    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun getSaleItems(saleId: String): Flow<List<SaleItemEntity>>
    
    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun getSaleItemsSync(saleId: String): List<SaleItemEntity>
    
    @Query("SELECT * FROM sale_items WHERE productId = :productId ORDER BY saleId DESC")
    fun getSaleItemsByProduct(productId: String): Flow<List<SaleItemEntity>>
    
    @Query("""
        SELECT si.*, s.soldAt 
        FROM sale_items si 
        INNER JOIN sales s ON si.saleId = s.id 
        WHERE si.productId = :productId 
        AND s.status = 'COMPLETED'
        ORDER BY s.soldAt DESC 
        LIMIT :limit
    """)
    suspend fun getRecentSalesOfProduct(productId: String, limit: Int = 20): List<SaleItemEntity>
    
    // Ganancia por producto
    @Query("""
        SELECT SUM((si.unitPrice - si.purchasePrice) * si.quantity) 
        FROM sale_items si 
        INNER JOIN sales s ON si.saleId = s.id 
        WHERE si.productId = :productId 
        AND s.status = 'COMPLETED'
    """)
    suspend fun getProductProfit(productId: String): Long?
    
    // Cantidad total vendida de un producto
    @Query("""
        SELECT COALESCE(SUM(si.quantity), 0) 
        FROM sale_items si 
        INNER JOIN sales s ON si.saleId = s.id 
        WHERE si.productId = :productId 
        AND s.status = 'COMPLETED'
    """)
    suspend fun getProductTotalSold(productId: String): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItem(item: SaleItemEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)
    
    @Delete
    suspend fun deleteSaleItem(item: SaleItemEntity)
    
    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    suspend fun deleteSaleItemsBySale(saleId: String)
    
    // ==================== MOVIMIENTOS DE STOCK ====================
    
    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC")
    fun getStockMovements(productId: String): Flow<List<StockMovementEntity>>
    
    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentStockMovements(productId: String, limit: Int = 20): List<StockMovementEntity>
    
    @Query("SELECT * FROM stock_movements WHERE syncStatus != 0")
    suspend fun getUnsyncedStockMovements(): List<StockMovementEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovement(movement: StockMovementEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovements(movements: List<StockMovementEntity>)
    
    // ==================== RESUMEN DIARIO ====================
    
    @Query("SELECT * FROM daily_cash_summary WHERE date = :date LIMIT 1")
    suspend fun getDailySummary(date: String): DailyCashSummaryEntity?
    
    @Query("SELECT * FROM daily_cash_summary ORDER BY date DESC LIMIT :limit")
    fun getRecentDailySummaries(limit: Int = 30): Flow<List<DailyCashSummaryEntity>>
    
    @Query("SELECT * FROM daily_cash_summary WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getDailySummariesByRange(startDate: String, endDate: String): Flow<List<DailyCashSummaryEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailySummary(summary: DailyCashSummaryEntity)
    
    @Update
    suspend fun updateDailySummary(summary: DailyCashSummaryEntity)
    
    // ==================== GASTOS ====================
    
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>
    
    @Query("SELECT * FROM expenses WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getExpensesByDateRange(startDate: Long, endDate: Long): Flow<List<ExpenseEntity>>
    
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM expenses 
        WHERE date(date/1000, 'unixepoch', 'localtime') = date(:date/1000, 'unixepoch', 'localtime')
    """)
    fun getDailyExpensesTotal(date: Long): Flow<Long>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)
    
    @Update
    suspend fun updateExpense(expense: ExpenseEntity)
    
    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)
    
    // ==================== CLIENTES ====================
    
    @Query("SELECT * FROM customers WHERE isActive = 1 ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>
    
    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: String): CustomerEntity?
    
    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR ruc LIKE '%' || :query || '%' AND isActive = 1")
    suspend fun searchCustomers(query: String): List<CustomerEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)
    
    @Update
    suspend fun updateCustomer(customer: CustomerEntity)
    
    @Query("UPDATE customers SET totalPurchases = totalPurchases + :amount, purchaseCount = purchaseCount + 1, lastPurchaseAt = :purchaseDate WHERE id = :customerId")
    suspend fun updateCustomerPurchaseStats(customerId: String, amount: Long, purchaseDate: Long)
    
    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)
}
