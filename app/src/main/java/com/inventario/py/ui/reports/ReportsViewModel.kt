package com.inventario.py.ui.reports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventario.py.data.local.entity.*
import com.inventario.py.data.repository.ProductRepository
import com.inventario.py.data.repository.SalesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val topProducts: List<TopProduct> = emptyList(),
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
    val exportedFilePath: String? = null,
    // Propiedades adicionales para ReportsState
    val reportsState: ReportsState = ReportsState()
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

    // Estados públicos para observar desde el Fragment
    private val _reportsState = MutableStateFlow(ReportsState())
    val reportsState: StateFlow<ReportsState> = _reportsState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

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

    fun setCustomStartDate(date: Long) {
        _uiState.value = _uiState.value.copy(customStartDate = date)
    }

    fun setCustomEndDate(date: Long) {
        _uiState.value = _uiState.value.copy(customEndDate = date)
    }

    fun applyCustomDateRange() {
        val start = _uiState.value.customStartDate
        val end = _uiState.value.customEndDate
        if (start != null && end != null) {
            setCustomDateRange(start, end)
        }
    }

    /**
     * Carga reportes según el rango de fecha
     */
    fun loadReports(dateRange: DateRange) {
        val period = when (dateRange) {
            DateRange.TODAY -> PeriodType.TODAY
            DateRange.WEEK -> PeriodType.THIS_WEEK
            DateRange.MONTH -> PeriodType.THIS_MONTH
            DateRange.YEAR -> PeriodType.THIS_YEAR
            DateRange.CUSTOM -> PeriodType.CUSTOM
        }
        setPeriod(period)
    }

    fun refreshReports() {
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
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                ReportPeriod(start, now, "Esta semana")
            }
            PeriodType.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                ReportPeriod(start, now, "Este mes")
            }
            PeriodType.THIS_YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                ReportPeriod(start, now, "Este año")
            }
            PeriodType.CUSTOM -> {
                val start = state.customStartDate ?: now
                val end = state.customEndDate ?: now
                ReportPeriod(start, end, "Personalizado")
            }
        }
    }

    private fun generateReport() {
        viewModelScope.launch {
            _isLoading.value = true
            _uiState.value = _uiState.value.copy(isLoading = true)

            when (_uiState.value.selectedReportType) {
                ReportType.SALES -> generateSalesReport()
                ReportType.INVENTORY -> generateInventoryReport()
                ReportType.PROFIT -> generateSalesReport() // Usa el mismo pero con foco en ganancias
            }

            _isLoading.value = false
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private suspend fun generateSalesReport() {
        val period = getReportPeriod()

        salesRepository.getSalesByDateRange(period.start, period.end).collectLatest { sales ->
            val completedSales = sales.filter { it.status == SaleEntity.STATUS_COMPLETED }

            val totalSales = completedSales.sumOf { it.total }
            val totalTransactions = completedSales.size
            val averageTicket = if (totalTransactions > 0) totalSales / totalTransactions else 0L

            // Agrupar por método de pago
            val salesByPaymentMethod = completedSales
                .groupBy { PaymentMethod.fromString(it.paymentMethod) }
                .mapValues { entry -> entry.value.sumOf { it.total } }

            // Top products - simplificado
            val topProducts = mutableListOf<TopProduct>()

            // Calcular profit (simplificado)
            val totalProfit = completedSales.sumOf { sale ->
                sale.total - (sale.subtotal * 0.7).toLong() // Estimación
            }

            val reportData = SalesReportData(
                period = period,
                totalSales = totalSales,
                totalTransactions = totalTransactions,
                totalProfit = totalProfit,
                averageTicket = averageTicket,
                salesByPaymentMethod = salesByPaymentMethod,
                topProducts = topProducts,
                salesList = completedSales
            )

            // Actualizar ReportsState
            val reportsState = ReportsState(
                totalSales = totalSales,
                totalTransactions = totalTransactions,
                totalProfit = totalProfit,
                cashSales = salesByPaymentMethod[PaymentMethod.CASH] ?: 0L,
                cardSales = salesByPaymentMethod[PaymentMethod.CARD] ?: 0L,
                transferSales = salesByPaymentMethod[PaymentMethod.TRANSFER] ?: 0L,
                qrSales = salesByPaymentMethod[PaymentMethod.QR] ?: 0L,
                topProducts = topProducts,
                paymentMethodBreakdown = salesByPaymentMethod
            )

            _uiState.value = _uiState.value.copy(
                salesReport = reportData,
                reportsState = reportsState
            )
            _reportsState.value = reportsState
        }
    }

    private suspend fun generateInventoryReport() {
        productRepository.getAllProducts().collectLatest { products ->
            val activeProducts = products.filter { it.isActive }
            val totalStock = activeProducts.sumOf { it.totalStock }
            val inventoryValue = activeProducts.sumOf { it.salePrice * it.totalStock }
            val lowStockProducts = activeProducts.filter { it.totalStock <= it.minStockAlert && it.totalStock > 0 }
            val outOfStockProducts = activeProducts.filter { it.totalStock <= 0 }

            val productsByCategory = activeProducts.groupBy { it.categoryId ?: "Sin categoría" }

            val reportData = InventoryReportData(
                totalProducts = activeProducts.size,
                totalStock = totalStock,
                inventoryValue = inventoryValue,
                lowStockProducts = lowStockProducts,
                outOfStockProducts = outOfStockProducts,
                productsByCategory = productsByCategory
            )

            _uiState.value = _uiState.value.copy(inventoryReport = reportData)
        }
    }

    fun exportSalesReport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)

            try {
                val filePath = withContext(Dispatchers.IO) {
                    // Crear archivo CSV simple
                    val fileName = "reporte_ventas_${System.currentTimeMillis()}.csv"
                    val file = File(context.getExternalFilesDir(null), fileName)

                    FileOutputStream(file).bufferedWriter().use { writer ->
                        writer.write("Fecha,Total,Método de Pago,Estado\n")
                        _uiState.value.salesReport?.salesList?.forEach { sale ->
                            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(sale.soldAt))
                            writer.write("$date,${sale.total},${sale.paymentMethod},${sale.status}\n")
                        }
                    }

                    file.absolutePath
                }

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportedFilePath = filePath
                )
                _exportResult.value = filePath
                _events.emit(ReportEvent.ExportSuccess(filePath))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isExporting = false)
                _events.emit(ReportEvent.ExportError(e.message ?: "Error al exportar"))
                _message.value = "Error al exportar: ${e.message}"
            }
        }
    }

    fun exportInventoryReport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true)

            try {
                val filePath = withContext(Dispatchers.IO) {
                    val fileName = "reporte_inventario_${System.currentTimeMillis()}.csv"
                    val file = File(context.getExternalFilesDir(null), fileName)

                    productRepository.getAllProducts().first().let { products ->
                        FileOutputStream(file).bufferedWriter().use { writer ->
                            writer.write("Nombre,Stock,Precio Venta,Precio Compra,Código\n")
                            products.forEach { product ->
                                writer.write("${product.name},${product.totalStock},${product.salePrice},${product.purchasePrice},${product.barcode ?: ""}\n")
                            }
                        }
                    }

                    file.absolutePath
                }

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportedFilePath = filePath
                )
                _exportResult.value = filePath
                _events.emit(ReportEvent.ExportSuccess(filePath))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isExporting = false)
                _events.emit(ReportEvent.ExportError(e.message ?: "Error al exportar"))
            }
        }
    }

    fun openExportedFile(filePath: String) {
        // Esta función se implementaría en el Fragment
        _exportResult.value = filePath
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearExportResult() {
        _exportResult.value = null
        _uiState.value = _uiState.value.copy(exportedFilePath = null)
    }
}