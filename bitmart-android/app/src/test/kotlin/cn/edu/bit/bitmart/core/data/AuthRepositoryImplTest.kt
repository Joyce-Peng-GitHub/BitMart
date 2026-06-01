package cn.edu.bit.bitmart.core.data

import cn.edu.bit.bitmart.core.data.repository.AuthRepositoryImpl
import cn.edu.bit.bitmart.core.domain.DomainResult
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryImplTest {

    private val authBody = """{"token":"tok-123","user":{"id":1,"studentId":"1120201234","displayName":"匿名","role":"NORMAL"}}"""

    @Test
    fun `login success persists token and returns user`() = runTest {
        val store = FakeTokenStore()
        val api = TestApiSupport.fixedApi(HttpStatusCode.OK, authBody)
        val repo = AuthRepositoryImpl(api, store)

        val result = repo.login("1120201234", "Secret123")

        assertTrue(result is DomainResult.Success)
        assertEquals("1120201234", (result as DomainResult.Success).data.studentId)
        assertEquals("tok-123", store.current())
        assertTrue(repo.isLoggedIn.first())
    }

    @Test
    fun `login failure surfaces error code and does not persist token`() = runTest {
        val store = FakeTokenStore()
        val body = """{"error":{"code":"UNAUTHORIZED","message":"学号或密码错误"}}"""
        val api = TestApiSupport.fixedApi(HttpStatusCode.Unauthorized, body)
        val repo = AuthRepositoryImpl(api, store)

        val result = repo.login("x", "y")

        assertTrue(result is DomainResult.Failure)
        assertEquals("UNAUTHORIZED", (result as DomainResult.Failure).code)
        assertNull(store.current())
    }

    @Test
    fun `register verifies then registers and stores token`() = runTest {
        val store = FakeTokenStore()
        // register 直接命中：本测试只覆盖 register() 调用本身（verify 在 ViewModel 编排）。
        val api = TestApiSupport.fixedApi(HttpStatusCode.OK, authBody)
        val repo = AuthRepositoryImpl(api, store)

        val result = repo.register("ticket", "1120201234", "Secret123", "小明")

        assertTrue(result is DomainResult.Success)
        assertEquals("tok-123", store.current())
    }

    @Test
    fun `logout clears token even when offline`() = runTest {
        val store = FakeTokenStore(initial = "tok-123")
        // 让 API 抛网络错误。
        val api = TestApiSupport.api(token = "tok-123") { throw RuntimeException("offline") }
        val repo = AuthRepositoryImpl(api, store)

        val result = repo.logout()

        assertTrue(result is DomainResult.Success)
        assertNull(store.current())
    }
}
