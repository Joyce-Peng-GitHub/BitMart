package cn.edu.bit.bitmart

import androidx.navigation.NavType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定 AUTH 目的地的参数契约:publishType 必须是可选参数(nullable 且默认值存在),
 * 否则三处裸 navigate(Routes.AUTH) 将无法解析到带参 AUTH_ROUTE。NavHost 与本测试
 * 共用 Routes.authNavArguments(),配置不会漂移。
 */
class AuthRouteArgumentsTest {

    @Test
    fun `auth destination declares exactly one publishType argument`() {
        val args = Routes.authNavArguments()
        assertEquals(1, args.size)
        assertEquals(Routes.AUTH_PUBLISH_ARG, args.single().name)
        assertEquals(NavType.StringType, args.single().argument.type)
    }

    @Test
    fun `publishType argument is optional so bare navigate(AUTH) still resolves`() {
        val arg = Routes.authNavArguments().single().argument
        assertTrue("publishType must be nullable", arg.isNullable)
        assertTrue("publishType must have a default value (optional)", arg.isDefaultValuePresent)
    }
}
