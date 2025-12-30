package com.inventario.py.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.inventario.py.data.local.dao.*
import com.inventario.py.data.local.entity.*

@Database(
    entities = [
        // Usuarios
        UserEntity::class,
        
        // Productos
        ProductEntity::class,
        ProductVariantEntity::class,
        ProductImageEntity::class,
        ProductPriceHistoryEntity::class,
        CategoryEntity::class,
        SupplierEntity::class,
        
        // Ventas
        CartItemEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        StockMovementEntity::class,
        DailyCashSummaryEntity::class,
        ExpenseEntity::class,
        CustomerEntity::class,
        
        // Sincronización
        SyncQueueEntity::class,
        
        // Configuración
        SettingEntity::class,
        BusinessInfoEntity::class,
        PriceHistoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class InventarioDatabase : RoomDatabase() {
    
    abstract fun userDao(): UserDao
    abstract fun productDao(): ProductDao
    abstract fun salesDao(): SalesDao
    abstract fun cartDao(): CartDao
    abstract fun syncQueueDao(): SyncQueueDao
    
    companion object {
        const val DATABASE_NAME = "inventario_db"
    }
}

// Alias for compatibility
typealias AppDatabase = InventarioDatabase
