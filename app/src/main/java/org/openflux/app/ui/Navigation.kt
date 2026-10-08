package org.openflux.app.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.openflux.app.R
import org.openflux.app.data.Profile
import org.openflux.app.data.ProfileDeepLink
import org.openflux.app.ui.deploy.DeployListScreen
import org.openflux.app.ui.deploy.DeployServerDetailScreen
import org.openflux.app.ui.deploy.DeployServerEditScreen
import org.openflux.app.ui.home.HomeScreen
import org.openflux.app.ui.logs.TunnelLogsScreen
import org.openflux.app.ui.profiles.ProfileEditScreen
import org.openflux.app.ui.profiles.ProfileListScreen
import org.openflux.app.ui.profiles.QrScanScreen
import org.openflux.app.ui.settings.SettingsScreen
import org.openflux.app.ui.settings.SiteSplitTunnelScreen
import org.openflux.app.ui.settings.SplitTunnelScreen

private sealed class Destination(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Destination("home", R.string.nav_home, Icons.Filled.Home)
    data object Logs : Destination("logs", R.string.nav_logs, Icons.Filled.Info)
    data object Profiles : Destination("profiles", R.string.nav_profiles, Icons.Filled.List)
    data object Deploy : Destination("deploy", R.string.nav_deploy, Icons.Filled.Build)
    data object Settings : Destination("settings", R.string.nav_settings, Icons.Filled.Settings)
}

private const val PROFILE_EDIT_ROUTE = "profile_edit"
private const val PROFILE_ID_ARG = "profileId"
private const val DEPLOY_EDIT_ROUTE = "deploy_edit"
private const val DEPLOY_DETAIL_ROUTE = "deploy_detail"
private const val DEPLOY_ID_ARG = "serverId"
private const val SPLIT_TUNNEL_ROUTE = "split_tunnel"
private const val QR_SCAN_ROUTE = "qr_scan"
private const val SITE_SPLIT_TUNNEL_ROUTE = "site_split_tunnel"

private val TAB_ROUTES = setOf(
    Destination.Home.route,
    Destination.Logs.route,
    Destination.Profiles.route,
    Destination.Deploy.route,
    Destination.Settings.route,
)

// Tabs sit side by side with no inherent order, so sliding between them would imply a direction
// that isn't there - only screens pushed on top of a tab (edit/detail/settings sub-pages) get the
// slide; switching tabs just crossfades.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES

