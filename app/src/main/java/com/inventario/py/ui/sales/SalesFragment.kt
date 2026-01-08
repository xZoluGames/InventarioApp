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
import androidx.navigation.fragment.findNavController
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
import com.inventario.py.utils.CurrencyUtils.toGuaraniFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class SalesFragment : Fragment(), RefreshableFragment {

    private var _binding: FragmentSalesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SalesHistoryViewModel by viewModels()
    private lateinit var salesAdapter: SaleAdapter

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es", "PY"))
    private var customStartDate: Date? = null
    private var customEndDate: Date? = null

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
        setupDateButtons()
        setupSwipeRefresh()
        setupNewSaleButtons()
        observeState()
    }

    private fun setupRecyclerView() {
        salesAdapter = SaleAdapter(
            onItemClick = { saleWithDetails ->
                navigateToSaleDetail(saleWithDetails.sale.id)
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
                    R.id.chipYesterday -> DateFilter.YESTERDAY
                    R.id.chipThisWeek -> DateFilter.THIS_WEEK
                    R.id.chipThisMonth -> DateFilter.THIS_MONTH
                    R.id.chipYear -> DateFilter.THIS_YEAR
                    R.id.chipAll -> DateFilter.ALL
                    R.id.chipCustom -> {
                        showDateRangePicker()
                        return@setOnCheckedStateChangeListener
                    }
                    else -> DateFilter.TODAY
                }
                viewModel.setDateFilter(filter)
                updateDateButtonsVisibility(filter)
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
                checkedIds.contains(R.id.chipCredit) -> PaymentMethod.CREDIT
                else -> null
            }
            viewModel.setPaymentMethodFilter(method)
        }

        // Clear filters button
        binding.btnClearFilters.setOnClickListener {
            clearAllFilters()
        }
    }

    private fun setupDateButtons() {
        binding.btnStartDate.setOnClickListener {
            showSingleDatePicker(true)
        }

        binding.btnEndDate.setOnClickListener {
            showSingleDatePicker(false)
        }

        // Initialize date buttons with today's date
        updateDateButtons()
    }

    private fun updateDateButtons() {
        val today = Date()
        if (customStartDate == null) customStartDate = today
        if (customEndDate == null) customEndDate = today

        binding.btnStartDate.text = dateFormat.format(customStartDate!!)
        binding.btnEndDate.text = dateFormat.format(customEndDate!!)
    }

    private fun updateDateButtonsVisibility(filter: DateFilter) {
        val isCustom = filter == DateFilter.CUSTOM
        binding.btnStartDate.isEnabled = isCustom
        binding.btnEndDate.isEnabled = isCustom
    }

    private fun showSingleDatePicker(isStartDate: Boolean) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(if (isStartDate) R.string.select_start_date else R.string.select_end_date))
            .setSelection(
                if (isStartDate) customStartDate?.time ?: Date().time
                else customEndDate?.time ?: Date().time
            )
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val selectedDate = Date(selection)
            if (isStartDate) {
                customStartDate = selectedDate
                binding.btnStartDate.text = dateFormat.format(selectedDate)
            } else {
                customEndDate = selectedDate
                binding.btnEndDate.text = dateFormat.format(selectedDate)
            }

            // Apply custom filter if both dates are set
            if (customStartDate != null && customEndDate != null) {
                binding.chipGroupDateFilter.check(R.id.chipCustom)
                viewModel.setDateFilter(DateFilter.CUSTOM)
                // Optionally, pass the dates to ViewModel
                // viewModel.setCustomDateRange(customStartDate!!, customEndDate!!)
            }
        }

        datePicker.show(parentFragmentManager, "single_date_picker")
    }

    private fun showDateRangePicker() {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.select_date_range))
            .setSelection(
                androidx.core.util.Pair(
                    customStartDate?.time ?: Date().time,
                    customEndDate?.time ?: Date().time
                )
            )
            .build()

        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            val startDate = selection.first ?: return@addOnPositiveButtonClickListener
            val endDate = selection.second ?: return@addOnPositiveButtonClickListener

            customStartDate = Date(startDate)
            customEndDate = Date(endDate)

            viewModel.setDateFilter(DateFilter.CUSTOM)

            // Update date buttons
            binding.btnStartDate.text = dateFormat.format(customStartDate!!)
            binding.btnEndDate.text = dateFormat.format(customEndDate!!)

            // Update custom chip text
            binding.chipCustom.text = "${dateFormat.format(customStartDate!!)} - ${dateFormat.format(customEndDate!!)}"
        }

        dateRangePicker.show(parentFragmentManager, "date_range_picker")
    }

    private fun clearAllFilters() {
        binding.chipGroupDateFilter.check(R.id.chipToday)
        binding.chipGroupStatus.check(R.id.chipAllStatus)
        binding.chipGroupPayment.check(R.id.chipAllPayment)

        viewModel.setDateFilter(DateFilter.TODAY)
        viewModel.setStatusFilter(null)
        viewModel.setPaymentMethodFilter(null)

        // Reset custom dates
        val today = Date()
        customStartDate = today
        customEndDate = today
        updateDateButtons()

        // Reset custom chip text
        binding.chipCustom.text = getString(R.string.custom)
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

    private fun setupNewSaleButtons() {
        // FAB button
        binding.fabNewSale.setOnClickListener {
            navigateToNewSale()
        }

        // Empty state button
        binding.btnNewSaleEmpty.setOnClickListener {
            navigateToNewSale()
        }
    }

    private fun navigateToNewSale() {
        try {
            findNavController().navigate(R.id.cartFragment)
        } catch (e: Exception) {
            // Fallback navigation
            (requireActivity() as? MainActivity)?.let {
                // Alternative navigation method if needed
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

    private fun updateUI(state: SalesHistoryUiState) {
        // Update loading state
        binding.swipeRefresh.isRefreshing = state.isLoading
        binding.progressBar.isVisible = state.isLoading && salesAdapter.itemCount == 0

        val sales = state.filteredSales

        // Update adapter
        salesAdapter.submitList(sales.map { saleEntity ->
            com.inventario.py.data.local.entity.SaleWithDetails(
                sale = saleEntity,
                items = emptyList(), // O cargar los items si están disponibles en el state
                seller = null // O el vendedor si está disponible en el state
            )
        })

        // Show/hide empty state
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
                Snackbar.make(
                    binding.root,
                    getString(R.string.sale_cancelled),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            is SalesHistoryEvent.Error -> {
                Snackbar.make(
                    binding.root,
                    event.message,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showCancelDialog(sale: SaleEntity) {
        if (sale.status != SaleStatus.COMPLETED.name) {
            Snackbar.make(
                binding.root,
                getString(R.string.cannot_cancel_sale),
                Snackbar.LENGTH_SHORT
            ).show()
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
        // TEMPORAL: Mostrar información en un diálogo
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Detalle de Venta")
            .setMessage("ID de venta: $saleId\n\nLa pantalla de detalles estará disponible próximamente.")
            .setPositiveButton("Entendido", null)
            .show()

        // ORIGINAL (comentado):
        // try {
        //     val action = SalesFragmentDirections
        //         .actionSalesFragmentToSaleDetailFragment(saleId.toLong().toString())
        //     findNavController().navigate(action)
        // } catch (e: Exception) {
        //     (requireActivity() as? MainActivity)?.navigateToSaleDetail(saleId.toLong())
        // }
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


