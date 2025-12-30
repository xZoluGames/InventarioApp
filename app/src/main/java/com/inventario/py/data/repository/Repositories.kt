package com.inventario.py.data.repository

import com.inventario.py.data.local.dao.*
import com.inventario.py.data.local.database.hashPassword
import com.inventario.py.data.local.entities.*
import com.inventario.py.domain.model.*
import com.inventario.py.utils.DateFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

// ========== Auth Repository ==========

@Singleton
class AuthRepository @Inject constructor(
    private val userDao: UserDao
) {
    suspend fun login(username: String, password: String): User? {
        val passwordHash = hashPassword(password)
        val userEntity = userDao.authenticate(username, passwordHash)
        return userEntity?.let {
            userDao.updateLastLogin(it.id)
            it.toDomain()
        }
    }

    suspend fun getUserById(id: Long): User? {
        return userDao.getUserById(id)?.toDomain()
    }

    suspend fun getUserByUsername(username: String): UserEntity? {
        return userDao.getUserByUsername(username)
    }

    fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getAllUsersList(): List<User> {
        return userDao.getAllUsersList().map { it.toDomain() }
    }

    suspend fun createUser(username: String, password: String, name: String, role: UserRole): Long {
        val userEntity = UserEntity(
            username = username,
            passwordHash = hashPassword(password),
            name = name,
            role = role
        )
        return userDao.insertUser(userEntity)
    }

    suspend fun updateUser(user: User) {
        val existing = userDao.getUserById(user.id)
        existing?.let {
            userDao.updateUser(it.copy(
                username = user.username,
                name = user.name,
                role = user.role,
                isActive = user.isActive
            ))
        }
    }

    suspend fun updatePassword(userId: Long, newPassword: String) {
        userDao.updatePassword(userId, hashPassword(newPassword))
    }

    suspend fun toggleUserActive(userId: Long) {
        val user = userDao.getUserById(userId)
        user?.let {
            userDao.updateUserActive(userId, !it.isActive)
        }
    }

    suspend fun deleteUser(userId: Long) {
        userDao.deleteUserById(userId)
    }

    suspend fun getUserCount(): Int {
        return userDao.getUserCount()
    }
}

// ========== Product Repository ==========

