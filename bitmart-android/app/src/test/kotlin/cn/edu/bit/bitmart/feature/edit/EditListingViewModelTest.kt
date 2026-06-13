package cn.edu.bit.bitmart.feature.edit

import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingDetail
import cn.edu.bit.bitmart.core.domain.model.ListingPage
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.PublishConfig
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.PublishDraft
import cn.edu.bit.bitmart.core.domain.repository.TagInfo
import cn.edu.bit.bitmart.core.domain.repository.UpdateDraft
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

class EditListingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private val sellDetail = ListingDetail(
        id = 7, type = ListingType.SELL, category = ListingCategory.GENERAL, userId = 5, nickname = "卖家",
        title = "高数教材", description = "九成新", unitPrice = "30.00", quantityTotal = 3, quantitySold = 1,
        pickupLocation = "三号楼", contacts = emptyList(), tags = emptyList(), imageUrls = emptyList(),
        expiresAt = "2026-07-01T00:00:00Z", createdAt = "2026-06-02T00:00:00Z", book = null,
    )

    private class FakeRepo(
        private val detail: DomainResult<ListingDetail>,
        var updateResult: DomainResult<Unit> = DomainResult.Success(Unit),
    ) : ListingRepository {
        var lastUpdateId: Long? = null
        var lastUpdate: UpdateDraft? = null
        override suspend fun list(query: ListingQuery) = DomainResult.Success(ListingPage(emptyList(), null))
        override suspend fun myListings(query: ListingQuery) = DomainResult.Success(ListingPage(emptyList(), null))
        override suspend fun detail(id: Long) = detail
        override suspend fun publish(draft: PublishDraft) = DomainResult.Success(1L)
        override suspend fun publishBatch(drafts: List<PublishDraft>) = DomainResult.Success(listOf(1L))
        override suspend fun uploadImage(bytes: ByteArray, filename: String) = DomainResult.Success("blob-key")
        override suspend fun lookupBook(isbn: String) = DomainResult.Success(null)
        override suspend fun update(id: Long, update: UpdateDraft): DomainResult<Unit> {
            lastUpdateId = id; lastUpdate = update; return updateResult
        }
        override suspend fun delete(id: Long) = DomainResult.Success(Unit)
        override suspend fun popularTags(limit: Int) = DomainResult.Success(emptyList<TagInfo>())
    }

    @Test
    fun `load prefills form from detail`() = runTest {
        val vm = EditListingViewModel(FakeRepo(DomainResult.Success(sellDetail)))
        vm.load(7); dispatcher.scheduler.advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals("高数教材", s.title)
        assertEquals("九成新", s.description)
        assertEquals("30.00", s.unitPrice)
        assertEquals("三号楼", s.pickupLocation)
        assertEquals("", s.expiresInDays) // 有效期默认留空 = 不修改。
        assertEquals(ListingType.SELL, s.type)
    }

    @Test
    fun `load 401 sets loadError`() = runTest {
        val vm = EditListingViewModel(FakeRepo(DomainResult.Failure("UNAUTHORIZED", "需要登录", 401)))
        vm.load(7); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("请登录后编辑", vm.state.value.loadError)
    }

    @Test
    fun `save maps edited fields to UpdateDraft and marks saved`() = runTest {
        val repo = FakeRepo(DomainResult.Success(sellDetail))
        val vm = EditListingViewModel(repo)
        vm.load(7); dispatcher.scheduler.advanceUntilIdle()

        vm.onTitle("线性代数"); vm.onUnitPrice("25.5"); vm.onExpiresInDays("60")
        vm.save(); dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.saved)
        assertEquals(7L, repo.lastUpdateId)
        val d = repo.lastUpdate!!
        assertEquals("线性代数", d.title)
        assertEquals("25.5", d.unitPrice)
        assertFalse(d.clearUnitPrice)
        assertEquals(60, d.expiresInDays)
    }

    @Test
    fun `blank price clears unit price`() = runTest {
        val repo = FakeRepo(DomainResult.Success(sellDetail))
        val vm = EditListingViewModel(repo)
        vm.load(7); dispatcher.scheduler.advanceUntilIdle()

        vm.onUnitPrice("   ")
        vm.save(); dispatcher.scheduler.advanceUntilIdle()

        val d = repo.lastUpdate!!
        assertNull(d.unitPrice)
        assertTrue(d.clearUnitPrice) // 价格留空 → 面议。
    }

    @Test
    fun `blank expiry maps to null (unchanged)`() = runTest {
        val repo = FakeRepo(DomainResult.Success(sellDetail))
        val vm = EditListingViewModel(repo)
        vm.load(7); dispatcher.scheduler.advanceUntilIdle()

        vm.save(); dispatcher.scheduler.advanceUntilIdle()

        assertNull(repo.lastUpdate!!.expiresInDays)
    }

    @Test
    fun `blank title blocks save`() = runTest {
        val repo = FakeRepo(DomainResult.Success(sellDetail))
        val vm = EditListingViewModel(repo)
        vm.load(7); dispatcher.scheduler.advanceUntilIdle()

        vm.onTitle("   ")
        vm.save(); dispatcher.scheduler.advanceUntilIdle()

        assertEquals("请填写标题", vm.state.value.formError)
        assertNull(repo.lastUpdate)
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun `invalid price blocks save`() = runTest {
        val repo = FakeRepo(DomainResult.Success(sellDetail))
        val vm = EditListingViewModel(repo)
        vm.load(7); dispatcher.scheduler.advanceUntilIdle()

        vm.onUnitPrice("abc")
        vm.save(); dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.formError!!.contains("价格"))
        assertNull(repo.lastUpdate)
    }

    @Test
    fun `too-large price blocks save`() = runTest {
        val repo = FakeRepo(DomainResult.Success(sellDetail))
        val vm = EditListingViewModel(repo)
        vm.load(7); dispatcher.scheduler.advanceUntilIdle()

        // 超出 NUMERIC(10,2) 上限被拒绝（入库前拦截）。
        vm.onUnitPrice("100000000")
        vm.save(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.formError!!.contains("价格"))
        assertNull(repo.lastUpdate)

        // 恰好等于上限合法，可保存。
        vm.onUnitPrice(PublishConfig.MAX_UNIT_PRICE)
        vm.save(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.saved)
        assertEquals(PublishConfig.MAX_UNIT_PRICE, repo.lastUpdate!!.unitPrice)
    }

    @Test
    fun `out-of-range expiry blocks save`() = runTest {
        val repo = FakeRepo(DomainResult.Success(sellDetail))
        val vm = EditListingViewModel(repo)
        vm.load(7); dispatcher.scheduler.advanceUntilIdle()

        for (bad in listOf("0", "366", "1.5", "abc")) {
            vm.onExpiresInDays(bad)
            vm.save(); dispatcher.scheduler.advanceUntilIdle()
            assertTrue("应拒绝有效期: $bad", vm.state.value.formError!!.contains("有效期"))
            assertNull(repo.lastUpdate)
        }
    }

    @Test
    fun `save failure surfaces error and not saved`() = runTest {
        val repo = FakeRepo(DomainResult.Success(sellDetail), updateResult = DomainResult.Failure("X", "标题重复", 400))
        val vm = EditListingViewModel(repo)
        vm.load(7); dispatcher.scheduler.advanceUntilIdle()

        vm.save(); dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.formError!!.contains("保存失败"))
        assertFalse(vm.state.value.saved)
    }
}
