package com.inventario.py.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import com.inventario.py.worker.scheduleLowStockNotifications
import com.inventario.py.worker.scheduleSyncWork
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val DEFAULT_SYNC_INTERVAL = 15L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, scheduling workers...")

            val workManager = WorkManager.getInstance(context)

            try {
                workManager.scheduleSyncWork(DEFAULT_SYNC_INTERVAL)
                Log.d(TAG, "Sync worker scheduled")

                workManager.scheduleLowStockNotifications()
                Log.d(TAG, "Low stock notification worker scheduled")

            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling workers", e)
            }
        }
    }
}

