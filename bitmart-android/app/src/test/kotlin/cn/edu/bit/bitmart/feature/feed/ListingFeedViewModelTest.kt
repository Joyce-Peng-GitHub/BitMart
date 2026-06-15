package cn.edu.bit.bitmart.feature.feed

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingPage
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.NotificationPage
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import cn.edu.bit.bitmart.core.domain.repository.UpdateDraft
import cn.edu.bit.bitmart.core.ui.FilterState
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

class ListingFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private fun summary(id: Long, ownerId: Long = 1L, total: Int = 1, sold: Int = 0) = ListingSummary(
        id, ownerId, ListingType.SELL, ListingCategory.GENERAL, "商品$id", "10.00", total, sold, null, null, emptyList(), "2026-06-02T00:00:00Z", "2026-07-02T00:00:00Z",
    )

    private fun user(id: Long) = User(id, "212$id", "昵称$id", "昵称$id", "USER")

    /** 记录最近一次查询/更新/删除并按脚本返回页。listResult 非空时 list() 改为返回它（用于测错误路径）。 */
    private class FakeRepo(
        val pages: List<ListingPage>,
        val listResult: DomainResult<ListingPage>? = null,
        val updateResult: DomainResult<Unit> = DomainResult.Success(Unit),
        val deleteResult: DomainResult<Unit> = DomainResult.Success(Unit),
    ) : ListingRepository {
        var lastQuery: ListingQuery? = null
        var lastUpdate: Pair<Long, UpdateDraft>? = null
        var deletedId: Long? = null
        private var call = 0
        override suspend fun list(query: ListingQuery): DomainResult<ListingPage> {
            lastQuery = query
            return listResult ?: DomainResult.Success(pages[call++.coerceAtMost(pages.size - 1)])
        }
        override suspend fun myListings(query: ListingQuery) = DomainResult.Success(ListingPage(emptyList(), null))
        override suspend fun detail(id: Long) = DomainResult.Failure("X", "n/a", 404)
        override suspend fun publish(draft: cn.edu.bit.bitmart.core.domain.repository.PublishDraft) = DomainResult.Success(1L)
        override suspend fun publishBatch(drafts: List<cn.edu.bit.bitmart.core.domain.repository.PublishDraft>) = DomainResult.Success(listOf(1L))
        override suspend fun uploadImage(bytes: ByteArray, filename: String) = DomainResult.Success("blob-key")
        override suspend fun lookupBook(isbn: String) = DomainResult.Success(null)
        override suspend fun update(id: Long, update: UpdateDraft): DomainResult<Unit> {
            lastUpdate = id to update; return updateResult
        }
        override suspend fun delete(id: Long): DomainResult<Unit> { deletedId = id; return deleteResult }
        override suspend fun popularTags(limit: Int) = DomainResult.Success(emptyList<cn.edu.bit.bitmart.core.domain.repository.TagInfo>())
    }

    private class FakeProfileRepo(private val me: User?) : ProfileRepository {
        override suspend fun getMe(): DomainResult<User> =
            me?.let { DomainResult.Success(it) } ?: DomainResult.Failure("UNAUTH", "未登录", 401)
        override suspend fun updateNickname(nickname: String?) = getMe()
        override suspend fun notifications(cursor: String?, limit: Int) = DomainResult.Success(NotificationPage(emptyList(), null))
        override suspend fun markNotificationRead(id: Long) = DomainResult.Success(Unit)
        override suspend fun unreadNotificationCount() = DomainResult.Success(0)
    }

    private class FakeAuthRepo(loggedIn: Boolean, private val me: User? = null) : AuthRepository {
        override val isLoggedIn: Flow<Boolean> = flowOf(loggedIn)
        override suspend fun verify(studentId: String, password: String) = DomainResult.Success("ticket")
        override suspend fun register(verifyTicket: String, studentId: String, password: String, nickname: String?) =
            me?.let { DomainResult.Success(it) } ?: DomainResult.Failure("X", "n/a", 400)
        override suspend fun login(studentId: String, password: String) =
            me?.let { DomainResult.Success(it) } ?: DomainResult.Failure("X", "n/a", 400)
        override suspend fun resetPassword(verifyTicket: String, studentId: String, newPassword: String) = DomainResult.Success(Unit)
        override suspend fun logout() = DomainResult.Success(Unit)
        override suspend fun deleteAccount() = DomainResult.Success(Unit)
    }

    /** 构造 VM。默认未登录（currentUserId 解析为 null）。 */
    private fun feedVm(repo: ListingRepository, me: User? = null, loggedIn: Boolean = false) =
        ListingFeedViewModel(repo, FakeProfileRepo(me), FakeAuthRepo(loggedIn, me))

    @Test
    fun `refresh loads first page`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1), summary(2)), nextCursor = "c1")))
        val vm = feedVm(repo)
        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        assertEquals("c1", vm.state.value.nextCursor)
    }

    @Test
    fun `loadMore appends next page and uses cursor`() = runTest {
        val repo = FakeRepo(listOf(
            ListingPage(listOf(summary(1)), nextCursor = "c1"),
            ListingPage(listOf(summary(2)), nextCursor = null),
        ))
        val vm = feedVm(repo)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        vm.loadMore(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        assertEquals("c1", repo.lastQuery?.cursor)
        assertTrue(vm.state.value.endReached)
    }

    @Test
    fun `consumeError clears error so the same error can fire again`() = runTest {
        val repo = FakeRepo(emptyList(), listResult = DomainResult.NetworkError("断网"))
        val vm = feedVm(repo)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("网络异常：断网", vm.state.value.error)

        // UI 以 Toast 展示后消费置空。
        vm.consumeError()
        assertNull(vm.state.value.error)

        // 相同错误可再次置位（从而再次弹 Toast）。
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("网络异常：断网", vm.state.value.error)
    }

    @Test
    fun `pull refresh reloads first page and clears refreshing`() = runTest {
        val repo = FakeRepo(listOf(
            ListingPage(listOf(summary(1)), nextCursor = null),
            ListingPage(listOf(summary(2), summary(3)), nextCursor = null),
        ))
        val vm = feedVm(repo)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)

        // 下拉刷新（showSpinner=false）：重新拉首屏，结束后 refreshing/loading 均为 false。
        vm.refresh(showSpinner = false); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        assertEquals(false, vm.state.value.refreshing)
        assertEquals(false, vm.state.value.loading)
    }

    @Test
    fun `applySearch sets trimmed query and refreshes`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = feedVm(repo)
        vm.applySearch("  线性代数  ")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("线性代数", vm.state.value.query)
        assertEquals("线性代数", repo.lastQuery?.text)
    }

    @Test
    fun `applySearch with blank clears the text filter`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = feedVm(repo)
        vm.applySearch("书"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("书", repo.lastQuery?.text)
        // 清空：空串 → toQuery 用 null（无文字筛选）。
        vm.applySearch("   "); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("", vm.state.value.query)
        assertNull(repo.lastQuery?.text)
    }

    @Test
    fun `setType switches type and reloads`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = feedVm(repo)
        vm.setType(ListingType.BUY)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ListingType.BUY, vm.state.value.type)
        assertEquals(ListingType.BUY, repo.lastQuery?.type)
    }

    @Test
    fun `applyFilter wires price and flags into query and refreshes`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = feedVm(repo)
        vm.applyFilter(FilterState(minPrice = "10", maxPrice = "50", includeNoPrice = false, includeSold = true, selectedTagIds = listOf(1L, 2L)))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("10", vm.state.value.minPrice)
        assertEquals("50", vm.state.value.maxPrice)
        assertEquals(false, vm.state.value.includeNoPrice)
        assertTrue(vm.state.value.includeSold)
        assertEquals(listOf(1L, 2L), vm.state.value.selectedTagIds)
        assertEquals("10", repo.lastQuery?.minPrice)
        assertEquals("50", repo.lastQuery?.maxPrice)
        assertEquals(false, repo.lastQuery?.includeNoPrice)
        assertEquals(true, repo.lastQuery?.includeSold)
        // 公开列表恒不含过期项。
        assertEquals(false, repo.lastQuery?.includeExpired)
        assertEquals(listOf(1L, 2L), repo.lastQuery?.tagIds)
    }

    @Test
    fun `applyFilter never includes expired even if FilterState says so`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = feedVm(repo)
        // 即便 FilterState.includeExpired=true（公开页弹窗本不展示该开关），查询也不应包含过期项。
        vm.applyFilter(FilterState(includeExpired = true))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, repo.lastQuery?.includeExpired)
    }

    @Test
    fun `blank price filters map to null in query`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = feedVm(repo)
        vm.applyFilter(FilterState(minPrice = "  ", maxPrice = "", includeNoPrice = true, includeSold = false, selectedTagIds = emptyList()))
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(repo.lastQuery?.minPrice)
        assertNull(repo.lastQuery?.maxPrice)
    }

    @Test
    fun `clearFilter resets filter state and reloads with defaults`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = feedVm(repo)
        vm.applyFilter(FilterState(minPrice = "10", maxPrice = "50", includeNoPrice = false, includeSold = true, selectedTagIds = listOf(1L)))
        dispatcher.scheduler.advanceUntilIdle()
        vm.clearFilter()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("", vm.state.value.minPrice)
        assertEquals("", vm.state.value.maxPrice)
        assertTrue(vm.state.value.includeNoPrice)
        assertEquals(false, vm.state.value.includeSold)
        assertTrue(vm.state.value.selectedTagIds.isEmpty())
        assertNull(repo.lastQuery?.minPrice)
        assertEquals(true, repo.lastQuery?.includeNoPrice)
        assertEquals(false, repo.lastQuery?.includeSold)
    }

    // —— 本人项识别与左滑操作 ——

    @Test
    fun `currentUserId resolves from getMe when logged in`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1, ownerId = 7)), null)))
        val vm = feedVm(repo, me = user(7), loggedIn = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(7L, vm.state.value.currentUserId)
    }

    @Test
    fun `currentUserId stays null when logged out`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1, ownerId = 7)), null)))
        // 未登录：即便有用户也不解析（isLoggedIn=false）。
        val vm = feedVm(repo, me = user(7), loggedIn = false)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.state.value.currentUserId)
    }

    @Test
    fun `currentUserId resolves on a later refresh after an initial getMe failure`() = runTest {
        // 首启 getMe 失败一次（模拟离线），之后成功；联网后下拉刷新应补解析本人 id。
        val profile = object : ProfileRepository {
            var calls = 0
            override suspend fun getMe(): DomainResult<User> =
                if (calls++ == 0) DomainResult.NetworkError("断网") else DomainResult.Success(user(9))
            override suspend fun updateNickname(nickname: String?) = getMe()
            override suspend fun notifications(cursor: String?, limit: Int) = DomainResult.Success(NotificationPage(emptyList(), null))
            override suspend fun markNotificationRead(id: Long) = DomainResult.Success(Unit)
            override suspend fun unreadNotificationCount() = DomainResult.Success(0)
        }
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1, ownerId = 9)), null)))
        val vm = ListingFeedViewModel(repo, profile, FakeAuthRepo(loggedIn = true, me = user(9)))
        dispatcher.scheduler.advanceUntilIdle()
        // init 的首次 getMe 失败 → 仍为 null。
        assertNull(vm.state.value.currentUserId)
        // 再次 refresh（如联网后下拉）→ 补解析成功。
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(9L, vm.state.value.currentUserId)
    }

    @Test
    fun `delete removes own item locally and calls repo`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1, ownerId = 7), summary(2, ownerId = 7)), null)))
        val vm = feedVm(repo, me = user(7), loggedIn = true)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        vm.delete(1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1L, repo.deletedId)
        assertEquals(listOf(2L), vm.state.value.items.map { it.id })
    }

    @Test
    fun `adjustSold updates own item locally and calls repo`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1, ownerId = 7, total = 5, sold = 0)), null)))
        val vm = feedVm(repo, me = user(7), loggedIn = true)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        vm.adjustSold(1, 3); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1L, repo.lastUpdate?.first)
        assertEquals(3, repo.lastUpdate?.second?.quantitySold)
        assertEquals(3, vm.state.value.items.first { it.id == 1L }.quantitySold)
        assertNull(vm.state.value.adjustingId)
    }

    @Test
    fun `delete failure surfaces error and keeps item`() = runTest {
        val repo = FakeRepo(
            listOf(ListingPage(listOf(summary(1, ownerId = 7), summary(2, ownerId = 7)), null)),
            deleteResult = DomainResult.Failure("FORBIDDEN", "无权删除", 403),
        )
        val vm = feedVm(repo, me = user(7), loggedIn = true)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        vm.delete(1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("删除失败：无权删除", vm.state.value.error)
        assertEquals(listOf(1L, 2L), vm.state.value.items.map { it.id })
    }
}
