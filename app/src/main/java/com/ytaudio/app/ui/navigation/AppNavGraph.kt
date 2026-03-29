package com.ytaudio.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ytaudio.app.data.local.entity.VideoEntity
import com.ytaudio.app.ui.channels.ChannelsScreen
import com.ytaudio.app.ui.downloads.DownloadsScreen
import com.ytaudio.app.ui.home.HomeScreen
import com.ytaudio.app.ui.player.PlayerBar
import com.ytaudio.app.ui.player.PlayerScreen
import com.ytaudio.app.ui.player.PlayerViewModel

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    var showFullPlayer by remember { mutableStateOf(false) }

    if (showFullPlayer) {
        PlayerScreen(
            viewModel = playerViewModel,
            onCollapse = { showFullPlayer = false }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavScreens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
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
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    HomeScreen()
                }
                composable(Screen.Channels.route) {
                    ChannelsScreen()
                }
                composable(Screen.Downloads.route) {
                    DownloadsScreen(
                        onVideoClick = { video ->
                            playerViewModel.play(video)
                            showFullPlayer = true
                        }
                    )
                }
            }
        }

        // Mini player bar overlay
        PlayerBar(
            viewModel = playerViewModel,
            onExpand = { showFullPlayer = true }
        )
    }
}
