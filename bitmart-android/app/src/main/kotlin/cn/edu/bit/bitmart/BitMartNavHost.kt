package cn.edu.bit.bitmart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.edu.bit.bitmart.feature.about.AboutScreen
import cn.edu.bit.bitmart.feature.auth.AuthScreen
import cn.edu.bit.bitmart.feature.bookscan.BookScanScreen
import cn.edu.bit.bitmart.feature.detail.ListingDetailScreen
import cn.edu.bit.bitmart.feature.notifications.NotificationsScreen
import cn.edu.bit.bitmart.feature.profile.ContactsScreen
import cn.edu.bit.bitmart.feature.profile.MyListingsScreen
import cn.edu.bit.bitmart.feature.publish.PublishScreen
import cn.edu.bit.bitmart.feature.settings.AccountSettingsScreen
import cn.edu.bit.bitmart.feature.settings.LlmSettingsScreen
import cn.edu.bit.bitmart.feature.settings.SettingsScreen

/** 顶层路由。SHELL 为起始目的地（无需登录）；其余以全屏方式叠加于其上。 */
object Routes {
    const val SHELL = "shell"
    const val AUTH = "auth"
    const val PUBLISH = "publish"
    const val BOOK_SCAN = "book_scan"
    const val DETAIL = "detail"
    const val DETAIL_ARG = "id"
    const val NOTIFICATIONS = "notifications"
    const val CONTACTS = "contacts"
    const val SETTINGS = "settings"
    const val ACCOUNT_SETTINGS = "account_settings"
    const val LLM_SETTINGS = "llm_settings"
    const val ABOUT = "about"
    const val MY_LISTINGS = "my_listings"
    const val MY_LISTINGS_ARG = "buy"
    fun detail(id: Long) = "$DETAIL/$id"
    fun myListings(buy: Boolean) = "$MY_LISTINGS/$buy"
}

/**
 * 应用顶层导航图。外壳（底部导航栏 + 买卖/我的）为起始目的地，可在未登录下浏览；
 * 登录、发布、详情作为同级路由全屏叠加（不带底部栏）。详情/发布自行做登录校验。
 */
@Composable
fun BitMartNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.SHELL) {
        composable(Routes.SHELL) {
            BitMartShell(
                onItemClick = { id -> navController.navigate(Routes.detail(id)) },
                onPublishClick = { navController.navigate(Routes.PUBLISH) },
                onLoginClick = { navController.navigate(Routes.AUTH) },
                onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) },
                onContactsClick = { navController.navigate(Routes.CONTACTS) },
                onMyListingsClick = { buy -> navController.navigate(Routes.myListings(buy)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onAboutClick = { navController.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.AUTH) {
            AuthScreen(onAuthenticated = {
                // 登录成功后回到外壳（弹出登录页）。
                navController.popBackStack(Routes.SHELL, inclusive = false)
            })
        }
        composable(Routes.PUBLISH) { entry ->
            // 从条码扫描页回传的 ISBN 写在本条目的 savedStateHandle（键 "isbn_result"）。
            // 消费后删除，避免重复触发。
            val scannedIsbn by entry.savedStateHandle.getStateFlow<String?>("isbn_result", null)
                .collectAsStateWithLifecycle()
            PublishScreen(
                onPublished = { navController.popBackStack() },
                onNavigateToLlmSettings = { navController.navigate(Routes.LLM_SETTINGS) },
                onNavigateToBookScan = { navController.navigate(Routes.BOOK_SCAN) },
                scannedIsbn = scannedIsbn?.also { entry.savedStateHandle.remove<String>("isbn_result") },
            )
        }
        composable(Routes.BOOK_SCAN) {
            // 扫到 ISBN 后写入 PUBLISH 条目（previousBackStackEntry）的 savedStateHandle，再弹出本页。
            BookScanScreen(
                onIsbnScanned = { isbn ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("isbn_result", isbn)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.DETAIL}/{${Routes.DETAIL_ARG}}",
            arguments = listOf(navArgument(Routes.DETAIL_ARG) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.DETAIL_ARG) ?: return@composable
            ListingDetailScreen(listingId = id)
        }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CONTACTS) {
            ContactsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onAccountClick = { navController.navigate(Routes.ACCOUNT_SETTINGS) },
                onLlmClick = { navController.navigate(Routes.LLM_SETTINGS) },
                onComingSoon = { /* 语言 / 主题设置；暂不导航。 */ },
            )
        }
        composable(Routes.LLM_SETTINGS) {
            LlmSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ACCOUNT_SETTINGS) {
            AccountSettingsScreen(
                onBack = { navController.popBackStack() },
                // 未登录 → 跳登录页（替换账号设置，避免返回时再次弹回登录）。
                onRequireLogin = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.ACCOUNT_SETTINGS) { inclusive = true }
                    }
                },
                onLoggedOut = { navController.popBackStack() },
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = "${Routes.MY_LISTINGS}/{${Routes.MY_LISTINGS_ARG}}",
            arguments = listOf(navArgument(Routes.MY_LISTINGS_ARG) { type = NavType.BoolType }),
        ) { entry ->
            val buy = entry.arguments?.getBoolean(Routes.MY_LISTINGS_ARG) ?: false
            MyListingsScreen(
                buy = buy,
                // 详情为全屏同级页，使用外层导航控制器。
                onItemClick = { id -> navController.navigate(Routes.detail(id)) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
