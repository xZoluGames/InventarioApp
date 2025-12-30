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
        setupBackPressHandler()
        observeState()
        
        // Start initial sync
        viewModel.syncData()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Setup bottom navigation with nav controller
        binding.bottomNavigation.setupWithNavController(navController)

        // Listen for destination changes to show/hide bottom nav
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.visibility = 
                if (destination.id in fragmentsWithoutBottomNav) View.GONE else View.VISIBLE
        }

        // Handle bottom nav item reselection (scroll to top or refresh)
        binding.bottomNavigation.setOnItemReselectedListener { item ->
            // Find the current fragment and trigger refresh/scroll to top
            val currentFragment = navHostFragment.childFragmentManager.primaryNavigationFragment
            if (currentFragment is RefreshableFragment) {
                currentFragment.onRefresh()
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentDestination = navController.currentDestination?.id
                
                // If on main screens, ask to exit
                if (currentDestination == R.id.homeFragment) {
                    showExitConfirmation()
                } else {
                    // Navigate back normally
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun showExitConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
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

    fun navigateToProductDetail(productId: Long) {
        val bundle = Bundle().apply {
            putLong("productId", productId)
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

    fun navigateToSaleDetail(saleId: Long) {
        val bundle = Bundle().apply {
            putLong("saleId", saleId)
        }
        navController.navigate(R.id.saleDetailFragment, bundle)
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
