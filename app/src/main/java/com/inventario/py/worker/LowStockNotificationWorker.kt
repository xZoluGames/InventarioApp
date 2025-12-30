package com.inventario.py.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.inventario.py.MainActivity
import com.inventario.py.R
import com.inventario.py.data.local.dao.ProductDao
import com.inventario.py.data.local.entities.toDomain
import com.inventario.py.domain.model.Product
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class LowStockNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val productDao: ProductDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "low_stock_notification_worker"
        const val TAG = "LowStockWorker"
        const val CHANNEL_ID = "low_stock_channel"
        const val NOTIFICATION_ID = 1001
        
        // Crear trabajo periódico (cada 4 horas)
        fun createPeriodicWorkRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<LowStockNotificationWorker>(
                4, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .addTag(WORK_NAME)
                .build()
        }
        
        // Crear trabajo único
        fun createOneTimeWorkRequest(): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<LowStockNotificationWorker>()
                .addTag("${WORK_NAME}_immediate")
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking low stock products...")
            
            // Crear canal de notificación (necesario para Android 8+)
            createNotificationChannel()
            
            // Obtener productos con stock bajo directamente del DAO
            val lowStockEntities = productDao.getLowStockProducts().first()
            val lowStockProducts: List<Product> = lowStockEntities.map { it.toDomain() }
            
            if (lowStockProducts.isEmpty()) {
                Log.d(TAG, "No low stock products found")
                return@withContext Result.success()
            }
            
            Log.d(TAG, "Found ${lowStockProducts.size} low stock products")
            
            // Construir notificación
            val title = "⚠️ Alerta de Stock Bajo"
            val content = if (lowStockProducts.size == 1) {
                "${lowStockProducts.first().name} tiene stock bajo (${lowStockProducts.first().stock} unidades)"
            } else {
                "${lowStockProducts.size} productos tienen stock bajo"
            }
            
            val expandedContent = buildString {
                appendLine("Productos con stock bajo:")
                lowStockProducts.take(5).forEach { product: Product ->
                    appendLine("• ${product.name}: ${product.stock}/${product.minStock} unidades")
                }
                if (lowStockProducts.size > 5) {
                    appendLine("... y ${lowStockProducts.size - 5} más")
                }
            }
            
            // Verificar permisos
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(TAG, "Notification permission not granted")
                    return@withContext Result.success()
                }
            }
            
            // Intent para abrir la app
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to", "low_stock")
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Construir y mostrar notificación
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(expandedContent))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            
            Log.d(TAG, "Low stock notification sent")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking low stock: ${e.message}", e)
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas de Stock"
            val descriptionText = "Notificaciones cuando productos tienen stock bajo"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

// Extensiones para WorkManager
fun WorkManager.scheduleLowStockNotifications() {
    enqueueUniquePeriodicWork(
        LowStockNotificationWorker.WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        LowStockNotificationWorker.createPeriodicWorkRequest()
    )
}

fun WorkManager.checkLowStockNow() {
    enqueue(LowStockNotificationWorker.createOneTimeWorkRequest())
}

fun WorkManager.cancelLowStockNotifications() {
    cancelUniqueWork(LowStockNotificationWorker.WORK_NAME)
}
