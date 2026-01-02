package com.inventario.py.ui.common

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.util.Size
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    companion object {
        const val EXTRA_SCAN_MODE = "scan_mode"
        const val EXTRA_BARCODE_VALUE = "barcode_value"
        const val EXTRA_BARCODE_FORMAT = "barcode_format"

        const val SCAN_MODE_SINGLE = 0
        const val SCAN_MODE_CONTINUOUS = 1

        const val REQUEST_CODE_SCAN = 1001
    }

    private lateinit var binding: ActivityBarcodeScannerBinding
    private lateinit var cameraExecutor: ExecutorService

    private var toneGenerator: ToneGenerator? = null
    private var isFlashOn = false
    private var hasScanned = false
    private var cameraProvider: ProcessCameraProvider? = null

    private val barcodeScanner = BarcodeScanning.getClient()

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

    @OptIn(ExperimentalGetImage::class) private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    // CORRECCIÓN: Usar setSurfaceProvider correctamente
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (!hasScanned) {
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )

                                barcodeScanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            barcode.rawValue?.let { value ->
                                                onBarcodeDetected(value, barcode.format)
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                val camera = cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

                // Configurar flash
                camera?.cameraControl?.enableTorch(isFlashOn)

            } catch (e: Exception) {
                Toast.makeText(this, "Error al iniciar cámara", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun onBarcodeDetected(value: String, format: Int) {
        if (hasScanned) return
        hasScanned = true

        // Reproducir sonido
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)

        // Devolver resultado
        val resultIntent = Intent().apply {
            putExtra(EXTRA_BARCODE_VALUE, value)
            putExtra(EXTRA_BARCODE_FORMAT, format)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        cameraProvider?.let {
            try {
                val camera = it.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA
                )
                camera.cameraControl.enableTorch(isFlashOn)

                binding.btnFlash.setIconResource(
                    if (isFlashOn) R.drawable.ic_flash_off else R.drawable.ic_flash
                )
            } catch (e: Exception) {
                // Ignorar error
            }
        }
    }

    private fun showManualEntryDialog() {
        val input = EditText(this).apply {
            hint = "Ingrese código de barras"
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Entrada Manual")
            .setView(input)
            .setPositiveButton("Aceptar") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isNotEmpty()) {
                    onBarcodeDetected(code, Barcode.FORMAT_UNKNOWN)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        toneGenerator?.release()
        barcodeScanner.close()
    }
}