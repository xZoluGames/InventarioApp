package com.inventario.py

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class InventarioApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal para alertas de stock bajo
            val lowStockChannel = NotificationChannel(
                CHANNEL_LOW_STOCK,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
                enableVibration(true)
                setShowBadge(true)
            }

            // Canal para sincronización
            val syncChannel = NotificationChannel(
                CHANNEL_SYNC,
                "Sincronización",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones de sincronización de datos"
                setShowBadge(false)
            }

            // Canal para progreso de exportación
            val exportChannel = NotificationChannel(
                CHANNEL_EXPORT,
                "Exportación",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Progreso de exportación de archivos"
            }

            notificationManager.createNotificationChannels(
                listOf(lowStockChannel, syncChannel, exportChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_LOW_STOCK = "low_stock_channel"
        const val CHANNEL_SYNC = "sync_channel"
        const val CHANNEL_EXPORT = "export_channel"
    }
}
