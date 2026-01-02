package com.inventario.py.ui.main

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inventario.py.R
import com.inventario.py.data.local.entity.SyncState
import com.inventario.py.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val viewModel: MainViewModel by viewModels()

    // Fragments where bottom navigation should be hidden
    private val fragmentsWithoutBottomNav = setOf(
        R.id.scannerFragment,
        R.id.productDetailFragment,
        R.id.addProductFragment,
        R.id.checkoutFragment,
        R.id.saleDetailFragment,
        R.id.newSaleFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupBackPress()
        observeState()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        // Handle navigation visibility
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.visibility =
                if (destination.id in fragmentsWithoutBottomNav) View.GONE else View.VISIBLE
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!navController.navigateUp()) {
                    showExitConfirmation()
                }
            }
        })
    }

    private fun showExitConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Salir")
            .setMessage("¿Desea salir de la aplicación?")
            .setPositiveButton("Salir") { _, _ ->
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.syncState.collect { state ->
                        when (state) {
                            is SyncState.Syncing -> {
                                // Could show a sync indicator
                            }
                            is SyncState.Success -> {
                                // Sync completed silently
                            }
                            is SyncState.Error -> {
                                showSyncError(state.message)
                            }
                            is SyncState.Idle -> {
                                // Nothing to do
                            }
                        }
                    }
                }

                launch {
                    viewModel.lowStockProducts.collect { count ->
                        updateLowStockBadge(count)
                    }
                }
            }
        }
    }

    private fun updateLowStockBadge(count: Int) {
        val badge = binding.bottomNavigation.getOrCreateBadge(R.id.productsFragment)
        if (count > 0) {
            badge.isVisible = true
            badge.number = count
        } else {
            badge.isVisible = false
        }
    }

    private fun showSyncError(message: String) {
        Snackbar.make(binding.root, "Error de sincronización: $message", Snackbar.LENGTH_LONG)
            .setAction("Reintentar") {
                viewModel.syncData()
            }
            .show()
    }

    // ==================== NAVIGATION HELPERS ====================
    // NOTA: Los IDs de producto y venta son String, no Long

    fun navigateToProductDetail(productId: String) {
        val bundle = Bundle().apply {
            putString("productId", productId)  // Cambiado de putLong a putString
        }
        navController.navigate(R.id.productDetailFragment, bundle)
    }

    fun navigateToScanner(forSale: Boolean = false) {
        val bundle = Bundle().apply {
            putBoolean("forSale", forSale)
        }
        navController.navigate(R.id.scannerFragment, bundle)
    }

    fun navigateToAddProduct(barcode: String? = null) {
        val bundle = Bundle().apply {
            barcode?.let { putString("barcode", it) }
        }
        navController.navigate(R.id.addProductFragment, bundle)
    }

    fun navigateToCheckout() {
        navController.navigate(R.id.checkoutFragment)
    }

    fun navigateToSaleDetail(saleId: String) {
        val bundle = Bundle().apply {
            putString("saleId", saleId)  // Cambiado de putLong a putString
        }
        navController.navigate(R.id.saleDetailFragment, bundle)
    }

    // Sobrecarga para compatibilidad con código que use Long
    fun navigateToSaleDetail(saleId: Long) {
        navigateToSaleDetail(saleId.toString())
    }

    fun getCurrentUserId(): Long = viewModel.getCurrentUserId()

    fun isOwner(): Boolean = viewModel.isOwner()
}

/**
 * Interface for fragments that can be refreshed
 */
interface RefreshableFragment {
    fun onRefresh()
}