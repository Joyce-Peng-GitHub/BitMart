package cn.edu.bit.bitmart

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.edu.bit.bitmart.feature.about.AboutScreen
import cn.edu.bit.bitmart.feature.auth.AppAuthViewModel
import cn.edu.bit.bitmart.feature.auth.AuthScreen
import cn.edu.bit.bitmart.feature.bookscan.BookScanScreen
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.feature.detail.ListingDetailScreen
import cn.edu.bit.bitmart.feature.notifications.NotificationsScreen
import cn.edu.bit.bitmart.feature.profile.ContactsScreen
import cn.edu.bit.bitmart.feature.profile.MyListingsScreen
import cn.edu.bit.bitmart.feature.publish.PublishScreen
import cn.edu.bit.bitmart.feature.settings.AccountSettingsScreen
import cn.edu.bit.bitmart.feature.settings.ChangePasswordScreen
import cn.edu.bit.bitmart.feature.settings.LlmSettingsScreen
import cn.edu.bit.bitmart.feature.settings.SettingsScreen

/** 顶层路由。SHELL 为起始目的地（无需登录）；其余以全屏方式叠加于其上。 */
object Routes {
    const val SHELL = "shell"
    const val AUTH = "auth"
    const val AUTH_PUBLISH_ARG = "publishType"
    /** AUTH 路由模式：带可选 publishType 参数。用于 composable 注册与登录后续接的 popUpTo。 */
    const val AUTH_ROUTE = "$AUTH?$AUTH_PUBLISH_ARG={$AUTH_PUBLISH_ARG}"
    const val PUBLISH = "publish"
    const val PUBLISH_ARG = "type"
    const val BOOK_SCAN = "book_scan"
    const val DETAIL = "detail"
    const val DETAIL_ARG = "id"
    const val EDIT = "edit"
    const val EDIT_ARG = "id"
    const val NOTIFICATIONS = "notifications"
    const val CONTACTS = "contacts"
    const val SETTINGS = "settings"
    const val ACCOUNT_SETTINGS = "account_settings"
    const val CHANGE_PASSWORD = "change_password"
    const val LLM_SETTINGS = "llm_settings"
    const val ABOUT = "about"
    const val MY_LISTINGS = "my_listings"
    const val MY_LISTINGS_ARG = "buy"
    /** 编辑保存成功后写入上一页 savedStateHandle 的标记键，供“我的”列表刷新。 */
    const val LISTING_CHANGED_KEY = "listing_changed"
    fun detail(id: Long) = "$DETAIL/$id"
    fun edit(id: Long) = "$EDIT/$id"
    fun publish(type: ListingType) = "$PUBLISH/${type.name}"
    fun myListings(buy: Boolean) = "$MY_LISTINGS/$buy"
    /** 携带“待发布意图”的登录路由：登录/注册成功后续接进对应类型的发布页。 */
    fun authForPublish(type: ListingType) = "$AUTH?$AUTH_PUBLISH_ARG=${type.name}"
    /** 发布入口的未登录拦截决策：已登录直达发布页，未登录先跳登录页并携带类型以便续接。 */
    fun publishDestination(loggedIn: Boolean, type: ListingType) =
        if (loggedIn) publish(type) else authForPublish(type)

    /**
     * AUTH 目的地的导航参数(唯一来源):publishType 为可选(nullable + 默认 null),
     * 以保证未携带"待发布意图"的裸 navigate(AUTH) 仍能解析到带参 AUTH_ROUTE。
     * NavHost 注册与单测共用本函数,避免两处配置漂移。
     */
    fun authNavArguments(): List<NamedNavArgument> = listOf(
        navArgument(AUTH_PUBLISH_ARG) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    )
}

/**
 * 应用顶层导航图。外壳（底部导航栏 + 买卖/我的）为起始目的地，可在未登录下浏览；
 * 登录、发布、详情作为同级路由全屏叠加（不带底部栏）。详情/发布自行做登录校验。
 */
private const val NAV_TAG = "BitMartNav"

