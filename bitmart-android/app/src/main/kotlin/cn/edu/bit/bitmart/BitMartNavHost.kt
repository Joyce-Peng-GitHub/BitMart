package cn.edu.bit.bitmart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.edu.bit.bitmart.feature.auth.AuthScreen
import cn.edu.bit.bitmart.feature.detail.ListingDetailScreen
import cn.edu.bit.bitmart.feature.feed.ListingFeedScreen
import cn.edu.bit.bitmart.feature.publish.PublishScreen

/** 顶层路由。 */
object Routes {
    const val AUTH = "auth"
    const val FEED = "feed"
    const val PUBLISH = "publish"
    const val DETAIL = "detail"
    const val DETAIL_ARG = "id"
    fun detail(id: Long) = "$DETAIL/$id"
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
            ListingFeedScreen(
                onItemClick = { id -> navController.navigate(Routes.detail(id)) },
                onPublishClick = { navController.navigate(Routes.PUBLISH) },
            )
        }
        composable(Routes.PUBLISH) {
            PublishScreen(onPublished = { navController.popBackStack() })
        }
        composable(
            route = "${Routes.DETAIL}/{${Routes.DETAIL_ARG}}",
            arguments = listOf(navArgument(Routes.DETAIL_ARG) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.DETAIL_ARG) ?: return@composable
            ListingDetailScreen(listingId = id)
        }
    }
}
