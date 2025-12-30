package com.inventario.py.utils

import android.content.Context
import android.util.Log
import com.inventario.py.domain.model.PaymentMethod
import com.inventario.py.domain.model.Product
import com.inventario.py.domain.model.Sale
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import org.apache.poi.xssf.usermodel.XSSFCellStyle
class ExcelExporter(private val context: Context) {
    
    companion object {
        const val TAG = "ExcelExporter"
        const val REPORTS_FOLDER = "reports"
    }
    
    private val reportsDir: File
        get() = File(context.filesDir, REPORTS_FOLDER).apply {
            if (!exists()) mkdirs()
        }
    
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PY"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale("es", "PY"))
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
    
    // ========== Reportes de Ventas ==========
    
    fun exportDailySalesReport(
        sales: List<Sale>,
        date: Date
    ): ExportResult {
        return try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Ventas del Día")
            
            // Estilos
            val titleStyle = createTitleStyle(workbook)
            val headerStyle = createHeaderStyle(workbook)
            val boldStyle = createBoldStyle(workbook)
            val currencyStyle = createCurrencyStyle(workbook)
            
            // Título
            var rowNum = 0
            val titleRow = sheet.createRow(rowNum++)
            val titleCell = titleRow.createCell(0)
            titleCell.setCellValue("Reporte de Ventas - ${dateFormat.format(date)}")
            titleCell.cellStyle = titleStyle
            sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 6))
            
            rowNum++ // Fila vacía
            
            // Resumen
            val summaryRow = sheet.createRow(rowNum++)
            summaryRow.createCell(0).apply {
                setCellValue("Total Ventas: ${sales.size}")
                cellStyle = boldStyle
            }
            summaryRow.createCell(2).apply {
                setCellValue("Ingresos: ${CurrencyFormatter.formatGuaranies(sales.sumOf { it.total })}")
                cellStyle = boldStyle
            }
            summaryRow.createCell(4).apply {
                setCellValue("Ganancia: ${CurrencyFormatter.formatGuaranies(sales.sumOf { it.profit })}")
                cellStyle = boldStyle
            }
            
            rowNum++ // Fila vacía
            
            // Encabezados
            val headerRow = sheet.createRow(rowNum++)
            val headers = listOf("ID", "Hora", "Productos", "Items", "Total", "Ganancia", "Método Pago")
            headers.forEachIndexed { index, header ->
                headerRow.createCell(index).apply {
                    setCellValue(header)
                    cellStyle = headerStyle
                }
            }
            
            // Datos
            sales.forEach { sale ->
                val dataRow = sheet.createRow(rowNum++)
                dataRow.createCell(0).setCellValue(sale.id.toDouble())
                dataRow.createCell(1).setCellValue(timeFormat.format(sale.date))
                dataRow.createCell(2).setCellValue(sale.items.joinToString(", ") { it.productName })
                dataRow.createCell(3).setCellValue(sale.totalItems.toDouble())
                dataRow.createCell(4).apply {
                    setCellValue(sale.total.toDouble())
                    cellStyle = currencyStyle
                }
                dataRow.createCell(5).apply {
                    setCellValue(sale.profit.toDouble())
                    cellStyle = currencyStyle
                }
                dataRow.createCell(6).setCellValue(translatePaymentMethod(sale.paymentMethod))
            }
            
            // Autosize columnas
            (0 until 7).forEach { sheet.autoSizeColumn(it) }
            
            // Guardar archivo
            val fileName = "ventas_${fileDateFormat.format(date)}.xlsx"
            val file = File(reportsDir, fileName)
            FileOutputStream(file).use { workbook.write(it) }
            workbook.close()
            
            Log.d(TAG, "Report exported: ${file.absolutePath}")
            ExportResult.Success(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting report: ${e.message}", e)
            ExportResult.Error(e.message ?: "Error desconocido")
        }
    }
    
    fun exportProductsReport(products: List<Product>): ExportResult {
        return try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Inventario")
            
            // Estilos
            val titleStyle = createTitleStyle(workbook)
            val headerStyle = createHeaderStyle(workbook)
            val currencyStyle = createCurrencyStyle(workbook)
            
            // Título
            var rowNum = 0
            val titleRow = sheet.createRow(rowNum++)
            val titleCell = titleRow.createCell(0)
            titleCell.setCellValue("Reporte de Inventario - ${dateFormat.format(Date())}")
            titleCell.cellStyle = titleStyle
            sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 7))
            
            rowNum++ // Fila vacía
            
            // Encabezados
            val headerRow = sheet.createRow(rowNum++)
            val headers = listOf("ID", "Nombre", "Código", "Precio", "Costo", "Stock", "Stock Mín", "Categoría")
            headers.forEachIndexed { index, header ->
                headerRow.createCell(index).apply {
                    setCellValue(header)
                    cellStyle = headerStyle
                }
            }
            
            // Datos
            products.forEach { product ->
                val dataRow = sheet.createRow(rowNum++)
                dataRow.createCell(0).setCellValue(product.id.toDouble())
                dataRow.createCell(1).setCellValue(product.name)
                dataRow.createCell(2).setCellValue(product.barcode ?: product.identifier ?: "")
                dataRow.createCell(3).apply {
                    setCellValue(product.price.toDouble())
                    cellStyle = currencyStyle
                }
                dataRow.createCell(4).apply {
                    setCellValue(product.cost.toDouble())
                    cellStyle = currencyStyle
                }
                dataRow.createCell(5).setCellValue(product.stock.toDouble())
                dataRow.createCell(6).setCellValue(product.minStock.toDouble())
                dataRow.createCell(7).setCellValue(product.category ?: "")
            }
            
            // Autosize columnas
            (0 until 8).forEach { sheet.autoSizeColumn(it) }
            
            // Guardar archivo
            val fileName = "inventario_${fileDateFormat.format(Date())}.xlsx"
            val file = File(reportsDir, fileName)
            FileOutputStream(file).use { workbook.write(it) }
            workbook.close()
            
            Log.d(TAG, "Report exported: ${file.absolutePath}")
            ExportResult.Success(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting report: ${e.message}", e)
            ExportResult.Error(e.message ?: "Error desconocido")
        }
    }
    
    fun exportMonthlySalesReport(
        sales: List<Sale>,
        month: Int,
        year: Int
    ): ExportResult {
        return try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Ventas del Mes")
            
            // Estilos
            val titleStyle = createTitleStyle(workbook)
            val headerStyle = createHeaderStyle(workbook)
            val boldStyle = createBoldStyle(workbook)
            val currencyStyle = createCurrencyStyle(workbook)
            
            // Título
            var rowNum = 0
            val titleRow = sheet.createRow(rowNum++)
            val titleCell = titleRow.createCell(0)
            titleCell.setCellValue("Reporte Mensual - ${getMonthName(month)} $year")
            titleCell.cellStyle = titleStyle
            sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 6))
            
            rowNum++ // Fila vacía
            
            // Resumen
            val totalSales = sales.size
            val totalRevenue = sales.sumOf { it.total }
            val totalProfit = sales.sumOf { it.profit }
            
            sheet.createRow(rowNum++).apply {
                createCell(0).apply {
                    setCellValue("Total de Ventas:")
                    cellStyle = boldStyle
                }
                createCell(1).setCellValue(totalSales.toDouble())
            }
            
            sheet.createRow(rowNum++).apply {
                createCell(0).apply {
                    setCellValue("Ingresos Totales:")
                    cellStyle = boldStyle
                }
                createCell(1).apply {
                    setCellValue(totalRevenue.toDouble())
                    cellStyle = currencyStyle
                }
            }
            
            sheet.createRow(rowNum++).apply {
                createCell(0).apply {
                    setCellValue("Ganancia Total:")
                    cellStyle = boldStyle
                }
                createCell(1).apply {
                    setCellValue(totalProfit.toDouble())
                    cellStyle = currencyStyle
                }
            }
            
            rowNum += 2 // Filas vacías
            
            // Encabezados detalle
            val headerRow = sheet.createRow(rowNum++)
            val headers = listOf("Fecha", "Hora", "Items", "Total", "Ganancia", "Método")
            headers.forEachIndexed { index, header ->
                headerRow.createCell(index).apply {
                    setCellValue(header)
                    cellStyle = headerStyle
                }
            }
            
            // Datos
            sales.forEach { sale ->
                val dataRow = sheet.createRow(rowNum++)
                dataRow.createCell(0).setCellValue(dateFormat.format(sale.date))
                dataRow.createCell(1).setCellValue(timeFormat.format(sale.date))
                dataRow.createCell(2).setCellValue(sale.totalItems.toDouble())
                dataRow.createCell(3).apply {
                    setCellValue(sale.total.toDouble())
                    cellStyle = currencyStyle
                }
                dataRow.createCell(4).apply {
                    setCellValue(sale.profit.toDouble())
                    cellStyle = currencyStyle
                }
                dataRow.createCell(5).setCellValue(translatePaymentMethod(sale.paymentMethod))
            }
            
            // Autosize columnas
            (0 until 6).forEach { sheet.autoSizeColumn(it) }
            
            // Guardar archivo
            val fileName = "ventas_${getMonthName(month).lowercase()}_$year.xlsx"
            val file = File(reportsDir, fileName)
            FileOutputStream(file).use { workbook.write(it) }
            workbook.close()
            
            ExportResult.Success(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting report: ${e.message}", e)
            ExportResult.Error(e.message ?: "Error desconocido")
        }
    }
    
    // ========== Helper Methods ==========

    private fun createTitleStyle(workbook: XSSFWorkbook): XSSFCellStyle {
        val font = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 14
        }
        return workbook.createCellStyle().apply {
            setFont(font)
        }
    }
    
    private fun createHeaderStyle(workbook: XSSFWorkbook): XSSFCellStyle {
        val headerFont = workbook.createFont().apply {
            bold = true
        }
        return workbook.createCellStyle().apply {
            setFont(headerFont)
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
    }
    
    private fun createBoldStyle(workbook: XSSFWorkbook): XSSFCellStyle {
        val boldFont = workbook.createFont().apply {
            bold = true
        }
        return workbook.createCellStyle().apply {
            setFont(boldFont)
        }
    }
    
    private fun createCurrencyStyle(workbook: XSSFWorkbook): XSSFCellStyle {
        val currencyFormat = workbook.createDataFormat().getFormat("#,##0")
        return workbook.createCellStyle().apply {
            dataFormat = currencyFormat
        }
    }
    
    private fun getMonthName(month: Int): String {
        return listOf(
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        ).getOrElse(month - 1) { "Mes" }
    }
    
    private fun translatePaymentMethod(method: PaymentMethod): String {
        return when (method) {
            PaymentMethod.CASH -> "Efectivo"
            PaymentMethod.CARD -> "Tarjeta"
            PaymentMethod.QR -> "QR"
            PaymentMethod.TRANSFER -> "Transferencia"
        }
    }
    
    fun getExportedReports(): List<ReportFile> {
        if (!reportsDir.exists()) return emptyList()
        
        return reportsDir.listFiles { file ->
            file.extension == "xlsx"
        }?.map { file ->
            ReportFile(
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                date = Date(file.lastModified())
            )
        }?.sortedByDescending { it.date } ?: emptyList()
    }
    
    fun deleteReport(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting report: ${e.message}")
            false
        }
    }
}

data class ReportFile(
    val name: String,
    val path: String,
    val size: Long,
    val date: Date
) {
    val formattedSize: String
        get() = when {
            size >= 1_000_000 -> "%.1f MB".format(size / 1_000_000.0)
            size >= 1_000 -> "%.1f KB".format(size / 1_000.0)
            else -> "$size bytes"
        }
}

sealed class ExportResult {
    data class Success(val filePath: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
}
