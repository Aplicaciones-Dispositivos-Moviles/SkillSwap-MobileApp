package com.innovify.skillswap.shared.presentation.design

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

/** Navigation route of each top-level tab. "Path" is the home tab and the root of the back stack. */
val StudentTab.route: String
    get() = when (this) {
        StudentTab.Path -> "Path"
        StudentTab.Certificates -> "Certificates"
        StudentTab.Assessments -> "Assessments"
        StudentTab.Profile -> "Profile"
    }

/**
 * Switches tab without stacking screens: everything above Mi ruta is popped (its state saved), the tab is
 * opened once and its saved state restored. Back from any other tab therefore returns to Mi ruta.
 */
fun NavHostController.openTab(tab: StudentTab) {
    navigate(tab.route) {
        popUpTo(StudentTab.Path.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Frame of the four top-level screens: Figma background and the bottom navigation. [content] receives the
 * padding of the system bars and the bottom bar so nothing is hidden behind them.
 */
@Composable
fun StudentTabScaffold(
    selected: StudentTab,
    nav: NavHostController,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = ScreenBackground,
        bottomBar = { StudentBottomBar(selected = selected, onSelect = { nav.openTab(it) }) },
        floatingActionButton = floatingActionButton,
        content = content
    )
}
