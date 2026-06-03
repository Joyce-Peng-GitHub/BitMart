package cn.edu.bit.bitmart.feature.settings

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.NotificationPage
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private val user = User(7, "1120201234", "小明", "小明", "NORMAL")

    /** 记录调用顺序的可配置认证仓储。 */
    private class FakeAuthRepo(
        val loggedIn: Boolean = true,
        val verifyResult: DomainResult<String> = DomainResult.Success("ticket-1"),
        val resetResult: DomainResult<Unit> = DomainResult.Success(Unit),
        val logoutResult: DomainResult<Unit> = DomainResult.Success(Unit),
        val deleteResult: DomainResult<Unit> = DomainResult.Success(Unit),
    ) : AuthRepository {
        val calls = mutableListOf<String>()
        var verifyArgs: Triple<String, String, String>? = null
        override suspend fun verify(studentId: String, password: String): DomainResult<String> {
            calls += "verify"; verifyArgs = Triple(studentId, password, ""); return verifyResult
        }
        override suspend fun register(verifyTicket: String, studentId: String, password: String, nickname: String?) =
            DomainResult.Success(User(1, studentId, nickname, "x", "NORMAL"))
        override suspend fun login(studentId: String, password: String) = DomainResult.Success(User(1, studentId, null, "x", "NORMAL"))
        override suspend fun resetPassword(verifyTicket: String, studentId: String, newPassword: String): DomainResult<Unit> {
            calls += "reset:$verifyTicket"; return resetResult
        }
        override suspend fun logout(): DomainResult<Unit> { calls += "logout"; return logoutResult }
        override suspend fun deleteAccount(): DomainResult<Unit> { calls += "delete"; return deleteResult }
        override val isLoggedIn: Flow<Boolean> = flowOf(loggedIn)
    }

    private fun profileRepo(
        me: DomainResult<User> = DomainResult.Success(user),
        update: DomainResult<User> = DomainResult.Success(user.copy(nickname = "新昵称")),
    ) = object : ProfileRepository {
        var lastNickname: String? = "<unset>"
        override suspend fun getMe() = me
        override suspend fun updateNickname(nickname: String?): DomainResult<User> { lastNickname = nickname; return update }
        override suspend fun notifications(cursor: String?, limit: Int) = DomainResult.Success(NotificationPage(emptyList(), null))
        override suspend fun markNotificationRead(id: Long) = DomainResult.Success(Unit)
    }

    @Test
    fun `init loads me when logged in`() = runTest {
        val vm = AccountSettingsViewModel(FakeAuthRepo(), profileRepo())
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(7L, vm.state.value.user?.id)
        assertTrue(vm.state.value.loggedIn)
    }

    @Test
    fun `updateNickname success sets message and user`() = runTest {
        val vm = AccountSettingsViewModel(FakeAuthRepo(), profileRepo())
        dispatcher.scheduler.advanceUntilIdle()
        vm.updateNickname("新昵称")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("新昵称", vm.state.value.user?.nickname)
        assertEquals("昵称已更新", vm.state.value.message)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `updateNickname blank passes null to repo`() = runTest {
        val repo = profileRepo()
        val vm = AccountSettingsViewModel(FakeAuthRepo(), repo)
        dispatcher.scheduler.advanceUntilIdle()
        vm.updateNickname("   ")
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(repo.lastNickname)
    }

    @Test
    fun `updateNickname failure surfaces error`() = runTest {
        val vm = AccountSettingsViewModel(FakeAuthRepo(), profileRepo(update = DomainResult.Failure("X", "昵称重复", 409)))
        dispatcher.scheduler.advanceUntilIdle()
        vm.updateNickname("dup")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("昵称重复", vm.state.value.error)
    }

    @Test
    fun `changePassword orchestrates verify then reset`() = runTest {
        val auth = FakeAuthRepo()
        val vm = AccountSettingsViewModel(auth, profileRepo())
        dispatcher.scheduler.advanceUntilIdle()
        vm.changePassword("1120201234", "unifiedPwd", "newPwd")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("verify", "reset:ticket-1"), auth.calls)
        assertEquals("密码已修改", vm.state.value.message)
    }

    @Test
    fun `changePassword stops when verify fails`() = runTest {
        val auth = FakeAuthRepo(verifyResult = DomainResult.Failure("UNAUTHORIZED", "认证失败", 401))
        val vm = AccountSettingsViewModel(auth, profileRepo())
        dispatcher.scheduler.advanceUntilIdle()
        vm.changePassword("1120201234", "wrong", "newPwd")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("verify"), auth.calls) // 未调用 reset
        assertEquals("认证失败", vm.state.value.error)
    }

    @Test
    fun `changePassword validates blank fields`() = runTest {
        val auth = FakeAuthRepo()
        val vm = AccountSettingsViewModel(auth, profileRepo())
        dispatcher.scheduler.advanceUntilIdle()
        vm.changePassword("", "", "")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(auth.calls.isEmpty())
        assertTrue(vm.state.value.error!!.isNotBlank())
    }

    @Test
    fun `logout sets loggedOut on success`() = runTest {
        val vm = AccountSettingsViewModel(FakeAuthRepo(), profileRepo())
        dispatcher.scheduler.advanceUntilIdle()
        vm.logout()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.loggedOut)
    }

    @Test
    fun `deleteAccount sets loggedOut on success`() = runTest {
        val vm = AccountSettingsViewModel(FakeAuthRepo(), profileRepo())
        dispatcher.scheduler.advanceUntilIdle()
        vm.deleteAccount()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.loggedOut)
    }

    @Test
    fun `deleteAccount failure surfaces error and stays logged in`() = runTest {
        val vm = AccountSettingsViewModel(
            FakeAuthRepo(deleteResult = DomainResult.Failure("X", "注销失败", 500)),
            profileRepo(),
        )
        dispatcher.scheduler.advanceUntilIdle()
        vm.deleteAccount()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("注销失败", vm.state.value.error)
        assertTrue(!vm.state.value.loggedOut)
    }
}
