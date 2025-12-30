package com.inventario.py.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.CategoryEntity
import com.inventario.py.data.local.entity.ProductEntity
import com.inventario.py.data.local.entity.ProductVariantEntity
import com.inventario.py.data.local.entity.VariantType
import com.inventario.py.data.repository.ProductRepository
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
    val filteredProducts: List<ProductEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: CategoryEntity? = null,
    val showLowStockOnly: Boolean = false,
    val sortBy: ProductSortBy = ProductSortBy.NAME_ASC
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
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductsUiState())
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProductEvent>()
    val events: SharedFlow<ProductEvent> = _events.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        loadProducts()
        loadCategories()
        setupSearch()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            productRepository.getAllProducts().collectLatest { products ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    products = products
                )
                applyFilters()
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            productRepository.getAllCategories().collectLatest { categories ->
                _uiState.value = _uiState.value.copy(categories = categories)
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

    fun onCategorySelected(category: CategoryEntity?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        applyFilters()
    }

    fun onShowLowStockOnlyChanged(show: Boolean) {
        _uiState.value = _uiState.value.copy(showLowStockOnly = show)
        applyFilters()
    }

    fun onSortByChanged(sortBy: ProductSortBy) {
        _uiState.value = _uiState.value.copy(sortBy = sortBy)
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
                product.identifier?.lowercase()?.contains(query) == true ||
                product.description?.lowercase()?.contains(query) == true
            }
        }

        // Filtrar por categoría
        state.selectedCategory?.let { category ->
            filtered = filtered.filter { it.categoryId == category.id }
        }

        // Filtrar por bajo stock
        if (state.showLowStockOnly) {
            filtered = filtered.filter { it.totalStock <= it.minStockAlert }
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

        _uiState.value = state.copy(filteredProducts = filtered)
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            try {
                productRepository.deleteProduct(product)
                _events.emit(ProductEvent.ProductDeleted(product))
            } catch (e: Exception) {
                _events.emit(ProductEvent.Error("Error al eliminar producto: ${e.message}"))
            }
        }
    }

    suspend fun getProductByBarcode(barcode: String): ProductEntity? {
        return productRepository.getProductByBarcode(barcode)
    }

    suspend fun getProductByIdentifier(identifier: String): ProductEntity? {
        return productRepository.getProductByIdentifier(identifier)
    }

    fun refreshProducts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            productRepository.syncProducts()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
}

// ViewModel para agregar/editar producto
@HiltViewModel
class ProductFormViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductFormUiState())
    val uiState: StateFlow<ProductFormUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ProductFormEvent>()
    val events: SharedFlow<ProductFormEvent> = _events.asSharedFlow()

    fun loadProduct(productId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            productRepository.getProductById(productId)?.let { product ->
                val variants = productRepository.getVariantsByProductId(productId)
                _uiState.value = ProductFormUiState(
                    isEditing = true,
                    productId = productId,
                    name = product.name,
                    description = product.description ?: "",
                    barcode = product.barcode ?: "",
                    identifier = product.identifier ?: "",
                    categoryId = product.categoryId,
                    salePrice = product.salePrice.toString(),
                    purchasePrice = product.purchasePrice?.toString() ?: "",
                    minStockAlert = product.minStockAlert.toString(),
                    quality = product.quality ?: "",
                    supplierId = product.supplierId,
                    imageUrl = product.imageUrl,
                    variants = variants.toMutableList(),
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

    fun addVariant(type: VariantType, label: String, value: String, stock: Int, additionalPrice: Long = 0) {
        val newVariant = ProductVariantEntity(
            productId = _uiState.value.productId ?: "",
            variantType = type,
            variantLabel = label,
            variantValue = value,
            stock = stock,
            additionalPrice = additionalPrice
        )
        val variants = _uiState.value.variants.toMutableList()
        variants.add(newVariant)
        _uiState.value = _uiState.value.copy(variants = variants)
    }

    fun removeVariant(variant: ProductVariantEntity) {
        val variants = _uiState.value.variants.toMutableList()
        variants.remove(variant)
        _uiState.value = _uiState.value.copy(variants = variants)
    }

    fun updateVariantStock(variantIndex: Int, stock: Int) {
        val variants = _uiState.value.variants.toMutableList()
        if (variantIndex in variants.indices) {
            variants[variantIndex] = variants[variantIndex].copy(stock = stock)
            _uiState.value = _uiState.value.copy(variants = variants)
        }
    }

    fun saveProduct() {
        val state = _uiState.value
        
        // Validaciones
        var hasError = false
        var newState = state

        if (state.name.isBlank()) {
            newState = newState.copy(nameError = "El nombre es requerido")
            hasError = true
        }

        val salePrice = state.salePrice.toLongOrNull()
        if (salePrice == null || salePrice <= 0) {
            newState = newState.copy(salePriceError = "Ingrese un precio válido")
            hasError = true
        }

        if (hasError) {
            _uiState.value = newState
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)

            try {
                val totalStock = if (state.variants.isEmpty()) {
                    state.initialStock.toIntOrNull() ?: 0
                } else {
                    state.variants.sumOf { it.stock }
                }

                val product = ProductEntity(
                    id = state.productId ?: java.util.UUID.randomUUID().toString(),
                    name = state.name.trim(),
                    description = state.description.takeIf { it.isNotBlank() },
                    barcode = state.barcode.takeIf { it.isNotBlank() },
                    identifier = state.identifier.takeIf { it.isNotBlank() },
                    imageUrl = state.imageUrl,
                    categoryId = state.categoryId,
                    totalStock = totalStock,
                    minStockAlert = state.minStockAlert.toIntOrNull() ?: 5,
                    salePrice = salePrice!!,
                    purchasePrice = state.purchasePrice.toLongOrNull(),
                    supplierId = state.supplierId,
                    quality = state.quality.takeIf { it.isNotBlank() },
                    createdAt = if (state.isEditing) System.currentTimeMillis() else System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                if (state.isEditing) {
                    productRepository.updateProduct(product)
                } else {
                    productRepository.insertProduct(product)
                }

                // Guardar variantes
                if (state.variants.isNotEmpty()) {
                    val variantsWithProductId = state.variants.map { 
                        it.copy(productId = product.id)
                    }
                    productRepository.saveVariants(product.id, variantsWithProductId)
                }

                _uiState.value = state.copy(isSaving = false)
                _events.emit(ProductFormEvent.Saved)

            } catch (e: Exception) {
                _uiState.value = state.copy(isSaving = false)
                _events.emit(ProductFormEvent.Error("Error al guardar: ${e.message}"))
            }
        }
    }
}

data class ProductFormUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
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
    val suppliers: List<com.inventario.py.data.local.entity.SupplierEntity> = emptyList()
)

sealed class ProductFormEvent {
    object Saved : ProductFormEvent()
    data class Error(val message: String) : ProductFormEvent()
}
