package cn.edu.bit.bitmart.feature.publish

import app.cash.turbine.test
import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.data.FakeContactPrefsStore
import cn.edu.bit.bitmart.core.data.FakeLanguagePrefsStore
import cn.edu.bit.bitmart.core.data.FakeLlmConfigStore
import cn.edu.bit.bitmart.core.domain.DomainResult
import cn.edu.bit.bitmart.core.domain.model.BookInfo
import cn.edu.bit.bitmart.core.domain.model.Contact
import cn.edu.bit.bitmart.core.domain.model.ListingCategory
import cn.edu.bit.bitmart.core.domain.model.ListingDetail
import cn.edu.bit.bitmart.core.domain.model.ListingPage
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.PublishConfig
import cn.edu.bit.bitmart.core.domain.repository.ListingQuery
import cn.edu.bit.bitmart.core.domain.repository.ListingRepository
import cn.edu.bit.bitmart.core.domain.repository.PublishDraft
import cn.edu.bit.bitmart.core.ui.UiText
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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class PublishViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private class FakeRepo(
        var publishResult: DomainResult<Long> = DomainResult.Success(1L),
        var batchResult: DomainResult<List<Long>> = DomainResult.Success(listOf(1L, 2L)),
        var uploadResult: DomainResult<String> = DomainResult.Success("blob-key"),
        var lookupResult: DomainResult<BookInfo?> = DomainResult.Success(null),
        var detailResult: DomainResult<ListingDetail> = DomainResult.Failure("X", "n/a", 404),
        var tagsResult: DomainResult<List<cn.edu.bit.bitmart.core.domain.repository.TagInfo>> = DomainResult.Success(listOf(
            cn.edu.bit.bitmart.core.domain.repository.TagInfo(1L, "热门1"),
            cn.edu.bit.bitmart.core.domain.repository.TagInfo(2L, "热门2"),
        )),
    ) : ListingRepository {
        var lastBatchDrafts: List<PublishDraft>? = null
        var lastUpdate: Pair<Long, cn.edu.bit.bitmart.core.domain.repository.UpdateDraft>? = null
        var uploadCalls = 0

        override suspend fun list(query: ListingQuery) = DomainResult.Success(ListingPage(emptyList(), null))
        override suspend fun myListings(query: ListingQuery) = DomainResult.Success(ListingPage(emptyList(), null))
        override suspend fun detail(id: Long): DomainResult<ListingDetail> = detailResult
        override suspend fun publish(draft: PublishDraft) = publishResult
        override suspend fun publishBatch(drafts: List<PublishDraft>): DomainResult<List<Long>> {
            lastBatchDrafts = drafts; return batchResult
        }
        override suspend fun uploadImage(bytes: ByteArray, filename: String): DomainResult<String> {
            uploadCalls++; return uploadResult
        }
        override suspend fun lookupBook(isbn: String) = lookupResult
        override suspend fun update(id: Long, update: cn.edu.bit.bitmart.core.domain.repository.UpdateDraft): DomainResult<Unit> {
            lastUpdate = id to update; return DomainResult.Success(Unit)
        }
        override suspend fun delete(id: Long) = DomainResult.Success(Unit)
        override suspend fun popularTags(limit: Int) = tagsResult
    }

    private class FakeLlmClient(var result: DomainResult<List<LlmRecognition>>) : LlmClient {
        override suspend fun recognize(config: LlmConfig, imageBytes: ByteArray, category: ListingCategory, languageTag: String) = result
    }

    @Test
    fun `addDraftToBatch validates and adds to batch list`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("二手书"); vm.onContact("wxid_x"); vm.onQuantity("2")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.draftBatch.size)
        assertEquals("二手书", vm.state.value.draftBatch[0].title)
        assertEquals("", vm.state.value.currentDraft.title) // 编辑器已重置。
    }

    @Test
    fun `addDraftToBatch blocks blank title`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onContact("x")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiText.Res(R.string.publish_error_title_required), vm.state.value.error)
        assertTrue(vm.state.value.draftBatch.isEmpty())
    }

    @Test
    fun `consumeError clears error so the same message can fire again`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        // 触发一次校验错误（空标题）→ Toast 由 UI 据此弹出。
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(UiText.Res(R.string.publish_error_title_required), vm.state.value.error)

        // UI 展示后消费置空。
        vm.consumeError()
        assertNull(vm.state.value.error)

        // 同样的错误能再次被置位（从而再次弹 Toast）。
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(UiText.Res(R.string.publish_error_title_required), vm.state.value.error)
    }

    @Test
    fun `expiresInDays passes through to PublishDraft`() = runTest {
        val repo = FakeRepo()
        val vm = PublishViewModel(repo, FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("台灯"); vm.onContact("x"); vm.onExpiresInDays("60")
        vm.addDraftToBatch()
        vm.submitBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(60, repo.lastBatchDrafts!![0].expiresInDays)
    }

    @Test
    fun `blank expiresInDays maps to null for server default`() = runTest {
        val repo = FakeRepo()
        val vm = PublishViewModel(repo, FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("台灯"); vm.onContact("x")
        vm.addDraftToBatch()
        vm.submitBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertNull(repo.lastBatchDrafts!![0].expiresInDays)
    }

    @Test
    fun `addDraftToBatch blocks out-of-range or invalid expiresInDays`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("台灯"); vm.onContact("x")

        for (bad in listOf("0", "${PublishConfig.EXPIRY_MAX_DAYS + 1}", "abc", "1.5")) {
            vm.onExpiresInDays(bad)
            vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                "应拒绝有效期输入: $bad",
                R.string.publish_error_expiry_days_range,
                (vm.state.value.error as UiText.Res).id,
            )
            assertTrue(vm.state.value.draftBatch.isEmpty())
        }

        // 边界值合法：min 与 max 都应通过。
        vm.onExpiresInDays("${PublishConfig.EXPIRY_MIN_DAYS}")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.draftBatch.size)

        vm.onTitle("台灯2"); vm.onContact("x"); vm.onExpiresInDays("${PublishConfig.EXPIRY_MAX_DAYS}")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, vm.state.value.draftBatch.size)
    }

    @Test
    fun `addDraftToBatch rejects non-integer or out-of-range quantity with a range message`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("台灯"); vm.onContact("x")

        // 非正整数、超上限、超 Int 的"过大整数"统一为「1~上限」区间提示。
        for (bad in listOf("0", "-3", "abc", "1.5", "${PublishConfig.MAX_QUANTITY + 1}", "100000000000")) {
            vm.onQuantity(bad)
            vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                "应判为越界数量: $bad",
                UiText.Res(R.string.publish_error_quantity_range, listOf(PublishConfig.MAX_QUANTITY)),
                vm.state.value.error,
            )
            assertTrue(vm.state.value.draftBatch.isEmpty())
        }

        // 恰好等于上限合法。
        vm.onQuantity("${PublishConfig.MAX_QUANTITY}")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.draftBatch.size)
        assertEquals(PublishConfig.MAX_QUANTITY, vm.state.value.draftBatch[0].quantityTotal.toInt())
    }

    @Test
    fun `addDraftToBatch rejects invalid or too-large unit price with a range message`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("台灯"); vm.onContact("x")

        // 格式非法、为负、超 NUMERIC(10,2) 上限统一为「0~上限」区间提示。
        for (bad in listOf("abc", "-1", "100000000")) {
            vm.onUnitPrice(bad)
            vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                "应判为越界单价: $bad",
                UiText.Res(R.string.publish_error_unit_price_range, listOf(PublishConfig.MAX_UNIT_PRICE)),
                vm.state.value.error,
            )
            assertTrue(vm.state.value.draftBatch.isEmpty())
        }

        // 恰好等于上限合法。
        vm.onUnitPrice(PublishConfig.MAX_UNIT_PRICE)
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.draftBatch.size)
    }

    @Test
    fun `addDraftToBatch rejects invalid or too-large original price with a range message`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("台灯"); vm.onContact("x")

        // 原价是显式补齐的客户端校验缺口：格式非法、为负、超上限统一为「0~上限」区间提示。
        for (bad in listOf("abc", "-1", "100000000")) {
            vm.onOriginalPrice(bad)
            vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                "应判为越界原价: $bad",
                UiText.Res(R.string.publish_error_original_price_range, listOf(PublishConfig.MAX_UNIT_PRICE)),
                vm.state.value.error,
            )
            assertTrue(vm.state.value.draftBatch.isEmpty())
        }

        // 恰好等于上限合法。
        vm.onOriginalPrice(PublishConfig.MAX_UNIT_PRICE)
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.draftBatch.size)
    }

    @Test
    fun `date expiry mode sends absolute expiresAt and clears days`() = runTest {
        val repo = FakeRepo()
        val vm = PublishViewModel(repo, FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        val date = LocalDate.now().plusDays(10)
        vm.onTitle("台灯"); vm.onContact("x")
        vm.onExpiryMode(ExpiryMode.DATE); vm.onExpiresOn(date.toString())
        vm.addDraftToBatch()
        vm.submitBatch(); dispatcher.scheduler.advanceUntilIdle()

        val d = repo.lastBatchDrafts!![0]
        // 绝对过期：该日 00:00（设备时区），且天数被清空。
        assertNull(d.expiresInDays)
        val expected = date.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().toString()
        assertEquals(expected, d.expiresAtIso)
    }

    @Test
    fun `date expiry mode requires a valid in-range date`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("台灯"); vm.onContact("x"); vm.onExpiryMode(ExpiryMode.DATE)

        // 未选日期 → 拒绝。
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(UiText.Res(R.string.publish_error_expiry_date_required), vm.state.value.error)
        assertTrue(vm.state.value.draftBatch.isEmpty())

        // 今天（不晚于今天）→ 拒绝。
        vm.onExpiresOn(LocalDate.now().toString())
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            UiText.Res(R.string.publish_error_expiry_date_range, listOf(PublishConfig.EXPIRY_MAX_DAYS)),
            vm.state.value.error,
        )
        assertTrue(vm.state.value.draftBatch.isEmpty())

        // 超过一年 → 拒绝。
        vm.onExpiresOn(LocalDate.now().plusDays((PublishConfig.EXPIRY_MAX_DAYS + 1).toLong()).toString())
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            UiText.Res(R.string.publish_error_expiry_date_range, listOf(PublishConfig.EXPIRY_MAX_DAYS)),
            vm.state.value.error,
        )
        assertTrue(vm.state.value.draftBatch.isEmpty())

        // 合法（明天）→ 通过。
        vm.onExpiresOn(LocalDate.now().plusDays(1).toString())
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.draftBatch.size)
    }

    @Test
    fun `submitBatch maps drafts with book and imageKeys`() = runTest {
        val repo = FakeRepo()
        val vm = PublishViewModel(repo, FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.setCategory(ListingCategory.BOOK)
        vm.onTitle("深入理解计算机系统"); vm.onIsbn("9787111544937"); vm.onAuthor("Bryant")
        vm.onContact("wxid_x"); vm.onQuantity("1")
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
    fun `setType makes published drafts carry that type`() = runTest {
        val repo = FakeRepo()
        val vm = PublishViewModel(repo, FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        // 入口决定类型：收购入口 → BUY，所有提交草稿均为该类型。
        vm.setType(ListingType.BUY)
        vm.onTitle("求购台灯"); vm.onContact("x")
        vm.addDraftToBatch()
        vm.submitBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ListingType.BUY, repo.lastBatchDrafts!![0].type)
    }

    @Test
    fun `submitBatch empty list shows error`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.submitBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiText.Res(R.string.publish_error_empty_batch), vm.state.value.error)
        assertFalse(vm.state.value.batchSubmitted)
    }

    @Test
    fun `submitBatch failure maps to a localized error`() = runTest {
        val vm = PublishViewModel(
            FakeRepo(batchResult = DomainResult.Failure("VALIDATION_FAILED", "第1项标题不能为空", 400)),
            FakeLlmClient(DomainResult.Success(emptyList())),
            FakeLlmConfigStore(),
            FakeContactPrefsStore(),
            FakeLanguagePrefsStore(),
        )
        vm.onTitle("x"); vm.onContact("y")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()

        vm.submitBatch(); dispatcher.scheduler.advanceUntilIdle()

        // 服务端失败按稳定 error code 映射到本地化文案（不再透传原始服务端消息）。
        assertEquals(UiText.Res(R.string.error_validation_failed), vm.state.value.error)
        assertFalse(vm.state.value.batchSubmitted)
    }

    @Test
    fun `submitBatch server failure with details surfaces only the first labeled problem`() = runTest {
        // 服务端 400 携带结构化明细：批量字段前缀 items[1]. → 弹窗只展示「第 2 条：原价区间」首个问题。
        val vm = PublishViewModel(
            FakeRepo(batchResult = DomainResult.Failure(
                "VALIDATION_FAILED", "n/a", 400,
                details = listOf(
                    cn.edu.bit.bitmart.core.domain.ValidationDetail(
                        "items[1].originalPrice", "PRICE_TOO_LARGE", mapOf("max" to "99999999.99"),
                    ),
                    cn.edu.bit.bitmart.core.domain.ValidationDetail(
                        "items[0].title", "TITLE_BLANK", emptyMap(),
                    ),
                ),
            )),
            FakeLlmClient(DomainResult.Success(emptyList())),
            FakeLlmConfigStore(),
            FakeContactPrefsStore(),
            FakeLanguagePrefsStore(),
        )
        // 两条均通过客户端校验，才会走到服务端（由服务端返回上面的明细）。
        vm.onTitle("A"); vm.onContact("y"); vm.addDraftToBatch()
        vm.onTitle("B"); vm.onContact("y"); vm.addDraftToBatch()
        dispatcher.scheduler.advanceUntilIdle()

        vm.submitBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            UiText.Res(
                R.string.publish_error_batch_item,
                listOf(2, UiText.Res(R.string.publish_error_original_price_range, listOf("99999999.99"))),
            ),
            vm.state.value.error,
        )
        assertFalse(vm.state.value.batchSubmitted)
    }

    @Test
    fun `uploadImage success adds blobKey to currentDraft`() = runTest {
        val vm = PublishViewModel(
            FakeRepo(uploadResult = DomainResult.Success("2026/06/02/uuid.jpg")),
            FakeLlmClient(DomainResult.Success(emptyList())),
            FakeLlmConfigStore(),
            FakeContactPrefsStore(),
            FakeLanguagePrefsStore(),
        )
        vm.uploadImage(byteArrayOf(1, 2, 3), "test.jpg"); dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.currentDraft.imageKeys.contains("2026/06/02/uuid.jpg"))
        assertFalse(vm.state.value.uploadingImage)
    }

    @Test
    fun `uploadImage rejects when MAX_IMAGES reached`() = runTest {
        val repo = FakeRepo()
        val vm = PublishViewModel(repo, FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        repeat(PublishConfig.MAX_IMAGES) {
            repo.uploadResult = DomainResult.Success("key-$it")
            vm.uploadImage(byteArrayOf(1), "img.jpg"); dispatcher.scheduler.advanceUntilIdle()
        }
        assertEquals(PublishConfig.MAX_IMAGES, vm.state.value.currentDraft.imageKeys.size)

        vm.uploadImage(byteArrayOf(1), "overflow.jpg"); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PublishConfig.MAX_IMAGES, vm.state.value.currentDraft.imageKeys.size)
        assertEquals(UiText.Res(R.string.publish_error_max_images, listOf(PublishConfig.MAX_IMAGES)), vm.state.value.error)
        assertEquals(PublishConfig.MAX_IMAGES, repo.uploadCalls) // 超限时不再发起上传请求。
    }

    @Test
    fun `uploadImage concurrent uploads clamp at MAX_IMAGES with error`() = runTest {
        val repo = FakeRepo()
        val vm = PublishViewModel(repo, FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        repeat(PublishConfig.MAX_IMAGES - 1) {
            repo.uploadResult = DomainResult.Success("key-$it")
            vm.uploadImage(byteArrayOf(1), "img.jpg"); dispatcher.scheduler.advanceUntilIdle()
        }

        // 两次上传同时在途（都先通过入口校验，再先后完成）→ 第二次完成时已满，应报错而非静默丢弃。
        repo.uploadResult = DomainResult.Success("key-last")
        vm.uploadImage(byteArrayOf(1), "a.jpg")
        vm.uploadImage(byteArrayOf(2), "b.jpg")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PublishConfig.MAX_IMAGES, vm.state.value.currentDraft.imageKeys.size)
        assertEquals(UiText.Res(R.string.publish_error_max_images, listOf(PublishConfig.MAX_IMAGES)), vm.state.value.error)
    }

    @Test
    fun `removeImage removes by index`() = runTest {
        val repo = FakeRepo()
        val vm = PublishViewModel(repo, FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        repo.uploadResult = DomainResult.Success("key-a")
        vm.uploadImage(byteArrayOf(1), "a.jpg"); dispatcher.scheduler.advanceUntilIdle()
        repo.uploadResult = DomainResult.Success("key-b")
        vm.uploadImage(byteArrayOf(2), "b.jpg"); dispatcher.scheduler.advanceUntilIdle()

        vm.removeImage(0)

        assertEquals(listOf("key-b"), vm.state.value.currentDraft.imageKeys)
    }

    @Test
    fun `submitBatch blocks a recognized draft missing contact`() = runTest {
        // 识别入暂存区的草稿没有联系方式，直接提交应被客户端校验拦下（不调用 publishBatch）。
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        val repo = FakeRepo()
        val llm = FakeLlmClient(DomainResult.Success(listOf(
            LlmRecognition.General("台灯", "九成新", "", emptyList()),
        )))
        val vm = PublishViewModel(repo, llm, store, FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.setCategory(ListingCategory.GENERAL)
        vm.recognizeWithLlm(byteArrayOf(1)); dispatcher.scheduler.advanceUntilIdle()

        vm.submitBatch(); dispatcher.scheduler.advanceUntilIdle()

        // 逐条校验失败被包成「第 N 项：<错误>」，其内层文案为"请填写联系方式"。
        assertEquals(
            UiText.Res(
                R.string.publish_error_batch_item,
                listOf(1, UiText.Res(R.string.publish_error_contact_required)),
            ),
            vm.state.value.error,
        )
        assertNull(repo.lastBatchDrafts) // 未提交。
    }

    @Test
    fun `recognizeWithLlm not configured emits navigate event`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig())
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), store, FakeContactPrefsStore(), FakeLanguagePrefsStore())

        vm.events.test {
            vm.recognizeWithLlm(byteArrayOf(1, 2)); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(PublishEvent.NavigateToLlmSettings, awaitItem())
        }
    }

    @Test
    fun `recognizeWithLlm adds each recognized item to draftBatch without touching the form`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        val llm = FakeLlmClient(DomainResult.Success(listOf(
            LlmRecognition.Book("深入理解计算机系统", "Bryant", "机械工业", "第3版", "9787111544937", "139.00"),
            LlmRecognition.Book("算法导论", "CLRS", "机械工业", "第3版", "9787111407010", ""),
        )))
        val vm = PublishViewModel(FakeRepo(), llm, store, FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.setCategory(ListingCategory.BOOK)

        vm.recognizeWithLlm(byteArrayOf(1, 2, 3)); dispatcher.scheduler.advanceUntilIdle()

        // 两本书各成一条草稿入暂存区；当前表单不被改写。
        assertEquals(2, vm.state.value.draftBatch.size)
        assertEquals("深入理解计算机系统", vm.state.value.draftBatch[0].title)
        assertEquals("139.00", vm.state.value.draftBatch[0].originalPrice)
        assertEquals("算法导论", vm.state.value.draftBatch[1].title)
        assertEquals("", vm.state.value.currentDraft.title)
        // 售价不由 LLM 产生。
        assertEquals("", vm.state.value.draftBatch[0].unitPrice)
        // 识别后待确认是否附图，记录新增条数。
        assertEquals(2, vm.state.value.recognizedCount)
        assertTrue(vm.state.value.pendingRecognitionImage != null)
    }

    @Test
    fun `recognizeWithLlm general items go to batch with originalPrice and tags`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        val llm = FakeLlmClient(DomainResult.Success(listOf(
            LlmRecognition.General("二手台灯", "九成新", "35", listOf("家居", "照明")),
        )))
        val vm = PublishViewModel(FakeRepo(), llm, store, FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.setCategory(ListingCategory.GENERAL)

        vm.recognizeWithLlm(byteArrayOf(1)); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.draftBatch.size)
        val d = vm.state.value.draftBatch[0]
        assertEquals("二手台灯", d.title)
        assertEquals("九成新", d.description)
        assertEquals("35", d.originalPrice)
        assertEquals("", d.unitPrice)
        assertEquals(listOf("家居", "照明"), d.tags)
    }

    @Test
    fun `recognizeWithLlm empty result reports error and adds nothing`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), store, FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.recognizeWithLlm(byteArrayOf(1)); dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.draftBatch.isEmpty())
        assertEquals(UiText.Res(R.string.publish_error_no_recognizable), vm.state.value.error)
        assertNull(vm.state.value.pendingRecognitionImage)
    }

    @Test
    fun `recognizeWithLlm drops fully blank recognized items`() = runTest {
        // 模型可能返回全空字段的占位项；这些应被过滤，不进入暂存区。
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        val llm = FakeLlmClient(DomainResult.Success(listOf(
            LlmRecognition.Book("算法导论", "CLRS", "机械工业", "第3版", "9787111407010", null),
            LlmRecognition.Book("", "", "", "", null, null), // 全空占位项
        )))
        val vm = PublishViewModel(FakeRepo(), llm, store, FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.setCategory(ListingCategory.BOOK)

        vm.recognizeWithLlm(byteArrayOf(1)); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.draftBatch.size)
        assertEquals("算法导论", vm.state.value.draftBatch[0].title)
        assertEquals(1, vm.state.value.recognizedCount) // 计数也只算保留的项。
    }

    @Test
    fun `recognizeWithLlm with only blank items reports error and adds nothing`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        val llm = FakeLlmClient(DomainResult.Success(listOf(
            LlmRecognition.General("", "", null, emptyList()),
        )))
        val vm = PublishViewModel(FakeRepo(), llm, store, FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.setCategory(ListingCategory.GENERAL)

        vm.recognizeWithLlm(byteArrayOf(1)); dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.draftBatch.isEmpty())
        assertEquals(UiText.Res(R.string.publish_error_no_recognizable), vm.state.value.error)
        assertNull(vm.state.value.pendingRecognitionImage)
    }

    @Test
    fun `applyToRecognized with attachImage uploads and attaches blobKey to all recognized drafts`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        val repo = FakeRepo(uploadResult = DomainResult.Success("blob-xyz"))
        val llm = FakeLlmClient(DomainResult.Success(listOf(
            LlmRecognition.General("台灯", "九成新", "", emptyList()),
            LlmRecognition.General("水杯", "全新", "", emptyList()),
        )))
        val vm = PublishViewModel(repo, llm, store, FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.setCategory(ListingCategory.GENERAL)
        vm.recognizeWithLlm(byteArrayOf(1)); dispatcher.scheduler.advanceUntilIdle()

        vm.applyToRecognized(attachImage = true); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.uploadCalls)
        assertEquals(listOf("blob-xyz"), vm.state.value.draftBatch[0].imageKeys)
        assertEquals(listOf("blob-xyz"), vm.state.value.draftBatch[1].imageKeys)
        assertNull(vm.state.value.pendingRecognitionImage)
    }

    @Test
    fun `applyToRecognized fills common fields on every recognized draft, overriding tags`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        val repo = FakeRepo(uploadResult = DomainResult.Success("blob-xyz"))
        val llm = FakeLlmClient(DomainResult.Success(listOf(
            LlmRecognition.General("台灯", "九成新", "20", listOf("家居")),
            LlmRecognition.General("水杯", "全新", null, emptyList()),
        )))
        val vm = PublishViewModel(repo, llm, store, FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.setCategory(ListingCategory.GENERAL)
        vm.recognizeWithLlm(byteArrayOf(1)); dispatcher.scheduler.advanceUntilIdle()

        vm.applyToRecognized(
            attachImage = true,
            originalPrice = "",        // 留空 → 各项保留原值
            unitPrice = "30",
            expiresInDays = "15",
            pickupLocation = "三号楼",
            contact = "wxid_x",
            tags = listOf("二手"),
        ); dispatcher.scheduler.advanceUntilIdle()

        val b = vm.state.value.draftBatch
        assertEquals("30", b[0].unitPrice); assertEquals("30", b[1].unitPrice)
        assertEquals("15", b[0].expiresInDays); assertEquals(ExpiryMode.DAYS, b[0].expiryMode)
        assertEquals("三号楼", b[0].pickupLocation); assertEquals("wxid_x", b[1].contact)
        // 原价留空 → 各自保留（台灯 20，水杯 空）。
        assertEquals("20", b[0].originalPrice); assertEquals("", b[1].originalPrice)
        // 标签覆盖：丢弃识别出的"家居"，仅用统一填写的"二手"。
        assertEquals(listOf("二手"), b[0].tags); assertEquals(listOf("二手"), b[1].tags)
        // 勾选附图 → 都带上传得到的 blobKey。
        assertEquals(listOf("blob-xyz"), b[0].imageKeys); assertEquals(listOf("blob-xyz"), b[1].imageKeys)
        assertNull(vm.state.value.pendingRecognitionImage)
    }

    @Test
    fun `applyToRecognized without attachImage does not upload`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        val repo = FakeRepo()
        val llm = FakeLlmClient(DomainResult.Success(listOf(
            LlmRecognition.General("台灯", "九成新", "", emptyList()),
        )))
        val vm = PublishViewModel(repo, llm, store, FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.setCategory(ListingCategory.GENERAL)
        vm.recognizeWithLlm(byteArrayOf(1)); dispatcher.scheduler.advanceUntilIdle()

        vm.applyToRecognized(attachImage = false, unitPrice = "30"); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repo.uploadCalls)
        assertTrue(vm.state.value.draftBatch[0].imageKeys.isEmpty())
        assertEquals("30", vm.state.value.draftBatch[0].unitPrice)
        assertNull(vm.state.value.pendingRecognitionImage)
    }

    @Test
    fun `applyToRecognized overrides LLM tags with uniform tags, discarding all LLM ones`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        // LLM 已给满上限的标签。
        val llmTags = (1..PublishConfig.MAX_TAGS).map { "llm$it" }
        val llm = FakeLlmClient(DomainResult.Success(listOf(
            LlmRecognition.General("台灯", "九成新", null, llmTags),
        )))
        val vm = PublishViewModel(FakeRepo(), llm, store, FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.setCategory(ListingCategory.GENERAL)
        vm.recognizeWithLlm(byteArrayOf(1)); dispatcher.scheduler.advanceUntilIdle()

        vm.applyToRecognized(attachImage = false, tags = listOf("用户A", "用户B")); dispatcher.scheduler.advanceUntilIdle()

        val tags = vm.state.value.draftBatch[0].tags
        assertEquals(listOf("用户A", "用户B"), tags)               // 仅保留统一标签，全部 LLM 标签被舍弃
        assertTrue(tags.none { it.startsWith("llm") })
    }

    @Test
    fun `applyToRecognized with blank tags keeps each draft's own LLM tags`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        val llm = FakeLlmClient(DomainResult.Success(listOf(
            LlmRecognition.General("台灯", "九成新", null, listOf("家居")),
            LlmRecognition.General("水杯", "全新", null, emptyList()),
        )))
        val vm = PublishViewModel(FakeRepo(), llm, store, FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.setCategory(ListingCategory.GENERAL)
        vm.recognizeWithLlm(byteArrayOf(1)); dispatcher.scheduler.advanceUntilIdle()

        vm.applyToRecognized(attachImage = false, contact = "wxid_x"); dispatcher.scheduler.advanceUntilIdle()

        val b = vm.state.value.draftBatch
        assertEquals(listOf("家居"), b[0].tags)   // 统一标签留空 → 保留各自 LLM 标签
        assertEquals(emptyList<String>(), b[1].tags)
    }

    @Test
    fun `dismissRecognitionImage attaches nothing`() = runTest {
        val store = FakeLlmConfigStore(LlmConfig(baseUrl = "x", apiKey = "y", model = "z"))
        val repo = FakeRepo()
        val llm = FakeLlmClient(DomainResult.Success(listOf(
            LlmRecognition.General("台灯", "九成新", "", emptyList()),
        )))
        val vm = PublishViewModel(repo, llm, store, FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.setCategory(ListingCategory.GENERAL)
        vm.recognizeWithLlm(byteArrayOf(1)); dispatcher.scheduler.advanceUntilIdle()

        vm.dismissRecognitionImage(); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repo.uploadCalls)
        assertTrue(vm.state.value.draftBatch[0].imageKeys.isEmpty())
        assertNull(vm.state.value.pendingRecognitionImage)
    }

    @Test
    fun `lookupBook 200 prefills currentDraft`() = runTest {
        val vm = PublishViewModel(
            FakeRepo(lookupResult = DomainResult.Success(
                BookInfo("9787111544937", "深入理解计算机系统", "Bryant", "机械工业", "第3版"),
            )),
            FakeLlmClient(DomainResult.Success(emptyList())),
            FakeLlmConfigStore(),
            FakeContactPrefsStore(),
            FakeLanguagePrefsStore(),
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
            FakeLlmClient(DomainResult.Success(emptyList())),
            FakeLlmConfigStore(),
            FakeContactPrefsStore(),
            FakeLanguagePrefsStore(),
        )
        vm.setCategory(ListingCategory.BOOK)

        vm.lookupBook("1234567890123"); dispatcher.scheduler.advanceUntilIdle()

        assertEquals("1234567890123", vm.state.value.currentDraft.isbn)
        assertEquals("", vm.state.value.currentDraft.title)
    }

    @Test
    fun `toggleTag enforces MAX_TAGS`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        repeat(PublishConfig.MAX_TAGS) { vm.toggleTag("tag$it") }

        assertEquals(PublishConfig.MAX_TAGS, vm.state.value.currentDraft.tags.size)

        vm.toggleTag("overflow")
        assertEquals(PublishConfig.MAX_TAGS, vm.state.value.currentDraft.tags.size)
        assertFalse(vm.state.value.currentDraft.tags.contains("overflow"))
    }

    @Test
    fun `removeDraft removes by index`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("A"); vm.onContact("x"); vm.addDraftToBatch()
        vm.onTitle("B"); vm.onContact("x"); vm.addDraftToBatch()
        dispatcher.scheduler.advanceUntilIdle()

        vm.removeDraft(0)
        assertEquals(1, vm.state.value.draftBatch.size)
        assertEquals("B", vm.state.value.draftBatch[0].title)
    }

    @Test
    fun `editDraft loads into currentDraft without removing from batch`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("A"); vm.onContact("x"); vm.addDraftToBatch()
        dispatcher.scheduler.advanceUntilIdle()

        vm.editDraft(0)
        // 非破坏：项仍留在列表，currentDraft 载入该项，editingIndex 记录其位置。
        assertEquals("A", vm.state.value.currentDraft.title)
        assertEquals(1, vm.state.value.draftBatch.size)
        assertEquals(0, vm.state.value.editingIndex)
    }

    @Test
    fun `editDraft then addDraftToBatch writes back in place without duplicating`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("A"); vm.onContact("x"); vm.addDraftToBatch()
        vm.onTitle("B"); vm.onContact("x"); vm.addDraftToBatch()
        dispatcher.scheduler.advanceUntilIdle()

        // 编辑第 0 项，改标题后保存 → 写回原位，不新增、不打乱顺序。
        vm.editDraft(0)
        vm.onTitle("A2")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.state.value.draftBatch.size)
        assertEquals("A2", vm.state.value.draftBatch[0].title)
        assertEquals("B", vm.state.value.draftBatch[1].title)
        assertNull(vm.state.value.editingIndex) // 保存后回到新建态。
        assertEquals("", vm.state.value.currentDraft.title)
    }

    @Test
    fun `editDraft syncs selectedCategory to the edited item`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.setCategory(ListingCategory.BOOK)
        vm.onTitle("书"); vm.onContact("x"); vm.addDraftToBatch()
        dispatcher.scheduler.advanceUntilIdle()
        // 切到一般商品后再回头编辑书籍项：顶部类别应同步回 BOOK。
        vm.setCategory(ListingCategory.GENERAL)
        vm.editDraft(0)

        assertEquals(ListingCategory.BOOK, vm.state.value.selectedCategory)
        assertEquals(ListingCategory.BOOK, vm.state.value.currentDraft.category)
    }

    @Test
    fun `newDraft clears the form and leaves editing state`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("A"); vm.onContact("x"); vm.addDraftToBatch()
        dispatcher.scheduler.advanceUntilIdle()
        vm.editDraft(0) // 进入编辑既有项。
        assertEquals(0, vm.state.value.editingIndex)

        vm.newDraft()
        // 表单清空、退出编辑态；暂存项不受影响。
        assertEquals("", vm.state.value.currentDraft.title)
        assertNull(vm.state.value.editingIndex)
        assertEquals(1, vm.state.value.draftBatch.size)
    }

    @Test
    fun `removeDraft of the edited item resets the form and editingIndex`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("A"); vm.onContact("x"); vm.addDraftToBatch()
        vm.onTitle("B"); vm.onContact("x"); vm.addDraftToBatch()
        dispatcher.scheduler.advanceUntilIdle()

        vm.editDraft(1) // 正在编辑第 1 项（B）。
        vm.removeDraft(1)

        assertEquals(1, vm.state.value.draftBatch.size)
        assertEquals("A", vm.state.value.draftBatch[0].title)
        assertNull(vm.state.value.editingIndex)
        assertEquals("", vm.state.value.currentDraft.title)
    }

    @Test
    fun `removeDraft before the edited item shifts editingIndex left`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("A"); vm.onContact("x"); vm.addDraftToBatch()
        vm.onTitle("B"); vm.onContact("x"); vm.addDraftToBatch()
        dispatcher.scheduler.advanceUntilIdle()

        vm.editDraft(1)      // 编辑 B（index 1）。
        vm.removeDraft(0)    // 删除其之前的 A → B 左移到 0。

        assertEquals(0, vm.state.value.editingIndex)
        assertEquals("B", vm.state.value.draftBatch[0].title)
    }

    @Test
    fun `editDraft parks the in-progress new item before loading the target`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("A"); vm.onContact("x"); vm.addDraftToBatch()
        dispatcher.scheduler.advanceUntilIdle()
        // 表单里有一条正在编写、尚未加入的新项 B。
        vm.onTitle("B")
        // 选中已暂存的 A → B 先并入列表（不丢失），再载入 A。
        vm.editDraft(0)

        val s = vm.state.value
        assertEquals("A", s.currentDraft.title)
        assertEquals(0, s.editingIndex)
        assertEquals(2, s.draftBatch.size)
        assertEquals("A", s.draftBatch[0].title)
        assertEquals("B", s.draftBatch[1].title)
    }

    @Test
    fun `newDraft parks the in-progress new item then resets the form`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("C") // 编写中的新项，未加入。
        vm.newDraft()

        val s = vm.state.value
        assertEquals(1, s.draftBatch.size)
        assertEquals("C", s.draftBatch[0].title)
        assertNull(s.editingIndex)
        assertEquals("", s.currentDraft.title)
    }

    @Test
    fun `discardDraft drops the in-progress item without adding it`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        vm.onTitle("D")
        vm.discardDraft()

        val s = vm.state.value
        assertTrue(s.draftBatch.isEmpty())
        assertEquals("", s.currentDraft.title)
        assertNull(s.editingIndex)
    }

    @Test
    fun `submitBatch includes the in-progress item by parking it first`() = runTest {
        val repo = FakeRepo()
        val vm = PublishViewModel(repo, FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())
        // 一条已暂存项 + 一条仅在表单中的临时项，直接提交应一并发布。
        vm.onTitle("已暂存"); vm.onContact("x"); vm.addDraftToBatch()
        vm.onTitle("临时项"); vm.onContact("y")
        vm.submitBatch(); dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.batchSubmitted)
        val drafts = repo.lastBatchDrafts!!
        assertEquals(2, drafts.size)
        assertEquals("已暂存", drafts[0].title)
        assertEquals("临时项", drafts[1].title)
    }

    @Test
    fun `isBlankDraft distinguishes a pristine draft from a touched one`() {
        assertTrue(DraftItem(ListingCategory.GENERAL).isBlankDraft())
        assertTrue(DraftItem(ListingCategory.BOOK).isBlankDraft())
        assertFalse(DraftItem(ListingCategory.GENERAL, title = "x").isBlankDraft())
    }

    @Test
    fun `openBookScan emits navigate event`() = runTest {
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore())

        vm.events.test {
            vm.openBookScan(); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(PublishEvent.NavigateToBookScan, awaitItem())
        }
    }

    @Test
    fun `loads popular tags on init`() = runTest {
        val vm = PublishViewModel(
            FakeRepo(tagsResult = DomainResult.Success(listOf(
                cn.edu.bit.bitmart.core.domain.repository.TagInfo(1L, "热门1"),
                cn.edu.bit.bitmart.core.domain.repository.TagInfo(2L, "热门2"),
            ))),
            FakeLlmClient(DomainResult.Success(emptyList())),
            FakeLlmConfigStore(),
            FakeContactPrefsStore(),
            FakeLanguagePrefsStore(),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("热门1", "热门2"), vm.state.value.popularTags)
    }

    // —— 常用联系方式 ——

    @Test
    fun `commonContacts reflects the store`() = runTest {
        val store = FakeContactPrefsStore(listOf("wxid_a", "qq_b"))
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), store, FakeLanguagePrefsStore())
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("wxid_a", "qq_b"), vm.state.value.commonContacts)
    }

    @Test
    fun `addDraftToBatch prompts to save a new contact but not an existing one`() = runTest {
        val store = FakeContactPrefsStore(listOf("wxid_existing"))
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), store, FakeLanguagePrefsStore())
        dispatcher.scheduler.advanceUntilIdle()

        // 已在常用列表中的联系方式 → 不询问。
        vm.onTitle("A"); vm.onContact("wxid_existing")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.state.value.pendingSaveContact)

        // 新联系方式 → 询问是否保存。
        vm.onTitle("B"); vm.onContact("new_contact")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("new_contact", vm.state.value.pendingSaveContact)
    }

    @Test
    fun `savePendingContact stores it, updates chips, and stops re-prompting`() = runTest {
        val store = FakeContactPrefsStore()
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), store, FakeLanguagePrefsStore())
        dispatcher.scheduler.advanceUntilIdle()

        vm.onTitle("A"); vm.onContact("wx_new")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("wx_new", vm.state.value.pendingSaveContact)

        vm.savePendingContact(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.state.value.pendingSaveContact)
        assertTrue(vm.state.value.commonContacts.contains("wx_new")) // chips 即时反映

        // 同一会话再次使用该联系方式 → 已保存，不再询问。
        vm.onTitle("B"); vm.onContact("wx_new")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.state.value.pendingSaveContact)
    }

    @Test
    fun `dismissPendingContact clears without saving and does not re-prompt in session`() = runTest {
        val store = FakeContactPrefsStore()
        val vm = PublishViewModel(FakeRepo(), FakeLlmClient(DomainResult.Success(emptyList())), FakeLlmConfigStore(), store, FakeLanguagePrefsStore())
        dispatcher.scheduler.advanceUntilIdle()

        vm.onTitle("A"); vm.onContact("wx_decline")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("wx_decline", vm.state.value.pendingSaveContact)

        vm.dismissPendingContact()
        assertNull(vm.state.value.pendingSaveContact)
        assertTrue(vm.state.value.commonContacts.isEmpty()) // 未保存

        // 同一会话再次使用该联系方式 → 已询问过，不再打扰。
        vm.onTitle("B"); vm.onContact("wx_decline")
        vm.addDraftToBatch(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.state.value.pendingSaveContact)
    }

    // —— 编辑模式（复用发布表单） ——

    private fun detail(
        type: ListingType = ListingType.SELL,
        category: ListingCategory = ListingCategory.BOOK,
        expiresAt: String = "2026-07-01T00:00:00Z",
        originalPrice: String? = null,
    ) = ListingDetail(
        id = 7, type = type, category = category, userId = 5, nickname = "卖家",
        title = "高数教材", description = "九成新", unitPrice = "30.00", originalPrice = originalPrice,
        quantityTotal = 4, quantitySold = 1,
        pickupLocation = "三号楼", contacts = listOf(Contact("WECHAT", "wxid_x")), tags = listOf("教材"),
        imageUrls = listOf("/static/2026/06/a.jpg"), expiresAt = expiresAt,
        createdAt = "2026-06-02T00:00:00Z",
        book = BookInfo("9787111407010", "高数", "作者", "机械工业", "第3版"),
    )

    private fun editVm(repo: FakeRepo) = PublishViewModel(
        repo, FakeLlmClient(DomainResult.Success(emptyList())),
        FakeLlmConfigStore(), FakeContactPrefsStore(), FakeLanguagePrefsStore(),
    )

    @Test
    fun `loadForEdit prefills draft from detail and enters edit mode`() = runTest {
        val repo = FakeRepo(detailResult = DomainResult.Success(detail(type = ListingType.BUY)))
        val vm = editVm(repo)
        vm.loadForEdit(7); dispatcher.scheduler.advanceUntilIdle()

        val s = vm.state.value
        assertEquals(7L, s.editingId)
        assertEquals(ListingType.BUY, s.type)
        assertEquals(ListingCategory.BOOK, s.selectedCategory)
        val d = s.currentDraft
        assertEquals("高数教材", d.title)
        assertEquals("30.00", d.unitPrice)
        assertEquals("4", d.quantityTotal)
        assertEquals("三号楼", d.pickupLocation)
        assertEquals("wxid_x", d.contact)
        assertEquals(listOf("教材"), d.tags)
        assertEquals("9787111407010", d.isbn)
        assertEquals(listOf("2026/06/a.jpg"), d.imageKeys) // /static/ 前缀已去除
        assertEquals(ExpiryMode.DATE, d.expiryMode)        // 过期以日期模式预填
    }

    @Test
    fun `saveEdit sends full UpdateDraft and marks saved`() = runTest {
        // 过期取真实"现在 + 30 天"，避开日期校验窗口的时区/时钟敏感。
        val repo = FakeRepo(detailResult = DomainResult.Success(
            detail(category = ListingCategory.GENERAL, expiresAt = OffsetDateTime.now().plusDays(30).toString()),
        ))
        val vm = editVm(repo)
        vm.loadForEdit(7); dispatcher.scheduler.advanceUntilIdle()

        vm.onTitle("新标题"); vm.onQuantity("9")
        vm.saveEdit(); dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.saved)
        assertEquals(7L, repo.lastUpdate?.first)
        val u = repo.lastUpdate!!.second
        assertEquals("新标题", u.title)
        assertEquals(9, u.quantityTotal)
        assertEquals(ListingCategory.GENERAL, u.category)
        assertEquals("wxid_x", u.contacts?.firstOrNull()?.value)
        assertEquals(listOf("教材"), u.tags)
        assertEquals(listOf("2026/06/a.jpg"), u.imageKeys)
    }

    @Test
    fun `saveEdit blocked by invalid draft does not call update`() = runTest {
        val repo = FakeRepo(detailResult = DomainResult.Success(detail()))
        val vm = editVm(repo)
        vm.loadForEdit(7); dispatcher.scheduler.advanceUntilIdle()

        vm.onTitle("   ")
        vm.saveEdit(); dispatcher.scheduler.advanceUntilIdle()

        assertEquals(UiText.Res(R.string.publish_error_title_required), vm.state.value.error)
        assertNull(repo.lastUpdate)
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun `saveEdit blanking originalPrice sends clearOriginalPrice with null value`() = runTest {
        val repo = FakeRepo(detailResult = DomainResult.Success(
            detail(
                category = ListingCategory.GENERAL,
                expiresAt = OffsetDateTime.now().plusDays(30).toString(),
                originalPrice = "199.00",
            ),
        ))
        val vm = editVm(repo)
        vm.loadForEdit(7); dispatcher.scheduler.advanceUntilIdle()

        // 编辑加载后原价应预填为旧值 199.00（确保后续是真实的"有值→清空"转变）。
        assertEquals("199.00", vm.state.value.currentDraft.originalPrice)

        // 用户把原价清空（空白）→ 应下发清除标志，而非沿用旧值。
        vm.onOriginalPrice("   ")
        vm.saveEdit(); dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.saved)
        val u = repo.lastUpdate!!.second
        assertTrue(u.clearOriginalPrice)
        assertNull(u.originalPrice)
    }

    @Test
    fun `saveEdit with a non-blank originalPrice sends the trimmed value and no clear flag`() = runTest {
        val repo = FakeRepo(detailResult = DomainResult.Success(
            detail(category = ListingCategory.GENERAL, expiresAt = OffsetDateTime.now().plusDays(30).toString()),
        ))
        val vm = editVm(repo)
        vm.loadForEdit(7); dispatcher.scheduler.advanceUntilIdle()

        vm.onOriginalPrice("  88.50  ")
        vm.saveEdit(); dispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.saved)
        val u = repo.lastUpdate!!.second
        assertFalse(u.clearOriginalPrice)
        assertEquals("88.50", u.originalPrice)
    }
}
