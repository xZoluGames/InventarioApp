package com.inventario.py.ui.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.inventario.py.data.local.entity.ProductEntity
import com.inventario.py.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

data class ScannerUiState(
    val isScanning: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val lastScannedCode: String? = null,
    val foundProduct: ProductEntity? = null,
    val isFlashOn: Boolean = false,
    val scanMode: ScanMode = ScanMode.SINGLE,
    val scannedCodes: List<String> = emptyList(),
    val errorMessage: String? = null
)

enum class ScanMode {
    SINGLE,      // Escanea un código y cierra
    CONTINUOUS,  // Escanea múltiples códigos
    ADD_TO_CART  // Agrega directamente al carrito
}

sealed class ScannerEvent {
    data class CodeScanned(val code: String) : ScannerEvent()
    data class ProductFound(val product: ProductEntity) : ScannerEvent()
    data class ProductNotFound(val code: String) : ScannerEvent()
    data class Error(val message: String) : ScannerEvent()
    object CameraPermissionRequired : ScannerEvent()
    data class ProductAddedToCart(val product: ProductEntity) : ScannerEvent()
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ScannerEvent>()
    val events: SharedFlow<ScannerEvent> = _events.asSharedFlow()

    private var cameraExecutor: ExecutorService? = null
    private var camera: Camera? = null
    private val barcodeScanner = BarcodeScanning.getClient()
    
    private val processedCodes = mutableSetOf<String>()
    private var lastScanTime = 0L
    private val scanDebounceMs = 1500L // Evitar escaneos duplicados

    fun checkCameraPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        _uiState.value = _uiState.value.copy(hasCameraPermission = hasPermission)
        return hasPermission
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(hasCameraPermission = granted)
        if (!granted) {
            viewModelScope.launch {
                _events.emit(ScannerEvent.CameraPermissionRequired)
            }
        }
    }

    fun setScanMode(mode: ScanMode) {
        _uiState.value = _uiState.value.copy(scanMode = mode)
        if (mode == ScanMode.CONTINUOUS) {
            processedCodes.clear()
        }
    }

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor!!, BarcodeAnalyzer { barcode ->
                            onBarcodeDetected(barcode)
                        })
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )

                _uiState.value = _uiState.value.copy(isScanning = true)

            } catch (e: Exception) {
                viewModelScope.launch {
                    _events.emit(ScannerEvent.Error("Error al iniciar cámara: ${e.message}"))
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun onBarcodeDetected(barcode: String) {
        val currentTime = System.currentTimeMillis()
        
        // Debounce para evitar múltiples escaneos del mismo código
        if (currentTime - lastScanTime < scanDebounceMs) {
            return
        }
        
        val state = _uiState.value
        
        when (state.scanMode) {
            ScanMode.SINGLE -> {
                if (state.lastScannedCode != barcode) {
                    lastScanTime = currentTime
                    processScannedCode(barcode)
                }
            }
            ScanMode.CONTINUOUS -> {
                if (!processedCodes.contains(barcode)) {
                    lastScanTime = currentTime
                    processedCodes.add(barcode)
                    processScannedCode(barcode)
                }
            }
            ScanMode.ADD_TO_CART -> {
                lastScanTime = currentTime
                processScannedCode(barcode, addToCart = true)
            }
        }
    }

    private fun processScannedCode(code: String, addToCart: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(lastScannedCode = code)
            _events.emit(ScannerEvent.CodeScanned(code))

            // Buscar producto
            val product = productRepository.getProductByBarcode(code)
                ?: productRepository.getProductByIdentifier(code)

            if (product != null) {
                _uiState.value = _uiState.value.copy(foundProduct = product)
                
                if (addToCart) {
                    _events.emit(ScannerEvent.ProductAddedToCart(product))
                } else {
                    _events.emit(ScannerEvent.ProductFound(product))
                }

                // Agregar a lista de escaneados
                val updatedList = _uiState.value.scannedCodes.toMutableList()
                if (!updatedList.contains(code)) {
                    updatedList.add(code)
                    _uiState.value = _uiState.value.copy(scannedCodes = updatedList)
                }
            } else {
                _events.emit(ScannerEvent.ProductNotFound(code))
            }
        }
    }

    fun toggleFlash() {
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                val newFlashState = !_uiState.value.isFlashOn
                cam.cameraControl.enableTorch(newFlashState)
                _uiState.value = _uiState.value.copy(isFlashOn = newFlashState)
            }
        }
    }

    fun clearScannedCodes() {
        processedCodes.clear()
        _uiState.value = _uiState.value.copy(
            scannedCodes = emptyList(),
            lastScannedCode = null,
            foundProduct = null
        )
    }

    fun stopScanning() {
        _uiState.value = _uiState.value.copy(isScanning = false)
        cameraExecutor?.shutdown()
        cameraExecutor = null
    }

    override fun onCleared() {
        super.onCleared()
        barcodeScanner.close()
        cameraExecutor?.shutdown()
    }

    // Analyzer de imagen para ML Kit
    private inner class BarcodeAnalyzer(
        private val onBarcodeDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {
        
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
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
                                // Aceptar varios formatos de código de barras
                                when (barcode.format) {
                                    Barcode.FORMAT_EAN_13,
                                    Barcode.FORMAT_EAN_8,
                                    Barcode.FORMAT_UPC_A,
                                    Barcode.FORMAT_UPC_E,
                                    Barcode.FORMAT_CODE_128,
                                    Barcode.FORMAT_CODE_39,
                                    Barcode.FORMAT_CODE_93,
                                    Barcode.FORMAT_QR_CODE,
                                    Barcode.FORMAT_DATA_MATRIX -> {
                                        onBarcodeDetected(value)
                                    }
                                }
                            }
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }
}

// Clase auxiliar para entrada manual de código
data class ManualCodeEntry(
    val code: String,
    val type: CodeType
)

enum class CodeType {
    BARCODE, IDENTIFIER, NAME
}
