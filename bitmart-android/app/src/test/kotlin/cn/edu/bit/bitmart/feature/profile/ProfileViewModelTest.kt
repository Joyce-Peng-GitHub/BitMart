package cn.edu.bit.bitmart.feature.profile

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private val user = User(7, "1120201234", "小明", "小明", "NORMAL")

    private fun authRepo(loggedIn: Boolean) = object : AuthRepository {
        override suspend fun verify(studentId: String, password: String) = DomainResult.Success("t")
        override suspend fun register(verifyTicket: String, studentId: String, password: String, nickname: String?) = DomainResult.Success(user)
        override suspend fun login(studentId: String, password: String) = DomainResult.Success(user)
        override suspend fun resetPassword(verifyTicket: String, studentId: String, newPassword: String) = DomainResult.Success(Unit)
        override suspend fun logout() = DomainResult.Success(Unit)
        override suspend fun deleteAccount() = DomainResult.Success(Unit)
        override val isLoggedIn: Flow<Boolean> = flowOf(loggedIn)
    }

    /** 可变登录态的 auth 仓储，用于验证登录态变化时 UI 状态的响应式更新。 */
    private fun authRepo(loginFlow: Flow<Boolean>) = object : AuthRepository {
        override suspend fun verify(studentId: String, password: String) = DomainResult.Success("t")
        override suspend fun register(verifyTicket: String, studentId: String, password: String, nickname: String?) = DomainResult.Success(user)
        override suspend fun login(studentId: String, password: String) = DomainResult.Success(user)
        override suspend fun resetPassword(verifyTicket: String, studentId: String, newPassword: String) = DomainResult.Success(Unit)
        override suspend fun logout() = DomainResult.Success(Unit)
        override suspend fun deleteAccount() = DomainResult.Success(Unit)
        override val isLoggedIn: Flow<Boolean> = loginFlow
    }

    private fun profileRepo(
        me: DomainResult<User> = DomainResult.Success(user),
        unread: DomainResult<Int> = DomainResult.Success(0),
    ) = object : ProfileRepository {
        override suspend fun getMe() = me
        override suspend fun updateNickname(nickname: String?) = DomainResult.Success(user)
        override suspend fun notifications(cursor: String?, limit: Int) =
            DomainResult.Success(cn.edu.bit.bitmart.core.domain.model.NotificationPage(emptyList(), null))
        override suspend fun markNotificationRead(id: Long) = DomainResult.Success(Unit)
        override suspend fun unreadNotificationCount() = unread
    }

    @Test
    fun `logged in loads me and exposes user`() = runTest {
        val vm = ProfileViewModel(authRepo(loggedIn = true), profileRepo())
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.loggedIn)
        assertEquals(7L, vm.state.value.user?.id)
        assertEquals("1120201234", vm.state.value.user?.studentId)
    }

    @Test
    fun `logged out exposes no user and does not error`() = runTest {
        val vm = ProfileViewModel(authRepo(loggedIn = false), profileRepo())
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.loggedIn)
        assertNull(vm.state.value.user)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `me failure surfaces message while logged in`() = runTest {
        val vm = ProfileViewModel(
            authRepo(loggedIn = true),
            profileRepo(DomainResult.Failure("X", "拉取失败", 500)),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.loggedIn)
        assertNull(vm.state.value.user)
        assertEquals("拉取失败", vm.state.value.error)
    }

    @Test
    fun `me network error surfaces prefixed message`() = runTest {
        val vm = ProfileViewModel(
            authRepo(loggedIn = true),
            profileRepo(DomainResult.NetworkError("超时")),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("网络异常：超时", vm.state.value.error)
        assertNull(vm.state.value.user)
    }

    @Test
    fun `logout transition clears user reactively`() = runTest {
        // 初始已登录 → 翻转为未登录，验证响应式清空用户信息。
        val loginFlow = MutableStateFlow(true)
        val vm = ProfileViewModel(authRepo(loginFlow), profileRepo())
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.loggedIn)
        assertEquals(7L, vm.state.value.user?.id)

        loginFlow.value = false
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.loggedIn)
        assertNull(vm.state.value.user)
    }

    @Test
    fun `logged in loads unread count for badge`() = runTest {
        val vm = ProfileViewModel(authRepo(loggedIn = true), profileRepo(unread = DomainResult.Success(5)))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(5, vm.state.value.unreadCount)
    }

    @Test
    fun `unread count fetch failure keeps previous value silently`() = runTest {
        val vm = ProfileViewModel(
            authRepo(loggedIn = true),
            profileRepo(unread = DomainResult.NetworkError("超时")),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, vm.state.value.unreadCount)
        assertNull(vm.state.value.error) // 角标失败不打扰主流程。
    }

    @Test
    fun `logout transition clears unread count`() = runTest {
        val loginFlow = MutableStateFlow(true)
        val vm = ProfileViewModel(authRepo(loginFlow), profileRepo(unread = DomainResult.Success(3)))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, vm.state.value.unreadCount)

        loginFlow.value = false
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, vm.state.value.unreadCount)
    }

    @Test
    fun `refreshUnreadCount is noop when logged out`() = runTest {
        val vm = ProfileViewModel(authRepo(loggedIn = false), profileRepo(unread = DomainResult.Success(9)))
        dispatcher.scheduler.advanceUntilIdle()
        vm.refreshUnreadCount()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, vm.state.value.unreadCount)
    }

    @Test
    fun `refresh re-fetches me and unread when logged in and clears refreshing`() = runTest {
        // 模拟网络不佳：首拉 /me 失败；恢复后下拉刷新重试成功。
        var meResult: DomainResult<User> = DomainResult.Failure("X", "首拉失败", 500)
        var unreadResult: DomainResult<Int> = DomainResult.Success(1)
        val repo = object : ProfileRepository {
            override suspend fun getMe() = meResult
            override suspend fun updateNickname(nickname: String?) = DomainResult.Success(user)
            override suspend fun notifications(cursor: String?, limit: Int) =
                DomainResult.Success(cn.edu.bit.bitmart.core.domain.model.NotificationPage(emptyList(), null))
            override suspend fun markNotificationRead(id: Long) = DomainResult.Success(Unit)
            override suspend fun unreadNotificationCount() = unreadResult
        }
        val vm = ProfileViewModel(authRepo(loggedIn = true), repo)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.state.value.user)
        assertEquals("首拉失败", vm.state.value.error)

        meResult = DomainResult.Success(user)
        unreadResult = DomainResult.Success(7)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(7L, vm.state.value.user?.id)
        assertEquals(7, vm.state.value.unreadCount)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `refresh is noop when logged out`() = runTest {
        val vm = ProfileViewModel(authRepo(loggedIn = false), profileRepo(unread = DomainResult.Success(9)))
        dispatcher.scheduler.advanceUntilIdle()
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.state.value.user)
        assertEquals(0, vm.state.value.unreadCount)
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `consumeError clears the error`() = runTest {
        val vm = ProfileViewModel(
            authRepo(loggedIn = true),
            profileRepo(DomainResult.Failure("X", "拉取失败", 500)),
        )
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("拉取失败", vm.state.value.error)
        // UI 以 Toast 展示后消费置空。
        vm.consumeError()
        assertNull(vm.state.value.error)
    }
}
