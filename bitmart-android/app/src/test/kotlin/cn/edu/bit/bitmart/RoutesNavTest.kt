package cn.edu.bit.bitmart

import cn.edu.bit.bitmart.core.domain.model.ListingType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Routes] 中“发布入口未登录拦截”相关纯函数的单元测试（无 Android、无协程）。
 * 覆盖已登录直达发布、未登录跳登录携带类型，以及路由与 NavHost 解析方式的往返一致性。
 */
class RoutesNavTest {

    @Test
    fun `publishDestination when logged in goes straight to publish SELL`() {
        assertEquals("publish/SELL", Routes.publishDestination(loggedIn = true, type = ListingType.SELL))
    }

    @Test
    fun `publishDestination when logged in goes straight to publish BUY`() {
        assertEquals("publish/BUY", Routes.publishDestination(loggedIn = true, type = ListingType.BUY))
    }

    @Test
    fun `publishDestination when logged out routes to auth carrying SELL`() {
        assertEquals("auth?publishType=SELL", Routes.publishDestination(loggedIn = false, type = ListingType.SELL))
    }

    @Test
    fun `publishDestination when logged out routes to auth carrying BUY`() {
        assertEquals("auth?publishType=BUY", Routes.publishDestination(loggedIn = false, type = ListingType.BUY))
    }

    @Test
    fun `authForPublish builds auth route with the type as query value`() {
        assertEquals("auth?publishType=SELL", Routes.authForPublish(ListingType.SELL))
    }

    @Test
    fun `AUTH_ROUTE declares the optional publishType query placeholder`() {
        assertEquals("auth?publishType={publishType}", Routes.AUTH_ROUTE)
    }

    @Test
    fun `authForPublish round-trips back to the same ListingType - SELL`() {
        assertRoundTrip(ListingType.SELL)
    }

    @Test
    fun `authForPublish round-trips back to the same ListingType - BUY`() {
        assertRoundTrip(ListingType.BUY)
    }

    @Test
    fun `logged-in publishDestination round-trips back to the same ListingType`() {
        // 对称性：已登录分支产出的 publish/{type} 路由，按 PUBLISH composable 的方式解析应还原同一枚举。
        for (type in ListingType.entries) {
            val route = Routes.publishDestination(loggedIn = true, type = type)
            val arg = route.substringAfter("${Routes.PUBLISH}/")
            assertEquals(type, ListingType.valueOf(arg))
        }
    }

    /**
     * 模拟 NavHost 的解析：从 [Routes.authForPublish] 产出的路由中取出 publishType 查询值，
     * 再用 [ListingType.valueOf] 还原，应得到同一枚举（与 BitMartNavHost 的解析逻辑一致）。
     */
    private fun assertRoundTrip(type: ListingType) {
        val route = Routes.authForPublish(type)
        val queryValue = route.substringAfter("${Routes.AUTH_PUBLISH_ARG}=")
        assertEquals(type, ListingType.valueOf(queryValue))
    }
}
