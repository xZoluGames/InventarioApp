package com.inventario.py.utils

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object DateUtils {
    
    private val fullDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PY"))
    private val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PY"))
    private val timeOnlyFormat = SimpleDateFormat("HH:mm", Locale("es", "PY"))
    private val dayMonthFormat = SimpleDateFormat("dd MMM", Locale("es", "PY"))
    
    fun formatFull(timestamp: Long): String {
        return fullDateFormat.format(Date(timestamp))
    }
    
    fun formatDateOnly(timestamp: Long): String {
        return dateOnlyFormat.format(Date(timestamp))
    }
    
    fun formatTimeOnly(timestamp: Long): String {
        return timeOnlyFormat.format(Date(timestamp))
    }
    
    fun formatDayMonth(timestamp: Long): String {
        return dayMonthFormat.format(Date(timestamp))
    }
    
    fun formatRelative(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Ahora"
            diff < TimeUnit.HOURS.toMillis(1) -> {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
                "Hace $minutes min"
            }
            diff < TimeUnit.DAYS.toMillis(1) -> {
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                "Hace $hours h"
            }
            diff < TimeUnit.DAYS.toMillis(2) -> "Ayer"
            diff < TimeUnit.DAYS.toMillis(7) -> {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                "Hace $days días"
            }
            else -> dateOnlyFormat.format(Date(timestamp))
        }
    }
    
    fun isToday(timestamp: Long): Boolean {
        val today = Calendar.getInstance()
        val date = Calendar.getInstance().apply { timeInMillis = timestamp }
        return today.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
               today.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
    }
    
    fun isYesterday(timestamp: Long): Boolean {
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val date = Calendar.getInstance().apply { timeInMillis = timestamp }
        return yesterday.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
               yesterday.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
    }
    
    fun isThisWeek(timestamp: Long): Boolean {
        val now = Calendar.getInstance()
        val date = Calendar.getInstance().apply { timeInMillis = timestamp }
        return now.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
               now.get(Calendar.WEEK_OF_YEAR) == date.get(Calendar.WEEK_OF_YEAR)
    }
    
    fun isThisMonth(timestamp: Long): Boolean {
        val now = Calendar.getInstance()
        val date = Calendar.getInstance().apply { timeInMillis = timestamp }
        return now.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
               now.get(Calendar.MONTH) == date.get(Calendar.MONTH)
    }
    
    fun isThisYear(timestamp: Long): Boolean {
        val now = Calendar.getInstance()
        val date = Calendar.getInstance().apply { timeInMillis = timestamp }
        return now.get(Calendar.YEAR) == date.get(Calendar.YEAR)
    }
    
    fun getStartOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    
    fun getEndOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
    
    fun getStartOfWeek(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    
    fun getStartOfMonth(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    
    fun getStartOfYear(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
