package com.inventario.py.ui.products

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inventario.py.R
import com.inventario.py.data.local.entity.ProductVariantEntity
import com.inventario.py.databinding.FragmentAddProductBinding
import com.inventario.py.ui.adapters.EditVariantAdapter
import com.inventario.py.ui.main.MainActivity
import com.inventario.py.utils.CurrencyUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddProductFragment : Fragment() {

    private var _binding: FragmentAddProductBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddProductViewModel by viewModels()
    private val args: AddProductFragmentArgs by navArgs()

    private lateinit var variantAdapter: EditVariantAdapter
    private var selectedImageUri: Uri? = null

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                loadSelectedImage(uri)
                viewModel.setImageUri(uri.toString())
            }
        }
    }

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            selectedImageUri?.let { uri ->
                loadSelectedImage(uri)
                viewModel.setImageUri(uri.toString())
            }
        }
    }

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Snackbar.make(binding.root, R.string.camera_permission_denied, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupInputListeners()
        setupVariantSection()
        setupClickListeners()
        setupCategorySpinner()
        setupSupplierSpinner()
        observeState()

        // Cargar producto si estamos editando
        args.productId?.let { productId ->
            viewModel.loadProduct(productId)
        }

        // Si viene un barcode escaneado
        args.barcode?.let { barcode ->
            binding.etBarcode.setText(barcode)
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Cambiar título según modo
        binding.toolbar.title = if (args.productId != null) {
            getString(R.string.edit_product)
        } else {
            getString(R.string.add_product)
        }
    }

    private fun setupInputListeners() {
        with(binding) {
            // Formatear precios al perder foco
            etSalePrice.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    formatPriceField(etSalePrice)
                }
            }

            etPurchasePrice.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    formatPriceField(etPurchasePrice)
                }
            }

            // Validación en tiempo real
            etName.doAfterTextChanged {
                tilName.error = if (it.isNullOrBlank()) getString(R.string.required_field) else null
            }
        }
    }

    private fun formatPriceField(editText: com.google.android.material.textfield.TextInputEditText) {
        val text = editText.text.toString().replace(".", "").replace(",", "")
        val value = text.toLongOrNull() ?: 0L
        if (value > 0) {
            editText.setText(CurrencyUtils.formatNumber(value))
        }
    }

    private fun setupVariantSection() {
        variantAdapter = EditVariantAdapter(
            onDeleteClick = { variant ->
                viewModel.removeVariant(variant)
            },
            onEditClick = { variant ->
                showEditVariantDialog(variant)
            }
        )

        binding.rvVariants.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = variantAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        with(binding) {
            // Imagen
            ivProductImage.setOnClickListener {
                showImagePickerDialog()
            }

            fabAddImage.setOnClickListener {
                showImagePickerDialog()
            }

            // Escanear código de barras
            tilBarcode.setOnClickListener {
                findNavController().navigate(
                    R.id.action_add_to_scanner,
                    Bundle().apply {
                        putString("scanMode", "barcode")
                    }
                )
            }

            // Agregar variante
            btnAddVariant.setOnClickListener {
                showAddVariantDialog()
            }

            // Guardar producto
            btnSave.setOnClickListener {
                saveProduct()
            }
        }
    }

    private fun setupCategorySpinner() {
        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            val categoryNames = listOf(getString(R.string.select_category)) + categories.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categoryNames)
            binding.spinnerCategory.setAdapter(adapter)
        }
    }

    private fun setupSupplierSpinner() {
        viewModel.suppliers.observe(viewLifecycleOwner) { suppliers ->
            val supplierNames = listOf(getString(R.string.select_supplier)) + suppliers.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, supplierNames)
            binding.spinnerSupplier.setAdapter(adapter)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Estado de UI
                launch {
                    viewModel.uiState.collectLatest { state ->
                        updateUI(state)
                    }
                }

                // Eventos
                launch {
                    viewModel.events.collectLatest { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun updateUI(state: AddProductUiState) {
        with(binding) {
            // Mostrar carga
            progressBar.isVisible = state.isLoading

            // Mostrar datos del producto si estamos editando
            if (state.product != null && !state.isDataLoaded) {
                populateFields(state)
                viewModel.markDataAsLoaded()
            }

            // Variantes
            variantAdapter.submitList(state.variants)
            layoutVariants.isVisible = state.variants.isNotEmpty()

            // Solo mostrar proveedor para dueño
            val isOwner = (activity as? MainActivity)?.isOwner() == true
            layoutSupplier.isVisible = isOwner
            tilPurchasePrice.isVisible = isOwner
        }
    }

    private fun populateFields(state: AddProductUiState) {
        val product = state.product?.product ?: return

        with(binding) {
            etName.setText(product.name)
            etDescription.setText(product.description)
            etBarcode.setText(product.barcode)
            etIdentifier.setText(product.identifier)
            etStock.setText(product.totalStock.toString())
            etMinStock.setText(product.minStockAlert.toString())
            etSalePrice.setText(CurrencyUtils.formatNumber(product.salePrice))
            etPurchasePrice.setText(CurrencyUtils.formatNumber(product.purchasePrice))

            // Imagen
            if (!product.imageUrl.isNullOrEmpty()) {
                Glide.with(this@AddProductFragment)
                    .load(product.imageUrl)
                    .placeholder(R.drawable.placeholder_product)
                    .into(ivProductImage)
            }
        }
    }

    private fun handleEvent(event: AddProductEvent) {
        when (event) {
            is AddProductEvent.ProductSaved -> {
                Snackbar.make(binding.root, R.string.product_saved, Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            is AddProductEvent.Error -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
            }
            is AddProductEvent.ValidationError -> {
                showValidationErrors(event.errors)
            }
        }
    }

    private fun showValidationErrors(errors: List<String>) {
        errors.forEach { error ->
            when {
                error.contains("nombre", ignoreCase = true) -> {
                    binding.tilName.error = error
                }
                error.contains("precio", ignoreCase = true) -> {
                    binding.tilSalePrice.error = error
                }
            }
        }
    }

    private fun saveProduct() {
        with(binding) {
            val name = etName.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val barcode = etBarcode.text.toString().trim()
            val identifier = etIdentifier.text.toString().trim()
            val stock = etStock.text.toString().toIntOrNull() ?: 0
            val minStock = etMinStock.text.toString().toIntOrNull() ?: 5
            val salePrice = etSalePrice.text.toString()
                .replace(".", "")
                .replace(",", "")
                .toLongOrNull() ?: 0L
            val purchasePrice = etPurchasePrice.text.toString()
                .replace(".", "")
                .replace(",", "")
                .toLongOrNull() ?: 0L

            // Validaciones básicas
            if (name.isBlank()) {
                tilName.error = getString(R.string.required_field)
                return
            }

            if (salePrice <= 0) {
                tilSalePrice.error = getString(R.string.invalid_price)
                return
            }

            viewModel.saveProduct(
                name = name,
                description = description.ifEmpty { null },
                barcode = barcode.ifEmpty { null },
                identifier = identifier.ifEmpty { null },
                stock = stock,
                minStock = minStock,
                salePrice = salePrice,
                purchasePrice = purchasePrice,
                categoryId = getSelectedCategoryId(),
                supplierId = getSelectedSupplierId()
            )
        }
    }

    private fun getSelectedCategoryId(): String? {
        val position = binding.spinnerCategory.text.toString()
        return viewModel.categories.value?.find { it.name == position }?.id
    }

    private fun getSelectedSupplierId(): String? {
        val position = binding.spinnerSupplier.text.toString()
        return viewModel.suppliers.value?.find { it.name == position }?.id
    }

    private fun showImagePickerDialog() {
        val options = arrayOf(
            getString(R.string.take_photo),
            getString(R.string.choose_from_gallery)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.select_image))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun openCamera() {
        val photoUri = viewModel.createImageUri(requireContext())
        selectedImageUri = photoUri
        cameraLauncher.launch(photoUri)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun loadSelectedImage(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .placeholder(R.drawable.placeholder_product)
            .centerCrop()
            .into(binding.ivProductImage)
    }

    private fun showAddVariantDialog() {
        // Implementar diálogo para agregar variante
        // Similar al que existe en ProductDetailFragment
        val variantTypes = arrayOf(
            getString(R.string.variant_color),
            getString(R.string.variant_type),
            getString(R.string.variant_capacity),
            getString(R.string.variant_size),
            getString(R.string.variant_custom)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.select_variant_type))
            .setItems(variantTypes) { _, which ->
                showVariantValueDialog(variantTypes[which])
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showVariantValueDialog(variantType: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_variant, null)

        // Configurar campos del diálogo
        // ... implementar según tu layout

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_variant))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                // Obtener valores y agregar variante
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showEditVariantDialog(variant: ProductVariantEntity) {
        // Similar a showAddVariantDialog pero con valores precargados
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
