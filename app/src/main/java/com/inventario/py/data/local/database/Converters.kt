package com.inventario.py.data.local.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inventario.py.data.local.entities.ProductVariantEntity
import com.inventario.py.data.local.entities.SyncStatus
import com.inventario.py.domain.model.HistoryAction
import com.inventario.py.domain.model.PaymentMethod
import com.inventario.py.domain.model.UserRole
import java.util.Date

class Converters {
    private val gson = Gson()

    // Date converters
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    // String List converters
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(value, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ProductVariantEntity List converters
    @TypeConverter
    fun fromVariantList(value: List<ProductVariantEntity>?): String {
        return gson.toJson(value ?: emptyList<ProductVariantEntity>())
    }

    @TypeConverter
    fun toVariantList(value: String): List<ProductVariantEntity> {
        val type = object : TypeToken<List<ProductVariantEntity>>() {}.type
        return try {
            gson.fromJson(value, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // SyncStatus converters
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String {
        return status.name
    }

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return try {
            SyncStatus.valueOf(value)
        } catch (e: Exception) {
            SyncStatus.PENDING
        }
    }

    // PaymentMethod converters
    @TypeConverter
    fun fromPaymentMethod(method: PaymentMethod): String {
        return method.name
    }

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod {
        return try {
            PaymentMethod.valueOf(value)
        } catch (e: Exception) {
            PaymentMethod.CASH
        }
    }

    // UserRole converters
    @TypeConverter
    fun fromUserRole(role: UserRole): String {
        return role.name
    }

    @TypeConverter
    fun toUserRole(value: String): UserRole {
        return try {
            UserRole.valueOf(value)
        } catch (e: Exception) {
            UserRole.EMPLOYEE
        }
    }

    // HistoryAction converters
    @TypeConverter
    fun fromHistoryAction(action: HistoryAction): String {
        return action.name
    }

    @TypeConverter
    fun toHistoryAction(value: String): HistoryAction {
        return try {
            HistoryAction.valueOf(value)
        } catch (e: Exception) {
            HistoryAction.UPDATED
        }
    }
}
