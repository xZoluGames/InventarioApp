package com.inventario.py.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.inventario.py.data.local.dao.*
import com.inventario.py.data.local.entities.*
import com.inventario.py.domain.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

@Database(
    entities = [
        ProductEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        UserEntity::class,
        CartItemEntity::class,
        ProductHistoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class InventarioDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun userDao(): UserDao
    abstract fun cartDao(): CartDao
    abstract fun productHistoryDao(): ProductHistoryDao

    companion object {
        const val DATABASE_NAME = "inventario_database"

        @Volatile
        private var INSTANCE: InventarioDatabase? = null

        fun getInstance(context: Context): InventarioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InventarioDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    // Crear usuario dueño por defecto
                    val defaultOwner = UserEntity(
                        username = "admin",
                        passwordHash = hashPassword("admin123"),
                        name = "Administrador",
                        role = UserRole.OWNER
                    )
                    database.userDao().insertUser(defaultOwner)

                    // Crear algunos productos de ejemplo
                    val sampleProducts = listOf(
                        ProductEntity(
                            name = "Memory Card SanDisk 64GB",
                            description = "Tarjeta de memoria clase 10",
                            identifier = "MEM-SD-64",
                            price = 120000,
                            cost = 80000,
                            stock = 10,
                            minStock = 3,
                            category = "Memorias",
                            capacities = listOf(
                                ProductVariantEntity("64GB", 0),
                                ProductVariantEntity("128GB", 50000)
                            )
                        ),
                        ProductEntity(
                            name = "Vidrio Templado A12",
                            description = "Protector de pantalla premium",
                            identifier = "VID-A12",
                            price = 25000,
                            cost = 12000,
                            stock = 20,
                            minStock = 5,
                            category = "Accesorios",
                            types = listOf(
                                ProductVariantEntity("Transparente", 0),
                                ProductVariantEntity("Anticurioso", 10000)
                            )
                        ),
                        ProductEntity(
                            name = "Power Bank 20000mAh",
                            description = "Cargador portátil de alta capacidad",
                            identifier = "PB-20K",
                            price = 180000,
                            cost = 120000,
                            stock = 5,
                            minStock = 2,
                            category = "Cargadores",
                            capacities = listOf(
                                ProductVariantEntity("20000mAh", 0),
                                ProductVariantEntity("30000mAh", 60000)
                            ),
                            colors = listOf("Negro", "Blanco", "Azul")
                        )
                    )
                    database.productDao().insertProducts(sampleProducts)
                }
            }
        }
    }
}

fun hashPassword(password: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
