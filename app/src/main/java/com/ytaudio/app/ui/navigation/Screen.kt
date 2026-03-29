package com.ytaudio.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Channels : Screen("channels", "Channels", Icons.Default.Subscriptions)
    data object Downloads : Screen("downloads", "Downloads", Icons.Default.Download)
}

val bottomNavScreens = listOf(Screen.Home, Screen.Channels, Screen.Downloads)
