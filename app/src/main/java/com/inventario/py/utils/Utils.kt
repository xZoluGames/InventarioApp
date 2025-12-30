package com.inventario.py.utils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ========== Network Result ==========

sealed class NetworkResult<T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error<T>(val message: String) : NetworkResult<T>()
    class Loading<T> : NetworkResult<T>()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading
    
    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): String? = (this as? Error)?.message
}

// ========== Currency Formatting ==========

object CurrencyFormatter {
    private val numberFormat = NumberFormat.getNumberInstance(Locale("es", "PY")).apply {
        maximumFractionDigits = 0
        isGroupingUsed = true
    }

    fun formatGuaranies(amount: Long): String {
        return "${numberFormat.format(amount)} Gs."
    }

    fun formatGuaraniesShort(amount: Long): String {
        return when {
            amount >= 1_000_000_000 -> "${numberFormat.format(amount / 1_000_000_000)}B Gs."
            amount >= 1_000_000 -> "${numberFormat.format(amount / 1_000_000)}M Gs."
            amount >= 1_000 -> "${numberFormat.format(amount / 1_000)}K Gs."
            else -> "${numberFormat.format(amount)} Gs."
        }
    }

    fun parseGuaranies(text: String): Long {
        return try {
            text.replace(".", "")
                .replace(",", "")
                .replace("Gs", "")
                .replace(".", "")
                .trim()
                .toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}

// ========== Date Formatting ==========

object DateFormatter {
    private val fullFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PY"))
    private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PY"))
    private val timeOnlyFormat = SimpleDateFormat("HH:mm", Locale("es", "PY"))
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale("es", "PY"))
    private val dayMonthFormat = SimpleDateFormat("dd MMM", Locale("es", "PY"))

    fun formatFull(date: Date): String = fullFormat.format(date)
    fun formatDate(date: Date): String = dateOnlyFormat.format(date)
    fun formatTime(date: Date): String = timeOnlyFormat.format(date)
    fun formatMonthYear(date: Date): String = monthYearFormat.format(date)
    fun formatDayMonth(date: Date): String = dayMonthFormat.format(date)

    fun formatRelative(date: Date): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }

        return when {
            isSameDay(now, target) -> "Hoy ${formatTime(date)}"
            isYesterday(now, target) -> "Ayer ${formatTime(date)}"
            isSameWeek(now, target) -> {
                val dayName = SimpleDateFormat("EEEE", Locale("es", "PY")).format(date)
                "$dayName ${formatTime(date)}"
            }
            isSameYear(now, target) -> formatDayMonth(date)
            else -> formatDate(date)
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, target: Calendar): Boolean {
        val yesterday = Calendar.getInstance().apply {
            time = now.time
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(yesterday, target)
    }

    private fun isSameWeek(now: Calendar, target: Calendar): Boolean {
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
                now.get(Calendar.WEEK_OF_YEAR) == target.get(Calendar.WEEK_OF_YEAR)
    }

    private fun isSameYear(now: Calendar, target: Calendar): Boolean {
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    }

    // Utilidades para rangos de fecha
    fun getStartOfDay(date: Date = Date()): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    fun getEndOfDay(date: Date = Date()): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
    }

    fun getStartOfWeek(date: Date = Date()): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    fun getStartOfMonth(date: Date = Date()): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    fun getEndOfMonth(date: Date = Date()): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
    }

    fun getStartOfYear(date: Date = Date()): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    fun getEndOfYear(date: Date = Date()): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
    }
}

// ========== Validation ==========

object Validators {
    fun isValidPrice(price: String): Boolean {
        return price.isNotBlank() && price.replace(".", "").toLongOrNull()?.let { it >= 0 } ?: false
    }

    fun isValidQuantity(quantity: String): Boolean {
        return quantity.isNotBlank() && quantity.toIntOrNull()?.let { it >= 0 } ?: false
    }

    fun isValidBarcode(barcode: String): Boolean {
        return barcode.length >= 8 && barcode.all { it.isDigit() }
    }

    fun isValidUsername(username: String): Boolean {
        return username.length >= 3 && username.all { it.isLetterOrDigit() || it == '_' }
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }
}

// ========== Global Functions ==========

fun formatGuaranies(amount: Long): String = CurrencyFormatter.formatGuaranies(amount)
fun formatCurrency(amount: Long): String = CurrencyFormatter.formatGuaranies(amount)
fun formatRelativeDate(date: Date): String = DateFormatter.formatRelative(date)

// ========== Extensions ==========


fun Long.toFormattedPrice(): String = CurrencyFormatter.formatGuaranies(this)
fun Long.toShortPrice(): String = CurrencyFormatter.formatGuaraniesShort(this)
fun Date.toFormattedDate(): String = DateFormatter.formatDate(this)
fun Date.toFormattedDateTime(): String = DateFormatter.formatFull(this)
fun Date.toFormattedFull(): String = DateFormatter.formatFull(this)
fun Date.toRelativeDate(): String = DateFormatter.formatRelative(this)

fun <T> List<T>.safeSubList(fromIndex: Int, toIndex: Int): List<T> {
    return if (fromIndex >= size) {
        emptyList()
    } else {
        subList(fromIndex, minOf(toIndex, size))
    }
}
