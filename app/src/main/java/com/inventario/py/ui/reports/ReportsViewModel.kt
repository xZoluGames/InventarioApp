package com.inventario.py.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.*
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SalesRepository
import com.inventario.py.utils.toGuaraniFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class ReportPeriod(
    val start: Long,
    val end: Long,
    val label: String
)

data class SalesReportData(
    val period: ReportPeriod,
    val totalSales: Long = 0L,
    val totalTransactions: Int = 0,
    val totalProfit: Long = 0L,
    val averageTicket: Long = 0L,
    val salesByPaymentMethod: Map<PaymentMethod, Long> = emptyMap(),
    val salesByDay: List<DailySales> = emptyList(),
    val topProducts: List<ProductSalesData> = emptyList(),
    val salesList: List<SaleEntity> = emptyList()
)

data class DailySales(
    val date: Long,
    val dateLabel: String,
    val total: Long,
    val transactions: Int
)

data class ProductSalesData(
    val product: ProductEntity,
    val quantitySold: Int,
    val totalRevenue: Long,
    val totalProfit: Long
)

data class InventoryReportData(
    val totalProducts: Int = 0,
    val totalStock: Int = 0,
    val inventoryValue: Long = 0L,
    val lowStockProducts: List<ProductEntity> = emptyList(),
    val outOfStockProducts: List<ProductEntity> = emptyList(),
    val productsByCategory: Map<String, List<ProductEntity>> = emptyMap()
)

data class ReportsUiState(
    val isLoading: Boolean = false,
    val selectedReportType: ReportType = ReportType.SALES,
    val selectedPeriod: PeriodType = PeriodType.TODAY,
    val customStartDate: Long? = null,
    val customEndDate: Long? = null,
    val salesReport: SalesReportData? = null,
    val inventoryReport: InventoryReportData? = null,
    val isExporting: Boolean = false,
    val exportedFilePath: String? = null
)

enum class ReportType {
    SALES, INVENTORY, PROFIT
}

enum class PeriodType {
    TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, CUSTOM
}

sealed class ReportEvent {
    data class ExportSuccess(val filePath: String) : ReportEvent()
    data class ExportError(val message: String) : ReportEvent()
    data class Error(val message: String) : ReportEvent()
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val salesRepository: SalesRepository,
    private val productRepository: ProductRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ReportEvent>()
    val events: SharedFlow<ReportEvent> = _events.asSharedFlow()

    init {
        generateReport()
    }

    fun setReportType(type: ReportType) {
        _uiState.value = _uiState.value.copy(selectedReportType = type)
        generateReport()
    }

    fun setPeriod(period: PeriodType) {
        _uiState.value = _uiState.value.copy(selectedPeriod = period)
        generateReport()
    }

    fun setCustomDateRange(start: Long, end: Long) {
        _uiState.value = _uiState.value.copy(
            selectedPeriod = PeriodType.CUSTOM,
            customStartDate = start,
            customEndDate = end
        )
        generateReport()
    }

