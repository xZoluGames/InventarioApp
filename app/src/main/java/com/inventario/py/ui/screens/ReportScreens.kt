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
import com.inventario.py.ui.viewmodel.SaleViewModel
import com.inventario.py.utils.formatGuaranies
import java.text.SimpleDateFormat
import java.util.*

// ========== Reports Screen ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDailyReport: () -> Unit,
    onNavigateToMonthlyReport: () -> Unit,
    onNavigateToAnnualReport: () -> Unit,
    viewModel: SaleViewModel = hiltViewModel()
) {
    val todayStats by viewModel.todayStats.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadTodayStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reportes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Resumen Rápido", style = MaterialTheme.typography.titleMedium)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Ventas Hoy", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${todayStats.todaySalesCount}",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Ingresos", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatGuaranies(todayStats.todayRevenue),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Ventas del Mes", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${todayStats.monthSalesCount}",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Ingresos del Mes", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatGuaranies(todayStats.monthRevenue),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Reportes Detallados", style = MaterialTheme.typography.titleMedium)
            }

            item {
                Card(
                    onClick = onNavigateToDailyReport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Today,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Reporte Diario", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Ventas del día actual",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            item {
                Card(
                    onClick = onNavigateToMonthlyReport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Reporte Mensual", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Resumen del mes actual",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            item {
                Card(
                    onClick = onNavigateToAnnualReport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Reporte Anual", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Resumen del año",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

// ========== Daily Report Screen ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportScreen(
    onNavigateBack: () -> Unit,
    viewModel: SaleViewModel = hiltViewModel()
) {
    val todaySales by viewModel.todaySales.collectAsState()
    val todayStats by viewModel.todayStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val dateFormat = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("es", "PY"))

    LaunchedEffect(Unit) {
        viewModel.loadTodaySales()
        viewModel.loadTodayStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reporte Diario") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    dateFormat.format(Date()),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Ventas", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${todayStats.todaySalesCount}",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Ingresos", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatGuaranies(todayStats.todayRevenue),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Ganancia", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatGuaranies(todayStats.todayProfit),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Detalle de Ventas", style = MaterialTheme.typography.titleMedium)
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (todaySales.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No hay ventas registradas hoy",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(todaySales) { sale ->
                    val timeFormat = SimpleDateFormat("HH:mm", Locale("es", "PY"))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(timeFormat.format(sale.date))
                                Text(
                                    "${sale.totalItems} items",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                formatGuaranies(sale.total),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ========== Monthly Report Screen ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScreen(
    onNavigateBack: () -> Unit,
    viewModel: SaleViewModel = hiltViewModel()
) {
    val monthSales by viewModel.monthSales.collectAsState()
    val todayStats by viewModel.todayStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("es", "PY"))

    LaunchedEffect(Unit) {
        viewModel.loadMonthSales()
        viewModel.loadTodayStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reporte Mensual") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    monthFormat.format(Date()).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleLarge
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Ventas", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${todayStats.monthSalesCount}",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Ingresos", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatGuaranies(todayStats.monthRevenue),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Ganancia Total", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatGuaranies(todayStats.monthProfit),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Promedios", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        val avgPerSale = if (todayStats.monthSalesCount > 0) 
                            todayStats.monthRevenue / todayStats.monthSalesCount 
                        else 0L
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Promedio por venta")
                            Text(formatGuaranies(avgPerSale))
                        }
                    }
                }
            }

            item {
                Text("Últimas Ventas del Mes", style = MaterialTheme.typography.titleMedium)
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(monthSales.take(10)) { sale ->
                    val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale("es", "PY"))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(dateFormat.format(sale.date))
                                Text(
                                    "${sale.totalItems} items",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                formatGuaranies(sale.total),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ========== Annual Report Screen ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnualReportScreen(
    onNavigateBack: () -> Unit,
    viewModel: SaleViewModel = hiltViewModel()
) {
    val todayStats by viewModel.todayStats.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadTodayStats()
    }

    val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reporte Anual") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Año $currentYear",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Reporte Anual",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Esta función estará disponible próximamente",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text("Datos del Mes Actual", style = MaterialTheme.typography.titleMedium)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Ventas del Mes")
                            Text("${todayStats.monthSalesCount}")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Ingresos del Mes")
                            Text(
                                formatGuaranies(todayStats.monthRevenue),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Ganancia del Mes")
                            Text(
                                formatGuaranies(todayStats.monthProfit),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
