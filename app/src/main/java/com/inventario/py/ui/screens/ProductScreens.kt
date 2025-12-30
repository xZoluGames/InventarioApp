package com.inventario.py.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventario.py.domain.model.Product
import com.inventario.py.domain.model.ProductVariant
import com.inventario.py.ui.viewmodel.AuthViewModel
import com.inventario.py.ui.viewmodel.CartViewModel
import com.inventario.py.ui.viewmodel.ProductViewModel
import com.inventario.py.utils.formatGuaranies

// ========== Products Screen ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    initialFilter: String? = null,
    onNavigateToProduct: (Long) -> Unit,
    onNavigateToAddProduct: () -> Unit,
    onNavigateToScanner: () -> Unit,
    viewModel: ProductViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val products by viewModel.filteredProducts.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(initialFilter) {
        when (initialFilter) {
            "low_stock" -> viewModel.filterByLowStock()
            "in_stock" -> viewModel.filterByInStock()
            "out_of_stock" -> viewModel.filterByOutOfStock()
            else -> viewModel.loadProducts()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Productos") },
                actions = {
                    IconButton(onClick = onNavigateToScanner) {
                        Icon(Icons.Default.QrCodeScanner, "Escanear")
                    }
                    if (currentUser?.canEditProducts == true) {
                        IconButton(onClick = onNavigateToAddProduct) {
                            Icon(Icons.Default.Add, "Agregar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchProducts(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Buscar productos...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.searchProducts("")
                        }) {
                            Icon(Icons.Default.Clear, "Limpiar")
                        }
                    }
                },
                singleLine = true
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !searchState.hasActiveFilter,
                    onClick = { viewModel.clearFilters() },
                    label = { Text("Todos") }
                )
                FilterChip(
                    selected = searchState.isLowStockFilter,
                    onClick = { viewModel.filterByLowStock() },
                    label = { Text("Stock Bajo") }
                )
                FilterChip(
                    selected = searchState.isOutOfStockFilter,
                    onClick = { viewModel.filterByOutOfStock() },
                    label = { Text("Sin Stock") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (products.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No se encontraron productos")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(products) { product ->
                        ProductCard(
                            product = product,
                            showCost = currentUser?.canViewCosts == true,
                            onClick = { onNavigateToProduct(product.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductCard(
    product: Product,
    showCost: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium
                )
                product.identifier?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatGuaranies(product.price),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (showCost) {
                    Text(
                        text = "Costo: ${formatGuaranies(product.cost)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val stockColor = when {
                    product.isOutOfStock -> MaterialTheme.colorScheme.error
                    product.isLowStock -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                Text(
                    text = "Stock: ${product.stock}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = stockColor
                )
            }
        }
    }
}

// ========== Product Detail Screen ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onAddToCart: () -> Unit,
    viewModel: ProductViewModel = hiltViewModel(),
    cartViewModel: CartViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val product by viewModel.selectedProduct.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedCapacity by remember { mutableStateOf<String?>(null) }
    var selectedColor by remember { mutableStateOf<String?>(null) }
    var quantity by remember { mutableStateOf(1) }

    LaunchedEffect(productId) {
        viewModel.loadProduct(productId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle del Producto") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    if (currentUser?.canEditProducts == true) {
                        IconButton(onClick = onNavigateToEdit) {
                            Icon(Icons.Default.Edit, "Editar")
                        }
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, "Historial")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (product == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Producto no encontrado")
            }
        } else {
            val p = product!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(p.name, style = MaterialTheme.typography.headlineMedium)
                }

                item {
                    p.identifier?.let {
                        Text("ID: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    p.barcode?.let {
                        Text("Código: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                item {
                    if (p.description.isNotEmpty()) {
                        Text(p.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Precio", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    formatGuaranies(p.price),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (currentUser?.canViewCosts == true) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Costo", style = MaterialTheme.typography.bodyMedium)
                                    Text(formatGuaranies(p.cost))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Ganancia", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        formatGuaranies(p.profit),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    val stockColor = when {
                        p.isOutOfStock -> MaterialTheme.colorScheme.error
                        p.isLowStock -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Stock Disponible")
                            Text(
                                "${p.stock} unidades",
                                color = stockColor,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                // Variants
                if (p.types.isNotEmpty()) {
                    item {
                        Text("Tipo", style = MaterialTheme.typography.titleSmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            p.types.forEach { variant ->
                                FilterChip(
                                    selected = selectedType == variant.name,
                                    onClick = { selectedType = variant.name },
                                    label = { 
                                        Text(
                                            if (variant.additionalPrice > 0) 
                                                "${variant.name} (+${formatGuaranies(variant.additionalPrice)})"
                                            else variant.name
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                if (p.capacities.isNotEmpty()) {
                    item {
                        Text("Capacidad", style = MaterialTheme.typography.titleSmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            p.capacities.forEach { variant ->
                                FilterChip(
                                    selected = selectedCapacity == variant.name,
                                    onClick = { selectedCapacity = variant.name },
                                    label = { 
                                        Text(
                                            if (variant.additionalPrice > 0) 
                                                "${variant.name} (+${formatGuaranies(variant.additionalPrice)})"
                                            else variant.name
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                if (p.colors.isNotEmpty()) {
                    item {
                        Text("Color", style = MaterialTheme.typography.titleSmall)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            p.colors.forEach { color ->
                                FilterChip(
                                    selected = selectedColor == color,
                                    onClick = { selectedColor = color },
                                    label = { Text(color) }
                                )
                            }
                        }
                    }
                }

                // Quantity
                if (p.stock > 0) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (quantity > 1) quantity-- },
                                enabled = quantity > 1
                            ) {
                                Icon(Icons.Default.Remove, "Menos")
                            }
                            Text(
                                "$quantity",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            IconButton(
                                onClick = { if (quantity < p.stock) quantity++ },
                                enabled = quantity < p.stock
                            ) {
                                Icon(Icons.Default.Add, "Más")
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                cartViewModel.addToCart(p, quantity, selectedType, selectedCapacity, selectedColor)
                                onAddToCart()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Agregar al Carrito")
                        }
                    }
                } else {
                    item {
                        Text(
                            "Sin stock disponible",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ========== Add/Edit Product Screen ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    productId: Long?,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToScanner: () -> Unit,
    viewModel: ProductViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val isEditing = productId != null && productId > 0L
    val product by viewModel.selectedProduct.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }
    var minStock by remember { mutableStateOf("5") }

    LaunchedEffect(productId) {
        if (isEditing) {
            viewModel.loadProduct(productId!!)
        }
    }

    LaunchedEffect(product) {
        if (isEditing && product != null) {
            name = product!!.name
            description = product!!.description
            barcode = product!!.barcode ?: ""
            identifier = product!!.identifier ?: ""
            price = product!!.price.toString()
            cost = product!!.cost.toString()
            stock = product!!.stock.toString()
            minStock = product!!.minStock.toString()
        }
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            viewModel.resetSaveSuccess()
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar Producto" else "Nuevo Producto") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToScanner) {
                        Icon(Icons.Default.QrCodeScanner, "Escanear")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = name.isBlank()
                )
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Código de Barras") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = identifier,
                        onValueChange = { identifier = it },
                        label = { Text("Identificador") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it.filter { c -> c.isDigit() } },
                        label = { Text("Precio *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = price.isBlank(),
                        suffix = { Text("Gs.") }
                    )
                    OutlinedTextField(
                        value = cost,
                        onValueChange = { cost = it.filter { c -> c.isDigit() } },
                        label = { Text("Costo") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        suffix = { Text("Gs.") }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = stock,
                        onValueChange = { stock = it.filter { c -> c.isDigit() } },
                        label = { Text("Stock *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = stock.isBlank()
                    )
                    OutlinedTextField(
                        value = minStock,
                        onValueChange = { minStock = it.filter { c -> c.isDigit() } },
                        label = { Text("Stock Mínimo") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val newProduct = Product(
                            id = productId ?: 0L,
                            name = name,
                            description = description,
                            barcode = barcode.ifBlank { null },
                            identifier = identifier.ifBlank { null },
                            price = price.toLongOrNull() ?: 0L,
                            cost = cost.toLongOrNull() ?: 0L,
                            stock = stock.toIntOrNull() ?: 0,
                            minStock = minStock.toIntOrNull() ?: 5
                        )
                        viewModel.saveProduct(newProduct, currentUser?.id ?: 0L)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && name.isNotBlank() && price.isNotBlank() && stock.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isEditing) "Guardar Cambios" else "Crear Producto")
                    }
                }
            }
        }
    }
}

// ========== Product History Screen ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductHistoryScreen(
    productId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ProductViewModel = hiltViewModel()
) {
    val product by viewModel.selectedProduct.collectAsState()

    LaunchedEffect(productId) {
        viewModel.loadProduct(productId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Historial de ${product?.name ?: "Producto"}")
                Text(
                    "Función en desarrollo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
