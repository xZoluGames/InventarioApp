package com.inventario.py.ui.cart

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inventario.py.R
import com.inventario.py.databinding.FragmentCartBinding
import com.inventario.py.ui.adapters.CartAdapter
import com.inventario.py.ui.main.MainActivity
import com.inventario.py.ui.main.RefreshableFragment
import com.inventario.py.utils.CurrencyUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController

@AndroidEntryPoint
class CartFragment : Fragment(), RefreshableFragment {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CartViewModel by viewModels()
    private lateinit var cartAdapter: CartAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupAdapter()
        setupSwipeToDelete()
        setupClickListeners()
        observeState()
    }

    private fun setupAdapter() {
        cartAdapter = CartAdapter(
            onQuantityChanged = { cartItem, newQuantity ->
                viewModel.updateQuantity(cartItem, newQuantity)
            },
            onDeleteClick = { cartItem ->
                viewModel.removeFromCart(cartItem)
            },
            onItemClick = { cartItem ->
                (activity as? MainActivity)?.navigateToProductDetail(cartItem.productId)
            }
        )
        
        binding.rvCart.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = cartAdapter
        }
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val item = cartAdapter.currentList[position]
                viewModel.removeFromCart(item)
                
                Snackbar.make(binding.root, "Producto eliminado", Snackbar.LENGTH_LONG)
                    .setAction("Deshacer") {
                        viewModel.restoreCartItem(item)
                    }
                    .show()
            }
        }
        
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.rvCart)
    }

    private fun setupClickListeners() {
        with(binding) {
            // Scan product button
            btnScanProduct.setOnClickListener {
                (activity as? MainActivity)?.navigateToScanner(forSale = true)
            }
            
            // Add product button
            btnAddProduct.setOnClickListener {
                findNavController().navigate(R.id.productsFragment)
            }
            
            // Clear cart
            btnClearCart.setOnClickListener {
                showClearCartConfirmation()
            }
            
            // Proceed to checkout
            btnCheckout.setOnClickListener {
                if (viewModel.cartItems.value.isNotEmpty()) {
                    findNavController().navigate(R.id.checkoutFragment)
                } else {
                    Snackbar.make(binding.root, "El carrito está vacío", Snackbar.LENGTH_SHORT).show()
                }
            }
            
            // Empty state actions
            btnEmptyScan.setOnClickListener {
                (activity as? MainActivity)?.navigateToScanner(forSale = true)
            }
            
            btnEmptyBrowse.setOnClickListener {
                findNavController().navigate(R.id.productsFragment)
            }
        }
    }
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }
    private fun showClearCartConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Vaciar Carrito")
            .setMessage("¿Está seguro de eliminar todos los productos del carrito?")
            .setPositiveButton("Vaciar") { _, _ ->
                viewModel.clearCart()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.cartItems.collect { items ->
                        cartAdapter.submitList(items)
                        
                        // Toggle empty state
                        if (items.isEmpty()) {
                            binding.rvCart.visibility = View.GONE
                            binding.emptyState.visibility = View.VISIBLE
                            binding.bottomBar.visibility = View.GONE
                            binding.btnClearCart.visibility = View.GONE
                        } else {
                            binding.rvCart.visibility = View.VISIBLE
                            binding.emptyState.visibility = View.GONE
                            binding.bottomBar.visibility = View.VISIBLE
                            binding.btnClearCart.visibility = View.VISIBLE
                        }
                    }
                }

                launch {
                    viewModel.cartTotal.collect { total ->
                        binding.tvSubtotal.text = CurrencyUtils.formatGuarani(total.subtotal)
                        binding.tvDiscount.text = "-${CurrencyUtils.formatGuarani(total.discount)}"
                        binding.tvTotal.text = CurrencyUtils.formatGuarani(total.total)
                        binding.tvItemCount.text = "${total.itemCount} productos"
                        
                        // Show/hide discount row
                        binding.layoutDiscount.visibility = 
                            if (total.discount > 0) View.VISIBLE else View.GONE
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
            }
        }
    }

    override fun onRefresh() {
        binding.rvCart.smoothScrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