private const val TAB_FADE_MS = 160
private const val PUSH_SLIDE_MS = 280
private const val TAB_SCALE_IN_START = 0.97f
private const val TAB_SCALE_OUT_END = 1.02f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenFluxNavHost(
    onConnectRequested: (String) -> Unit,
    onDisconnectRequested: () -> Unit,
    onSocks5Requested: (String) -> Unit,
    onSocks5StopRequested: () -> Unit,
    deepLinkUri: Uri? = null,
    onDeepLinkHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val tabs = listOf(Destination.Home, Destination.Logs, Destination.Profiles, Destination.Deploy, Destination.Settings)

    var importedProfile by remember { mutableStateOf<Profile?>(null) }

    LaunchedEffect(deepLinkUri) {
        val uri = deepLinkUri ?: return@LaunchedEffect
        ProfileDeepLink.parse(uri)?.let { imported ->
            importedProfile = imported
            navController.navigate("$PROFILE_EDIT_ROUTE/new") { launchSingleTop = true }
        }
        onDeepLinkHandled()
    }

    val imeVisible = WindowInsets.isImeVisible

    Scaffold(
        modifier = androidx.compose.ui.Modifier.imePadding(),
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            if (!imeVisible) NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                // No saveState/restoreState: it previously let a stale "new profile" draft reappear.
                                popUpTo(navController.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = {
                            Text(
                                stringResourceCompat(tab.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = androidx.compose.ui.Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = {
                if (isTabSwitch()) {
                    fadeIn(tween(TAB_FADE_MS)) + scaleIn(initialScale = TAB_SCALE_IN_START, animationSpec = tween(TAB_FADE_MS))
                } else {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(PUSH_SLIDE_MS)) +
                        fadeIn(tween(PUSH_SLIDE_MS))
                }
            },
            exitTransition = {
                if (isTabSwitch()) {
                    fadeOut(tween(TAB_FADE_MS)) + scaleOut(targetScale = TAB_SCALE_OUT_END, animationSpec = tween(TAB_FADE_MS))
                } else {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(PUSH_SLIDE_MS)) +
                        fadeOut(tween(PUSH_SLIDE_MS))
                }
            },
            popEnterTransition = {
                if (isTabSwitch()) {
                    fadeIn(tween(TAB_FADE_MS)) + scaleIn(initialScale = TAB_SCALE_IN_START, animationSpec = tween(TAB_FADE_MS))
                } else {
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(PUSH_SLIDE_MS)) +
                        fadeIn(tween(PUSH_SLIDE_MS))
                }
            },
            popExitTransition = {
                if (isTabSwitch()) {
                    fadeOut(tween(TAB_FADE_MS)) + scaleOut(targetScale = TAB_SCALE_OUT_END, animationSpec = tween(TAB_FADE_MS))
                } else {
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(PUSH_SLIDE_MS)) +
                        fadeOut(tween(PUSH_SLIDE_MS))
                }
            },
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    onConnectRequested = onConnectRequested,
                    onDisconnectRequested = onDisconnectRequested,
                    onSocks5Requested = onSocks5Requested,
                    onSocks5StopRequested = onSocks5StopRequested,
                    onManageProfiles = { navController.navigate(Destination.Profiles.route) },
                )
            }
            composable(Destination.Logs.route) {
                TunnelLogsScreen()
            }
            composable(Destination.Profiles.route) {
                ProfileListScreen(
                    onAddProfile = {
                        importedProfile = null
                        navController.navigate("$PROFILE_EDIT_ROUTE/new") { launchSingleTop = true }
                    },
                    onScanQr = { navController.navigate(QR_SCAN_ROUTE) },
                    onEditProfile = { id -> navController.navigate("$PROFILE_EDIT_ROUTE/$id") },
                )
            }
            composable(
                route = QR_SCAN_ROUTE,
                // Pop instantly: the default animation left a frozen camera-colored rectangle fading out.
                popExitTransition = { fadeOut(animationSpec = tween(0)) },
            ) {
                QrScanScreen(onDone = { navController.popBackStack() })
            }
            composable(
                route = "$PROFILE_EDIT_ROUTE/{$PROFILE_ID_ARG}",
            ) { backStackEntry ->
                val rawId = backStackEntry.arguments?.getString(PROFILE_ID_ARG)
                val isNew = rawId == null || rawId == "new"

                // Clears on any exit path, including system back, to avoid prefilling the next "add profile".
                androidx.compose.runtime.DisposableEffect(Unit) {
                    onDispose { importedProfile = null }
                }

                ProfileEditScreen(
                    profileId = rawId?.takeIf { !isNew },
                    importedProfile = if (isNew) importedProfile else null,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Destination.Deploy.route) {
                DeployListScreen(
                    onAddServer = { navController.navigate("$DEPLOY_EDIT_ROUTE/new") },
                    onOpenServer = { id -> navController.navigate("$DEPLOY_DETAIL_ROUTE/$id") },
                )
            }
            composable(
                route = "$DEPLOY_EDIT_ROUTE/{$DEPLOY_ID_ARG}",
            ) { backStackEntry ->
                val rawId = backStackEntry.arguments?.getString(DEPLOY_ID_ARG)
                DeployServerEditScreen(
                    serverId = rawId?.takeIf { it != "new" },
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                route = "$DEPLOY_DETAIL_ROUTE/{$DEPLOY_ID_ARG}",
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString(DEPLOY_ID_ARG)
                if (id != null) {
                    DeployServerDetailScreen(
                        serverId = id,
                        onEditServer = { navController.navigate("$DEPLOY_EDIT_ROUTE/$it") },
                    )
                }
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onOpenSplitTunnel = { navController.navigate(SPLIT_TUNNEL_ROUTE) },
                    onOpenSiteSplitTunnel = { navController.navigate(SITE_SPLIT_TUNNEL_ROUTE) },
                )
            }
            composable(SPLIT_TUNNEL_ROUTE) {
                SplitTunnelScreen(onDone = { navController.popBackStack() })
            }
            composable(SITE_SPLIT_TUNNEL_ROUTE) {
                SiteSplitTunnelScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun stringResourceCompat(resId: Int): String = androidx.compose.ui.res.stringResource(id = resId)
