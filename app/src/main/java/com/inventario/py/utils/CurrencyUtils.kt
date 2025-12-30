package com.inventario.py.utils

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * Utilidad para formateo de moneda en Guaraníes (Gs.)
 */
object CurrencyUtils {
    
    private val paraguayLocale = Locale("es", "PY")
    private val numberFormat = NumberFormat.getNumberInstance(paraguayLocale)
    private val decimalFormat = DecimalFormat("#,###")
    
    /**
     * Formatea un valor Long a formato de Guaraníes
     * Ejemplo: 150000 -> "Gs. 150.000"
     */
    fun formatGs(amount: Long): String {
        return "Gs. ${decimalFormat.format(amount)}"
    }
    
    fun formatGs(amount: Double): String {
        return "Gs. ${decimalFormat.format(amount.toLong())}"
    }
    
    fun formatGs(amount: Int): String {
        return "Gs. ${decimalFormat.format(amount)}"
    }
    
    // ==================== ALIASES para compatibilidad ====================
    
    fun formatGuarani(amount: Long): String = formatGs(amount)
    fun formatGuarani(amount: Double): String = formatGs(amount)
    fun formatGuarani(amount: Int): String = formatGs(amount)
    
    // ==================== Otros métodos ====================
    
    fun formatNumber(amount: Long): String {
        return decimalFormat.format(amount)
    }
    
    fun formatShort(amount: Long): String {
        return when {
            amount >= 1_000_000_000 -> "Gs. ${String.format("%.1f", amount / 1_000_000_000.0)}B"
            amount >= 1_000_000 -> "Gs. ${String.format("%.1f", amount / 1_000_000.0)}M"
            amount >= 1_000 -> "Gs. ${String.format("%.1f", amount / 1_000.0)}K"
            else -> formatGs(amount)
        }
    }
    
    fun parseGs(formatted: String): Long {
        return try {
            formatted
                .replace("Gs.", "")
                .replace(".", "")
                .replace(",", "")
                .replace(" ", "")
                .trim()
                .toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    fun calculatePercentage(part: Long, total: Long): Float {
        return if (total > 0) (part.toFloat() / total.toFloat()) * 100f else 0f
    }
    
    fun formatPercentage(percentage: Float): String {
        return String.format("%.1f%%", percentage)
    }
    
    fun calculateMargin(salePrice: Long, purchasePrice: Long): Float {
        return if (purchasePrice > 0) {
            ((salePrice - purchasePrice).toFloat() / purchasePrice.toFloat()) * 100f
        } else 0f
    }
    
    fun formatMargin(margin: Float): String {
        val sign = if (margin >= 0) "+" else ""
        return "$sign${String.format("%.1f", margin)}%"
    }
}
