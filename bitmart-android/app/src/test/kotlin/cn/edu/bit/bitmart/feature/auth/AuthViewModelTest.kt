package cn.edu.bit.bitmart.feature.auth

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private val user = User(1, "1120201234", null, "匿名", "NORMAL")

    private fun repo(
        loginResult: DomainResult<User> = DomainResult.Success(user),
        verifyResult: DomainResult<String> = DomainResult.Success("ticket"),
        registerResult: DomainResult<User> = DomainResult.Success(user),
    ) = object : AuthRepository {
        override suspend fun verify(studentId: String, password: String) = verifyResult
        override suspend fun register(verifyTicket: String, studentId: String, password: String, nickname: String?) = registerResult
        override suspend fun login(studentId: String, password: String) = loginResult
        override suspend fun resetPassword(verifyTicket: String, studentId: String, newPassword: String) = DomainResult.Success(Unit)
        override suspend fun logout() = DomainResult.Success(Unit)
        override suspend fun deleteAccount() = DomainResult.Success(Unit)
        override val isLoggedIn: Flow<Boolean> = flowOf(false)
    }

    @Test
    fun `login success sets loggedIn`() = runTest {
        val vm = AuthViewModel(repo())
        vm.onStudentIdChange("1120201234"); vm.onPasswordChange("Secret123")
        vm.login()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.loggedIn)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `login with blank fields shows error and does not call repo`() = runTest {
        val vm = AuthViewModel(repo(loginResult = DomainResult.Failure("X", "should not be called", 400)))
        vm.login()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.loggedIn)
        assertEquals("请填写学号和密码", vm.state.value.error)
    }

    @Test
    fun `login failure surfaces message`() = runTest {
        val vm = AuthViewModel(repo(loginResult = DomainResult.Failure("UNAUTHORIZED", "学号或密码错误", 401)))
        vm.onStudentIdChange("a"); vm.onPasswordChange("b")
        vm.login()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("学号或密码错误", vm.state.value.error)
        assertFalse(vm.state.value.loggedIn)
    }

    @Test
    fun `register orchestrates verify then register`() = runTest {
        val vm = AuthViewModel(repo())
        vm.onStudentIdChange("1120201234"); vm.onPasswordChange("Secret123")
        vm.register(unifiedPassword = "unifiedPw")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.loggedIn)
    }

    @Test
    fun `register stops when verify fails`() = runTest {
        val vm = AuthViewModel(repo(verifyResult = DomainResult.Failure("UNAUTHORIZED", "统一认证失败", 401)))
        vm.onStudentIdChange("1120201234"); vm.onPasswordChange("Secret123")
        vm.register(unifiedPassword = "wrong")
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.loggedIn)
        assertEquals("统一认证失败", vm.state.value.error)
    }
}
