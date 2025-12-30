package com.inventario.py.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.inventario.py.ui.screens.*

// ========== Rutas de Navegación ==========

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null
) {
    // Auth
    object Login : Screen("login", "Iniciar Sesión")
    
    // Main screens (Bottom Navigation)
    object Home : Screen(
        "home", 
        "Inicio",
        Icons.Filled.Home,
        Icons.Outlined.Home
    )
    object Products : Screen(
        "products", 
        "Productos",
        Icons.Filled.Inventory2,
        Icons.Outlined.Inventory2
    )
    object Cart : Screen(
        "cart", 
        "Carrito",
        Icons.Filled.ShoppingCart,
        Icons.Outlined.ShoppingCart
    )
    object Sales : Screen(
        "sales", 
        "Ventas",
        Icons.Filled.Receipt,
        Icons.Outlined.Receipt
    )
    object Settings : Screen(
        "settings", 
        "Ajustes",
        Icons.Filled.Settings,
        Icons.Outlined.Settings
    )
    
    // Detail screens
    object ProductDetail : Screen("product/{productId}", "Producto") {
        fun createRoute(productId: Long) = "product/$productId"
    }
    object AddProduct : Screen("product/add", "Agregar Producto")
    object EditProduct : Screen("product/edit/{productId}", "Editar Producto") {
        fun createRoute(productId: Long) = "product/edit/$productId"
    }
    object SaleDetail : Screen("sale/{saleId}", "Venta") {
        fun createRoute(saleId: Long) = "sale/$saleId"
    }
    
    // Scanner
    object BarcodeScanner : Screen("scanner", "Escanear")
    object BarcodeScannerForAdd : Screen("scanner/add", "Escanear para Agregar")
    
    // Reports
    object Reports : Screen("reports", "Reportes")
    object DailyReport : Screen("reports/daily", "Reporte Diario")
    object MonthlyReport : Screen("reports/monthly", "Reporte Mensual")
    object AnnualReport : Screen("reports/annual", "Reporte Anual")
    
    // Users (Owner only)
    object Users : Screen("users", "Usuarios")
    object AddUser : Screen("user/add", "Agregar Usuario")
    object EditUser : Screen("user/edit/{userId}", "Editar Usuario") {
        fun createRoute(userId: Long) = "user/edit/$userId"
    }
    
    // Backup
    object Backup : Screen("backup", "Respaldo")
    
    // Product History
    object ProductHistory : Screen("product/{productId}/history", "Historial") {
        fun createRoute(productId: Long) = "product/$productId/history"
    }
}

// Items para Bottom Navigation
val bottomNavItems = listOf(
    Screen.Home,
    Screen.Products,
    Screen.Cart,
    Screen.Sales,
    Screen.Settings
)

// ========== NavHost Principal ==========

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Login
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        // Home / Dashboard
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToProducts = { navController.navigate(Screen.Products.route) },
                onNavigateToLowStock = { 
                    navController.navigate(Screen.Products.route + "?filter=low_stock")
                },
                onNavigateToReports = { navController.navigate(Screen.Reports.route) },
                onNavigateToScanner = { navController.navigate(Screen.BarcodeScanner.route) }
            )
        }
        
        // Products
        composable(
            route = Screen.Products.route + "?filter={filter}",
            arguments = listOf(
                navArgument("filter") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val filter = backStackEntry.arguments?.getString("filter")
            ProductsScreen(
                initialFilter = filter,
                onNavigateToProduct = { productId ->
                    navController.navigate(Screen.ProductDetail.createRoute(productId))
                },
                onNavigateToAddProduct = {
                    navController.navigate(Screen.AddProduct.route)
                },
                onNavigateToScanner = {
                    navController.navigate(Screen.BarcodeScanner.route)
                }
            )
        }
        
        // Product Detail
        composable(
            route = Screen.ProductDetail.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
            ProductDetailScreen(
                productId = productId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { 
                    navController.navigate(Screen.EditProduct.createRoute(productId))
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.ProductHistory.createRoute(productId))
                },
                onAddToCart = {
                    navController.navigate(Screen.Cart.route)
                }
            )
        }
        
        // Add Product
        composable(Screen.AddProduct.route) {
            AddEditProductScreen(
                productId = null,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onNavigateToScanner = {
                    navController.navigate(Screen.BarcodeScannerForAdd.route)
                }
            )
        }
        
        // Edit Product
        composable(
            route = Screen.EditProduct.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
            AddEditProductScreen(
                productId = productId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onNavigateToScanner = {
                    navController.navigate(Screen.BarcodeScannerForAdd.route)
                }
            )
        }
        
        // Product History
        composable(
            route = Screen.ProductHistory.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
            ProductHistoryScreen(
                productId = productId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Cart
        composable(Screen.Cart.route) {
            CartScreen(
                onNavigateToProducts = { navController.navigate(Screen.Products.route) },
                onNavigateToScanner = { navController.navigate(Screen.BarcodeScanner.route) },
                onSaleCompleted = {
                    navController.navigate(Screen.Sales.route) {
                        popUpTo(Screen.Cart.route) { inclusive = true }
                    }
                }
            )
        }
        
        // Sales
        composable(Screen.Sales.route) {
            SalesScreen(
                onNavigateToSale = { saleId ->
                    navController.navigate(Screen.SaleDetail.createRoute(saleId))
                },
                onNavigateToReports = { navController.navigate(Screen.Reports.route) }
            )
        }
        
        // Sale Detail
        composable(
            route = Screen.SaleDetail.route,
            arguments = listOf(
                navArgument("saleId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val saleId = backStackEntry.arguments?.getLong("saleId") ?: 0L
            SaleDetailScreen(
                saleId = saleId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Barcode Scanner
        composable(Screen.BarcodeScanner.route) {
            BarcodeScannerScreen(
                onBarcodeScanned = { barcode ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("scanned_barcode", barcode)
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Barcode Scanner for Add
        composable(Screen.BarcodeScannerForAdd.route) {
            BarcodeScannerScreen(
                onBarcodeScanned = { barcode ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("scanned_barcode", barcode)
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Reports
        composable(Screen.Reports.route) {
            ReportsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDailyReport = { navController.navigate(Screen.DailyReport.route) },
                onNavigateToMonthlyReport = { navController.navigate(Screen.MonthlyReport.route) },
                onNavigateToAnnualReport = { navController.navigate(Screen.AnnualReport.route) }
            )
        }
        
        composable(Screen.DailyReport.route) {
            DailyReportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.MonthlyReport.route) {
            MonthlyReportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.AnnualReport.route) {
            AnnualReportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        // Settings
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToUsers = { navController.navigate(Screen.Users.route) },
                onNavigateToBackup = { navController.navigate(Screen.Backup.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        // Users
        composable(Screen.Users.route) {
            UsersScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddUser = { navController.navigate(Screen.AddUser.route) },
                onNavigateToEditUser = { userId ->
                    navController.navigate(Screen.EditUser.createRoute(userId))
                }
            )
        }
        
        composable(Screen.AddUser.route) {
            AddEditUserScreen(
                userId = null,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.EditUser.route,
            arguments = listOf(
                navArgument("userId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            AddEditUserScreen(
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
        
        // Backup
        composable(Screen.Backup.route) {
            BackupScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
