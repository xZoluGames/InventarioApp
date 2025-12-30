package com.inventario.py

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class InventarioApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        
        // Inicializar Timber para logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Crear canales de notificación
        createNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Canal para alertas de stock bajo
            val stockChannel = NotificationChannel(
                CHANNEL_STOCK_ALERTS,
                "Alertas de Stock",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones cuando el stock de productos está bajo"
                enableVibration(true)
                enableLights(true)
            }

            // Canal para sincronización
            val syncChannel = NotificationChannel(
                CHANNEL_SYNC,
                "Sincronización",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones de sincronización de datos"
            }

            // Canal para ventas
            val salesChannel = NotificationChannel(
                CHANNEL_SALES,
                "Ventas",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de ventas realizadas"
            }

            // Canal general
            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "General",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones generales de la aplicación"
            }

            notificationManager.createNotificationChannels(
                listOf(stockChannel, syncChannel, salesChannel, generalChannel)
            )
        }
    }

    companion object {
        const val CHANNEL_STOCK_ALERTS = "stock_alerts_channel"
        const val CHANNEL_SYNC = "sync_channel"
        const val CHANNEL_SALES = "sales_channel"
        const val CHANNEL_GENERAL = "general_channel"
    }
}
