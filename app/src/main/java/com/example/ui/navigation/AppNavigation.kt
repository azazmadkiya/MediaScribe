package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.NoteViewModel
import com.example.ui.screens.ExtractScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NoteDetailScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: NoteViewModel = viewModel()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToExtract = { navController.navigate("extract") },
                onNavigateToDetail = { noteId -> navController.navigate("detail/$noteId") }
            )
        }
        composable("extract") {
            ExtractScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNoteExtracted = { noteId ->
                    if (noteId > 0) {
                        navController.navigate("detail/$noteId") {
                            popUpTo("home")
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }
        composable(
            route = "detail/{noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.LongType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L
            NoteDetailScreen(
                noteId = noteId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
