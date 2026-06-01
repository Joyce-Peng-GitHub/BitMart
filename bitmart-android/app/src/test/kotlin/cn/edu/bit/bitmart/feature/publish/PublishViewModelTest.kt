package cn.edu.bit.bitmart.feature.publish

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingDetail
import cn.edu.bit.bitmart.core.domain.model.ListingPage
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PublishViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private class FakeRepo(val result: DomainResult<Long>) : ListingRepository {
        var lastDraft: PublishDraft? = null
        override suspend fun list(query: ListingQuery) = DomainResult.Success(ListingPage(emptyList(), null))
        override suspend fun detail(id: Long): DomainResult<ListingDetail> = DomainResult.Failure("X", "n/a", 404)
        override suspend fun publish(draft: PublishDraft): DomainResult<Long> { lastDraft = draft; return result }
        override suspend fun popularTags(limit: Int) = DomainResult.Success(emptyList<String>())
    }

    private fun fill(vm: PublishViewModel) {
        vm.onTitle("二手书"); vm.onContactValue("wxid_x"); vm.onQuantity("2"); vm.onUnitPrice("30")
    }

    @Test
    fun `submit success exposes published id and maps fields`() = runTest {
        val repo = FakeRepo(DomainResult.Success(42L))
        val vm = PublishViewModel(repo)
        fill(vm)
        vm.submit(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(42L, vm.state.value.publishedId)
        assertEquals("二手书", repo.lastDraft?.title)
        assertEquals(2, repo.lastDraft?.quantityTotal)
        assertEquals("30", repo.lastDraft?.unitPrice)
    }

    @Test
    fun `blank title blocked client-side`() = runTest {
        val repo = FakeRepo(DomainResult.Success(1L))
        val vm = PublishViewModel(repo)
        vm.onContactValue("x")
        vm.submit(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("请填写标题", vm.state.value.error)
        assertNull(repo.lastDraft)   // 未调用仓储
    }

    @Test
    fun `blank price becomes null (面议)`() = runTest {
        val repo = FakeRepo(DomainResult.Success(1L))
        val vm = PublishViewModel(repo)
        vm.onTitle("t"); vm.onContactValue("x"); vm.onUnitPrice("")
        vm.submit(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(repo.lastDraft?.unitPrice)
    }

    @Test
    fun `server failure surfaces message`() = runTest {
        val repo = FakeRepo(DomainResult.Failure("VALIDATION_FAILED", "标题不能为空", 400))
        val vm = PublishViewModel(repo)
        fill(vm)
        vm.submit(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("标题不能为空", vm.state.value.error)
        assertNull(vm.state.value.publishedId)
    }
}
