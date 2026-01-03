package com.inventario.py.ui.products

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inventario.py.R
import com.inventario.py.data.local.entity.ProductVariant
import com.inventario.py.databinding.FragmentProductDetailBinding
import com.inventario.py.ui.adapters.StockMovementAdapter
import com.inventario.py.ui.adapters.VariantAdapter
import com.inventario.py.ui.main.MainActivity
import com.inventario.py.utils.CurrencyUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.inventario.py.data.local.entity.category
import com.inventario.py.data.local.entity.costPrice
import com.inventario.py.data.local.entity.currentStock
import com.inventario.py.data.local.entity.minStock
import com.inventario.py.data.local.entity.supplier
import com.inventario.py.data.local.entity.toProductVariant

@AndroidEntryPoint
class ProductDetailFragment : Fragment() {

    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProductDetailViewModel by viewModels()
    private val args: ProductDetailFragmentArgs by navArgs()
    
    private lateinit var variantAdapter: VariantAdapter
    private lateinit var stockMovementAdapter: StockMovementAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupMenu()
        setupAdapters()
        setupClickListeners()
        observeState()
        
        viewModel.loadProduct(args.productId)
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_product_detail, menu)
                
                // Hide edit/delete for non-owners if needed
                val isOwner = (activity as? MainActivity)?.isOwner() == true
                menu.findItem(R.id.action_delete)?.isVisible = isOwner
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_edit -> {
                        navigateToEdit()
                        true
                    }
                    R.id.action_delete -> {
                        showDeleteConfirmation()
                        true
                    }
                    R.id.action_share -> {
                        shareProduct()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupAdapters() {
        // Variants adapter
        variantAdapter = VariantAdapter(
            onEditClick = { variant ->
                showEditVariantDialog(variant)
            }
        )
        binding.rvVariants.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = variantAdapter
            isNestedScrollingEnabled = false
        }

        // Stock movements adapter
        stockMovementAdapter = StockMovementAdapter()
        binding.rvStockMovements.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = stockMovementAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        with(binding) {
            // Back button
            toolbar.setNavigationOnClickListener {
                findNavController().navigateUp()
            }
            
            // Add to cart button
            btnAddToCart.setOnClickListener {
                viewModel.addToCart()
            }
            
            // Sell button
            btnSell.setOnClickListener {
                viewModel.addToCart()
                findNavController().navigate(R.id.cartFragment)
            }
            
            // Adjust stock button
            btnAdjustStock.setOnClickListener {
                showAdjustStockDialog()
            }
            
            // Add variant button
            btnAddVariant.setOnClickListener {
                showAddVariantDialog()
            }
            
            // View all stock movements
            btnViewAllMovements.setOnClickListener {
                // Could navigate to full stock movements list
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.productState.collect { state ->
                        updateUI(state)
                    }
                }

                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.message.collect { message ->
                        message?.let {
                            Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show()
                            viewModel.clearMessage()
                        }
                    }
                }
                
                launch {
                    viewModel.deleteSuccess.collect { deleted ->
                        if (deleted) {
                            findNavController().navigateUp()
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(state: ProductDetailState) {
        val product = state.product ?: return
        val isOwner = (activity as? MainActivity)?.isOwner() == true
        
        with(binding) {
            // Toolbar title
            toolbar.title = product.product.name
            
            // Product image
            if (product.product.imageUrl.isNullOrEmpty()) {
                ivProductImage.setImageResource(R.drawable.placeholder_product)
            } else {
                Glide.with(this@ProductDetailFragment)
                    .load(product.product.imageUrl)
                    .placeholder(R.drawable.placeholder_product)
                    .error(R.drawable.placeholder_product)
                    .centerCrop()
                    .into(ivProductImage)
            }
            
            // Basic info
            tvProductName.text = product.product.name
            tvCategory.text = product.product.category ?: "Sin categoría"
            tvBarcode.text = product.product.barcode ?: "Sin código"
            tvDescription.text = product.product.description ?: "Sin descripción"
            
            // Prices
            tvSalePrice.text = CurrencyUtils.formatGuarani(product.product.salePrice)
            
            // Cost price - only show for owner
            if (isOwner) {
                layoutCostPrice.visibility = View.VISIBLE
                tvCostPrice.text = CurrencyUtils.formatGuarani(product.product.costPrice)
                
                // Calculate profit margin
                val margin = if (product.product.costPrice > 0) {
                    ((product.product.salePrice - product.product.costPrice) / product.product.costPrice) * 100
                } else {
                    0.0
                }
                tvProfitMargin.text = String.format("%.1f%%", margin)
            } else {
                layoutCostPrice.visibility = View.GONE
            }
            
            // Stock information
            val totalStock = if (product.variants.isEmpty()) {
                product.product.currentStock
            } else {
                product.variants.sumOf { it.currentStock }
            }
            
            tvCurrentStock.text = totalStock.toString()
            tvMinStock.text = "Mínimo: ${product.product.minStock}"
            
            // Stock status indicator
            when {
                totalStock <= 0 -> {
                    stockIndicator.setBackgroundResource(R.drawable.bg_stock_out)
                    tvStockStatus.text = "Agotado"
                    tvStockStatus.setTextColor(resources.getColor(R.color.stock_out, null))
                }
                totalStock <= product.product.minStock -> {
                    stockIndicator.setBackgroundResource(R.drawable.bg_stock_low)
                    tvStockStatus.text = "Stock bajo"
                    tvStockStatus.setTextColor(resources.getColor(R.color.stock_low, null))
                }
                else -> {
                    stockIndicator.setBackgroundResource(R.drawable.bg_stock_ok)
                    tvStockStatus.text = "En stock"
                    tvStockStatus.setTextColor(resources.getColor(R.color.stock_ok, null))
                }
            }
            
            // Variants section
            if (product.variants.isEmpty()) {
                cardVariants.visibility = View.GONE
            } else {
                cardVariants.visibility = View.VISIBLE
                variantAdapter.submitList(product.variants.map { it.toProductVariant() })
            }
            
            // Supplier info - only for owner
            if (isOwner && !product.product.supplier.isNullOrEmpty()) {
                cardSupplier.visibility = View.VISIBLE
                tvSupplier.text = product.product.supplier
            } else {
                cardSupplier.visibility = View.GONE
            }
            
            // Created date - only for owner
            if (isOwner) {
                tvCreatedAt.visibility = View.VISIBLE
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PY"))
                tvCreatedAt.text = "Creado: ${dateFormat.format(product.product.createdAt)}"
            } else {
                tvCreatedAt.visibility = View.GONE
            }
            
            // Stock movements
            if (state.stockMovements.isEmpty()) {
                rvStockMovements.visibility = View.GONE
                tvNoMovements.visibility = View.VISIBLE
            } else {
                rvStockMovements.visibility = View.VISIBLE
                tvNoMovements.visibility = View.GONE
                stockMovementAdapter.submitList(state.stockMovements.take(5))
                
                btnViewAllMovements.visibility = if (state.stockMovements.size > 5) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
            
            // Disable add to cart if out of stock
            btnAddToCart.isEnabled = totalStock > 0
            btnSell.isEnabled = totalStock > 0
        }
    }

    private fun showAdjustStockDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_adjust_stock, null)
        val etQuantity = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etQuantity)
        val etReason = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etReason)
        val rgType = dialogView.findViewById<android.widget.RadioGroup>(R.id.rgType)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ajustar Stock")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val quantity = etQuantity.text.toString().toIntOrNull() ?: 0
                etReason.text.toString()
                val isIncrease = rgType.checkedRadioButtonId == R.id.rbIncrease
                
                if (quantity > 0) {
                    viewModel.adjustStock(
                        quantity = if (isIncrease) quantity else -quantity
                    )
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showAddVariantDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_variant, null)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVariantName)
        val etSku = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSku)
        val etPrice = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPrice)
        val etStock = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etStock)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Agregar Variante")
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val name = etName.text.toString()
                val sku = etSku.text.toString()
                val price = etPrice.text.toString().toDoubleOrNull()
                val stock = etStock.text.toString().toIntOrNull() ?: 0

                if (name.isNotEmpty()) {
                    viewModel.addVariant(name, sku, price, stock)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditVariantDialog(variant: ProductVariant) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_variant, null)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVariantName)
        val etSku = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSku)
        val etPrice = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPrice)
        val etStock = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etStock)

        // Pre-fill values
        etName.setText(variant.name)
        etSku.setText(variant.sku)
        if (variant.additionalPrice > 0) { etPrice.setText(variant.additionalPrice.toString()) }
        etStock.setText(variant.stock.toString())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Editar Variante")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val name = etName.text.toString()
                val sku = etSku.text.toString()
                val price = etPrice.text.toString().toDoubleOrNull()
                val stock = etStock.text.toString().toIntOrNull() ?: variant.stock
                val priceModifier = (price ?: 0.0).toLong()

                if (name.isNotEmpty()) {
                    viewModel.updateVariant(variant.copy(
                        name = name,
                        sku = sku,
                        additionalPrice = priceModifier,
                        stock = stock
                    ))
                }
            }
            .setNeutralButton("Eliminar") { _, _ ->
                showDeleteVariantConfirmation(variant)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteVariantConfirmation(variant: ProductVariant) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar Variante")
            .setMessage("¿Está seguro de eliminar la variante '${variant.name}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteVariant(variant)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar Producto")
            .setMessage("¿Está seguro de eliminar este producto? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.deleteProduct()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun navigateToEdit() {
        // TEMPORAL: Mostrar toast
        android.widget.Toast.makeText(
            requireContext(),
            "Edición de producto disponible próximamente",
            android.widget.Toast.LENGTH_SHORT
        ).show()

        // ORIGINAL (comentado):
        // val bundle = Bundle().apply {
        //     putString("productId", args.productId)
        // }
        // findNavController().navigate(R.id.addProductFragment, bundle)
    }

    private fun shareProduct() {
        val product = viewModel.productState.value.product?.product ?: return
        
        val shareText = buildString {
            append("${product.name}\n")
            append("Precio: ${CurrencyUtils.formatGuarani(product.salePrice)}\n")
            product.description?.let { append("$it\n") }
            product.barcode?.let { append("Código: $it") }
        }
        
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
        }
        startActivity(android.content.Intent.createChooser(intent, "Compartir producto"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
