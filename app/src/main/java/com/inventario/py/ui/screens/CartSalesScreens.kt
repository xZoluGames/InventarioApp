package com.inventario.py.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.inventario.py.domain.model.PaymentMethod
import com.inventario.py.domain.model.Sale
import com.inventario.py.ui.viewmodel.AuthViewModel
import com.inventario.py.ui.viewmodel.CartViewModel
import com.inventario.py.ui.viewmodel.SaleViewModel
import com.inventario.py.utils.formatGuaranies
import com.inventario.py.utils.toFormattedDateTime
import java.text.SimpleDateFormat
import java.util.*

// ========== Cart Screen ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onNavigateToProducts: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onSaleCompleted: () -> Unit,
    viewModel: CartViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val cartItems by viewModel.cartItems.collectAsState()
    val cartTotal by viewModel.cartTotal.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val saleCompleted by viewModel.saleCompleted.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    var showPaymentDialog by remember { mutableStateOf(false) }
    var selectedPaymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(saleCompleted) {
        if (saleCompleted) {
            viewModel.resetSaleCompleted()
            onSaleCompleted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Carrito (${cartItems.size})") },
                actions = {
                    IconButton(onClick = onNavigateToScanner) {
                        Icon(Icons.Default.QrCodeScanner, "Escanear")
                    }
                    if (cartItems.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearCart() }) {
                            Icon(Icons.Default.DeleteSweep, "Vaciar")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (cartItems.isNotEmpty()) {
                Surface(
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total", style = MaterialTheme.typography.titleMedium)
                            Text(
                                formatGuaranies(cartTotal),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showPaymentDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Finalizar Venta")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (cartItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("El carrito está vacío")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onNavigateToProducts) {
                        Text("Ver Productos")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cartItems) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.productName, style = MaterialTheme.typography.titleMedium)
                                item.selectedType?.let {
                                    Text("Tipo: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                item.selectedCapacity?.let {
                                    Text("Capacidad: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                item.selectedColor?.let {
                                    Text("Color: $it", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    formatGuaranies(item.unitPrice),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.decrementQuantity(item) }
                                ) {
                                    Icon(Icons.Default.Remove, "Menos")
                                }
                                Text(
                                    "${item.quantity}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                IconButton(
                                    onClick = { viewModel.incrementQuantity(item) },
                                    enabled = item.quantity < item.maxQuantity
                                ) {
                                    Icon(Icons.Default.Add, "Más")
                                }
                                IconButton(
                                    onClick = { viewModel.removeItem(item.id) }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        "Eliminar",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                "Subtotal: ${formatGuaranies(item.subtotal)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Payment Dialog
        if (showPaymentDialog) {
            AlertDialog(
                onDismissRequest = { showPaymentDialog = false },
                title = { Text("Método de Pago") },
                text = {
                    Column {
                        PaymentMethod.values().forEach { method ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedPaymentMethod == method,
                                    onClick = { selectedPaymentMethod = method }
                                )
                                Text(
                                    when (method) {
                                        PaymentMethod.CASH -> "Efectivo"
                                        PaymentMethod.CARD -> "Tarjeta"
                                        PaymentMethod.QR -> "QR"
                                        PaymentMethod.TRANSFER -> "Transferencia"
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notas (opcional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPaymentDialog = false
                            viewModel.completeSale(
                                employeeId = currentUser?.id ?: 0L,
                                employeeName = currentUser?.name ?: "Desconocido",
                                paymentMethod = selectedPaymentMethod,
                                notes = notes.ifBlank { null }
                            )
                        }
                    ) {
                        Text("Confirmar Venta")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPaymentDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

// ========== Sales Screen ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    onNavigateToSale: (Long) -> Unit,
    onNavigateToReports: () -> Unit,
    viewModel: SaleViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val sales by viewModel.sales.collectAsState()
    val todayStats by viewModel.todayStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Hoy", "Semana", "Mes")

    LaunchedEffect(Unit) {
        viewModel.loadTodaySales()
        viewModel.loadWeekSales()
        viewModel.loadMonthSales()
        viewModel.loadTodayStats()
    }

    val displayedSales by remember(selectedTab) {
        derivedStateOf {
            when (selectedTab) {
                0 -> viewModel.todaySales.value
                1 -> viewModel.weekSales.value
                2 -> viewModel.monthSales.value
                else -> emptyList()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ventas") },
                actions = {
                    IconButton(onClick = onNavigateToReports) {
                        Icon(Icons.Default.BarChart, "Reportes")
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
            // Stats Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ventas Hoy", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${todayStats.todaySalesCount}",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ingresos", style = MaterialTheme.typography.bodySmall)
                        Text(
                            formatGuaranies(todayStats.todayRevenue),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Sales List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (displayedSales.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No hay ventas en este período")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedSales) { sale ->
                        SaleCard(
                            sale = sale,
                            showProfit = currentUser?.canViewCosts == true,
                            onClick = { onNavigateToSale(sale.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleCard(
    sale: Sale,
    showProfit: Boolean = false,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PY"))
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    dateFormat.format(sale.date),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    when (sale.paymentMethod) {
                        PaymentMethod.CASH -> "Efectivo"
                        PaymentMethod.CARD -> "Tarjeta"
                        PaymentMethod.QR -> "QR"
                        PaymentMethod.TRANSFER -> "Transferencia"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${sale.totalItems} items")
                Text(
                    formatGuaranies(sale.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (showProfit) {
                Text(
                    "Ganancia: ${formatGuaranies(sale.profit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                "Por: ${sale.employeeName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ========== Sale Detail Screen ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleDetailScreen(
    saleId: Long,
    onNavigateBack: () -> Unit,
    viewModel: SaleViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val sale by viewModel.selectedSale.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PY"))

    LaunchedEffect(saleId) {
        viewModel.loadSale(saleId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle de Venta") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        if (sale == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val s = sale!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Venta #${s.id}", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Fecha: ${dateFormat.format(s.date)}")
                            Text("Vendedor: ${s.employeeName}")
                            Text(
                                "Método: ${when (s.paymentMethod) {
                                    PaymentMethod.CASH -> "Efectivo"
                                    PaymentMethod.CARD -> "Tarjeta"
                                    PaymentMethod.QR -> "QR"
                                    PaymentMethod.TRANSFER -> "Transferencia"
                                }}"
                            )
                            s.notes?.let {
                                Text("Notas: $it")
                            }
                        }
                    }
                }

                item {
                    Text("Items", style = MaterialTheme.typography.titleMedium)
                }

                items(s.items) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.productName)
                                Text(
                                    "${item.quantity} x ${formatGuaranies(item.unitPrice)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(formatGuaranies(item.subtotal))
                        }
                    }
                }

                item {
                    Divider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total", style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatGuaranies(s.total),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (currentUser?.canViewCosts == true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Costo")
                            Text(formatGuaranies(s.totalCost))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Ganancia")
                            Text(
                                formatGuaranies(s.profit),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
