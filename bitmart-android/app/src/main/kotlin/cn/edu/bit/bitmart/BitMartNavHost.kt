package cn.edu.bit.bitmart

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.edu.bit.bitmart.feature.auth.AuthScreen
import cn.edu.bit.bitmart.feature.detail.ListingDetailScreen
import cn.edu.bit.bitmart.feature.publish.PublishScreen

/** 顶层路由。SHELL 为起始目的地（无需登录）；AUTH/PUBLISH/DETAIL 以全屏方式叠加于其上。 */
object Routes {
    const val SHELL = "shell"
    const val AUTH = "auth"
    const val PUBLISH = "publish"
    const val DETAIL = "detail"
    const val DETAIL_ARG = "id"
    fun detail(id: Long) = "$DETAIL/$id"
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
            )
        }
        composable(Routes.AUTH) {
            AuthScreen(onAuthenticated = {
                // 登录成功后回到外壳（弹出登录页）。
                navController.popBackStack(Routes.SHELL, inclusive = false)
            })
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
