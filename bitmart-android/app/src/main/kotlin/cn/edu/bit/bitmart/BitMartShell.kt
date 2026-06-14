package cn.edu.bit.bitmart

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cn.edu.bit.bitmart.feature.profile.ProfileScreen
import cn.edu.bit.bitmart.feature.trade.TradeScreen
import cn.edu.bit.bitmart.core.domain.model.ListingType

/** 底部导航的两个顶层目的地。 */
private enum class ShellTab(val route: String, val label: String, val icon: ImageVector) {
    TRADE("trade", "买卖", Icons.Default.Storefront),
    PROFILE("profile", "我的", Icons.Default.Person),
}

/**
 * 应用外壳：Material3 底部导航栏，承载 “买卖” 与 “我的” 两个顶层目的地。
 * 该外壳无需登录即可浏览（列表为公开）；详情/发布/登录/通知/设置等由上层导航以全屏方式叠加。
 * @param onItemClick 点击列表项进入详情。
 * @param onPublishClick 点击发布。
 * @param onLoginClick “我的” 页未登录时点击登录。
 * @param onNotificationsClick 进入通知页。
 * @param onContactsClick 进入常用联系方式页。
 * @param onMyListingsClick 进入“我的商品/收购”管理页（buy=true 为收购）。
 * @param onSettingsClick 进入设置页。
 * @param onAboutClick 进入关于页。
 */
@Composable
fun BitMartShell(
    onItemClick: (Long) -> Unit,
    onPublishClick: (ListingType) -> Unit,
    onLoginClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onContactsClick: () -> Unit,
    onMyListingsClick: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                ShellTab.entries.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ShellTab.TRADE.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(ShellTab.TRADE.route) {
                TradeScreen(onItemClick = onItemClick, onPublishClick = onPublishClick)
            }
            composable(ShellTab.PROFILE.route) {
                ProfileScreen(
                    onLoginClick = onLoginClick,
                    onNotificationsClick = onNotificationsClick,
                    onContactsClick = onContactsClick,
                    onMyListingsClick = onMyListingsClick,
                    onSettingsClick = onSettingsClick,
                    onAboutClick = onAboutClick,
                )
            }
        }
    }
}
