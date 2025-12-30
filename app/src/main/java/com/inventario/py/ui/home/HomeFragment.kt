package com.inventario.py.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.inventario.py.R
import com.inventario.py.databinding.FragmentHomeBinding
import com.inventario.py.ui.adapters.ProductCompactAdapter
import com.inventario.py.ui.adapters.SaleAdapter
import com.inventario.py.ui.main.MainActivity
import com.inventario.py.ui.main.RefreshableFragment
import com.inventario.py.utils.CurrencyUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.navigation.fragment.findNavController

@AndroidEntryPoint
class HomeFragment : Fragment(), RefreshableFragment {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    
    private lateinit var lowStockAdapter: ProductCompactAdapter
    private lateinit var recentSalesAdapter: SaleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupGreeting()
        setupAdapters()
        setupClickListeners()
        observeState()
        
        viewModel.loadDashboardData()
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Buenos días"
            hour < 18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        
        val userName = viewModel.getUserName()
        binding.tvGreeting.text = "$greeting, $userName"
        
        // Set current date
        val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "PY"))
        binding.tvDate.text = dateFormat.format(Date()).replaceFirstChar { it.uppercase() }
    }

    private fun setupAdapters() {
        // Low stock products adapter
        lowStockAdapter = ProductCompactAdapter { product ->
            (activity as? MainActivity)?.navigateToProductDetail(product.id)
        }
        binding.rvLowStock.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = lowStockAdapter
        }

        // Recent sales adapter
        recentSalesAdapter = SaleAdapter(
            onItemClick = { sale ->
                (activity as? MainActivity)?.navigateToSaleDetail(sale.id)
            },
            showDetails = false
        )
        binding.rvRecentSales.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = recentSalesAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        with(binding) {
            // Quick actions
            btnNewSale.setOnClickListener {
                findNavController().navigate(R.id.cartFragment)
            }
            
            btnScanProduct.setOnClickListener {
                (activity as? MainActivity)?.navigateToScanner(forSale = true)
            }
            
            btnAddProduct.setOnClickListener {
                (activity as? MainActivity)?.navigateToAddProduct()
            }
            
            btnViewReports.setOnClickListener {
                findNavController().navigate(R.id.reportsFragment)
            }
            
            // Swipe to refresh
            swipeRefresh.setOnRefreshListener {
                viewModel.loadDashboardData()
            }
            
            // View all buttons
            btnViewAllLowStock.setOnClickListener {
                findNavController().navigate(R.id.productsFragment)
            }
            
            btnViewAllSales.setOnClickListener {
                findNavController().navigate(R.id.salesFragment)
            }
            
            // Stats cards click
            cardTodaySales.setOnClickListener {
                findNavController().navigate(R.id.reportsFragment)
            }
            
            cardTotalProducts.setOnClickListener {
                findNavController().navigate(R.id.productsFragment)
            }
            
            cardLowStock.setOnClickListener {
                // Navigate to products with low stock filter
                val bundle = Bundle().apply {
                    putBoolean("filterLowStock", true)
                }
                findNavController().navigate(R.id.productsFragment, bundle)
            }
            
            cardPendingSync.setOnClickListener {
                viewModel.syncNow()
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.dashboardState.collect { state ->
                        updateUI(state)
                    }
                }
                
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.swipeRefresh.isRefreshing = isLoading
                    }
                }
            }
        }
    }

    private fun updateUI(state: DashboardState) {
        with(binding) {
            // Today's sales
            tvTodaySales.text = CurrencyUtils.formatGuarani(state.todaySales)
            tvTodayTransactions.text = "${state.todayTransactions} ventas"
            
            // Total products
            tvTotalProducts.text = state.totalProducts.toString()
            tvActiveProducts.text = "${state.activeProducts} activos"
            
            // Low stock count
            tvLowStockCount.text = state.lowStockCount.toString()
            tvOutOfStock.text = "${state.outOfStockCount} agotados"
            
            // Pending sync
            if (state.pendingSyncCount > 0) {
                cardPendingSync.visibility = View.VISIBLE
                tvPendingSync.text = state.pendingSyncCount.toString()
                tvPendingSyncLabel.text = "cambios pendientes"
            } else {
                cardPendingSync.visibility = View.GONE
            }
            
            // Low stock products list
            if (state.lowStockProducts.isEmpty()) {
                rvLowStock.visibility = View.GONE
                tvNoLowStock.visibility = View.VISIBLE
            } else {
                rvLowStock.visibility = View.VISIBLE
                tvNoLowStock.visibility = View.GONE
                lowStockAdapter.submitList(state.lowStockProducts)
            }
            
            // Recent sales list
            if (state.recentSales.isEmpty()) {
                rvRecentSales.visibility = View.GONE
                tvNoRecentSales.visibility = View.VISIBLE
            } else {
                rvRecentSales.visibility = View.VISIBLE
                tvNoRecentSales.visibility = View.GONE
                recentSalesAdapter.submitList(state.recentSales)
            }
        }
    }

    override fun onRefresh() {
        binding.rvLowStock.smoothScrollToPosition(0)
        binding.rvRecentSales.smoothScrollToPosition(0)
        viewModel.loadDashboardData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
