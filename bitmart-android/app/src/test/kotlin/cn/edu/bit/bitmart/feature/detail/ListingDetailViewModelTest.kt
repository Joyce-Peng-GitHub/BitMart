package cn.edu.bit.bitmart.feature.detail

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingDetail
import cn.edu.bit.bitmart.core.domain.model.ListingPage
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.PublishDraft
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

class ListingDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private fun repo(result: DomainResult<ListingDetail>) = object : ListingRepository {
        override suspend fun list(query: ListingQuery) = DomainResult.Success(ListingPage(emptyList(), null))
        override suspend fun detail(id: Long) = result
        override suspend fun publish(draft: PublishDraft) = DomainResult.Success(1L)
        override suspend fun popularTags(limit: Int) = DomainResult.Success(emptyList<String>())
    }

    private val detail = ListingDetail(
        1, ListingType.SELL, ListingCategory.GENERAL, 5, "卖家", "书", "描述",
        "30.00", 2, 0, "三号楼", emptyList(), emptyList(), "2026-07-01T00:00:00Z", "2026-06-02T00:00:00Z", null,
    )

    @Test
    fun `load success exposes detail`() = runTest {
        val vm = ListingDetailViewModel(repo(DomainResult.Success(detail)))
        vm.load(1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("书", vm.state.value.detail?.title)
    }

    @Test
    fun `401 sets needLogin`() = runTest {
        val vm = ListingDetailViewModel(repo(DomainResult.Failure("UNAUTHORIZED", "需要登录", 401)))
        vm.load(1); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.needLogin)
    }

    @Test
    fun `other failure sets error`() = runTest {
        val vm = ListingDetailViewModel(repo(DomainResult.Failure("NOT_FOUND", "未找到", 404)))
        vm.load(1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("未找到", vm.state.value.error)
    }
}
