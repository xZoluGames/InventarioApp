package com.inventario.py.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.snackbar.Snackbar
import com.inventario.py.BuildConfig
import com.inventario.py.R
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ==================== CONSTANTES ====================

object Constants {
    const val CURRENCY_SYMBOL = "Gs."
    const val DATE_FORMAT_DISPLAY = "dd/MM/yyyy"
    const val DATE_FORMAT_TIME = "HH:mm"
    const val DATE_FORMAT_FULL = "dd/MM/yyyy HH:mm"
    const val DATE_FORMAT_API = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    const val DATE_FORMAT_FILE = "yyyyMMdd_HHmmss"
    
    const val REQUEST_CODE_CAMERA = 1001
    const val REQUEST_CODE_GALLERY = 1002
    const val REQUEST_CODE_BARCODE = 1003
    
    const val EXTRA_PRODUCT_ID = "product_id"
    const val EXTRA_SALE_ID = "sale_id"
    const val EXTRA_BARCODE = "barcode"
    const val EXTRA_SCAN_MODE = "scan_mode"
    
    const val SCAN_MODE_ADD_PRODUCT = "add_product"
    const val SCAN_MODE_SEARCH = "search"
    const val SCAN_MODE_ADD_TO_CART = "add_to_cart"
}







// ==================== EXTENSIONES DE VIEW ====================

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.visibleIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

fun View.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    Snackbar.make(this, message, duration).show()
}

fun View.showSnackbarWithAction(
    message: String,
    actionText: String,
    action: () -> Unit
) {
    Snackbar.make(this, message, Snackbar.LENGTH_LONG)
        .setAction(actionText) { action() }
        .show()
}

// ==================== EXTENSIONES DE CONTEXT ====================

fun Context.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Fragment.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    requireContext().toast(message, duration)
}

// ==================== EXTENSIONES DE IMAGEVIEW ====================

fun ImageView.loadImage(url: String?, placeholder: Int = R.drawable.ic_placeholder_product) {
    Glide.with(context)
        .load(url)
        .placeholder(placeholder)
        .error(placeholder)
        .transition(DrawableTransitionOptions.withCrossFade())
        .into(this)
}

fun ImageView.loadImageFromFile(path: String?, placeholder: Int = R.drawable.ic_placeholder_product) {
    Glide.with(context)
        .load(path?.let { File(it) })
        .placeholder(placeholder)
        .error(placeholder)
        .into(this)
}

// ==================== UTILIDADES DE ARCHIVO ====================

object FileUtils {
    
    fun getImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat(Constants.DATE_FORMAT_FILE, Locale.getDefault())
            .format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("IMG_${timeStamp}_", ".jpg", storageDir)
    }
    
    fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
    }
    
    fun saveBitmapToFile(context: Context, bitmap: Bitmap): File {
        val file = getImageFile(context)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file
    }
    
    fun getExcelFile(context: Context, fileName: String): File {
        val timeStamp = SimpleDateFormat(Constants.DATE_FORMAT_FILE, Locale.getDefault())
            .format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        return File(storageDir, "${fileName}_${timeStamp}.xlsx")
    }
    
    fun createExcelFile(context: Context, fileName: String, headers: List<String>, data: List<List<Any>>): File {
        val workbook: Workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Datos")
        
        // Header row
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).setCellValue(header)
        }
        
        // Data rows
        data.forEachIndexed { rowIndex, rowData ->
            val row = sheet.createRow(rowIndex + 1)
            rowData.forEachIndexed { cellIndex, cellData ->
                val cell = row.createCell(cellIndex)
                when (cellData) {
                    is String -> cell.setCellValue(cellData)
                    is Number -> cell.setCellValue(cellData.toDouble())
                    is Boolean -> cell.setCellValue(cellData)
                    is Date -> cell.setCellValue(cellData)
                    else -> cell.setCellValue(cellData.toString())
                }
            }
        }
        
        // Auto-size columns
        headers.indices.forEach { sheet.autoSizeColumn(it) }
        
        val file = getExcelFile(context, fileName)
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
        
        return file
    }
    
    fun shareFile(context: Context, file: File, mimeType: String = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") {
        val uri = getFileUri(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir archivo"))
    }
}

// ==================== VALIDACIONES ====================

object Validators {
    
    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    fun isValidPhone(phone: String): Boolean {
        // Formato paraguayo: puede empezar con 09 o +595
        val phonePattern = "^(\\+595|0)?9[0-9]{8}$".toRegex()
        return phonePattern.matches(phone.replace(" ", "").replace("-", ""))
    }
    
    fun isValidPassword(password: String): Boolean {
        // Mínimo 6 caracteres
        return password.length >= 6
    }
    
    fun isValidBarcode(barcode: String): Boolean {
        // EAN-13, EAN-8, UPC-A, etc.
        return barcode.matches("^[0-9]{8,13}$".toRegex())
    }
    
    fun isValidRuc(ruc: String): Boolean {
        // RUC paraguayo
        return ruc.matches("^[0-9]{6,9}-[0-9]$".toRegex())
    }
}

// ==================== GENERADORES ====================

object Generators {
    
    fun generateId(): String {
        return UUID.randomUUID().toString()
    }
    
    fun generateSaleNumber(lastNumber: Int?): String {
        val nextNumber = (lastNumber ?: 0) + 1
        return nextNumber.toString().padStart(8, '0')
    }
    
    fun generateIdentifier(prefix: String = "PRD"): String {
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        val random = (1000..9999).random()
        return "$prefix-$timestamp-$random"
    }
}

// ==================== BOOT RECEIVER ====================



class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Programar sincronización periódica
            // Se puede usar WorkManager aquí
        }
    }
}
