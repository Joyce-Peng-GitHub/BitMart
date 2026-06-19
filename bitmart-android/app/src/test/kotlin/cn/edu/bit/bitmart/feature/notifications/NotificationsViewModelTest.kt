package cn.edu.bit.bitmart.feature.notifications

import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.Notification
import cn.edu.bit.bitmart.core.domain.model.NotificationPage
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import cn.edu.bit.bitmart.core.ui.UiText
import kotlinx.coroutines.Dispatchers
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

class NotificationsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private fun notif(id: Long, read: Boolean = false, announcement: Boolean = true) =
        Notification(id, 0, "标题$id", "内容$id", null, read, "2026-06-02T00:00:00Z", announcement)

    /** 可配置的通知仓储：按页返回结果并记录 markRead 调用。 */
    private class FakeProfileRepo(
        val pages: List<DomainResult<NotificationPage>>,
        val markResult: DomainResult<Unit> = DomainResult.Success(Unit),
    ) : ProfileRepository {
        var pageIndex = 0
        var marked: Long? = null
        override suspend fun getMe(): DomainResult<User> = DomainResult.Failure("X", "n/a", 400)
        override suspend fun updateNickname(nickname: String?): DomainResult<User> = DomainResult.Failure("X", "n/a", 400)
        override suspend fun notifications(cursor: String?, limit: Int): DomainResult<NotificationPage> =
            pages[pageIndex.coerceAtMost(pages.size - 1)].also { pageIndex++ }
        override suspend fun markNotificationRead(id: Long): DomainResult<Unit> {
            marked = id; return markResult
        }
        override suspend fun unreadNotificationCount(): DomainResult<Int> = DomainResult.Success(0)
    }

    @Test
    fun `refresh loads first page`() = runTest {
        val repo = FakeProfileRepo(listOf(DomainResult.Success(NotificationPage(listOf(notif(1), notif(2)), "c|2"))))
        val vm = NotificationsViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        assertEquals("c|2", vm.state.value.nextCursor)
        assertTrue(vm.state.value.canLoadMore)
    }

    @Test
    fun `loadMore appends next page and clears cursor at end`() = runTest {
        val repo = FakeProfileRepo(
            listOf(
                DomainResult.Success(NotificationPage(listOf(notif(1)), "c|1")),
                DomainResult.Success(NotificationPage(listOf(notif(2)), null)),
            ),
        )
        val vm = NotificationsViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)

        vm.loadMore()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        assertNull(vm.state.value.nextCursor)
        assertFalse(vm.state.value.canLoadMore)
    }

    @Test
    fun `loadMore noop when no cursor`() = runTest {
        val repo = FakeProfileRepo(listOf(DomainResult.Success(NotificationPage(listOf(notif(1)), null))))
        val vm = NotificationsViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.loadMore()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.pageIndex) // 仅首屏拉取一次
    }

    @Test
    fun `markRead flips read state on success`() = runTest {
        val repo = FakeProfileRepo(listOf(DomainResult.Success(NotificationPage(listOf(notif(1, read = false)), null))))
        val vm = NotificationsViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.markRead(1)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1L, repo.marked)
        assertTrue(vm.state.value.items.first().read)
    }

    @Test
    fun `markRead skips already-read item`() = runTest {
        val repo = FakeProfileRepo(listOf(DomainResult.Success(NotificationPage(listOf(notif(1, read = true)), null))))
        val vm = NotificationsViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()

        vm.markRead(1)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(repo.marked) // 未发起请求
    }

    @Test
    fun `refresh failure surfaces localized error`() = runTest {
        val repo = FakeProfileRepo(listOf(DomainResult.Failure("VALIDATION_FAILED", "加载失败", 500)))
        val vm = NotificationsViewModel(repo)
        dispatcher.scheduler.advanceUntilIdle()
        // 错误不再透传服务端原始中文，而是按 error code 映射为本地化资源。
        assertEquals(UiText.Res(R.string.error_validation_failed), vm.state.value.error)
        assertTrue(vm.state.value.items.isEmpty())
    }
}
