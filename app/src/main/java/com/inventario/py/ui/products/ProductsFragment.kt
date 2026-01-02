package com.inventario.py.ui.products

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.inventario.py.R
import com.inventario.py.data.local.entity.StockFilter
import com.inventario.py.databinding.FragmentProductsBinding
import com.inventario.py.ui.adapters.ProductAdapter
import com.inventario.py.ui.main.MainActivity
import com.inventario.py.ui.main.RefreshableFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.appcompat.widget.SearchView
import com.inventario.py.data.local.entity.SortOption

@AndroidEntryPoint
class ProductsFragment : Fragment(), RefreshableFragment {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProductsViewModel by viewModels()
    private lateinit var productAdapter: ProductAdapter
    
    private var isGridView = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Check for low stock filter from arguments
        arguments?.getBoolean("filterLowStock", false)?.let { filterLowStock ->
            if (filterLowStock) {
                viewModel.setStockFilter(StockFilter.LOW_STOCK)
            }
        }
        
        setupAdapter()
        setupSearchView()
        setupFilters()
        setupClickListeners()
        observeState()
        
        viewModel.loadProducts()
        viewModel.loadCategories()
    }

    private fun setupAdapter() {
        productAdapter = ProductAdapter(
            onItemClick = { product ->
                (activity as? MainActivity)?.navigateToProductDetail(product.product.id)
            },
            onAddToCartClick = { product ->
                viewModel.addToCart(product)
            }
        )
        
        updateLayoutManager()
        binding.rvProducts.adapter = productAdapter
    }

    private fun updateLayoutManager() {
        binding.rvProducts.layoutManager = if (isGridView) {
            GridLayoutManager(context, 2)
        } else {
            LinearLayoutManager(context)
        }
        productAdapter.setViewType(isGridView)
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setSearchQuery(query ?: "")
                binding.searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })
    }

    private fun setupFilters() {
        // Stock filter chips
        binding.chipGroupStock.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when {
                checkedIds.contains(R.id.chipAllStock) -> StockFilter.ALL
                checkedIds.contains(R.id.chipInStock) -> StockFilter.IN_STOCK
                checkedIds.contains(R.id.chipLowStock) -> StockFilter.LOW_STOCK
                checkedIds.contains(R.id.chipOutOfStock) -> StockFilter.OUT_OF_STOCK
                else -> StockFilter.ALL
            }
            viewModel.setStockFilter(filter)
        }

        // Sort spinner
        val sortOptions = arrayOf(
            "Nombre (A-Z)",
            "Nombre (Z-A)", 
            "Precio (menor)",
            "Precio (mayor)",
            "Stock (menor)",
            "Stock (mayor)",
            "Más reciente"
        )
        binding.spinnerSort.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            sortOptions
        )
        
        binding.spinnerSort.setOnItemSelectedListener { position ->
            val sortOption = SortOption.entries[position]
            viewModel.setSortOption(sortOption)
        }
    }

    private fun setupClickListeners() {
        with(binding) {
            // Swipe to refresh
            swipeRefresh.setOnRefreshListener {
                viewModel.loadProducts()
            }
            
            // FAB - Add product
            fabAddProduct.setOnClickListener {
                (activity as? MainActivity)?.navigateToAddProduct()
            }
            
            // Toggle view mode
            btnToggleView.setOnClickListener {
                isGridView = !isGridView
                updateLayoutManager()
                btnToggleView.setIconResource(
                    if (isGridView) R.drawable.ic_view_list else R.drawable.ic_view_grid
                )
            }
            
            // Scanner button
            btnScanner.setOnClickListener {
                (activity as? MainActivity)?.navigateToScanner(forSale = false)
            }
            
            // Filter button
            btnFilter.setOnClickListener {
                toggleFilterVisibility()
            }
            
            // Clear filters
            btnClearFilters.setOnClickListener {
                clearAllFilters()
            }
        }
    }

    private fun toggleFilterVisibility() {
        binding.filterContainer.visibility = 
            if (binding.filterContainer.visibility == View.VISIBLE) {
                View.GONE
            } else {
                View.VISIBLE
            }
    }

    private fun clearAllFilters() {
        binding.chipAllStock.isChecked = true
        binding.spinnerSort.setSelection(0)
        binding.searchView.setQuery("", false)
        viewModel.clearFilters()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.products.collect { products ->
                        productAdapter.submitList(products)
                        
                        // Show empty state
                        if (products.isEmpty()) {
                            binding.rvProducts.visibility = View.GONE
                            binding.emptyState.visibility = View.VISIBLE
                        } else {
                            binding.rvProducts.visibility = View.VISIBLE
                            binding.emptyState.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.swipeRefresh.isRefreshing = isLoading
                    }
                }

                launch {
                    viewModel.categories.collect { categories ->
                        setupCategoryChips(categories)
                    }
                }

                launch {
                    viewModel.productCount.collect { count ->
                        binding.tvProductCount.text = "$count productos"
                    }
                }
                
                launch {
                    viewModel.addToCartResult.collect { result ->
                        result?.let {
                            showAddToCartResult(it)
                            viewModel.clearAddToCartResult()
                        }
                    }
                }
            }
        }
    }

    private fun setupCategoryChips(categories: List<String>) {
        binding.chipGroupCategory.removeAllViews()
        
        // Add "Todas" chip
        val allChip = Chip(context).apply {
            text = "Todas"
            isCheckable = true
            isChecked = true
            setOnClickListener {
                viewModel.setCategoryFilter(null)
            }
        }
        binding.chipGroupCategory.addView(allChip)
        
        // Add category chips
        categories.forEach { category ->
            val chip = Chip(context).apply {
                text = category
                isCheckable = true
                setOnClickListener {
                    viewModel.setCategoryFilter(category)
                }
            }
            binding.chipGroupCategory.addView(chip)
        }
    }

    private fun showAddToCartResult(success: Boolean) {
        val message = if (success) {
            "Producto agregado al carrito"
        } else {
            "Error al agregar producto"
        }
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            message,
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onRefresh() {
        binding.rvProducts.smoothScrollToPosition(0)
        viewModel.loadProducts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Extension function for spinner item selection
private fun android.widget.Spinner.setOnItemSelectedListener(listener: (Int) -> Unit) {
    onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
            listener(position)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
}
