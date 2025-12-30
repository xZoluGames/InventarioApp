package com.inventario.py.ui.sales

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inventario.py.R
import com.inventario.py.data.local.entity.PaymentMethod
import com.inventario.py.data.local.entity.SaleEntity
import com.inventario.py.data.local.entity.SaleStatus
import com.inventario.py.databinding.FragmentSalesBinding
import com.inventario.py.ui.adapters.SaleAdapter
import com.inventario.py.ui.main.MainActivity
import com.inventario.py.ui.main.RefreshableFragment
import com.inventario.py.utils.toGuaraniFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.navigation.fragment.findNavController

@AndroidEntryPoint
class SalesFragment : Fragment(), RefreshableFragment {

    private var _binding: FragmentSalesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SalesHistoryViewModel by viewModels()
    private lateinit var salesAdapter: SaleAdapter

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PY"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilters()
        setupSwipeRefresh()
        setupNewSaleButton()
        observeState()
    }

    private fun setupRecyclerView() {
        salesAdapter = SaleAdapter(
            onSaleClick = { sale ->
                navigateToSaleDetail(sale.id)
            },
            onCancelClick = { sale ->
                showCancelDialog(sale)
            }
        )

        binding.recyclerViewSales.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = salesAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupFilters() {
        // Date filter chips
        binding.chipGroupDateFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val filter = when (checkedIds.first()) {
                    R.id.chipToday -> DateFilter.TODAY
                    R.id.chipWeek -> DateFilter.THIS_WEEK
                    R.id.chipMonth -> DateFilter.THIS_MONTH
                    R.id.chipYear -> DateFilter.THIS_YEAR
                    R.id.chipAll -> DateFilter.ALL
                    R.id.chipCustom -> {
                        showDateRangePicker()
                        return@setOnCheckedStateChangeListener
                    }
                    else -> DateFilter.TODAY
                }
                viewModel.setDateFilter(filter)
            }
        }

        // Status filter
        binding.chipGroupStatus.setOnCheckedStateChangeListener { _, checkedIds ->
            val status = when {
                checkedIds.contains(R.id.chipCompleted) -> SaleStatus.COMPLETED
                checkedIds.contains(R.id.chipCancelled) -> SaleStatus.CANCELLED
                checkedIds.contains(R.id.chipPending) -> SaleStatus.PENDING
                else -> null
            }
            viewModel.setStatusFilter(status)
        }

        // Payment method filter
        binding.chipGroupPayment.setOnCheckedStateChangeListener { _, checkedIds ->
            val method = when {
                checkedIds.contains(R.id.chipCash) -> PaymentMethod.CASH
                checkedIds.contains(R.id.chipCard) -> PaymentMethod.CARD
                checkedIds.contains(R.id.chipTransfer) -> PaymentMethod.TRANSFER
                else -> null
            }
            viewModel.setPaymentMethodFilter(method)
        }

        // Clear filters button
        binding.btnClearFilters.setOnClickListener {
            clearAllFilters()
        }
    }

    private fun clearAllFilters() {
        binding.chipGroupDateFilter.check(R.id.chipToday)
        binding.chipGroupStatus.clearCheck()
        binding.chipGroupPayment.clearCheck()
        viewModel.setDateFilter(DateFilter.TODAY)
        viewModel.setStatusFilter(null)
        viewModel.setPaymentMethodFilter(null)
    }

    private fun showDateRangePicker() {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.select_date_range))
            .build()

        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            val startDate = selection.first ?: return@addOnPositiveButtonClickListener
            val endDate = selection.second ?: return@addOnPositiveButtonClickListener
            
            viewModel.setDateFilter(DateFilter.CUSTOM)
            // The ViewModel would need a method to set custom date range
            // For now, we'll handle it through the existing filter
            
            binding.chipCustom.text = "${dateFormat.format(Date(startDate))} - ${dateFormat.format(Date(endDate))}"
        }

        dateRangePicker.show(parentFragmentManager, "date_range_picker")
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            onRefresh()
        }
        binding.swipeRefresh.setColorSchemeResources(
            R.color.primary,
            R.color.secondary
        )
    }

    private fun setupNewSaleButton() {
        binding.fabNewSale.setOnClickListener {
            // Navigate to cart/new sale
            findNavController().navigate(R.id.cartFragment)
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

    private fun updateUI(state: SalesHistoryUiState) {
        binding.swipeRefresh.isRefreshing = state.isLoading
        
        val sales = state.filteredSales
        
        // Update adapter
        salesAdapter.submitList(sales)
        
        // Show empty state
        binding.emptyState.isVisible = sales.isEmpty() && !state.isLoading
        binding.recyclerViewSales.isVisible = sales.isNotEmpty()

        // Update statistics card
        val stats = viewModel.getStatistics()
        binding.apply {
            tvTotalSales.text = stats.totalSales.toGuaraniFormat()
            tvTotalTransactions.text = stats.totalTransactions.toString()
            tvAverageTicket.text = stats.averageTicket.toGuaraniFormat()
        }

        // Show active filters indicator
        val hasActiveFilters = state.statusFilter != null || 
                               state.paymentMethodFilter != null || 
                               state.dateFilter != DateFilter.TODAY
        binding.btnClearFilters.isVisible = hasActiveFilters
    }

    private fun handleEvent(event: SalesHistoryEvent) {
        when (event) {
            is SalesHistoryEvent.SaleCancelled -> {
                Snackbar.make(binding.root, getString(R.string.sale_cancelled), Snackbar.LENGTH_SHORT).show()
            }
            is SalesHistoryEvent.Error -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showCancelDialog(sale: SaleEntity) {
        if (sale.status != SaleStatus.COMPLETED.name) {
            Snackbar.make(binding.root, getString(R.string.cannot_cancel_sale), Snackbar.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.cancel_sale))
            .setMessage(getString(R.string.cancel_sale_confirmation))
            .setPositiveButton(getString(R.string.cancel)) { _, _ ->
                viewModel.cancelSale(sale.id, "Cancelado por usuario")
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun navigateToSaleDetail(saleId: String) {
        try {
            val action = SalesFragmentDirections.actionSalesFragmentToSaleDetailFragment(saleId.toLong())
            findNavController().navigate(action)
        } catch (e: Exception) {
            // Fallback navigation
            (requireActivity() as? MainActivity)?.navigateToSaleDetail(saleId.toLong())
        }
    }

    override fun onRefresh() {
        binding.swipeRefresh.isRefreshing = true
        // The ViewModel will automatically refresh through Flow collection
        binding.swipeRefresh.postDelayed({
            binding.swipeRefresh.isRefreshing = false
        }, 1000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
