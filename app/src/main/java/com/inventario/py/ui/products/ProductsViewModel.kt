package com.inventario.py.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.CategoryEntity
import com.inventario.py.data.local.entity.ProductEntity
import com.inventario.py.data.local.entity.ProductVariantEntity
import com.inventario.py.data.local.entity.ProductWithVariants
import com.inventario.py.data.local.entity.SortOption
import com.inventario.py.data.local.entity.StockFilter
import com.inventario.py.data.local.entity.SupplierEntity
import com.inventario.py.data.local.entity.VariantType
import com.inventario.py.data.repository.CartRepository
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SalesRepository
import com.inventario.py.utils.Generators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductsUiState(
    val isLoading: Boolean = false,
    val products: List<ProductEntity> = emptyList(),
    val filteredProducts: List<ProductWithVariants> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: CategoryEntity? = null,
    val showLowStockOnly: Boolean = false,
    val sortBy: ProductSortBy = ProductSortBy.NAME_ASC,
    val stockFilter: StockFilter = StockFilter.ALL,
    val sortOption: SortOption = SortOption.NAME_ASC
)

enum class ProductSortBy {
    NAME_ASC, NAME_DESC, STOCK_ASC, STOCK_DESC, PRICE_ASC, PRICE_DESC, RECENT
}

sealed class ProductEvent {
    data class ProductDeleted(val product: ProductEntity) : ProductEvent()
    data class Error(val message: String) : ProductEvent()
    object ProductSaved : ProductEvent()
}

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val salesRepository: CartRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductsUiState())
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProductEvent>()
    val events: SharedFlow<ProductEvent> = _events.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")

    // StateFlows públicos para observar desde el Fragment
    private val _products = MutableStateFlow<List<ProductWithVariants>>(emptyList())
    val products: StateFlow<List<ProductWithVariants>> = _products.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _productCount = MutableStateFlow(0)
    val productCount: StateFlow<Int> = _productCount.asStateFlow()

    private val _addToCartResult = MutableStateFlow<Boolean?>(null)
    val addToCartResult: StateFlow<Boolean?> = _addToCartResult.asStateFlow()

    init {
        loadProducts()
        loadCategories()
        setupSearch()
    }

    fun loadProducts() {
        viewModelScope.launch {
            _isLoading.value = true
            _uiState.value = _uiState.value.copy(isLoading = true)
            productRepository.getAllProducts().collectLatest { products ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    products = products
                )
                _productCount.value = products.size
                applyFilters()
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            productRepository.getAllCategories().collectLatest { categoriesList ->
                _uiState.value = _uiState.value.copy(categories = categoriesList)
                _categories.value = categoriesList.map { it.name }
            }
        }
    }

    private fun setupSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .collectLatest { query ->
                    _uiState.value = _uiState.value.copy(searchQuery = query)
                    applyFilters()
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: CategoryEntity?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        applyFilters()
    }

    fun setCategoryFilter(categoryName: String?) {
        val category = if (categoryName != null) {
            _uiState.value.categories.find { it.name == categoryName }
        } else null
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        applyFilters()
    }

    fun onShowLowStockOnlyChanged(show: Boolean) {
        _uiState.value = _uiState.value.copy(showLowStockOnly = show)
        applyFilters()
    }

    fun setStockFilter(filter: StockFilter) {
        _uiState.value = _uiState.value.copy(
            stockFilter = filter,
            showLowStockOnly = filter == StockFilter.LOW_STOCK
        )
        applyFilters()
    }

    fun onSortByChanged(sortBy: ProductSortBy) {
        _uiState.value = _uiState.value.copy(sortBy = sortBy)
        applyFilters()
    }

    fun setSortOption(option: SortOption) {
        val sortBy = when (option) {
            SortOption.NAME_ASC -> ProductSortBy.NAME_ASC
            SortOption.NAME_DESC -> ProductSortBy.NAME_DESC
            SortOption.PRICE_ASC -> ProductSortBy.PRICE_ASC
            SortOption.PRICE_DESC -> ProductSortBy.PRICE_DESC
            SortOption.STOCK_ASC -> ProductSortBy.STOCK_ASC
            SortOption.STOCK_DESC -> ProductSortBy.STOCK_DESC
            SortOption.DATE_ASC, SortOption.DATE_DESC -> ProductSortBy.RECENT
        }
        _uiState.value = _uiState.value.copy(sortBy = sortBy, sortOption = option)
        applyFilters()
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            selectedCategory = null,
            showLowStockOnly = false,
            stockFilter = StockFilter.ALL,
            sortBy = ProductSortBy.NAME_ASC
        )
        _searchQuery.value = ""
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        var filtered = state.products

        // Filtrar por búsqueda
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase()
            filtered = filtered.filter { product ->
                product.name.lowercase().contains(query) ||
                        product.barcode?.lowercase()?.contains(query) == true ||
                        product.identifier.lowercase().contains(query) ||
                        product.description?.lowercase()?.contains(query) == true
            }
        }

        // Filtrar por categoría
        state.selectedCategory?.let { category ->
            filtered = filtered.filter { it.categoryId == category.id }
        }

        // Filtrar por stock
        filtered = when (state.stockFilter) {
            StockFilter.ALL -> filtered
            StockFilter.IN_STOCK -> filtered.filter { it.totalStock > it.minStockAlert }
            StockFilter.LOW_STOCK -> filtered.filter { it.totalStock in 1..it.minStockAlert }
            StockFilter.OUT_OF_STOCK -> filtered.filter { it.totalStock <= 0 }
        }

        // Ordenar
        filtered = when (state.sortBy) {
            ProductSortBy.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            ProductSortBy.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            ProductSortBy.STOCK_ASC -> filtered.sortedBy { it.totalStock }
            ProductSortBy.STOCK_DESC -> filtered.sortedByDescending { it.totalStock }
            ProductSortBy.PRICE_ASC -> filtered.sortedBy { it.salePrice }
            ProductSortBy.PRICE_DESC -> filtered.sortedByDescending { it.salePrice }
            ProductSortBy.RECENT -> filtered.sortedByDescending { it.createdAt }
        }

        // Convertir a ProductWithVariants
        val productsWithVariants = filtered.map { product ->
            ProductWithVariants(product = product, variants = emptyList())
        }

        _uiState.value = state.copy(filteredProducts = productsWithVariants)
        _products.value = productsWithVariants
        _isLoading.value = false
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            try {
                productRepository.deleteProduct(product.id)
                _events.emit(ProductEvent.ProductDeleted(product))
            } catch (e: Exception) {
                _events.emit(ProductEvent.Error("Error al eliminar producto: ${e.message}"))
            }
        }
    }

    fun addToCart(product: ProductWithVariants) {
        viewModelScope.launch {
            try {
                salesRepository.addToCart(product.product, null, 1)
                _addToCartResult.value = true
            } catch (e: Exception) {
                _addToCartResult.value = false
            }
        }
    }

    fun clearAddToCartResult() {
        _addToCartResult.value = null
    }

    suspend fun getProductByBarcode(barcode: String): ProductEntity? {
        return productRepository.getProductByBarcode(barcode)
    }

    suspend fun getProductByIdentifier(identifier: String): ProductEntity? {
        return productRepository.getProductByIdentifier(identifier)
    }
}

