package com.pocketwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pocketwise.core.ui.theme.PocketWiseTheme
import com.pocketwise.feature.expense_core.AddExpenseScreen
import com.pocketwise.feature.expense_core.CategoryScreen
import com.pocketwise.feature.expense_core.HomeScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketWiseTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onManageCategories = { navController.navigate("categories") },
                                onAddExpense = { navController.navigate("add_expense") },
                                onEditExpense = { id -> navController.navigate("add_expense?expenseId=$id") }
                            )
                        }
                        composable("categories") {
                            CategoryScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            "add_expense?expenseId={expenseId}",
                            arguments = listOf(navArgument("expenseId") { type = NavType.LongType; defaultValue = -1L })
                        ) { backStackEntry ->
                            val expenseId = backStackEntry.arguments?.getLong("expenseId") ?: -1L
                            AddExpenseScreen(
                                onDone = { navController.popBackStack() },
                                expenseId = if (expenseId == -1L) null else expenseId
                            )
                        }
                    }
                }
            }
        }
    }
}