@Composable
fun BitMartNavHost(
    navController: NavHostController = rememberNavController(),
    authViewModel: AppAuthViewModel = hiltViewModel(),
) {
    val loggedIn by authViewModel.isLoggedIn.collectAsStateWithLifecycle()
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, arguments ->
            Log.d(NAV_TAG, "navigate -> ${destination.route} args=$arguments")
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    NavHost(navController = navController, startDestination = Routes.SHELL) {
        composable(Routes.SHELL) { entry ->
            // 本人项从买卖列表左滑编辑后，编辑页把 LISTING_CHANGED_KEY 写到 SHELL entry；据此刷新买卖列表。
            val listingChanged by entry.savedStateHandle
                .getStateFlow(Routes.LISTING_CHANGED_KEY, false)
                .collectAsStateWithLifecycle()
            BitMartShell(
                onItemClick = { id ->
                    Log.i(NAV_TAG, "click: listing id=$id")
                    navController.navigate(Routes.detail(id))
                },
                onPublishClick = { type ->
                    Log.i(NAV_TAG, "click: publish type=$type loggedIn=$loggedIn")
                    navController.navigate(Routes.publishDestination(loggedIn, type))
                },
                onEditClick = { id ->
                    Log.i(NAV_TAG, "click: edit listing id=$id")
                    navController.navigate(Routes.edit(id))
                },
                onLoginClick = {
                    Log.i(NAV_TAG, "click: login")
                    navController.navigate(Routes.AUTH)
                },
                onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) },
                onContactsClick = { navController.navigate(Routes.CONTACTS) },
                onAccountClick = { navController.navigate(Routes.ACCOUNT_SETTINGS) },
                onMyListingsClick = { buy -> navController.navigate(Routes.myListings(buy)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onAboutClick = { navController.navigate(Routes.ABOUT) },
                listingChanged = listingChanged,
                onListingChangeConsumed = { entry.savedStateHandle[Routes.LISTING_CHANGED_KEY] = false },
            )
        }
        composable(
            route = Routes.AUTH_ROUTE,
            arguments = Routes.authNavArguments(),
        ) { entry ->
            // 携带“待发布意图”时：登录/注册成功后用发布页替换登录页（popUpTo 登录页 inclusive），返回栈干净。
            val pendingPublishType = entry.arguments?.getString(Routes.AUTH_PUBLISH_ARG)
                ?.let { runCatching { ListingType.valueOf(it) }.getOrNull() }
            AuthScreen(
                onAuthenticated = {
                    Log.i(NAV_TAG, "auth: login success pendingPublish=$pendingPublishType")
                    if (pendingPublishType != null) {
                        navController.navigate(Routes.publish(pendingPublishType)) {
                            popUpTo(Routes.AUTH_ROUTE) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack(Routes.SHELL, inclusive = false)
                    }
                },
                onBack = { navController.popBackStack() },
                onForgotPassword = { navController.navigate(Routes.CHANGE_PASSWORD) },
            )
        }
        composable(
            route = "${Routes.PUBLISH}/{${Routes.PUBLISH_ARG}}",
            arguments = listOf(navArgument(Routes.PUBLISH_ARG) { type = NavType.StringType }),
        ) { entry ->
            // 发布类型由入口决定（商品→SELL，收购→BUY）；解析失败兜底 SELL。
            val publishType = runCatching {
                ListingType.valueOf(entry.arguments?.getString(Routes.PUBLISH_ARG) ?: "")
            }.getOrDefault(ListingType.SELL)
            val scannedIsbn by entry.savedStateHandle.getStateFlow<String?>("isbn_result", null)
                .collectAsStateWithLifecycle()
            PublishScreen(
                initialType = publishType,
                onPublished = {
                    Log.i(NAV_TAG, "action: listing published")
                    navController.popBackStack()
                },
                onNavigateToLlmSettings = { navController.navigate(Routes.LLM_SETTINGS) },
                onNavigateToBookScan = { navController.navigate(Routes.BOOK_SCAN) },
                onBack = { navController.popBackStack() },
                scannedIsbn = scannedIsbn,
                onIsbnConsumed = { entry.savedStateHandle.remove<String>("isbn_result") },
            )
        }
        composable(Routes.BOOK_SCAN) {
            BookScanScreen(
                onIsbnScanned = { isbn ->
                    Log.i(NAV_TAG, "action: isbn scanned isbn=$isbn")
                    navController.previousBackStackEntry?.savedStateHandle?.set("isbn_result", isbn)
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
            // 编辑保存后 onSaved 把标记写到详情页 entry；详情据此 reload，
            // 并把标记前递给更上一层（“我的”列表），保证返回列表时也刷新。
            val changed by entry.savedStateHandle
                .getStateFlow(Routes.LISTING_CHANGED_KEY, false)
                .collectAsStateWithLifecycle()
            ListingDetailScreen(
                listingId = id,
                onBack = { navController.popBackStack() },
                onEditClick = { editId -> navController.navigate(Routes.edit(editId)) },
                onDeleteSuccess = {
                    // 通知上一页（“我的”列表）刷新，然后返回。
                    navController.previousBackStackEntry?.savedStateHandle?.set(Routes.LISTING_CHANGED_KEY, true)
                    navController.popBackStack()
                },
                refreshSignal = changed,
                onRefreshConsumed = {
                    entry.savedStateHandle[Routes.LISTING_CHANGED_KEY] = false
                    // 前递给列表：返回时触发列表刷新。
                    navController.previousBackStackEntry?.savedStateHandle?.set(Routes.LISTING_CHANGED_KEY, true)
                },
            )
        }
        composable(
            route = "${Routes.EDIT}/{${Routes.EDIT_ARG}}",
            arguments = listOf(navArgument(Routes.EDIT_ARG) { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong(Routes.EDIT_ARG) ?: return@composable
            // 编辑复用发布表单（PublishScreen 编辑模式），字段与发布页相同。
            val scannedIsbn by entry.savedStateHandle.getStateFlow<String?>("isbn_result", null)
                .collectAsStateWithLifecycle()
            PublishScreen(
                editListingId = id,
                onPublished = {
                    // 保存成功：标记上一页（详情或"我的"列表）刷新，然后返回。
                    navController.previousBackStackEntry?.savedStateHandle?.set(Routes.LISTING_CHANGED_KEY, true)
                    navController.popBackStack()
                },
                onNavigateToLlmSettings = { navController.navigate(Routes.LLM_SETTINGS) },
                onNavigateToBookScan = { navController.navigate(Routes.BOOK_SCAN) },
                onBack = { navController.popBackStack() },
                scannedIsbn = scannedIsbn,
                onIsbnConsumed = { entry.savedStateHandle.remove<String>("isbn_result") },
            )
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
                onChangePassword = { navController.navigate(Routes.CHANGE_PASSWORD) },
            )
        }
        composable(Routes.CHANGE_PASSWORD) {
            ChangePasswordScreen(
                onBack = { navController.popBackStack() },
                onPasswordChanged = {
                    // 改密会吊销会话；成功后跳登录页。popUpTo(SHELL) 同时兼容“账号设置”与“登录页·忘记密码”两个入口，
                    // 清掉中间页，避免落回失效的设置/改密页或重复的登录页。
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.SHELL) { inclusive = false }
                    }
                },
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
            // 编辑/删除返回后通过 savedStateHandle 标记触发刷新。
            val changed by entry.savedStateHandle
                .getStateFlow(Routes.LISTING_CHANGED_KEY, false)
                .collectAsStateWithLifecycle()
            MyListingsScreen(
                buy = buy,
                // 详情/编辑为全屏同级页，使用外层导航控制器。
                onItemClick = { id -> navController.navigate(Routes.detail(id)) },
                onEditClick = { id -> navController.navigate(Routes.edit(id)) },
                onPublishClick = {
                    val type = if (buy) ListingType.BUY else ListingType.SELL
                    navController.navigate(Routes.publishDestination(loggedIn, type))
                },
                onBack = { navController.popBackStack() },
                refreshSignal = changed,
                onRefreshConsumed = { entry.savedStateHandle[Routes.LISTING_CHANGED_KEY] = false },
            )
        }
    }
}
