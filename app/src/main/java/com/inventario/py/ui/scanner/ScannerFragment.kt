package com.inventario.py.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inventario.py.R
import com.inventario.py.data.local.entity.ProductEntity
import com.inventario.py.databinding.FragmentScannerBinding
import com.inventario.py.ui.main.MainActivity
import com.inventario.py.ui.sales.CartViewModel
import com.inventario.py.utils.toGuaraniFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ScannerFragment : Fragment() {

    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScannerViewModel by viewModels()
    private val cartViewModel: CartViewModel by viewModels()

    // Navigation args
    private val args: ScannerFragmentArgs by navArgs()

    private var toneGenerator: ToneGenerator? = null
    private var scanLineAnimation: Animation? = null

    // Permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
        if (isGranted) {
            startScanning()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupClickListeners()
        observeState()
        
        // Set scan mode based on navigation args
        if (args.forSale) {
            viewModel.setScanMode(ScanMode.ADD_TO_CART)
        }
        
        // Initialize tone generator for beep sound
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        } catch (e: Exception) {
            // Ignore - sound is optional
        }
        
        checkPermissionAndStart()
    }

    private fun setupUI() {
        // Setup scan line animation
        setupScanLineAnimation()
        
        // Initially hide result card
        binding.cardScanResult.isVisible = false
        binding.layoutPermissionDenied.isVisible = false
    }

    private fun setupScanLineAnimation() {
        scanLineAnimation = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_PARENT, 0f,
            Animation.RELATIVE_TO_SELF, 0f,
            Animation.RELATIVE_TO_SELF, 1f
        ).apply {
            duration = 2000
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        binding.scanLine.startAnimation(scanLineAnimation)
    }

    private fun setupClickListeners() {
        binding.apply {
            // Back button
            btnBack.setOnClickListener {
                findNavController().navigateUp()
            }

            // Flash toggle
            btnFlash.setOnClickListener {
                viewModel.toggleFlash()
            }

            // Manual entry
            btnManualEntry.setOnClickListener {
                showManualEntryDialog()
            }

            // Grant permission button
            btnGrantPermission.setOnClickListener {
                requestCameraPermission()
            }

            // Scan again button
            btnScanAgain.setOnClickListener {
                hideScanResult()
                viewModel.clearScannedCodes()
            }

            // Add to cart button
            btnAddToCart.setOnClickListener {
                viewModel.uiState.value.foundProduct?.let { product ->
                    addProductToCart(product)
                }
            }

            // Add product button (when product not found)
            btnAddProduct.setOnClickListener {
                viewModel.uiState.value.lastScannedCode?.let { barcode ->
                    navigateToAddProduct(barcode)
                }
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        updateUI(state)
                    }
                }

                launch {
                    viewModel.events.collectLatest { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun updateUI(state: ScannerUiState) {
        binding.apply {
            // Update flash button icon
            btnFlash.setImageResource(
                if (state.isFlashOn) R.drawable.ic_flash_off
                else R.drawable.ic_flash
            )

            // Update permission denied view
            layoutPermissionDenied.isVisible = !state.hasCameraPermission && !state.isScanning
            previewView.isVisible = state.hasCameraPermission

            // Scan frame visibility
            scanFrame.isVisible = state.isScanning && !cardScanResult.isVisible
        }
    }

    private fun handleEvent(event: ScannerEvent) {
        when (event) {
            is ScannerEvent.CodeScanned -> {
                playBeepSound()
            }
            is ScannerEvent.ProductFound -> {
                showProductFound(event.product)
            }
            is ScannerEvent.ProductNotFound -> {
                showProductNotFound(event.code)
            }
            is ScannerEvent.ProductAddedToCart -> {
                addProductToCart(event.product)
            }
            is ScannerEvent.Error -> {
                showError(event.message)
            }
            is ScannerEvent.CameraPermissionRequired -> {
                binding.layoutPermissionDenied.isVisible = true
            }
        }
    }

    private fun showProductFound(product: ProductEntity) {
        binding.apply {
            cardScanResult.isVisible = true
            layoutProductFound.isVisible = true
            layoutProductNotFound.isVisible = false

            // Load product info
            tvProductName.text = product.name
            tvProductPrice.text = product.salePrice.toGuaraniFormat()
            tvProductStock.text = getString(R.string.stock_count, product.totalStock)

            // Load image with Glide
            if (!product.imageUrl.isNullOrEmpty()) {
                Glide.with(this@ScannerFragment)
                    .load(product.imageUrl)
                    .placeholder(R.drawable.placeholder_product)
                    .error(R.drawable.placeholder_product)
                    .into(ivProductImage)
            } else {
                ivProductImage.setImageResource(R.drawable.placeholder_product)
            }

            // Stock indicator color
            val stockColor = when {
                product.totalStock <= 0 -> R.color.stock_out
                product.totalStock <= product.lowStockThreshold -> R.color.stock_low
                else -> R.color.stock_ok
            }
            tvProductStock.setTextColor(ContextCompat.getColor(requireContext(), stockColor))

            // Update button text based on mode
            btnAddToCart.text = if (args.forSale) {
                getString(R.string.add_to_cart)
            } else {
                getString(R.string.view_product)
            }
        }

        // Pause scan line animation
        binding.scanLine.clearAnimation()
    }

    private fun showProductNotFound(code: String) {
        binding.apply {
            cardScanResult.isVisible = true
            layoutProductFound.isVisible = false
            layoutProductNotFound.isVisible = true

            tvBarcodeNotFound.text = code
        }

        binding.scanLine.clearAnimation()
    }

    private fun hideScanResult() {
        binding.cardScanResult.isVisible = false
        binding.scanLine.startAnimation(scanLineAnimation)
    }

    private fun addProductToCart(product: ProductEntity) {
        cartViewModel.addToCart(product, null, 1)
        
        Snackbar.make(
            binding.root,
            getString(R.string.product_added_to_cart, product.name),
            Snackbar.LENGTH_SHORT
        ).setAction(getString(R.string.view_cart)) {
            findNavController().navigate(R.id.cartFragment)
        }.show()

        // Clear and continue scanning if in add to cart mode
        if (args.forSale) {
            hideScanResult()
            viewModel.clearScannedCodes()
        }
    }

    private fun showManualEntryDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.enter_code_or_name)
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.manual_entry))
            .setView(input)
            .setPositiveButton(getString(R.string.search)) { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    cartViewModel.searchAndAddProduct(query)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun navigateToAddProduct(barcode: String) {
        (requireActivity() as? MainActivity)?.navigateToAddProduct(barcode)
    }

    private fun playBeepSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            // Ignore - sound is optional
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun checkPermissionAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.onPermissionResult(true)
                startScanning()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionRationale()
            }
            else -> {
                requestCameraPermission()
            }
        }
    }

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showPermissionRationale() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.camera_permission_required))
            .setMessage(getString(R.string.camera_permission_description))
            .setPositiveButton(getString(R.string.grant_permission)) { _, _ ->
                requestCameraPermission()
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                findNavController().navigateUp()
            }
            .show()
    }

    private fun startScanning() {
        binding.previewView.post {
            viewModel.startCamera(viewLifecycleOwner, binding.previewView)
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.uiState.value.hasCameraPermission) {
            startScanning()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopScanning()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        toneGenerator?.release()
        toneGenerator = null
        scanLineAnimation?.cancel()
        _binding = null
    }
}
