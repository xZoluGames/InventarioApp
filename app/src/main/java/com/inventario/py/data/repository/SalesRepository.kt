package com.inventario.py.data.repository

import com.inventario.py.data.local.dao.ProductDao
import com.inventario.py.data.local.dao.SalesDao
import com.inventario.py.data.local.entity.*
import com.inventario.py.data.remote.api.InventarioApi
import com.inventario.py.ui.sales.SaleWithItems
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


    // ==================== VENTAS ====================

    fun getAllSales(): Flow<List<SaleEntity>> = salesDao.getAllSales()

    fun getRecentSales(limit: Int = 50): Flow<List<SaleEntity>> =
        salesDao.getRecentSales(limit)

    suspend fun getSaleById(id: String): SaleEntity? = salesDao.getSaleById(id)

    fun getSaleByIdFlow(id: String): Flow<SaleEntity?> = salesDao.getSaleByIdFlow(id)

    /**
     * Obtiene una venta con sus items
     */
    suspend fun getSaleWithItems(saleId: String): SaleWithItems? {
        val sale = salesDao.getSaleById(saleId) ?: return null
        val items = salesDao.getSaleItemsSync(saleId)
        return SaleWithItems(sale = sale, items = items)
    }

    fun getSalesByDateRange(startDate: Long, endDate: Long): Flow<List<SaleEntity>> =
        salesDao.getSalesByDateRange(startDate, endDate)

    fun getTodaySales(): Flow<List<SaleEntity>> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        return salesDao.getSalesByDateRange(startOfDay, System.currentTimeMillis())
    }

    /**
     * Genera un número de venta único
     */
    suspend fun generateSaleNumber(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val datePrefix = dateFormat.format(Date())
        val count = salesDao.getTodaySalesCount() + 1
        return "$datePrefix-${count.toString().padStart(4, '0')}"
    }

    /**
     * Crea una nueva venta
     */
    suspend fun createSale(sale: SaleEntity, items: List<SaleItemEntity>): String {
        salesDao.insertSale(sale)
        salesDao.insertSaleItems(items)
        return sale.id
    }

    suspend fun cancelSale(
        saleId: String,
        reason: String?
    ): Boolean = withContext(Dispatchers.IO) {
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
        salesDao.getProductProfit(productId) ?: 0L

    // ==================== ESTADÍSTICAS ====================

    fun getTotalSalesInRange(startDate: Long, endDate: Long): Flow<Long?> =
        salesDao.getTotalSalesInRange(startDate, endDate)

    fun getSalesCountInRange(startDate: Long, endDate: Long): Flow<Int> =
        salesDao.getSalesCountInRange(startDate, endDate)

    suspend fun getTodaySalesTotal(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        return salesDao.getTotalSalesInRangeSync(startOfDay, System.currentTimeMillis()) ?: 0L
    }

    // ==================== SINCRONIZACIÓN ====================

    suspend fun getPendingSales(): List<SaleEntity> =
        salesDao.getPendingSales()

    suspend fun updateSyncStatus(saleId: String, syncStatus: Int) {
        salesDao.updateSyncStatus(saleId, syncStatus)
    }
}