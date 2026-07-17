package com.rhymo.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector

/** Single source of truth for Rhymo destinations and navigation metadata. */
enum class AppDestination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Search("Search", Icons.Outlined.Search),
    Swipe("Swipe", Icons.Outlined.GraphicEq),
    Library("Library", Icons.Outlined.LibraryMusic),
    Notifications("Alerts", Icons.Outlined.Notifications),
    Profile("Profile", Icons.Outlined.Person)
}

val TopLevelDestinations = listOf(
    AppDestination.Home,
    AppDestination.Search,
    AppDestination.Library
)
