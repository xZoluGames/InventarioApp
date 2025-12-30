package com.inventario.py.utils

import android.content.Context
import android.util.Log
import com.inventario.py.data.local.database.InventarioDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class RestoreManager(
    private val context: Context,
    private val database: InventarioDatabase
) {
    
    companion object {
        const val TAG = "RestoreManager"
    }
    
    suspend fun restoreFromBackup(backupPath: String): RestoreResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting restore from: $backupPath")
            
            val backupFile = File(backupPath)
            if (!backupFile.exists()) {
                return@withContext RestoreResult.Error("Archivo de backup no encontrado")
            }
            
            // Crear directorio temporal para extracción
            val tempDir = File(context.cacheDir, "restore_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            
            try {
                // Extraer ZIP
                extractZip(backupFile, tempDir)
                
                // Verificar que exista el archivo de base de datos
                val extractedDb = File(tempDir, "inventario_database")
                if (!extractedDb.exists()) {
                    return@withContext RestoreResult.Error("Archivo de base de datos no encontrado en el backup")
                }
                
                // Cerrar la base de datos actual
                database.close()
                
                // Obtener rutas de la base de datos
                val dbFile = context.getDatabasePath("inventario_database")
                val dbShmFile = File(dbFile.parent, "inventario_database-shm")
                val dbWalFile = File(dbFile.parent, "inventario_database-wal")
                
                // Eliminar archivos actuales
                dbFile.delete()
                dbShmFile.delete()
                dbWalFile.delete()
                
                // Copiar archivos extraídos
                extractedDb.copyTo(dbFile, overwrite = true)
                
                val extractedShm = File(tempDir, "inventario_database-shm")
                if (extractedShm.exists()) {
                    extractedShm.copyTo(dbShmFile, overwrite = true)
                }
                
                val extractedWal = File(tempDir, "inventario_database-wal")
                if (extractedWal.exists()) {
                    extractedWal.copyTo(dbWalFile, overwrite = true)
                }
                
                // Restaurar SharedPreferences si existen
                val prefsDir = File(tempDir, "prefs")
                if (prefsDir.exists() && prefsDir.isDirectory) {
                    val appPrefsDir = File(context.dataDir, "shared_prefs")
                    prefsDir.listFiles()?.forEach { prefFile ->
                        prefFile.copyTo(File(appPrefsDir, prefFile.name), overwrite = true)
                    }
                }
                
                Log.d(TAG, "Restore completed successfully")
                RestoreResult.Success("Restauración completada. La aplicación se reiniciará.")
                
            } finally {
                // Limpiar directorio temporal
                tempDir.deleteRecursively()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed: ${e.message}", e)
            RestoreResult.Error("Error al restaurar: ${e.message}")
        }
    }
    
    private fun extractZip(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val destFile = File(destDir, entry.name)
                
                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
    
    suspend fun validateBackup(backupPath: String): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val backupFile = File(backupPath)
            if (!backupFile.exists()) {
                return@withContext ValidationResult(
                    isValid = false,
                    error = "Archivo no encontrado"
                )
            }
            
            val tempDir = File(context.cacheDir, "validate_temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            
            try {
                extractZip(backupFile, tempDir)
                
                val hasDatabase = File(tempDir, "inventario_database").exists()
                val hasMetadata = File(tempDir, "backup_info.json").exists()
                
                if (!hasDatabase) {
                    return@withContext ValidationResult(
                        isValid = false,
                        error = "El archivo no contiene una base de datos válida"
                    )
                }
                
                // Leer metadata si existe
                var backupDate = ""
                var appVersion = ""
                if (hasMetadata) {
                    try {
                        val metadataContent = File(tempDir, "backup_info.json").readText()
                        // Parse simple del JSON
                        val dateMatch = Regex("\"date\"\\s*:\\s*\"([^\"]+)\"").find(metadataContent)
                        val versionMatch = Regex("\"app_version\"\\s*:\\s*\"([^\"]+)\"").find(metadataContent)
                        backupDate = dateMatch?.groupValues?.get(1) ?: ""
                        appVersion = versionMatch?.groupValues?.get(1) ?: ""
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not read backup metadata: ${e.message}")
                    }
                }
                
                ValidationResult(
                    isValid = true,
                    backupDate = backupDate,
                    appVersion = appVersion,
                    fileSize = backupFile.length()
                )
                
            } finally {
                tempDir.deleteRecursively()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Validation failed: ${e.message}", e)
            ValidationResult(
                isValid = false,
                error = "Error al validar: ${e.message}"
            )
        }
    }
}

sealed class RestoreResult {
    data class Success(val message: String) : RestoreResult()
    data class Error(val message: String) : RestoreResult()
}

data class ValidationResult(
    val isValid: Boolean,
    val error: String? = null,
    val backupDate: String = "",
    val appVersion: String = "",
    val fileSize: Long = 0
)