// ==================== PRODUCT FORM VIEW MODEL ====================

data class ProductFormUiState(
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val productId: String? = null,
    val name: String = "",
    val nameError: String? = null,
    val description: String = "",
    val barcode: String = "",
    val identifier: String = "",
    val categoryId: String? = null,
    val salePrice: String = "",
    val salePriceError: String? = null,
    val purchasePrice: String = "",
    val minStockAlert: String = "5",
    val initialStock: String = "0",
    val quality: String = "",
    val supplierId: String? = null,
    val imageUrl: String? = null,
    val variants: List<ProductVariantEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val suppliers: List<SupplierEntity> = emptyList()
)

sealed class ProductFormEvent {
    object Saved : ProductFormEvent()
    data class Error(val message: String) : ProductFormEvent()
}

@HiltViewModel
class ProductFormViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductFormUiState())
    val uiState: StateFlow<ProductFormUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProductFormEvent>()
    val events: SharedFlow<ProductFormEvent> = _events.asSharedFlow()

    init {
        loadCategories()
        loadSuppliers()
    }

    fun loadProduct(productId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val product = productRepository.getProductById(productId)
            val variants = productRepository.getVariantsByProductSync(productId)

            if (product != null) {
                _uiState.value = _uiState.value.copy(
                    isEditing = true,
                    productId = productId,
                    name = product.name,
                    description = product.description ?: "",
                    barcode = product.barcode ?: "",
                    identifier = product.identifier,
                    categoryId = product.categoryId,
                    salePrice = product.salePrice.toString(),
                    purchasePrice = product.purchasePrice.toString(),
                    minStockAlert = product.minStockAlert.toString(),
                    quality = product.quality ?: "",
                    supplierId = product.supplierId,
                    imageUrl = product.imageUrl,
                    variants = variants,
                    isLoading = false
                )
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            productRepository.getAllCategories().collectLatest { categories ->
                _uiState.value = _uiState.value.copy(categories = categories)
            }
        }
    }

    fun loadSuppliers() {
        viewModelScope.launch {
            productRepository.getAllSuppliers().collectLatest { suppliers ->
                _uiState.value = _uiState.value.copy(suppliers = suppliers)
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name, nameError = null)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updateBarcode(barcode: String) {
        _uiState.value = _uiState.value.copy(barcode = barcode)
    }

    fun updateIdentifier(identifier: String) {
        _uiState.value = _uiState.value.copy(identifier = identifier)
    }

    fun updateSalePrice(price: String) {
        _uiState.value = _uiState.value.copy(salePrice = price, salePriceError = null)
    }

    fun updatePurchasePrice(price: String) {
        _uiState.value = _uiState.value.copy(purchasePrice = price)
    }

    fun updateMinStock(stock: String) {
        _uiState.value = _uiState.value.copy(minStockAlert = stock)
    }

    fun updateQuality(quality: String) {
        _uiState.value = _uiState.value.copy(quality = quality)
    }

    fun updateCategory(categoryId: String?) {
        _uiState.value = _uiState.value.copy(categoryId = categoryId)
    }

    fun updateSupplier(supplierId: String?) {
        _uiState.value = _uiState.value.copy(supplierId = supplierId)
    }

    fun updateImageUrl(url: String?) {
        _uiState.value = _uiState.value.copy(imageUrl = url)
    }

    fun addVariant(type: String, label: String, value: String, stock: Int, additionalPrice: Long = 0) {
        val newVariant = ProductVariantEntity(
            id = Generators.generateId(),
            productId = _uiState.value.productId ?: "",
            variantType = type,
            variantLabel = label,
            variantValue = value,
            stock = stock,
            additionalPrice = additionalPrice,
            barcode = null
        )
        val updatedVariants = _uiState.value.variants + newVariant
        _uiState.value = _uiState.value.copy(variants = updatedVariants)
    }

    fun removeVariant(variant: ProductVariantEntity) {
        val updatedVariants = _uiState.value.variants.filter { it.id != variant.id }
        _uiState.value = _uiState.value.copy(variants = updatedVariants)
    }

    fun saveProduct() {
        val state = _uiState.value

        // Validaciones
        if (state.name.isBlank()) {
            _uiState.value = state.copy(nameError = "El nombre es requerido")
            return
        }

        if (state.salePrice.isBlank() || state.salePrice.toLongOrNull() == null) {
            _uiState.value = state.copy(salePriceError = "El precio de venta es requerido")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)

            try {
                val product = ProductEntity(
                    id = state.productId ?: Generators.generateId(),
                    name = state.name,
                    description = state.description.ifBlank { null },
                    barcode = state.barcode.ifBlank { null },
                    identifier = state.identifier.ifBlank { Generators.generateIdentifier() },
                    categoryId = state.categoryId,
                    totalStock = state.initialStock.toIntOrNull() ?: 0,
                    minStockAlert = state.minStockAlert.toIntOrNull() ?: 5,
                    salePrice = state.salePrice.toLongOrNull() ?: 0,
                    purchasePrice = state.purchasePrice.toLongOrNull() ?: 0,
                    supplierId = state.supplierId,
                    quality = state.quality.ifBlank { null },
                    imageUrl = state.imageUrl,
                    syncStatus = ProductEntity.SYNC_STATUS_PENDING
                )

                productRepository.saveProduct(product)

                // Guardar variantes
                state.variants.forEach { variant ->
                    productRepository.saveVariant(variant.copy(productId = product.id))
                }

                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.emit(ProductFormEvent.Saved)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.emit(ProductFormEvent.Error("Error al guardar: ${e.message}"))
            }
        }
    }
}