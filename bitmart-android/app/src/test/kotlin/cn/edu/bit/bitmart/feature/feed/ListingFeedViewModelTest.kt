package cn.edu.bit.bitmart.feature.feed

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingPage
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ListingFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private fun summary(id: Long) = ListingSummary(
        id, ListingType.SELL, ListingCategory.GENERAL, "商品$id", "10.00", 1, 0, null, null, emptyList(), "2026-06-02T00:00:00Z",
    )

    /** 记录最近一次查询并按脚本返回页。 */
    private class FakeRepo(val pages: List<ListingPage>) : ListingRepository {
        var lastQuery: ListingQuery? = null
        private var call = 0
        override suspend fun list(query: ListingQuery): DomainResult<ListingPage> {
            lastQuery = query
            return DomainResult.Success(pages[call++.coerceAtMost(pages.size - 1)])
        }
        override suspend fun detail(id: Long) = DomainResult.Failure("X", "n/a", 404)
        override suspend fun publish(draft: cn.edu.bit.bitmart.core.domain.repository.PublishDraft) = DomainResult.Success(1L)
        override suspend fun popularTags(limit: Int) = DomainResult.Success(emptyList<String>())
    }

    @Test
    fun `refresh loads first page`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(listOf(summary(1), summary(2)), nextCursor = "c1")))
        val vm = ListingFeedViewModel(repo)
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
        val vm = ListingFeedViewModel(repo)
        vm.refresh(); dispatcher.scheduler.advanceUntilIdle()
        vm.loadMore(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.items.size)
        assertEquals("c1", repo.lastQuery?.cursor)
        assertTrue(vm.state.value.endReached)
    }

    @Test
    fun `setType switches type and reloads`() = runTest {
        val repo = FakeRepo(listOf(ListingPage(emptyList(), null)))
        val vm = ListingFeedViewModel(repo)
        vm.setType(ListingType.BUY)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ListingType.BUY, vm.state.value.type)
        assertEquals(ListingType.BUY, repo.lastQuery?.type)
    }
}
