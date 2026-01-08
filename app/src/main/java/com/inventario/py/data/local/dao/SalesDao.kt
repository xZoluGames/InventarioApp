package com.inventario.py.data.local.dao

import androidx.room.*
import com.inventario.py.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesDao {


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

    @Query("SELECT * FROM sales WHERE soldAt BETWEEN :startDate AND :endDate ORDER BY soldAt DESC")
    fun getSalesByDateRange(startDate: Long, endDate: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE soldAt BETWEEN :startDate AND :endDate ORDER BY soldAt DESC")
    suspend fun getSalesByDateRangeSync(startDate: Long, endDate: Long): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE soldBy = :userId ORDER BY soldAt DESC")
    fun getSalesByUser(userId: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE status = :status ORDER BY soldAt DESC")
    fun getSalesByStatus(status: String): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE syncStatus = 1")
    suspend fun getPendingSales(): List<SaleEntity>

    @Query("SELECT COUNT(*) FROM sales WHERE DATE(soldAt/1000, 'unixepoch', 'localtime') = DATE('now', 'localtime')")
    suspend fun getTodaySalesCount(): Int

    @Query("SELECT SUM(total) FROM sales WHERE soldAt BETWEEN :startDate AND :endDate AND status = 'COMPLETED'")
    fun getTotalSalesInRange(startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(total) FROM sales WHERE soldAt BETWEEN :startDate AND :endDate AND status = 'COMPLETED'")
    suspend fun getTotalSalesInRangeSync(startDate: Long, endDate: Long): Long?

    @Query("SELECT COUNT(*) FROM sales WHERE soldAt BETWEEN :startDate AND :endDate AND status = 'COMPLETED'")
    fun getSalesCountInRange(startDate: Long, endDate: Long): Flow<Int>

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
        SELECT si.* 
        FROM sale_items si 
        INNER JOIN sales s ON si.saleId = s.id 
        WHERE si.productId = :productId 
        AND s.status = 'COMPLETED'
        ORDER BY s.soldAt DESC 
        LIMIT :limit
    """)
    suspend fun getRecentSalesOfProduct(productId: String, limit: Int = 20): List<SaleItemEntity>

    @Query("""
        SELECT SUM((si.unitPrice - si.purchasePrice) * si.quantity) 
        FROM sale_items si 
        INNER JOIN sales s ON si.saleId = s.id 
        WHERE si.productId = :productId 
        AND s.status = 'COMPLETED'
    """)
    suspend fun getProductProfit(productId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItem(item: SaleItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Delete
    suspend fun deleteSaleItem(item: SaleItemEntity)

    @Query("DELETE FROM sale_items WHERE saleId = :saleId")
    suspend fun deleteSaleItemsBySale(saleId: String)

    // ==================== MOVIMIENTOS DE STOCK ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovement(movement: StockMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockMovements(movements: List<StockMovementEntity>)

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC")
    fun getStockMovementsByProduct(productId: String): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentStockMovements(productId: String, limit: Int = 20): List<StockMovementEntity>
}