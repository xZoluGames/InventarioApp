package com.inventario.py.ui.products

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.*
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.utils.Generators
import com.inventario.py.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class AddProductUiState(
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val isDataLoaded: Boolean = false,
    val product: ProductWithVariants? = null,
    val variants: List<ProductVariantEntity> = emptyList(),
    val imageUri: String? = null
)

sealed class AddProductEvent {
    data class ProductSaved(val productId: String) : AddProductEvent()
    data class Error(val message: String) : AddProductEvent()
    data class ValidationError(val errors: List<String>) : AddProductEvent()
}

@HiltViewModel
class AddProductViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddProductUiState())
    val uiState: StateFlow<AddProductUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddProductEvent>()
    val events: SharedFlow<AddProductEvent> = _events.asSharedFlow()

    private val _categories = MutableLiveData<List<CategoryEntity>>()
    val categories: LiveData<List<CategoryEntity>> = _categories

    private val _suppliers = MutableLiveData<List<SupplierEntity>>()
    val suppliers: LiveData<List<SupplierEntity>> = _suppliers

    private var editingProductId: String? = null
    private var currentImagePath: String? = null

    init {
        loadCategories()
        loadSuppliers()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            productRepository.getAllCategories().collectLatest { categoryList ->
                _categories.value = categoryList
            }
        }
    }

    private fun loadSuppliers() {
        viewModelScope.launch {
            productRepository.getAllSuppliers().collectLatest { supplierList ->
                _suppliers.value = supplierList
            }
        }
    }

    fun loadProduct(productId: String) {
        editingProductId = productId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isEditing = true)

            try {
                val product = productRepository.getProductById(productId)
                val variants = productRepository.getVariantsByProductId(productId)

                if (product != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        product = ProductWithVariants(product, variants),
                        variants = variants,
                        imageUri = product.imageUrl
                    )
                } else {
                    _events.emit(AddProductEvent.Error("Producto no encontrado"))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.emit(AddProductEvent.Error(e.message ?: "Error al cargar producto"))
            }
        }
    }

    fun markDataAsLoaded() {
        _uiState.value = _uiState.value.copy(isDataLoaded = true)
    }

    fun setImageUri(uri: String) {
        _uiState.value = _uiState.value.copy(imageUri = uri)
    }

    fun createImageUri(context: Context): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "PRODUCT_${timeStamp}.jpg"

        val storageDir = File(context.filesDir, "product_images")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val imageFile = File(storageDir, imageFileName)
        currentImagePath = imageFile.absolutePath

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    fun addVariant(
        variantType: String,
        variantLabel: String,
        variantValue: String,
        stock: Int,
        additionalPrice: Long,
        barcode: String?
    ) {
        val newVariant = ProductVariantEntity(
            id = Generators.generateId(),
            productId = editingProductId ?: "",
            variantType = variantType,
            variantLabel = variantLabel,
            variantValue = variantValue,
            stock = stock,
            additionalPrice = additionalPrice,
            barcode = barcode
        )

        _uiState.value = _uiState.value.copy(
            variants = _uiState.value.variants + newVariant
        )
    }

    fun removeVariant(variant: ProductVariantEntity) {
        _uiState.value = _uiState.value.copy(
            variants = _uiState.value.variants.filter { it.id != variant.id }
        )
    }

    fun saveProduct(
        name: String,
        description: String?,
        barcode: String?,
        identifier: String?,
        stock: Int,
        minStock: Int,
        salePrice: Long,
        purchasePrice: Long,
        categoryId: String?,
        supplierId: String?
    ) {
        viewModelScope.launch {
            // Validaciones
            val errors = mutableListOf<String>()

            if (name.isBlank()) {
                errors.add("El nombre es requerido")
            }
            if (salePrice <= 0) {
                errors.add("El precio de venta debe ser mayor a 0")
            }

            if (errors.isNotEmpty()) {
                _events.emit(AddProductEvent.ValidationError(errors))
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                sessionManager.getUserId()
                val supplierEntity = supplierId?.let { productRepository.getSupplierById(it) }

                if (editingProductId != null) {
                    // Actualizar producto existente
                    val existingProduct = productRepository.getProductById(editingProductId!!)
                    if (existingProduct != null) {
                        val updatedProduct = existingProduct.copy(
                            name = name,
                            description = description,
                            barcode = barcode,
                            identifier = identifier ?: existingProduct.identifier,
                            totalStock = stock,
                            minStockAlert = minStock,
                            salePrice = salePrice,
                            purchasePrice = purchasePrice,
                            categoryId = categoryId,
                            supplierId = supplierId,
                            supplierName = supplierEntity?.name,
                            imageUrl = _uiState.value.imageUri ?: existingProduct.imageUrl,
                            updatedAt = System.currentTimeMillis()
                        )

                        productRepository.updateProduct(updatedProduct)

                        // Actualizar variantes
                        saveVariants(editingProductId!!)

                        _uiState.value = _uiState.value.copy(isLoading = false)
                        _events.emit(AddProductEvent.ProductSaved(editingProductId!!))
                    }
                } else {
                    // Crear nuevo producto
                    val newProduct = productRepository.createProduct(
                        name = name,
                        description = description,
                        barcode = barcode,
                        identifier = identifier,
                        categoryId = categoryId,
                        totalStock = stock,
                        minStockAlert = minStock,
                        salePrice = salePrice,
                        purchasePrice = purchasePrice,
                        supplierId = supplierId,
                        supplierName = supplierEntity?.name,
                        quality = null,
                        imageUrl = _uiState.value.imageUri
                    )

                    // Guardar variantes
                    saveVariants(newProduct.id)

                    _uiState.value = _uiState.value.copy(isLoading = false)
                    _events.emit(AddProductEvent.ProductSaved(newProduct.id))
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _events.emit(AddProductEvent.Error(e.message ?: "Error al guardar producto"))
            }
        }
    }

    private suspend fun saveVariants(productId: String) {
        val variants = _uiState.value.variants

        for (variant in variants) {
            val variantToSave = variant.copy(productId = productId)
            productRepository.saveVariant(variantToSave)
        }
    }
}
