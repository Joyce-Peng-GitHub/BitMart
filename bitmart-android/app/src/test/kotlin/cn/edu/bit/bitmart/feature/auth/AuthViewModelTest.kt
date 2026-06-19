package cn.edu.bit.bitmart.feature.auth

import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import cn.edu.bit.bitmart.core.ui.UiText
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
        assertEquals(UiText.Res(R.string.auth_fill_credentials), vm.state.value.error)
    }

    @Test
    fun `login failure with 401 shows invalid-credentials message`() = runTest {
        val vm = AuthViewModel(repo(loginResult = DomainResult.Failure("UNAUTHORIZED", "学号或密码错误", 401)))
        vm.onStudentIdChange("a"); vm.onPasswordChange("b")
        vm.login()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(UiText.Res(R.string.auth_error_invalid_credentials), vm.state.value.error)
        assertFalse(vm.state.value.loggedIn)
    }

    @Test
    fun `login failure with non-401 falls back to code mapping`() = runTest {
        val vm = AuthViewModel(repo(loginResult = DomainResult.Failure("FORBIDDEN", "账号已被封禁", 403)))
        vm.onStudentIdChange("a"); vm.onPasswordChange("b")
        vm.login()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(UiText.Res(R.string.error_forbidden), vm.state.value.error)
        assertFalse(vm.state.value.loggedIn)
    }

    @Test
    fun `register orchestrates verify then register`() = runTest {
        val vm = AuthViewModel(repo())
        vm.onStudentIdChange("1120201234"); vm.onPasswordChange("Secret123")
        vm.register(unifiedPassword = "unifiedPw", confirmPassword = "Secret123")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.loggedIn)
    }

    @Test
    fun `register stops when verify fails with 401 verify-failed message`() = runTest {
        val vm = AuthViewModel(repo(verifyResult = DomainResult.Failure("UNAUTHORIZED", "统一认证失败", 401)))
        vm.onStudentIdChange("1120201234"); vm.onPasswordChange("Secret123")
        vm.register(unifiedPassword = "wrong", confirmPassword = "Secret123")
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.loggedIn)
        assertEquals(UiText.Res(R.string.auth_error_verify_failed), vm.state.value.error)
    }

    @Test
    fun `register with 401 invalid ticket shows verify-failed message`() = runTest {
        // verify succeeds, but the register step rejects the ticket (expired/invalid) with 401.
        val vm = AuthViewModel(
            repo(registerResult = DomainResult.Failure("UNAUTHORIZED", "验证票无效或已过期，请重新验证身份", 401)),
        )
        vm.onStudentIdChange("1120201234"); vm.onPasswordChange("Secret123")
        vm.register(unifiedPassword = "unifiedPw", confirmPassword = "Secret123")
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.loggedIn)
        assertEquals(UiText.Res(R.string.auth_error_verify_failed), vm.state.value.error)
    }

    @Test
    fun `register with non-401 conflict falls back to code mapping`() = runTest {
        // Already-registered student is a CONFLICT (409), not a 401 → keep generic code mapping.
        val vm = AuthViewModel(
            repo(registerResult = DomainResult.Failure("CONFLICT", "该学号已注册", 409)),
        )
        vm.onStudentIdChange("1120201234"); vm.onPasswordChange("Secret123")
        vm.register(unifiedPassword = "unifiedPw", confirmPassword = "Secret123")
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.loggedIn)
        assertEquals(UiText.Res(R.string.error_conflict), vm.state.value.error)
    }

    @Test
    fun `register with non-401 password policy violation falls back to code mapping`() = runTest {
        // Weak password is VALIDATION_FAILED (400), not a 401 → keep generic code mapping.
        val vm = AuthViewModel(
            repo(registerResult = DomainResult.Failure("VALIDATION_FAILED", "密码强度不足", 400)),
        )
        vm.onStudentIdChange("1120201234"); vm.onPasswordChange("Secret123")
        vm.register(unifiedPassword = "unifiedPw", confirmPassword = "Secret123")
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.loggedIn)
        assertEquals(UiText.Res(R.string.error_validation_failed), vm.state.value.error)
    }

    @Test
    fun `register with mismatched confirm password shows error and does not call repo`() = runTest {
        val vm = AuthViewModel(repo(verifyResult = DomainResult.Failure("X", "should not be called", 400)))
        vm.onStudentIdChange("1120201234"); vm.onPasswordChange("Secret123")
        vm.register(unifiedPassword = "unifiedPw", confirmPassword = "Mismatch")
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.loggedIn)
        assertEquals(UiText.Res(R.string.auth_error_password_mismatch), vm.state.value.error)
    }
}
