package com.inventario.py.ui.checkout

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inventario.py.R
import com.inventario.py.data.local.entity.PaymentMethod
import com.inventario.py.databinding.FragmentCheckoutBinding
import com.inventario.py.ui.adapters.OrderSummaryAdapter
import com.inventario.py.utils.CurrencyUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CheckoutFragment : Fragment() {

    private var _binding: FragmentCheckoutBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CheckoutViewModel by viewModels()
    private lateinit var orderSummaryAdapter: OrderSummaryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCheckoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupOrderSummary()
        setupPaymentMethods()
        setupQuickAmountButtons()
        setupClickListeners()
        observeState()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupOrderSummary() {
        orderSummaryAdapter = OrderSummaryAdapter()
        binding.rvOrderItems.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = orderSummaryAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupPaymentMethods() {
        binding.rgPaymentMethod.setOnCheckedChangeListener { _, checkedId ->
            val paymentMethod = when (checkedId) {
                R.id.rbCash -> PaymentMethod.CASH
                R.id.rbCard -> PaymentMethod.CARD
                R.id.rbTransfer -> PaymentMethod.TRANSFER
                R.id.rbCredit -> PaymentMethod.CREDIT
                else -> PaymentMethod.CASH
            }
            viewModel.setPaymentMethod(paymentMethod)

            // Mostrar/ocultar secciones según método de pago
            binding.cardCashPayment.isVisible = paymentMethod == PaymentMethod.CASH
            binding.cardCreditCustomer.isVisible = paymentMethod == PaymentMethod.CREDIT
        }
    }

    private fun setupQuickAmountButtons() {
        with(binding) {
            // Botón "Exacto" - poner el total exacto
            btnExact.setOnClickListener {
                val total = viewModel.uiState.value.total
                etAmountReceived.setText(total.toString())
                viewModel.setAmountReceived(total)
            }

            // Botón 50.000
            btn50k.setOnClickListener {
                etAmountReceived.setText("50000")
                viewModel.setAmountReceived(50000L)
            }

            // Botón 100.000
            btn100k.setOnClickListener {
                etAmountReceived.setText("100000")
                viewModel.setAmountReceived(100000L)
            }
        }
    }

    private fun setupClickListeners() {
        with(binding) {
            // Editar carrito
            btnEditCart.setOnClickListener {
                findNavController().navigateUp()
            }

            // Confirmar venta
            btnConfirmSale.setOnClickListener {
                confirmSale()
            }

            // Calcular cambio cuando se ingresa monto
            etAmountReceived.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    calculateChange()
                }
            })
        }
    }

    private fun calculateChange() {
        val amountReceived = binding.etAmountReceived.text.toString()
            .replace(".", "")
            .replace(",", "")
            .toLongOrNull() ?: 0L

        viewModel.setAmountReceived(amountReceived)
    }

    private fun confirmSale() {
        // Validar datos del cliente si es necesario
        val customerName = binding.etCustomerName.text.toString().trim()
        val notes = binding.etNotes.text.toString().trim()

        // Validar monto recibido para efectivo
        val state = viewModel.uiState.value
        if (state.paymentMethod == PaymentMethod.CASH) {
            val amountReceived = binding.etAmountReceived.text.toString()
                .replace(".", "")
                .replace(",", "")
                .toLongOrNull() ?: 0L

            if (amountReceived < state.total) {
                binding.tilAmountReceived.error = getString(R.string.insufficient_amount)
                return
            }
            binding.tilAmountReceived.error = null
        }

        // Validar cliente para crédito
        if (state.paymentMethod == PaymentMethod.CREDIT && customerName.isBlank()) {
            binding.tilCustomerName.error = getString(R.string.customer_name_required)
            return
        }
        binding.tilCustomerName.error = null

        viewModel.completeSale(customerName, notes)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observar estado de UI
                launch {
                    viewModel.uiState.collectLatest { state ->
                        updateUI(state)
                    }
                }

                // Observar eventos
                launch {
                    viewModel.events.collectLatest { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun updateUI(state: CheckoutUiState) {
        with(binding) {
            // Items del pedido
            orderSummaryAdapter.submitList(state.items)

            // Totales
            tvSubtotal.text = CurrencyUtils.formatGuarani(state.subtotal)

            // Descuento
            if (state.discount > 0) {
                layoutDiscount.isVisible = true
                tvDiscount.text = "-${CurrencyUtils.formatGuarani(state.discount)}"
            } else {
                layoutDiscount.isVisible = false
            }

            // Total
            tvTotal.text = CurrencyUtils.formatGuarani(state.total)

            // Cambio (para efectivo)
            if (state.paymentMethod == PaymentMethod.CASH && state.amountReceived > 0) {
                layoutChange.isVisible = true
                tvChange.text = CurrencyUtils.formatGuarani(state.changeAmount)
            } else {
                layoutChange.isVisible = false
            }

            // Estado de carga
            loadingOverlay.isVisible = state.isProcessing
            btnConfirmSale.isEnabled = !state.isProcessing && state.items.isNotEmpty()
        }
    }

    private fun handleEvent(event: CheckoutEvent) {
        when (event) {
            is CheckoutEvent.SaleCompleted -> {
                showSuccessDialog(event.saleNumber, event.total)
            }
            is CheckoutEvent.Error -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
            }
            is CheckoutEvent.PrintReceipt -> {
                // TODO: Implementar impresión
                Snackbar.make(binding.root, "Impresión disponible próximamente", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSuccessDialog(saleNumber: String, total: Long) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.sale_completed))
            .setMessage("Venta $saleNumber completada\nTotal: ${CurrencyUtils.formatGuarani(total)}")
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                // Navegar al home
                findNavController().popBackStack(R.id.homeFragment, false)
            }
            .setNeutralButton(getString(R.string.print_receipt)) { _, _ ->
                viewModel.printReceipt()
                findNavController().popBackStack(R.id.homeFragment, false)
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
