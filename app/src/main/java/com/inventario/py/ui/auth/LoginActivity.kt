package com.inventario.py.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.inventario.py.data.local.entity.LoginState
import com.inventario.py.databinding.ActivityLoginBinding
import com.inventario.py.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if already logged in
        if (viewModel.isLoggedIn()) {
            navigateToMain()
            return
        }
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
        observeState()
    }

    private fun setupViews() {
        with(binding) {
            // Clear errors on text change
            etEmail.doAfterTextChanged {
                tilEmail.error = null
            }
            
            etPassword.doAfterTextChanged {
                tilPassword.error = null
            }
            
            // Handle keyboard done action
            etPassword.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    attemptLogin()
                    true
                } else {
                    false
                }
            }
            
            // Login button click
            btnLogin.setOnClickListener {
                attemptLogin()
            }
            
            // Forgot password
            tvForgotPassword.setOnClickListener {
                showForgotPasswordDialog()
            }
            
            // Server config
            tvServerConfig.setOnClickListener {
                showServerConfigDialog()
            }
        }
    }

    private fun attemptLogin() {
        val input = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        // Validate inputs
        var isValid = true

        if (input.isEmpty()) {
            binding.tilEmail.error = "Ingrese su usuario o correo"
            isValid = false
        }
        // Acepta tanto usuario simple como email
        // No validamos formato, el servidor decidirá si es válido

        if (password.isEmpty()) {
            binding.tilPassword.error = "Ingrese su contraseña"
            isValid = false
        } else if (password.length < 4) {
            binding.tilPassword.error = "La contraseña debe tener al menos 4 caracteres"
            isValid = false
        }

        if (isValid) {
            // Detectar si es email o usuario
            val isEmail = input.contains("@")

            if (isEmail) {
                // TODO: Login con email
                // loginWithEmail(input, password)
                viewModel.login(input, password)
            } else {
                // Login con username
                viewModel.login(input, password)
            }
        }
    }

// ==================== FUNCIONES PARA LOGIN CON EMAIL (FUTURO) ====================

    /*
    private fun loginWithEmail(email: String, password: String) {
        // Validar formato de email
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Correo electrónico inválido"
            return
        }

        // Llamar al ViewModel con método específico para email
        // viewModel.loginWithEmail(email, password)
        viewModel.login(email, password)
    }

    private fun validateEmailFormat(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun getLoginType(input: String): LoginType {
        return if (input.contains("@")) LoginType.EMAIL else LoginType.USERNAME
    }

    enum class LoginType {
        USERNAME,
        EMAIL
    }
    */

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loginState.collect { state ->
                    when (state) {
                        is LoginState.Idle -> {
                            setLoading(false)
                        }
                        is LoginState.Loading -> {
                            setLoading(true)
                        }
                        is LoginState.Success -> {
                            setLoading(false)
                            navigateToMain()
                        }
                        is LoginState.Error -> {
                            setLoading(false)
                            showError(state.message)
                        }
                    }
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        with(binding) {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnLogin.isEnabled = !isLoading
            etEmail.isEnabled = !isLoading
            etPassword.isEnabled = !isLoading
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(getColor(com.inventario.py.R.color.error))
            .show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showForgotPasswordDialog() {
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Recuperar Contraseña")
            .setMessage("Contacte al administrador del sistema para recuperar su contraseña.")
            .setPositiveButton("Entendido", null)
            .create()
        dialog.show()
    }

    private fun showServerConfigDialog() {
        val dialogView = layoutInflater.inflate(
            com.inventario.py.R.layout.dialog_server_config,
            null
        )
        
        val etServerUrl = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
            com.inventario.py.R.id.etServerUrl
        )
        
        etServerUrl.setText(viewModel.getServerUrl())
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Configuración del Servidor")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val newUrl = etServerUrl.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    viewModel.setServerUrl(newUrl)
                    Snackbar.make(binding.root, "Servidor actualizado", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