@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao,
    private val historyDao: ProductHistoryDao
) {
    fun getAllProducts(): Flow<List<Product>> {
        return productDao.getAllProducts().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getAllProductsList(): List<Product> {
        return productDao.getAllProductsList().map { it.toDomain() }
    }

    suspend fun getProductById(id: Long): Product? {
        return productDao.getProductById(id)?.toDomain()
    }

    fun getProductByIdFlow(id: Long): Flow<Product?> {
        return productDao.getProductByIdFlow(id).map { it?.toDomain() }
    }

    suspend fun getProductByBarcode(barcode: String): Product? {
        return productDao.getProductByBarcode(barcode)?.toDomain()
    }

    suspend fun getProductByIdentifier(identifier: String): Product? {
        return productDao.getProductByIdentifier(identifier)?.toDomain()
    }

    fun searchProducts(query: String): Flow<List<Product>> {
        return productDao.searchProducts(query).map { list -> list.map { it.toDomain() } }
    }

    fun getLowStockProducts(): Flow<List<Product>> {
        return productDao.getLowStockProducts().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getLowStockProductsList(): List<Product> {
        return productDao.getLowStockProductsList().map { it.toDomain() }
    }

    fun getInStockProducts(): Flow<List<Product>> {
        return productDao.getInStockProducts().map { list -> list.map { it.toDomain() } }
    }

    fun getOutOfStockProducts(): Flow<List<Product>> {
        return productDao.getOutOfStockProducts().map { list -> list.map { it.toDomain() } }
    }

    fun getProductsByCategory(category: String): Flow<List<Product>> {
        return productDao.getProductsByCategory(category).map { list -> list.map { it.toDomain() } }
    }

    fun getAllCategories(): Flow<List<String>> {
        return productDao.getAllCategories()
    }

    suspend fun insertProduct(product: Product, userId: Long): Long {
        val entity = product.toEntity()
        val id = productDao.insertProduct(entity)
        
        historyDao.insertHistory(ProductHistoryEntity(
            productId = id,
            action = HistoryAction.CREATED,
            description = "Producto creado",
            userId = userId
        ))
        
        return id
    }

    suspend fun updateProduct(product: Product, userId: Long) {
        val oldProduct = productDao.getProductById(product.id)
        val entity = product.toEntity().copy(lastModified = Date(), syncStatus = SyncStatus.PENDING)
        productDao.updateProduct(entity)
        
        if (oldProduct != null && oldProduct.price != product.price) {
            historyDao.insertHistory(ProductHistoryEntity(
                productId = product.id,
                action = HistoryAction.PRICE_CHANGED,
                description = "Precio actualizado",
                oldValue = oldProduct.price.toString(),
                newValue = product.price.toString(),
                userId = userId
            ))
        }
        
        if (oldProduct != null && oldProduct.stock != product.stock) {
            historyDao.insertHistory(ProductHistoryEntity(
                productId = product.id,
                action = HistoryAction.STOCK_ADJUSTED,
                description = "Stock ajustado",
                oldValue = oldProduct.stock.toString(),
                newValue = product.stock.toString(),
                userId = userId
            ))
        }
    }

    suspend fun updateStock(productId: Long, newStock: Int, userId: Long) {
        val oldProduct = productDao.getProductById(productId)
        productDao.updateStock(productId, newStock)
        
        if (oldProduct != null) {
            historyDao.insertHistory(ProductHistoryEntity(
                productId = productId,
                action = HistoryAction.STOCK_ADJUSTED,
                description = "Stock ajustado",
                oldValue = oldProduct.stock.toString(),
                newValue = newStock.toString(),
                userId = userId
            ))
        }
    }

    suspend fun deleteProduct(productId: Long, userId: Long) {
        productDao.softDeleteProduct(productId)
        historyDao.insertHistory(ProductHistoryEntity(
            productId = productId,
            action = HistoryAction.DELETED,
            description = "Producto eliminado",
            userId = userId
        ))
    }

    fun getProductCount(): Flow<Int> {
        return productDao.getProductCount()
    }

    suspend fun getProductCountValue(): Int {
        return productDao.getProductCountValue()
    }

    suspend fun getLowStockCount(): Int {
        return productDao.getLowStockCount()
    }

    suspend fun getInStockCount(): Int {
        return productDao.getInStockCount()
    }

    suspend fun getOutOfStockCount(): Int {
        return productDao.getOutOfStockCount()
    }

    fun getTotalInventoryValue(): Flow<Long?> {
        return productDao.getTotalInventoryValue()
    }

    fun getProductHistory(productId: Long): Flow<List<ProductHistory>> {
        return historyDao.getProductHistory(productId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun getProductHistoryList(productId: Long): List<ProductHistory> {
        return historyDao.getProductHistoryList(productId).map { it.toDomain() }
    }

    suspend fun getPendingProducts(): List<ProductEntity> {
        return productDao.getProductsBySyncStatus(SyncStatus.PENDING)
    }

    suspend fun markAsSynced(productId: Long, serverId: Long) {
        productDao.updateServerIdAndStatus(productId, serverId, SyncStatus.SYNCED)
    }
}

// ========== Sale Repository ==========

@Singleton
class SaleRepository @Inject constructor(
    private val saleDao: SaleDao,
    private val productDao: ProductDao,
    private val cartDao: CartDao,
    private val historyDao: ProductHistoryDao
) {
    fun getAllSales(): Flow<List<Sale>> {
        return saleDao.getAllSales().map { list -> 
            list.map { saleEntity ->
                val items = saleDao.getSaleItems(saleEntity.id)
                SaleWithItems(saleEntity, items).toDomain()
            }
        }
    }

    suspend fun getAllSalesList(): List<Sale> {
        return saleDao.getAllSalesList().map { saleEntity ->
            val items = saleDao.getSaleItems(saleEntity.id)
            SaleWithItems(saleEntity, items).toDomain()
        }
    }

    suspend fun getSaleById(id: Long): Sale? {
        return saleDao.getSaleWithItems(id)?.toDomain()
    }

    fun getSalesByDateRange(startDate: Date, endDate: Date): Flow<List<Sale>> {
        return saleDao.getSalesByDateRange(startDate, endDate).map { list ->
            list.map { saleEntity ->
                val items = saleDao.getSaleItems(saleEntity.id)
                SaleWithItems(saleEntity, items).toDomain()
            }
        }
    }

    suspend fun getSalesByDateRangeList(startDate: Date, endDate: Date): List<Sale> {
        return saleDao.getSalesByDateRangeList(startDate, endDate).map { saleEntity ->
            val items = saleDao.getSaleItems(saleEntity.id)
            SaleWithItems(saleEntity, items).toDomain()
        }
    }

    suspend fun getTodaySales(): List<Sale> {
        val start = DateFormatter.getStartOfDay()
        val end = DateFormatter.getEndOfDay()
        return getSalesByDateRangeList(start, end)
    }

    suspend fun getWeekSales(): List<Sale> {
        val start = DateFormatter.getStartOfWeek()
        val end = DateFormatter.getEndOfDay()
        return getSalesByDateRangeList(start, end)
    }

    suspend fun getMonthSales(): List<Sale> {
        val start = DateFormatter.getStartOfMonth()
        val end = DateFormatter.getEndOfDay()
        return getSalesByDateRangeList(start, end)
    }

    suspend fun getTodayTotal(): Long {
        val start = DateFormatter.getStartOfDay()
        val end = DateFormatter.getEndOfDay()
        return saleDao.getTotalByDateRange(start, end) ?: 0L
    }

    suspend fun getTodayProfit(): Long {
        val start = DateFormatter.getStartOfDay()
        val end = DateFormatter.getEndOfDay()
        return saleDao.getProfitByDateRange(start, end) ?: 0L
    }

    suspend fun getTodayCount(): Int {
        val start = DateFormatter.getStartOfDay()
        val end = DateFormatter.getEndOfDay()
        return saleDao.getSalesCountByDateRange(start, end)
    }

    suspend fun getMonthTotal(): Long {
        val start = DateFormatter.getStartOfMonth()
        val end = DateFormatter.getEndOfDay()
        return saleDao.getTotalByDateRange(start, end) ?: 0L
    }

    suspend fun getMonthProfit(): Long {
        val start = DateFormatter.getStartOfMonth()
        val end = DateFormatter.getEndOfDay()
        return saleDao.getProfitByDateRange(start, end) ?: 0L
    }

    suspend fun getMonthCount(): Int {
        val start = DateFormatter.getStartOfMonth()
        val end = DateFormatter.getEndOfDay()
        return saleDao.getSalesCountByDateRange(start, end)
    }

    suspend fun getYearTotal(): Long {
        val start = DateFormatter.getStartOfYear()
        val end = DateFormatter.getEndOfDay()
        return saleDao.getTotalByDateRange(start, end) ?: 0L
    }

    suspend fun createSale(
        employeeId: Long,
        employeeName: String,
        paymentMethod: PaymentMethod,
        notes: String?
    ): Long {
        val cartItems = cartDao.getAllCartItemsList()
        if (cartItems.isEmpty()) return -1

        val total = cartItems.sumOf { it.unitPrice * it.quantity }
        val totalCost = cartItems.sumOf { it.unitCost * it.quantity }
        val profit = total - totalCost

        val saleEntity = SaleEntity(
            total = total,
            totalCost = totalCost,
            profit = profit,
            employeeId = employeeId,
            employeeName = employeeName,
            paymentMethod = paymentMethod,
            notes = notes
        )

        val saleItems = cartItems.map { cartItem ->
            SaleItemEntity(
                saleId = 0, // Will be set by insertSaleWithItems
                productId = cartItem.productId,
                productName = cartItem.productName,
                barcode = cartItem.barcode,
                unitPrice = cartItem.unitPrice,
                unitCost = cartItem.unitCost,
                quantity = cartItem.quantity,
                selectedType = cartItem.selectedType,
                selectedCapacity = cartItem.selectedCapacity,
                selectedColor = cartItem.selectedColor
            )
        }

        val saleId = saleDao.insertSaleWithItems(saleEntity, saleItems)

        // Update stock and record history
        for (cartItem in cartItems) {
            val product = productDao.getProductById(cartItem.productId)
            if (product != null) {
                val newStock = (product.stock - cartItem.quantity).coerceAtLeast(0)
                productDao.updateStock(cartItem.productId, newStock)
                
                historyDao.insertHistory(ProductHistoryEntity(
                    productId = cartItem.productId,
                    action = HistoryAction.SOLD,
                    description = "Vendido: ${cartItem.quantity} unidades",
                    oldValue = product.stock.toString(),
                    newValue = newStock.toString(),
                    userId = employeeId
                ))
            }
        }

        // Clear cart
        cartDao.clearCart()

        return saleId
    }

    suspend fun deleteSale(saleId: Long) {
        saleDao.deleteSaleWithItems(saleId)
    }

    suspend fun getPendingSales(): List<SaleEntity> {
        return saleDao.getSalesBySyncStatus(SyncStatus.PENDING)
    }

    suspend fun markAsSynced(saleId: Long) {
        saleDao.updateSyncStatus(saleId, SyncStatus.SYNCED)
    }

    // Cart operations
    fun getCartItems(): Flow<List<CartItem>> {
        return cartDao.getAllCartItems().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getCartItemsList(): List<CartItem> {
        return cartDao.getAllCartItemsList().map { it.toDomain() }
    }

    fun getCartItemCount(): Flow<Int> {
        return cartDao.getCartItemCount()
    }

    fun getCartTotal(): Flow<Long?> {
        return cartDao.getCartTotal()
    }

    suspend fun addToCart(product: Product, quantity: Int = 1, type: String? = null, capacity: String? = null, color: String? = null): Long {
        val existing = cartDao.getCartItemByProductAndVariant(product.id, type, capacity, color)
        
        return if (existing != null) {
            val newQuantity = (existing.quantity + quantity).coerceAtMost(product.stock)
            cartDao.updateQuantity(existing.id, newQuantity)
            existing.id
        } else {
            val additionalPrice = (product.types.find { it.name == type }?.additionalPrice ?: 0L) +
                    (product.capacities.find { it.name == capacity }?.additionalPrice ?: 0L)
            
            val cartItem = CartItemEntity(
                productId = product.id,
                productName = product.name,
                barcode = product.barcode,
                imagePath = product.imagePath,
                unitPrice = product.price + additionalPrice,
                unitCost = product.cost,
                quantity = quantity,
                maxQuantity = product.stock,
                selectedType = type,
                selectedCapacity = capacity,
                selectedColor = color
            )
            cartDao.insertCartItem(cartItem)
        }
    }

    suspend fun updateCartItemQuantity(itemId: Long, quantity: Int) {
        if (quantity <= 0) {
            cartDao.deleteCartItemById(itemId)
        } else {
            cartDao.updateQuantity(itemId, quantity)
        }
    }

    suspend fun removeFromCart(itemId: Long) {
        cartDao.deleteCartItemById(itemId)
    }

    suspend fun clearCart() {
        cartDao.clearCart()
    }
}
