package com.inventario.py

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.inventario.py.ui.navigation.AppNavHost
import com.inventario.py.ui.navigation.bottomNavItems
import com.inventario.py.ui.navigation.Screen
import com.inventario.py.ui.theme.InventarioPyTheme
import com.inventario.py.ui.viewmodel.AuthViewModel
import com.inventario.py.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val isDarkMode by settingsViewModel.isDarkMode.collectAsState()
            
            InventarioPyTheme(darkTheme = isDarkMode) {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val currentUser by authViewModel.currentUser.collectAsState()
    
    // Pantallas que no muestran la barra de navegación
    val screensWithoutBottomBar = listOf(
        Screen.Login.route,
        Screen.BarcodeScanner.route,
        Screen.BarcodeScannerForAdd.route,
        Screen.AddProduct.route,
        "product/edit",
        "product/",
        "sale/",
        "user/edit",
        "user/add"
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route ?: ""
    
    // Determinar si mostrar la barra de navegación
    val showBottomBar = remember(currentRoute) {
        currentRoute.isNotEmpty() && screensWithoutBottomBar.none { 
            currentRoute.startsWith(it) && currentRoute != Screen.Products.route + "?filter={filter}"
        }
    }
    
    // Verificar si el usuario está logueado
    val startDestination = if (currentUser != null) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }
    
    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar && currentUser != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { 
                            it.route == screen.route || it.route?.startsWith(screen.route) == true
                        } == true
                        
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        screen.selectedIcon ?: screen.unselectedIcon!!
                                    } else {
                                        screen.unselectedIcon ?: screen.selectedIcon!!
                                    },
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    // Pop up to the start destination of the graph to
                                    // avoid building up a large stack of destinations
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // Avoid multiple copies of the same destination
                                    launchSingleTop = true
                                    // Restore state when reselecting a previously selected item
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AppNavHost(
                navController = navController,
                startDestination = startDestination
            )
        }
    }
}
