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
        R.id.saleDetailFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupBackPress()
        setupFab()
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

            // Mostrar/ocultar FAB de escaneo
            binding.fabScan.visibility = when (destination.id) {
                R.id.homeFragment, R.id.productsFragment, R.id.cartFragment -> View.VISIBLE
                else -> View.GONE
            }
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

    private fun setupFab() {
        binding.fabScan.setOnClickListener {
            val currentDestination = navController.currentDestination?.id
            val forSale = currentDestination == R.id.cartFragment
            navigateToScanner(forSale)
        }
    }

    private fun showExitConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.exit))
            .setMessage(getString(R.string.exit_confirmation))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                finish()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observar estado de sincronización
                launch {
                    viewModel.syncState.collect { state ->
                        handleSyncState(state)
                    }
                }

                // Observar productos con bajo stock
                launch {
                    viewModel.lowStockProducts.collect { count ->
                        // Mostrar badge en el menú de productos si hay bajo stock
                        if (count > 0) {
                            binding.bottomNavigation.getOrCreateBadge(R.id.productsFragment).apply {
                                number = count
                                isVisible = true
                            }
                        } else {
                            binding.bottomNavigation.removeBadge(R.id.productsFragment)
                        }
                    }
                }
            }
        }
    }

    private fun handleSyncState(state: SyncState) {
        when (state) {
            is SyncState.Syncing -> {
                // Mostrar indicador de sincronización
            }
            is SyncState.Success -> {
                Snackbar.make(binding.root, R.string.sync_completed, Snackbar.LENGTH_SHORT).show()
            }
            is SyncState.Error -> {
                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG)
                    .setAction(R.string.retry) {
                        viewModel.syncNow()
                    }
                    .show()
            }
            else -> { /* Idle */ }
        }
    }

    // ==================== FUNCIONES DE NAVEGACIÓN ====================

    fun navigateToProductDetail(productId: String) {
        val bundle = Bundle().apply {
            putString("productId", productId)
        }
        navController.navigate(R.id.productDetailFragment, bundle)
    }

    fun navigateToScanner(forSale: Boolean = false) {
        val bundle = Bundle().apply {
            putBoolean("forSale", forSale)
            putString("scanMode", if (forSale) "sale" else "search")
        }
        navController.navigate(R.id.scannerFragment, bundle)
    }

    fun navigateToAddProduct(barcode: String? = null) {
        val bundle = Bundle().apply {
            barcode?.let { putString("barcode", it) }
        }
        navController.navigate(R.id.addProductFragment, bundle)
    }

    fun navigateToEditProduct(productId: String) {
        val bundle = Bundle().apply {
            putString("productId", productId)
        }
        navController.navigate(R.id.addProductFragment, bundle)
    }

    fun navigateToCart() {
        navController.navigate(R.id.cartFragment)
    }

    fun navigateToCheckout() {
        navController.navigate(R.id.checkoutFragment)
    }

    fun navigateToSaleDetail(saleId: String) {
        val bundle = Bundle().apply {
            putString("saleId", saleId)
        }
        navController.navigate(R.id.saleDetailFragment, bundle)
    }

    // Sobrecarga para compatibilidad con código que use Long
    fun navigateToSaleDetail(saleId: Long) {
        navigateToSaleDetail(saleId.toString())
    }

    fun navigateToNewSale() {
        navController.navigate(R.id.cartFragment)
    }

    // ==================== UTILIDADES ====================

    fun getCurrentUserId(): Long = viewModel.getCurrentUserId()

    fun isOwner(): Boolean = viewModel.isOwner()

    fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(binding.root, message, duration).show()
    }

    fun showSnackbar(messageResId: Int, duration: Int = Snackbar.LENGTH_SHORT) {
        Snackbar.make(binding.root, messageResId, duration).show()
    }
}

/**
 * Interface for fragments that can be refreshed
 */
interface RefreshableFragment {
    fun onRefresh()
}
