package com.inventario.py.ui.sales

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inventario.py.R
import com.inventario.py.data.local.entity.PaymentMethod
import com.inventario.py.data.local.entity.SaleEntity
import com.inventario.py.data.local.entity.SaleStatus
import com.inventario.py.databinding.FragmentSaleDetailBinding
import com.inventario.py.ui.adapters.SaleDetailAdapter
import com.inventario.py.ui.main.MainActivity
import com.inventario.py.utils.CurrencyUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class SaleDetailFragment : Fragment() {

    private var _binding: FragmentSaleDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SaleDetailViewModel by viewModels()
    private val args: SaleDetailFragmentArgs by navArgs()

    private lateinit var itemsAdapter: SaleDetailAdapter
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PY"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSaleDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupMenu()
        setupItemsAdapter()
        setupClickListeners()
        observeState()

        // Cargar venta
        viewModel.loadSale(args.saleId)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_sale_detail, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_print -> {
                        viewModel.printReceipt()
                        true
                    }
                    R.id.action_share -> {
                        shareSaleDetails()
                        true
                    }
                    R.id.action_cancel -> {
                        showCancelConfirmation()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupItemsAdapter() {
        itemsAdapter = SaleDetailAdapter()
        binding.rvItems.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = itemsAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        with(binding) {
            // Duplicar venta (crear nueva con los mismos items)
            btnDuplicateSale.setOnClickListener {
                viewModel.duplicateSale()
            }

            // Ver cliente
            cardCustomer.setOnClickListener {
                // TODO: Navegar a detalle de cliente si existe
            }
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

    private fun updateUI(state: SaleDetailUiState) {
        with(binding) {
            // Loading
            progressBar.isVisible = state.isLoading
            contentLayout.isVisible = !state.isLoading && state.sale != null

            val sale = state.sale ?: return

            // Información de la venta
            toolbar.title = getString(R.string.sale_number_format, sale.saleNumber)

            // Estado de la venta
            val statusColor = when (sale.status) {
                SaleEntity.STATUS_COMPLETED -> R.color.success
                SaleEntity.STATUS_CANCELLED -> R.color.error
                SaleEntity.STATUS_REFUNDED -> R.color.warning
                else -> R.color.text_secondary
            }
            chipStatus.text = getStatusText(sale.status)
            chipStatus.setChipBackgroundColorResource(statusColor)

            // Fecha y hora
            tvDateTime.text = dateFormat.format(Date(sale.soldAt))

            // Vendedor
            tvSeller.text = sale.soldByName

            // Cliente
            if (!sale.customerName.isNullOrEmpty()) {
                cardCustomer.isVisible = true
                tvCustomerName.text = sale.customerName
            } else {
                cardCustomer.isVisible = false
            }

            // Método de pago
            tvPaymentMethod.text = getPaymentMethodText(sale.paymentMethod)
            ivPaymentIcon.setImageResource(getPaymentMethodIcon(sale.paymentMethod))

            // Items
            itemsAdapter.submitList(state.items)
            tvItemsCount.text = getString(R.string.items_count, state.items.size)

            // Totales
            tvSubtotal.text = CurrencyUtils.formatGuarani(sale.subtotal)

            // Descuento
            if (sale.totalDiscount > 0) {
                layoutDiscount.isVisible = true
                tvDiscount.text = "-${CurrencyUtils.formatGuarani(sale.totalDiscount)}"
            } else {
                layoutDiscount.isVisible = false
            }

            // Total
            tvTotal.text = CurrencyUtils.formatGuarani(sale.total)

            // Pago y vuelto (solo para efectivo)
            if (sale.paymentMethod == SaleEntity.PAYMENT_CASH) {
                layoutPaymentDetails.isVisible = true
                tvAmountPaid.text = CurrencyUtils.formatGuarani(sale.amountPaid)
                tvChange.text = CurrencyUtils.formatGuarani(sale.changeAmount)
            } else {
                layoutPaymentDetails.isVisible = false
            }

            // Notas
            if (!sale.notes.isNullOrEmpty()) {
                cardNotes.isVisible = true
                tvNotes.text = sale.notes
            } else {
                cardNotes.isVisible = false
            }

            // Mostrar info de cancelación si aplica
            if (sale.status == SaleEntity.STATUS_CANCELLED) {
                cardCancellation.isVisible = true
                tvCancellationReason.text = sale.cancellationReason ?: getString(R.string.no_reason_provided)
                tvCancelledAt.text = sale.cancelledAt?.let { dateFormat.format(Date(it)) } ?: ""
            } else {
                cardCancellation.isVisible = false
            }

            // Ocultar botón de duplicar si está cancelada
            btnDuplicateSale.isVisible = sale.status == SaleEntity.STATUS_COMPLETED
        }
    }

    private fun getStatusText(status: String): String {
        return when (status) {
            SaleEntity.STATUS_COMPLETED -> getString(R.string.status_completed)
            SaleEntity.STATUS_CANCELLED -> getString(R.string.status_cancelled)
            SaleEntity.STATUS_REFUNDED -> getString(R.string.status_refunded)
            SaleEntity.STATUS_PENDING -> getString(R.string.status_pending)
            else -> status
        }
    }

    private fun getPaymentMethodText(method: String): String {
        return when (method) {
            SaleEntity.PAYMENT_CASH -> getString(R.string.payment_cash)
            SaleEntity.PAYMENT_CARD -> getString(R.string.payment_card)
            SaleEntity.PAYMENT_TRANSFER -> getString(R.string.payment_transfer)
            SaleEntity.PAYMENT_CREDIT -> getString(R.string.payment_credit)
            SaleEntity.PAYMENT_MIXED -> getString(R.string.payment_mixed)
            else -> method
        }
    }

    private fun getPaymentMethodIcon(method: String): Int {
        return when (method) {
            SaleEntity.PAYMENT_CASH -> R.drawable.ic_cash
            SaleEntity.PAYMENT_CARD -> R.drawable.ic_credit_card
            SaleEntity.PAYMENT_TRANSFER -> R.drawable.ic_transfer
            SaleEntity.PAYMENT_CREDIT -> R.drawable.ic_credit
            else -> R.drawable.ic_payment
        }
    }

    private fun handleEvent(event: SaleDetailEvent) {
        when (event) {
            is SaleDetailEvent.SaleCancelled -> {
                Snackbar.make(binding.root, R.string.sale_cancelled_success, Snackbar.LENGTH_SHORT).show()
            }
            is SaleDetailEvent.SaleDuplicated -> {
                Snackbar.make(binding.root, R.string.sale_duplicated, Snackbar.LENGTH_SHORT)
                    .setAction(R.string.go_to_cart) {
                        findNavController().navigate(R.id.cartFragment)
                    }
                    .show()
            }
            is SaleDetailEvent.PrintReceipt -> {
                // TODO: Implementar impresión
                Snackbar.make(binding.root, R.string.printing, Snackbar.LENGTH_SHORT).show()
            }
            is SaleDetailEvent.Error -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun showCancelConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cancel_sale)
            .setMessage(R.string.cancel_sale_confirmation)
            .setPositiveButton(R.string.yes_cancel) { _, _ ->
                showCancelReasonDialog()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun showCancelReasonDialog() {
        val reasons = arrayOf(
            getString(R.string.cancel_reason_customer_request),
            getString(R.string.cancel_reason_wrong_product),
            getString(R.string.cancel_reason_payment_issue),
            getString(R.string.cancel_reason_other)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_cancel_reason)
            .setItems(reasons) { _, which ->
                viewModel.cancelSale(reasons[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun shareSaleDetails() {
        val state = viewModel.uiState.value
        val sale = state.sale ?: return

        val shareText = buildString {
            appendLine("🧾 ${getString(R.string.sale_receipt)}")
            appendLine("━━━━━━━━━━━━━━━━━")
            appendLine("N°: ${sale.saleNumber}")
            appendLine("${getString(R.string.date)}: ${dateFormat.format(Date(sale.soldAt))}")
            appendLine()
            appendLine("${getString(R.string.items)}:")
            state.items.forEach { item ->
                appendLine("• ${item.productName} x${item.quantity}")
                appendLine("  ${CurrencyUtils.formatGuarani(item.subtotal)}")
            }
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━")
            appendLine("${getString(R.string.total)}: ${CurrencyUtils.formatGuarani(sale.total)}")
            appendLine()
            appendLine("${getString(R.string.payment_method)}: ${getPaymentMethodText(sale.paymentMethod)}")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
