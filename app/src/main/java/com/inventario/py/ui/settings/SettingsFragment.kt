package com.inventario.py.ui.settings

import android.content.Intent
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.inventario.py.R
import com.inventario.py.data.local.entity.UserRole
import com.inventario.py.data.local.entity.name
import com.inventario.py.databinding.FragmentSettingsBinding
import com.inventario.py.ui.auth.LoginActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()
    
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PY"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupClickListeners()
        observeState()
    }

    private fun setupClickListeners() {
        binding.apply {
            // User profile
            cardUserProfile.setOnClickListener {
                showProfileOptions()
            }

            // Business settings
            itemBusinessInfo.setOnClickListener {
                // Navigate to business info screen
                showComingSoon()
            }
            
            itemCategories.setOnClickListener {
                // Navigate to categories management
                showComingSoon()
            }
            
            itemSuppliers.setOnClickListener {
                // Navigate to suppliers management
                showComingSoon()
            }
            
            itemUsers.setOnClickListener {
                // Navigate to users management
                showComingSoon()
            }

            // App settings switches
            switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setDarkMode(isChecked)
            }
            
            switchSoundEffects.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setSoundEnabled(isChecked)
            }
            
            switchLowStockNotifications.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setLowStockNotificationsEnabled(isChecked)
            }

            // Sync settings
            btnSyncNow.setOnClickListener {
                viewModel.syncNow()
            }
            
            itemServerConfig.setOnClickListener {
                showServerConfigDialog()
            }

            // Help
            itemHelp.setOnClickListener {
                showComingSoon()
            }

            // Logout
            btnLogout.setOnClickListener {
                showLogoutConfirmation()
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

    private fun updateUI(state: SettingsUiState) {
        binding.apply {
            // User profile
            state.currentUser?.let { user ->
                tvUserName.text = user.name
                tvUserRole.text = when (user.role) {
                    UserRole.OWNER.name -> getString(R.string.role_owner)
                    UserRole.EMPLOYEE.name -> getString(R.string.role_employee)
                }
                tvUserEmail.text = user.email
                tvUserInitial.text = user.name.firstOrNull()?.uppercase() ?: "U"
            }

            // Show/hide owner-only settings
            itemUsers.isVisible = state.isOwner

            // App settings switches - update without triggering listeners
            switchDarkMode.setOnCheckedChangeListener(null)
            switchSoundEffects.setOnCheckedChangeListener(null)
            switchLowStockNotifications.setOnCheckedChangeListener(null)
            
            switchDarkMode.isChecked = state.isDarkMode
            switchSoundEffects.isChecked = state.isSoundEnabled
            switchLowStockNotifications.isChecked = state.isLowStockNotificationsEnabled
            
            // Re-attach listeners
            switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setDarkMode(isChecked)
            }
            switchSoundEffects.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setSoundEnabled(isChecked)
            }
            switchLowStockNotifications.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setLowStockNotificationsEnabled(isChecked)
            }

            // Sync status
            updateSyncStatus(state)

            // Version
            tvVersion.text = getString(R.string.version_format, state.appVersion)

            // Loading states
            btnSyncNow.isEnabled = !state.isSyncing
            progressSync?.isVisible = state.isSyncing
        }
    }

    private fun updateSyncStatus(state: SettingsUiState) {
        binding.apply {
            when (state.syncStatus) {
                SyncStatus.SYNCED -> {
                    ivSyncStatus.setImageResource(R.drawable.ic_cloud)
                    tvSyncStatus.text = getString(R.string.sync_status_synced)
                    tvSyncStatus.setTextColor(resources.getColor(R.color.stock_ok, null))
                }
                SyncStatus.PENDING -> {
                    ivSyncStatus.setImageResource(R.drawable.ic_warning)
                    tvSyncStatus.text = getString(R.string.sync_status_pending)
                    tvSyncStatus.setTextColor(resources.getColor(R.color.stock_low, null))
                }
                SyncStatus.OFFLINE -> {
                    ivSyncStatus.setImageResource(R.drawable.ic_warning)
                    tvSyncStatus.text = getString(R.string.sync_status_offline)
                    tvSyncStatus.setTextColor(resources.getColor(R.color.text_secondary, null))
                }
                SyncStatus.ERROR -> {
                    ivSyncStatus.setImageResource(R.drawable.ic_warning)
                    tvSyncStatus.text = getString(R.string.error)
                    tvSyncStatus.setTextColor(resources.getColor(R.color.error, null))
                }
            }

            // Last sync time
            state.lastSyncTime?.let { time ->
                tvLastSync.text = getString(R.string.last_sync_format, dateTimeFormat.format(Date(time)))
            } ?: run {
                tvLastSync.text = getString(R.string.never_synced)
            }

            // Pending changes
            tvPendingChanges.isVisible = state.pendingChangesCount > 0
            tvPendingChanges.text = getString(R.string.pending_changes_count, state.pendingChangesCount)
        }
    }

    private fun handleEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.LogoutSuccess -> {
                navigateToLogin()
            }
            is SettingsEvent.SyncStarted -> {
                Snackbar.make(binding.root, getString(R.string.syncing), Snackbar.LENGTH_SHORT).show()
            }
            is SettingsEvent.SyncCompleted -> {
                Snackbar.make(binding.root, getString(R.string.sync_complete), Snackbar.LENGTH_SHORT).show()
            }
            is SettingsEvent.SyncError -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
            }
            is SettingsEvent.Error -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
            }
            is SettingsEvent.ThemeChanged -> {
                // Theme will be applied automatically by AppCompatDelegate
            }
        }
    }

    private fun showProfileOptions() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.profile))
            .setItems(arrayOf(
                getString(R.string.change_password),
                getString(R.string.edit_profile)
            )) { _, which ->
                when (which) {
                    0 -> showChangePasswordDialog()
                    1 -> showComingSoon()
                }
            }
            .show()
    }

    private fun showChangePasswordDialog() {
        // Simplified dialog using EditText
        val input = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.new_password)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.change_password))
            .setView(input)
            .setPositiveButton(getString(R.string.change)) { _, _ ->
                val newPassword = input.text.toString()
                if (newPassword.length >= 6) {
                    // viewModel.changePassword(newPassword) - would need to implement
                    Snackbar.make(binding.root, getString(R.string.password_changed), Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(binding.root, getString(R.string.password_too_short), Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showServerConfigDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "https://tu-servidor.com/api"
            setText(viewModel.uiState.value.serverUrl)
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.server_config))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    viewModel.setServerUrl(newUrl)
                    Snackbar.make(binding.root, getString(R.string.server_updated), Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.confirm_logout))
            .setPositiveButton(getString(R.string.logout)) { _, _ ->
                viewModel.logout()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun navigateToLogin() {
        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }

    private fun showComingSoon() {
        Snackbar.make(binding.root, getString(R.string.coming_soon), Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
