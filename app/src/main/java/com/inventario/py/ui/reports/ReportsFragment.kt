package com.inventario.py.ui.reports

import android.app.DatePickerDialog
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
import com.google.android.material.snackbar.Snackbar
import com.inventario.py.R
import com.inventario.py.databinding.FragmentReportsBinding
import com.inventario.py.ui.adapters.TopProductAdapter
import com.inventario.py.ui.main.MainActivity
import com.inventario.py.ui.main.RefreshableFragment
import com.inventario.py.utils.CurrencyUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class ReportsFragment : Fragment(), RefreshableFragment {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReportsViewModel by viewModels()
    private lateinit var topProductAdapter: TopProductAdapter
    
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PY"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAdapter()
        setupDateRangeSelector()
        setupClickListeners()
        observeState()
        
        // Load default (today)
        viewModel.loadReports(DateRange.TODAY)
    }

    private fun setupAdapter() {
        topProductAdapter = TopProductAdapter { topProduct ->
            (activity as? MainActivity)?.navigateToProductDetail(topProduct.productId)
        }
        
        binding.rvTopProducts.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = topProductAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupDateRangeSelector() {
        binding.chipGroupDateRange.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chipToday) -> {
                    viewModel.loadReports(DateRange.TODAY)
                    hideCustomDateRange()
                }
                checkedIds.contains(R.id.chipWeek) -> {
                    viewModel.loadReports(DateRange.WEEK)
                    hideCustomDateRange()
                }
                checkedIds.contains(R.id.chipMonth) -> {
                    viewModel.loadReports(DateRange.MONTH)
                    hideCustomDateRange()
                }
                checkedIds.contains(R.id.chipCustom) -> {
                    showCustomDateRange()
                }
            }
        }
    }

    private fun showCustomDateRange() {
        binding.layoutCustomDate.visibility = View.VISIBLE
    }

    private fun hideCustomDateRange() {
        binding.layoutCustomDate.visibility = View.GONE
    }

    private fun setupClickListeners() {
        with(binding) {
            // Swipe to refresh
            swipeRefresh.setOnRefreshListener {
                viewModel.refreshReports()
            }
            
            // Start date picker
            btnStartDate.setOnClickListener {
                showDatePicker { date ->
                    viewModel.setCustomStartDate(date)
                    btnStartDate.text = dateFormat.format(date)
                }
            }
            
            // End date picker
            btnEndDate.setOnClickListener {
                showDatePicker { date ->
                    viewModel.setCustomEndDate(date)
                    btnEndDate.text = dateFormat.format(date)
                }
            }
            
            // Apply custom date range
            btnApplyDateRange.setOnClickListener {
                viewModel.applyCustomDateRange()
            }
            
            // Export sales report
            btnExportSales.setOnClickListener {
                viewModel.exportSalesReport()
            }
            
            // Export inventory report
            btnExportInventory.setOnClickListener {
                viewModel.exportInventoryReport()
            }
        }
    }

    private fun showDatePicker(onDateSelected: (Date) -> Unit) {
        val calendar = Calendar.getInstance()
        
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                onDateSelected(calendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.reportsState.collect { state ->
                        updateUI(state)
                    }
                }

                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.swipeRefresh.isRefreshing = isLoading
                        binding.loadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
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
                    viewModel.exportResult.collect { filePath ->
                        filePath?.let {
                            showExportSuccess(it)
                            viewModel.clearExportResult()
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(state: ReportsState) {
        with(binding) {
            // Sales summary
            tvTotalSales.text = CurrencyUtils.formatGuarani(state.totalSales)
            tvTransactionCount.text = state.transactionCount.toString()
            tvAverageSale.text = CurrencyUtils.formatGuarani(state.averageSale)
            tvTotalProfit.text = CurrencyUtils.formatGuarani(state.totalProfit)
            
            // Payment methods breakdown
            tvCashTotal.text = CurrencyUtils.formatGuarani(state.cashTotal)
            tvCashPercentage.text = String.format("%.1f%%", state.cashPercentage)
            
            tvCardTotal.text = CurrencyUtils.formatGuarani(state.cardTotal)
            tvCardPercentage.text = String.format("%.1f%%", state.cardPercentage)
            
            tvTransferTotal.text = CurrencyUtils.formatGuarani(state.transferTotal)
            tvTransferPercentage.text = String.format("%.1f%%", state.transferPercentage)
            
            // Inventory summary
            tvTotalProducts.text = state.totalProducts.toString()
            tvLowStockCount.text = state.lowStockCount.toString()
            tvOutOfStockCount.text = state.outOfStockCount.toString()
            tvInventoryValue.text = CurrencyUtils.formatGuarani(state.inventoryValue)
            
            // Top products
            if (state.topProducts.isEmpty()) {
                rvTopProducts.visibility = View.GONE
                tvNoTopProducts.visibility = View.VISIBLE
            } else {
                rvTopProducts.visibility = View.VISIBLE
                tvNoTopProducts.visibility = View.GONE
                topProductAdapter.submitList(state.topProducts)
            }
            
            // Chart placeholder - would integrate with MPAndroidChart
            // For now, show a placeholder
            tvChartPlaceholder.visibility = if (state.totalSales > 0) View.GONE else View.VISIBLE
        }
    }

    private fun showExportSuccess(filePath: String) {
        Snackbar.make(
            binding.root,
            "Reporte exportado exitosamente",
            Snackbar.LENGTH_LONG
        ).setAction("Abrir") {
            // Open file with external app
            viewModel.openExportedFile(filePath)
        }.show()
    }

    override fun onRefresh() {
        viewModel.refreshReports()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

enum class DateRange {
    TODAY, WEEK, MONTH, CUSTOM
}
