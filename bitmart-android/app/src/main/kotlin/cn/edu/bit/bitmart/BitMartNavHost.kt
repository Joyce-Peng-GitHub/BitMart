package cn.edu.bit.bitmart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cn.edu.bit.bitmart.feature.auth.AuthScreen
import cn.edu.bit.bitmart.feature.feed.ListingFeedScreen

/** 顶层路由。 */
object Routes {
    const val AUTH = "auth"
    const val FEED = "feed"
}

/**
 * 应用导航图。根据登录态决定起始目的地；登录成功后跳转列表并清空回退栈。
 */
@Composable
fun BitMartNavHost(
    rootViewModel: RootViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    val loggedIn by rootViewModel.isLoggedIn.collectAsStateWithLifecycle(initialValue = false)
    val start = if (loggedIn) Routes.FEED else Routes.AUTH

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.AUTH) {
            AuthScreen(onAuthenticated = {
                navController.navigate(Routes.FEED) {
                    popUpTo(Routes.AUTH) { inclusive = true }
                }
            })
        }
        composable(Routes.FEED) {
            ListingFeedScreen(onItemClick = { /* 详情导航占位，后续接入 listing-detail */ })
        }
    }
}
