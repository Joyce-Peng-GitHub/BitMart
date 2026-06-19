package cn.edu.bit.bitmart.feature.listing

import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingPage
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.NotificationPage
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.AuthRepository
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import cn.edu.bit.bitmart.core.domain.repository.PublishDraft
import cn.edu.bit.bitmart.core.domain.repository.TagInfo
import cn.edu.bit.bitmart.core.domain.repository.UpdateDraft
import cn.edu.bit.bitmart.core.ui.FilterState
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 统一列表 ViewModel 测试，覆盖两种来源（[ListingScope.PUBLIC] 公开买卖列表 /
 * [ListingScope.MINE] 我的商品/收购）。两者共用大部分逻辑，差异路径单独断言。
 */
class ListingListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private fun summary(id: Long, ownerId: Long = 1L, total: Int = 1, sold: Int = 0) = ListingSummary(
        id, ownerId, ListingType.SELL, ListingCategory.GENERAL, "商品$id", "10.00", total, sold, null, null, emptyList(), "2026-06-02T00:00:00Z", "2026-07-02T00:00:00Z",
    )

    private fun user(id: Long) = User(id, "212$id", "昵称$id", "昵称$id", "USER")

    /**
     * 记录公开/本人查询与 update/delete 调用并按脚本返回页。
     * listResult 非空时 list() 改为返回它（测公开列表错误路径）；myResult 同理用于本人列表。
     */
    private class FakeRepo(
        val pages: List<ListingPage>,
        val listResult: DomainResult<ListingPage>? = null,
        val myResult: DomainResult<ListingPage>? = null,
        val updateResult: DomainResult<Unit> = DomainResult.Success(Unit),
        val deleteResult: DomainResult<Unit> = DomainResult.Success(Unit),
    ) : ListingRepository {
        var lastQuery: ListingQuery? = null
        var lastMyQuery: ListingQuery? = null
        var lastUpdate: Pair<Long, UpdateDraft>? = null
        var deletedId: Long? = null
        private var call = 0
        private var myCall = 0
        override suspend fun list(query: ListingQuery): DomainResult<ListingPage> {
            lastQuery = query
            return listResult ?: DomainResult.Success(pages[call++.coerceAtMost(pages.size - 1)])
        }
        override suspend fun myListings(query: ListingQuery): DomainResult<ListingPage> {
            lastMyQuery = query
            return myResult ?: DomainResult.Success(pages[myCall++.coerceAtMost(pages.size - 1)])
        }
        override suspend fun detail(id: Long) = DomainResult.Failure("X", "n/a", 404)
        override suspend fun publish(draft: PublishDraft) = DomainResult.Success(1L)
        override suspend fun publishBatch(drafts: List<PublishDraft>) = DomainResult.Success(listOf(1L))
        override suspend fun uploadImage(bytes: ByteArray, filename: String) = DomainResult.Success("blob-key")
        override suspend fun lookupBook(isbn: String) = DomainResult.Success(null)
        override suspend fun update(id: Long, update: UpdateDraft): DomainResult<Unit> {
            lastUpdate = id to update; return updateResult
        }
        override suspend fun delete(id: Long): DomainResult<Unit> { deletedId = id; return deleteResult }
        override suspend fun popularTags(limit: Int) = DomainResult.Success(emptyList<TagInfo>())
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

    /** 构造公开列表 VM。默认未登录（currentUserId 解析为 null）。 */
    private fun publicVm(repo: ListingRepository, me: User? = null, loggedIn: Boolean = false) =
        ListingListViewModel(repo, FakeProfileRepo(me), FakeAuthRepo(loggedIn, me), ListingScope.PUBLIC)

    /** 构造"我的列表" VM。MINE 不解析本人 id，profile/auth 仅占位。 */
    private fun mineVm(repo: ListingRepository) =
        ListingListViewModel(repo, FakeProfileRepo(null), FakeAuthRepo(loggedIn = true), ListingScope.MINE)

    // —— 公开列表（PUBLIC） ——

    @Test
    fun `public refresh loads first page`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1), summary(2)), nextCursor = "c1")))
        val vm = publicVm(repo)
        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        assertEquals("c1", vm.state.value.nextCursor)
    }

    @Test
    fun `public loadMore appends next page and uses cursor`() = runTest {
        val repo = FakeRepo(listOf(
            ListingPage(listOf(summary(1)), nextCursor = "c1"),
            ListingPage(listOf(summary(2)), nextCursor = null),
        ))
        val vm = publicVm(repo)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        vm.loadMore(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        assertEquals("c1", repo.lastQuery?.cursor)
        assertTrue(vm.state.value.endReached)
    }

    @Test
    fun `consumeError clears error so the same error can fire again`() = runTest {
        val repo = FakeRepo(emptyList(), listResult = DomainResult.NetworkError("断网"))
        val vm = publicVm(repo)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(UiText.Res(R.string.error_network), vm.state.value.error)
        vm.consumeError()
        assertNull(vm.state.value.error)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(UiText.Res(R.string.error_network), vm.state.value.error)
    }

    @Test
    fun `public pull refresh reloads first page and clears refreshing`() = runTest {
        val repo = FakeRepo(listOf(
            ListingPage(listOf(summary(1)), nextCursor = null),
            ListingPage(listOf(summary(2), summary(3)), nextCursor = null),
        ))
        val vm = publicVm(repo)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)
        vm.refresh(showSpinner = false); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        assertEquals(false, vm.state.value.refreshing)
        assertEquals(false, vm.state.value.loading)
    }

    @Test
    fun `applySearch sets trimmed query and refreshes`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = publicVm(repo)
        vm.applySearch("  线性代数  ")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("线性代数", vm.state.value.query)
        assertEquals("线性代数", repo.lastQuery?.text)
    }

    @Test
    fun `applySearch with blank clears the text filter`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = publicVm(repo)
        vm.applySearch("书"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("书", repo.lastQuery?.text)
        vm.applySearch("   "); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("", vm.state.value.query)
        assertNull(repo.lastQuery?.text)
    }

    @Test
    fun `public setType switches type and reloads`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = publicVm(repo)
        vm.setType(ListingType.BUY)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ListingType.BUY, vm.state.value.type)
        assertEquals(ListingType.BUY, repo.lastQuery?.type)
    }

    @Test
    fun `public setType reloads even for same type (shared across tabs)`() = runTest {
        // PUBLIC 买/卖共用一个实例：即便类型相同也重新加载（与 MINE 的跳过行为相反）。
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1)), null)))
        val vm = publicVm(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        repo.lastQuery = null
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ListingType.SELL, repo.lastQuery?.type)
    }

    @Test
    fun `applyFilter wires price and flags into query and refreshes`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = publicVm(repo)
        vm.applyFilter(FilterState(minPrice = "10", maxPrice = "50", includeNoPrice = false, includeSold = true, selectedTags = listOf("教材", "考研")))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("10", vm.state.value.minPrice)
        assertEquals("50", vm.state.value.maxPrice)
        assertEquals(false, vm.state.value.includeNoPrice)
        assertTrue(vm.state.value.includeSold)
        assertEquals(listOf("教材", "考研"), vm.state.value.selectedTags)
        assertEquals("10", repo.lastQuery?.minPrice)
        assertEquals("50", repo.lastQuery?.maxPrice)
        assertEquals(false, repo.lastQuery?.includeNoPrice)
        assertEquals(true, repo.lastQuery?.includeSold)
        assertEquals(false, repo.lastQuery?.includeExpired)
        assertEquals(listOf("教材", "考研"), repo.lastQuery?.tagNames)
    }

    @Test
    fun `public applyFilter never includes expired even if FilterState says so`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = publicVm(repo)
        vm.applyFilter(FilterState(includeExpired = true))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, repo.lastQuery?.includeExpired)
    }

    @Test
    fun `blank price filters map to null in query`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = publicVm(repo)
        vm.applyFilter(FilterState(minPrice = "  ", maxPrice = "", includeNoPrice = true, includeSold = false, selectedTags = emptyList()))
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(repo.lastQuery?.minPrice)
        assertNull(repo.lastQuery?.maxPrice)
    }

    @Test
    fun `public clearFilter resets filter state and reloads with defaults`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = publicVm(repo)
        vm.applyFilter(FilterState(minPrice = "10", maxPrice = "50", includeNoPrice = false, includeSold = true, selectedTags = listOf("教材")))
        dispatcher.scheduler.advanceUntilIdle()
        vm.clearFilter()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("", vm.state.value.minPrice)
        assertEquals("", vm.state.value.maxPrice)
        assertTrue(vm.state.value.includeNoPrice)
        assertEquals(false, vm.state.value.includeSold)
        assertTrue(vm.state.value.selectedTags.isEmpty())
        assertNull(repo.lastQuery?.minPrice)
        assertEquals(true, repo.lastQuery?.includeNoPrice)
        assertEquals(false, repo.lastQuery?.includeSold)
    }

    // —— 本人项识别与左滑操作（PUBLIC） ——

    @Test
    fun `currentUserId resolves from getMe when logged in`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1, ownerId = 7)), null)))
        val vm = publicVm(repo, me = user(7), loggedIn = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(7L, vm.state.value.currentUserId)
    }

    @Test
    fun `currentUserId stays null when logged out`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1, ownerId = 7)), null)))
        val vm = publicVm(repo, me = user(7), loggedIn = false)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.state.value.currentUserId)
    }

    @Test
    fun `currentUserId resolves on a later refresh after an initial getMe failure`() = runTest {
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
        val vm = ListingListViewModel(repo, profile, FakeAuthRepo(loggedIn = true, me = user(9)), ListingScope.PUBLIC)
        dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.state.value.currentUserId)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(9L, vm.state.value.currentUserId)
    }

    @Test
    fun `public delete removes own item locally and calls repo`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1, ownerId = 7), summary(2, ownerId = 7)), null)))
        val vm = publicVm(repo, me = user(7), loggedIn = true)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        vm.delete(1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1L, repo.deletedId)
        assertEquals(listOf(2L), vm.state.value.items.map { it.id })
    }

    @Test
    fun `public adjustSold updates own item locally and calls repo`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1, ownerId = 7, total = 5, sold = 0)), null)))
        val vm = publicVm(repo, me = user(7), loggedIn = true)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        vm.adjustSold(1, 3); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1L, repo.lastUpdate?.first)
        assertEquals(3, repo.lastUpdate?.second?.quantitySold)
        assertEquals(3, vm.state.value.items.first { it.id == 1L }.quantitySold)
        assertNull(vm.state.value.adjustingId)
    }

    @Test
    fun `public delete failure surfaces error and keeps item`() = runTest {
        val repo = FakeRepo(
            listOf(ListingPage(listOf(summary(1, ownerId = 7), summary(2, ownerId = 7)), null)),
            deleteResult = DomainResult.Failure("FORBIDDEN", "无权删除", 403),
        )
        val vm = publicVm(repo, me = user(7), loggedIn = true)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        vm.delete(1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(UiText.Res(R.string.error_forbidden), vm.state.value.error)
        assertEquals(listOf(1L, 2L), vm.state.value.items.map { it.id })
    }

    // —— 我的列表（MINE） ——

    @Test
    fun `mine setType loads own listings of given type`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1), summary(2)), nextCursor = "c1")))
        val vm = mineVm(repo)
        vm.setType(ListingType.BUY)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ListingType.BUY, vm.state.value.type)
        assertEquals(ListingType.BUY, repo.lastMyQuery?.type)
        assertEquals(2, vm.state.value.items.size)
        assertEquals("c1", vm.state.value.nextCursor)
    }

    @Test
    fun `mine defaults to including sold and expired`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = mineVm(repo)
        vm.setType(ListingType.SELL)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, repo.lastMyQuery?.includeSold)
        assertEquals(true, repo.lastMyQuery?.includeExpired)
    }

    @Test
    fun `mine setType skips reload for same type when already loaded`() = runTest {
        // MINE 为分离入口、各自独立实例：同类型且已有数据时不重复加载首屏。
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1)), null)))
        val vm = mineVm(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)
        repo.lastMyQuery = null
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        assertNull(repo.lastMyQuery)
    }

    @Test
    fun `mine applyFilter can hide expired and sold`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = mineVm(repo)
        vm.setType(ListingType.SELL)
        vm.applyFilter(FilterState(includeSold = false, includeExpired = false, selectedTags = listOf("二手")))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, vm.state.value.includeExpired)
        assertEquals(false, repo.lastMyQuery?.includeSold)
        assertEquals(false, repo.lastMyQuery?.includeExpired)
        assertEquals(listOf("二手"), repo.lastMyQuery?.tagNames)
    }

    @Test
    fun `mine clearFilter restores owner defaults (sold and expired on)`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = mineVm(repo)
        vm.setType(ListingType.SELL)
        vm.applyFilter(FilterState(includeSold = false, includeExpired = false))
        dispatcher.scheduler.advanceUntilIdle()
        vm.clearFilter()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(true, vm.state.value.includeSold)
        assertEquals(true, vm.state.value.includeExpired)
        assertEquals(true, repo.lastMyQuery?.includeSold)
        assertEquals(true, repo.lastMyQuery?.includeExpired)
    }

    @Test
    fun `mine loadMore appends next page using cursor`() = runTest {
        val repo = FakeRepo(listOf(
            ListingPage(listOf(summary(1)), nextCursor = "c1"),
            ListingPage(listOf(summary(2)), nextCursor = null),
        ))
        val vm = mineVm(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        vm.loadMore(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        assertEquals("c1", repo.lastMyQuery?.cursor)
        assertTrue(vm.state.value.endReached)
    }

    @Test
    fun `mine empty result yields no items and endReached`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = mineVm(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.items.isEmpty())
        assertTrue(vm.state.value.endReached)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `mine adjustSold failure surfaces error and keeps item unchanged`() = runTest {
        val repo = FakeRepo(
            listOf(ListingPage(listOf(summary(1, total = 5, sold = 3)), null)),
            updateResult = DomainResult.Failure("CONFLICT", "售出数量冲突，请刷新后重试", 409),
        )
        val vm = mineVm(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        vm.adjustSold(1, 1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(UiText.Res(R.string.error_conflict), vm.state.value.error)
        assertNull(vm.state.value.adjustingId)
        assertEquals(3, vm.state.value.items.first { it.id == 1L }.quantitySold)
    }

    @Test
    fun `mine refresh maps 401 to a login prompt`() = runTest {
        // 我的列表需登录：401 给出明确引导文案（公开列表无此分支）。
        val repo = FakeRepo(emptyList(), myResult = DomainResult.Failure("UNAUTH", "未登录", 401))
        val vm = mineVm(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(UiText.Res(R.string.error_login_required), vm.state.value.error)
    }

    @Test
    fun `mine does not resolve currentUserId`() = runTest {
        // MINE 全为本人项，不解析本人 id（currentUserId 恒为 null）。
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1)), null)))
        val vm = mineVm(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.state.value.currentUserId)
    }

    @Test
    fun `mine pull refresh reloads first page and clears refreshing`() = runTest {
        val repo = FakeRepo(listOf(
            ListingPage(listOf(summary(1)), nextCursor = null),
            ListingPage(listOf(summary(2), summary(3)), nextCursor = null),
        ))
        val vm = mineVm(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.items.size)
        vm.refresh(showSpinner = false); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        assertEquals(false, vm.state.value.refreshing)
        assertEquals(false, vm.state.value.loading)
    }

    @Test
    fun `mine delete removes item from list`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1), summary(2)), null)))
        val vm = mineVm(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        vm.delete(1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1L, repo.deletedId)
        assertEquals(listOf(2L), vm.state.value.items.map { it.id })
    }

    @Test
    fun `mine applySearch routes to myListings with trimmed text`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = mineVm(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        vm.applySearch("  高数  ")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("高数", vm.state.value.query)
        assertEquals("高数", repo.lastMyQuery?.text)
        assertNull(repo.lastQuery?.text) // 不应走公开 list()
    }

    @Test
    fun `mine applySearch blank clears text and routes to myListings`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = mineVm(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        vm.applySearch("书"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("书", repo.lastMyQuery?.text)
        vm.applySearch("   "); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("", vm.state.value.query)
        assertNull(repo.lastMyQuery?.text)
    }
}
