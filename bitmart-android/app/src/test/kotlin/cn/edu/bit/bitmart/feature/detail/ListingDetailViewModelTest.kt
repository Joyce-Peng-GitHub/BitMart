package cn.edu.bit.bitmart.feature.detail

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingDetail
import cn.edu.bit.bitmart.core.domain.model.ListingPage
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.User
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.ProfileRepository
import cn.edu.bit.bitmart.core.domain.repository.PublishDraft
import cn.edu.bit.bitmart.core.domain.repository.UpdateDraft
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

    private fun listingRepo(result: DomainResult<ListingDetail>) = object : ListingRepository {
        override suspend fun list(query: ListingQuery) = DomainResult.Success(ListingPage(emptyList(), null))
        override suspend fun detail(id: Long) = result
        override suspend fun publish(draft: PublishDraft) = DomainResult.Success(1L)
        override suspend fun update(id: Long, update: UpdateDraft) = DomainResult.Success(Unit)
        override suspend fun delete(id: Long) = DomainResult.Success(Unit)
        override suspend fun popularTags(limit: Int) = DomainResult.Success(emptyList<String>())
    }

    /** 当前用户：用于 ListingDetailViewModel 判定是否为发布者本人。 */
    private fun profileRepo(userId: Long? = null) = object : ProfileRepository {
        override suspend fun getMe(): DomainResult<User> =
            if (userId == null) DomainResult.Failure("UNAUTHORIZED", "未登录", 401)
            else DomainResult.Success(User(userId, "1120201234", null, "匿名", "NORMAL"))
    }

    private val detail = ListingDetail(
        1, ListingType.SELL, ListingCategory.GENERAL, 5, "卖家", "书", "描述",
        "30.00", 2, 0, "三号楼", emptyList(), emptyList(), emptyList(),
        "2026-07-01T00:00:00Z", "2026-06-02T00:00:00Z", null,
    )

    @Test
    fun `load success exposes detail`() = runTest {
        val vm = ListingDetailViewModel(listingRepo(DomainResult.Success(detail)), profileRepo())
        vm.load(1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("书", vm.state.value.detail?.title)
    }

    @Test
    fun `401 sets needLogin`() = runTest {
        val vm = ListingDetailViewModel(listingRepo(DomainResult.Failure("UNAUTHORIZED", "需要登录", 401)), profileRepo())
        vm.load(1); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.needLogin)
    }

    @Test
    fun `other failure sets error`() = runTest {
        val vm = ListingDetailViewModel(listingRepo(DomainResult.Failure("NOT_FOUND", "未找到", 404)), profileRepo())
        vm.load(1); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("未找到", vm.state.value.error)
    }

    @Test
    fun `owner is detected when current user matches listing owner`() = runTest {
        // detail.userId = 5；当前用户 id=5 → 应判定为本人。
        val vm = ListingDetailViewModel(listingRepo(DomainResult.Success(detail)), profileRepo(userId = 5))
        vm.load(1); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.isOwner)
    }
}
