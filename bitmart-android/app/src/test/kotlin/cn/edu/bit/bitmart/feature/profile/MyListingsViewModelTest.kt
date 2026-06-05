package cn.edu.bit.bitmart.feature.profile

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingPage
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.PublishDraft
import cn.edu.bit.bitmart.core.domain.repository.UpdateDraft
import kotlinx.coroutines.Dispatchers
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

class MyListingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private fun summary(id: Long, total: Int = 2, sold: Int = 0) = ListingSummary(
        id, ListingType.SELL, ListingCategory.GENERAL, "商品$id", "10.00", total, sold, null, null, emptyList(), "2026-06-02T00:00:00Z",
    )

    /** 记录 my-listings 的查询脚本与 update/delete 调用；update/delete 结果可配置以测失败路径。 */
    private class FakeRepo(
        val pages: List<ListingPage>,
        val updateResult: DomainResult<Unit> = DomainResult.Success(Unit),
        val deleteResult: DomainResult<Unit> = DomainResult.Success(Unit),
    ) : ListingRepository {
        var lastMyQuery: ListingQuery? = null
        var lastUpdate: Pair<Long, UpdateDraft>? = null
        var deletedId: Long? = null
        private var call = 0
        override suspend fun list(query: ListingQuery) = DomainResult.Success(ListingPage(emptyList(), null))
        override suspend fun myListings(query: ListingQuery): DomainResult<ListingPage> {
            lastMyQuery = query
            return DomainResult.Success(pages[call++.coerceAtMost(pages.size - 1)])
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
        override suspend fun popularTags(limit: Int) = DomainResult.Success(emptyList<String>())
    }

    @Test
    fun `setType loads own listings of given type`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1), summary(2)), nextCursor = "c1")))
        val vm = MyListingsViewModel(repo)
        vm.setType(ListingType.BUY)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ListingType.BUY, vm.state.value.type)
        assertEquals(ListingType.BUY, repo.lastMyQuery?.type)
        assertEquals(2, vm.state.value.items.size)
        assertEquals("c1", vm.state.value.nextCursor)
    }

    @Test
    fun `loadMore appends next page using cursor`() = runTest {
        val repo = FakeRepo(listOf(
            ListingPage(listOf(summary(1)), nextCursor = "c1"),
            ListingPage(listOf(summary(2)), nextCursor = null),
        ))
        val vm = MyListingsViewModel(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        vm.loadMore(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        assertEquals("c1", repo.lastMyQuery?.cursor)
        assertTrue(vm.state.value.endReached)
    }

    @Test
    fun `empty result yields no items and endReached`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = MyListingsViewModel(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.items.isEmpty())
        assertTrue(vm.state.value.endReached)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `adjustSold updates item locally and calls repo`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1, total = 5, sold = 0)), null)))
        val vm = MyListingsViewModel(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        vm.adjustSold(1, 3); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1L, repo.lastUpdate?.first)
        assertEquals(3, repo.lastUpdate?.second?.quantitySold)
        assertEquals(3, vm.state.value.items.first { it.id == 1L }.quantitySold)
        assertNull(vm.state.value.adjustingId)
    }

    @Test
    fun `delete removes item from list`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1), summary(2)), null)))
        val vm = MyListingsViewModel(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        vm.delete(1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1L, repo.deletedId)
        assertEquals(listOf(2L), vm.state.value.items.map { it.id })
    }

    @Test
    fun `adjustSold failure surfaces error and keeps item unchanged`() = runTest {
        // 服务端返回 409（如并发修改冲突）；UI 需能看到错误，且本地不应错误改值。
        val repo = FakeRepo(
            listOf(ListingPage(listOf(summary(1, total = 5, sold = 3)), null)),
            updateResult = DomainResult.Failure("CONFLICT", "售出数量冲突，请刷新后重试", 409),
        )
        val vm = MyListingsViewModel(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        vm.adjustSold(1, 1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("调整失败：售出数量冲突，请刷新后重试", vm.state.value.error)
        assertNull(vm.state.value.adjustingId)
        // 失败时不应本地改值，仍为 3。
        assertEquals(3, vm.state.value.items.first { it.id == 1L }.quantitySold)
    }

    @Test
    fun `delete failure surfaces error and keeps item`() = runTest {
        val repo = FakeRepo(
            listOf(ListingPage(listOf(summary(1), summary(2)), null)),
            deleteResult = DomainResult.Failure("FORBIDDEN", "无权删除", 403),
        )
        val vm = MyListingsViewModel(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        vm.delete(1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("删除失败：无权删除", vm.state.value.error)
        // 未删除：两条仍在。
        assertEquals(listOf(1L, 2L), vm.state.value.items.map { it.id })
    }

    @Test
    fun `consumeError clears the error`() = runTest {
        val repo = FakeRepo(
            listOf(ListingPage(listOf(summary(1)), null)),
            deleteResult = DomainResult.NetworkError("断网"),
        )
        val vm = MyListingsViewModel(repo)
        vm.setType(ListingType.SELL); dispatcher.scheduler.advanceUntilIdle()
        vm.delete(1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("网络异常：断网", vm.state.value.error)
        vm.consumeError()
        assertNull(vm.state.value.error)
    }
}
