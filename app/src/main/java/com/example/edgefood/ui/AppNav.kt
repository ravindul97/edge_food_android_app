package com.example.edgefood.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.edgefood.MainViewModel

@Composable
fun EdgeFoodApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val vm: MainViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "profile",
        modifier = modifier,
    ) {
        composable("profile") {
            ProfileScreen(
                viewModel = vm,
                onContinue = { navController.navigate("analyze") },
            )
        }
        composable("analyze") {
            AnalyzeScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
