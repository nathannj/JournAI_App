package com.journai.journai.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.journai.journai.R
import com.journai.journai.ui.screens.create.CreateEntryScreen
import com.journai.journai.ui.screens.chat.ChatScreen
import com.journai.journai.ui.screens.entries.EntriesScreen
import com.journai.journai.ui.screens.settings.SettingsScreen
import com.journai.journai.ui.screens.settings.OrganizationPreferencesScreen
import com.journai.journai.ui.screens.settings.BlacklistSettingsScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JournAINavigation() {
    val navController = rememberNavController()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    
    Scaffold(
        bottomBar = {
            NavigationBar(modifier = Modifier.height(52.dp)) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = null,
                    alwaysShowLabel = false,
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        scope.launch {
                            if (navController.currentDestination?.route != "root") {
                                navController.popBackStack("root", false)
                            }
                            pagerState.animateScrollToPage(0)
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                    label = null,
                    alwaysShowLabel = false,
                    selected = pagerState.currentPage == 1,
                    onClick = {
                        scope.launch {
                            if (navController.currentDestination?.route != "root") {
                                navController.popBackStack("root", false)
                            }
                            pagerState.animateScrollToPage(1)
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = null,
                    alwaysShowLabel = false,
                    selected = pagerState.currentPage == 2,
                    onClick = {
                        scope.launch {
                            if (navController.currentDestination?.route != "root") {
                                navController.popBackStack("root", false)
                            }
                            pagerState.animateScrollToPage(2)
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val isOverlayActive = currentRoute != "root"

        LaunchedEffect(pagerState.currentPage) {
            if (navController.currentDestination?.route != "root") {
                navController.popBackStack("root", false)
            }
        }

        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (!isOverlayActive) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (page) {
                        0 -> EntriesScreen(navController = navController)
                        1 -> ChatScreen()
                        else -> SettingsScreen(navController = navController)
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = "root",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("root") { }
                composable(
                    route = "create?dateMillis={dateMillis}",
                    arguments = listOf(
                        navArgument("dateMillis") {
                            type = NavType.LongType
                            defaultValue = -1L
                        }
                    )
                ) { backStackEntry ->
                    val millis = backStackEntry.arguments?.getLong("dateMillis") ?: -1L
                    val initialDateMillis = if (millis > 0L) millis else null
                    CreateEntryScreen(initialDateMillis = initialDateMillis)
                }
                composable("organization_preferences") {
                    OrganizationPreferencesScreen(navController = navController)
                }
                composable("blacklist_settings") {
                    BlacklistSettingsScreen(navController = navController)
                }
            }
        }
    }
}
