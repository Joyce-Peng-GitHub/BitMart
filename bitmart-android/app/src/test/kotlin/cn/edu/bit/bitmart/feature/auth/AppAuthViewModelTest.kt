package cn.edu.bit.bitmart.feature.auth

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [AppAuthViewModel] 应忠实镜像 [AuthRepository.isLoggedIn]，并以 Eagerly 共享使 `.value` 可同步读取，
 * 供导航层在点击“发布”时即时判断登录态。
 */
class AppAuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private val user = User(1, "1120201234", null, "匿名", "NORMAL")

    /** 仅 [isLoggedIn] 参与本测试；其余 suspend 方法返回成功占位。 */
    private inner class FakeAuthRepo(override val isLoggedIn: Flow<Boolean>) : AuthRepository {
        override suspend fun verify(studentId: String, password: String) = DomainResult.Success("ticket")
        override suspend fun register(verifyTicket: String, studentId: String, password: String, nickname: String?) =
            DomainResult.Success(user)
        override suspend fun login(studentId: String, password: String) = DomainResult.Success(user)
        override suspend fun resetPassword(verifyTicket: String, studentId: String, newPassword: String) = DomainResult.Success(Unit)
        override suspend fun logout() = DomainResult.Success(Unit)
        override suspend fun deleteAccount() = DomainResult.Success(Unit)
    }

    @Test
    fun `mirrors logged-in true from repository`() = runTest {
        val vm = AppAuthViewModel(FakeAuthRepo(flowOf(true)))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.isLoggedIn.value)
    }

    @Test
    fun `mirrors logged-in false from repository`() = runTest {
        val vm = AppAuthViewModel(FakeAuthRepo(flowOf(false)))
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.isLoggedIn.value)
    }

    @Test
    fun `reflects login state transitions from the repository`() = runTest {
        val source = MutableStateFlow(false)
        val vm = AppAuthViewModel(FakeAuthRepo(source))
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.isLoggedIn.value)

        source.value = true
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.isLoggedIn.value)
    }
}