    private fun getReportPeriod(): ReportPeriod {
        val state = _uiState.value
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PY"))

        return when (state.selectedPeriod) {
            PeriodType.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                ReportPeriod(start, now, "Hoy - ${dateFormat.format(Date(start))}")
            }
            PeriodType.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val start = calendar.timeInMillis
                ReportPeriod(start, now, "Esta semana")
            }
            PeriodType.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val start = calendar.timeInMillis
                val monthName = SimpleDateFormat("MMMM yyyy", Locale("es", "PY")).format(Date(start))
                ReportPeriod(start, now, monthName.replaceFirstChar { it.uppercase() })
            }
            PeriodType.THIS_YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val start = calendar.timeInMillis
                val year = SimpleDateFormat("yyyy", Locale("es", "PY")).format(Date(start))
                ReportPeriod(start, now, "Año $year")
            }
            PeriodType.CUSTOM -> {
                val start = state.customStartDate ?: now
                val end = state.customEndDate ?: now
                ReportPeriod(
                    start, end,
                    "${dateFormat.format(Date(start))} - ${dateFormat.format(Date(end))}"
                )
            }
        }
    }

    fun generateReport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                when (_uiState.value.selectedReportType) {
                    ReportType.SALES, ReportType.PROFIT -> generateSalesReport()
                    ReportType.INVENTORY -> generateInventoryReport()
                }
            } catch (e: Exception) {
                _events.emit(ReportEvent.Error("Error al generar reporte: ${e.message}"))
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private suspend fun generateSalesReport() {
        val period = getReportPeriod()
        
        salesRepository.getSalesByDateRange(period.start, period.end)
            .first()
            .let { sales ->
                val completedSales = sales.filter { it.status == SaleStatus.COMPLETED.name }
                
                // Calcular estadísticas
                val totalSales = completedSales.sumOf { it.totalAmount }
                val totalTransactions = completedSales.size
                val averageTicket = if (totalTransactions > 0) totalSales / totalTransactions else 0L

                // Ventas por método de pago
                val salesByPayment = completedSales.groupBy { it.paymentMethod }
                    .mapValues { (_, sales) -> sales.sumOf { it.totalAmount } }

                // Ventas por día
                val dateFormat = SimpleDateFormat("dd/MM", Locale("es", "PY"))
                val salesByDay = completedSales
                    .groupBy { 
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = it.createdAt
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        cal.timeInMillis
                    }
                    .map { (date, daySales) ->
                        DailySales(
                            date = date,
                            dateLabel = dateFormat.format(Date(date)),
                            total = daySales.sumOf { it.totalAmount },
                            transactions = daySales.size
                        )
                    }
                    .sortedBy { it.date }

                // Top productos (necesita cargar items de venta)
                val topProducts = calculateTopProducts(completedSales)

                // Calcular ganancia
                val totalProfit = calculateTotalProfit(completedSales)

                _uiState.value = _uiState.value.copy(
                    salesReport = SalesReportData(
                        period = period,
                        totalSales = totalSales,
                        totalTransactions = totalTransactions,
                        totalProfit = totalProfit,
                        averageTicket = averageTicket,
                        salesByPaymentMethod = salesByPayment,
                        salesByDay = salesByDay,
                        topProducts = topProducts,
                        salesList = completedSales
                    )
                )
            }
    }

    private suspend fun calculateTopProducts(sales: List<SaleEntity>): List<ProductSalesData> {
        val productSales = mutableMapOf<String, Pair<Int, Long>>()
        val productProfits = mutableMapOf<String, Long>()

        for (sale in sales) {
            salesRepository.getSaleItems(sale.id).forEach { item ->
                val current = productSales[item.productId] ?: Pair(0, 0L)
                productSales[item.productId] = Pair(
                    current.first + item.quantity,
                    current.second + item.subtotal
                )
                
                val purchasePrice = item.purchasePrice ?: 0L
                val profit = item.subtotal - (purchasePrice * item.quantity)
                productProfits[item.productId] = (productProfits[item.productId] ?: 0L) + profit
            }
        }

        return productSales.mapNotNull { (productId, data) ->
            productRepository.getProductById(productId)?.let { product ->
                ProductSalesData(
                    product = product,
                    quantitySold = data.first,
                    totalRevenue = data.second,
                    totalProfit = productProfits[productId] ?: 0L
                )
            }
        }.sortedByDescending { it.totalRevenue }.take(10)
    }

    private suspend fun calculateTotalProfit(sales: List<SaleEntity>): Long {
        var totalProfit = 0L
        for (sale in sales) {
            salesRepository.getSaleItems(sale.id).forEach { item ->
                val purchasePrice = item.purchasePrice ?: 0L
                totalProfit += item.subtotal - (purchasePrice * item.quantity)
            }
        }
        return totalProfit
    }

    private suspend fun generateInventoryReport() {
        productRepository.getAllProducts().first().let { products ->
            val lowStock = products.filter { it.totalStock in 1..it.minStockAlert }
            val outOfStock = products.filter { it.totalStock <= 0 }
            val inventoryValue = products.sumOf { it.salePrice * it.totalStock }
            
            val categories = productRepository.getAllCategories().first()
            val productsByCategory = products.groupBy { product ->
                categories.find { it.id == product.categoryId }?.name ?: "Sin categoría"
            }

            _uiState.value = _uiState.value.copy(
                inventoryReport = InventoryReportData(
                    totalProducts = products.size,
                    totalStock = products.sumOf { it.totalStock },
                    inventoryValue = inventoryValue,
                    lowStockProducts = lowStock,
                    outOfStockProducts = outOfStock,
                    productsByCategory = productsByCategory
                )
            )
        }
    }

    fun exportToExcel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)

            try {
                val filePath = withContext(Dispatchers.IO) {
                    when (_uiState.value.selectedReportType) {
                        ReportType.SALES, ReportType.PROFIT -> exportSalesReportToExcel()
                        ReportType.INVENTORY -> exportInventoryReportToExcel()
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportedFilePath = filePath
                )
                _events.emit(ReportEvent.ExportSuccess(filePath))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isExporting = false)
                _events.emit(ReportEvent.ExportError("Error al exportar: ${e.message}"))
            }
        }
    }

    private fun exportSalesReportToExcel(): String {
        val report = _uiState.value.salesReport ?: throw Exception("No hay datos de reporte")
        val workbook = XSSFWorkbook()

        // Estilos
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
                fontHeightInPoints = 12
            })
            alignment = HorizontalAlignment.CENTER
        }

        val currencyStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("#,##0")
            alignment = HorizontalAlignment.RIGHT
        }

        val titleStyle = workbook.createCellStyle().apply {
            setFont(workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 16
            })
        }

        // Hoja de Resumen
        val summarySheet = workbook.createSheet("Resumen")
        var rowNum = 0

        // Título
        summarySheet.createRow(rowNum++).createCell(0).apply {
            setCellValue("REPORTE DE VENTAS - ${report.period.label}")
            cellStyle = titleStyle
        }
        summarySheet.addMergedRegion(CellRangeAddress(0, 0, 0, 3))
        rowNum++

        // Estadísticas generales
        val statsData = listOf(
            listOf("Total Ventas", report.totalSales.toGuaraniFormat()),
            listOf("Total Transacciones", report.totalTransactions.toString()),
            listOf("Ticket Promedio", report.averageTicket.toGuaraniFormat()),
            listOf("Ganancia Total", report.totalProfit.toGuaraniFormat())
        )

        statsData.forEach { (label, value) ->
            val row = summarySheet.createRow(rowNum++)
            row.createCell(0).setCellValue(label)
            row.createCell(1).setCellValue(value)
        }

        rowNum++

        // Ventas por método de pago
        summarySheet.createRow(rowNum++).createCell(0).apply {
            setCellValue("Ventas por Método de Pago")
            cellStyle = headerStyle
        }

        report.salesByPaymentMethod.forEach { (method, total) ->
            val row = summarySheet.createRow(rowNum++)
            row.createCell(0).setCellValue(
                when (method) {
                    PaymentMethod.CASH -> "Efectivo"
                    PaymentMethod.CARD -> "Tarjeta"
                    PaymentMethod.TRANSFER -> "Transferencia"
                    PaymentMethod.CREDIT -> "Crédito"
                }
            )
            row.createCell(1).setCellValue(total.toGuaraniFormat())
        }

        // Hoja de Ventas Detalladas
        val salesSheet = workbook.createSheet("Ventas Detalladas")
        rowNum = 0

        val salesHeader = salesSheet.createRow(rowNum++)
        listOf("Nro. Venta", "Fecha", "Cliente", "Método Pago", "Subtotal", "Descuento", "Total")
            .forEachIndexed { index, title ->
                salesHeader.createCell(index).apply {
                    setCellValue(title)
                    cellStyle = headerStyle
                }
            }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PY"))
        report.salesList.forEach { sale ->
            val row = salesSheet.createRow(rowNum++)
            row.createCell(0).setCellValue(sale.saleNumber)
            row.createCell(1).setCellValue(dateFormat.format(Date(sale.createdAt)))
            row.createCell(2).setCellValue(sale.customerName ?: "-")
            row.createCell(3).setCellValue(
                when (sale.paymentMethod) {
                    PaymentMethod.CASH.name -> "Efectivo"
                    PaymentMethod.CARD.name -> "Tarjeta"
                    PaymentMethod.TRANSFER.name -> "Transferencia"
                    PaymentMethod.CREDIT.name -> "Crédito"
                }
            )
            row.createCell(4).apply {
                setCellValue(sale.subtotal.toDouble())
                cellStyle = currencyStyle
            }
            row.createCell(5).apply {
                setCellValue(sale.discount.toDouble())
                cellStyle = currencyStyle
            }
            row.createCell(6).apply {
                setCellValue(sale.totalAmount.toDouble())
                cellStyle = currencyStyle
            }
        }

        // Ajustar anchos de columna
        for (i in 0..6) {
            salesSheet.autoSizeColumn(i)
        }
        for (i in 0..3) {
            summarySheet.autoSizeColumn(i)
        }

        // Hoja de Top Productos
        val productsSheet = workbook.createSheet("Top Productos")
        rowNum = 0

        val productsHeader = productsSheet.createRow(rowNum++)
        listOf("Producto", "Cantidad Vendida", "Ingresos", "Ganancia")
            .forEachIndexed { index, title ->
                productsHeader.createCell(index).apply {
                    setCellValue(title)
                    cellStyle = headerStyle
                }
            }

        report.topProducts.forEach { productData ->
            val row = productsSheet.createRow(rowNum++)
            row.createCell(0).setCellValue(productData.product.name)
            row.createCell(1).setCellValue(productData.quantitySold.toDouble())
            row.createCell(2).apply {
                setCellValue(productData.totalRevenue.toDouble())
                cellStyle = currencyStyle
            }
            row.createCell(3).apply {
                setCellValue(productData.totalProfit.toDouble())
                cellStyle = currencyStyle
            }
        }

        for (i in 0..3) {
            productsSheet.autoSizeColumn(i)
        }

        // Guardar archivo
        val fileName = "Reporte_Ventas_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.xlsx"
        val file = File(context.getExternalFilesDir(null), fileName)
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()

        return file.absolutePath
    }

    private fun exportInventoryReportToExcel(): String {
        val report = _uiState.value.inventoryReport ?: throw Exception("No hay datos de inventario")
        val workbook = XSSFWorkbook()

        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            })
        }

        val currencyStyle = workbook.createCellStyle().apply {
            dataFormat = workbook.createDataFormat().getFormat("#,##0")
        }

        val lowStockStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_YELLOW.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        val outOfStockStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.CORAL.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        // Hoja de Inventario Completo
        val inventorySheet = workbook.createSheet("Inventario")
        var rowNum = 0

        val header = inventorySheet.createRow(rowNum++)
        listOf("Producto", "Código", "Categoría", "Stock", "Stock Mín.", "Precio Venta", "Valor Total")
            .forEachIndexed { index, title ->
                header.createCell(index).apply {
                    setCellValue(title)
                    cellStyle = headerStyle
                }
            }

        report.productsByCategory.forEach { (category, products) ->
            products.forEach { product ->
                val row = inventorySheet.createRow(rowNum++)
                row.createCell(0).setCellValue(product.name)
                row.createCell(1).setCellValue(product.barcode ?: product.identifier ?: "-")
                row.createCell(2).setCellValue(category)
                row.createCell(3).apply {
                    setCellValue(product.totalStock.toDouble())
                    if (product.totalStock <= 0) {
                        cellStyle = outOfStockStyle
                    } else if (product.totalStock <= product.minStockAlert) {
                        cellStyle = lowStockStyle
                    }
                }
                row.createCell(4).setCellValue(product.minStockAlert.toDouble())
                row.createCell(5).apply {
                    setCellValue(product.salePrice.toDouble())
                    cellStyle = currencyStyle
                }
                row.createCell(6).apply {
                    setCellValue((product.salePrice * product.totalStock).toDouble())
                    cellStyle = currencyStyle
                }
            }
        }

        // Hoja de Resumen
        val summarySheet = workbook.createSheet("Resumen")
        rowNum = 0

        val summaryData = listOf(
            listOf("Total Productos", report.totalProducts.toString()),
            listOf("Stock Total", report.totalStock.toString()),
            listOf("Valor del Inventario", report.inventoryValue.toGuaraniFormat()),
            listOf("Productos con Bajo Stock", report.lowStockProducts.size.toString()),
            listOf("Productos Agotados", report.outOfStockProducts.size.toString())
        )

        summaryData.forEach { (label, value) ->
            val row = summarySheet.createRow(rowNum++)
            row.createCell(0).setCellValue(label)
            row.createCell(1).setCellValue(value)
        }

        // Ajustar columnas
        for (i in 0..6) {
            inventorySheet.autoSizeColumn(i)
        }
        for (i in 0..1) {
            summarySheet.autoSizeColumn(i)
        }

        val fileName = "Reporte_Inventario_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.xlsx"
        val file = File(context.getExternalFilesDir(null), fileName)
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()

        return file.absolutePath
    }
}
