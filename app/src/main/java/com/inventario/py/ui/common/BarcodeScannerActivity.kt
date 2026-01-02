package com.inventario.py.ui.common

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.inventario.py.R
import com.inventario.py.databinding.ActivityBarcodeScannerBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Activity dedicada para escaneo de códigos de barras.
 * Se puede usar como alternativa al ScannerFragment cuando se necesita
 * un escaneo desde fuera del flujo de navegación principal.
 * 
 * Uso:
 * val intent = Intent(context, BarcodeScannerActivity::class.java)
 * intent.putExtra(EXTRA_SCAN_MODE, SCAN_MODE_SINGLE)
 * startActivityForResult(intent, REQUEST_CODE_SCAN)
 * 
 * Resultado:
 * RESULT_OK con EXTRA_BARCODE_VALUE y EXTRA_BARCODE_FORMAT
 * RESULT_CANCELED si el usuario cancela
 */
@AndroidEntryPoint
class BarcodeScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBarcodeScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    
    private var toneGenerator: ToneGenerator? = null
    private var isFlashOn = false
    private var hasScanned = false
    
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBarcodeScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        setupViews()
        checkCameraPermission()
    }
    
    private fun setupViews() {
        binding.apply {
            // Botón cerrar
            btnClose.setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
            
            // Botón flash
            btnFlash.setOnClickListener {
                toggleFlash()
            }
            
            // Entrada manual
            btnManualEntry.setOnClickListener {
                showManualEntryDialog()
            }
        }
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.previewView.surfaceProvider
                }
            
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                        onBarcodeDetected(barcode)
                    })
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                
                // Configurar flash
                binding.btnFlash.isVisible = camera.cameraInfo.hasFlashUnit()
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun toggleFlash() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val camera = cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA
            )
            
            isFlashOn = !isFlashOn
            camera.cameraControl.enableTorch(isFlashOn)
            
            binding.btnFlash.setIconResource(
                if (isFlashOn) R.drawable.ic_flash_off else R.drawable.ic_flash
            )
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun onBarcodeDetected(barcode: Barcode) {
        if (hasScanned) return
        hasScanned = true
        
        runOnUiThread {
            // Reproducir sonido
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Devolver resultado
            val resultIntent = Intent().apply {
                putExtra(EXTRA_BARCODE_VALUE, barcode.rawValue)
                putExtra(EXTRA_BARCODE_FORMAT, barcode.format)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
    
    private fun showManualEntryDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.enter_code_or_name)
            setPadding(48, 32, 48, 32)
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.manual_entry))
            .setView(input)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val code = input.text.toString().trim()
                if (code.isNotEmpty()) {
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_BARCODE_VALUE, code)
                        putExtra(EXTRA_BARCODE_FORMAT, Barcode.FORMAT_UNKNOWN)
                        putExtra(EXTRA_MANUAL_ENTRY, true)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        toneGenerator?.release()
    }
    
    /**
     * Analizador de imágenes para detectar códigos de barras usando ML Kit
     */
    private inner class BarcodeAnalyzer(
        private val onBarcodeDetected: (Barcode) -> Unit
    ) : ImageAnalysis.Analyzer {
        
        private val scanner = BarcodeScanning.getClient()
        
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        barcodes.firstOrNull()?.let { barcode ->
                            onBarcodeDetected(barcode)
                        }
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
    
    companion object {
        const val EXTRA_BARCODE_VALUE = "barcode_value"
        const val EXTRA_BARCODE_FORMAT = "barcode_format"
        const val EXTRA_MANUAL_ENTRY = "manual_entry"
        const val EXTRA_SCAN_MODE = "scan_mode"
        
        const val SCAN_MODE_SINGLE = "single"
        const val SCAN_MODE_CONTINUOUS = "continuous"
    }
}
