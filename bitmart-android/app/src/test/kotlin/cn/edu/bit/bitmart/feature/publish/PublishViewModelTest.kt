package cn.edu.bit.bitmart.feature.publish

import app.cash.turbine.test
import cn.edu.bit.bitmart.core.data.FakeLlmConfigStore
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.BookInfo
import cn.edu.bit.bitmart.core.domain.model.ContactChannel
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingDetail
import cn.edu.bit.bitmart.core.domain.model.ListingPage
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.PublishConfig
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.PublishDraft
import cn.edu.bit.bitmart.llm.LlmClient
import cn.edu.bit.bitmart.llm.LlmConfig
import cn.edu.bit.bitmart.llm.LlmRecognition
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

class PublishViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private class FakeRepo(
        var publishResult: DomainResult<Long> = DomainResult.Success(1L),
        var batchResult: DomainResult<List<Long>> = DomainResult.Success(listOf(1L, 2L)),
        var uploadResult: DomainResult<String> = DomainResult.Success("blob-key"),
        var lookupResult: DomainResult<BookInfo?> = DomainResult.Success(null),
        var tagsResult: DomainResult<List<String>> = DomainResult.Success(listOf("热门1", "热门2")),
    ) : ListingRepository {
        var lastBatchDrafts: List<PublishDraft>? = null

        override suspend fun list(query: ListingQuery) = DomainResult.Success(ListingPage(emptyList(), null))
        override suspend fun myListings(query: ListingQuery) = DomainResult.Success(ListingPage(emptyList(), null))
        override suspend fun detail(id: Long): DomainResult<ListingDetail> = DomainResult.Failure("X", "n/a", 404)
        override suspend fun publish(draft: PublishDraft) = publishResult
        override suspend fun publishBatch(drafts: List<PublishDraft>): DomainResult<List<Long>> {
            lastBatchDrafts = drafts; return batchResult
        }
        override suspend fun uploadImage(bytes: ByteArray, filename: String) = uploadResult
        override suspend fun lookupBook(isbn: String) = lookupResult
        override suspend fun update(id: Long, update: cn.edu.bit.bitmart.core.domain.repository.UpdateDraft) = DomainResult.Success(Unit)
        override suspend fun delete(id: Long) = DomainResult.Success(Unit)
        override suspend fun popularTags(limit: Int) = tagsResult
    }

    private class FakeLlmClient(var result: DomainResult<LlmRecognition>) : LlmClient {
        override suspend fun recognize(config: LlmConfig, imageBytes: ByteArray, category: ListingCategory) = result
    }

    @Test
    fun `addDraftToBatch validates and adds to batch list`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))), FakeLlmConfigStore())
        vm.onTitle("二手书"); vm.onContactValue("wxid_x"); vm.onQuantity("2")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.draftBatch.size)
        assertEquals("二手书", vm.state.value.draftBatch[0].title)
        assertEquals("", vm.state.value.currentDraft.title) // 编辑器已重置。
    }

    @Test
    fun `addDraftToBatch blocks blank title`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))), FakeLlmConfigStore())
        vm.onContactValue("x")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertEquals("请填写标题", vm.state.value.error)
        assertTrue(vm.state.value.draftBatch.isEmpty())
    }

    @Test
    fun `submitBatch maps drafts with book and imageKeys`() = runTest {
        val repo = FakeRepo()
        val vm = PublishViewModel(repo, FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))), FakeLlmConfigStore())
        vm.setCategory(ListingCategory.BOOK)
        vm.onTitle("深入理解计算机系统"); vm.onIsbn("9787111544937"); vm.onAuthor("Bryant")
        vm.onContactValue("wxid_x"); vm.onQuantity("1")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()

        vm.submitBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.batchSubmitted)
        val drafts = repo.lastBatchDrafts!!
        assertEquals(1, drafts.size)
        assertEquals(ListingCategory.BOOK, drafts[0].category)
        assertEquals("9787111544937", drafts[0].book?.isbn)
        assertEquals("Bryant", drafts[0].book?.authors)
    }

    @Test
    fun `submitBatch empty list shows error`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))), FakeLlmConfigStore())
        vm.submitBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertEquals("请至少添加一项到待发布列表", vm.state.value.error)
        assertFalse(vm.state.value.batchSubmitted)
    }

    @Test
    fun `submitBatch failure surfaces message`() = runTest {
        val vm = PublishViewModel(
            FakeRepo(batchResult = DomainResult.Failure("VALIDATION", "第1项标题不能为空", 400)),
            FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))),
            FakeLlmConfigStore(),
        )
        vm.onTitle("x"); vm.onContactValue("y")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()

        vm.submitBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.error!!.contains("标题"))
        assertFalse(vm.state.value.batchSubmitted)
    }

    @Test
    fun `uploadImage success adds blobKey to currentDraft`() = runTest {
        val vm = PublishViewModel(
            FakeRepo(uploadResult = DomainResult.Success("2026/06/02/uuid.jpg")),
            FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))),
            FakeLlmConfigStore(),
        )
        vm.uploadImage(byteArrayOf(1, 2, 3), "test.jpg"); dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.currentDraft.imageKeys.contains("2026/06/02/uuid.jpg"))
        assertFalse(vm.state.value.uploadingImage)
    }

    @Test
    fun `recognizeWithLlm not configured emits navigate event`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig())
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))), store)

        vm.events.test {
            vm.recognizeWithLlm(byteArrayOf(1, 2)); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(PublishEvent.NavigateToLlmSettings, awaitItem())
        }
    }

    @Test
    fun `recognizeWithLlm book merges fields into currentDraft`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        val llm = FakeLlmClient(DomainResult.Success(
            LlmRecognition.Book("深入理解计算机系统", "Bryant", "机械工业", "第3版", "9787111544937"),
        ))
        val vm = PublishViewModel(FakeRepo(), llm, store)
        vm.setCategory(ListingCategory.BOOK)

        vm.recognizeWithLlm(byteArrayOf(1, 2, 3)); dispatcher.scheduler.advanceUntilIdle()

        assertEquals("深入理解计算机系统", vm.state.value.currentDraft.title)
        assertEquals("Bryant", vm.state.value.currentDraft.author)
        assertEquals("9787111544937", vm.state.value.currentDraft.isbn)
    }

    @Test
    fun `recognizeWithLlm general merges title and tags respecting MAX_TAGS`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        val llm = FakeLlmClient(DomainResult.Success(
            LlmRecognition.General("二手台灯", "九成新", "35", listOf("家居", "照明", "电器")),
        ))
        val vm = PublishViewModel(FakeRepo(), llm, store)
        vm.setCategory(ListingCategory.GENERAL)
        repeat(PublishConfig.MAX_TAGS - 1) { vm.addCustomTag("tag$it") }

        vm.recognizeWithLlm(byteArrayOf(1)); dispatcher.scheduler.advanceUntilIdle()

        assertEquals("二手台灯", vm.state.value.currentDraft.title)
        assertEquals("九成新", vm.state.value.currentDraft.description)
        assertEquals("35", vm.state.value.currentDraft.unitPrice)
        assertTrue(vm.state.value.currentDraft.tags.size <= PublishConfig.MAX_TAGS)
    }

    @Test
    fun `lookupBook 200 prefills currentDraft`() = runTest {
        val vm = PublishViewModel(
            FakeRepo(lookupResult = DomainResult.Success(
                BookInfo("9787111544937", "深入理解计算机系统", "Bryant", "机械工业", "第3版"),
            )),
            FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))),
            FakeLlmConfigStore(),
        )
        vm.setCategory(ListingCategory.BOOK)

        vm.lookupBook("9787111544937"); dispatcher.scheduler.advanceUntilIdle()

        assertEquals("深入理解计算机系统", vm.state.value.currentDraft.title)
        assertEquals("Bryant", vm.state.value.currentDraft.author)
        assertEquals("9787111544937", vm.state.value.currentDraft.isbn)
    }

    @Test
    fun `lookupBook 404 fills isbn only`() = runTest {
        val vm = PublishViewModel(
            FakeRepo(lookupResult = DomainResult.Success(null)),
            FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))),
            FakeLlmConfigStore(),
        )
        vm.setCategory(ListingCategory.BOOK)

        vm.lookupBook("1234567890123"); dispatcher.scheduler.advanceUntilIdle()

        assertEquals("1234567890123", vm.state.value.currentDraft.isbn)
        assertEquals("", vm.state.value.currentDraft.title)
    }

    @Test
    fun `toggleTag enforces MAX_TAGS`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))), FakeLlmConfigStore())
        repeat(PublishConfig.MAX_TAGS) { vm.toggleTag("tag$it") }

        assertEquals(PublishConfig.MAX_TAGS, vm.state.value.currentDraft.tags.size)

        vm.toggleTag("overflow")
        assertEquals(PublishConfig.MAX_TAGS, vm.state.value.currentDraft.tags.size)
        assertFalse(vm.state.value.currentDraft.tags.contains("overflow"))
    }

    @Test
    fun `removeDraft removes by index`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))), FakeLlmConfigStore())
        vm.onTitle("A"); vm.onContactValue("x"); vm.addDraftToBatch()
        vm.onTitle("B"); vm.onContactValue("x"); vm.addDraftToBatch()
        dispatcher.scheduler.advanceUntilIdle()

        vm.removeDraft(0)
        assertEquals(1, vm.state.value.draftBatch.size)
        assertEquals("B", vm.state.value.draftBatch[0].title)
    }

    @Test
    fun `editDraft loads into currentDraft and removes from batch`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))), FakeLlmConfigStore())
        vm.onTitle("A"); vm.onContactValue("x"); vm.addDraftToBatch()
        dispatcher.scheduler.advanceUntilIdle()

        vm.editDraft(0)
        assertEquals("A", vm.state.value.currentDraft.title)
        assertTrue(vm.state.value.draftBatch.isEmpty())
    }

    @Test
    fun `openBookScan emits navigate event`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))), FakeLlmConfigStore())

        vm.events.test {
            vm.openBookScan(); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(PublishEvent.NavigateToBookScan, awaitItem())
        }
    }

    @Test
    fun `loads popular tags on init`() = runTest {
        val vm = PublishViewModel(
            FakeRepo(tagsResult = DomainResult.Success(listOf("热门1", "热门2"))),
            FakeLlmClient(DomainResult.Success(LlmRecognition.General("", "", null, emptyList()))),
            FakeLlmConfigStore(),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("热门1", "热门2"), vm.state.value.popularTags)
    }
}
