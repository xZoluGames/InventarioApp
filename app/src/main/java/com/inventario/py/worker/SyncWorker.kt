package com.inventario.py.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.inventario.py.data.local.dao.ProductDao
import com.inventario.py.data.local.dao.SaleDao
import com.inventario.py.data.local.entities.SyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val productDao: ProductDao,
    private val saleDao: SaleDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "sync_worker"
        const val TAG = "SyncWorker"
        
        // Crear trabajo periódico
        fun createPeriodicWorkRequest(intervalMinutes: Long): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            return PeriodicWorkRequestBuilder<SyncWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
        }
        
        // Crear trabajo único inmediato
        fun createOneTimeWorkRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            return OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .addTag("${WORK_NAME}_immediate")
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting sync...")
            
            // Get pending items count
            val pendingProductsCount = productDao.getPendingProductsCount(SyncStatus.PENDING)
            val pendingSalesCount = saleDao.getPendingSalesCount(SyncStatus.PENDING)
            
            Log.d(TAG, "Found $pendingProductsCount pending products and $pendingSalesCount pending sales")
            
            // TODO: Implement actual API sync when server is available
            // For now, just log and mark as success
            
            Log.d(TAG, "Sync completed successfully (offline mode)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }
}

// Extensión para programar sincronización
fun WorkManager.scheduleSyncWork(intervalMinutes: Long) {
    enqueueUniquePeriodicWork(
        SyncWorker.WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        SyncWorker.createPeriodicWorkRequest(intervalMinutes)
    )
}

fun WorkManager.syncNow() {
    enqueue(SyncWorker.createOneTimeWorkRequest())
}

fun WorkManager.cancelSync() {
    cancelUniqueWork(SyncWorker.WORK_NAME)
}
