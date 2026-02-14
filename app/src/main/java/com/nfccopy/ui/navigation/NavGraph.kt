package com.nfccopy.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nfccopy.data.repository.CardRepository
import com.nfccopy.nfc.CardEmulator
import com.nfccopy.nfc.NfcManager
import com.nfccopy.nfc.NfcReader
import com.nfccopy.nfc.NfcWriter
import com.nfccopy.ui.screens.CardDetailScreen
import com.nfccopy.ui.screens.EmulateScreen
import com.nfccopy.ui.screens.ExpertScreen
import com.nfccopy.ui.screens.ReadScreen
import com.nfccopy.ui.screens.SavedCardsScreen
import com.nfccopy.ui.screens.WriteScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Read : Screen("read", "Lesen", Icons.Default.Nfc)
    data object Saved : Screen("saved", "Gespeichert", Icons.Default.CreditCard)
    data object Write : Screen("write", "Schreiben", Icons.Default.Edit)
    data object Emulate : Screen("emulate", "Emulieren", Icons.Default.PhonelinkRing)
    data object Expert : Screen("expert", "Experte", Icons.Default.BugReport)
    data object CardDetail : Screen("card_detail/{cardId}", "Details", Icons.Default.CreditCard) {
        fun createRoute(cardId: Long) = "card_detail/$cardId"
    }
}

val bottomNavItems = listOf(Screen.Read, Screen.Saved, Screen.Write, Screen.Emulate, Screen.Expert)

@Composable
fun AppNavGraph(
    nfcManager: NfcManager,
    nfcReader: NfcReader,
    nfcWriter: NfcWriter,
    cardEmulator: CardEmulator,
    repository: CardRepository
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, fontSize = 10.sp, maxLines = 1) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Read.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Read.route) {
                ReadScreen(
                    nfcManager = nfcManager,
                    nfcReader = nfcReader,
                    repository = repository,
                    onCardRead = { cardId ->
                        navController.navigate(Screen.CardDetail.createRoute(cardId))
                    }
                )
            }
            composable(Screen.Saved.route) {
                SavedCardsScreen(
                    repository = repository,
                    onCardClick = { cardId ->
                        navController.navigate(Screen.CardDetail.createRoute(cardId))
                    }
                )
            }
            composable(Screen.Write.route) {
                WriteScreen(
                    nfcManager = nfcManager,
                    nfcWriter = nfcWriter,
                    repository = repository
                )
            }
            composable(Screen.Emulate.route) {
                EmulateScreen(
                    cardEmulator = cardEmulator,
                    repository = repository
                )
            }
            composable(Screen.Expert.route) {
                ExpertScreen(
                    nfcManager = nfcManager
                )
            }
            composable(
                route = Screen.CardDetail.route,
                arguments = listOf(navArgument("cardId") { type = NavType.LongType })
            ) { backStackEntry ->
                val cardId = backStackEntry.arguments?.getLong("cardId") ?: return@composable
                CardDetailScreen(
                    cardId = cardId,
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
