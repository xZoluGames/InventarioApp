package com.inventario.py.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.inventario.py.data.local.database.InventarioDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: InventarioDatabase
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "backup_worker"
        const val TAG = "BackupWorker"
        const val BACKUP_FOLDER = "backups"
        const val MAX_BACKUPS = 7 // Mantener últimos 7 backups
        
        // Crear trabajo periódico (diario)
        fun createPeriodicWorkRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            
            return PeriodicWorkRequestBuilder<BackupWorker>(
                1, TimeUnit.DAYS,
                2, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()
        }
        
        // Crear trabajo único (backup manual)
        fun createOneTimeWorkRequest(): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<BackupWorker>()
                .addTag("${WORK_NAME}_manual")
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting backup...")
            
            // Crear directorio de backups si no existe
            val backupDir = File(context.filesDir, BACKUP_FOLDER)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            
            // Generar nombre del archivo
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val backupFileName = "inventario_backup_$timestamp.zip"
            val backupFile = File(backupDir, backupFileName)
            
            // Checkpoint la base de datos para asegurar que todo esté escrito
            database.checkpoint()
            
            // Obtener archivos de la base de datos
            val dbFile = context.getDatabasePath("inventario_database")
            val dbShmFile = File(dbFile.parent, "inventario_database-shm")
            val dbWalFile = File(dbFile.parent, "inventario_database-wal")
            
            // Crear archivo ZIP con la base de datos
            ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
                // Agregar archivo principal de la base de datos
                if (dbFile.exists()) {
                    addFileToZip(zipOut, dbFile, "inventario_database")
                }
                
                // Agregar archivos WAL si existen
                if (dbShmFile.exists()) {
                    addFileToZip(zipOut, dbShmFile, "inventario_database-shm")
                }
                if (dbWalFile.exists()) {
                    addFileToZip(zipOut, dbWalFile, "inventario_database-wal")
                }
                
                // Agregar SharedPreferences
                val prefsDir = File(context.dataDir, "shared_prefs")
                if (prefsDir.exists() && prefsDir.isDirectory) {
                    prefsDir.listFiles()?.forEach { prefFile ->
                        if (prefFile.name.contains("inventario") || prefFile.name.contains("settings")) {
                            addFileToZip(zipOut, prefFile, "prefs/${prefFile.name}")
                        }
                    }
                }
                
                // Agregar información del backup
                val metadata = """
                    {
                        "version": 1,
                        "timestamp": "${Date().time}",
                        "date": "$timestamp",
                        "app_version": "1.0.0"
                    }
                """.trimIndent()
                
                zipOut.putNextEntry(ZipEntry("backup_info.json"))
                zipOut.write(metadata.toByteArray())
                zipOut.closeEntry()
            }
            
            Log.d(TAG, "Backup created: ${backupFile.absolutePath} (${backupFile.length()} bytes)")
            
            // Limpiar backups antiguos
            cleanOldBackups(backupDir)
            
            Result.success(
                workDataOf(
                    "backup_path" to backupFile.absolutePath,
                    "backup_size" to backupFile.length()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed: ${e.message}", e)
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }
    }

    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val entry = ZipEntry(entryName)
            zipOut.putNextEntry(entry)
            fis.copyTo(zipOut)
            zipOut.closeEntry()
        }
    }

    private fun cleanOldBackups(backupDir: File) {
        try {
            val backupFiles = backupDir.listFiles { file ->
                file.name.startsWith("inventario_backup_") && file.name.endsWith(".zip")
            }?.sortedByDescending { it.lastModified() } ?: return
            
            // Eliminar backups antiguos si hay más del máximo permitido
            if (backupFiles.size > MAX_BACKUPS) {
                backupFiles.drop(MAX_BACKUPS).forEach { file ->
                    Log.d(TAG, "Deleting old backup: ${file.name}")
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old backups: ${e.message}")
        }
    }
}

// Manager para operaciones de backup
class BackupManager(private val context: Context) {
    
    private val backupDir: File
        get() = File(context.filesDir, BackupWorker.BACKUP_FOLDER)
    
    fun getBackupList(): List<BackupInfo> {
        if (!backupDir.exists()) return emptyList()
        
        return backupDir.listFiles { file ->
            file.name.startsWith("inventario_backup_") && file.name.endsWith(".zip")
        }?.map { file ->
            BackupInfo(
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                date = Date(file.lastModified())
            )
        }?.sortedByDescending { it.date } ?: emptyList()
    }
    
    fun deleteBackup(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            Log.e("BackupManager", "Error deleting backup: ${e.message}")
            false
        }
    }
    
    fun getBackupSize(): Long {
        if (!backupDir.exists()) return 0L
        return backupDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
    
    fun deleteAllBackups(): Boolean {
        return try {
            backupDir.listFiles()?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Error deleting all backups: ${e.message}")
            false
        }
    }
    
    fun exportBackup(backupPath: String, destinationPath: String): Boolean {
        return try {
            File(backupPath).copyTo(File(destinationPath), overwrite = true)
            true
        } catch (e: Exception) {
            Log.e("BackupManager", "Error exporting backup: ${e.message}")
            false
        }
    }
}

data class BackupInfo(
    val name: String,
    val path: String,
    val size: Long,
    val date: Date
) {
    val formattedSize: String
        get() = when {
            size >= 1_000_000 -> "%.1f MB".format(size / 1_000_000.0)
            size >= 1_000 -> "%.1f KB".format(size / 1_000.0)
            else -> "$size bytes"
        }
}

// Extensiones para WorkManager
fun WorkManager.scheduleAutomaticBackups() {
    enqueueUniquePeriodicWork(
        BackupWorker.WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        BackupWorker.createPeriodicWorkRequest()
    )
}

fun WorkManager.backupNow() {
    enqueue(BackupWorker.createOneTimeWorkRequest())
}

fun WorkManager.cancelAutomaticBackups() {
    cancelUniqueWork(BackupWorker.WORK_NAME)
}

// Extensión para checkpoint de la base de datos
fun InventarioDatabase.checkpoint() {
    openHelper.writableDatabase.apply {
        execSQL("PRAGMA wal_checkpoint(FULL)")
    }
}
